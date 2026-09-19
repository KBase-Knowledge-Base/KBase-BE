package com.kbase.project.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.project.dto.response.ProjectMemberResponse;
import com.kbase.project.service.ProjectMemberService;
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

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
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
@Tag(name = OpenApiConfig.TAG_PROJECT_MEMBERS,
        description = "Project membership. Removing a member never deletes their uploaded documents; the project "
                + "OWNER cannot be removed or leave.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
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

    @Operation(summary = "List project members",
            description = "Access: project MEMBER, OWNER, or system ADMIN. "
                    + "Sortable fields: joinedAt, email, displayName (default joinedAt,asc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged members"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PageResponse<ProjectMemberResponse>> listMembers(
            @PathVariable UUID projectId,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: joinedAt, email, displayName")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(projectMemberService.listMembers(
                projectId, currentUserService.requirePrincipal(), pageable));
    }

    /** Literal path wins over {@code {userId}}; OWNER leaving is rejected. */
    @Operation(summary = "Leave project",
            description = "A MEMBER leaves their own membership. The project OWNER cannot leave in Core v1. "
                    + "Documents uploaded by the member remain in the project with their original uploader reference.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membership removed; documents remain; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "OWNER_CANNOT_LEAVE_PROJECT — the project OWNER cannot leave",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> leaveProject(@PathVariable UUID projectId) {
        projectMemberService.leaveProject(projectId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove project member",
            description = "Removes a MEMBER from the project. Requires the project OWNER or system ADMIN; the project "
                    + "OWNER cannot be removed, even by an ADMIN. Removed members immediately lose project access, "
                    + "but their uploaded documents remain.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed; documents remain; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN may remove members",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND or PROJECT_MEMBER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "PROJECT_OWNER_REMOVAL_FORBIDDEN — the project OWNER cannot be removed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        projectMemberService.removeMember(
                projectId, userId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
