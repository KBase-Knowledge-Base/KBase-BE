package com.kbase.ai.provider.springai;

import java.util.List;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.model.EmbeddingMode;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiGeminiEmbeddingAdapterTest {

    @Test
    void preparesQueryWithKBaseQuestionAnsweringPrefixAndLockedOptions() {
        CapturingEmbeddingModel delegate = new CapturingEmbeddingModel(response(vector(768), "provider-model"));
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        AiEmbeddingResult result = adapter.embed(AiEmbeddingRequest.query("How does KBase work?"));

        assertThat(result.dimensions()).isEqualTo(768);
        assertThat(delegate.request.getInstructions())
                .containsExactly("task: question answering | query: How does KBase work?");
        GoogleGenAiTextEmbeddingOptions options =
                (GoogleGenAiTextEmbeddingOptions) delegate.request.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-embedding-2");
        assertThat(options.getDimensions()).isEqualTo(768);
        assertThat(delegate.request.getInstructions()).doesNotContain("DOCUMENT");
    }

    @Test
    void preparesDocumentWithTitleAndWithoutTitleDeterministically() {
        CapturingEmbeddingModel withTitle = new CapturingEmbeddingModel(response(vector(768), "model"));
        CapturingEmbeddingModel withoutTitle = new CapturingEmbeddingModel(response(vector(768), "model"));
        SpringAiGeminiEmbeddingAdapter titledAdapter =
                new SpringAiGeminiEmbeddingAdapter(withTitle, properties());
        SpringAiGeminiEmbeddingAdapter untitledAdapter =
                new SpringAiGeminiEmbeddingAdapter(withoutTitle, properties());

        titledAdapter.embed(AiEmbeddingRequest.document("KBase Guide", "KBase content"));
        untitledAdapter.embed(AiEmbeddingRequest.document("KBase content"));

        assertThat(withTitle.request.getInstructions())
                .containsExactly("title: KBase Guide | text: KBase content");
        assertThat(withoutTitle.request.getInstructions())
                .containsExactly("title: none | text: KBase content");
    }

    @Test
    void acceptsExactly768Values() {
        CapturingEmbeddingModel delegate = new CapturingEmbeddingModel(response(vector(768), "model"));

        AiEmbeddingResult result = new SpringAiGeminiEmbeddingAdapter(delegate, properties())
                .embed(AiEmbeddingRequest.document("text"));

        assertThat(result.dimensions()).isEqualTo(768);
    }

    @Test
    void rejects767And769ValuesWithoutPaddingOrTruncation() {
        for (int dimension : List.of(767, 769)) {
            CapturingEmbeddingModel delegate = new CapturingEmbeddingModel(response(vector(dimension), "model"));
            SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

            assertThatThrownBy(() -> adapter.embed(AiEmbeddingRequest.query("question")))
                    .isInstanceOf(AiProviderException.class)
                    .satisfies(error -> {
                        AiProviderException providerException = (AiProviderException) error;
                        assertThat(providerException.category()).isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE);
                    });
        }
    }

    @Test
    void rejectsNonFiniteValuesAsInvalidResponse() {
        float[] values = vector(768);
        values[17] = Float.NaN;
        CapturingEmbeddingModel delegate = new CapturingEmbeddingModel(response(values, "model"));
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        assertThatThrownBy(() -> adapter.embed(AiEmbeddingRequest.query("question")))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).category())
                .isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    @Test
    void translatesProviderFailureWithoutExposingItsMessage() {
        CapturingEmbeddingModel delegate = new CapturingEmbeddingModel(null);
        delegate.failure = new com.google.genai.errors.ApiException(
                429, "RESOURCE_EXHAUSTED", "PROVIDER_BODY_SENTINEL");
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        assertThatThrownBy(() -> adapter.embed(AiEmbeddingRequest.document("DOCUMENT_SENTINEL")))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider rate limit was reached.")
                .hasMessageNotContaining("PROVIDER_BODY_SENTINEL")
                .satisfies(error -> assertThat(((AiProviderException) error).category())
                        .isEqualTo(AiProviderErrorCategory.RATE_LIMITED));
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getGemini().setEmbeddingModel("gemini-embedding-2");
        properties.getGemini().setEmbeddingDimensions(768);
        return properties;
    }

    private static float[] vector(int dimension) {
        float[] values = new float[dimension];
        if (dimension > 0) {
            values[0] = 1.0f;
        }
        return values;
    }

    private static EmbeddingResponse response(float[] values, String model) {
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        metadata.setModel(model);
        return new EmbeddingResponse(List.of(new Embedding(values, 0)), metadata);
    }

    private static final class CapturingEmbeddingModel implements EmbeddingModel {

        private final EmbeddingResponse response;
        private EmbeddingRequest request;
        private RuntimeException failure;

        private CapturingEmbeddingModel(EmbeddingResponse response) {
            this.response = response;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("test double does not embed Documents");
        }
    }
}
