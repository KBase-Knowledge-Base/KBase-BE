package com.kbase.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendAiMessageRequest(@NotBlank String message) {
}
