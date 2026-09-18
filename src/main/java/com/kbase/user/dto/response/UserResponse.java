package com.kbase.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Safe user profile projection. The password hash is never exposed and
 * {@code emailVerified} is the derived API value of {@code emailVerifiedAt}.
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        SystemRole systemRole,
        UserStatus status,

        @JsonProperty("emailVerified")
        boolean emailVerified,

        Instant createdAt,
        Instant updatedAt) {
}
