package com.kbase.storage.service;

import java.util.Collection;

import com.kbase.storage.model.ObjectMetadata;
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.model.StoredObject;
import com.kbase.storage.model.StoredResource;

/**
 * Vendor-neutral binary-object port. It deliberately owns no authorization,
 * project membership, document metadata, or HTTP behavior.
 */
public interface StorageService {

    StoredObject upload(StorageUploadRequest request);

    StoredResource get(String storageKey);

    StoredResource getRange(String storageKey, long offset, long length);

    ObjectMetadata stat(String storageKey);

    void delete(String storageKey);

    void deleteAll(Collection<String> storageKeys);
}
