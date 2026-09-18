package com.kbase.invitation.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.invitation.dto.request.CreateInvitationRequest;
import com.kbase.invitation.dto.response.InvitationResponse;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.service.InvitationService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project invitation management endpoints. OWNER/ADMIN enforced in service. */
@Tag(name = OpenApiConfig.TAG_PROJECT_INVITATIONS,
        description = "Invitations are OWNER/ADMIN managed and use a separate secure invitation token sent by email "
                + "(never the registration OTP); the raw token travels only inside the email link and PostgreSQL keeps "
                + "only its hash. Expiration is 72h by default and configurable.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/projects/{projectId}/invitations")
public class InvitationController {

    private static final List<String> SORTABLE_FIELDS =
            List.of("createdAt", "email", "status", "expiresAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final InvitationService invitationService;
    private final CurrentUserService currentUserService;
    private final PaginationParser paginationParser;

    public InvitationController(
            InvitationService invitationService,
            CurrentUserService currentUserService,
            PaginationParser paginationParser) {
        this.invitationService = invitationService;
        this.currentUserService = currentUserService;
        this.paginationParser = paginationParser;
    }

    @Operation(summary = "Create project invitation",
            description = "Sends an invitation email with a secure invitation link to the address. Requires the "
                    + "project OWNER or system ADMIN. The email must belong to an existing registered account and not "
                    + "already be a member; only one PENDING invitation may exist per project and email. The response "
                    + "and database never contain the raw token or its hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "PENDING invitation created and emailed"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — the email is invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage invitations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND — no registered account uses that email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "PROJECT_MEMBER_ALREADY_EXISTS — the email is already a member, or "
                    + "INVITATION_ALREADY_PENDING — a pending invitation exists for this email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "EMAIL_SERVICE_UNAVAILABLE — Gmail delivery failed and the "
                    + "invitation was rolled back",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateInvitationRequest request) {
        InvitationResponse response = invitationService.createInvitation(
                projectId, request, currentUserService.requirePrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List project invitations",
            description = "Requires the project OWNER or system ADMIN. "
                    + "Sortable fields: createdAt, email, status, expiresAt (default createdAt,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged invitations"),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage invitations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PageResponse<InvitationResponse>> listInvitations(
            @PathVariable UUID projectId,
            @Parameter(description = "Filter by invitation status")
            @RequestParam(name = "status", required = false) InvitationStatus status,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: createdAt, email, status, expiresAt")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(invitationService.listInvitations(
                projectId, status, currentUserService.requirePrincipal(), pageable));
    }

    @Operation(summary = "Resend invitation",
            description = "Requires the project OWNER or system ADMIN. Issues a new invitation token, invalidates the "
                    + "old link, resets the expiry and re-sends the email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token issued and emailed"),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage invitations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING — the invitation was cancelled or already accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "EMAIL_SERVICE_UNAVAILABLE — Gmail delivery failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{invitationId}/resend")
    public ResponseEntity<InvitationResponse> resendInvitation(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId) {
        return ResponseEntity.ok(invitationService.resend(
                projectId, invitationId, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Cancel invitation",
            description = "Requires the project OWNER or system ADMIN. Marks a PENDING invitation CANCELLED; the row "
                    + "is kept and the link stops working.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Invitation cancelled; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage invitations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING — the invitation was already cancelled or accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId) {
        invitationService.cancel(projectId, invitationId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
