package com.kbase.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login response. Only the short-lived access token is returned in the body;
 * the refresh token travels exclusively in the HttpOnly cookie.
 */
public record LoginResponse(
        @Schema(description = "JWT access token to send as the Authorization: Bearer header")
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
