package com.kbase.shared.exception;

/** Requested resource does not exist. */
public class ResourceNotFoundException extends KBaseException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String safeMessage) {
        super(errorCode, safeMessage);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        super(errorCode, safeMessage, cause);
    }
}
