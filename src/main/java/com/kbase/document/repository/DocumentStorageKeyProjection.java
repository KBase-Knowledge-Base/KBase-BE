package com.kbase.document.repository;

import java.util.UUID;

/** Minimal projection used before a project hard-delete storage cleanup. */
public interface DocumentStorageKeyProjection {

    UUID getId();

    String getStorageKey();
}
