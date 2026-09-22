package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kbase.ai.entity.AiJob;
import com.kbase.ai.enums.AiJobStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobEnqueueResult;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.job.AiJobExecutionResult;
import com.kbase.ai.job.AiJobHandler;
import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.repository.AiJobClaimRepository;
import com.kbase.ai.config.AiProperties;
import com.kbase.ai.service.AiJobHandlerRegistry;
import com.kbase.ai.service.AiJobScheduler;
import com.kbase.ai.service.AiJobStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL evidence for M3 claim, lease, retry and active-dedup semantics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class AiJobEngineIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("kbase.postgres.host", POSTGRES::getHost);
        registry.add("kbase.postgres.port", () -> POSTGRES.getFirstMappedPort());
        registry.add("kbase.postgres.database", () -> POSTGRES.getDatabaseName());
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "m3-job-engine-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m3-job-engine-otp-secret");
        registry.add("kbase.mail.username", () -> "m3@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m3-mail-password");
        registry.add("kbase.storage.access-key", () -> "m3-access-key");
        registry.add("kbase.storage.secret-key", () -> "m3-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @Autowired
    private AiJobStore jobStore;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private AiJobClaimRepository claimRepository;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    @Qualifier("aiClock")
    private Clock aiClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearJobs() {
        jdbcTemplate.update("DELETE FROM ai_jobs");
    }

    @Test
    void twoWorkersCannotClaimOneDueJob() throws Exception {
        UUID jobId = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3,
                null, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(3);
        try {
            List<Future<List<AiJobClaim>>> futures = List.of(
                    executor.submit(() -> claimAfter(start)),
                    executor.submit(() -> claimAfter(start)));
            start.await(10, TimeUnit.SECONDS);
            List<AiJobClaim> claims = new ArrayList<>();
            for (Future<List<AiJobClaim>> future : futures) {
                claims.addAll(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(claims).singleElement().satisfies(claim -> {
                assertThat(claim.id()).isEqualTo(jobId);
                assertThat(claim.attemptCount()).isEqualTo(1);
                assertThat(claim.leaseToken()).contains(":");
            });
            AiJob saved = jobRepository.findById(jobId).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(AiJobStatus.PROCESSING);
            assertThat(saved.getAttemptCount()).isEqualTo(1);
            assertThat(saved.getLockedBy()).isEqualTo(claims.getFirst().leaseToken());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void skipLockedAllowsAnotherJobToProgressWhileFirstRowIsLocked() throws Exception {
        UUID lockedJob = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(2), 0, 3,
                null, null);
        UUID independentJob = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3,
                null, null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT id FROM ai_jobs WHERE id = ? FOR UPDATE",
                                UUID.class, lockedJob);
                        locked.countDown();
                        try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return null;
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            List<AiJobClaim> claims = jobStore.claimDueJobs(
                    Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE);

            assertThat(claims).singleElement().extracting(AiJobClaim::id)
                    .isEqualTo(independentJob);
            release.countDown();
            holder.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void retryRunAtIsNotClaimedBeforeDueTime() {
        UUID jobId = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3,
                null, null);
        AiJobClaim first = jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE)
                .getFirst();
        assertThat(first.id()).isEqualTo(jobId);
        Instant retryAt = BASE.plusSeconds(30);
        assertThat(jobStore.markRetry(first, retryAt, "TEMPORARY_FAILURE")).isTrue();

        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                BASE.plusSeconds(10))).isEmpty();
        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                BASE.plusSeconds(31))).singleElement().extracting(AiJobClaim::id)
                .isEqualTo(jobId);
    }

    @Test
    void staleLeaseGetsNewTokenAndOldWorkerCannotComplete() {
        UUID jobId = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(10), 1, 3,
                BASE.minusSeconds(1), "worker-a:old-token");
        AiJobClaim reclaimed = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE).getFirst();

        assertThat(reclaimed.id()).isEqualTo(jobId);
        assertThat(reclaimed.attemptCount()).isEqualTo(2);
        assertThat(reclaimed.leaseToken()).isNotEqualTo("worker-a:old-token");

        AiJobClaim oldClaim = new AiJobClaim(jobId, AiJobType.DOCUMENT_INDEX, null, null, null,
                "old", null, BASE.minusSeconds(10), 1, 3, BASE.minusSeconds(1),
                "worker-a:old-token");
        assertThat(jobStore.markDone(oldClaim)).isFalse();
        assertThat(jobStore.markDone(reclaimed)).isTrue();
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.DONE);
    }

    @Test
    void staleLeaseWithExhaustedAttemptsBecomesFailedWithoutExecution() {
        UUID jobId = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(10), 3, 3,
                BASE.minusSeconds(1), "worker-a:exhausted");

        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE)).isEmpty();
        AiJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AiJobStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("MAX_ATTEMPTS_EXHAUSTED");
        assertThat(failed.getCompletedAt()).isEqualTo(BASE);
    }

    @Test
    void futureAndTerminalJobsAreNotClaimedAndBatchIsBounded() {
        UUID futurePending = insertJob(AiJobStatus.PENDING, BASE.plusSeconds(30), 0, 3,
                null, null);
        UUID futureRetry = insertJob(AiJobStatus.RETRY, BASE.plusSeconds(30), 1, 3,
                null, null);
        insertJob(AiJobStatus.DONE, BASE.minusSeconds(30), 1, 3, null, null);
        insertJob(AiJobStatus.FAILED, BASE.minusSeconds(30), 1, 3, null, null);
        insertJob(AiJobStatus.CANCELLED, BASE.minusSeconds(30), 1, 3, null, null);
        UUID dueOne = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(2), 0, 3, null, null);
        UUID dueTwo = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3, null, null);

        List<AiJobClaim> firstBatch = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE);
        assertThat(firstBatch).singleElement().extracting(AiJobClaim::id).isEqualTo(dueOne);
        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 10, BASE))
                .extracting(AiJobClaim::id).containsExactly(dueTwo);
        assertThat(jobRepository.findById(futurePending).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.PENDING);
        assertThat(jobRepository.findById(futureRetry).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.RETRY);
    }

    @Test
    void activeDedupIsConcurrencySafeButTerminalRowsDoNotBlockReenqueue() throws Exception {
        String dedupKey = "dedup-concurrency:" + UUID.randomUUID();
        AiJobSchedule schedule = new AiJobSchedule(
                AiJobType.DOCUMENT_INDEX, null, null, null, dedupKey,
                "{\"schemaVersion\":1}", BASE, 3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(3);
        try {
            List<Future<AiJobEnqueueResult>> futures = List.of(
                    executor.submit(() -> enqueueAfter(start, schedule)),
                    executor.submit(() -> enqueueAfter(start, schedule)));
            start.await(10, TimeUnit.SECONDS);
            AiJobEnqueueResult first = futures.get(0).get(20, TimeUnit.SECONDS);
            AiJobEnqueueResult second = futures.get(1).get(20, TimeUnit.SECONDS);
            assertThat(first.jobId()).isEqualTo(second.jobId());
            assertThat(List.of(first.created(), second.created())).containsExactlyInAnyOrder(true, false);
            assertThat(countByDedup(dedupKey)).isEqualTo(1);

            AiJobClaim claim = jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE)
                    .getFirst();
            assertThat(jobStore.markDone(claim)).isTrue();
            AiJobEnqueueResult reenqueue = jobStore.enqueueActive(schedule);
            assertThat(reenqueue.created()).isTrue();
            assertThat(countByDedup(dedupKey)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void schedulerDoesNotClaimWithoutRegisteredHandlerAndRunsHandlerAfterClaimTransaction() {
        UUID unhandledId = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3,
                null, null);
        AiJobScheduler emptyScheduler = new AiJobScheduler(
                jobStore, new AiJobHandlerRegistry(List.of()));
        assertThat(emptyScheduler.pollOnce()).isZero();
        assertThat(jobRepository.findById(unhandledId).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.PENDING);

        UUID handledId = insertJob(AiJobType.GUIDE_REINDEX, AiJobStatus.PENDING,
                BASE.minusSeconds(1), 0, 3, null, null);
        AtomicBoolean transactionWasOpen = new AtomicBoolean(true);
        AiJobHandler testHandler = new AiJobHandler() {
            @Override
            public AiJobType jobType() {
                return AiJobType.GUIDE_REINDEX;
            }

            @Override
            public AiJobExecutionResult handle(AiJobClaim claim) {
                transactionWasOpen.set(TransactionSynchronizationManager.isActualTransactionActive());
                assertThat(claim.id()).isEqualTo(handledId);
                return AiJobExecutionResult.success();
            }
        };
        AiJobScheduler scheduler = new AiJobScheduler(
                jobStore, new AiJobHandlerRegistry(List.of(testHandler)));

        assertThat(scheduler.pollOnce()).isEqualTo(1);
        assertThat(transactionWasOpen.get()).isFalse();
        assertThat(jobRepository.findById(handledId).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.DONE);
    }

    @Test
    void durablePendingStateSurvivesRecreatedWorkerService() {
        UUID jobId = insertJob(AiJobStatus.PENDING, BASE.minusSeconds(1), 0, 3,
                null, null);
        AiJobStore recreatedWorker = new AiJobStore(
                claimRepository, aiProperties, transactionManager, aiClock, "worker-recreated");

        assertThat(recreatedWorker.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE))
                .singleElement().satisfies(claim -> {
                    assertThat(claim.id()).isEqualTo(jobId);
                    assertThat(claim.leaseToken()).startsWith("worker-recreated:");
                });
    }

    private List<AiJobClaim> claimAfter(CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, BASE);
    }

    private AiJobEnqueueResult enqueueAfter(CyclicBarrier barrier, AiJobSchedule schedule)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return jobStore.enqueueActive(schedule);
    }

    private UUID insertJob(AiJobStatus status, Instant runAt, int attempts, int maxAttempts,
            Instant leaseUntil, String lockedBy) {
        return insertJob(AiJobType.DOCUMENT_INDEX, status, runAt, attempts, maxAttempts,
                leaseUntil, lockedBy);
    }

    private UUID insertJob(AiJobType jobType, AiJobStatus status, Instant runAt, int attempts,
            int maxAttempts, Instant leaseUntil, String lockedBy) {
        AiJob job = new AiJob(jobType, status,
                "job-test:" + UUID.randomUUID(), runAt, maxAttempts);
        job.setAttemptCount(attempts);
        job.setLeaseUntil(leaseUntil);
        job.setLockedBy(lockedBy);
        return jobRepository.saveAndFlush(job).getId();
    }

    private long countByDedup(String dedupKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_jobs WHERE dedup_key = ?", Long.class, dedupKey);
    }
}
