package com.kbase.auth.dto.response;

/** New access token issued by the refresh flow. */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
