package com.kbase.storage.service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Creates backend-controlled keys without user filenames or organization metadata. */
@Component
public class StorageKeyFactory {

    public String documentObjectKey(UUID projectId, UUID documentId, String validatedExtension) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(documentId, "documentId");
        if (validatedExtension == null || validatedExtension.isBlank()) {
            throw new IllegalArgumentException("Storage extension is required.");
        }
        String extension = validatedExtension.trim().toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("Storage extension is invalid.");
        }
        return "projects/%s/documents/%s.%s".formatted(projectId, documentId, extension);
    }
}
