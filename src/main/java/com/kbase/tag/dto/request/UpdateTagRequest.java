package com.kbase.tag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename request for a project tag. */
public record UpdateTagRequest(
        @NotBlank(message = "Tag name is required")
        @Size(max = 50, message = "Tag name must contain at most 50 characters")
        String name) {
}
