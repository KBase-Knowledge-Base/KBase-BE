package com.kbase.invitation.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.service.AiConversationRetentionService;
import com.kbase.config.properties.InvitationProperties;
import com.kbase.invitation.dto.request.CreateInvitationRequest;
import com.kbase.invitation.dto.response.AcceptInvitationResponse;
import com.kbase.invitation.dto.response.InvitationResponse;
import com.kbase.invitation.entity.ProjectInvitation;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.mail.service.MailService;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.user.entity.User;
import com.kbase.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitation lifecycle. Invitations use their own secure token (never OTP);
 * only the SHA-256 token hash is persisted and the raw token travels only in
 * the email link and the accept request. Acceptance runs under a
 * pessimistic write lock so concurrent accepts cannot create two members.
 */
@Service
public class InvitationService {

    private final ProjectAuthorizationService authorizationService;
    private final ProjectInvitationRepository invitationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final InvitationTokens invitationTokens;
    private final InvitationProperties invitationProperties;
    private final java.time.Clock clock;
    private final AiConversationRetentionService retentionService;

    @Autowired
    public InvitationService(
            ProjectAuthorizationService authorizationService,
            ProjectInvitationRepository invitationRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            MailService mailService,
            InvitationTokens invitationTokens,
            InvitationProperties invitationProperties,
            AiConversationRetentionService retentionService) {
        this(authorizationService, invitationRepository, projectMemberRepository,
                userRepository, mailService, invitationTokens, invitationProperties,
                java.time.Clock.systemUTC(), retentionService);
    }

    /** Compatibility constructor for existing Core tests and callers. */
    public InvitationService(
            ProjectAuthorizationService authorizationService,
            ProjectInvitationRepository invitationRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            MailService mailService,
            InvitationTokens invitationTokens,
            InvitationProperties invitationProperties) {
        this(authorizationService, invitationRepository, projectMemberRepository,
                userRepository, mailService, invitationTokens, invitationProperties,
                java.time.Clock.systemUTC(), null);
    }

    public InvitationService(
            ProjectAuthorizationService authorizationService,
            ProjectInvitationRepository invitationRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            MailService mailService,
            InvitationTokens invitationTokens,
            InvitationProperties invitationProperties,
            java.time.Clock clock) {
        this(authorizationService, invitationRepository, projectMemberRepository,
                userRepository, mailService, invitationTokens, invitationProperties, clock, null);
    }

    public InvitationService(
            ProjectAuthorizationService authorizationService,
            ProjectInvitationRepository invitationRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            MailService mailService,
            InvitationTokens invitationTokens,
            InvitationProperties invitationProperties,
            java.time.Clock clock,
            AiConversationRetentionService retentionService) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.invitationRepository = Objects.requireNonNull(invitationRepository, "invitationRepository");
        this.projectMemberRepository =
                Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.mailService = Objects.requireNonNull(mailService, "mailService");
        this.invitationTokens = Objects.requireNonNull(invitationTokens, "invitationTokens");
        this.invitationProperties = Objects.requireNonNull(invitationProperties, "invitationProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retentionService = retentionService;
    }

    /** OWNER/ADMIN invites a normalized email; mail failure rolls back the row. */
    @Transactional
    public InvitationResponse createInvitation(
            UUID projectId, CreateInvitationRequest request, CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireOwner(projectId, principal);
        Project project = access.project();
        User inviter = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String email = normalizeEmail(request.email());
        ensureEmailNotMember(projectId, email);
        if (invitationRepository.existsByProjectIdAndEmailAndStatus(
                projectId, email, InvitationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_PENDING);
        }

        String rawToken = invitationTokens.generate();
        ProjectInvitation invitation = invitationRepository.save(new ProjectInvitation(
                project,
                inviter,
                email,
                invitationTokens.hash(rawToken),
                InvitationStatus.PENDING,
                clock.instant().plus(invitationProperties.getExpiration())));

        // Inside the create transaction: a failed send propagates and rolls
        // the invitation row back, avoiding a misleading PENDING state.
        mailService.sendProjectInvitation(
                email,
                inviter.getDisplayName(),
                project.getName(),
                invitationUrl(rawToken),
                invitation.getExpiresAt());

        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> listInvitations(
            UUID projectId, InvitationStatus status, CustomUserPrincipal principal, Pageable pageable) {
        authorizationService.requireOwner(projectId, principal);
        Page<ProjectInvitation> page = status == null
                ? invitationRepository.findAllByProjectId(projectId, pageable)
                : invitationRepository.findAllByProjectIdAndStatus(projectId, status, pageable);
        return PageResponse.from(page, InvitationService::toResponse);
    }

    /** Replaces the pending token (invalidating the old one) and resets expiry. */
    @Transactional
    public InvitationResponse resend(
            UUID projectId, UUID invitationId, CustomUserPrincipal principal) {
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireOwner(projectId, principal);
        ProjectInvitation invitation = invitationRepository.findByIdAndProjectId(invitationId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        ensureEmailNotMember(projectId, invitation.getEmail());

        String rawToken = invitationTokens.generate();
        invitation.setTokenHash(invitationTokens.hash(rawToken));
        invitation.setExpiresAt(clock.instant().plus(invitationProperties.getExpiration()));

        // The invited-by row is LAZY; resolving the display name stays inside
        // this transactional write.
        mailService.sendProjectInvitation(
                invitation.getEmail(),
                invitation.getInvitedBy().getDisplayName(),
                access.project().getName(),
                invitationUrl(rawToken),
                invitation.getExpiresAt());

        return toResponse(invitation);
    }

    /** Marks a pending invitation CANCELLED; the record is never hard-deleted. */
    @Transactional
    public void cancel(UUID projectId, UUID invitationId, CustomUserPrincipal principal) {
        authorizationService.requireOwner(projectId, principal);
        ProjectInvitation invitation = invitationRepository.findByIdAndProjectId(invitationId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
    }

    /**
     * Accepts an invitation with a pessimistic write lock on the invitation
     * row, so exactly one of two concurrent accepts can succeed. When the
     * invitation has expired, the {@code EXPIRED} status write is committed
     * (noRollbackFor) before the error is returned, so the stored lifecycle
     * matches the status model and the pending-invitation uniqueness slot is
     * released for a replacement invitation.
     */
    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public AcceptInvitationResponse accept(String rawToken, CustomUserPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        String tokenHash = invitationTokens.hash(
                rawToken == null ? "" : rawToken);
        ProjectInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        if (!invitation.getExpiresAt().isAfter(clock.instant())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new InvitationExpiredException();
        }

        User acceptor = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!normalizeEmail(acceptor.getEmail()).equals(invitation.getEmail())) {
            throw new BusinessException(ErrorCode.INVITATION_EMAIL_MISMATCH);
        }

        UUID projectId = invitation.getProject().getId();
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, acceptor.getId())) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        ProjectMember membership = projectMemberRepository.save(
                new ProjectMember(invitation.getProject(), acceptor, ProjectRole.MEMBER));
        if (retentionService != null) {
            retentionService.cancelPurgeOnRejoin(projectId, acceptor.getId());
        }
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(clock.instant());

        return new AcceptInvitationResponse(
                projectId, membership.getId(), membership.getRole(), membership.getJoinedAt());
    }

    private void ensureEmailNotMember(UUID projectId, String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
                throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
            }
        });
    }

    private String invitationUrl(String rawToken) {
        return invitationProperties.getAcceptBaseUrl() + "?token=" + rawToken;
    }

    private static InvitationResponse toResponse(ProjectInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getProject().getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt());
    }

    static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email");
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
