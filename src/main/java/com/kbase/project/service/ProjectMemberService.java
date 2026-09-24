package com.kbase.project.service;

import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.service.AiConversationRetentionService;
import com.kbase.project.dto.response.ProjectMemberResponse;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.pagination.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership listing and lifecycle. Removing or leaving a member never
 * touches their uploaded documents; ownership stays with ProjectMember.role.
 */
@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService authorizationService;
    private final AiConversationRetentionService retentionService;

    @Autowired
    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService authorizationService,
            AiConversationRetentionService retentionService) {
        this.projectMemberRepository =
                Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.retentionService = retentionService;
    }

    /** Compatibility constructor for Core-focused unit tests. */
    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService authorizationService) {
        this(projectMemberRepository, authorizationService, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectMemberResponse> listMembers(
            UUID projectId, CustomUserPrincipal principal, Pageable pageable) {
        authorizationService.requireProjectAccess(projectId, principal);
        Page<ProjectMemberResponse> page = projectMemberRepository
                .findAllByProjectId(projectId, pageable)
                .map(ProjectMemberService::toResponse);
        return PageResponse.from(page, response -> response);
    }

    /** OWNER/ADMIN removes a MEMBER; the current project OWNER is untouchable. */
    @Transactional
    public void removeMember(UUID projectId, UUID targetUserId, CustomUserPrincipal principal) {
        authorizationService.requireOwner(projectId, principal);
        ProjectMember target = projectMemberRepository
                .findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));
        if (target.getRole() == ProjectRole.OWNER) {
            // Single-OWNER invariant: not removable through this endpoint,
            // even by an ADMIN.
            throw new BusinessException(ErrorCode.PROJECT_OWNER_REMOVAL_FORBIDDEN);
        }
        if (retentionService != null) {
            retentionService.lockLifecycle(projectId, targetUserId);
        }
        projectMemberRepository.delete(target);
        if (retentionService != null) {
            retentionService.schedulePurge(projectId, targetUserId);
        }
    }

    /** MEMBER leaves; the OWNER cannot leave in Core v1. Documents remain. */
    @Transactional
    public void leaveProject(UUID projectId, CustomUserPrincipal principal) {
        authorizationService.requireProjectAccess(projectId, principal);
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));
        if (membership.getRole() == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE_PROJECT);
        }
        if (retentionService != null) {
            retentionService.lockLifecycle(projectId, principal.getUserId());
        }
        projectMemberRepository.delete(membership);
        if (retentionService != null) {
            retentionService.schedulePurge(projectId, principal.getUserId());
        }
    }

    private static ProjectMemberResponse toResponse(ProjectMember membership) {
        return new ProjectMemberResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getJoinedAt());
    }
}
