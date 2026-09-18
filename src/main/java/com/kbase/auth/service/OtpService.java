package com.kbase.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.kbase.auth.port.OtpStore;
import com.kbase.auth.port.OtpVerificationState;
import com.kbase.config.properties.OtpProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Email-verification OTP application service.
 *
 * <p>This service deliberately does not implement registration, login, MFA,
 * password reset or invitation flows. It creates transient OTP material for a
 * later application service to pass to {@code MailService}.</p>
 */
@Service
public class OtpService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HMAC_CONTEXT = "kbase:email-verification:otp:";

    private final OtpStore otpStore;
    private final OtpProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public OtpService(OtpStore otpStore, OtpProperties properties) {
        this(otpStore, properties, Clock.systemUTC(), new SecureRandom());
    }

    public OtpService(OtpStore otpStore, OtpProperties properties, Clock clock) {
        this(otpStore, properties, clock, new SecureRandom());
    }

    public OtpService(OtpStore otpStore, OtpProperties properties, Clock clock, SecureRandom secureRandom) {
        this.otpStore = Objects.requireNonNull(otpStore, "otpStore");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        validateProperties(properties);
    }

    /** Issues a new email-verification OTP and resets any previous state. */
    public GeneratedOtp issueVerificationOtp(UUID userId) {
        requireUserId(userId);
        String otp = generateOtp();
        Instant issuedAt = clock.instant();
        otpStore.saveVerificationOtp(
                userId,
                protect(userId, otp),
                properties.getTtl(),
                properties.getResendCooldown());
        return new GeneratedOtp(otp, issuedAt.plus(properties.getTtl()));
    }

    /**
     * Issues a replacement OTP only after the resend cooldown has elapsed.
     * RedisOtpStore performs the cooldown check and replacement atomically.
     */
    public GeneratedOtp resendVerificationOtp(UUID userId) {
        requireUserId(userId);
        String otp = generateOtp();
        Instant issuedAt = clock.instant();
        boolean replaced = otpStore.replaceVerificationOtp(
                userId,
                protect(userId, otp),
                properties.getTtl(),
                properties.getResendCooldown());
        if (!replaced) {
            throw new BusinessException(ErrorCode.OTP_RESEND_COOLDOWN);
        }
        return new GeneratedOtp(otp, issuedAt.plus(properties.getTtl()));
    }

    /**
     * Verifies a submitted email-verification OTP. A successful verification
     * deletes the transient state; a failed verification increments attempts.
     */
    public void verify(UUID userId, String submittedOtp) {
        requireUserId(userId);
        Optional<OtpVerificationState> state = otpStore.getVerificationOtp(userId);
        if (state.isEmpty() || state.get().isExpired()) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }

        OtpVerificationState current = state.get();
        if (current.getAttempts() >= properties.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        String candidate = submittedOtp == null ? "" : submittedOtp;
        boolean valid = candidate.length() == properties.getLength()
                && constantTimeMatches(userId, candidate, current.getProtectedOtpHash());
        if (valid) {
            otpStore.deleteVerificationOtp(userId);
            return;
        }

        int attempts = otpStore.incrementAttempts(userId);
        if (attempts < 0) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }
        if (attempts >= properties.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        throw new BusinessException(ErrorCode.INVALID_OTP);
    }

    /** Alias used by future email-verification orchestration code. */
    public void verifyOtp(UUID userId, String submittedOtp) {
        verify(userId, submittedOtp);
    }

    public void deleteVerificationOtp(UUID userId) {
        requireUserId(userId);
        otpStore.deleteVerificationOtp(userId);
    }

    private String generateOtp() {
        int length = properties.getLength();
        StringBuilder otp = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    private String protect(UUID userId, String otp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getHashSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            byte[] input = (HMAC_CONTEXT + userId + ":" + otp).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            // HmacSHA256 is required by the JDK; an unavailable primitive is a
            // programming/deployment failure, never a client-visible OTP error.
            throw new IllegalStateException("OTP protection primitive is unavailable", exception);
        }
    }

    private boolean constantTimeMatches(UUID userId, String candidate, String protectedOtpHash) {
        byte[] expected = protect(userId, candidate).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = protectedOtpHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private static void requireUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
    }

    private static void validateProperties(OtpProperties properties) {
        if (properties.getLength() <= 0
                || properties.getTtl() == null
                || properties.getTtl().isZero()
                || properties.getTtl().isNegative()
                || properties.getResendCooldown() == null
                || properties.getResendCooldown().isZero()
                || properties.getResendCooldown().isNegative()
                || properties.getMaxAttempts() <= 0
                || properties.getHashSecret() == null
                || properties.getHashSecret().isBlank()) {
            throw new IllegalArgumentException("OTP configuration is invalid");
        }
    }

    /**
     * Transient hand-off value for the future email-verification service.
     * Its diagnostic representation is deliberately redacted.
     */
    public static final class GeneratedOtp {

        private final String otp;
        private final Instant expiresAt;

        private GeneratedOtp(String otp, Instant expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
        }

        static GeneratedOtp of(String otp, Instant expiresAt) {
            return new GeneratedOtp(otp, expiresAt);
        }

        public String getOtp() {
            return otp;
        }

        public String otp() {
            return otp;
        }

        public String getCode() {
            return otp;
        }

        public String code() {
            return otp;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public String toString() {
            return "GeneratedOtp{otp=[REDACTED], expiresAt=" + expiresAt + "}";
        }
    }
}
