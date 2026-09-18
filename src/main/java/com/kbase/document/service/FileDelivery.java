package com.kbase.document.service;

import java.io.InputStream;

/** Service-level streaming result; controller owns closing its input stream. */
public record FileDelivery(InputStream inputStream, long contentLength, long totalLength,
        String mimeType, String filename, boolean partial, long rangeStart, long rangeEnd) {
}
