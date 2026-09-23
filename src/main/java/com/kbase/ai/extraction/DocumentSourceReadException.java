package com.kbase.ai.extraction;

/** Safe signal that the binary stream failed while an extractor was reading it. */
public final class DocumentSourceReadException extends RuntimeException {

    public DocumentSourceReadException(Throwable cause) {
        super("Document source could not be read.", cause);
    }
}
