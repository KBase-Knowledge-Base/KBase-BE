package com.kbase.storage.model;

import java.io.InputStream;
import java.util.Objects;

/** Streaming upload input. Caller owns and closes the supplied stream. */
public record StorageUploadRequest(
        String storageKey,
        InputStream inputStream,
        long sizeBytes,
        String contentType) {

    public StorageUploadRequest {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required.");
        }
        Objects.requireNonNull(inputStream, "inputStream");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Storage size must not be negative.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Storage content type is required.");
        }
    }
}
