package com.kbase.ai.extraction;

import java.util.Objects;

/** One ordered, non-empty text block emitted by a document extractor. */
public record ExtractedBlock(String text, SourceLocation sourceLocation) {

    public ExtractedBlock {
        text = Objects.requireNonNull(text, "text").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        sourceLocation = sourceLocation == null ? SourceLocation.none() : sourceLocation;
    }
}
