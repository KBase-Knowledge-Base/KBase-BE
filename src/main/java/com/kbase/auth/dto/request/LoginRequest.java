package com.kbase.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login request. Failure responses never reveal whether the email exists. */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must contain at most 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(format = "password")
        String password) {
}
