package com.kbase.ai.evaluation.manual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kbase.KBaseApplication;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.CorpusChunk;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.EvalQuery;
import com.kbase.ai.evaluation.RetrievalEvaluationDataset.Split;
import com.kbase.ai.evaluation.RetrievalEvaluationMetrics;
import com.kbase.ai.evaluation.RetrievalEvaluationMetrics.PerQuery;
import com.kbase.ai.evaluation.RetrievalEvaluationMetrics.Report;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.model.EmbeddingMode;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.repository.DocumentAiChunkMatch;
import com.kbase.ai.retrieval.EvidenceBlock;
import com.kbase.ai.retrieval.EvidenceSelector;
import com.kbase.ai.retrieval.EvidenceSource;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M5 manual real-Gemini embedding calibration on the M4 synthetic dataset —
 * never part of the automated gate (no {@code @Test}, name outside Surefire
 * includes, so {@code mvn clean verify} cannot run it).
 *
 * <p>Contract:
 * <ul>
 *   <li>isolated fresh PostgreSQL/pgvector runtime owned by the operator
 *       (the harness aborts unless {@code document_ai_indexes} and
 *       {@code ai_guide_chunks} are empty, so deterministic-fake vector
 *       spaces can never mix with live Gemini vectors);</li>
 *   <li>the same mechanical scheduling-isolation contract as the provider
 *       smoke ({@code --kbase.ai.worker.scheduling-enabled=false} as a
 *       command-line argument, asserted before any provider call);</li>
 *   <li>preflight fails before any provider call unless the approved
 *       candidate configuration is effective;</li>
 *   <li>bounded provider usage: 15 corpus + 2 forbidden + 36 query embedding
 *       calls, one pass, no retries, no chat calls;</li>
 *   <li>threshold sweep set and the selection rule are fixed BEFORE the
 *       holdout is evaluated; the holdout runs exactly once at the selected
 *       threshold and is never used for tuning;</li>
 *   <li>output is metric counts and model identifiers only — never vectors,
 *       prompt/chunk content, or the API key.</li>
 * </ul>
 *
 * <p>Run (fresh isolated Compose project + deps only, host ports exported so
 * they override the operator {@code .env}; profile {@code local} imports the
 * real Gemini configuration from {@code .env}):
 * <pre>
 * export KBASE_POSTGRES_PORT=15434 KBASE_REDIS_PORT=16382 KBASE_SERVER_PORT=18086 \
 *        KBASE_STORAGE_API_PORT=19004 KBASE_STORAGE_CONSOLE_PORT=19005
 * docker compose -p kbase-rag-m5 -f docker-compose.yml up -d postgres minio redis
 * mvn -q test-compile \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=com.kbase.ai.evaluation.manual.RealGeminiRetrievalCalibration \
 *   -Dexec.classpathScope=test
 * </pre>
 */
public final class RealGeminiRetrievalCalibration {

    /** Pre-registered sweep set, fixed before any holdout evaluation. */
    static final double[] SWEEP = {0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85};
    static final String EXPECTED_PROVIDER_MODE = "gemini";
    static final String EXPECTED_CHAT_MODEL = "gemini-3.5-flash-lite";
    static final String EXPECTED_EMBEDDING_MODEL = "gemini-embedding-2";
    static final String EXPECTED_EMBEDDING_DIMENSIONS = "768";

