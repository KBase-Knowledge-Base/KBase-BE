package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
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
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobEnqueueResult;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.job.AiJobExecutionResult;
import com.kbase.ai.job.AiJobExecutionOutcome;
import com.kbase.ai.job.AiJobHandler;
import com.kbase.ai.job.DocumentIndexJobHandler;
import com.kbase.ai.extraction.ChunkedDocument;
import com.kbase.ai.extraction.DocumentChunk;
import com.kbase.ai.extraction.DocumentContentExtractor;
import com.kbase.ai.extraction.DocumentExtractionRequest;
import com.kbase.ai.extraction.ExtractedBlock;
import com.kbase.ai.extraction.ExtractedDocument;
import com.kbase.ai.extraction.SourceLocation;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.repository.AiJobClaimRepository;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.config.AiProperties;
import com.kbase.ai.service.AiJobHandlerRegistry;
import com.kbase.ai.service.AiJobScheduler;
import com.kbase.ai.service.AiJobStore;
import com.kbase.ai.service.DocumentAiIndexPersistenceService;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.storage.service.StorageService;

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

    @Autowired
    private DocumentRepository documentRepositoryBean;

    @Autowired
    private DocumentAiIndexRepository documentAiIndexRepositoryBean;

    @Autowired
    private DocumentAiIndexPersistenceService indexPersistence;

    private final DocumentContentExtractor extractor = org.mockito.Mockito.mock(DocumentContentExtractor.class);
    private final StructureAwareDocumentChunker chunker = org.mockito.Mockito.mock(StructureAwareDocumentChunker.class);
    private final AiEmbeddingModel embeddings = org.mockito.Mockito.mock(AiEmbeddingModel.class);
    private final StorageService storage = org.mockito.Mockito.mock(StorageService.class);

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
        // Terminal transitions validate the lease against the store clock, so the
        // claim uses wall-clock time and the lease is therefore wall-clock valid.
        AiJobClaim first = jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                Instant.now()).getFirst();
        assertThat(first.id()).isEqualTo(jobId);
        Instant retryAt = Instant.now().plusSeconds(30);
        assertThat(jobStore.markRetry(first, retryAt, "TEMPORARY_FAILURE")).isTrue();

        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                Instant.now().plusSeconds(10))).isEmpty();
        assertThat(jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                Instant.now().plusSeconds(31))).singleElement().extracting(AiJobClaim::id)
                .isEqualTo(jobId);
    }

    @Test
    void staleLeaseGetsNewTokenAndOldWorkerCannotComplete() {
        UUID jobId = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(10), 1, 3,
                BASE.minusSeconds(1), "worker-a:old-token");
        AiJobClaim reclaimed = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, Instant.now()).getFirst();

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

            AiJobClaim claim = jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1,
                    Instant.now()).getFirst();
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

    @Test
    void expiredUnreclaimedOwnerCannotTerminateJobWhileTokenStillMatches() {
        UUID doneJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                BASE.minusSeconds(5), "worker-a:expired-done");
        UUID retryJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                BASE.minusSeconds(5), "worker-a:expired-retry");
        UUID failedJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                BASE.minusSeconds(5), "worker-a:expired-failed");

        assertThat(jobStore.markDone(claim(doneJob, "worker-a:expired-done"))).isFalse();
        assertThat(jobStore.markRetry(claim(retryJob, "worker-a:expired-retry"),
                BASE.plusSeconds(30), "TRANSIENT_FAILURE")).isFalse();
        assertThat(jobStore.markFailed(claim(failedJob, "worker-a:expired-failed"),
                "PROCESSING_ERROR")).isFalse();

        assertThat(jobRepository.findById(doneJob).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.PROCESSING);
        assertThat(jobRepository.findById(retryJob).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.PROCESSING);
        assertThat(jobRepository.findById(failedJob).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.PROCESSING);
        assertThat(jobRepository.findById(doneJob).orElseThrow().getLockedBy())
                .isEqualTo("worker-a:expired-done");
    }

    @Test
    void validOwnerCanFailAndTerminalJobsCannotBeReopened() {
        UUID validLeaseJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                Instant.now().plusSeconds(60), "worker-a:valid");
        AiJobClaim validClaim = new AiJobClaim(validLeaseJob, AiJobType.DOCUMENT_INDEX, null,
                null, null, "job-test:" + validLeaseJob, null, BASE.minusSeconds(20), 1, 3,
                Instant.now().plusSeconds(60), "worker-a:valid");
        assertThat(jobStore.markFailed(validClaim, "EXTRACTION_FAILED")).isTrue();
        AiJob failed = jobRepository.findById(validLeaseJob).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AiJobStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("EXTRACTION_FAILED");

        // Terminal states are final: no owner, token or lease state can reopen them.
        assertThat(jobStore.markDone(validClaim)).isFalse();
        assertThat(jobStore.markRetry(validClaim, BASE.plusSeconds(30), "X")).isFalse();
        assertThat(jobStore.markFailed(validClaim, "X")).isFalse();
        assertThat(jobRepository.findById(validLeaseJob).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.FAILED);
    }

    @Test
    void leaseRenewalExtendsOnlyLiveOwnedProcessingLeases() {
        Instant liveLease = Instant.now().plusSeconds(30);
        Instant expiredLease = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .minusSeconds(5);
        UUID liveJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                liveLease, "worker-a:live");
        UUID expiredJob = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(20), 1, 3,
                expiredLease, "worker-a:expired");
        UUID doneJob = insertJob(AiJobStatus.DONE, BASE.minusSeconds(20), 1, 3,
                liveLease, "worker-a:done");
        UUID retryJob = insertJob(AiJobStatus.RETRY, BASE.minusSeconds(20), 1, 3,
                null, null);

        AiJobClaim live = claimWithLease(liveJob, "worker-a:live", liveLease);
        AiJobClaim expired = claimWithLease(expiredJob, "worker-a:expired", expiredLease);
        AiJobClaim done = claimWithLease(doneJob, "worker-a:done", liveLease);
        AiJobClaim retry = new AiJobClaim(retryJob, AiJobType.DOCUMENT_INDEX, null, null, null,
                "job-test:" + retryJob, null, BASE.minusSeconds(20), 1, 3, null, "worker-a:retry");

        assertThat(jobStore.renewLease(live)).isTrue();
        assertThat(jobStore.renewLease(expired)).isFalse();
        assertThat(jobStore.renewLease(done)).isFalse();
        assertThat(jobStore.renewLease(retry)).isFalse();

        AiJob renewed = jobRepository.findById(liveJob).orElseThrow();
        assertThat(renewed.getStatus()).isEqualTo(AiJobStatus.PROCESSING);
        // The renewed lease is bounded: now + configured lease timeout, not cumulative.
        assertThat(renewed.getLeaseUntil()).isAfter(Instant.now().plusSeconds(60));
        assertThat(renewed.getLeaseUntil())
                .isBefore(Instant.now().plusSeconds(aiProperties.getWorker().getLeaseTimeout()
                        .toSeconds() + 30));
        // The expired row stays exactly as it was: reclaimable, not revived.
        assertThat(jobRepository.findById(expiredJob).orElseThrow().getLeaseUntil())
                .isEqualTo(expiredLease);
        assertThat(jobRepository.findById(doneJob).orElseThrow().getStatus())
                .isEqualTo(AiJobStatus.DONE);
    }

    @Test
    void oldTokenCannotRenewAfterAnotherWorkerReclaims() {
        UUID jobId = insertJob(AiJobStatus.PROCESSING, BASE.minusSeconds(10), 1, 3,
                Instant.now().minusSeconds(1), "worker-a:old");
        AiJobClaim reclaimed = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, Instant.now()).getFirst();
        AiJobClaim oldClaim = new AiJobClaim(jobId, AiJobType.DOCUMENT_INDEX, null, null, null,
                "old", null, BASE.minusSeconds(10), 1, 3, Instant.now().minusSeconds(1),
                "worker-a:old");

        assertThat(jobStore.renewLease(oldClaim)).isFalse();
        assertThat(jobStore.renewLease(reclaimed)).isTrue();
        assertThat(jobRepository.findById(jobId).orElseThrow().getLockedBy())
                .isEqualTo(reclaimed.leaseToken());
    }

    private AiJobClaim claimWithLease(UUID jobId, String leaseToken, Instant leaseUntil) {
        return new AiJobClaim(jobId, AiJobType.DOCUMENT_INDEX, null, null, null,
                "job-test:" + jobId, null, BASE.minusSeconds(20), 1, 3, leaseUntil, leaseToken);
    }

    @Test
    void expiredOwnerSuccessOutcomeCannotDoneJobWhileIndexStaysProcessing() {
        Fixture fixture = fixture();
        AiJobSchedule schedule = new AiJobSchedule(AiJobType.DOCUMENT_INDEX, fixture.projectId(),
                fixture.documentId(), null, "document-index:" + fixture.documentId() + ":v1",
                "{\"schemaVersion\":1}", null, 3);
        jobStore.enqueueActive(schedule);
        AiJobClaim claim = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, Instant.now()).getFirst();

        // The lease expires while the handler is working (slow provider window):
        // beginProcessing succeeds on the live lease, then the fake embedding
        // expires the stored lease before stage/activate run.
        when(embeddings.embed(any(AiEmbeddingRequest.class))).thenAnswer(invocation -> {
            jdbcTemplate.update("UPDATE ai_jobs SET lease_until = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minusSeconds(1)), claim.id());
            return new com.kbase.ai.provider.model.AiEmbeddingResult(
                    java.util.Collections.nCopies(768, 0.25));
        });
        DocumentIndexJobHandler handler = new DocumentIndexJobHandler(mockDocuments(fixture),
                mockIndexes(fixture), new com.kbase.ai.service.AiDocumentSupportPolicy(),
                extractor, chunker, embeddings, storage, indexPersistence,
                jobStore, new com.kbase.ai.observability.AiObservability(),
                new com.kbase.config.properties.UploadProperties(), aiProperties, aiClock);

        // The scheduler maps a handler SUCCESS outcome to jobStore.markDone(claim);
        // the same production finalization call is exercised directly because the
        // job is already claimed here (a poll would not re-claim it).
        AiJobExecutionResult result = handler.handle(claim);
        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.SUCCESS);

        // The expired owner must not persist DONE: the job stays reclaimable and
        // the index stays PROCESSING for normal stale recovery.
        AiJob job = jobRepository.findById(claim.id()).orElseThrow();
        assertThat(job.getStatus()).isNotEqualTo(AiJobStatus.DONE);
        assertThat(job.getStatus()).isEqualTo(AiJobStatus.PROCESSING);
        assertThat(countChunks(fixture, 1)).isZero();
        Long indexStatus = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ai_indexes WHERE document_id = ? AND status = 'PROCESSING'",
                Long.class, fixture.documentId());
        assertThat(indexStatus).isEqualTo(1L);
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

    private AiJobClaim claim(UUID jobId, String leaseToken) {
        return new AiJobClaim(jobId, AiJobType.DOCUMENT_INDEX, null, null, null,
                "job-test:" + jobId, null, BASE.minusSeconds(20), 1, 3,
                BASE.minusSeconds(5), leaseToken);
    }

    /** Raw-SQL document + PENDING index fixture mirroring the M5 persistence tests. */
    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                + "VALUES (?, ?, 'hash', 'M0 User', 'USER', 'ACTIVE')",
                userId, "m0-job-" + UUID.randomUUID() + "@example.com");
        jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                "M0 job project " + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, ?, 'M0 document', 'm0.txt', 'DOCUMENT', 'txt', 'text/plain', 5, ?)",
                documentId, projectId, userId,
                "projects/" + projectId + "/documents/" + documentId + ".txt");
        jdbcTemplate.update("INSERT INTO document_ai_indexes (document_id, project_id, status, active_version, "
                + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                + "VALUES (?, ?, 'PENDING', NULL, 1, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, projectId);
        return new Fixture(userId, projectId, documentId);
    }

    private DocumentRepository mockDocuments(Fixture fixture) {
        DocumentRepository documents = org.mockito.Mockito.mock(DocumentRepository.class);
        com.kbase.document.entity.Document document =
                org.mockito.Mockito.mock(com.kbase.document.entity.Document.class);
        com.kbase.project.entity.Project project =
                org.mockito.Mockito.mock(com.kbase.project.entity.Project.class);
        when(project.getId()).thenReturn(fixture.projectId());
        when(document.getId()).thenReturn(fixture.documentId());
        when(document.getProject()).thenReturn(project);
        when(document.getSizeBytes()).thenReturn(5L);
        when(document.getExtension()).thenReturn("txt");
        when(document.getStorageKey()).thenReturn(
                "projects/" + fixture.projectId() + "/documents/" + fixture.documentId() + ".txt");
        when(document.getOriginalFilename()).thenReturn("m0.txt");
        when(document.getMimeType()).thenReturn("text/plain");
        when(document.getDisplayName()).thenReturn("M0 document");
        when(documents.findByIdAndProjectId(fixture.documentId(), fixture.projectId()))
                .thenReturn(java.util.Optional.of(document));
        when(storage.get(document.getStorageKey())).thenReturn(new com.kbase.storage.model.StoredResource(
                new java.io.ByteArrayInputStream("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                5L, "text/plain"));
        when(extractor.extract(any(), any(DocumentExtractionRequest.class))).thenReturn(
                new ExtractedDocument(List.of(new ExtractedBlock("hello", SourceLocation.none()))));
        when(chunker.chunk(any(ExtractedDocument.class))).thenReturn(new ChunkedDocument("chunk-v1",
                List.of(new DocumentChunk(0, "hello", SourceLocation.none(), 1, "hash"))));
        return documents;
    }

    private DocumentAiIndexRepository mockIndexes(Fixture fixture) {
        DocumentAiIndexRepository indexes = org.mockito.Mockito.mock(DocumentAiIndexRepository.class);
        when(indexes.findByDocumentIdAndProjectId(fixture.documentId(), fixture.projectId()))
                .thenReturn(java.util.Optional.of(new com.kbase.ai.entity.DocumentAiIndex(
                        fixture.documentId(), fixture.projectId(), DocumentAiIndexStatus.PENDING,
                        1, "chunk-v1", "gemini-embedding-2", 768)));
        return indexes;
    }

    private long countChunks(Fixture fixture, long version) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_ai_chunks "
                        + "WHERE document_id = ? AND project_id = ? AND index_version = ?",
                Long.class, fixture.documentId(), fixture.projectId(), version);
    }

    private record Fixture(UUID userId, UUID projectId, UUID documentId) {
    }
}
