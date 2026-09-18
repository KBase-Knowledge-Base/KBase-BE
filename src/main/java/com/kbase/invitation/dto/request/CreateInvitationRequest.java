package com.kbase.invitation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitation creation request. The email is normalized before storage. */
public record CreateInvitationRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must contain at most 254 characters")
        String email) {
}
