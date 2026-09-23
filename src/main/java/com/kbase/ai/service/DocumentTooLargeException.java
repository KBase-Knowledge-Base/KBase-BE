package com.kbase.ai.service;

/** Safe internal signal for a source that exceeds the configured extraction bound. */
public final class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException() {
        super("Document exceeds the extraction size limit.");
    }
}
