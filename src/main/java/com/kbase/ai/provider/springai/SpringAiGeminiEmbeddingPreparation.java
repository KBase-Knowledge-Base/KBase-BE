package com.kbase.ai.provider.springai;

import java.util.Objects;

import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.EmbeddingMode;

/** KBase-owned deterministic preparation for gemini-embedding-2 input. */
final class SpringAiGeminiEmbeddingPreparation {

    private SpringAiGeminiEmbeddingPreparation() {
    }

    static String prepare(AiEmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return switch (request.mode()) {
            case QUERY -> "task: question answering | query: " + request.content();
            case DOCUMENT -> "title: " + (request.title() == null ? "none" : request.title())
                    + " | text: " + request.content();
        };
    }
}
