package com.kbase.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for a project category. */
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must contain at most 100 characters")
        String name) {
}