    private RealGeminiRetrievalCalibration() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(KBaseApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .run(
                        "--server.port=18086",
                        "--kbase.ai.worker.scheduling-enabled=false");
        try {
            var environment = context.getEnvironment();
            String effectiveScheduling = environment.getProperty("kbase.ai.worker.scheduling-enabled");
            String providerMode = environment.getProperty("kbase.ai.provider.mode");
            String chatModel = environment.getProperty("kbase.ai.gemini.chat-model");
            String embeddingModel = environment.getProperty("kbase.ai.gemini.embedding-model");
            String dimensionsProperty = environment.getProperty("kbase.ai.gemini.embedding-dimensions");

            if (!"false".equals(effectiveScheduling)) {
                System.out.println("[calibration] RESULT=FAIL (isolation override not effective)");
                System.exit(1);
            }
            if (context.containsBean(org.springframework.scheduling.config.TaskManagementConfigUtils
                    .SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
                System.out.println("[calibration] RESULT=FAIL (background scheduling active)");
                System.exit(1);
            }
            List<String> violations = new ArrayList<>();
            if (!EXPECTED_PROVIDER_MODE.equals(providerMode)) {
                violations.add("provider.mode must be " + EXPECTED_PROVIDER_MODE);
            }
            if (!EXPECTED_CHAT_MODEL.equals(chatModel)) {
                violations.add("chat model must be " + EXPECTED_CHAT_MODEL + " (configuration baseline; chat is not called)");
            }
            if (!EXPECTED_EMBEDDING_MODEL.equals(embeddingModel)) {
                violations.add("embedding model must be " + EXPECTED_EMBEDDING_MODEL);
            }
            if (!EXPECTED_EMBEDDING_DIMENSIONS.equals(dimensionsProperty)) {
                violations.add("embedding dimensions must be " + EXPECTED_EMBEDDING_DIMENSIONS);
            }
            if (!violations.isEmpty()) {
                violations.forEach(violation -> System.out.println("[calibration] PREFLIGHT_VIOLATION " + violation));
                System.out.println("[calibration] RESULT=FAIL (no provider request was sent)");
                System.exit(1);
            }

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            AiVectorRepository vectorRepository = context.getBean(AiVectorRepository.class);
            AiEmbeddingModel embeddings = context.getBean(AiEmbeddingModel.class);

            Long existingIndexRows = jdbc.queryForObject("SELECT COUNT(*) FROM document_ai_indexes", Long.class);
            Long existingGuideChunks = jdbc.queryForObject("SELECT COUNT(*) FROM ai_guide_chunks", Long.class);
            if (existingIndexRows == null || existingIndexRows != 0
                    || existingGuideChunks == null || existingGuideChunks != 0) {
                System.out.println("[calibration] RESULT=FAIL (runtime is not fresh: document_ai_indexes="
                        + existingIndexRows + " ai_guide_chunks=" + existingGuideChunks
                        + "; no provider request was sent)");
                System.exit(1);
            }

            System.out.println("[calibration] runtime=fresh providerMode=" + providerMode
                    + " embeddingModel=" + embeddingModel + " dimensions=" + dimensionsProperty
                    + " chatCalls=0 scheduling=disabled");

            // --- Corpus build: real Gemini DOCUMENT embeddings on real pgvector ---
            List<CorpusChunk> corpus = RetrievalEvaluationDataset.corpus();
            Map<String, UUID> projectIds = new HashMap<>();
            Map<String, UUID> documentIds = new HashMap<>();
            Map<String, UUID> chunkIds = new HashMap<>();
            for (String projectKey : List.of(RetrievalEvaluationDataset.PROJECT_A,
                    RetrievalEvaluationDataset.PROJECT_B)) {
                UUID userId = UUID.randomUUID();
                UUID projectId = UUID.randomUUID();
                jdbc.update("INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                        + "VALUES (?, ?, 'hash', 'M5 User', 'USER', 'ACTIVE')",
                        userId, "m5-" + UUID.randomUUID() + "@example.com");
                jdbc.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                        "M5 calibration project " + projectKey);
                projectIds.put(projectKey, projectId);
            }
            Set<String> documentKeys = new HashSet<>();
            for (CorpusChunk chunk : corpus) {
                documentKeys.add(chunk.documentKey());
            }
            for (String documentKey : documentKeys) {
                CorpusChunk first = corpus.stream().filter(chunk -> chunk.documentKey().equals(documentKey))
                        .findFirst().orElseThrow();
                UUID documentId = UUID.randomUUID();
                UUID projectId = projectIds.get(first.projectKey());
                insertDocumentRow(jdbc, documentId, projectId, first.displayName());
                documentIds.put(documentKey, documentId);
                jdbc.update("INSERT INTO document_ai_indexes (document_id, project_id, status, active_version, "
                        + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                        + "VALUES (?, ?, 'READY', 1, 1, 'chunk-v1', ?, ?)",
                        documentId, projectId, embeddingModel,
                        Integer.parseInt(EXPECTED_EMBEDDING_DIMENSIONS));
            }
            int corpusCalls = 0;
            for (CorpusChunk chunk : corpus) {
                AiEmbeddingResult embedded = embeddings.embed(AiEmbeddingRequest.document(
                        firstDisplayName(corpus, chunk.documentKey()), chunk.content()));
                corpusCalls++;
                if (embedded.dimensions() != Integer.parseInt(EXPECTED_EMBEDDING_DIMENSIONS)) {
                    System.out.println("[calibration] RESULT=FAIL (unexpected embedding dimensions "
                            + embedded.dimensions() + ")");
                    System.exit(1);
                }
                UUID chunkId = UUID.randomUUID();
                chunkIds.put(chunk.contentHash(), chunkId);
                long version = RetrievalEvaluationDataset.isInactiveStaging(chunk) ? 2L : 1L;
                vectorRepository.insertDocumentChunk(new DocumentAiChunkInsert(chunkId,
                        projectIds.get(chunk.projectKey()), documentIds.get(chunk.documentKey()),
                        version, chunk.chunkIndex(), chunk.content(), null, null, null, 1,
                        chunk.contentHash(), toFloatArray(embedded.vector())));
            }
            // Deleted-source lifecycle through the normal FK cascade.
            jdbc.update("DELETE FROM documents WHERE id = ?", documentIds.get("DA9"));
            chunkIds.remove("DA9-c0");

            // --- Query embeddings: one QUERY call per annotated query ---
            List<EvalQuery> queries = RetrievalEvaluationDataset.queries();
            Map<String, float[]> queryVectors = new HashMap<>();
            int queryCalls = 0;
            for (EvalQuery query : queries) {
                AiEmbeddingResult embedded = embeddings.embed(AiEmbeddingRequest.query(query.question()));
                queryCalls++;
                queryVectors.put(query.id(), toFloatArray(embedded.vector()));
            }
            System.out.println("[calibration] providerCalls corpus=" + corpusCalls
                    + " queries=" + queryCalls + " total=" + (corpusCalls + queryCalls) + " chatCalls=0");

            UUID projectA = projectIds.get(RetrievalEvaluationDataset.PROJECT_A);
            UUID projectB = projectIds.get(RetrievalEvaluationDataset.PROJECT_B);

            // Candidates fetched once per query (candidate limit 10); the sweep
            // re-filters the same fetched candidates, so no extra provider calls.
            Map<String, List<DocumentAiChunkMatch>> candidatesById = new HashMap<>();
            for (EvalQuery query : queries) {
                candidatesById.put(query.id(), vectorRepository.findNearestDocumentChunks(
                        projectA, queryVectors.get(query.id()),
                        RetrievalEvaluationDataset.CANDIDATE_LIMIT));
            }

            // Non-vacuous trap corpus check.
            List<DocumentAiChunkMatch> projectBCandidates = vectorRepository.findNearestDocumentChunks(
                    projectB, queryVectors.get("C01"), RetrievalEvaluationDataset.CANDIDATE_LIMIT);
            boolean trapRetrievableInB = projectBCandidates.stream()
                    .anyMatch(candidate -> candidate.contentHash().equals("DB1-c0"))
                    && projectBCandidates.stream().allMatch(candidate ->
                            candidate.projectId().equals(projectB));

            // --- Pre-registered selection rule: minimize calibration FN+FP;
            // ties prefer 0.70, then the value closest to 0.70; a non-0.70
            // winner is adopted only if it strictly improves FN+FP by at least
            // two queries with zero FP and zero leakage. ---
            Map<Double, Report> calibrationReports = new HashMap<>();
            for (double threshold : SWEEP) {
                calibrationReports.put(threshold, evaluate(queries.stream()
                        .filter(query -> query.split() == Split.CALIBRATION).toList(),
                        candidatesById, chunkIds, projectA, threshold));
            }
            System.out.println("[calibration] CALIBRATION sweep (fake-dataset expectations, live embeddings):");
            for (double threshold : SWEEP) {
                System.out.println("[calibration] threshold=" + threshold + " -> "
                        + describe(calibrationReports.get(threshold)));
            }
            double selected = selectThreshold(calibrationReports);
            System.out.println("[calibration] selectedThreshold=" + selected
                    + " (rule: minimize FN+FP; ties prefer 0.70; adopt non-0.70 only if FN+FP "
                    + "improves >=2 with zero FP/leakage)");

            // --- Holdout: exactly one evaluation at the selected threshold ---
            Report holdout = evaluate(queries.stream()
                    .filter(query -> query.split() == Split.HOLDOUT).toList(),
                    candidatesById, chunkIds, projectA, selected);
            System.out.println("[calibration] HOLDOUT threshold=" + selected + " -> " + describe(holdout));
            System.out.println("[calibration] trapRetrievableInB=" + trapRetrievableInB
                    + " holdoutLeakage=" + holdout.crossProjectLeakageCount()
                    + " holdoutValidity=" + holdout.citationSourceValidity());
            System.out.println("[calibration] RESULT=COMPLETE");
        } finally {
            context.close();
        }
    }

