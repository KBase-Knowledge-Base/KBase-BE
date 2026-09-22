package com.kbase.ai.provider.springai;

import java.util.List;

import com.google.genai.errors.ApiException;
import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEvidenceBlock;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class AiProviderPrivacyTest {

    @Test
    void chatFailureDoesNotLogOrExposePromptEvidenceOrProviderBody(CapturedOutput output) {
        String prompt = "PROMPT_SENTINEL_DO_NOT_LOG";
        String document = "DOCUMENT_SENTINEL_DO_NOT_LOG";
        String providerBody = "PROVIDER_BODY_SENTINEL_DO_NOT_LOG";
        FailingChatModel delegate = new FailingChatModel(
                new ApiException(500, "INTERNAL", providerBody));
        AiChatModel adapter = new SpringAiGeminiChatAdapter(delegate, "gemini-2.5-flash");

        assertThatThrownBy(() -> adapter.generate(new AiChatRequest(
                prompt, List.of(), List.of(new AiEvidenceBlock("SOURCE", document)), prompt)))
                .isInstanceOf(AiProviderException.class)
                .hasMessageNotContaining(prompt)
                .hasMessageNotContaining(document)
                .hasMessageNotContaining(providerBody);
        assertThat(output.getOut()).doesNotContain(prompt, document, providerBody);
        assertThat(output.getErr()).doesNotContain(prompt, document, providerBody);
    }

    @Test
    void embeddingFailureDoesNotLogOrExposeDocumentOrProviderBody(CapturedOutput output) {
        String document = "EMBEDDING_SENTINEL_DO_NOT_LOG";
        String providerBody = "EMBED_PROVIDER_BODY_SENTINEL_DO_NOT_LOG";
        AiEmbeddingModel adapter = new SpringAiGeminiEmbeddingAdapter(
                new FailingEmbeddingModel(new ApiException(400, "INVALID_ARGUMENT", providerBody)),
                new AiProperties());

        assertThatThrownBy(() -> adapter.embed(AiEmbeddingRequest.document(document)))
                .isInstanceOf(AiProviderException.class)
                .hasMessageNotContaining(document)
                .hasMessageNotContaining(providerBody);
        assertThat(output.getOut()).doesNotContain(document, providerBody);
        assertThat(output.getErr()).doesNotContain(document, providerBody);
    }

    private static final class FailingChatModel implements org.springframework.ai.chat.model.ChatModel {

        private final RuntimeException failure;

        private FailingChatModel(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public org.springframework.ai.chat.model.ChatResponse call(
                org.springframework.ai.chat.prompt.Prompt prompt) {
            throw failure;
        }
    }

    private static final class FailingEmbeddingModel implements EmbeddingModel {

        private final RuntimeException failure;

        private FailingEmbeddingModel(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(EmbeddingRequest request) {
            throw failure;
        }

        @Override
        public float[] embed(Document document) {
            throw failure;
        }
    }
}
