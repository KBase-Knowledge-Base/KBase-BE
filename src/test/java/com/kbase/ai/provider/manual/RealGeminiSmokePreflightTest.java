package com.kbase.ai.provider.manual;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated evidence for the manual smoke's fail-before-provider-call
 * preflight: any drift from the approved candidate configuration produces
 * violations, and only the exact approved configuration passes. Pure unit
 * test — no Spring context and no Gemini network.
 */
class RealGeminiSmokePreflightTest {

    @Test
    void approvedCandidateConfigurationPassesWithNoViolations() {
        assertThat(RealGeminiAdapterManualSmoke.candidateViolations(
                "gemini", "gemini-3.5-flash-lite", "gemini-embedding-2", "768")).isEmpty();
    }

    @Test
    void wrongProviderModeIsRejected() {
        List<String> violations = RealGeminiAdapterManualSmoke.candidateViolations(
                "deterministic", "gemini-3.5-flash-lite", "gemini-embedding-2", "768");
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("provider.mode");
    }

    @Test
    void wrongChatModelIsRejected() {
        List<String> violations = RealGeminiAdapterManualSmoke.candidateViolations(
                "gemini", "gemini-2.5-flash", "gemini-embedding-2", "768");
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("chat model");
    }

    @Test
    void wrongEmbeddingModelIsRejected() {
        List<String> violations = RealGeminiAdapterManualSmoke.candidateViolations(
                "gemini", "gemini-3.5-flash-lite", "text-embedding-004", "768");
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("embedding model");
    }

    @Test
    void wrongEmbeddingDimensionsIsRejected() {
        List<String> violations = RealGeminiAdapterManualSmoke.candidateViolations(
                "gemini", "gemini-3.5-flash-lite", "gemini-embedding-2", "1536");
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains("embedding dimensions");
    }

    @Test
    void everyDriftIsReportedTogether() {
        List<String> violations = RealGeminiAdapterManualSmoke.candidateViolations(
                "deterministic", "gemini-2.5-flash", "text-embedding-004", "1536");
        assertThat(violations).hasSize(4);
    }
}
