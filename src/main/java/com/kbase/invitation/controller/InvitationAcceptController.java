package com.kbase.invitation.controller;

import com.kbase.config.OpenApiConfig;
import com.kbase.invitation.dto.request.AcceptInvitationRequest;
import com.kbase.invitation.dto.response.AcceptInvitationResponse;
import com.kbase.invitation.service.InvitationService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated invitation acceptance. The raw token arrives from the email
 * link; the acceptance never uses OTP and runs under a pessimistic lock.
 */
@Tag(name = OpenApiConfig.TAG_INVITATIONS,
        description = "Accepting a project invitation with the raw token from the invitation email link. "
                + "The invitation token is separate from the registration email-verification OTP.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationAcceptController {

    private final InvitationService invitationService;
    private final CurrentUserService currentUserService;

    public InvitationAcceptController(InvitationService invitationService,
            CurrentUserService currentUserService) {
        this.invitationService = invitationService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Accept project invitation",
            description = "Authenticated ACTIVE user required; their normalized account email must equal the "
                    + "invitation email. The invitation must be PENDING and not expired. Creates a MEMBER membership "
                    + "and marks the invitation ACCEPTED. Concurrent accepts are serialized so only one succeeds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted; MEMBER membership created"),
            @ApiResponse(responseCode = "403", description = "INVITATION_EMAIL_MISMATCH — the account email differs from the invitation email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND — the token does not match any invitation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING — already accepted or cancelled, "
                    + "INVITATION_EXPIRED — past the expiry, or PROJECT_MEMBER_ALREADY_EXISTS — already a member",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(invitationService.accept(
                request.token(), currentUserService.requirePrincipal()));
    }
}
