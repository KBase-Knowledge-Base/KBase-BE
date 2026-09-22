package com.kbase.ai.repository;

import java.util.UUID;

/** Internal JDBC write shape for a Guide vector chunk; not a REST DTO. */
public record GuideChunkInsert(
        UUID id,
        UUID guideSourceId,
        long indexVersion,
        int chunkIndex,
        String content,
        String headingPath,
        Integer tokenCount,
        String contentHash,
        float[] embedding) {
}
