package com.kbase.ai.extraction;

import java.io.InputStream;

/** KBase-owned extraction port. The caller owns and closes the supplied stream. */
public interface DocumentContentExtractor {

    ExtractedDocument extract(InputStream input, DocumentExtractionRequest request);
}
