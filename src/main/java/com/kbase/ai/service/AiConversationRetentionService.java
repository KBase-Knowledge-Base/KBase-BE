package com.kbase.ai.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobSchedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Schedules retention intent only; destructive conversation purge is a later milestone. */
@Service
public class AiConversationRetentionService {

    private final AiJobStore jobStore;
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public AiConversationRetentionService(
            AiJobStore jobStore, AiProperties properties, @Qualifier("aiClock") Clock clock) {
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void schedulePurge(UUID projectId, UUID userId) {
        schedulePurge(projectId, userId, clock.instant());
    }

    public void schedulePurge(UUID projectId, UUID userId, Instant membershipLostAt) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(membershipLostAt, "membershipLostAt");
        Instant purgeAfter = membershipLostAt.plus(properties.getRetention());
        jobStore.enqueueActive(new AiJobSchedule(
                AiJobType.CONVERSATION_PURGE,
                projectId,
                null,
                userId,
                purgeDedupKey(projectId, userId),
                "{\"schemaVersion\":1,\"purgeAfter\":\"" + purgeAfter + "\"}",
                purgeAfter,
                properties.getWorker().getMaxAttempts()));
    }

    public int cancelPurgeOnRejoin(UUID projectId, UUID userId) {
        return jobStore.cancelActiveByDedupKey(purgeDedupKey(projectId, userId));
    }

    public static String purgeDedupKey(UUID projectId, UUID userId) {
        return "conversation-purge:" + Objects.requireNonNull(projectId, "projectId")
                + ":" + Objects.requireNonNull(userId, "userId");
    }
}
