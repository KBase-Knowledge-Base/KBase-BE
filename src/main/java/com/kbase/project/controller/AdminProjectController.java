package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.project.dto.response.ProjectResponse;
import com.kbase.project.service.ProjectService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin project listing. Route protection (ADMIN) lives in SecurityConfig. */
@Tag(name = OpenApiConfig.TAG_ADMIN_PROJECTS,
        description = "System-wide project listing. Requires SystemRole.ADMIN.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
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

    @Operation(summary = "List all projects",
            description = "Requires SystemRole.ADMIN. Lists every project in the system with owner filtering. "
                    + "Memberships of the calling ADMIN are not implied; currentUserRole stays null for projects the "
                    + "ADMIN is not a member of. Sortable fields: name, createdAt, updatedAt (default createdAt,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged projects")
    })
    @GetMapping
    public ResponseEntity<PageResponse<ProjectResponse>> listAllProjects(
            @Parameter(description = "Case-insensitive search across project name")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by the OWNER membership user id")
            @RequestParam(name = "ownerId", required = false) UUID ownerId,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: name, createdAt, updatedAt")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectService.adminListProjects(q, ownerId, pageable));
    }
}
