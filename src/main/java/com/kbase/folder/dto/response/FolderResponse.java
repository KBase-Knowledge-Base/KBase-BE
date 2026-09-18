package com.kbase.folder.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public project-scoped folder representation. */
public record FolderResponse(
        UUID id,
        UUID projectId,
        UUID parentId,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
