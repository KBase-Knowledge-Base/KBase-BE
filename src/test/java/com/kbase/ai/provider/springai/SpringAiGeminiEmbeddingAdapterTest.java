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

    @Test
    void batchEmbeddingCallsProviderOnceInOrderWithOneToOneResults() {
        EchoingEmbeddingModel delegate = new EchoingEmbeddingModel();
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        List<AiEmbeddingResult> results = adapter.embedAll(List.of(
                AiEmbeddingRequest.document("Title", "first chunk"),
                AiEmbeddingRequest.document("Title", "second chunk"),
                AiEmbeddingRequest.document("Title", "third chunk")));

        assertThat(results).hasSize(3);
        assertThat(delegate.callCount).isEqualTo(1);
        assertThat(delegate.capturedInstructions).containsExactly(
                "title: Title | text: first chunk",
                "title: Title | text: second chunk",
                "title: Title | text: third chunk");
        assertThat(results).allSatisfy(result -> assertThat(result.dimensions()).isEqualTo(768));
    }

    @Test
    void batchResponseCountMismatchIsInvalidResponseWithoutPartialSuccess() {
        EchoingEmbeddingModel delegate = new EchoingEmbeddingModel().shortResponse();
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        assertThatThrownBy(() -> adapter.embedAll(List.of(
                AiEmbeddingRequest.document("one"),
                AiEmbeddingRequest.document("two"))))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).category())
                .isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    @Test
    void batchVectorDimensionMismatchIsInvalidResponse() {
        EchoingEmbeddingModel delegate = new EchoingEmbeddingModel().corruptLastVector();
        SpringAiGeminiEmbeddingAdapter adapter = new SpringAiGeminiEmbeddingAdapter(delegate, properties());

        assertThatThrownBy(() -> adapter.embedAll(List.of(
                AiEmbeddingRequest.document("one"),
                AiEmbeddingRequest.document("two"))))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).category())
                .isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE);
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

    /** Echoes one 768-dim result per instruction, capturing call count and order. */
    private static final class EchoingEmbeddingModel implements EmbeddingModel {

        private final List<String> capturedInstructions = new java.util.ArrayList<>();
        private int callCount;
        private boolean shortResponse;
        private boolean corruptLastVector;

        private EchoingEmbeddingModel shortResponse() {
            this.shortResponse = true;
            return this;
        }

        private EchoingEmbeddingModel corruptLastVector() {
            this.corruptLastVector = true;
            return this;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            callCount++;
            capturedInstructions.addAll(request.getInstructions());
            List<Embedding> results = new java.util.ArrayList<>();
            int count = shortResponse ? request.getInstructions().size() - 1
                    : request.getInstructions().size();
            for (int index = 0; index < count; index++) {
                float[] values = vector(768);
                if (corruptLastVector && index == count - 1) {
                    values = vector(767);
                }
                results.add(new Embedding(values, index));
            }
            return new EmbeddingResponse(results, new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("test double does not embed Documents");
        }
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
