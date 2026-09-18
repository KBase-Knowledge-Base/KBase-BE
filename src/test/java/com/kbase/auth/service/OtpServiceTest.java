package com.kbase.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.kbase.auth.port.OtpStore;
import com.kbase.auth.port.OtpVerificationState;
import com.kbase.config.properties.OtpProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.OtpServiceUnavailableException;

import org.junit.jupiter.api.Test;

class OtpServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void issuesSixDigitOtpWithProtectedStateAndRedactedDiagnostics() {
        FakeOtpStore store = new FakeOtpStore();
        OtpService service = new OtpService(store, properties(), fixedClock());

        OtpService.GeneratedOtp generated = service.issueVerificationOtp(UUID.randomUUID());

        assertThat(generated.getOtp()).matches("\\d{6}");
        assertThat(store.state.getProtectedOtpHash()).isNotEqualTo(generated.getOtp());
        assertThat(store.state.getProtectedOtpHash()).hasSize(64);
        assertThat(generated.toString()).doesNotContain(generated.getOtp());
        assertThat(store.state.toString()).doesNotContain(store.state.getProtectedOtpHash());
    }

    @Test
    void verifiesCorrectOtpAndDeletesTransientState() {
        FakeOtpStore store = new FakeOtpStore();
        OtpService service = new OtpService(store, properties(), fixedClock());
        UUID userId = UUID.randomUUID();
        String otp = service.issueVerificationOtp(userId).getOtp();

        service.verify(userId, otp);

        assertThat(store.getVerificationOtp(userId)).isEmpty();
    }

    @Test
    void invalidOtpIncrementsAttemptsAndTheFifthFailureStopsFurtherVerification() {
        FakeOtpStore store = new FakeOtpStore();
        OtpService service = new OtpService(store, properties(), fixedClock());
        UUID userId = UUID.randomUUID();
        String validOtp = service.issueVerificationOtp(userId).getOtp();
        String invalidOtp = "000000".equals(validOtp) ? "000001" : "000000";

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(userId, invalidOtp))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_OTP);
        }
        assertThatThrownBy(() -> service.verify(userId, invalidOtp))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        assertThatThrownBy(() -> service.verify(userId, invalidOtp))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        assertThat(store.state.getAttempts()).isEqualTo(5);
    }

    @Test
    void missingOrExpiredStateMapsToOtpExpired() {
        MutableClock clock = new MutableClock(BASE_TIME);
        FakeOtpStore store = new FakeOtpStore(clock);
        OtpProperties properties = properties();
        properties.setTtl(Duration.ofSeconds(2));
        OtpService service = new OtpService(store, properties, clock);
        UUID userId = UUID.randomUUID();
        String otp = service.issueVerificationOtp(userId).getOtp();

        clock.advance(Duration.ofSeconds(3));

        assertThatThrownBy(() -> service.verify(userId, otp))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void resendHonorsCooldownAndReplacesStateAfterCooldown() {
        MutableClock clock = new MutableClock(BASE_TIME);
        FakeOtpStore store = new FakeOtpStore(clock);
        OtpProperties properties = properties();
        properties.setResendCooldown(Duration.ofSeconds(60));
        OtpService service = new OtpService(store, properties, clock);
        UUID userId = UUID.randomUUID();
        service.issueVerificationOtp(userId);
        store.incrementAttempts(userId);
        String oldHash = store.state.getProtectedOtpHash();

        assertThatThrownBy(() -> service.resendVerificationOtp(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OTP_RESEND_COOLDOWN);

        clock.advance(Duration.ofSeconds(61));
        service.resendVerificationOtp(userId);

        assertThat(store.state.getProtectedOtpHash()).isNotEqualTo(oldHash);
        assertThat(store.state.getAttempts()).isZero();
    }

    @Test
    void otpStoreFailureRemainsTheStableInfrastructureError() {
        OtpStore unavailableStore = new OtpStore() {
            @Override
            public void saveVerificationOtp(UUID userId, String protectedOtpHash, Duration ttl,
                    Duration resendCooldown) {
                throw new OtpServiceUnavailableException();
            }

            @Override
            public Optional<OtpVerificationState> getVerificationOtp(UUID userId) {
                throw new OtpServiceUnavailableException();
            }

            @Override
            public int incrementAttempts(UUID userId) {
                throw new OtpServiceUnavailableException();
            }

            @Override
            public boolean isResendCooldownActive(UUID userId) {
                throw new OtpServiceUnavailableException();
            }

            @Override
            public boolean replaceVerificationOtp(UUID userId, String protectedOtpHash, Duration ttl,
                    Duration resendCooldown) {
                throw new OtpServiceUnavailableException();
            }

            @Override
            public void deleteVerificationOtp(UUID userId) {
                throw new OtpServiceUnavailableException();
            }
        };
        OtpService service = new OtpService(unavailableStore, properties(), fixedClock());

        assertThatThrownBy(() -> service.issueVerificationOtp(UUID.randomUUID()))
                .isInstanceOf(OtpServiceUnavailableException.class)
                .extracting(exception -> ((OtpServiceUnavailableException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OTP_SERVICE_UNAVAILABLE);
    }

    private static OtpProperties properties() {
        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setTtl(Duration.ofMinutes(5));
        properties.setResendCooldown(Duration.ofSeconds(60));
        properties.setMaxAttempts(5);
        properties.setHashSecret("test-only-otp-hash-secret");
        return properties;
    }

    private static Clock fixedClock() {
        return Clock.fixed(BASE_TIME, ZoneOffset.UTC);
    }

    private static final class FakeOtpStore implements OtpStore {

        private final Map<UUID, StoredState> states = new HashMap<>();
        private final Map<UUID, Instant> cooldowns = new HashMap<>();
        private final Clock clock;
        private OtpVerificationState state;

        private FakeOtpStore() {
            this(fixedClock());
        }

        private FakeOtpStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void saveVerificationOtp(UUID userId, String protectedOtpHash, Duration ttl,
                Duration resendCooldown) {
            StoredState stored = new StoredState(protectedOtpHash, 0,
                    clock.instant().plus(ttl), clock.instant().plus(resendCooldown));
            states.put(userId, stored);
            cooldowns.put(userId, stored.cooldownUntil);
            refreshPublicState(userId);
        }

        @Override
        public Optional<OtpVerificationState> getVerificationOtp(UUID userId) {
            StoredState stored = states.get(userId);
            if (stored == null || !clock.instant().isBefore(stored.expiresAt)) {
                states.remove(userId);
                refreshPublicState(userId);
                return Optional.empty();
            }
            refreshPublicState(userId);
            return Optional.of(state);
        }

        @Override
        public int incrementAttempts(UUID userId) {
            StoredState stored = states.get(userId);
            if (stored == null || !clock.instant().isBefore(stored.expiresAt)) {
                return -1;
            }
            stored.attempts++;
            refreshPublicState(userId);
            return stored.attempts;
        }

        @Override
        public boolean isResendCooldownActive(UUID userId) {
            Instant cooldownUntil = cooldowns.get(userId);
            return cooldownUntil != null && clock.instant().isBefore(cooldownUntil);
        }

        @Override
        public boolean replaceVerificationOtp(UUID userId, String protectedOtpHash, Duration ttl,
                Duration resendCooldown) {
            if (isResendCooldownActive(userId)) {
                return false;
            }
            saveVerificationOtp(userId, protectedOtpHash, ttl, resendCooldown);
            return true;
        }

        @Override
        public void deleteVerificationOtp(UUID userId) {
            states.remove(userId);
            cooldowns.remove(userId);
            state = null;
        }

        private void refreshPublicState(UUID userId) {
            StoredState stored = states.get(userId);
            state = stored == null ? null : new OtpVerificationState(
                    stored.protectedOtpHash,
                    stored.attempts,
                    Duration.between(clock.instant(), stored.expiresAt));
        }

        private static final class StoredState {
            private final String protectedOtpHash;
            private int attempts;
            private final Instant expiresAt;
            private final Instant cooldownUntil;

            private StoredState(String protectedOtpHash, int attempts, Instant expiresAt,
                    Instant cooldownUntil) {
                this.protectedOtpHash = protectedOtpHash;
                this.attempts = attempts;
                this.expiresAt = expiresAt;
                this.cooldownUntil = cooldownUntil;
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
