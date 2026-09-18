package com.kbase.invitation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.invitation.enums.InvitationStatus;

/**
 * Invitation representation. The raw invitation token and its hash are never
 * part of this view; the raw token travels only inside the email link.
 */
public record InvitationResponse(
        UUID id,
        UUID projectId,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        Instant createdAt) {
}
