package com.kbase.ai.extraction;

import java.util.ArrayList;
import java.util.List;

/** Immutable deterministic chunking result with its algorithm version. */
public record ChunkedDocument(String chunkingVersion, List<DocumentChunk> chunks) {

    public ChunkedDocument {
        chunkingVersion = chunkingVersion == null || chunkingVersion.isBlank()
                ? StructureAwareDocumentChunker.VERSION : chunkingVersion;
        chunks = chunks == null ? List.of() : List.copyOf(new ArrayList<>(chunks));
    }

    public boolean hasChunks() {
        return !chunks.isEmpty();
    }
}
