package com.kbase.document.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.document.enums.FileKind;

/** Reserved for M12 search/listing; M11 does not expose a list endpoint. */
public record DocumentSummaryResponse(UUID id, String displayName, String originalFilename,
        FileKind fileKind, String extension, String mimeType, long sizeBytes,
        UUID folderId, Instant createdAt, Instant updatedAt) {
}
