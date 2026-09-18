package com.kbase.storage.model;

import java.time.Instant;

/** Vendor-neutral object metadata returned by {@code StorageService.stat}. */
public record ObjectMetadata(
        String storageKey,
        long sizeBytes,
        String contentType,
        String etag,
        Instant lastModified) {
}
