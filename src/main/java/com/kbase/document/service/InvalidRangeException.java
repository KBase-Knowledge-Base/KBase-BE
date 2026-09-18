package com.kbase.document.service;

/** Transport-safe range failure; the controller adds the required Content-Range header. */
public final class InvalidRangeException extends RuntimeException {
    private final long totalLength;
    public InvalidRangeException(long totalLength) { this.totalLength = totalLength; }
    public long totalLength() { return totalLength; }
}
