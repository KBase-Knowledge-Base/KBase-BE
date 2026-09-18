package com.kbase.shared.exception;

/** Authenticated caller is not allowed to perform the requested operation. */
public class ForbiddenOperationException extends KBaseException {

    public ForbiddenOperationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenOperationException(ErrorCode errorCode, String safeMessage) {
        super(errorCode, safeMessage);
    }

    public ForbiddenOperationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ForbiddenOperationException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        super(errorCode, safeMessage, cause);
    }
}
