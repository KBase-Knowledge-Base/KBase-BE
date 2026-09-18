package com.kbase.storage.exception;

/** Non-availability failure while streaming an object to storage. */
public final class StorageUploadException extends StorageException {

    public StorageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
