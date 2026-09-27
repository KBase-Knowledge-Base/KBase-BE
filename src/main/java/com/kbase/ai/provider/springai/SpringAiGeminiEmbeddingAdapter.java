package com.kbase.ai.provider.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.util.StringUtils;

/** KBase-owned embedding port adapter backed by Spring AI Google GenAI text embeddings. */
public final class SpringAiGeminiEmbeddingAdapter implements AiEmbeddingModel {

    public static final int REQUIRED_DIMENSIONS = 768;

    private final EmbeddingModel delegate;
    private final String configuredModel;
    private final int configuredDimensions;

    public SpringAiGeminiEmbeddingAdapter(EmbeddingModel delegate, AiProperties properties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(properties.getGemini(), "gemini properties must not be null");
        this.configuredModel = requireText(properties.getGemini().getEmbeddingModel(), "embeddingModel");
        this.configuredDimensions = properties.getGemini().getEmbeddingDimensions();
        if (configuredDimensions != REQUIRED_DIMENSIONS) {
            throw new AiProviderException(AiProviderErrorCategory.CONFIGURATION);
        }
    }

    @Override
    public AiEmbeddingResult embed(AiEmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        EmbeddingResponse response;
        try {
            response = delegate.call(new EmbeddingRequest(
                    List.of(SpringAiGeminiEmbeddingPreparation.prepare(request)), options()));
        }
        catch (RuntimeException failure) {
            throw SpringAiGeminiErrorTranslator.translate(failure);
        }
        return toSingleResult(response);
    }

    /**
     * One provider batch call for the whole bounded batch. Query/document
     * preparation stays per request, results map one-to-one in input order,
     * and every vector passes the same validation as the single path; any
     * mismatch is a safe INVALID_RESPONSE, never a partial success.
     */
    @Override
    public List<AiEmbeddingResult> embedAll(List<AiEmbeddingRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");
        if (requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() == 1) {
            return List.of(embed(requests.getFirst()));
        }
        List<String> prepared = new ArrayList<>(requests.size());
        for (AiEmbeddingRequest request : requests) {
            Objects.requireNonNull(request, "request must not be null");
            prepared.add(SpringAiGeminiEmbeddingPreparation.prepare(request));
        }
        EmbeddingResponse response;
        try {
            response = delegate.call(new EmbeddingRequest(prepared, options()));
        }
        catch (RuntimeException failure) {
            throw SpringAiGeminiErrorTranslator.translate(failure);
        }
        if (response == null || response.getResults() == null
                || response.getResults().size() != requests.size()) {
            throw invalidResponse();
        }
        List<AiEmbeddingResult> results = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            Embedding embedding = response.getResults().get(index);
            if (embedding == null || embedding.getOutput() == null
                    || embedding.getOutput().length != configuredDimensions) {
                throw invalidResponse();
            }
            results.add(toResult(embedding, configuredModel));
        }
        return List.copyOf(results);
    }

    private GoogleGenAiTextEmbeddingOptions options() {
        return GoogleGenAiTextEmbeddingOptions.builder()
                .model(configuredModel)
                .dimensions(configuredDimensions)
                // M0 confirmed that gemini-embedding-2 does not use task_type on this path.
                // Query/document semantics are therefore owned by preparedInput above.
                .build();
    }

    private AiEmbeddingResult toSingleResult(EmbeddingResponse response) {
        if (response == null || response.getResults() == null || response.getResults().size() != 1) {
            throw invalidResponse();
        }
        Embedding embedding = response.getResult();
        if (embedding == null || embedding.getOutput() == null
                || embedding.getOutput().length != configuredDimensions) {
            throw invalidResponse();
        }
        String modelId = configuredModel;
        if (response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())) {
            modelId = response.getMetadata().getModel();
        }
        return toResult(embedding, modelId);
    }

    private AiEmbeddingResult toResult(Embedding embedding, String modelId) {
        float[] output = embedding.getOutput();
        List<Double> values = new ArrayList<>(output.length);
        for (float value : output) {
            values.add((double) value);
        }
        try {
            return new AiEmbeddingResult(values, modelId);
        }
        catch (IllegalArgumentException failure) {
            throw invalidResponse();
        }
    }

    private static AiProviderException invalidResponse() {
        return new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
