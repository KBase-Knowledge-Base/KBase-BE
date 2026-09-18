package com.kbase.auth.port;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Application port for short-lived registration email-verification OTP state.
 *
 * <p>Implementations must store only the protected OTP representation. Redis
 * client types, keys and serialization details must not cross this boundary.</p>
 */
public interface OtpStore {

    void saveVerificationOtp(
            UUID userId,
            String protectedOtpHash,
            Duration ttl,
            Duration resendCooldown);

    Optional<OtpVerificationState> getVerificationOtp(UUID userId);

    /** Returns the incremented attempt count, or {@code -1} when state expired. */
    int incrementAttempts(UUID userId);

    boolean isResendCooldownActive(UUID userId);

    /** Replaces state only when the resend cooldown is not active, atomically. */
    boolean replaceVerificationOtp(
            UUID userId,
            String protectedOtpHash,
            Duration ttl,
            Duration resendCooldown);

    void deleteVerificationOtp(UUID userId);
}
