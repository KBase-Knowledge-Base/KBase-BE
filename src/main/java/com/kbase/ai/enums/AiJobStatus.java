package com.kbase.ai.enums;

/** Durable job state; execution/claiming is implemented in a later milestone. */
public enum AiJobStatus {
    PENDING,
    PROCESSING,
    RETRY,
    DONE,
    FAILED,
    CANCELLED
}
