package com.kbase.ai.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.entity.AiConversation;

public record AiConversationResponse(UUID id, UUID projectId, String title,
        Instant createdAt, Instant updatedAt) {

    public static AiConversationResponse from(AiConversation conversation) {
        return new AiConversationResponse(conversation.getId(), conversation.getProjectId(),
                conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }
}
