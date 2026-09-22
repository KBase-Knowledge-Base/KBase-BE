package com.kbase.ai.job;

import java.time.Instant;

/** Safe handler result; errorCode is an internal category, never raw exception text. */
public record AiJobExecutionResult(
        AiJobExecutionOutcome outcome,
        Instant nextRunAt,
        String errorCode) {

    public static AiJobExecutionResult success() {
        return new AiJobExecutionResult(AiJobExecutionOutcome.SUCCESS, null, null);
    }

    public static AiJobExecutionResult retry(Instant nextRunAt, String errorCode) {
        return new AiJobExecutionResult(AiJobExecutionOutcome.RETRY, nextRunAt, errorCode);
    }

    public static AiJobExecutionResult failure(String errorCode) {
        return new AiJobExecutionResult(AiJobExecutionOutcome.FAILURE, null, errorCode);
    }
}
