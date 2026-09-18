package com.kbase.storage.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.kbase.config.properties.StorageProperties;
import com.kbase.storage.exception.StorageDeleteException;
import com.kbase.storage.exception.StorageException;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.exception.StorageUnavailableException;
import com.kbase.storage.exception.StorageUploadException;
import com.kbase.storage.model.ObjectMetadata;
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.model.StoredObject;
import com.kbase.storage.model.StoredResource;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.errors.ServerException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;

import org.springframework.stereotype.Service;

/** MinIO-only implementation of the vendor-neutral {@link StorageService} port. */
@Service
public class MinioStorageService implements StorageService {

    private static final Set<String> NOT_FOUND_CODES = Set.of("NOSUCHKEY", "NOSUCHOBJECT");

    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioStorageService(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public StoredObject upload(StorageUploadRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            ObjectWriteResponse response = minioClient.putObject(io.minio.PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(request.storageKey())
                    .stream(request.inputStream(), request.sizeBytes(), -1L)
                    .contentType(request.contentType())
                    .build());
            return new StoredObject(request.storageKey(), response.etag(), toInstant(response.lastModified()));
        } catch (Exception exception) {
            throw uploadFailure(exception);
        }
    }

    @Override
    public StoredResource get(String storageKey) {
        return read(storageKey, null, null);
    }

    @Override
    public StoredResource getRange(String storageKey, long offset, long length) {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Range offset and length are invalid.");
        }
        return read(storageKey, offset, length);
    }

    @Override
    public ObjectMetadata stat(String storageKey) {
        String key = requireKey(storageKey);
        try {
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            return new ObjectMetadata(key, response.size(), response.contentType(), response.etag(),
                    toInstant(response.lastModified()));
        } catch (Exception exception) {
            throw readFailure(exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        String key = requireKey(storageKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception exception) {
            throw deleteFailure(exception);
        }
    }

    @Override
    public void deleteAll(Collection<String> storageKeys) {
        Objects.requireNonNull(storageKeys, "storageKeys");
        if (storageKeys.isEmpty()) {
            return;
        }
        List<DeleteRequest.Object> objects = storageKeys.stream()
                .map(MinioStorageService::requireKey)
                .map(DeleteRequest.Object::new)
                .toList();
        List<Throwable> failures = new ArrayList<>();
        try {
            Iterable<Result<DeleteResult.Error>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(properties.getBucket()).objects(objects).build());
            for (Result<DeleteResult.Error> result : results) {
                try {
                    DeleteResult.Error error = result.get();
                    if (error != null) {
                        failures.add(new IllegalStateException("Storage batch delete rejected an object."));
                    }
                } catch (Exception exception) {
                    failures.add(exception);
                }
            }
        } catch (Exception exception) {
            throw deleteFailure(exception);
        }
        if (!failures.isEmpty()) {
            StorageDeleteException exception = new StorageDeleteException(
                    "One or more storage objects could not be deleted.", failures.getFirst());
            failures.stream().skip(1).forEach(exception::addSuppressed);
            throw exception;
        }
    }

    private StoredResource read(String storageKey, Long offset, Long length) {
        String key = requireKey(storageKey);
        GetObjectResponse response = null;
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key);
            if (offset != null) {
                builder.offset(offset).length(length);
            }
            response = minioClient.getObject(builder.build());
            return new StoredResource(response, contentLength(response), contentType(response));
        } catch (Exception exception) {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw readFailure(exception);
        }
    }

    private StorageException uploadFailure(Exception exception) {
        if (isUnavailable(exception)) {
            return new StorageUnavailableException("Storage service is unavailable.", exception);
        }
        return new StorageUploadException("Storage upload failed.", exception);
    }

    private StorageException readFailure(Exception exception) {
        if (isNotFound(exception)) {
            return new StorageObjectNotFoundException("Stored object was not found.", exception);
        }
        return new StorageUnavailableException("Storage service is unavailable.", exception);
    }

    private StorageException deleteFailure(Exception exception) {
        if (isUnavailable(exception)) {
            return new StorageUnavailableException("Storage service is unavailable.", exception);
        }
        return new StorageDeleteException("Storage delete failed.", exception);
    }

    private static boolean isNotFound(Exception exception) {
        if (exception instanceof ErrorResponseException errorResponseException) {
            String code = errorResponseException.errorResponse() == null
                    ? null : errorResponseException.errorResponse().code();
            return code != null && NOT_FOUND_CODES.contains(code.toUpperCase(java.util.Locale.ROOT));
        }
        return false;
    }

    private static boolean isUnavailable(Exception exception) {
        return exception instanceof ServerException
                || exception instanceof java.io.IOException
                || exception instanceof java.net.ConnectException
                || exception instanceof java.net.SocketTimeoutException;
    }

    private static String requireKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required.");
        }
        return storageKey;
    }

    private static long contentLength(GetObjectResponse response) {
        String value = response.headers().get("Content-Length");
        if (value == null) {
            throw new StorageUnavailableException("Storage response has no content length.", null);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new StorageUnavailableException("Storage response has invalid content length.", exception);
        }
    }

    private static String contentType(GetObjectResponse response) {
        String value = response.headers().get("Content-Type");
        return value == null || value.isBlank() ? "application/octet-stream" : value;
    }

    private static Instant toInstant(java.time.ZonedDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
