package com.kbase.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.evaluation.RetrievalEvaluationDataset.Category;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.CorpusChunk;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.EvalQuery;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.Split;
import com.kbase.ai.provider.fake.FakeAiEmbeddingModel;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.EmbeddingMode;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.repository.DocumentAiChunkMatch;
import com.kbase.ai.retrieval.EvidenceBlock;
import com.kbase.ai.retrieval.EvidenceSelector;
import com.kbase.ai.retrieval.EvidenceSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * M4 deterministic retrieval evaluation harness on real PostgreSQL/pgvector.
 *
 * <p>It proves dataset loading, project-scoped SQL filtering, threshold
 * selection, dedup/adjacent selection, deleted/inactive exclusion and metric
 * mechanics end-to-end with the deterministic embedding fake. Scores are
 * fake-vector mechanics only and must never be reported as live-Gemini
 * calibration; live calibration belongs to M5.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class RetrievalEvaluationHarnessIntegrationTest {

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
        registry.add("kbase.jwt.signing-secret", () -> "m4-eval-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m4-eval-otp-secret");
        registry.add("kbase.mail.username", () -> "m4-eval@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m4-eval-mail-password");
        registry.add("kbase.storage.access-key", () -> "m4-eval-access-key");
        registry.add("kbase.storage.secret-key", () -> "m4-eval-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    private static final List<CorpusChunk> CORPUS = RetrievalEvaluationDataset.corpus();
    private static final List<EvalQuery> QUERIES = RetrievalEvaluationDataset.queries();
    private static final FakeAiEmbeddingModel EMBEDDINGS = new FakeAiEmbeddingModel();
    private static final EvidenceSelector SELECTOR = new EvidenceSelector();

    /** Corpus identity resolved during setup: contentHash -> chunk row id. */
    private static final Map<String, UUID> CHUNK_IDS = new HashMap<>();
    private static final Map<String, UUID> PROJECT_IDS = new HashMap<>();
    private static final Map<String, UUID> DOCUMENT_IDS = new HashMap<>();

    @Autowired
    private AiVectorRepository vectorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void registerQueryFixtures() {
        for (EvalQuery query : QUERIES) {
            EMBEDDINGS.registerFixture(EmbeddingMode.QUERY, query.question(),
                    query.queryVector().stream().mapToDouble(Double::doubleValue).toArray());
        }
    }

    @AfterAll
    static void clearFixtureMemory() {
        EMBEDDINGS.reset();
    }

    @Test
    void deterministicDatasetSatisfiesHardInvariantsAndReportsMetrics() {
        setUpCorpus();
        UUID projectA = PROJECT_IDS.get(RetrievalEvaluationDataset.PROJECT_A);
        UUID projectB = PROJECT_IDS.get(RetrievalEvaluationDataset.PROJECT_B);

        List<RetrievalEvaluationMetrics.PerQuery> results = new ArrayList<>();
        Set<String> forbiddenHashes = forbiddenHashes();
        for (EvalQuery query : QUERIES) {
            // The query embedding flows through the deterministic fake's QUERY
            // path, mirroring the production retrieval entry point.
            float[] queryVector = toFloatArray(EMBEDDINGS.embed(
                    AiEmbeddingRequest.query(query.question())).vector());
            List<DocumentAiChunkMatch> candidates = vectorRepository.findNearestDocumentChunks(
                    projectA, queryVector, RetrievalEvaluationDataset.CANDIDATE_LIMIT);
            List<EvidenceBlock> evidence = SELECTOR.select(candidates,
                    RetrievalEvaluationDataset.SIMILARITY_THRESHOLD,
                    RetrievalEvaluationDataset.FINAL_CONTEXT_LIMIT);
            results.add(evaluate(query, candidates, evidence));

            // Hard dataset-wide exclusions: inactive staging rows and the deleted
            // document never appear for any query, at candidate level.
            for (DocumentAiChunkMatch candidate : candidates) {
                assertThat(candidate.contentHash()).isNotIn(forbiddenHashes);
                assertThat(candidate.indexVersion()).isEqualTo(1L);
                assertThat(candidate.projectId()).isEqualTo(projectA);
            }
        }

        // Non-vacuous corpus: the same rollback axis IS retrievable inside
        // Project B, so Project A leakage=0 below is a real boundary result.
        List<DocumentAiChunkMatch> projectBCandidates = vectorRepository.findNearestDocumentChunks(
                projectB, toFloatArray(EMBEDDINGS.embed(
                        AiEmbeddingRequest.query(QUERIES.getFirst().question())).vector()),
                RetrievalEvaluationDataset.CANDIDATE_LIMIT);
        assertThat(projectBCandidates).extracting(DocumentAiChunkMatch::contentHash)
                .contains("DB1-c0");
        assertThat(projectBCandidates).allSatisfy(candidate ->
                assertThat(candidate.projectId()).isEqualTo(projectB));

        RetrievalEvaluationMetrics.Report report = RetrievalEvaluationMetrics.aggregate(results);

        // Machine-checkable deterministic expectations for this fake-vector run.
        assertThat(report.queryCount()).isEqualTo(36);
        assertThat(report.crossProjectLeakageCount()).isZero();
        assertThat(report.citationSourceValidity()).isEqualTo(1.0);
        assertThat(report.noEvidenceFalsePositives()).isZero();
        assertThat(report.noEvidenceFalseNegatives()).isZero();
        assertThat(report.meanRecallAtK()).isEqualTo(1.0);
        assertThat(report.selectedContextHitRate()).isEqualTo(1.0);

        for (RetrievalEvaluationMetrics.PerQuery result : results) {
            assertThat(result.recallAtK()).isEqualTo(1.0);
            assertThat(result.leaked()).isFalse();
            assertThat(result.citationValid()).isTrue();
            assertThat(result.producedNoEvidence()).isEqualTo(!result.expectsEvidence());
        }

        // Split integrity: both splits carry the required category coverage.
        for (Split split : Split.values()) {
            Set<Category> categories = new HashSet<>();
            for (EvalQuery query : QUERIES) {
                if (query.split() == split) {
                    categories.add(query.category());
                }
            }
            assertThat(categories).contains(Category.DIRECT_FACT, Category.PARAPHRASE,
                    Category.MULTI_CHUNK, Category.SEMANTIC_DISTRACTOR, Category.NO_EVIDENCE,
                    Category.CROSS_PROJECT_TRAP);
            assertThat(QUERIES.stream().filter(query -> query.split() == split).count())
                    .isGreaterThanOrEqualTo(16);
        }
        System.out.printf(
                "retrieval-evaluation report: queries=%d recall@%d=%.4f selectedHit=%.4f "
                + "noEvidenceFP=%d noEvidenceFN=%d leakage=%d citationValidity=%.4f "
                + "(deterministic fake-vector mechanics, not live calibration)%n",
                report.queryCount(), RetrievalEvaluationDataset.CANDIDATE_LIMIT,
                report.meanRecallAtK(), report.selectedContextHitRate(),
                report.noEvidenceFalsePositives(), report.noEvidenceFalseNegatives(),
                report.crossProjectLeakageCount(), report.citationSourceValidity());
    }

    private RetrievalEvaluationMetrics.PerQuery evaluate(EvalQuery query,
            List<DocumentAiChunkMatch> candidates, List<EvidenceBlock> evidence) {
        Set<String> expected = query.expectedChunkHashes();
        Set<UUID> expectedIds = new HashSet<>();
        for (String hash : expected) {
            expectedIds.add(CHUNK_IDS.get(hash));
        }
        int recalled = 0;
        boolean leaked = false;
        Set<UUID> candidateIds = new HashSet<>();
        for (DocumentAiChunkMatch candidate : candidates) {
            candidateIds.add(candidate.id());
            if (expectedIds.contains(candidate.id())) {
                recalled++;
            }
            if (!candidate.projectId().equals(PROJECT_IDS.get(query.projectKey()))) {
                leaked = true;
            }
        }
        boolean selectedHit = false;
        boolean citationValid = true;
        int blockOrder = 0;
        for (EvidenceBlock block : evidence) {
            blockOrder++;
            assertThat(block.label()).isEqualTo("SOURCE_" + blockOrder);
            for (EvidenceSource source : block.sources()) {
                if (expectedIds.contains(source.chunkId())) {
                    selectedHit = true;
                }
                // Citation integrity: sources map only to backend-selected rows
                // of this request, at the active index version.
                if (!candidateIds.contains(source.chunkId())
                        || !PROJECT_IDS.get(query.projectKey()).equals(source.projectId())
                        || source.indexVersion() != 1L) {
                    citationValid = false;
                }
            }
        }
        boolean producedNoEvidence = evidence.isEmpty();
        return new RetrievalEvaluationMetrics.PerQuery(query.id(), query.category(),
                !expected.isEmpty(), expected.size(), recalled, selectedHit,
                producedNoEvidence, leaked, citationValid);
    }

    private void setUpCorpus() {
        for (String projectKey : List.of(RetrievalEvaluationDataset.PROJECT_A,
                RetrievalEvaluationDataset.PROJECT_B)) {
            UUID userId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO users (id, email, password_hash, display_name, "
                    + "system_role, status) VALUES (?, ?, 'hash', 'M4 User', 'USER', 'ACTIVE')",
                    userId, "m4-" + UUID.randomUUID() + "@example.com");
            jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                    "M4 evaluation project " + projectKey);
            PROJECT_IDS.put(projectKey, projectId);
        }

        Set<String> documentKeys = new HashSet<>();
        for (CorpusChunk chunk : CORPUS) {
            documentKeys.add(chunk.documentKey());
        }
        for (String documentKey : documentKeys) {
            CorpusChunk first = CORPUS.stream().filter(chunk -> chunk.documentKey()
                    .equals(documentKey)).findFirst().orElseThrow();
            UUID documentId = UUID.randomUUID();
            DOCUMENT_IDS.put(documentKey, documentId);
            jdbcTemplate.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, "
                    + "display_name, original_filename, file_kind, extension, mime_type, size_bytes, "
                    + "storage_key) VALUES (?, ?, ?, ?, 'm4.txt', 'DOCUMENT', 'txt', 'text/plain', 10, ?)",
                    documentId, PROJECT_IDS.get(first.projectKey()),
                    jdbcTemplate.queryForObject("SELECT id FROM users LIMIT 1", UUID.class),
                    first.displayName(),
                    "projects/" + PROJECT_IDS.get(first.projectKey()) + "/documents/" + documentId + ".txt");
            jdbcTemplate.update("INSERT INTO document_ai_indexes (document_id, project_id, status, "
                    + "active_version, desired_version, chunking_version, embedding_model, "
                    + "embedding_dimensions) VALUES (?, ?, 'READY', 1, 1, 'chunk-v1', "
                    + "'fake-eval-embedding', 768)", documentId, PROJECT_IDS.get(first.projectKey()));
        }
        for (CorpusChunk chunk : CORPUS) {
            UUID chunkId = UUID.randomUUID();
            CHUNK_IDS.put(chunk.contentHash(), chunkId);
            long version = RetrievalEvaluationDataset.isInactiveStaging(chunk) ? 2L : 1L;
            vectorRepository.insertDocumentChunk(new DocumentAiChunkInsert(chunkId,
                    PROJECT_IDS.get(chunk.projectKey()), DOCUMENT_IDS.get(chunk.documentKey()),
                    version, chunk.chunkIndex(), chunk.content(), null, null, null, 1,
                    chunk.contentHash(), toFloatArray(chunk.vector())));
        }
        // Deleted-source lifecycle: normal document delete cascades index+chunks.
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?",
                DOCUMENT_IDS.get("DA9"));
        CHUNK_IDS.remove("DA9-c0");
    }

    private Set<String> forbiddenHashes() {
        Set<String> forbidden = new HashSet<>();
        for (CorpusChunk chunk : CORPUS) {
            if (RetrievalEvaluationDataset.isInactiveStaging(chunk)
                    || RetrievalEvaluationDataset.isDeletedDocument(chunk)) {
                forbidden.add(chunk.contentHash());
            }
        }
        return forbidden;
    }

    private static float[] toFloatArray(double[] vector) {
        float[] values = new float[vector.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) vector[index];
        }
        return values;
    }

    private static float[] toFloatArray(List<Double> vector) {
        float[] values = new float[vector.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = vector.get(index).floatValue();
        }
        return values;
    }
}
