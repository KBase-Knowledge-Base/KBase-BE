package com.kbase.tag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for a project tag. MEMBER may create tags. */
public record CreateTagRequest(
        @NotBlank(message = "Tag name is required")
        @Size(max = 50, message = "Tag name must contain at most 50 characters")
        String name) {
}
