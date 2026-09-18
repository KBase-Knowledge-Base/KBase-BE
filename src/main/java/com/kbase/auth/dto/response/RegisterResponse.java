package com.kbase.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

/** Registration result. The account is always created unverified. */
public record RegisterResponse(
        UUID id,
        String email,
        String displayName,
        SystemRole systemRole,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt) {
}
