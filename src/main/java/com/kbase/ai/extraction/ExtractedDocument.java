package com.kbase.ai.extraction;

import java.util.ArrayList;
import java.util.List;

/** Immutable KBase-owned extraction result. An empty list means no extractable text. */
public record ExtractedDocument(List<ExtractedBlock> blocks) {

    public ExtractedDocument {
        blocks = blocks == null ? List.of() : List.copyOf(new ArrayList<>(blocks));
    }

    public boolean hasText() {
        return !blocks.isEmpty();
    }
}
