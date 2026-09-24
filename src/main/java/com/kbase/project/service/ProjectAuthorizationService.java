package com.kbase.project.service;

import java.util.Objects;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.enums.SystemRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized project-level authorization. ADMIN is a system-level override
 * and never receives a fake ProjectMember row; project roles are always read
 * from the current {@code project_members} data.
 */
@Service
public class ProjectAuthorizationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAuthorizationService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository");
        this.projectMemberRepository =
                Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
    }

    /**
     * Requires read access: MEMBER, OWNER or ADMIN. Returns the membership
     * role, or null when access is granted through the ADMIN override.
     */
    @Transactional(readOnly = true)
    public ProjectAccess requireProjectAccess(UUID projectId, CustomUserPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Project project = requireProject(projectId);
        if (principal.getSystemRole() == SystemRole.ADMIN) {
            return ProjectAccess.adminOverride(project);
        }
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_FORBIDDEN));
        return new ProjectAccess(project, membership.getRole(), false, membership.getId());
    }

    /**
     * Requires management rights: project OWNER or ADMIN. A non-member caller
     * without ADMIN fails with the management-forbidden contract of the
     * update/delete endpoints.
     */
    @Transactional(readOnly = true)
    public ProjectAccess requireOwner(UUID projectId, CustomUserPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Project project = requireProject(projectId);
        if (principal.getSystemRole() == SystemRole.ADMIN) {
            return ProjectAccess.adminOverride(project);
        }
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, principal.getUserId())
                .filter(member -> member.getRole() == ProjectRole.OWNER)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));
        return new ProjectAccess(project, membership.getRole(), false, membership.getId());
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    /**
     * Result of an authorization check. {@code role} is null for the ADMIN
     * override and must never be rendered as a fake project role.
     */
    public record ProjectAccess(Project project, ProjectRole role, boolean adminOverride,
            UUID membershipId) {

        /** Compatibility constructor for Core callers that do not need continuity state. */
        public ProjectAccess(Project project, ProjectRole role, boolean adminOverride) {
            this(project, role, adminOverride, null);
        }

        static ProjectAccess adminOverride(Project project) {
            return new ProjectAccess(project, null, true, null);
        }

        public boolean hasRole(ProjectRole expected) {
            return role == expected;
        }
    }
}
