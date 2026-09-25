package com.kbase.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.kbase.ai.provider.fake.FakeAiEmbeddingModel;
import com.kbase.ai.provider.model.AiEmbeddingRequest;

import org.junit.jupiter.api.Test;

/**
 * Small reproducible M10 tuning matrix using the existing deterministic
 * embedding fake. It is not a claim of calibration against live Gemini.
 */
class RetrievalThresholdEvaluationTest {

    private static final double SELECTED_THRESHOLD = 0.70;

    @Test
    void selectedThresholdSeparatesRelevantWeakAndUnrelatedFixtureBands() {
        FakeAiEmbeddingModel embeddings = new FakeAiEmbeddingModel()
                .registerFixture("documented Guide question", FakeAiEmbeddingModel.unitVector(0))
                .registerFixture("project positive question", FakeAiEmbeddingModel.unitVector(0))
                .registerFixture("Guide off-topic question", FakeAiEmbeddingModel.unitVector(1))
                .registerFixture("project no-evidence question", FakeAiEmbeddingModel.unitVector(1));

        double relevant = cosine(embeddings.embed(AiEmbeddingRequest.query("documented Guide question")),
                FakeAiEmbeddingModel.unitVector(0));
        double weak = cosine(embeddings.embed(AiEmbeddingRequest.query("project positive question")),
                weakEvidenceVector());
        double unrelated = cosine(embeddings.embed(AiEmbeddingRequest.query("Guide off-topic question")),
                FakeAiEmbeddingModel.unitVector(0));

        assertThat(relevant).isBetween(0.99, 1.0);
        assertThat(weak).isBetween(0.64, 0.66);
        assertThat(unrelated).isBetween(-0.01, 0.01);
        assertThat(relevant >= SELECTED_THRESHOLD).isTrue();
        assertThat(weak >= SELECTED_THRESHOLD).isFalse();
        assertThat(unrelated >= SELECTED_THRESHOLD).isFalse();
    }

    private static List<Double> weakEvidenceVector() {
        double first = 0.65;
        double second = Math.sqrt(1.0 - first * first);
        double[] vector = new double[FakeAiEmbeddingModel.DIMENSIONS];
        vector[0] = first;
        vector[1] = second;
        return java.util.Arrays.stream(vector).boxed().toList();
    }

    private static double cosine(com.kbase.ai.provider.model.AiEmbeddingResult result,
            List<Double> other) {
        double value = 0.0;
        for (int index = 0; index < result.vector().size(); index++) {
            value += result.vector().get(index) * other.get(index);
        }
        return value;
    }
}
