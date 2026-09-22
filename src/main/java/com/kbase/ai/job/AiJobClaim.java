package com.kbase.ai.job;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.AiJobType;

/** Immutable lease snapshot handed to a handler after the claim transaction commits. */
public record AiJobClaim(
        UUID id,
        AiJobType jobType,
        UUID projectId,
        UUID documentId,
        UUID userId,
        String dedupKey,
        String payload,
        Instant runAt,
        int attemptCount,
        int maxAttempts,
        Instant leaseUntil,
        String leaseToken) {
}
