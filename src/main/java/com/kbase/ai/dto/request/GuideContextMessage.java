package com.kbase.ai.dto.request;

import com.kbase.ai.provider.model.AiChatRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Client-supplied, non-authoritative Guide context. SYSTEM is not representable. */
public record GuideContextMessage(@NotNull AiChatRole role,
        @NotBlank @Size(max = 4000) String content) {
}
