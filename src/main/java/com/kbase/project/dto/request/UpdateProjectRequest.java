package com.kbase.project.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial project update. Fields left null are unchanged; a present-but-blank
 * name is rejected by the service as a validation error.
 */
public record UpdateProjectRequest(

        @Size(max = 150, message = "Project name must contain at most 150 characters")
        String name,

        @Size(max = 2000, message = "Description must contain at most 2000 characters")
        String description) {
}
