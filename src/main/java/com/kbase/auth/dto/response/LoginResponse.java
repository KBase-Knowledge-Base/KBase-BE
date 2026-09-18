package com.kbase.auth.dto.response;

/**
 * Login response. Only the short-lived access token is returned in the body;
 * the refresh token travels exclusively in the HttpOnly cookie.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
