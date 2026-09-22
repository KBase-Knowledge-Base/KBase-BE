package com.kbase.ai.provider.model;

import java.util.Objects;

/** Provider-neutral generated text and optional model identifier. */
public record AiChatResult(String text, String modelId) {

    public AiChatResult {
        text = Objects.requireNonNull(text, "text must not be null");
        modelId = modelId == null ? "" : modelId;
    }

    public AiChatResult(String text) {
        this(text, "");
    }

    /** Alias that makes the application-level meaning explicit at call sites. */
    public String generatedText() {
        return text;
    }
}
