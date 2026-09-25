package com.kbase.shared.exception;

/** Stable 503 failure when the AI-only usage state cannot be evaluated. */
public final class AiUsageGuardUnavailableException extends InfrastructureException {

    public AiUsageGuardUnavailableException() {
        super(ErrorCode.AI_USAGE_GUARD_UNAVAILABLE);
    }
}
