package com.kbase.ai.dto.response;

import java.util.List;

public record AiTurnResponse(AiMessageResponse message, List<AiSourceResponse> sources) {
    public AiTurnResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
