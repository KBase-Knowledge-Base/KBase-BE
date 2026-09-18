package com.kbase.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password change request; the current password must be verified first. */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @Schema(format = "password")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 64, message = "New password must contain between 8 and 64 characters")
        @Schema(format = "password")
        String newPassword) {
}
