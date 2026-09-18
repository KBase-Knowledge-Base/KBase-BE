package com.kbase.storage.model;

import java.io.InputStream;
import java.util.Objects;

/** Streaming read result. The caller must close {@link #inputStream()}. */
public record StoredResource(InputStream inputStream, long contentLength, String contentType) {

    public StoredResource {
        Objects.requireNonNull(inputStream, "inputStream");
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length must not be negative.");
        }
    }
}
