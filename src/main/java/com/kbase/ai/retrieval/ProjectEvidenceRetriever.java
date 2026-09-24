package com.kbase.ai.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkMatch;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Authorizes before embedding and keeps SQL project scope inside the repository. */
@Service
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public class ProjectEvidenceRetriever {
    private final ProjectAuthorizationService authorization;
    private final AiEmbeddingModel embeddingModel;
    private final AiVectorRepository vectors;
    private final AiProperties properties;

    public ProjectEvidenceRetriever(ProjectAuthorizationService authorization,
            AiEmbeddingModel embeddingModel, AiVectorRepository vectors, AiProperties properties) {
        this.authorization = Objects.requireNonNull(authorization);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        this.vectors = Objects.requireNonNull(vectors);
        this.properties = Objects.requireNonNull(properties);
    }

    public List<DocumentAiChunkMatch> retrieve(UUID projectId, CustomUserPrincipal principal,
            String query) {
        authorization.requireProjectAccess(projectId, principal);
        AiEmbeddingResult result = embeddingModel.embed(AiEmbeddingRequest.query(query));
        if (result == null || result.dimensions() != AiVectorRepository.EMBEDDING_DIMENSIONS) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }
        List<Double> values = result.vector();
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            if (!Double.isFinite(value) || !Float.isFinite((float) value)) {
                throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
            }
            vector[i] = (float) value;
        }
        return vectors.findNearestDocumentChunks(projectId, vector,
                properties.getRetrievalCandidateLimit());
    }
}
