package com.kbase.category.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public project category representation. */
public record CategoryResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
