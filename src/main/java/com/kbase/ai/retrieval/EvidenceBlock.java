package com.kbase.ai.retrieval;

import java.util.List;

/** One labelled prompt block, possibly backed by multiple real chunks. */
public record EvidenceBlock(String label, String content, List<EvidenceSource> sources) {
    public EvidenceBlock {
        sources = List.copyOf(sources);
    }
}
