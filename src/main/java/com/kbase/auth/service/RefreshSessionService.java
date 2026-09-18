package com.kbase.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.kbase.auth.entity.RefreshSession;
import com.kbase.auth.repository.RefreshSessionRepository;
import com.kbase.config.properties.JwtProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed refresh-session lifecycle. Raw refresh tokens exist only
 * as the return value handed to the controller for the client cookie; only
 * the SHA-256 hash is persisted.
 */
@Service
public class RefreshSessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshSessionRepository repository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public RefreshSessionService(RefreshSessionRepository repository, JwtProperties jwtProperties) {
        this(repository, jwtProperties, Clock.systemUTC(), new SecureRandom());
    }

    public RefreshSessionService(
            RefreshSessionRepository repository,
            JwtProperties jwtProperties,
            Clock clock,
            SecureRandom secureRandom) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        if (jwtProperties.getRefreshTokenTtl() == null
                || jwtProperties.getRefreshTokenTtl().isZero()
                || jwtProperties.getRefreshTokenTtl().isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
    }

    /** Creates a session and returns the raw token exactly once. */
    @Transactional
    public CreatedRefreshSession createSession(User user) {
        Objects.requireNonNull(user, "user");
        String rawToken = generateRawToken();
        RefreshSession session = new RefreshSession(
                user,
                hashToken(rawToken),
                clock.instant().plus(jwtProperties.getRefreshTokenTtl()));
        session = repository.save(session);
        return new CreatedRefreshSession(rawToken, session.getId(), session.getExpiresAt());
    }

    /** Validates existence, revocation state and expiry, in that order. */
    @Transactional(readOnly = true)
    public RefreshSession validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshSession session = repository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (session.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.REFRESH_SESSION_REVOKED);
        }
        if (!session.getExpiresAt().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return session;
    }

    /** Effectively idempotent: missing or already-revoked tokens do nothing. */
    @Transactional
    public void revokeIfPresent(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hashToken(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> session.setRevokedAt(clock.instant()));
    }

    /** Revokes every active session for the user (password change, disable). */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        Instant now = clock.instant();
        repository.findAllByUserId(userId).stream()
                .filter(session -> session.getRevokedAt() == null)
                .forEach(session -> session.setRevokedAt(now));
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the JDK; an unavailable primitive is a
            // programming/deployment failure, never a client-visible error.
            throw new IllegalStateException("Refresh token hash primitive is unavailable", exception);
        }
    }

    /** One-time hand-off of the raw token from service to controller. */
    public record CreatedRefreshSession(String rawToken, UUID sessionId, Instant expiresAt) {

        @Override
        public String toString() {
            return "CreatedRefreshSession{sessionId=" + sessionId
                    + ", expiresAt=" + expiresAt + ", rawToken=[REDACTED]}";
        }
    }
}
