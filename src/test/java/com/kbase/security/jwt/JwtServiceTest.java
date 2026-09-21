package com.kbase.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import com.kbase.config.properties.JwtProperties;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-signing-secret-that-is-256-bits!";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private final JwtProperties properties = jwtProperties(Duration.ofMinutes(15));

    private JwtService service(Duration accessTtl) {
        JwtProperties ttlProperties = accessTtl == Duration.ofMinutes(15)
                ? properties
                : jwtProperties(accessTtl);
        return new JwtService(ttlProperties, fixedClock());
    }

    private static JwtProperties jwtProperties(Duration accessTtl) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSigningSecret(SECRET);
        jwtProperties.setAccessTokenTtl(accessTtl);
        jwtProperties.setRefreshTokenTtl(Duration.ofDays(7));
        return jwtProperties;
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private User user() {
        User user = new User(
                "user@example.com", "password-hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return user;
    }

    @Test
    void generatesTokenWithUserIdSubjectSystemRoleAndLifetime() {
        JwtService.IssuedAccessToken issued = service(Duration.ofMinutes(15))
                .generateAccessToken(user());

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(900);

        Claims claims = service(Duration.ofMinutes(15)).parseAndValidate(issued.token());
        assertThat(claims.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(claims.get("systemRole", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plusSeconds(900)));
    }

    @Test
    void parsesValidTokenAndExposesExpirationFlag() {
        JwtService jwtService = service(Duration.ofMinutes(15));
        String token = jwtService.generateAccessToken(user()).token();
        Claims claims = jwtService.parseAndValidate(token);

        assertThat(jwtService.isExpiredToken(new ExpiredJwtException(null, claims, "expired")))
                .isTrue();
        assertThat(jwtService.isExpiredToken(new JwtException("bad"))).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = service(Duration.ofMinutes(15));
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSigningSecret("another-unit-test-signing-secret-with-256-bits!!");
        otherProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        JwtService otherService = new JwtService(otherProperties, fixedClock());

        String tampered = otherService.generateAccessToken(user()).token();

        assertThatThrownBy(() -> issuer.parseAndValidate(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsManuallyTamperedToken() {
        JwtService jwtService = service(Duration.ofMinutes(15));
        String token = jwtService.generateAccessToken(user()).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejectedWithExpiredException() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("systemRole", "USER")
                .issuedAt(Date.from(NOW.minusSeconds(3600)))
                .expiration(Date.from(NOW.minusSeconds(1800)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service(Duration.ofMinutes(15)).parseAndValidate(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsShortSigningSecret() {
        JwtProperties shortSecret = new JwtProperties();
        shortSecret.setSigningSecret("too-short");
        shortSecret.setAccessTokenTtl(Duration.ofMinutes(15));

        assertThatThrownBy(() -> new JwtService(shortSecret, fixedClock()))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void tokenClaimsDoNotContainEmailOrProjectRoles() {
        JwtService jwtService = service(Duration.ofMinutes(15));
        String token = jwtService.generateAccessToken(user()).token();
        Claims claims = jwtService.parseAndValidate(token);

        assertThat(claims).doesNotContainKeys("email", "projectRoles", "projects", "permissions");
    }
}
