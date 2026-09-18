package com.kbase.shared.exception;

/** Stable application exception for Redis-backed OTP infrastructure failure. */
public class OtpServiceUnavailableException extends InfrastructureException {

    public OtpServiceUnavailableException() {
        super(ErrorCode.OTP_SERVICE_UNAVAILABLE);
    }
}
