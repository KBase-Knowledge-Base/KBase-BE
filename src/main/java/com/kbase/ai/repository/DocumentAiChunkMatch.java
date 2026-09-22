package com.kbase.ai.repository;

import java.util.UUID;

/** Read shape returned by project-scoped active document vector retrieval. */
public record DocumentAiChunkMatch(
        UUID id,
        UUID projectId,
        UUID documentId,
        long indexVersion,
        int chunkIndex,
        String content,
        Integer pageNumber,
        Integer slideNumber,
        String sectionTitle,
        Integer tokenCount,
        String contentHash,
        double similarity) {
}
