package com.kbase.storage.exception;

/** Single or batch object deletion failure. */
public final class StorageDeleteException extends StorageException {

    public StorageDeleteException(String message, Throwable cause) {
        super(message, cause);
    }
}
