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
        String preparedInput = SpringAiGeminiEmbeddingPreparation.prepare(request);
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(configuredModel)
                .dimensions(configuredDimensions)
                // M0 confirmed that gemini-embedding-2 does not use task_type on this path.
                // Query/document semantics are therefore owned by preparedInput above.
                .build();
        EmbeddingResponse response;
        try {
            response = delegate.call(new EmbeddingRequest(List.of(preparedInput), options));
        }
        catch (RuntimeException failure) {
            throw SpringAiGeminiErrorTranslator.translate(failure);
        }

        return toResult(response);
    }

    private AiEmbeddingResult toResult(EmbeddingResponse response) {
        if (response == null || response.getResults() == null || response.getResults().size() != 1) {
            throw invalidResponse();
        }
        Embedding embedding = response.getResult();
        if (embedding == null || embedding.getOutput() == null
                || embedding.getOutput().length != configuredDimensions) {
            throw invalidResponse();
        }

        float[] output = embedding.getOutput();
        List<Double> values = new ArrayList<>(output.length);
        for (float value : output) {
            values.add((double) value);
        }

        String modelId = configuredModel;
        if (response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())) {
            modelId = response.getMetadata().getModel();
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
