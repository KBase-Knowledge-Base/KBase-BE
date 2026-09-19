package com.kbase.document.dto.request;

import java.util.List;
import java.util.UUID;

/** Partial metadata update; null tagIds means keep existing tags. */
public record UpdateDocumentRequest(
        String displayName,
        String description,
        UUID folderId,
        UUID categoryId,
        List<UUID> tagIds) {
}
