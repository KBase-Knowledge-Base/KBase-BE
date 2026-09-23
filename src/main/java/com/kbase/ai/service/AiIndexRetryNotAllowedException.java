package com.kbase.ai.service;

/** Stable application error for manual retry outside the FAILED state. */
public final class AiIndexRetryNotAllowedException extends RuntimeException {

    public static final String CODE = "AI_INDEX_RETRY_NOT_ALLOWED";

    public AiIndexRetryNotAllowedException() {
        super(CODE);
    }

    public String code() {
        return CODE;
    }
}
