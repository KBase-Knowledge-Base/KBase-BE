package com.kbase.ai.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobEnqueueResult;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.repository.AiJobClaimRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction boundary for durable AI jobs. Claim and state transitions are
 * short database transactions; handler code never runs in this class.
 */
@Service
public class AiJobStore {

    private static final String DEFAULT_WORKER_PREFIX = "kbase-worker-";

    private final AiJobClaimRepository repository;
    private final AiProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final String workerIdentity;

    @Autowired
    public AiJobStore(AiJobClaimRepository repository, AiProperties properties,
            PlatformTransactionManager transactionManager,
            @Qualifier("aiClock") Clock clock) {
        this(repository, properties, transactionManager, clock,
                DEFAULT_WORKER_PREFIX + UUID.randomUUID());
    }

    public AiJobStore(AiJobClaimRepository repository, AiProperties properties,
            PlatformTransactionManager transactionManager, Clock clock,
            String workerIdentity) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.workerIdentity = requireNonBlank(workerIdentity, "workerIdentity");
    }

    public List<AiJobClaim> claimDueJobs(Set<AiJobType> allowedTypes) {
        return claimDueJobs(allowedTypes, properties.getWorker().getBatchSize(), clock.instant());
    }

    public List<AiJobClaim> claimDueJobs(Set<AiJobType> allowedTypes, int batchSize) {
        return claimDueJobs(allowedTypes, batchSize, clock.instant());
    }

    /** Explicit time overload keeps retry/lease tests deterministic without sleeping. */
    public List<AiJobClaim> claimDueJobs(Set<AiJobType> allowedTypes, int batchSize, Instant now) {
        Objects.requireNonNull(now, "now");
        return transactionTemplate.execute(status -> {
            repository.failExhaustedStaleJobs(allowedTypes, now);
            return repository.claimDueJobs(allowedTypes, batchSize, now,
                    properties.getWorker().getLeaseTimeout(), workerIdentity);
        });
    }

    public AiJobEnqueueResult enqueueActive(AiJobSchedule schedule) {
        return transactionTemplate.execute(status -> repository.enqueueActive(
                schedule, clock.instant()));
    }

    public boolean markDone(AiJobClaim claim) {
        return transition(() -> repository.markDone(claim.id(), claim.leaseToken(), clock.instant()));
    }

    public boolean markRetry(AiJobClaim claim, Instant nextRunAt, String errorCode) {
        Objects.requireNonNull(nextRunAt, "nextRunAt");
        return transition(() -> repository.markRetry(claim.id(), claim.leaseToken(), nextRunAt,
                safeErrorCode(errorCode, "WORKER_RETRY"), clock.instant()));
    }

    public boolean markFailed(AiJobClaim claim, String errorCode) {
        return transition(() -> repository.markFailed(claim.id(), claim.leaseToken(),
                safeErrorCode(errorCode, "WORKER_FAILURE"), clock.instant()));
    }

    public int cancelActiveByDedupKey(String dedupKey) {
        requireNonBlank(dedupKey, "dedupKey");
        return transactionTemplate.execute(status -> repository.cancelActiveByDedupKey(
                dedupKey, clock.instant()));
    }

    /** Acquires the transaction-scoped PostgreSQL lock for a semantic lifecycle key. */
    public void lockLifecycleKey(String dedupKey) {
        requireNonBlank(dedupKey, "dedupKey");
        transactionTemplate.executeWithoutResult(status -> repository.lockDedupKey(dedupKey));
    }

    public Instant now() {
        return clock.instant();
    }

    public Duration retryBackoff() {
        return properties.getWorker().getRetryBackoff();
    }

    public Map<String, Long> depthByStatus(Set<AiJobType> allowedTypes) {
        return repository.countByStatus(allowedTypes);
    }

    public long staleJobCount(Set<AiJobType> allowedTypes) {
        return repository.countStale(allowedTypes, clock.instant());
    }

    private boolean transition(java.util.function.IntSupplier transition) {
        return transactionTemplate.execute(status -> transition.getAsInt() == 1);
    }

    private static String safeErrorCode(String errorCode, String fallback) {
        if (errorCode == null || errorCode.isBlank()
                || errorCode.length() > 120
                || !errorCode.matches("[A-Z0-9_]+")) {
            return fallback;
        }
        return errorCode;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
