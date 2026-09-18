package com.kbase.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename request for a project category. */
public record UpdateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must contain at most 100 characters")
        String name) {
}
