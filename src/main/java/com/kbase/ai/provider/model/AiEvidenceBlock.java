package com.kbase.ai.provider.model;

import java.util.Objects;

/**
 * Provider-neutral evidence content. It intentionally carries no persistence
 * entity, document DTO, storage key, or public API contract.
 */
public record AiEvidenceBlock(String label, String content) {

    public AiEvidenceBlock {
        label = requireText(label, "label");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
