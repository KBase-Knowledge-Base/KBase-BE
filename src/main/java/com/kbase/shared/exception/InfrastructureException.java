package com.kbase.shared.exception;

/** Technical failure translated at an application/infrastructure boundary. */
public class InfrastructureException extends KBaseException {

    public InfrastructureException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InfrastructureException(ErrorCode errorCode, String safeMessage) {
        super(errorCode, safeMessage);
    }

    public InfrastructureException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public InfrastructureException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        super(errorCode, safeMessage, cause);
    }
}
