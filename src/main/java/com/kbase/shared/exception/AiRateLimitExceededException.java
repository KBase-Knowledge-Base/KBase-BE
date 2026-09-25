package com.kbase.shared.exception;

/** Stable 429 failure for an exhausted interactive AI usage window. */
public final class AiRateLimitExceededException extends BusinessException {

    public AiRateLimitExceededException() {
        super(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
    }
}
