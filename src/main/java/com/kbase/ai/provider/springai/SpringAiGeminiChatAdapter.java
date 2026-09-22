package com.kbase.ai.provider.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.model.AiChatRole;
import com.kbase.ai.provider.model.AiEvidenceBlock;
import com.kbase.ai.provider.port.AiChatModel;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.util.StringUtils;

/** KBase-owned chat port adapter backed by Spring AI's Google GenAI model. */
public final class SpringAiGeminiChatAdapter implements AiChatModel {

    private final ChatModel delegate;
    private final String configuredModel;

    public SpringAiGeminiChatAdapter(ChatModel delegate, String configuredModel) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.configuredModel = requireText(configuredModel, "configuredModel");
    }

    @Override
    public AiChatResult generate(AiChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Prompt prompt = toPrompt(request);

        ChatResponse response;
        try {
            response = delegate.call(prompt);
        }
        catch (RuntimeException failure) {
            throw SpringAiGeminiErrorTranslator.translate(failure);
        }

        return toResult(response);
    }

    Prompt toPrompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (!request.systemInstructions().isBlank()) {
            messages.add(new SystemMessage(request.systemInstructions()));
        }
        for (AiChatMessage message : request.conversation()) {
            messages.add(toMessage(message));
        }
        if (!request.evidence().isEmpty()) {
            messages.add(new UserMessage(serializeEvidence(request.evidence())));
        }
        // The current question is deliberately appended last as the final user intent.
        messages.add(new UserMessage(request.question()));

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(configuredModel)
                .build();
        return new Prompt(messages, options);
    }

    private AiChatResult toResult(ChatResponse response) {
        if (response == null) {
            throw invalidResponse();
        }
        Generation generation;
        try {
            generation = response.getResult();
        }
        catch (RuntimeException failure) {
            throw SpringAiGeminiErrorTranslator.translate(failure);
        }
        if (generation == null || generation.getOutput() == null
                || !StringUtils.hasText(generation.getOutput().getText())) {
            throw invalidResponse();
        }

        String modelId = configuredModel;
        if (response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())) {
            modelId = response.getMetadata().getModel();
        }
        return new AiChatResult(generation.getOutput().getText(), modelId);
    }

    private static Message toMessage(AiChatMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    static String serializeEvidence(List<AiEvidenceBlock> evidence) {
        StringBuilder serialized = new StringBuilder("--- BEGIN KBASE EVIDENCE ---\n");
        for (AiEvidenceBlock block : evidence) {
            serialized.append("[EVIDENCE LABEL: ").append(block.label()).append("]\n")
                    .append(block.content()).append('\n');
        }
        return serialized.append("--- END KBASE EVIDENCE ---").toString();
    }

    private static AiProviderException invalidResponse() {
        return new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
