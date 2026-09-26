package com.kbase.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

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
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.MailServiceUnavailableException;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    private ProjectAuthorizationService authorizationService;
    private ProjectInvitationRepository invitationRepository;
    private ProjectMemberRepository projectMemberRepository;
    private UserRepository userRepository;
    private MailService mailService;
    private InvitationService service;

    private UUID projectId;
    private Project project;
    private User inviter;
    private CustomUserPrincipal inviterPrincipal;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authorizationService = mock(ProjectAuthorizationService.class);
        invitationRepository = mock(ProjectInvitationRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        userRepository = mock(UserRepository.class);
        mailService = mock(MailService.class);
        InvitationProperties properties = new InvitationProperties();
        properties.setExpiration(Duration.ofHours(72));
        service = new InvitationService(
                authorizationService, invitationRepository, projectMemberRepository,
                userRepository, mailService, new InvitationTokens(new java.security.SecureRandom()),
                properties, Clock.fixed(NOW, ZoneOffset.UTC));

        projectId = UUID.randomUUID();
        project = new Project("KBase Project", "desc");
        project.setId(projectId);

        inviter = new User("owner@example.com", "hash", "Owner User",
                SystemRole.USER, UserStatus.ACTIVE);
        inviter.setId(UUID.randomUUID());
        inviterPrincipal = new CustomUserPrincipal(
                inviter.getId(), inviter.getEmail(), SystemRole.USER, UserStatus.ACTIVE, true);

        when(authorizationService.requireOwner(projectId, inviterPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        when(userRepository.findById(inviter.getId())).thenReturn(Optional.of(inviter));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(projectMemberRepository.existsByProjectIdAndUserId(any(UUID.class), any(UUID.class)))
                .thenReturn(false);
        when(invitationRepository.existsByProjectIdAndEmailAndStatus(
                any(UUID.class), anyString(), any(InvitationStatus.class))).thenReturn(false);
        when(invitationRepository.saveAndFlush(any(ProjectInvitation.class)))
                .thenAnswer(invocation -> {
                    ProjectInvitation saved = invocation.getArgument(0);
                    java.lang.reflect.Field idField = ProjectInvitation.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(saved, UUID.randomUUID());
                    return saved;
                });
    }

    private User userWith(String email) {
        User user = new User(email, "hash", "Invited User", SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        user.setEmailVerifiedAt(NOW);
        return user;
    }

    @Test
    void createInvitationStoresHashOnlyPendingAndSendsMailWithLink() {
        InvitationResponse response = service.createInvitation(
                projectId, new CreateInvitationRequest("Friend@Example.com"), inviterPrincipal);

        ArgumentCaptor<ProjectInvitation> invitationCaptor =
                ArgumentCaptor.forClass(ProjectInvitation.class);
        verify(invitationRepository).saveAndFlush(invitationCaptor.capture());
        ProjectInvitation persisted = invitationCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(persisted.getEmail()).isEqualTo("friend@example.com");
        assertThat(persisted.getTokenHash()).hasSize(64);
        assertThat(persisted.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendProjectInvitation(
                anyString(), anyString(), anyString(), urlCaptor.capture(), any(Instant.class));
        assertThat(urlCaptor.getValue())
                .startsWith("http://localhost:3000/invitations/accept?token=")
                .doesNotContain(persisted.getTokenHash());

        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(String.valueOf(response)).doesNotContain("token");
    }

    @Test
    void createInvitationRejectsMemberEmailAndDuplicatePending() {
        User existingMember = userWith("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(existingMember));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, existingMember.getId()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.createInvitation(
                projectId, new CreateInvitationRequest("member@example.com"), inviterPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS));

        when(invitationRepository.existsByProjectIdAndEmailAndStatus(
                projectId, "pending@example.com", InvitationStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.createInvitation(
                projectId, new CreateInvitationRequest("pending@example.com"), inviterPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVITATION_ALREADY_PENDING));

        verify(mailService, never()).sendProjectInvitation(
                anyString(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void mailFailurePropagatesFromCreateWithoutSilentPendingState() {
        org.mockito.Mockito.doThrow(new MailServiceUnavailableException())
                .when(mailService).sendProjectInvitation(
                        anyString(), anyString(), anyString(), anyString(), any(Instant.class));

        assertThatThrownBy(() -> service.createInvitation(
                projectId, new CreateInvitationRequest("friend@example.com"), inviterPrincipal))
                .isInstanceOf(MailServiceUnavailableException.class);
        // The Spring transaction rolls the saved row back; the unit test only
        // proves the exception escapes the service boundary.
    }

    @Test
    void resendReplacesHashResetsExpiryAndInvalidatesOldToken() {
        ProjectInvitation invitation = new ProjectInvitation(
                project, inviter, "friend@example.com", "old-hash",
                InvitationStatus.PENDING, NOW.minus(Duration.ofHours(1)));
        when(invitationRepository.findByIdAndProjectId(
                invitation.getId(), projectId)).thenReturn(Optional.of(invitation));

        Instant before = invitation.getExpiresAt();
        InvitationResponse response = service.resend(
                projectId, invitation.getId(), inviterPrincipal);

        assertThat(invitation.getTokenHash()).isNotEqualTo("old-hash").hasSize(64);
        assertThat(invitation.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)))
                .isNotEqualTo(before);
        verify(mailService).sendProjectInvitation(
                anyString(), anyString(), anyString(), anyString(), any(Instant.class));
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void resendRejectsNonPendingAndMemberEmails() {
        ProjectInvitation accepted = new ProjectInvitation(
                project, inviter, "friend@example.com", "hash",
                InvitationStatus.ACCEPTED, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByIdAndProjectId(accepted.getId(), projectId))
                .thenReturn(Optional.of(accepted));
        assertThatThrownBy(() -> service.resend(projectId, accepted.getId(), inviterPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVITATION_NOT_PENDING));

        ProjectInvitation pending = new ProjectInvitation(
                project, inviter, "member@example.com", "hash",
                InvitationStatus.PENDING, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByIdAndProjectId(pending.getId(), projectId))
                .thenReturn(Optional.of(pending));
        User member = userWith("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, member.getId()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.resend(projectId, pending.getId(), inviterPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS));
    }

    @Test
    void cancelMarksPendingCancelledWithoutPhysicalDelete() {
        ProjectInvitation pending = new ProjectInvitation(
                project, inviter, "friend@example.com", "hash",
                InvitationStatus.PENDING, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByIdAndProjectId(pending.getId(), projectId))
                .thenReturn(Optional.of(pending));

        service.cancel(projectId, pending.getId(), inviterPrincipal);
        assertThat(pending.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        verify(invitationRepository, never()).delete(any(ProjectInvitation.class));

        when(invitationRepository.findByIdAndProjectId(UUID.randomUUID(), projectId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(
                projectId, UUID.randomUUID(), inviterPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
    }

    @Test
    void acceptCreatesMemberAndMarksAcceptedWithinSameFlow() {
        User acceptor = userWith("friend@example.com");
        CustomUserPrincipal acceptorPrincipal = new CustomUserPrincipal(
                acceptor.getId(), acceptor.getEmail(), SystemRole.USER, UserStatus.ACTIVE, true);
        when(userRepository.findById(acceptor.getId())).thenReturn(Optional.of(acceptor));

        ProjectInvitation invitation = new ProjectInvitation(
                project, inviter, "friend@example.com", "valid-hash",
                InvitationStatus.PENDING, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByTokenHashForUpdate(
                serviceHashOf("raw-invitation-token"))).thenReturn(Optional.of(invitation));
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(invocation -> {
                    ProjectMember saved = invocation.getArgument(0);
                    java.lang.reflect.Field idField;
                    try {
                        idField = ProjectMember.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(saved, UUID.randomUUID());
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException(exception);
                    }
                    return saved;
                });

        AcceptInvitationResponse response =
                service.accept("raw-invitation-token", acceptorPrincipal);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.membershipId()).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void acceptRejectsUnknownNotPendingExpiredAndMismatchedTokens() {
        User acceptor = userWith("friend@example.com");
        CustomUserPrincipal acceptorPrincipal = new CustomUserPrincipal(
                acceptor.getId(), acceptor.getEmail(), SystemRole.USER, UserStatus.ACTIVE, true);
        when(userRepository.findById(acceptor.getId())).thenReturn(Optional.of(acceptor));

        // Unknown token.
        when(invitationRepository.findByTokenHashForUpdate(serviceHashOf("unknown")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept("unknown", acceptorPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));

        // Cancelled / already accepted.
        ProjectInvitation cancelled = new ProjectInvitation(
                project, inviter, "friend@example.com", serviceHashOf("cancelled"),
                InvitationStatus.CANCELLED, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByTokenHashForUpdate(serviceHashOf("cancelled")))
                .thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.accept("cancelled", acceptorPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVITATION_NOT_PENDING));

        // Expired: status flips to EXPIRED before the error.
        ProjectInvitation expired = new ProjectInvitation(
                project, inviter, "friend@example.com", serviceHashOf("expired"),
                InvitationStatus.PENDING, NOW.minusSeconds(1));
        when(invitationRepository.findByTokenHashForUpdate(serviceHashOf("expired")))
                .thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.accept("expired", acceptorPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
        assertThat(expired.getStatus()).isEqualTo(InvitationStatus.EXPIRED);

        // Email mismatch.
        ProjectInvitation pending = new ProjectInvitation(
                project, inviter, "other@example.com", serviceHashOf("pending"),
                InvitationStatus.PENDING, NOW.plus(Duration.ofHours(1)));
        when(invitationRepository.findByTokenHashForUpdate(serviceHashOf("pending")))
                .thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.accept("pending", acceptorPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVITATION_EMAIL_MISMATCH));
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    private String serviceHashOf(String rawToken) {
        return new InvitationTokens().hash(rawToken);
    }
}
