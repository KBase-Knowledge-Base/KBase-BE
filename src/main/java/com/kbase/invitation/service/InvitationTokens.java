package com.kbase.invitation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generic invitation-token primitive: high-entropy opaque token plus a
 * deterministic SHA-256 hash for server-side storage. Domain rules live in
 * the invitation service; raw tokens are never persisted.
 */
@Component
public class InvitationTokens {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom;

    public InvitationTokens() {
        this(new SecureRandom());
    }

    public InvitationTokens(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /** Generates a URL-safe opaque token suitable for email links. */
    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /** Deterministic hash persisted instead of the raw token. */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the JDK; an unavailable primitive is a
            // programming/deployment failure, never a client-visible error.
            throw new IllegalStateException("Invitation token hash primitive is unavailable", exception);
        }
    }
}
