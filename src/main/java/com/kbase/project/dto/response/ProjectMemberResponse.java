package com.kbase.project.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.enums.ProjectRole;

/** Membership listing entry. Documents ownership is not part of this view. */
public record ProjectMemberResponse(
        UUID membershipId,
        UUID userId,
        String email,
        String displayName,
        ProjectRole role,
        Instant joinedAt) {
}
