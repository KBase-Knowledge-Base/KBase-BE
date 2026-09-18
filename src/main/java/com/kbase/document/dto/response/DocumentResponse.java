package com.kbase.document.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.document.enums.FileKind;

/** Public document metadata. Storage keys intentionally never appear here. */
public record DocumentResponse(
        UUID id, UUID projectId, DocumentUserResponse uploadedBy, UUID folderId,
        DocumentCategoryResponse category, List<DocumentTagResponse> tags,
        String displayName, String originalFilename, FileKind fileKind,
        String extension, String mimeType, long sizeBytes, String description,
        Instant createdAt, Instant updatedAt) {
}
