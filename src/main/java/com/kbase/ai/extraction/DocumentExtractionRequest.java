package com.kbase.ai.extraction;

import java.util.Locale;
import java.util.Objects;

/** Parser-neutral descriptor passed to an extraction adapter. */
public record DocumentExtractionRequest(
        String extension,
        String resourceName,
        String contentType,
        long declaredSizeBytes) {

    public DocumentExtractionRequest {
        extension = normalizeExtension(extension);
        resourceName = resourceName == null || resourceName.isBlank() ? extension : resourceName;
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType;
        if (declaredSizeBytes < 0) {
            throw new IllegalArgumentException("declaredSizeBytes must not be negative");
        }
    }

    private static String normalizeExtension(String value) {
        Objects.requireNonNull(value, "extension");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("extension must not be blank");
        }
        return normalized;
    }
}
