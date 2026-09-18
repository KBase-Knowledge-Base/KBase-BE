package com.kbase.invitation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.enums.ProjectRole;

/** Successful acceptance result. */
public record AcceptInvitationResponse(
        UUID projectId,
        UUID membershipId,
        ProjectRole role,
        Instant joinedAt) {
}
