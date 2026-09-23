package com.kbase.ai.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Objects;

import com.kbase.ai.extraction.DocumentSourceReadException;

/** Counts and hashes a storage stream while enforcing a hard byte ceiling. */
public final class BoundedDigestInputStream extends FilterInputStream {

    private final MessageDigest digest;
    private final long maxBytes;
    private long count;

    public BoundedDigestInputStream(InputStream input, MessageDigest digest, long maxBytes) {
        super(Objects.requireNonNull(input, "input"));
        this.digest = Objects.requireNonNull(digest, "digest");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value;
        try {
            value = in.read();
        } catch (IOException exception) {
            throw new DocumentSourceReadException(exception);
        }
        if (value < 0) {
            return value;
        }
        accept(new byte[] {(byte) value}, 0, 1);
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        int read;
        try {
            read = in.read(buffer, offset, length);
        } catch (IOException exception) {
            throw new DocumentSourceReadException(exception);
        }
        if (read > 0) {
            accept(buffer, offset, read);
        }
        return read;
    }

    public long count() {
        return count;
    }

    public byte[] digest() {
        return digest.digest();
    }

    public void drain() throws IOException {
        byte[] buffer = new byte[8192];
        while (read(buffer, 0, buffer.length) >= 0) {
            // read() updates count/digest; loop exits only at EOF.
        }
    }

    private void accept(byte[] buffer, int offset, int length) {
        if (count > maxBytes - length) {
            throw new DocumentTooLargeException();
        }
        digest.update(buffer, offset, length);
        count += length;
    }
}
