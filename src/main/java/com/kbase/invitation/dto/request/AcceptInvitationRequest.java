package com.kbase.invitation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Accept request carrying the raw invitation token from the email link. */
public record AcceptInvitationRequest(

        @NotBlank(message = "Invitation token is required")
        @Size(max = 512, message = "Invitation token is too long")
        String token) {
}
