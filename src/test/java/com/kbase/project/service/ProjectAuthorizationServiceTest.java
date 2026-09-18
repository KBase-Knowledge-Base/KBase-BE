package com.kbase.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectAuthorizationServiceTest {

    private ProjectRepository projectRepository;
    private ProjectMemberRepository projectMemberRepository;
    private ProjectAuthorizationService service;
    private Project project;
    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        service = new ProjectAuthorizationService(projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        project = new Project("KBase Project", "desc");
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    private CustomUserPrincipal principal(SystemRole systemRole) {
        return new CustomUserPrincipal(userId, "user@example.com", systemRole, UserStatus.ACTIVE, true);
    }

    private ProjectMember membership(ProjectRole role) {
        ProjectMember member = mock(ProjectMember.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }

    @Test
    void missingProjectMapsToProjectNotFound() {
        UUID unknown = UUID.randomUUID();
        when(projectRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireProjectAccess(unknown, principal(SystemRole.USER)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        assertThatThrownBy(() -> service.requireOwner(unknown, principal(SystemRole.ADMIN)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void adminOverrideReturnsAccessWithoutMembershipOrFakeRole() {
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.empty());

        ProjectAccess access = service.requireProjectAccess(projectId, principal(SystemRole.ADMIN));
        assertThat(access.project()).isSameAs(project);
        assertThat(access.role()).isNull();
        assertThat(access.adminOverride()).isTrue();

        ProjectAccess ownerAccess = service.requireOwner(projectId, principal(SystemRole.ADMIN));
        assertThat(ownerAccess.adminOverride()).isTrue();
        assertThat(ownerAccess.role()).isNull();
    }

    @Test
    void memberAccessReturnsCurrentRoleAndNonMemberIsForbidden() {
        ProjectMember member = membership(ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.of(member));

        ProjectAccess access = service.requireProjectAccess(projectId, principal(SystemRole.USER));
        assertThat(access.hasRole(ProjectRole.MEMBER)).isTrue();
        assertThat(access.adminOverride()).isFalse();

        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireProjectAccess(projectId, principal(SystemRole.USER)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_ACCESS_FORBIDDEN));
    }

    @Test
    void requireOwnerAllowsOwnerAndRejectsMemberAndNonMember() {
        ProjectMember ownerMembership = membership(ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.of(ownerMembership));
        assertThat(service.requireOwner(projectId, principal(SystemRole.USER))
                .hasRole(ProjectRole.OWNER)).isTrue();

        ProjectMember plainMembership = membership(ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.of(plainMembership));
        assertThatThrownBy(() -> service.requireOwner(projectId, principal(SystemRole.USER)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));

        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireOwner(projectId, principal(SystemRole.USER)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));
    }
}
