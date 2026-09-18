package com.kbase.auth.dto.response;

/** Successful email verification result. */
public record VerifyEmailResponse(
        String email,
        boolean emailVerified) {
}
