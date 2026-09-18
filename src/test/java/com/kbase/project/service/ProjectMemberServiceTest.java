package com.kbase.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectMemberServiceTest {

    private ProjectMemberRepository projectMemberRepository;
    private ProjectAuthorizationService authorizationService;
    private ProjectMemberService service;

    private UUID projectId;
    private Project project;
    private CustomUserPrincipal ownerPrincipal;
    private CustomUserPrincipal memberPrincipal;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        projectMemberRepository = mock(ProjectMemberRepository.class);
        authorizationService = mock(ProjectAuthorizationService.class);
        service = new ProjectMemberService(projectMemberRepository, authorizationService);

        projectId = UUID.randomUUID();
        project = new Project("KBase Project", "desc");
        project.setId(projectId);
        ownerIdAndPrincipals();
    }

    private UUID ownerId;
    private UUID adminId;

    private void ownerIdAndPrincipals() {
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        ownerPrincipal = new CustomUserPrincipal(
                ownerId, "owner@example.com", SystemRole.USER, UserStatus.ACTIVE, true);
        memberPrincipal = new CustomUserPrincipal(
                memberId, "member@example.com", SystemRole.USER, UserStatus.ACTIVE, true);
    }

    private ProjectMember membership(UUID userId, ProjectRole role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        ProjectMember member = mock(ProjectMember.class);
        when(member.getRole()).thenReturn(role);
        when(member.getUser()).thenReturn(user);
        when(member.getProject()).thenReturn(project);
        return member;
    }

    @Test
    void listMembersRequiresAccessAndMapsMembershipRows() {
        when(authorizationService.requireProjectAccess(projectId, memberPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        ProjectMember membership = membership(memberId, ProjectRole.MEMBER);
        when(projectMemberRepository.findAllByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(membership)));

        var response = service.listMembers(projectId, memberPrincipal,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).userId()).isEqualTo(memberId);
        assertThat(response.content().get(0).role()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    void removeMemberRequiresOwnerAndProtectsProjectOwner() {
        when(authorizationService.requireOwner(projectId, ownerPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        UUID targetId = UUID.randomUUID();
        ProjectMember target = membership(targetId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, targetId))
                .thenReturn(Optional.of(target));

        service.removeMember(projectId, targetId, ownerPrincipal);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProjectMember> captor =
                ArgumentCaptor.forClass((Class<ProjectMember>) (Class<?>) ProjectMember.class);
        verify(projectMemberRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(target);

        // The project OWNER membership is untouchable, even by an ADMIN override.
        CustomUserPrincipal adminPrincipal = new CustomUserPrincipal(
                adminId, "admin@example.com", SystemRole.ADMIN, UserStatus.ACTIVE, true);
        when(authorizationService.requireOwner(projectId, adminPrincipal))
                .thenReturn(ProjectAccess.adminOverride(project));
        UUID ownerIdValue = ownerId;
        ProjectMember ownerMembership = membership(ownerIdValue, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerIdValue))
                .thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> service.removeMember(projectId, ownerIdValue, adminPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_OWNER_REMOVAL_FORBIDDEN));
    }

    @Test
    void removeMemberRejectsMissingMembership() {
        when(authorizationService.requireOwner(projectId, ownerPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        UUID unknown = UUID.randomUUID();
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, unknown))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(projectId, unknown, ownerPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MEMBER_NOT_FOUND));
    }

    @Test
    void leaveProjectAllowsMemberAndRejectsOwner() {
        when(authorizationService.requireProjectAccess(projectId, memberPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        ProjectMember memberMembership = membership(memberId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        service.leaveProject(projectId, memberPrincipal);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProjectMember> captor =
                ArgumentCaptor.forClass((Class<ProjectMember>) (Class<?>) ProjectMember.class);
        verify(projectMemberRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(memberMembership);

        when(authorizationService.requireProjectAccess(projectId, ownerPrincipal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        ProjectMember ownerMembership = membership(ownerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> service.leaveProject(projectId, ownerPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.OWNER_CANNOT_LEAVE_PROJECT));
    }

    @Test
    void adminWithoutMembershipLeavingMapsToMemberNotFound() {
        CustomUserPrincipal adminPrincipal = new CustomUserPrincipal(
                adminId, "admin@example.com", SystemRole.ADMIN, UserStatus.ACTIVE, true);
        when(authorizationService.requireProjectAccess(projectId, adminPrincipal))
                .thenReturn(ProjectAccess.adminOverride(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveProject(projectId, adminPrincipal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MEMBER_NOT_FOUND));
    }
}
