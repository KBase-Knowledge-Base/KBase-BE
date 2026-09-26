package com.kbase.ai.provider.springai;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.deterministic.DeterministicRuntimeProviderConfiguration;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiEvidenceBlock;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AiGeminiProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void disabledAiStartsWithoutKeyOrProviderBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(AiChatModel.class)).isEmpty();
            assertThat(context.getBeansOfType(AiEmbeddingModel.class)).isEmpty();
            assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
            assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();
        });
    }

    @Test
    void enabledAiWithoutKeyFailsWithSafeConfigurationError() {
        contextRunner
                .withPropertyValues("kbase.ai.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("AI provider configuration is invalid.")
                            .satisfies(failure -> assertThat(failure.toString())
                                    .doesNotContain("KBASE_AI_GEMINI_API_KEY"));
                });
    }

    @Test
    void enabledAiWithTestOnlyKeyBuildsExplicitModelsWithoutCallingNetwork(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "kbase.ai.enabled=true",
                        "kbase.ai.gemini.api-key=SECRET_KEY_SENTINEL_DO_NOT_LOG",
                        "kbase.ai.provider.request-timeout=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(AiChatModel.class)).hasSize(1);
                    assertThat(context.getBeansOfType(AiEmbeddingModel.class)).hasSize(1);
                    assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EmbeddingModel.class)).hasSize(1);
                });
        assertThat(output.getOut()).doesNotContain("SECRET_KEY_SENTINEL_DO_NOT_LOG");
        assertThat(output.getErr()).doesNotContain("SECRET_KEY_SENTINEL_DO_NOT_LOG");
    }

    @Test
    void deterministicModeFailsWithoutTheDedicatedRuntimeTestProfile() {
        contextRunner
                .withPropertyValues(
                        "kbase.ai.enabled=true",
                        "kbase.ai.provider.mode=deterministic",
                        "kbase.ai.provider.deterministic.runtime-test-acknowledged=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "requires the runtime-test profile and explicit acknowledgement");
                });
    }

    @Test
    void acknowledgedRuntimeTestProfileUsesNetworkFreeDeterministicPorts() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("runtime-test"))
                .withPropertyValues(
                        "kbase.ai.enabled=true",
                        "kbase.ai.provider.mode=deterministic",
                        "kbase.ai.provider.deterministic.runtime-test-acknowledged=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
                    assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();

                    AiEmbeddingModel embeddings = context.getBean(AiEmbeddingModel.class);
                    assertThat(embeddings.embed(com.kbase.ai.provider.model.AiEmbeddingRequest.query("runtime marker"))
                            .vector()).hasSize(768);
                    var documentedQuestion = embeddings.embed(
                            com.kbase.ai.provider.model.AiEmbeddingRequest.query(
                                    "MEMBER có thể xóa file không?")).vector();
                    var documentedEvidence = embeddings.embed(
                            com.kbase.ai.provider.model.AiEmbeddingRequest.document(
                                    "MEMBER may delete a document in a project.")).vector();
                    double similarity = java.util.stream.IntStream.range(0, documentedQuestion.size())
                            .mapToDouble(index -> documentedQuestion.get(index) * documentedEvidence.get(index))
                            .sum();
                    assertThat(similarity).isGreaterThan(0.70);

                    AiChatModel chat = context.getBean(AiChatModel.class);
                    assertThat(chat.generate(new AiChatRequest("system", java.util.List.of(),
                            java.util.List.of(new AiEvidenceBlock("SOURCE_1", "runtime marker")),
                            "runtime marker")).text()).contains("[SOURCE_1]");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    @Import({AiGeminiProviderConfiguration.class, DeterministicRuntimeProviderConfiguration.class})
    static class TestConfiguration {
    }
}
