package com.kbase.ai.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.service.DocumentAiIndexStatusView;

public record DocumentAiIndexResponse(UUID documentId, DocumentAiIndexStatus status,
        String failureReason, Instant indexedAt, boolean retryAllowed) {

    public static DocumentAiIndexResponse from(DocumentAiIndexStatusView view) {
        return new DocumentAiIndexResponse(view.documentId(), view.status(),
                view.failureReason(), view.indexedAt(),
                view.status() == DocumentAiIndexStatus.FAILED);
    }
}
