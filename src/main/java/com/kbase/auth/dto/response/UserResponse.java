package com.kbase.auth.dto.response;

import java.util.UUID;

import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Safe user projection. Password hash and verification timestamp are never
 * exposed; {@code emailVerified} is the derived API value.
 */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        SystemRole systemRole,
        UserStatus status,

        @JsonProperty("emailVerified")
        boolean emailVerified) {
}
