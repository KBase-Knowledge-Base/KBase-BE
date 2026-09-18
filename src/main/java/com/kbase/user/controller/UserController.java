package com.kbase.user.controller;

import com.kbase.config.OpenApiConfig;
import com.kbase.shared.response.ApiErrorResponse;
import com.kbase.security.service.CurrentUserService;
import com.kbase.user.dto.request.ChangePasswordRequest;
import com.kbase.user.dto.request.UpdateProfileRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user profile and password endpoints. */
@Tag(name = OpenApiConfig.TAG_USERS, description = "Profile and password of the authenticated user.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Get current user profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — the account no longer exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser(currentUserService.requireUserId()));
    }

    @Operation(summary = "Update current user profile",
            description = "Only displayName is user-modifiable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated profile"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — displayName is blank or too long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — the account no longer exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                userService.updateProfile(currentUserService.requireUserId(), request));
    }

    @Operation(summary = "Change password",
            description = "Verifies the current password, stores the new hash and revokes all refresh sessions of "
                    + "the user, so other sessions must log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; refresh sessions revoked"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — the new password does not meet the length rules",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "CURRENT_PASSWORD_INVALID — the current password is wrong",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUserService.requireUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
