package com.kbase.ai.service;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.DocumentAiIndexStatus;

/** Safe application-facing status projection; no REST endpoint is implied. */
public record DocumentAiIndexStatusView(
        UUID documentId,
        UUID projectId,
        DocumentAiIndexStatus status,
        String failureReason,
        Long activeVersion,
        long desiredVersion,
        int attemptCount,
        String lastErrorCode,
        Instant indexedAt) {
}
