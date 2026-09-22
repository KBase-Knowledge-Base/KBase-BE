package com.kbase.ai.provider.springai;

import java.util.List;

import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.model.AiEvidenceBlock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiGeminiChatAdapterTest {

    @Test
    void mapsSystemConversationEvidenceAndQuestionDeterministically() {
        CapturingChatModel delegate = new CapturingChatModel();
        SpringAiGeminiChatAdapter adapter = new SpringAiGeminiChatAdapter(delegate, "gemini-2.5-flash");
        AiChatRequest request = new AiChatRequest(
                "System policy",
                List.of(AiChatMessage.user("previous question"), AiChatMessage.assistant("previous answer")),
                List.of(
                        new AiEvidenceBlock("SOURCE_1", "first evidence"),
                        new AiEvidenceBlock("SOURCE_2", "second evidence")),
                "current question");

        AiChatResult result = adapter.generate(request);

        assertThat(result.generatedText()).isEqualTo("provider answer");
        assertThat(result.modelId()).isEqualTo("provider-model");
        assertThat(delegate.prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        assertThat(((GoogleGenAiChatOptions) delegate.prompt.getOptions()).getModel())
                .isEqualTo("gemini-2.5-flash");

        List<Message> messages = delegate.prompt.getInstructions();
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).getText()).isEqualTo("System policy");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).getText()).isEqualTo("previous question");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) messages.get(2)).getText()).isEqualTo("previous answer");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        String evidence = ((UserMessage) messages.get(3)).getText();
        assertThat(evidence).contains("SOURCE_1", "first evidence", "SOURCE_2", "second evidence");
        assertThat(((SystemMessage) messages.get(0)).getText()).doesNotContain("SOURCE_1", "first evidence");
        assertThat(messages.get(4)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(4)).getText()).isEqualTo("current question");
    }

    @Test
    void omitsBlankSystemAndEvidenceMessagesButKeepsQuestionFinal() {
        CapturingChatModel delegate = new CapturingChatModel();
        SpringAiGeminiChatAdapter adapter = new SpringAiGeminiChatAdapter(delegate, "configured-model");

        adapter.generate(AiChatRequest.of("question"));

        assertThat(delegate.prompt.getInstructions()).hasSize(1);
        assertThat(delegate.prompt.getInstructions().get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) delegate.prompt.getInstructions().get(0)).getText()).isEqualTo("question");
    }

    @Test
    void fallsBackToConfiguredModelWhenProviderMetadataHasNoModel() {
        CapturingChatModel delegate = new CapturingChatModel();
        delegate.response = response("answer", "");
        SpringAiGeminiChatAdapter adapter = new SpringAiGeminiChatAdapter(delegate, "configured-model");

        assertThat(adapter.generate(AiChatRequest.of("question")).modelId()).isEqualTo("configured-model");
    }

    @Test
    void rejectsMissingProviderContentAsInvalidResponse() {
        CapturingChatModel delegate = new CapturingChatModel();
        delegate.response = response(" ", "provider-model");
        SpringAiGeminiChatAdapter adapter = new SpringAiGeminiChatAdapter(delegate, "configured-model");

        assertThatThrownBy(() -> adapter.generate(AiChatRequest.of("question")))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> {
                    AiProviderException providerException = (AiProviderException) error;
                    assertThat(providerException.category()).isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE);
                });
    }

    private static ChatResponse response(String text, String model) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model(model).build());
    }

    private static final class CapturingChatModel implements ChatModel {

        private Prompt prompt;
        private ChatResponse response = response("provider answer", "provider-model");

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return response;
        }
    }
}
