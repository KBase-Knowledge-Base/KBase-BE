package com.kbase.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobExecutionResult;
import com.kbase.ai.job.AiJobHandler;

import org.junit.jupiter.api.Test;

class AiJobSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void successMarksCurrentLeaseDone() {
        AiJobStore store = mockStore();
        AiJobClaim claim = claim(1, 3);
        AiJobHandler handler = handler(AiJobExecutionResult.success());
        when(store.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX))).thenReturn(List.of(claim));

        new AiJobScheduler(store, new AiJobHandlerRegistry(List.of(handler))).pollOnce();

        verify(store).markDone(claim);
    }

    @Test
    void retryOutcomePersistsFutureRunAtAndSafeCategory() {
        AiJobStore store = mockStore();
        AiJobClaim claim = claim(1, 3);
        Instant nextRunAt = NOW.plusSeconds(30);
        AiJobHandler handler = handler(AiJobExecutionResult.retry(nextRunAt, "TRANSIENT_TEST"));
        when(store.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX))).thenReturn(List.of(claim));

        new AiJobScheduler(store, new AiJobHandlerRegistry(List.of(handler))).pollOnce();

        verify(store).markRetry(claim, nextRunAt, "TRANSIENT_TEST");
    }

    @Test
    void permanentFailureMarksCurrentLeaseFailed() {
        AiJobStore store = mockStore();
        AiJobClaim claim = claim(1, 3);
        AiJobHandler handler = handler(AiJobExecutionResult.failure("PERMANENT_TEST"));
        when(store.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX))).thenReturn(List.of(claim));

        new AiJobScheduler(store, new AiJobHandlerRegistry(List.of(handler))).pollOnce();

        verify(store).markFailed(claim, "PERMANENT_TEST");
    }

    @Test
    void exceptionSchedulesBoundedRetryWithoutPersistingExceptionText() {
        AiJobStore store = mockStore();
        AiJobClaim claim = claim(1, 3);
        AiJobHandler handler = mock(AiJobHandler.class);
        when(handler.jobType()).thenReturn(AiJobType.DOCUMENT_INDEX);
        when(handler.handle(claim)).thenThrow(new IllegalStateException("raw document/provider detail"));
        when(store.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX))).thenReturn(List.of(claim));

        new AiJobScheduler(store, new AiJobHandlerRegistry(List.of(handler))).pollOnce();

        verify(store).markRetry(claim, NOW.plus(Duration.ofSeconds(30)), "WORKER_EXCEPTION");
    }

    @Test
    void exceptionOnLastAttemptBecomesTerminalFailure() {
        AiJobStore store = mockStore();
        AiJobClaim claim = claim(3, 3);
        AiJobHandler handler = mock(AiJobHandler.class);
        when(handler.jobType()).thenReturn(AiJobType.DOCUMENT_INDEX);
        when(handler.handle(claim)).thenThrow(new IllegalStateException("not persisted"));
        when(store.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX))).thenReturn(List.of(claim));

        new AiJobScheduler(store, new AiJobHandlerRegistry(List.of(handler))).pollOnce();

        verify(store).markFailed(claim, "WORKER_EXCEPTION");
    }

    private static AiJobStore mockStore() {
        AiJobStore store = mock(AiJobStore.class);
        when(store.now()).thenReturn(NOW);
        when(store.retryBackoff()).thenReturn(Duration.ofSeconds(30));
        return store;
    }

    private static AiJobHandler handler(AiJobExecutionResult result) {
        AiJobHandler handler = mock(AiJobHandler.class);
        when(handler.jobType()).thenReturn(AiJobType.DOCUMENT_INDEX);
        when(handler.handle(any(AiJobClaim.class))).thenReturn(result);
        return handler;
    }

    private static AiJobClaim claim(int attempt, int maxAttempts) {
        return new AiJobClaim(UUID.randomUUID(), AiJobType.DOCUMENT_INDEX, null, null, null,
                "scheduler-test", null, NOW, attempt, maxAttempts,
                NOW.plusSeconds(120), "worker:test-token");
    }
}
