package com.kbase.ai.enums;

/** Durable lifecycle state for a document's AI index. */
public enum DocumentAiIndexStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED,
    UNSUPPORTED
}
