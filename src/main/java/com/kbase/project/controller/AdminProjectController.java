package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.service.ProjectService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin project listing. Route protection (ADMIN) lives in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/projects")
public class AdminProjectController {

    private static final List<String> SORTABLE_FIELDS =
            List.of("name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProjectService projectService;
    private final PaginationParser paginationParser;

    public AdminProjectController(ProjectService projectService, PaginationParser paginationParser) {
        this.projectService = projectService;
        this.paginationParser = paginationParser;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProjectResponse>> listAllProjects(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectService.adminListProjects(q, ownerId, pageable));
    }
}
