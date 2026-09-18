package com.kbase.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Project creation request. The creator becomes the single OWNER. */
public record CreateProjectRequest(

        @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must contain at most 150 characters")
        String name,

        @Size(max = 2000, message = "Description must contain at most 2000 characters")
        String description) {
}
