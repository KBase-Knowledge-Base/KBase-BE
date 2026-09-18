package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.dto.request.UpdateProjectRequest;
import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project APIs for the current user. Listing is strictly membership-scoped;
 * ADMIN sees all projects only through the separate admin endpoint. Project
 * hard delete arrives with the MinIO-integrated milestone.
 */
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

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(
                currentUserService.requireUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProjectResponse>> listMyProjects(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) ProjectRole role,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectService.listMyProjects(
                currentUserService.requireUserId(), role, q, pageable));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.getProject(
                projectId, currentUserService.requirePrincipal()));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(
                projectId, request, currentUserService.requirePrincipal()));
    }
}
