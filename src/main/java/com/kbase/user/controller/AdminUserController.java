package com.kbase.user.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.user.dto.request.ChangeUserStatusRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) UserStatus status,
            @RequestParam(name = "systemRole", required = false) SystemRole systemRole,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(userService.listUsers(q, status, systemRole, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(userId, request.status()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
