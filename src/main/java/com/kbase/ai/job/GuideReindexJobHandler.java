package com.kbase.ai.job;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.AiGuideSource;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.guide.GuideChunk;
import com.kbase.ai.guide.GuideIndexPersistenceService;
import com.kbase.ai.guide.GuideMarkdownChunker;
import com.kbase.ai.guide.GuideReindexPayload;
import com.kbase.ai.guide.GuideSourceCatalog;
import com.kbase.ai.guide.GuideSourceLoader;
import com.kbase.ai.guide.LoadedGuideSource;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.AiGuideSourceRepository;
import com.kbase.ai.repository.GuideChunkInsert;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Durable, source-versioned Guide indexing. It never sees project data. */
@Component
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public final class GuideReindexJobHandler implements AiJobHandler {
    private final AiGuideSourceRepository sources;
    private final GuideSourceCatalog catalog;
    private final GuideSourceLoader loader;
    private final GuideMarkdownChunker chunker;
    private final AiEmbeddingModel embeddings;
    private final GuideIndexPersistenceService persistence;
    private final AiProperties properties;
    private final Clock clock;

    public GuideReindexJobHandler(AiGuideSourceRepository sources,
            GuideSourceCatalog catalog, GuideSourceLoader loader, GuideMarkdownChunker chunker,
            AiEmbeddingModel embeddings, GuideIndexPersistenceService persistence,
            AiProperties properties, @Qualifier("aiClock") Clock clock) {
        this.sources = sources; this.catalog = catalog; this.loader = loader;
        this.chunker = chunker; this.embeddings = embeddings; this.persistence = persistence;
        this.properties = properties; this.clock = clock;
    }

    @Override public AiJobType jobType() { return AiJobType.GUIDE_REINDEX; }

    @Override public AiJobExecutionResult handle(AiJobClaim claim) {
        GuideReindexPayload payload = payload(claim);
        if (payload == null || payload.schemaVersion() != GuideReindexPayload.SCHEMA_VERSION) {
            return AiJobExecutionResult.failure("GUIDE_PAYLOAD_INVALID");
        }
        AiGuideSource source = sources.findById(payload.guideSourceId()).orElse(null);
        if (source == null || source.getDesiredVersion() != payload.desiredVersion()) return AiJobExecutionResult.success();
        LoadedGuideSource loaded;
        try { loaded = loader.load(catalog.bySourceKey(source.getSourceKey())); }
        catch (RuntimeException exception) { return failed(claim, payload.desiredVersion(), "GUIDE_SOURCE_UNAVAILABLE"); }
        // A newer packaged content hash is reconciled by the startup/application service; stale jobs cannot activate it.
        if (!source.getContentHash().equals(loaded.contentHash())) return AiJobExecutionResult.success();
        if (!persistence.begin(claim, payload.desiredVersion())) return AiJobExecutionResult.success();
        try {
            List<GuideChunk> chunks = chunker.chunk(loaded.text());
            if (chunks.isEmpty()) return failed(claim, payload.desiredVersion(), "GUIDE_CONTENT_EMPTY");
            List<GuideChunkInsert> rows = new ArrayList<>(chunks.size());
            for (GuideChunk chunk : chunks) rows.add(new GuideChunkInsert(UUID.randomUUID(), source.getId(),
                    payload.desiredVersion(), chunk.chunkIndex(), chunk.content(), chunk.headingPath(),
                    chunk.tokenCount(), chunk.contentHash(), vector(embeddings.embed(
                            AiEmbeddingRequest.document(loaded.definition().publicTitle(), chunk.content())))));
            if (!persistence.stage(claim, payload.desiredVersion(), rows)) return AiJobExecutionResult.success();
            if (!persistence.activate(claim, payload.desiredVersion())) return AiJobExecutionResult.success();
            return AiJobExecutionResult.success();
        } catch (AiProviderException exception) {
            if (exception.retryable() && claim.attemptCount() < claim.maxAttempts()) {
                return AiJobExecutionResult.retry(clock.instant().plus(properties.getWorker().getRetryBackoff()), "AI_PROVIDER_UNAVAILABLE");
            }
            return failed(claim, payload.desiredVersion(), "AI_PROVIDER_UNAVAILABLE");
        } catch (RuntimeException exception) {
            return failed(claim, payload.desiredVersion(), "GUIDE_PROCESSING_ERROR");
        }
    }

    private AiJobExecutionResult failed(AiJobClaim claim, long version, String code) {
        persistence.fail(claim, version);
        return AiJobExecutionResult.failure(code);
    }
    private GuideReindexPayload payload(AiJobClaim claim) {
        if (claim == null || claim.jobType() != AiJobType.GUIDE_REINDEX || claim.projectId() != null
                || claim.documentId() != null || claim.userId() != null) return null;
        try { return new ObjectMapper().readValue(claim.payload(), GuideReindexPayload.class); }
        catch (Exception exception) { return null; }
    }
    private static float[] vector(AiEmbeddingResult result) {
        if (result == null || result.vector() == null || result.dimensions() != 768 || result.vector().size() != 768) throw new IllegalArgumentException();
        float[] values = new float[768];
        for (int index = 0; index < values.length; index++) {
            Double value = result.vector().get(index);
            if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) throw new IllegalArgumentException();
            values[index] = value.floatValue();
        }
        return values;
    }
}
