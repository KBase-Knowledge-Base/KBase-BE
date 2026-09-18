package com.kbase.invitation.controller;

import com.kbase.invitation.dto.request.AcceptInvitationRequest;
import com.kbase.invitation.dto.response.AcceptInvitationResponse;
import com.kbase.invitation.service.InvitationService;
import com.kbase.security.service.CurrentUserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated invitation acceptance. The raw token arrives from the email
 * link; the acceptance never uses OTP and runs under a pessimistic lock.
 */
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

    @PostMapping("/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(invitationService.accept(
                request.token(), currentUserService.requirePrincipal()));
    }
}
