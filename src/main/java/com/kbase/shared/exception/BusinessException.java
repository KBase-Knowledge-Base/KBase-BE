package com.kbase.shared.exception;

/** Business rule conflict or invalid domain state. */
public class BusinessException extends KBaseException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String safeMessage) {
        super(errorCode, safeMessage);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public BusinessException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        super(errorCode, safeMessage, cause);
    }
}
