package com.kbase.ai.service;

import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

/** Stable application error for manual retry outside the FAILED state. */
public final class AiIndexRetryNotAllowedException extends BusinessException {

    public static final String CODE = "AI_INDEX_RETRY_NOT_ALLOWED";

    public AiIndexRetryNotAllowedException() {
        super(ErrorCode.AI_INDEX_RETRY_NOT_ALLOWED);
    }

    public String code() {
        return CODE;
    }
}
