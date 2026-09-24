package com.kbase.ai.dto.response;

import java.util.List;

public record CreateAiConversationResponse(AiConversationResponse conversation,
        AiMessageResponse message, List<AiSourceResponse> sources) {
    public CreateAiConversationResponse {
        sources = List.copyOf(sources);
    }
}
