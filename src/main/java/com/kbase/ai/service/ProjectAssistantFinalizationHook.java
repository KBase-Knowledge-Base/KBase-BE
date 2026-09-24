package com.kbase.ai.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

/** Deterministic seam for testing the gap between M6 result and final DB authorization. */
@Component
public class ProjectAssistantFinalizationHook {
    public void beforeFinalization(UUID projectId, UUID conversationId) {
        // Production has no work in this gap; the final transaction owns the access recheck.
    }
}
