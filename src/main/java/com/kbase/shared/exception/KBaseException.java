package com.kbase.shared.exception;

import java.util.Objects;

/** Base class for known application errors with a safe, stable API code. */
public abstract class KBaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected KBaseException(ErrorCode errorCode) {
        this(errorCode, errorCode == null ? null : errorCode.getDefaultMessage(), null);
    }

    protected KBaseException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, safeMessage, null);
    }

    protected KBaseException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode == null ? null : errorCode.getDefaultMessage(), cause);
    }

    protected KBaseException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage(Objects.requireNonNull(errorCode, "errorCode"), safeMessage), cause);
        this.errorCode = errorCode;
    }

    private static String safeMessage(ErrorCode errorCode, String safeMessage) {
        return safeMessage == null || safeMessage.isBlank()
                ? errorCode.getDefaultMessage()
                : safeMessage;
    }

    public final ErrorCode getErrorCode() {
        return errorCode;
    }

    public final ErrorCode getCode() {
        return errorCode;
    }

    /** The exception message is constrained to an application-approved safe message. */
    public final String getSafeMessage() {
        return getMessage();
    }
}
