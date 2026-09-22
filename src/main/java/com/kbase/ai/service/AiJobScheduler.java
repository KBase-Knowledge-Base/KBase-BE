package com.kbase.ai.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobExecutionOutcome;
import com.kbase.ai.job.AiJobExecutionResult;
import com.kbase.ai.job.AiJobHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded poll/dispatch boundary; PostgreSQL, not this scheduler, owns backlog state. */
@Component
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public class AiJobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiJobScheduler.class);
    private static final String HANDLER_UNAVAILABLE = "HANDLER_UNAVAILABLE";
    private static final String WORKER_EXCEPTION = "WORKER_EXCEPTION";

    private final AiJobStore jobStore;
    private final AiJobHandlerRegistry handlerRegistry;

    public AiJobScheduler(AiJobStore jobStore, AiJobHandlerRegistry handlerRegistry) {
        this.jobStore = jobStore;
        this.handlerRegistry = handlerRegistry;
    }

    @Scheduled(fixedDelayString = "${kbase.ai.worker.poll-interval:5s}")
    public void scheduledPoll() {
        pollOnce();
    }

    /** Exposed for deterministic tests and operator diagnostics. */
    public int pollOnce() {
        Set<AiJobType> supportedTypes = handlerRegistry.supportedJobTypes();
        if (supportedTypes.isEmpty()) {
            return 0;
        }
        List<AiJobClaim> claims = jobStore.claimDueJobs(supportedTypes);
        for (AiJobClaim claim : claims) {
            executeOne(claim);
        }
        return claims.size();
    }

    private void executeOne(AiJobClaim claim) {
        AiJobExecutionResult result;
        try {
            AiJobHandler handler = handlerRegistry.handlerFor(claim.jobType()).orElse(null);
            if (handler == null) {
                result = AiJobExecutionResult.retry(
                        jobStore.now().plus(jobStore.retryBackoff()), HANDLER_UNAVAILABLE);
            } else {
                result = handler.handle(claim);
            }
        } catch (RuntimeException exception) {
            // Keep logs and durable state category-only; raw exception messages
            // may contain provider or document data in later milestones.
            LOGGER.warn("AI job handler failed jobType={} errorCode={}",
                    claim.jobType(), WORKER_EXCEPTION);
            result = claim.attemptCount() >= claim.maxAttempts()
                    ? AiJobExecutionResult.failure(WORKER_EXCEPTION)
                    : AiJobExecutionResult.retry(
                            jobStore.now().plus(jobStore.retryBackoff()), WORKER_EXCEPTION);
        }

        if (result == null || result.outcome() == null) {
            jobStore.markFailed(claim, "INVALID_HANDLER_RESULT");
            return;
        }

        switch (result.outcome()) {
            case SUCCESS -> jobStore.markDone(claim);
            case RETRY -> {
                Instant nextRunAt = result.nextRunAt() == null
                        ? jobStore.now().plus(jobStore.retryBackoff()) : result.nextRunAt();
                if (claim.attemptCount() >= claim.maxAttempts()) {
                    jobStore.markFailed(claim, result.errorCode());
                } else {
                    jobStore.markRetry(claim, nextRunAt, result.errorCode());
                }
            }
            case FAILURE -> jobStore.markFailed(claim, result.errorCode());
        }
    }
}
