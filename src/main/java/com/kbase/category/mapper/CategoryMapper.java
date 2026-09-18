package com.kbase.category.mapper;

import java.util.Objects;

import com.kbase.category.dto.response.CategoryResponse;
import com.kbase.category.entity.Category;

import org.springframework.stereotype.Component;

/** Maps a category entity to its public REST representation. */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        Objects.requireNonNull(category, "category");
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
