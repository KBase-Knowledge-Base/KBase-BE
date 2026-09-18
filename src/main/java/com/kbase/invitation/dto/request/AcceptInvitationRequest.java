package com.kbase.invitation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Accept request carrying the raw invitation token from the email link. */
public record AcceptInvitationRequest(

        @NotBlank(message = "Invitation token is required")
        @Size(max = 512, message = "Invitation token is too long")
        @Schema(description = "Raw invitation token from the invitation email link; separate from the "
                + "registration email-verification OTP")
        String token) {
}
