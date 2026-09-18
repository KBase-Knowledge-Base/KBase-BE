package com.kbase.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration request. Clients can never submit role or verification state. */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must contain at most 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must contain between 8 and 64 characters")
        String password,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must contain at most 100 characters")
        String displayName) {
}
