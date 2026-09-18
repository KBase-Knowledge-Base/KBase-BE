package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.project.dto.response.ProjectMemberResponse;
import com.kbase.project.service.ProjectMemberService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project membership APIs. Removal requires OWNER/ADMIN; leaving requires a
 * non-OWNER membership. Uploaded documents are never deleted here.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private static final List<String> SORTABLE_FIELDS =
            List.of("joinedAt", "email", "displayName");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "joinedAt");

    private final ProjectMemberService projectMemberService;
    private final CurrentUserService currentUserService;
    private final PaginationParser paginationParser;

    public ProjectMemberController(
            ProjectMemberService projectMemberService,
            CurrentUserService currentUserService,
            PaginationParser paginationParser) {
        this.projectMemberService = projectMemberService;
        this.currentUserService = currentUserService;
        this.paginationParser = paginationParser;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProjectMemberResponse>> listMembers(
            @PathVariable UUID projectId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectMemberService.listMembers(
                projectId, currentUserService.requirePrincipal(), pageable));
    }

    /** Literal path wins over {@code {userId}}; OWNER leaving is rejected. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> leaveProject(@PathVariable UUID projectId) {
        projectMemberService.leaveProject(projectId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        projectMemberService.removeMember(
                projectId, userId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
