package com.kbase.project.service;

import java.util.Objects;
import java.util.UUID;

import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.dto.request.UpdateProjectRequest;
import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectMembershipProjection;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.repository.ProjectSpecification;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.InfrastructureException;
import com.kbase.storage.exception.StorageException;
import com.kbase.storage.exception.StorageUnavailableException;
import com.kbase.storage.service.StorageService;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.user.entity.User;
import com.kbase.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project lifecycle management, including the M11 storage-first hard delete.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    @Autowired
    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService authorizationService,
            UserRepository userRepository,
            DocumentRepository documentRepository,
            StorageService storageService) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository");
        this.projectMemberRepository =
                Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.storageService = Objects.requireNonNull(storageService, "storageService");
    }

    /** Compatibility constructor retained for existing focused unit tests that do not exercise M11 delete. */
    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
            ProjectAuthorizationService authorizationService, UserRepository userRepository) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.documentRepository = null;
        this.storageService = null;
    }

    /** Creates the project and the creator's OWNER membership atomically. */
    @Transactional
    public ProjectResponse createProject(UUID currentUserId, CreateProjectRequest request) {
        Objects.requireNonNull(request, "request");
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Project project = new Project(request.name().trim(), request.description());
        project = projectRepository.save(project);
        ProjectMember membership = projectMemberRepository.save(
                new ProjectMember(project, creator, ProjectRole.OWNER));

        // One transaction: if the OWNER membership insert fails, the project
        // row rolls back with it. The DB partial unique index guarantees a
        // single OWNER per project.
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                membership.getRole(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    /** Lists only the caller's membership projects, regardless of system role. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listMyProjects(
            UUID currentUserId, ProjectRole role, String q, Pageable pageable) {
        // Empty sentinel instead of null: PostgreSQL cannot infer the type of
        // a null string parameter in the OR-filtered JPQL predicate.
        String query = q == null || q.isBlank() ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        Page<ProjectMembershipProjection> page = projectMemberRepository.findMembershipPageForUser(
                currentUserId, role, query, remapProjectSort(pageable));
        return PageResponse.from(page, membership -> toResponse(
                membership.getProject(), membership.getRole()));
    }

    /**
     * The membership query roots at ProjectMember, so the public sort fields
     * (name/createdAt/updatedAt) map onto the joined project path.
     */
    private static Pageable remapProjectSort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> switch (order.getProperty()) {
                    case "name" -> order.withProperty("project.name");
                    case "createdAt" -> order.withProperty("project.createdAt");
                    case "updatedAt" -> order.withProperty("project.updatedAt");
                    default -> order.withProperty("project." + order.getProperty());
                })
                .toList());
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, CustomUserPrincipal principal) {
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireProjectAccess(projectId, principal);
        return toResponse(access.project(), access.role());
    }

    @Transactional
    public ProjectResponse updateProject(
            UUID projectId, UpdateProjectRequest request, CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireOwner(projectId, principal);
        Project project = access.project();
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Project name must not be blank.");
            }
            project.setName(name);
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        return toResponse(project, access.role());
    }

    /**
     * Uses PostgreSQL's storage-key projection as the deletion source. A failed
     * or partially failed storage delete deliberately prevents the DB cascade.
     */
    @Transactional
    public void deleteProject(UUID projectId, CustomUserPrincipal principal) {
        var access = authorizationService.requireOwner(projectId, principal);
        try {
            storageService.deleteAll(documentRepository.findStorageKeysByProjectId(projectId).stream()
                    .map(projection -> projection.getStorageKey()).toList());
        } catch (StorageException exception) {
            throw new InfrastructureException(exception instanceof StorageUnavailableException
                    ? ErrorCode.STORAGE_SERVICE_UNAVAILABLE : ErrorCode.PROJECT_DELETE_FAILED, exception);
        }
        try {
            projectRepository.delete(access.project());
            projectRepository.flush();
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(ProjectService.class).error(
                    "Project DB delete failed after storage deletion projectId={}", projectId, exception);
            throw new InfrastructureException(ErrorCode.PROJECT_DELETE_FAILED, exception);
        }
    }

    /** Admin-only listing of every project; roles are not invented. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> adminListProjects(String q, UUID ownerId, Pageable pageable) {
        Page<Project> page = projectRepository.findAll(ProjectSpecification.withFilters(q, ownerId), pageable);
        return PageResponse.from(page, project -> toResponse(project, null));
    }

    private static ProjectResponse toResponse(Project project, ProjectRole currentUserRole) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                currentUserRole,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
