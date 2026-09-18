package com.kbase.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Resend request for the registration verification OTP. */
public record ResendVerificationOtpRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must contain at most 254 characters")
        String email) {
}
