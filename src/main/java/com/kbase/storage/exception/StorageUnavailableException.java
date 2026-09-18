package com.kbase.storage.exception;

/** Endpoint, bucket, timeout, or service availability failure. */
public final class StorageUnavailableException extends StorageException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
