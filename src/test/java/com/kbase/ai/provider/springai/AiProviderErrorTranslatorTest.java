package com.kbase.ai.provider.springai;

import java.net.SocketTimeoutException;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderErrorTranslatorTest {

    @Test
    void mapsTimeoutToRetryableTimeoutCategory() {
        AiProviderException translated = SpringAiGeminiErrorTranslator
                .translate(new SocketTimeoutException("TIMEOUT_SENTINEL"));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.TIMEOUT);
        assertThat(translated.retryable()).isTrue();
        assertThat(translated).hasMessage("AI provider request timed out.")
                .hasMessageNotContaining("TIMEOUT_SENTINEL");
    }

    @Test
    void mapsTimeoutCauseInsideGenAiIoException() {
        AiProviderException translated = SpringAiGeminiErrorTranslator.translate(
                new GenAiIOException("GENAI_IO_SENTINEL", new SocketTimeoutException("TIMEOUT_SENTINEL")));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.TIMEOUT);
        assertThat(translated.retryable()).isTrue();
    }

    @Test
    void maps429ToRateLimited() {
        AiProviderException translated = SpringAiGeminiErrorTranslator.translate(
                new ApiException(429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_SENTINEL"));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.RATE_LIMITED);
        assertThat(translated.retryable()).isTrue();
        assertThat(translated).hasMessageNotContaining("RATE_LIMIT_SENTINEL");
    }

    @Test
    void mapsRateLimitCauseInsideSpringAiTransientException() {
        AiProviderException translated = SpringAiGeminiErrorTranslator.translate(
                new TransientAiException("SPRING_RETRY_SENTINEL",
                        new ApiException(429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_SENTINEL")));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.RATE_LIMITED);
        assertThat(translated.retryable()).isTrue();
    }

    @Test
    void mapsCredentialAndPermissionFailuresToConfiguration() {
        for (int status : new int[] {400, 401, 403, 404}) {
            AiProviderException translated = SpringAiGeminiErrorTranslator.translate(
                    new ApiException(status, "CONFIGURATION", "CONFIG_SENTINEL"));
            assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.CONFIGURATION);
            assertThat(translated.retryable()).isFalse();
            assertThat(translated).hasMessageNotContaining("CONFIG_SENTINEL");
        }
    }

    @Test
    void mapsTemporaryServerFailuresToUnavailable() {
        AiProviderException translated = SpringAiGeminiErrorTranslator.translate(
                new ApiException(503, "UNAVAILABLE", "SERVER_SENTINEL"));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.UNAVAILABLE);
        assertThat(translated.retryable()).isTrue();
        assertThat(translated).hasMessageNotContaining("SERVER_SENTINEL");
    }

    @Test
    void mapsUnknownFailureToSafeUnavailableCategory() {
        AiProviderException translated = SpringAiGeminiErrorTranslator
                .translate(new RuntimeException("UNKNOWN_PROVIDER_SENTINEL"));

        assertThat(translated.category()).isEqualTo(AiProviderErrorCategory.UNAVAILABLE);
        assertThat(translated).hasMessage("AI provider is temporarily unavailable.")
                .hasMessageNotContaining("UNKNOWN_PROVIDER_SENTINEL");
    }
}
