package com.kbase.ai.repository;

import java.util.UUID;

/** Internal JDBC write shape for a document vector chunk; not a REST DTO. */
public record DocumentAiChunkInsert(
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
        float[] embedding) {
}
