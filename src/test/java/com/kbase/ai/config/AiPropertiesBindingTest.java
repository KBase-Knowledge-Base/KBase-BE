package com.kbase.ai.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiPropertiesConfiguration.class);

    @Test
    void bindsSafeDefaultsWithoutAProviderCredential() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getGemini().getApiKey()).isNull();
            assertThat(properties.getGemini().getChatModel()).isEqualTo("gemini-2.5-flash");
            assertThat(properties.getGemini().getEmbeddingModel()).isEqualTo("gemini-embedding-2");
            assertThat(properties.getGemini().getEmbeddingDimensions()).isEqualTo(768);
            assertThat(properties.getProvider().getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.getProvider().getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getMaxMessageChars()).isEqualTo(8_000);
            assertThat(properties.getChunkTargetTokens()).isEqualTo(700);
            assertThat(properties.getChunkOverlapPercent()).isEqualTo(12);
            assertThat(properties.getRetrievalCandidateLimit()).isEqualTo(10);
            assertThat(properties.getRetrievalFinalContextLimit()).isEqualTo(6);
            assertThat(properties.getRetrievalSimilarityThreshold()).isNull();
            assertThat(properties.getWorker().getPollInterval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getWorker().getBatchSize()).isEqualTo(10);
            assertThat(properties.getWorker().getLeaseTimeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.getWorker().getRetryBackoff()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getWorker().getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.getUsageRateNamespace()).isEqualTo("kbase:ai:rate");
        });
    }

    @Test
    void bindsExplicitValuesUsingKBasePropertyNames() {
        contextRunner
                .withPropertyValues(
                        "kbase.ai.enabled=true",
                        "kbase.ai.gemini.api-key=test-only-key",
                        "kbase.ai.gemini.chat-model=test-chat",
                        "kbase.ai.gemini.embedding-model=test-embedding",
                        "kbase.ai.gemini.embedding-dimensions=768",
                        "kbase.ai.provider.connect-timeout=11s",
                        "kbase.ai.provider.request-timeout=61s",
                        "kbase.ai.max-message-chars=9000",
                        "kbase.ai.chunk-target-tokens=701",
                        "kbase.ai.chunk-overlap-percent=13",
                        "kbase.ai.retrieval-candidate-limit=11",
                        "kbase.ai.retrieval-final-context-limit=7",
                        "kbase.ai.retrieval-similarity-threshold=0.72",
                        "kbase.ai.worker.poll-interval=6s",
                        "kbase.ai.worker.batch-size=11",
                        "kbase.ai.worker.lease-timeout=3m",
                        "kbase.ai.worker.retry-backoff=31s",
                        "kbase.ai.worker.max-attempts=4",
                        "kbase.ai.retention=8d",
                        "kbase.ai.usage-rate-namespace=test:ai:rate")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiProperties properties = context.getBean(AiProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getGemini().getApiKey()).isEqualTo("test-only-key");
                    assertThat(properties.getGemini().getChatModel()).isEqualTo("test-chat");
                    assertThat(properties.getProvider().getConnectTimeout()).isEqualTo(Duration.ofSeconds(11));
                    assertThat(properties.getProvider().getRequestTimeout()).isEqualTo(Duration.ofSeconds(61));
                    assertThat(properties.getMaxMessageChars()).isEqualTo(9_000);
                    assertThat(properties.getChunkTargetTokens()).isEqualTo(701);
                    assertThat(properties.getChunkOverlapPercent()).isEqualTo(13);
                    assertThat(properties.getRetrievalSimilarityThreshold()).isEqualTo(0.72);
                    assertThat(properties.getWorker().getLeaseTimeout()).isEqualTo(Duration.ofMinutes(3));
                    assertThat(properties.getWorker().getRetryBackoff()).isEqualTo(Duration.ofSeconds(31));
                    assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(8));
                    assertThat(properties.getUsageRateNamespace()).isEqualTo("test:ai:rate");
                });
    }

    @Test
    void rejectsDimensionsOutsideTheM1VectorContract() {
        contextRunner
                .withPropertyValues("kbase.ai.gemini.embedding-dimensions=1536")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveDurationsAndLimits() {
        contextRunner
                .withPropertyValues(
                        "kbase.ai.provider.connect-timeout=0s",
                        "kbase.ai.max-message-chars=0",
                        "kbase.ai.chunk-overlap-percent=100")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    static class AiPropertiesConfiguration {
    }
}