    /** Calibration selection rule, pre-registered before the holdout run. */
    static double selectThreshold(Map<Double, Report> calibrationReports) {
        double best = 0.70;
        int bestCost = cost(calibrationReports.get(0.70));
        boolean zeroPointSevenFeasible = calibrationReports.get(0.70).crossProjectLeakageCount() == 0;
        for (double threshold : SWEEP) {
            Report report = calibrationReports.get(threshold);
            if (report.crossProjectLeakageCount() != 0) {
                continue;
            }
            int candidateCost = cost(report);
            if (candidateCost < bestCost
                    || (candidateCost == bestCost && !zeroPointSevenFeasible
                            && Math.abs(threshold - 0.70) < Math.abs(best - 0.70))) {
                best = threshold;
                bestCost = candidateCost;
                zeroPointSevenFeasible = zeroPointSevenFeasible || threshold == 0.70;
            }
        }
        Report baseline = calibrationReports.get(0.70);
        Report winner = calibrationReports.get(best);
        boolean adoptedImprovement = best == 0.70
                || (cost(winner) + 2 <= cost(baseline)
                        && winner.noEvidenceFalsePositives() == 0);
        return adoptedImprovement ? best : 0.70;
    }

    private static int cost(Report report) {
        return report.noEvidenceFalseNegatives() + report.noEvidenceFalsePositives();
    }

