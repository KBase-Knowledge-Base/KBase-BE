package com.kbase.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RenameAiConversationRequest(@NotBlank String title) {
}
