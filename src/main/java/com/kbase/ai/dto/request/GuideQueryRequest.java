package com.kbase.ai.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuideQueryRequest(@NotBlank @Size(max = 8000) String message,
        @Valid @Size(max = 8) List<GuideContextMessage> context) {
    public GuideQueryRequest { context = context == null ? List.of() : List.copyOf(context); }
}
