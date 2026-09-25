package com.kbase.ai.guide;

/** Deterministic Guide chunk with its public Markdown heading path. */
public record GuideChunk(int chunkIndex, String content, String headingPath, int tokenCount,
        String contentHash) {
}
