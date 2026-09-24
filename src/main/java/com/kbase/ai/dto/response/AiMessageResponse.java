package com.kbase.ai.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.entity.AiMessage;
import com.kbase.ai.enums.AiAnswerType;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.AiMessageRole;

public record AiMessageResponse(UUID id, AiMessageRole role, String content,
        AiGenerationStatus generationStatus, AiAnswerType answerType, String failureCode,
        Instant createdAt, Instant completedAt) {

    public static AiMessageResponse from(AiMessage message) {
        return new AiMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getGenerationStatus(), message.getAnswerType(), message.getFailureCode(),
                message.getCreatedAt(), message.getCompletedAt());
    }
}
