package com.kbase.ai.job;

import java.util.UUID;

/** Identifies the active row returned by idempotent enqueue. */
public record AiJobEnqueueResult(UUID jobId, boolean created) {
}
