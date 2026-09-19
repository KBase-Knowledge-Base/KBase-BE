package com.kbase.document.dto.request;

import java.util.List;
import java.util.UUID;

/** Optional common metadata supplied with an upload. */
public record DocumentMetadataRequest(
        String displayName,
        String description,
        UUID folderId,
        UUID categoryId,
        List<UUID> tagIds) {
}
