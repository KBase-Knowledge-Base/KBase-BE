package com.kbase.ai.provider.model;

import java.util.Objects;

/** A bounded conversation turn passed through the KBase chat port. */
public record AiChatMessage(AiChatRole role, String content) {

    public AiChatMessage {
        role = Objects.requireNonNull(role, "role must not be null");
        content = requireText(content, "content");
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(AiChatRole.USER, content);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(AiChatRole.ASSISTANT, content);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
