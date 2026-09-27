package com.kbase.ai.evaluation.manual;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.kbase.KBaseApplication;
import com.kbase.ai.extraction.DocumentContentExtractor;
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
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.service.StorageService;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M6 manual indexing benchmark on a fresh isolated runtime with real Gemini
 * embeddings — never part of the automated gate (no {@code @Test}).
 *
 * <p>It drives the real {@link DocumentIndexJobHandler} path (Tika extract,
 * deterministic chunking, provider embeddings, staging/activation) over
 * synthetic small/medium/large TXT workloads and reports chunk counts,
 * provider invocation counts, wall-clock latency, lease renewals and a
 * memory-accumulation estimate. The same harness is re-run after any
 * embedding-transport change (e.g. batching) for the comparative evidence;
 * numbers are local synthetic evidence, never a production SLA claim.</p>
 *
 * <p>Run (fresh isolated Compose project, host ports exported so they
 * override the operator {@code .env}; the shortened lease timeout makes the
 * large workload cross several renewal points deterministically):
 * <pre>
 * export KBASE_POSTGRES_PORT=15435 KBASE_REDIS_PORT=16383 KBASE_SERVER_PORT=18087 \
 *        KBASE_STORAGE_API_PORT=19006 KBASE_STORAGE_CONSOLE_PORT=19007
 * docker compose -p kbase-rag-m6 -f docker-compose.yml up -d postgres minio redis
 * mvn -q test-compile \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=com.kbase.ai.evaluation.manual.RealGeminiIndexingBenchmark \
 *   -Dexec.classpathScope=test \
 *   -Dexec.args=--kbase.ai.worker.lease-timeout=10s
 * </pre>
 */
public final class RealGeminiIndexingBenchmark {

    private RealGeminiIndexingBenchmark() {
    }

