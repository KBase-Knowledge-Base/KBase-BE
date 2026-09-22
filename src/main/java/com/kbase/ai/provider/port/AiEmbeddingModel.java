package com.kbase.ai.provider.port;

import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;

/** KBase-owned boundary for query and document embeddings. */
public interface AiEmbeddingModel {

    AiEmbeddingResult embed(AiEmbeddingRequest request);
}
