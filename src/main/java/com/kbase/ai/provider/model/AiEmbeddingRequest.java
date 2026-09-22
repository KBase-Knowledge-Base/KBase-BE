package com.kbase.ai.provider.model;

import java.util.Objects;

/** Provider-neutral query/document embedding input. */
public record AiEmbeddingRequest(EmbeddingMode mode, String content, String title) {

    public AiEmbeddingRequest(EmbeddingMode mode, String content) {
        this(mode, content, null);
    }

    public AiEmbeddingRequest {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        content = requireText(content, "content");
        title = title == null || title.isBlank() ? null : title;
        if (mode == EmbeddingMode.QUERY && title != null) {
            throw new IllegalArgumentException("query embeddings must not have a title");
        }
    }

    public static AiEmbeddingRequest query(String content) {
        return new AiEmbeddingRequest(EmbeddingMode.QUERY, content);
    }

    public static AiEmbeddingRequest document(String content) {
        return new AiEmbeddingRequest(EmbeddingMode.DOCUMENT, content);
    }

    public static AiEmbeddingRequest document(String title, String content) {
        return new AiEmbeddingRequest(EmbeddingMode.DOCUMENT, content, title);
    }

    public EmbeddingMode inputType() {
        return mode;
    }

    public String text() {
        return content;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
