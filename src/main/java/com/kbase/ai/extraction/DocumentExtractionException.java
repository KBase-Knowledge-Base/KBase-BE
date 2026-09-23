package com.kbase.ai.extraction;

/** Safe terminal extraction failure; parser details are intentionally not retained. */
public final class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException() {
        super("Document extraction failed.");
    }

    public DocumentExtractionException(Throwable cause) {
        super("Document extraction failed.", cause);
    }
}
