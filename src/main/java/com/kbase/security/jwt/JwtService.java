package com.kbase.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import com.kbase.config.properties.JwtProperties;
import com.kbase.user.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Signs and validates short-lived access JWTs. This service never performs
 * project/document authorization and never persists tokens.
 */
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;
    private final Clock clock;

    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        validateProperties(properties);
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getSigningSecret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /** Issues an access token whose subject is the stable user ID. */
    public IssuedAccessToken generateAccessToken(User user) {
        Objects.requireNonNull(user, "user");
        Instant now = clock.instant();
        Duration ttl = properties.getAccessTokenTtl();
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("systemRole", user.getSystemRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, ttl.toSeconds());
    }

    /** Verifies signature and expiry; throws JJWT exceptions on failure. */
    public Claims parseAndValidate(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public boolean isExpiredToken(Throwable exception) {
        return exception instanceof ExpiredJwtException;
    }

    private static void validateProperties(JwtProperties properties) {
        Objects.requireNonNull(properties.getSigningSecret(), "signingSecret");
        if (properties.getSigningSecret().isBlank()) {
            throw new IllegalArgumentException("JWT signing secret is blank");
        }
        if (properties.getAccessTokenTtl() == null || properties.getAccessTokenTtl().isZero()
                || properties.getAccessTokenTtl().isNegative()) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
    }

    /** Hand-off value containing the signed token and its lifetime in seconds. */
    public record IssuedAccessToken(String token, long expiresInSeconds) {
    }
}
