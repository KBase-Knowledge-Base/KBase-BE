package com.kbase.ai.provider.port;

import java.util.ArrayList;
import java.util.List;

import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;

/** KBase-owned boundary for query and document embeddings. */
public interface AiEmbeddingModel {

    AiEmbeddingResult embed(AiEmbeddingRequest request);

    /**
     * Embeds a bounded batch in order. The default delegates to single
     * requests; adapters may override with a true provider batch call, but
     * results must map back one-to-one in input order and each vector must
     * satisfy the same validation as {@link #embed}.
     */
    default List<AiEmbeddingResult> embedAll(List<AiEmbeddingRequest> requests) {
        List<AiEmbeddingResult> results = new ArrayList<>(requests.size());
        for (AiEmbeddingRequest request : requests) {
            results.add(embed(request));
        }
        return results;
    }
}
