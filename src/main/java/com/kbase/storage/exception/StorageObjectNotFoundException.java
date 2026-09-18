package com.kbase.storage.exception;

/** The requested binary is absent even though higher-level metadata may exist. */
public final class StorageObjectNotFoundException extends StorageException {

    public StorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
