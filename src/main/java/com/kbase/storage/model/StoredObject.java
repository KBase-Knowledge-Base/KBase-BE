package com.kbase.storage.model;

import java.time.Instant;

/** Safe storage-write result with no vendor SDK type. */
public record StoredObject(String storageKey, String etag, Instant lastModified) {
}
