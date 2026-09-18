package com.kbase.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.dto.request.UpdateProjectRequest;
import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentStorageKeyProjection;
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;
import com.kbase.storage.service.StorageService;
import com.kbase.storage.exception.StorageDeleteException;
import com.kbase.shared.exception.KBaseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectMemberRepository projectMemberRepository;
    private ProjectAuthorizationService authorizationService;
    private UserRepository userRepository;
    private ProjectService service;
    private User creator;
    private CustomUserPrincipal principal;
    private Project project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        authorizationService = mock(ProjectAuthorizationService.class);
        userRepository = mock(UserRepository.class);
        service = new ProjectService(
                projectRepository, projectMemberRepository, authorizationService, userRepository);

        creator = new User("user@example.com", "hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        creator.setId(UUID.randomUUID());
        principal = new CustomUserPrincipal(
                creator.getId(), creator.getEmail(), SystemRole.USER, UserStatus.ACTIVE, true);
        project = new Project("KBase Project", "desc");
        project.setId(UUID.randomUUID());
    }

    @Test
    void createProjectPersistsProjectAndOwnerMembershipAtomically() {
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = service.createProject(
                creator.getId(), new CreateProjectRequest("KBase Project", "desc"));

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(captor.capture());
        ProjectMember persisted = captor.getValue();
        assertThat(persisted.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(persisted.getUser()).isSameAs(creator);
        assertThat(persisted.getProject().getId()).isEqualTo(response.id());

        assertThat(response.currentUserRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(response.name()).isEqualTo("KBase Project");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void getProjectMapsMembershipRoleAndAdminOverrideRole() {
        when(authorizationService.requireProjectAccess(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        assertThat(service.getProject(project.getId(), principal).currentUserRole())
                .isEqualTo(ProjectRole.MEMBER);

        // ADMIN override carries a null role, never a fake ADMIN project role.
        when(authorizationService.requireProjectAccess(project.getId(), principal))
                .thenReturn(ProjectAccess.adminOverride(project));
        ProjectResponse adminView = service.getProject(project.getId(), principal);
        assertThat(adminView.currentUserRole()).isNull();
        assertThat(adminView.id()).isEqualTo(project.getId());
    }

    @Test
    void hardDeleteUsesDbKeyProjectionThenStorageThenProjectAndStopsOnStorageFailure() {
        DocumentRepository documents = mock(DocumentRepository.class);
        StorageService storage = mock(StorageService.class);
        ProjectService deletingService = new ProjectService(projectRepository, projectMemberRepository,
                authorizationService, userRepository, documents, storage);
        when(authorizationService.requireOwner(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        DocumentStorageKeyProjection first = mock(DocumentStorageKeyProjection.class);
        DocumentStorageKeyProjection second = mock(DocumentStorageKeyProjection.class);
        when(first.getStorageKey()).thenReturn("projects/a/documents/one.pdf");
        when(second.getStorageKey()).thenReturn("projects/a/documents/two.pdf");
        when(documents.findStorageKeysByProjectId(project.getId())).thenReturn(java.util.List.of(first, second));

        deletingService.deleteProject(project.getId(), principal);
        verify(storage).deleteAll(java.util.List.of("projects/a/documents/one.pdf", "projects/a/documents/two.pdf"));
        verify(projectRepository).delete(project);

        org.mockito.Mockito.reset(projectRepository, storage);
        when(authorizationService.requireOwner(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));
        when(documents.findStorageKeysByProjectId(project.getId())).thenReturn(java.util.List.of());
        org.mockito.Mockito.doThrow(new StorageDeleteException("failed", new RuntimeException()))
                .when(storage).deleteAll(any());
        assertThatThrownBy(() -> deletingService.deleteProject(project.getId(), principal))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode()).isEqualTo(ErrorCode.PROJECT_DELETE_FAILED);
        verify(projectRepository, never()).delete(org.mockito.ArgumentMatchers.<Project>any());
    }

    @Test
    void updateProjectAppliesChangesOnlyThroughOwnerAuthorization() {
        when(authorizationService.requireOwner(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.OWNER, false));

        ProjectResponse updated = service.updateProject(project.getId(),
                new UpdateProjectRequest("Updated Name", "New desc"), principal);
        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(project.getName()).isEqualTo("Updated Name");
        assertThat(project.getDescription()).isEqualTo("New desc");
        assertThat(updated.currentUserRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void updateProjectRejectsMemberAuthorizationErrorsAndBlankName() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN))
                .when(authorizationService).requireOwner(project.getId(), principal);

        assertThatThrownBy(() -> service.updateProject(project.getId(),
                new UpdateProjectRequest("New Name", null), principal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));
        verify(projectRepository, never()).save(any(Project.class));

        org.mockito.Mockito.doReturn(new ProjectAccess(project, ProjectRole.OWNER, false))
                .when(authorizationService).requireOwner(project.getId(), principal);
        assertThatThrownBy(() -> service.updateProject(project.getId(),
                new UpdateProjectRequest("   ", null), principal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(project.getName()).isEqualTo("KBase Project");
    }

    @Test
    void adminListingMapsProjectsWithoutInventingRoles() {
        Pageable pageable = PageRequest.of(0, 20);
        when(projectRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(project)));

        PageResponse<ProjectResponse> response = service.adminListProjects(null, null, pageable);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).currentUserRole()).isNull();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isZero();
    }
}
