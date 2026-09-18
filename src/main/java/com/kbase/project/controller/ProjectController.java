package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.dto.request.UpdateProjectRequest;
import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project APIs for the current user. Listing is strictly membership-scoped;
 * ADMIN sees all projects only through the separate admin endpoint.
 */
@Tag(name = OpenApiConfig.TAG_PROJECTS,
        description = "Projects of the current user. Listing is membership-only even for ADMIN; "
                + "project roles are OWNER (exactly one per project) and MEMBER, never stored in the JWT.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private static final List<String> SORTABLE_FIELDS =
            List.of("name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final PaginationParser paginationParser;

    public ProjectController(
            ProjectService projectService,
            CurrentUserService currentUserService,
            PaginationParser paginationParser) {
        this.projectService = projectService;
        this.currentUserService = currentUserService;
        this.paginationParser = paginationParser;
    }

    @Operation(summary = "Create project",
            description = "Creates the project and the single OWNER membership for the creator in one transaction. "
                    + "Ownership transfer does not exist in Core v1.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created; the creator is its OWNER"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — name or description is invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(
                currentUserService.requireUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List my projects",
            description = "Returns only the projects the caller currently belongs to. ADMIN behaves the same as any "
                    + "other user here; the system-wide listing is GET /api/v1/admin/projects. "
                    + "Sortable fields: name, createdAt, updatedAt (default createdAt,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged joined projects")
    })
    @GetMapping
    public ResponseEntity<PageResponse<ProjectResponse>> listMyProjects(
            @Parameter(description = "Case-insensitive search across project name")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by the caller's membership role (OWNER or MEMBER)")
            @RequestParam(name = "role", required = false) ProjectRole role,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: name, createdAt, updatedAt")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectService.listMyProjects(
                currentUserService.requireUserId(), role, q, pageable));
    }

    @Operation(summary = "Get project",
            description = "Access: project MEMBER, OWNER, or system ADMIN. currentUserRole is null when an ADMIN "
                    + "views a project they are not a member of; no ADMIN project role is invented.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project details"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.getProject(
                projectId, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Update project",
            description = "Updates name and/or description. Requires the project OWNER or system ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — a present-but-blank name is rejected",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — the caller is neither OWNER nor ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Delete project",
            description = "Hard delete, storage-first: all MinIO objects of the project are deleted before the "
                    + "database rows cascade. If storage deletion fails, the project is not deleted. "
                    + "Requires the project OWNER or system ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Project and all its binaries deleted; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — the caller is neither OWNER nor ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "PROJECT_DELETE_FAILED — the deletion could not complete",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable, so nothing was deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        projectService.deleteProject(projectId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
