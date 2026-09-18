package com.kbase.auth.port;

import java.time.Duration;
import java.util.Objects;

/**
 * Application-facing snapshot of a pending email-verification OTP.
 *
 * <p>The protected value is intentionally available only to the OTP service
 * for comparison. The raw OTP is never part of this model.</p>
 */
public final class OtpVerificationState {

    private final String protectedOtpHash;
    private final int attempts;
    private final Duration remainingTtl;

    public OtpVerificationState(String protectedOtpHash, int attempts, Duration remainingTtl) {
        this.protectedOtpHash = Objects.requireNonNull(protectedOtpHash, "protectedOtpHash");
        this.attempts = attempts;
        this.remainingTtl = Objects.requireNonNull(remainingTtl, "remainingTtl");
    }

    public String getProtectedOtpHash() {
        return protectedOtpHash;
    }

    public String protectedOtpHash() {
        return protectedOtpHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public int attempts() {
        return attempts;
    }

    public Duration getRemainingTtl() {
        return remainingTtl;
    }

    public Duration remainingTtl() {
        return remainingTtl;
    }

    public boolean isExpired() {
        return remainingTtl.isZero() || remainingTtl.isNegative();
    }

    /** Do not include the protected OTP value in diagnostics or logs. */
    @Override
    public String toString() {
        return "OtpVerificationState{" +
                "attempts=" + attempts +
                ", remainingTtl=" + remainingTtl +
                '}';
    }
}
