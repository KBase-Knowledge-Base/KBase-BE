package com.kbase.ai.repository;

import java.util.UUID;

/** Read shape returned by active Guide vector retrieval. */
public record GuideChunkMatch(
        UUID id,
        UUID guideSourceId,
        long indexVersion,
        int chunkIndex,
        String content,
        String headingPath,
        Integer tokenCount,
        String contentHash,
        double similarity) {
}
