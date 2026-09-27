package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiJobStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.extraction.ChunkedDocument;
import com.kbase.ai.extraction.DocumentChunk;
import com.kbase.ai.extraction.DocumentContentExtractor;
import com.kbase.ai.extraction.DocumentExtractionRequest;
import com.kbase.ai.extraction.ExtractedBlock;
import com.kbase.ai.extraction.ExtractedDocument;
import com.kbase.ai.extraction.SourceLocation;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.job.DocumentIndexJobHandler;
import com.kbase.ai.observability.AiObservability;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.service.AiJobStore;
import com.kbase.ai.service.DocumentAiIndexPersistenceService;
import com.kbase.config.properties.UploadProperties;
import com.kbase.storage.model.StoredResource;
import com.kbase.storage.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * M2 slow-work evidence on real PostgreSQL with a controllable worker clock:
 * indexing whose total duration exceeds the original lease keeps ownership
 * through bounded token-conditional renewals, while expired/reclaimed owners
 * can neither renew nor finish.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
@ActiveProfiles("local")
class DocumentIndexLeaseRenewalIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    private static final byte[] SOURCE_BYTES = "synthetic-renewal-fixture".getBytes(StandardCharsets.UTF_8);

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
        registry.add("kbase.postgres.database", POSTGRES::getDatabaseName);
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "m2-renewal-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m2-renewal-otp-secret");
        registry.add("kbase.mail.username", () -> "m2-renewal@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m2-renewal-mail-password");
        registry.add("kbase.storage.access-key", () -> "m2-renewal-access-key");
        registry.add("kbase.storage.secret-key", () -> "m2-renewal-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @TestConfiguration
    static class WorkerClockConfiguration {
        @Bean(name = "aiClock")
        Clock aiClock() {
            return WORKER_CLOCK;
        }
    }

    /** Mutable worker clock shared by every context bean and the test body. */
    static final AdjustableClock WORKER_CLOCK = new AdjustableClock(T0);

    @Autowired
    private AiJobStore jobStore;

    @Autowired
    private DocumentAiIndexPersistenceService indexPersistence;

    @Autowired
    private com.kbase.document.repository.DocumentRepository documentRepository;

    @Autowired
    private com.kbase.ai.repository.DocumentAiIndexRepository indexRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiProperties aiProperties;

    private final DocumentContentExtractor extractor = Mockito.mock(DocumentContentExtractor.class);
    private final StructureAwareDocumentChunker chunker = Mockito.mock(StructureAwareDocumentChunker.class);
    private final AiEmbeddingModel embeddings = Mockito.mock(AiEmbeddingModel.class);
    private final StorageService storage = Mockito.mock(StorageService.class);

    @BeforeEach
    void resetClockAndJobs() {
        WORKER_CLOCK.reset(T0);
        jdbcTemplate.update("DELETE FROM document_ai_chunks");
        jdbcTemplate.update("DELETE FROM document_ai_indexes");
        jdbcTemplate.update("DELETE FROM ai_jobs");
        jdbcTemplate.update("DELETE FROM documents");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void slowWorkBeyondOriginalLeaseKeepsOwnershipAndReachesReady() {
        Fixture fixture = fixture();
        AiJobClaim claim = enqueueAndClaim(fixture);
        // Two chunks with 90 seconds of provider work each: total T0+180s, while
        // the original lease window is T0+120s. Without renewal this must fail.
        when(embeddings.embed(any(AiEmbeddingRequest.class))).thenAnswer(invocation -> {
            WORKER_CLOCK.advance(Duration.ofSeconds(90));
            return vector();
        });
        stubExtraction(fixture, 2);

        DocumentIndexJobHandler handler = handler();
        var result = handler.handle(claim);
        assertThat(result.outcome()).isEqualTo(com.kbase.ai.job.AiJobExecutionOutcome.SUCCESS);

        assertThat(indexStatus(fixture)).isEqualTo("READY");
        assertThat(activeVersion(fixture)).isEqualTo(1L);
        assertThat(chunkCount(fixture, 1)).isEqualTo(2L);
        // The renewed lease stays bounded and the finalization succeeds for the
        // owner that legitimately renewed through the whole slow delivery.
        assertThat(jobStore.markDone(claim)).isTrue();
        assertThat(jobStatus(claim.id())).isEqualTo(AiJobStatus.DONE);
    }

    @Test
    void expiredUnreclaimedOwnerCannotRenewOrFinishAndRecoveryStillCompletes() {
        Fixture fixture = fixture();
        AiJobClaim claim = enqueueAndClaim(fixture);
        WORKER_CLOCK.advance(Duration.ofMinutes(3));

        assertThat(jobStore.renewLease(claim)).isFalse();
        when(embeddings.embed(any(AiEmbeddingRequest.class))).thenAnswer(invocation -> vector());
        stubExtraction(fixture, 1);
        var result = handler().handle(claim);
        assertThat(result.outcome()).isEqualTo(com.kbase.ai.job.AiJobExecutionOutcome.SUCCESS);
        assertThat(indexStatus(fixture)).isEqualTo("PENDING");
        assertThat(chunkCount(fixture, 1)).isZero();
        assertThat(jobStatus(claim.id())).isEqualTo(AiJobStatus.PROCESSING);
        assertThat(jobStore.markDone(claim)).isFalse();

        // Normal stale recovery reclaims the row and a current owner completes it.
        AiJobClaim reclaimed = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, WORKER_CLOCK.instant()).getFirst();
        assertThat(reclaimed.attemptCount()).isEqualTo(2);
        var recovery = handler().handle(reclaimed);
        assertThat(recovery.outcome()).isEqualTo(com.kbase.ai.job.AiJobExecutionOutcome.SUCCESS);
        assertThat(indexStatus(fixture)).isEqualTo("READY");
        assertThat(chunkCount(fixture, 1)).isEqualTo(1L);
        assertThat(jobStore.markDone(reclaimed)).isTrue();
    }

    @Test
    void oldTokenCannotRenewAfterAnotherWorkerReclaims() {
        Fixture fixture = fixture();
        AiJobClaim oldClaim = enqueueAndClaim(fixture);
        WORKER_CLOCK.advance(Duration.ofMinutes(3));
        AiJobClaim reclaimed = jobStore.claimDueJobs(
                Set.of(AiJobType.DOCUMENT_INDEX), 1, WORKER_CLOCK.instant()).getFirst();

        assertThat(jobStore.renewLease(oldClaim)).isFalse();
        assertThat(jobStore.renewLease(reclaimed)).isTrue();
        assertThat(lockedBy(oldClaim.id())).isEqualTo(reclaimed.leaseToken());
    }

    @Test
    void documentDeletedDuringWorkCannotResurrectIndexOrChunks() {
        Fixture fixture = fixture();
        AiJobClaim claim = enqueueAndClaim(fixture);
        when(embeddings.embed(any(AiEmbeddingRequest.class))).thenAnswer(invocation -> {
            WORKER_CLOCK.advance(Duration.ofSeconds(90));
            jdbcTemplate.update("DELETE FROM documents WHERE id = ?", fixture.documentId());
            return vector();
        });
        stubExtraction(fixture, 2);

        var result = handler().handle(claim);
        assertThat(result.outcome()).isEqualTo(com.kbase.ai.job.AiJobExecutionOutcome.SUCCESS);
        assertThat(chunkCount(fixture, 1)).isZero();
        assertThat(indexRowCount(fixture)).isZero();
        assertThat(chunkRowCount(fixture)).isZero();
    }

    private DocumentIndexJobHandler handler() {
        return new DocumentIndexJobHandler(documentRepository, indexRepository,
                new com.kbase.ai.service.AiDocumentSupportPolicy(), extractor, chunker,
                embeddings, storage, indexPersistence, jobStore, new AiObservability(),
                new UploadProperties(), aiProperties, WORKER_CLOCK);
    }

    private void stubExtraction(Fixture fixture, int chunkCount) {
        when(storage.get(any())).thenReturn(new StoredResource(
                new ByteArrayInputStream(SOURCE_BYTES), SOURCE_BYTES.length, "text/plain"));
        when(extractor.extract(any(), any(DocumentExtractionRequest.class))).thenReturn(
                new ExtractedDocument(List.of(new ExtractedBlock("content", SourceLocation.none()))));
        List<DocumentChunk> chunks = new java.util.ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            chunks.add(new DocumentChunk(index, "content-" + index, SourceLocation.none(), 1,
                    "hash-" + index));
        }
        when(chunker.chunk(any(ExtractedDocument.class))).thenReturn(
                new ChunkedDocument("chunk-v1", chunks));
    }

    private AiJobClaim enqueueAndClaim(Fixture fixture) {
        jobStore.enqueueActive(new AiJobSchedule(AiJobType.DOCUMENT_INDEX, fixture.projectId(),
                fixture.documentId(), null, "document-index:" + fixture.documentId() + ":v1",
                "{\"schemaVersion\":1}", null, 3));
        return jobStore.claimDueJobs(Set.of(AiJobType.DOCUMENT_INDEX), 1, WORKER_CLOCK.instant())
                .getFirst();
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                + "VALUES (?, ?, 'hash', 'M2 User', 'USER', 'ACTIVE')",
                userId, "m2-" + UUID.randomUUID() + "@example.com");
        jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                "M2 renewal project " + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                        + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                        + "VALUES (?, ?, ?, 'M2 document', 'm2.txt', 'DOCUMENT', 'txt', 'text/plain', ?, ?)",
                documentId, projectId, userId, (long) SOURCE_BYTES.length,
                "projects/" + projectId + "/documents/" + documentId + ".txt");
        jdbcTemplate.update("INSERT INTO document_ai_indexes (document_id, project_id, status, active_version, "
                + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                + "VALUES (?, ?, 'PENDING', NULL, 1, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, projectId);
        return new Fixture(userId, projectId, documentId);
    }

    private String indexStatus(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM document_ai_indexes WHERE document_id = ?",
                String.class, fixture.documentId());
    }

    private Long activeVersion(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT active_version FROM document_ai_indexes WHERE document_id = ?",
                Long.class, fixture.documentId());
    }

    private Long chunkCount(Fixture fixture, long version) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_ai_chunks "
                + "WHERE document_id = ? AND index_version = ?", Long.class,
                fixture.documentId(), version);
    }

    private Long indexRowCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ai_indexes WHERE document_id = ?",
                Long.class, fixture.documentId());
    }

    private Long chunkRowCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ai_chunks WHERE document_id = ?",
                Long.class, fixture.documentId());
    }

    private AiJobStatus jobStatus(UUID jobId) {
        return AiJobStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_jobs WHERE id = ?", String.class, jobId));
    }

    private String lockedBy(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_by FROM ai_jobs WHERE id = ?", String.class, jobId);
    }

    private AiEmbeddingResult vector() {
        return new AiEmbeddingResult(Collections.nCopies(768, 0.25));
    }

    /** Bridges the two repository beans behind real JPA queries for fixtures. */
    private record Fixture(UUID userId, UUID projectId, UUID documentId) {
    }

    private static final class AdjustableClock extends Clock {
        private volatile Instant now;

        private AdjustableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        private void reset(Instant start) {
            now = start;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
