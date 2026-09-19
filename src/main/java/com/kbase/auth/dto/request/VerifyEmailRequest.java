package com.kbase.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Email verification request with the 6-digit OTP delivered by email. */
public record VerifyEmailRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must contain at most 254 characters")
        String email,

        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "\\d{6}", message = "Verification code must contain 6 digits")
        @Schema(description = "6-digit email verification OTP delivered through Gmail SMTP; used only for "
                + "registration email verification, never returned by the API")
        String otp) {
}
