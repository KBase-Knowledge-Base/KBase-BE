package com.kbase.ai.provider.model;

import java.util.List;
import java.util.Objects;

/**
 * Bounded, provider-neutral generation input owned by KBase.
 *
 * <p>Conversation and evidence are deliberately generic so the port does not
 * become a Project Assistant persistence DTO or an HTTP request model.</p>
 */
public record AiChatRequest(
        String systemInstructions,
        List<AiChatMessage> conversation,
        List<AiEvidenceBlock> evidence,
        String question) {

    public AiChatRequest {
        systemInstructions = systemInstructions == null ? "" : systemInstructions;
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        question = requireText(question, "question");
    }

    public static AiChatRequest of(String question) {
        return new AiChatRequest("", List.of(), List.of(), question);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
