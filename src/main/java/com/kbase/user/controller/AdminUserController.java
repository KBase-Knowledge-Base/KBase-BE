package com.kbase.user.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.shared.response.ApiErrorResponse;
import com.kbase.user.dto.request.ChangeUserStatusRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.service.UserService;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin user management. Route protection (ADMIN) lives in SecurityConfig. */
@Tag(name = OpenApiConfig.TAG_ADMIN_USERS,
        description = "System-wide user management. Requires SystemRole.ADMIN; the ADMIN role is a system role, "
                + "not a project role.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private static final List<String> SORTABLE_FIELDS =
            List.of("email", "displayName", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final UserService userService;
    private final PaginationParser paginationParser;

    public AdminUserController(UserService userService, PaginationParser paginationParser) {
        this.userService = userService;
        this.paginationParser = paginationParser;
    }

    @Operation(summary = "List users",
            description = "Requires SystemRole.ADMIN. Searches and lists every user in the system. Sortable fields: "
                    + "email, displayName, createdAt, updatedAt (default createdAt,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged users")
    })
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @Parameter(description = "Case-insensitive search across email and display name")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by account status")
            @RequestParam(name = "status", required = false) UserStatus status,
            @Parameter(description = "Filter by system role")
            @RequestParam(name = "systemRole", required = false) SystemRole systemRole,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: email, displayName, createdAt, updatedAt")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(userService.listUsers(q, status, systemRole, pageable));
    }

    @Operation(summary = "Get user by id",
            description = "Requires SystemRole.ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User details"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @Operation(summary = "Change user status",
            description = "Requires SystemRole.ADMIN. Switches a user between ACTIVE and DISABLED. Disabling revokes "
                    + "all refresh sessions; the JWT filter reloads the user on every request, so existing access "
                    + "tokens stop working immediately for a disabled account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "400", description = "INVALID_USER_STATUS — the status value is not ACTIVE/DISABLED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(userId, request.status()));
    }

    @Operation(summary = "Delete user",
            description = "Requires SystemRole.ADMIN. Hard-deletes a user. A user who still owns a project or other "
                    + "dependent resources is rejected instead of cascading into project knowledge.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted; no response body"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "USER_OWNS_PROJECT — the user still owns a project, or "
                    + "USER_HAS_DEPENDENCIES — the user still has dependent resources",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
