package com.kbase.tag.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public project tag representation. */
public record TagResponse(
        UUID id,
        String name,
        Instant createdAt) {
}
