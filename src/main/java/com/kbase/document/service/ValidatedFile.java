package com.kbase.document.service;

import com.kbase.document.enums.FileKind;

/** Safe, normalized upload facts derived before any storage operation. */
public record ValidatedFile(String originalFilename, String extension, String mimeType,
        FileKind fileKind, long sizeBytes) {
}
