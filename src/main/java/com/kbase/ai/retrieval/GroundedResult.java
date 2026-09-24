package com.kbase.ai.retrieval;

import java.util.List;

import com.kbase.ai.enums.AiAnswerType;

/** Internal result for M7 to persist; no REST or provider-specific shape. */
public record GroundedResult(AiAnswerType answerType, String text, String modelId,
        List<EvidenceSource> sources) {
    public GroundedResult {
        sources = List.copyOf(sources);
        if (answerType == AiAnswerType.GROUNDED && sources.isEmpty()) {
            throw new IllegalArgumentException("grounded result requires a source");
        }
        if (answerType == AiAnswerType.NO_EVIDENCE && !sources.isEmpty()) {
            throw new IllegalArgumentException("no-evidence result cannot have sources");
        }
    }
}
