package com.kbase.user.controller;

import com.kbase.security.service.CurrentUserService;
import com.kbase.user.dto.request.ChangePasswordRequest;
import com.kbase.user.dto.request.UpdateProfileRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user profile and password endpoints. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser(currentUserService.requireUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                userService.updateProfile(currentUserService.requireUserId(), request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUserService.requireUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
