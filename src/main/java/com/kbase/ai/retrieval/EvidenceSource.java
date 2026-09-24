package com.kbase.ai.retrieval;

import java.util.UUID;

/** Backend-owned identity and citation metadata for one selected chunk. */
public record EvidenceSource(UUID chunkId, UUID projectId, UUID documentId,
        String documentName, long indexVersion, int chunkIndex, int rank,
        double similarity, Integer pageNumber, Integer slideNumber, String sectionTitle) {
}
