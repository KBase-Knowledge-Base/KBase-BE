package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.service.DocumentAiIndexPersistenceService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL proof for M5 staging, atomic activation and lease/delete safety. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class DocumentAiIndexPersistenceIntegrationTest {

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
        registry.add("kbase.jwt.signing-secret", () -> "m5-index-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m5-index-otp-secret");
        registry.add("kbase.mail.username", () -> "m5-index@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m5-index-mail-password");
        registry.add("kbase.storage.access-key", () -> "m5-index-access-key");
        registry.add("kbase.storage.secret-key", () -> "m5-index-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentAiIndexPersistenceService persistence;

    @Autowired
    private AiVectorRepository vectorRepository;

    @Autowired
    private DocumentAiIndexRepository indexRepository;

    @Test
    void initialPendingVersionReachesReadyWithProjectScopedChunks() {
        Fixture fixture = fixture(DocumentAiIndexStatus.PENDING, 0, 1);
        AiJobClaim claim = claim(fixture, 1, activeLease());

        assertThat(persistence.beginProcessing(claim, 1)).isTrue();
        assertThat(persistence.stage(claim, 1, List.of(chunk(fixture, 1, "initial")))).isTrue();
        assertThat(persistence.activate(claim, 1, "source-hash-v1")).isTrue();

        DocumentAiIndex index = indexRepository.findByDocumentIdAndProjectId(
                fixture.documentId(), fixture.projectId()).orElseThrow();
        assertThat(index.getStatus()).isEqualTo(DocumentAiIndexStatus.READY);
        assertThat(index.getActiveVersion()).isEqualTo(1L);
        assertThat(index.getSourceHash()).isEqualTo("source-hash-v1");
        assertThat(index.getIndexedAt()).isNotNull();
        assertThat(countChunks(fixture, 1)).isEqualTo(1);
    }

    @Test
    void activatesStagedVersionAndCleansOnlyAfterTheSwitch() {
        Fixture fixture = fixture(DocumentAiIndexStatus.READY, 1, 2);
        vectorRepository.insertDocumentChunk(chunk(fixture, 1, "old"));
        AiJobClaim claim = claim(fixture, 2, activeLease());
        assertThat(persistence.beginProcessing(claim, 2)).isTrue();
        assertThat(persistence.stage(claim, 2, List.of(chunk(fixture, 2, "new")))).isTrue();

        assertThat(persistence.activate(claim, 2, "source-hash-v2")).isTrue();

        DocumentAiIndex index = indexRepository.findByDocumentIdAndProjectId(
                fixture.documentId(), fixture.projectId()).orElseThrow();
        assertThat(index.getStatus()).isEqualTo(DocumentAiIndexStatus.READY);
        assertThat(index.getActiveVersion()).isEqualTo(2L);
        assertThat(index.getSourceHash()).isEqualTo("source-hash-v2");
        assertThat(countChunks(fixture, 1)).isZero();
        assertThat(countChunks(fixture, 2)).isEqualTo(1);

        // A reclaimed/crashed delivery that reaches this path again is
        // idempotent and does not duplicate the active generation.
        assertThat(persistence.activate(claim, 2, "source-hash-v2")).isTrue();
        assertThat(countChunks(fixture, 2)).isEqualTo(1);
    }

    @Test
    void failedReplacementKeepsLastGoodVersionAndExpiredLeaseCannotActivate() {
        Fixture failedFixture = fixture(DocumentAiIndexStatus.READY, 1, 2);
        vectorRepository.insertDocumentChunk(chunk(failedFixture, 1, "last-good"));
        AiJobClaim failedClaim = claim(failedFixture, 2, activeLease());
        assertThat(persistence.beginProcessing(failedClaim, 2)).isTrue();
        assertThat(persistence.markFailure(failedClaim, 2, "AI_SERVICE_ERROR",
                "AI_PROVIDER_UNAVAILABLE")).isTrue();
        DocumentAiIndex failed = indexRepository.findByDocumentIdAndProjectId(
                failedFixture.documentId(), failedFixture.projectId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DocumentAiIndexStatus.FAILED);
        assertThat(failed.getActiveVersion()).isEqualTo(1L);
        assertThat(countChunks(failedFixture, 1)).isEqualTo(1);

        Fixture expiredFixture = fixture(DocumentAiIndexStatus.PENDING, 0, 1);
        AiJobClaim expiredClaim = claim(expiredFixture, 1, Instant.now().minusSeconds(30));
        assertThat(persistence.beginProcessing(expiredClaim, 1)).isFalse();
    }

    @Test
    void documentDeleteBeforeActivationLeavesNoLateChunks() {
        Fixture fixture = fixture(DocumentAiIndexStatus.PENDING, 0, 1);
        AiJobClaim claim = claim(fixture, 1, activeLease());
        assertThat(persistence.beginProcessing(claim, 1)).isTrue();
        assertThat(persistence.stage(claim, 1, List.of(chunk(fixture, 1, "staged")))).isTrue();
        jdbcTemplate.update("DELETE FROM documents WHERE id = ? AND project_id = ?",
                fixture.documentId(), fixture.projectId());

        assertThat(persistence.activate(claim, 1, "late-hash")).isFalse();
        assertThat(countRows("document_ai_indexes", fixture.documentId())).isZero();
        assertThat(countRows("document_ai_chunks", fixture.documentId())).isZero();
    }

    @Test
    void staleLeaseCannotActivateAfterAnotherWorkerReclaimsTheJob() {
        Fixture fixture = fixture(DocumentAiIndexStatus.PENDING, 0, 1);
        AiJobClaim staleClaim = claim(fixture, 1, activeLease());
        assertThat(persistence.beginProcessing(staleClaim, 1)).isTrue();
        assertThat(persistence.stage(staleClaim, 1, List.of(chunk(fixture, 1, "stale")))).isTrue();

        String currentLeaseToken = "m5-worker-reclaimed:" + UUID.randomUUID();
        Instant currentLeaseUntil = Instant.now().plusSeconds(120);
        jdbcTemplate.update("UPDATE ai_jobs SET locked_by = ?, lease_until = ? WHERE id = ?",
                currentLeaseToken, Timestamp.from(currentLeaseUntil), staleClaim.id());
        AiJobClaim currentClaim = new AiJobClaim(
                staleClaim.id(), staleClaim.jobType(), staleClaim.projectId(), staleClaim.documentId(),
                staleClaim.userId(), staleClaim.dedupKey(), staleClaim.payload(), staleClaim.runAt(),
                staleClaim.attemptCount() + 1, staleClaim.maxAttempts(), currentLeaseUntil,
                currentLeaseToken);

        assertThat(persistence.activate(staleClaim, 1, "stale-hash")).isFalse();
        assertThat(persistence.activate(currentClaim, 1, "current-hash")).isTrue();
        assertThat(countChunks(fixture, 1)).isEqualTo(1);
    }

    @Test
    void projectDeleteBeforeActivationCannotResurrectIndexOrChunks() {
        Fixture fixture = fixture(DocumentAiIndexStatus.PENDING, 0, 1);
        AiJobClaim claim = claim(fixture, 1, activeLease());
        assertThat(persistence.beginProcessing(claim, 1)).isTrue();
        assertThat(persistence.stage(claim, 1, List.of(chunk(fixture, 1, "project delete")))).isTrue();
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", fixture.projectId());

        assertThat(persistence.activate(claim, 1, "late-project-hash")).isFalse();
        assertThat(countRows("document_ai_indexes", fixture.documentId())).isZero();
        assertThat(countRows("document_ai_chunks", fixture.documentId())).isZero();
    }

    private Fixture fixture(DocumentAiIndexStatus status, long activeVersion, long desiredVersion) {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                + "VALUES (?, ?, 'hash', 'M5 User', 'USER', 'ACTIVE')",
                userId, "m5-" + UUID.randomUUID() + "@example.com");
        jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                "M5 project " + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, ?, 'M5 document', 'm5.txt', 'DOCUMENT', 'txt', 'text/plain', 5, ?)",
                documentId, projectId, userId,
                "projects/" + projectId + "/documents/" + documentId + ".txt");
        jdbcTemplate.update("INSERT INTO document_ai_indexes (document_id, project_id, status, active_version, "
                + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                + "VALUES (?, ?, ?, ?, ?, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, projectId, status.name(), activeVersion == 0 ? null : activeVersion, desiredVersion);
        return new Fixture(userId, projectId, documentId);
    }

    private AiJobClaim claim(Fixture fixture, long version, Instant leaseUntil) {
        UUID jobId = UUID.randomUUID();
        String leaseToken = "m5-worker:" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("INSERT INTO ai_jobs (id, job_type, status, project_id, document_id, dedup_key, "
                + "run_at, attempt_count, max_attempts, lease_until, locked_by) "
                + "VALUES (?, 'DOCUMENT_INDEX', 'PROCESSING', ?, ?, ?, ?, 1, 3, ?, ?)",
                jobId, fixture.projectId(), fixture.documentId(),
                "document-index:" + fixture.documentId() + ":v" + version,
                Timestamp.from(now), Timestamp.from(leaseUntil), leaseToken);
        return new AiJobClaim(jobId, AiJobType.DOCUMENT_INDEX, fixture.projectId(), fixture.documentId(),
                fixture.userId(), "document-index:" + fixture.documentId() + ":v" + version, "{}", now,
                1, 3, leaseUntil, leaseToken);
    }

    private DocumentAiChunkInsert chunk(Fixture fixture, long version, String content) {
        return new DocumentAiChunkInsert(UUID.randomUUID(), fixture.projectId(), fixture.documentId(),
                version, 0, content, null, null, null, 1, "hash-" + content,
                new float[AiVectorRepository.EMBEDDING_DIMENSIONS]);
    }

    private long countChunks(Fixture fixture, long version) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM document_ai_chunks "
                + "WHERE document_id = ? AND project_id = ? AND index_version = ?",
                Long.class, fixture.documentId(), fixture.projectId(), version);
    }

    private long countRows(String table, UUID documentId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE document_id = ?",
                Long.class, documentId);
    }

    private static Instant activeLease() {
        return Instant.now().plusSeconds(120);
    }

    private record Fixture(UUID userId, UUID projectId, UUID documentId) {
    }
}
