package com.kbase.ai.job;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.AiJobType;

/** Small, versioned scheduling input. It deliberately contains no document content or prompt. */
public record AiJobSchedule(
        AiJobType jobType,
        UUID projectId,
        UUID documentId,
        UUID userId,
        String dedupKey,
        String payload,
        Instant runAt,
        int maxAttempts) {
}