    public static void main(String[] args) {
        // Optional overrides for controlled-timing renewals and batch size, e.g.
        // -Dexec.args=--kbase.ai.worker.lease-timeout=30s --kbase.ai.provider.embedding-batch-size=16
        List<String> overrides = new ArrayList<>(List.of(
                "--server.port=18087",
                "--kbase.ai.worker.scheduling-enabled=false"));
        for (String argument : args) {
            if (argument.startsWith("--kbase.ai.worker.lease-timeout=")
                    || argument.startsWith("--kbase.ai.provider.embedding-batch-size=")) {
                overrides.add(argument);
            }
        }
        if (overrides.stream().noneMatch(argument -> argument.startsWith("--kbase.ai.worker.lease-timeout="))) {
            overrides.add("--kbase.ai.worker.lease-timeout=10s");
        }
        ConfigurableApplicationContext context = new SpringApplicationBuilder(KBaseApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .run(overrides.toArray(new String[0]));
        try {
            var environment = context.getEnvironment();
            if (!"false".equals(environment.getProperty("kbase.ai.worker.scheduling-enabled"))) {
                System.out.println("[benchmark] RESULT=FAIL (isolation override not effective)");
                System.exit(1);
            }
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM document_ai_indexes", Long.class);
            if (existing == null || existing != 0) {
                System.out.println("[benchmark] RESULT=FAIL (runtime not fresh: document_ai_indexes=" + existing + ")");
                System.exit(1);
            }

            AiEmbeddingModel delegate = context.getBean(AiEmbeddingModel.class);
            CountingEmbeddings counting = new CountingEmbeddings(delegate);
            StorageService storage = context.getBean(StorageService.class);
            DocumentContentExtractor extractor = context.getBean(DocumentContentExtractor.class);
            StructureAwareDocumentChunker chunker = context.getBean(StructureAwareDocumentChunker.class);
            DocumentAiIndexPersistenceService persistence = context.getBean(DocumentAiIndexPersistenceService.class);
            AiJobStore jobStore = context.getBean(AiJobStore.class);
            com.kbase.ai.config.AiProperties aiProperties = context.getBean(com.kbase.ai.config.AiProperties.class);
            UploadProperties uploadProperties = context.getBean(UploadProperties.class);
            var clock = context.getBean("aiClock", java.time.Clock.class);

            System.out.println("[benchmark] runtime=fresh leaseTimeout=" + aiProperties.getWorker().getLeaseTimeout()
                    + " embeddingBatchSize=" + environment.getProperty(
                            "kbase.ai.provider.embedding-batch-size", "1(default, serial)")
                    + " provider=" + environment.getProperty("kbase.ai.gemini.embedding-model")
                    + " chatCalls=0");

            UUID userId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                    + "VALUES (?, ?, 'hash', 'M6 User', 'USER', 'ACTIVE')",
                    userId, "m6-" + UUID.randomUUID() + "@example.com");
            jdbc.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                    "M6 benchmark project " + UUID.randomUUID());

            benchmark("SMALL", 400, projectId, userId, jdbc, context, storage, extractor,
                    chunker, persistence, jobStore, aiProperties, uploadProperties, clock, counting);
            benchmark("MEDIUM", 22000, projectId, userId, jdbc, context, storage, extractor,
                    chunker, persistence, jobStore, aiProperties, uploadProperties, clock, counting);
            benchmark("LARGE", 110000, projectId, userId, jdbc, context, storage, extractor,
                    chunker, persistence, jobStore, aiProperties, uploadProperties, clock, counting);

            System.out.println("[benchmark] RESULT=COMPLETE");
        } finally {
            context.close();
        }
    }

    private static void benchmark(String label, int targetWords, UUID projectId, UUID userId,
            JdbcTemplate jdbc, ConfigurableApplicationContext context, StorageService storage,
            DocumentContentExtractor extractor,
            StructureAwareDocumentChunker chunker, DocumentAiIndexPersistenceService persistence,
            AiJobStore jobStore, com.kbase.ai.config.AiProperties aiProperties,
            UploadProperties uploadProperties, java.time.Clock clock, CountingEmbeddings counting) {
        byte[] content = syntheticText(targetWords).getBytes(StandardCharsets.UTF_8);
        UUID documentId = UUID.randomUUID();
        String storageKey = "projects/" + projectId + "/documents/" + documentId + ".txt";
        storage.upload(new StorageUploadRequest(storageKey,
                new ByteArrayInputStream(content), content.length, "text/plain"));
        jdbc.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, ?, ?, 'm6.txt', 'DOCUMENT', 'txt', 'text/plain', ?, ?)",
                documentId, projectId, userId, "M6 " + label, (long) content.length, storageKey);
        jdbc.update("INSERT INTO document_ai_indexes (document_id, project_id, status, active_version, "
                + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                + "VALUES (?, ?, 'PENDING', NULL, 1, 'chunk-v1', ?, 768)",
                documentId, projectId, aiProperties.getGemini().getEmbeddingModel());
        jobStore.enqueueActive(new AiJobSchedule(com.kbase.ai.enums.AiJobType.DOCUMENT_INDEX,
                projectId, documentId, null, "document-index:" + documentId + ":v1",
                "{\"schemaVersion\":1}", null, 3));
        AiJobClaim claim = jobStore.claimDueJobs(
                Set.of(com.kbase.ai.enums.AiJobType.DOCUMENT_INDEX), 1, clock.instant()).getFirst();
        Instant claimAt = clock.instant();
        counting.reset();

        DocumentIndexJobHandler handler = new DocumentIndexJobHandler(
                context.getBean(com.kbase.document.repository.DocumentRepository.class),
                context.getBean(com.kbase.ai.repository.DocumentAiIndexRepository.class),
                new com.kbase.ai.service.AiDocumentSupportPolicy(), extractor, chunker,
                counting, storage, persistence, jobStore, new AiObservability(),
                uploadProperties, aiProperties, clock);
        long started = System.nanoTime();
        var result = handler.handle(claim);
        long wallMs = (System.nanoTime() - started) / 1_000_000;

        Long chunkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM document_ai_chunks WHERE document_id = ?", Long.class, documentId);
        String indexStatus = jdbc.queryForObject(
                "SELECT status FROM document_ai_indexes WHERE document_id = ?", String.class, documentId);
        java.sql.Timestamp leaseUntil = jdbc.queryForObject(
                "SELECT lease_until FROM ai_jobs WHERE id = ?", java.sql.Timestamp.class, claim.id());
        long renewals = 0;
        if (leaseUntil != null) {
            long leaseMs = leaseUntil.toInstant().toEpochMilli() - claimAt.toEpochMilli();
            long timeoutMs = aiProperties.getWorker().getLeaseTimeout().toMillis();
            renewals = Math.max(0, Math.round((double) leaseMs / timeoutMs) - 1);
        }
        boolean finalized = jobStore.markDone(claim);

        // Memory-risk observation: rows accumulate in JVM memory before staging.
        long estimatedRowBytes = (chunkCount == null ? 0 : chunkCount)
                * ((long) content.length / Math.max(1, chunkCount == null ? 1 : chunkCount.intValue())
                        + 768 * 4);
        System.out.println("[benchmark] " + label + " bytes=" + content.length
                + " chunks=" + chunkCount
                + " providerInvocations=" + counting.invocations.get()
                + " providerMs=" + counting.nanos.get() / 1_000_000
                + " wallMs=" + wallMs
                + " outcome=" + result.outcome()
                + " indexStatus=" + indexStatus
                + " renewals~" + renewals
                + " finalizationPersisted=" + finalized
                + " stagedRowEstimateKB=" + estimatedRowBytes / 1024);
    }

    /** Deterministic synthetic TXT body: numbered lines of neutral filler words. */
    private static String syntheticText(int targetWords) {
        StringBuilder builder = new StringBuilder(targetWords * 8);
        int words = 0;
        for (int line = 1; words < targetWords; line++) {
            builder.append("Line ").append(line).append(" of the synthetic benchmark document covers topic ")
                    .append(line % 7).append(" with neutral deterministic filler text for chunking.\n");
            words += 18;
        }
        return builder.toString();
    }

    /** Counting wrapper: invocation count and cumulative provider time only. */
    private static final class CountingEmbeddings implements AiEmbeddingModel {
        private final AiEmbeddingModel delegate;
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicLong nanos = new AtomicLong();

        private CountingEmbeddings(AiEmbeddingModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public AiEmbeddingResult embed(AiEmbeddingRequest request) {
            invocations.incrementAndGet();
            long started = System.nanoTime();
            try {
                return delegate.embed(request);
            } finally {
                nanos.addAndGet(System.nanoTime() - started);
            }
        }

        @Override
        public List<AiEmbeddingResult> embedAll(List<AiEmbeddingRequest> requests) {
            if (requests.isEmpty()) {
                return List.of();
            }
            invocations.incrementAndGet();
            long started = System.nanoTime();
            try {
                return delegate.embedAll(requests);
            } finally {
                nanos.addAndGet(System.nanoTime() - started);
            }
        }

        private void reset() {
            invocations.set(0);
            nanos.set(0);
        }
    }
}