    private static Report evaluate(List<EvalQuery> queries,
            Map<String, List<DocumentAiChunkMatch>> candidatesById,
            Map<String, UUID> chunkIds, UUID expectedProject, double threshold) {
        EvidenceSelector selector = new EvidenceSelector();
        List<PerQuery> results = new ArrayList<>();
        for (EvalQuery query : queries) {
            List<DocumentAiChunkMatch> candidates = candidatesById.get(query.id());
            List<EvidenceBlock> evidence = selector.select(candidates, threshold,
                    RetrievalEvaluationDataset.FINAL_CONTEXT_LIMIT);
            results.add(perQuery(query, candidates, evidence, chunkIds, expectedProject));
        }
        return RetrievalEvaluationMetrics.aggregate(results);
    }

    private static PerQuery perQuery(EvalQuery query, List<DocumentAiChunkMatch> candidates,
            List<EvidenceBlock> evidence, Map<String, UUID> chunkIds, UUID expectedProject) {
        Set<UUID> expectedIds = new HashSet<>();
        for (String hash : query.expectedChunkHashes()) {
            UUID chunkId = chunkIds.get(hash);
            if (chunkId != null) {
                expectedIds.add(chunkId);
            }
        }
        int recalled = 0;
        boolean leaked = false;
        Set<UUID> candidateIds = new HashSet<>();
        for (DocumentAiChunkMatch candidate : candidates) {
            candidateIds.add(candidate.id());
            if (expectedIds.contains(candidate.id())) {
                recalled++;
            }
            if (!candidate.projectId().equals(expectedProject)) {
                leaked = true;
            }
        }
        boolean selectedHit = false;
        boolean citationValid = true;
        for (EvidenceBlock block : evidence) {
            for (EvidenceSource source : block.sources()) {
                if (expectedIds.contains(source.chunkId())) {
                    selectedHit = true;
                }
                if (!candidateIds.contains(source.chunkId())
                        || !expectedProject.equals(source.projectId()) || source.indexVersion() != 1L) {
                    citationValid = false;
                }
            }
        }
        return new PerQuery(query.id(), query.category(), !query.expectedChunkHashes().isEmpty(),
                query.expectedChunkHashes().size(), recalled, selectedHit, evidence.isEmpty(),
                leaked, citationValid);
    }

    private static String describe(Report report) {
        return "recall@10=" + String.format(java.util.Locale.ROOT, "%.4f", report.meanRecallAtK())
                + " selectedHit=" + String.format(java.util.Locale.ROOT, "%.4f", report.selectedContextHitRate())
                + " noEvidenceFP=" + report.noEvidenceFalsePositives()
                + " noEvidenceFN=" + report.noEvidenceFalseNegatives()
                + " leakage=" + report.crossProjectLeakageCount()
                + " citationValidity=" + String.format(java.util.Locale.ROOT, "%.4f", report.citationSourceValidity());
    }

    private static void insertDocumentRow(JdbcTemplate jdbc, UUID documentId, UUID projectId,
            String displayName) {
        jdbc.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, (SELECT id FROM users LIMIT 1), ?, 'm5.txt', 'DOCUMENT', 'txt', "
                + "'text/plain', 10, ?)", documentId, projectId, displayName,
                "projects/" + projectId + "/documents/" + documentId + ".txt");
    }

    private static String firstDisplayName(List<CorpusChunk> corpus, String documentKey) {
        return corpus.stream().filter(chunk -> chunk.documentKey().equals(documentKey))
                .findFirst().orElseThrow().displayName();
    }

    private static float[] toFloatArray(List<Double> vector) {
        float[] values = new float[vector.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = vector.get(index).floatValue();
        }
        return values;
    }
}
