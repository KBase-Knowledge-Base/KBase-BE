package com.kbase.ai.usage;

import java.util.UUID;

/** KBase-owned application boundary for interactive AI usage enforcement. */
public interface AiUsageGuard {

    /** Consumes one request unit or raises a stable KBase exception. */
    void consume(UUID userId);
}
