package com.kbase.document.service;

import java.time.Instant;
import java.util.UUID;

import com.kbase.document.enums.FileKind;

/**
 * Internal, metadata-only filters for the project document browser. The
 * project boundary is deliberately not a filter here: it is a mandatory
 * argument of {@link DocumentSearchService#search}.
 */
public record DocumentSearchCriteria(
        String q,
        UUID folderId,
        UUID categoryId,
        UUID tagId,
        FileKind fileKind,
        UUID uploadedBy,
        Instant createdFrom,
        Instant createdTo) {
}
