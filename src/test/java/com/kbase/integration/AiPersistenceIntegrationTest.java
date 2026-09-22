package com.kbase.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kbase.ai.entity.AiConversation;
import com.kbase.ai.entity.AiJob;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.AiMessageRole;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.repository.AiConversationQuotaRepository;
import com.kbase.ai.repository.AiConversationRepository;
import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.repository.AiMessageRepository;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.repository.DocumentAiChunkMatch;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.repository.GuideChunkInsert;
import com.kbase.project.entity.Project;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;
import com.pgvector.PGvector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL/pgvector persistence and database-guard verification for M2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class AiPersistenceIntegrationTest {

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
        registry.add("kbase.jwt.signing-secret", () -> "test-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "test-otp-hash-secret");
        registry.add("kbase.mail.username", () -> "test@example.invalid");
        registry.add("kbase.mail.app-password", () -> "test-mail-app-password");
        registry.add("kbase.storage.access-key", () -> "test-access-key");
        registry.add("kbase.storage.secret-key", () -> "test-storage-secret-key");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiVectorRepository aiVectorRepository;

    @Autowired
    private AiConversationRepository aiConversationRepository;

    @Autowired
    private AiConversationQuotaRepository aiConversationQuotaRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private AiJobRepository aiJobRepository;

    @Autowired
    private DocumentAiIndexRepository documentAiIndexRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearPersistenceContext() {
        // Each test uses UUID-scoped rows. No global truncate is used because it
        // would hide the real FK/delete semantics under test.
    }

    @Test
    void vectorRepositoryRejectsWrongDimensionsAndDatabaseRejectsInvalidVector() {
        UUID userId = insertUser();
        UUID projectId = insertProject("vector-dimension-project");
        UUID documentId = insertDocument(projectId, userId, "dimension-document");

        assertThatThrownBy(() -> aiVectorRepository.insertDocumentChunk(new DocumentAiChunkInsert(
                UUID.randomUUID(), projectId, documentId, 1, 0, "invalid", null, null,
                null, null, "invalid-hash", new float[767])))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("768");

        ConnectionCallback<Void> invalidDatabaseInsert = connection -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO document_ai_chunks "
                            + "(id, project_id, document_id, index_version, chunk_index, content, "
                            + "content_hash, embedding) VALUES (?, ?, ?, 1, 0, 'invalid', 'hash', ?::vector)")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, projectId);
                statement.setObject(3, documentId);
                statement.setObject(4, new PGvector(axis(0, 767)));
                statement.executeUpdate();
            }
            return null;
        };

        assertThatThrownBy(() -> jdbcTemplate.execute(invalidDatabaseInsert))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void projectScopedVectorQueryExcludesForeignAndInactiveChunksInsideSql() {
        UUID userId = insertUser();
        UUID projectA = insertProject("vector-project-a");
        UUID projectB = insertProject("vector-project-b");
        UUID documentA = insertDocument(projectA, userId, "vector-document-a");
        UUID documentB = insertDocument(projectB, userId, "vector-document-b");
        insertDocumentAiIndex(documentA, projectA, "READY", 1, 2);
        insertDocumentAiIndex(documentB, projectB, "READY", 1, 1);

        aiVectorRepository.insertDocumentChunk(chunk(projectA, documentA, 1, "A active", axis(1)));
        aiVectorRepository.insertDocumentChunk(chunk(projectA, documentA, 2, "A inactive", axis(0)));
        aiVectorRepository.insertDocumentChunk(chunk(projectB, documentB, 1, "B perfect", axis(0)));

        List<DocumentAiChunkMatch> matches = aiVectorRepository
                .findNearestDocumentChunks(projectA, axis(0), 10);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).projectId()).isEqualTo(projectA);
        assertThat(matches.get(0).documentId()).isEqualTo(documentA);
        assertThat(matches.get(0).indexVersion()).isEqualTo(1);
        assertThat(matches.get(0).content()).isEqualTo("A active");
    }

    @Test
    void crossProjectDocumentChunkForeignKeyRejectsSecurityTrap() {
        UUID userId = insertUser();
        UUID projectA = insertProject("chunk-fk-project-a");
        UUID projectB = insertProject("chunk-fk-project-b");
        UUID documentA = insertDocument(projectA, userId, "chunk-fk-document-a");

        assertThatThrownBy(() -> aiVectorRepository.insertDocumentChunk(
                chunk(projectB, documentA, 1, "cross-project", axis(0))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_document_ai_chunks_document_same_project");
    }

    @Test
    void citationSurvivesDocumentAndChunkCascadeWithLiveReferencesNulled() {
        UUID userId = insertUser();
        UUID projectId = insertProject("citation-delete-project");
        UUID documentId = insertDocument(projectId, userId, "citation-document");
        insertDocumentAiIndex(documentId, projectId, "READY", 1, 1);
        UUID chunkId = UUID.randomUUID();
        aiVectorRepository.insertDocumentChunk(new DocumentAiChunkInsert(
                chunkId, projectId, documentId, 1, 0, "citation content", null, null,
                "Section", 4, "citation-hash", axis(0)));
        UUID conversationId = insertConversation(projectId, userId, "citation conversation");
        UUID assistantMessageId = insertMessage(
                conversationId, "ASSISTANT", "answer", "COMPLETED", "GROUNDED");
        UUID snapshotDocumentId = documentId;
        jdbcTemplate.update(
                "INSERT INTO ai_message_sources "
                        + "(assistant_message_id, source_order, document_id, chunk_id, "
                        + "document_id_snapshot, document_name_snapshot, page_number_snapshot, "
                        + "retrieval_score) VALUES (?, 0, ?, ?, ?, ?, 1, 0.75)",
                assistantMessageId, documentId, chunkId, snapshotDocumentId, "Citation document");

        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId);

        Map<String, Object> citation = jdbcTemplate.queryForMap(
                "SELECT document_id, chunk_id, document_id_snapshot, document_name_snapshot "
                        + "FROM ai_message_sources WHERE assistant_message_id = ?",
                assistantMessageId);
        assertThat(citation.get("document_id")).isNull();
        assertThat(citation.get("chunk_id")).isNull();
        assertThat(citation.get("document_id_snapshot")).isEqualTo(snapshotDocumentId);
        assertThat(citation.get("document_name_snapshot")).isEqualTo("Citation document");
        assertThat(count("document_ai_indexes", "document_id", documentId)).isZero();
        assertThat(count("document_ai_chunks", "document_id", documentId)).isZero();
    }

    @Test
    void membershipDeleteRetainsConversationAndProjectDeleteCascadesAiData() {
        UUID userId = insertUser();
        UUID projectId = insertProject("ai-delete-project");
        insertMembership(projectId, userId);
        UUID documentId = insertDocument(projectId, userId, "project-delete-document");
        insertDocumentAiIndex(documentId, projectId, "READY", 1, 1);
        aiVectorRepository.insertDocumentChunk(chunk(projectId, documentId, 1, "project content", axis(0)));
        UUID conversationId = insertConversation(projectId, userId, "retained conversation");
        UUID messageId = insertMessage(conversationId, "ASSISTANT", "answer", "COMPLETED", "GROUNDED");
        jdbcTemplate.update(
                "INSERT INTO ai_message_sources "
                        + "(assistant_message_id, source_order, document_id_snapshot, "
                        + "document_name_snapshot) VALUES (?, 0, ?, 'Project document')",
                messageId, documentId);
        insertJob(projectId, documentId, userId, "document-index:" + documentId);

        jdbcTemplate.update(
                "DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                projectId, userId);
        assertThat(count("ai_conversations", "id", conversationId)).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", projectId);

        assertThat(count("document_ai_indexes", "project_id", projectId)).isZero();
        assertThat(count("document_ai_chunks", "project_id", projectId)).isZero();
        assertThat(count("ai_conversations", "project_id", projectId)).isZero();
        assertThat(count("ai_messages", "conversation_id", conversationId)).isZero();
        assertThat(count("ai_message_sources", "assistant_message_id", messageId)).isZero();
        assertThat(count("ai_jobs", "project_id", projectId)).isZero();
    }

    @Test
    void conversationDeleteCascadesMessagesAndCitationRows() {
        UUID userId = insertUser();
        UUID projectId = insertProject("conversation-delete-project");
        UUID conversationId = insertConversation(projectId, userId, "delete me");
        UUID assistantMessageId = insertMessage(
                conversationId, "ASSISTANT", "answer", "COMPLETED", "GROUNDED");
        jdbcTemplate.update(
                "INSERT INTO ai_message_sources "
                        + "(assistant_message_id, source_order, document_id_snapshot, "
                        + "document_name_snapshot) VALUES (?, 0, ?, 'Snapshot')",
                assistantMessageId, UUID.randomUUID());

        jdbcTemplate.update("DELETE FROM ai_conversations WHERE id = ?", conversationId);

        assertThat(count("ai_messages", "conversation_id", conversationId)).isZero();
        assertThat(count("ai_message_sources", "assistant_message_id", assistantMessageId)).isZero();
    }

    @Test
    void aiStatusVocabularyRejectsUnknownValuesAcrossLifecycleTables() {
        UUID userId = insertUser();
        UUID projectId = insertProject("status-vocabulary-project");
        UUID documentId = insertDocument(projectId, userId, "status-document");
        UUID conversationId = insertConversation(projectId, userId, "status conversation");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO document_ai_indexes "
                        + "(document_id, project_id, status, desired_version, chunking_version, "
                        + "embedding_model, embedding_dimensions) "
                        + "VALUES (?, ?, 'QUEUED', 1, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, projectId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_document_ai_indexes_status");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_messages "
                        + "(id, conversation_id, role, content, generation_status) "
                        + "VALUES (?, ?, 'ASSISTANT', 'answer', 'QUEUED')",
                UUID.randomUUID(), conversationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_messages_generation_status");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_jobs "
                        + "(id, job_type, status, dedup_key, run_at, max_attempts) "
                        + "VALUES (?, 'GUIDE_REINDEX', 'QUEUED', ?, CURRENT_TIMESTAMP, 3)",
                UUID.randomUUID(), "invalid-status-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_jobs_status");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_guide_sources "
                        + "(id, source_key, content_hash, desired_version, status) "
                        + "VALUES (?, ?, 'hash', 1, 'QUEUED')",
                UUID.randomUUID(), "invalid-status-source-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_guide_sources_status");
    }

    @Test
    void guideSourceDeleteCascadesGuideChunks() {
        UUID sourceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_guide_sources "
                        + "(id, source_key, content_hash, active_version, desired_version, status) "
                        + "VALUES (?, ?, 'guide-hash', 1, 1, 'READY')",
                sourceId, "guide-source-" + UUID.randomUUID());
        UUID chunkId = UUID.randomUUID();
        aiVectorRepository.insertGuideChunk(new GuideChunkInsert(
                chunkId, sourceId, 1, 0, "guide content", "KBase > Help", 2,
                "guide-chunk-hash", axis(0)));

        assertThat(count("ai_guide_chunks", "guide_source_id", sourceId)).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM ai_guide_sources WHERE id = ?", sourceId);

        assertThat(count("ai_guide_chunks", "guide_source_id", sourceId)).isZero();
    }

    @Test
    void activeGenerationIndexAndStatusChecksAreDatabaseBackstops() {
        UUID userId = insertUser();
        UUID projectId = insertProject("generation-guard-project");
        UUID conversationId = insertConversation(projectId, userId, "generation conversation");

        insertMessage(conversationId, "ASSISTANT", null, "PROCESSING", null);
        assertThatThrownBy(() -> insertMessage(
                conversationId, "ASSISTANT", null, "PROCESSING", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ai_messages_active_generation");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_messages (id, conversation_id, role, generation_status) "
                        + "VALUES (?, ?, 'SYSTEM', 'COMPLETED')",
                UUID.randomUUID(), conversationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_messages_role");
    }

    @Test
    void jpaMappingsRepositoriesAndJsonJobPersistenceWorkAgainstFlywaySchema() {
        User user = userRepository.saveAndFlush(new User(
                uniqueEmail(), "password-hash", "Mapped AI User", SystemRole.USER, UserStatus.ACTIVE));
        Project project = projectRepository.saveAndFlush(new Project(
                "mapped-ai-project-" + UUID.randomUUID(), "AI persistence mapping"));
        AiConversation conversation = aiConversationRepository.saveAndFlush(
                new AiConversation(project, user, "Mapped conversation"));

        assertThat(aiConversationRepository.countByProjectIdAndCreatedByUserId(
                project.getId(), user.getId())).isEqualTo(1);

        aiMessageRepository.saveAndFlush(new com.kbase.ai.entity.AiMessage(
                conversation, AiMessageRole.USER, "Question", AiGenerationStatus.COMPLETED));

        AiJob job = new AiJob(AiJobType.GUIDE_REINDEX, AiJobStatus.PENDING,
                "guide-reindex:" + UUID.randomUUID(), Instant.now(), 3);
        job.setPayload("{\"source\":\"approved\"}");
        AiJob savedJob = aiJobRepository.saveAndFlush(job);
        assertThat(aiJobRepository.findById(savedJob.getId())).hasValueSatisfying(found -> {
            assertThat(found.getPayload()).contains("source", "approved");
            assertThat(found.getStatus()).isEqualTo(AiJobStatus.PENDING);
        });

        UUID documentId = insertDocument(project.getId(), user.getId(), "mapped-index-document");
        DocumentAiIndex index = documentAiIndexRepository.saveAndFlush(new DocumentAiIndex(
                documentId, project.getId(), DocumentAiIndexStatus.PENDING, 1,
                "chunk-v1", "gemini-embedding-2", 768));
        assertThat(documentAiIndexRepository.findByDocumentIdAndProjectId(
                documentId, project.getId())).hasValueSatisfying(found ->
                        assertThat(found.getStatus()).isEqualTo(DocumentAiIndexStatus.PENDING));
        assertThat(index.getEmbeddingDimensions()).isEqualTo(768);
    }

    @Test
    void conversationQuotaLockSerializesConcurrentCountThenInsertAttempts() throws Exception {
        UUID userId = insertUser();
        UUID projectId = insertProject("quota-lock-project");
        for (int i = 0; i < 4; i++) {
            insertConversation(projectId, userId, "existing-" + i);
        }

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> new TransactionTemplate(transactionManager)
                        .execute(status -> {
                            await(start);
                            if (aiConversationQuotaRepository.lockUserForConversationQuota(userId) == null) {
                                throw new IllegalStateException("quota user missing");
                            }
                            long count = aiConversationQuotaRepository
                                    .countConversations(projectId, userId);
                            if (count >= 5) {
                                return false;
                            }
                            insertConversation(projectId, userId, "concurrent-" + UUID.randomUUID());
                            return true;
                        })));
            }

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> attempt : attempts) {
                results.add(attempt.get(20, TimeUnit.SECONDS));
            }
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(aiConversationRepository.countByProjectIdAndCreatedByUserId(projectId, userId))
                    .isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    private DocumentAiChunkInsert chunk(UUID projectId, UUID documentId, long version,
            String content, float[] embedding) {
        return new DocumentAiChunkInsert(
                UUID.randomUUID(), projectId, documentId, version, 0, content, null, null,
                null, 2, "hash-" + UUID.randomUUID(), embedding);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                        + "VALUES (?, ?, 'password-hash', 'AI Test User', 'USER', 'ACTIVE')",
                userId, uniqueEmail());
        return userId;
    }

    private UUID insertProject(String name) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId, name);
        return projectId;
    }

    private UUID insertDocument(UUID projectId, UUID userId, String suffix) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                        + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                        + "VALUES (?, ?, ?, ?, ?, 'DOCUMENT', 'pdf', 'application/pdf', 1, ?)",
                documentId, projectId, userId, "Document " + suffix, suffix + ".pdf",
                "projects/" + projectId + "/documents/" + documentId + ".pdf");
        return documentId;
    }

    private void insertDocumentAiIndex(UUID documentId, UUID projectId, String status,
            long activeVersion, long desiredVersion) {
        jdbcTemplate.update(
                "INSERT INTO document_ai_indexes "
                        + "(document_id, project_id, status, active_version, desired_version, "
                        + "chunking_version, embedding_model, embedding_dimensions) "
                        + "VALUES (?, ?, ?, ?, ?, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, projectId, status, activeVersion, desiredVersion);
    }

    private UUID insertConversation(UUID projectId, UUID userId, String title) {
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_conversations (id, project_id, created_by_user_id, title) "
                        + "VALUES (?, ?, ?, ?)",
                conversationId, projectId, userId, title);
        return conversationId;
    }

    private UUID insertMessage(UUID conversationId, String role, String content,
            String generationStatus, String answerType) {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ai_messages (id, conversation_id, role, content, generation_status, "
                        + "answer_type) VALUES (?, ?, ?, ?, ?, ?)",
                messageId, conversationId, role, content, generationStatus, answerType);
        return messageId;
    }

    private void insertMembership(UUID projectId, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO project_members (id, project_id, user_id, role) "
                        + "VALUES (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(), projectId, userId);
    }

    private void insertJob(UUID projectId, UUID documentId, UUID userId, String dedupKey) {
        jdbcTemplate.update(
                "INSERT INTO ai_jobs "
                        + "(id, job_type, status, project_id, document_id, user_id, dedup_key, run_at) "
                        + "VALUES (?, 'DOCUMENT_INDEX', 'PENDING', ?, ?, ?, ?, ?)",
                UUID.randomUUID(), projectId, documentId, userId, dedupKey,
                Timestamp.from(Instant.now()));
    }

    private long count(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
    }

    private static String uniqueEmail() {
        return "ai-" + UUID.randomUUID() + "@example.com";
    }

    private static float[] axis(int axis) {
        return axis(axis, AiVectorRepository.EMBEDDING_DIMENSIONS);
    }

    private static float[] axis(int axis, int dimensions) {
        float[] vector = new float[dimensions];
        if (axis < dimensions) {
            vector[axis] = 1.0f;
        }
        return vector;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("concurrency test barrier failed", exception);
        }
    }
}
