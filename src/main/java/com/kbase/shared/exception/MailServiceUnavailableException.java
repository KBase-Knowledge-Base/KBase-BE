package com.kbase.shared.exception;

/** Stable application exception for SMTP/mail infrastructure failure. */
public class MailServiceUnavailableException extends InfrastructureException {

    public MailServiceUnavailableException() {
        super(ErrorCode.EMAIL_SERVICE_UNAVAILABLE);
    }
}
