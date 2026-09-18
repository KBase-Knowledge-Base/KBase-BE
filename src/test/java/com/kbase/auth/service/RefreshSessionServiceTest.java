package com.kbase.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.auth.entity.RefreshSession;
import com.kbase.auth.repository.RefreshSessionRepository;
import com.kbase.config.properties.JwtProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private RefreshSessionRepository repository;
    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshSessionRepository.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSigningSecret("unit-test-jwt-signing-secret-that-is-256-bits!");
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        jwtProperties.setRefreshTokenTtl(Duration.ofDays(7));
        service = new RefreshSessionService(
                repository, jwtProperties, Clock.fixed(NOW, ZoneOffset.UTC),
                new java.security.SecureRandom());
    }

    private User user() {
        User user = new User(
                "user@example.com", "password-hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void createSessionPersistsOnlyHashAndReturnsRawTokenOnce() throws Exception {
        User user = user();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshSessionService.CreatedRefreshSession created = service.createSession(user);

        assertThat(created.rawToken()).isNotBlank();
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(captor.capture());
        RefreshSession persisted = captor.getValue();
        assertThat(persisted.getUser()).isSameAs(user);
        assertThat(persisted.getRevokedAt()).isNull();
        assertThat(persisted.getTokenHash())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest
                        .getInstance("SHA-256")
                        .digest(created.rawToken().getBytes(StandardCharsets.UTF_8))))
                .hasSize(64)
                .doesNotContain(created.rawToken());
    }

    @Test
    void rawTokensAreUniqueAcrossSessions() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String first = service.createSession(user()).rawToken();
        String second = service.createSession(user()).rawToken();

        assertThat(first).isNotEqualTo(second);
    }

    private static String sha256Hex(String rawToken) throws Exception {
        return HexFormat.of().formatHex(MessageDigest
                .getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validateReturnsSessionForActiveHash() throws Exception {
        RefreshSession session = new RefreshSession(
                user(), "hash", NOW.plus(Duration.ofHours(1)));
        when(repository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.of(session));

        assertThat(service.validate("raw-token")).isSameAs(session);
    }

    @Test
    void validateRejectsUnknownBlankOrRevokedAndExpiredTokens() throws Exception {
        when(repository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("raw-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        assertThatThrownBy(() -> service.validate("  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

        RefreshSession revoked = new RefreshSession(
                user(), "revoked-hash", NOW.plus(Duration.ofHours(1)));
        revoked.setRevokedAt(NOW.minusSeconds(60));
        when(repository.findByTokenHash(sha256Hex("revoked-raw"))).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.validate("revoked-raw"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_SESSION_REVOKED));

        RefreshSession expired = new RefreshSession(user(), "expired-hash", NOW.minusSeconds(1));
        when(repository.findByTokenHash(sha256Hex("expired-raw"))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.validate("expired-raw"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED));
    }

    @Test
    void revokeIfPresentMarksOnlyActiveSessions() throws Exception {
        RefreshSession active = new RefreshSession(user(), "active-hash", NOW.plus(Duration.ofHours(1)));
        RefreshSession alreadyRevoked = new RefreshSession(
                user(), "revoked-hash", NOW.plus(Duration.ofHours(1)));
        alreadyRevoked.setRevokedAt(NOW.minusSeconds(120));
        when(repository.findByTokenHash(sha256Hex("active-raw")))
                .thenReturn(Optional.of(active));
        when(repository.findByTokenHash(sha256Hex("revoked-raw")))
                .thenReturn(Optional.of(alreadyRevoked));

        service.revokeIfPresent("active-raw");
        assertThat(active.getRevokedAt()).isEqualTo(NOW);

        service.revokeIfPresent("revoked-raw");
        assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(NOW.minusSeconds(120));

        service.revokeIfPresent(null);
        service.revokeIfPresent("  ");
        verify(repository, never()).findByTokenHash(null);
    }

    @Test
    void revokeAllForUserRevokesOnlyActiveSessions() {
        RefreshSession active = new RefreshSession(user(), "hash-a", NOW.plus(Duration.ofHours(1)));
        RefreshSession alreadyRevoked = new RefreshSession(
                user(), "hash-b", NOW.plus(Duration.ofHours(1)));
        alreadyRevoked.setRevokedAt(NOW.minusSeconds(60));
        UUID userId = user().getId();
        when(repository.findAllByUserId(userId)).thenReturn(List.of(active, alreadyRevoked));

        service.revokeAllForUser(userId);

        assertThat(active.getRevokedAt()).isEqualTo(NOW);
        assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(NOW.minusSeconds(60));
    }
}
