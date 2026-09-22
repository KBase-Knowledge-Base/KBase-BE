package com.kbase.ai.provider.fake;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.EmbeddingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiEmbeddingModelTest {

    @Test
    void unknownTextIsStableNormalizedAndExactly768Dimensions() {
        FakeAiEmbeddingModel fake = new FakeAiEmbeddingModel();
        AiEmbeddingRequest request = AiEmbeddingRequest.query("stable text");

        List<Double> first = fake.embed(request).vector();
        List<Double> second = fake.embed(request).vector();

        assertThat(first).hasSize(768).containsExactlyElementsOf(second);
        double norm = Math.sqrt(first.stream().mapToDouble(value -> value * value).sum());
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    @Test
    void fixturesControlSemanticOrderingAndCaptureQueryDocumentMode() {
        FakeAiEmbeddingModel fake = new FakeAiEmbeddingModel()
                .registerLabel("query", 0)
                .registerLabel("near-document", 0)
                .registerLabel("far-document", 1);

        fake.embed(AiEmbeddingRequest.query("query"));
        fake.embed(AiEmbeddingRequest.document("near-document"));
        fake.embed(AiEmbeddingRequest.document("far-document"));

        List<Double> query = fake.embed(AiEmbeddingRequest.query("query")).vector();
        List<Double> near = fake.embed(AiEmbeddingRequest.document("near-document")).vector();
        List<Double> far = fake.embed(AiEmbeddingRequest.document("far-document")).vector();

        assertThat(cosine(query, near)).isGreaterThan(cosine(query, far));
        assertThat(fake.capturedModes()).containsExactly(
                EmbeddingMode.QUERY,
                EmbeddingMode.DOCUMENT,
                EmbeddingMode.DOCUMENT,
                EmbeddingMode.QUERY,
                EmbeddingMode.DOCUMENT,
                EmbeddingMode.DOCUMENT);
    }

    @Test
    void rejectsWrongDimensionFixturesAndSupportsFailureSimulation() {
        FakeAiEmbeddingModel fake = new FakeAiEmbeddingModel();
        assertThatThrownBy(() -> fake.registerFixture("wrong", List.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("768");

        fake.simulateUnavailable();
        assertThatThrownBy(() -> fake.embed(AiEmbeddingRequest.query("question")))
                .isInstanceOf(FakeAiProviderException.class);
    }

    private static double cosine(List<Double> left, List<Double> right) {
        return left.stream().mapToDouble(value -> value).toArray().length == right.size()
                ? dot(left, right)
                : 0.0;
    }

    private static double dot(List<Double> left, List<Double> right) {
        double result = 0.0;
        for (int index = 0; index < left.size(); index++) {
            result += left.get(index) * right.get(index);
        }
        return result;
    }
}
