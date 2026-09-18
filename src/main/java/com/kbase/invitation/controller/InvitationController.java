package com.kbase.invitation.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.invitation.dto.request.CreateInvitationRequest;
import com.kbase.invitation.dto.response.InvitationResponse;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.service.InvitationService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateInvitationRequest request) {
        InvitationResponse response = invitationService.createInvitation(
                projectId, request, currentUserService.requirePrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<InvitationResponse>> listInvitations(
            @PathVariable UUID projectId,
            @RequestParam(name = "status", required = false) InvitationStatus status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return ResponseEntity.ok(invitationService.listInvitations(
                projectId, status, currentUserService.requirePrincipal(), pageable));
    }

    @PostMapping("/{invitationId}/resend")
    public ResponseEntity<InvitationResponse> resendInvitation(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId) {
        return ResponseEntity.ok(invitationService.resend(
                projectId, invitationId, currentUserService.requirePrincipal()));
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable UUID projectId,
            @PathVariable UUID invitationId) {
        invitationService.cancel(projectId, invitationId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
