package com.kbase.ai.extraction;

import java.util.Objects;

/** Deterministic chunk payload before embedding persistence. */
public record DocumentChunk(
        int chunkIndex,
        String content,
        SourceLocation sourceLocation,
        int tokenCount,
        String contentHash) {

    public DocumentChunk {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        content = Objects.requireNonNull(content, "content").trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        sourceLocation = sourceLocation == null ? SourceLocation.none() : sourceLocation;
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must not be negative");
        }
        contentHash = Objects.requireNonNull(contentHash, "contentHash");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
    }
}
