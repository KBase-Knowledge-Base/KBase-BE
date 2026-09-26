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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** MinIO-only implementation of the vendor-neutral {@link StorageService} port. */
@Service
public class MinioStorageService implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioStorageService.class);
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
        List<String> rejectedFailureTypes = new ArrayList<>();
        try {
            Iterable<Result<DeleteResult.Error>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(properties.getBucket()).objects(objects).build());
            for (Result<DeleteResult.Error> result : results) {
                try {
                    DeleteResult.Error error = result.get();
                    if (error != null) {
                        rejectedFailureTypes.add("batch-delete-rejection");
                    }
                } catch (Exception exception) {
                    rejectedFailureTypes.add(safeExceptionType(exception));
                }
            }
        } catch (Exception exception) {
            throw deleteFailure(exception);
        }
        if (!rejectedFailureTypes.isEmpty()) {
            // Per-object batch errors expose only S3 error codes, never an HTTP
            // status, so they stay generic delete failures by contract.
            LOGGER.error("Storage batch delete rejected objects count={} failureTypes={}",
                    rejectedFailureTypes.size(), rejectedFailureTypes);
            throw new StorageDeleteException("One or more storage objects could not be deleted.", null);
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

    /**
     * Classifies and sanitizes a raw provider failure. The MinIO SDK exception
     * message carries the bucket/object path, so — mirroring the SMTP
     * provider boundary — only the safe exception TYPE is logged and the raw
     * cause is never retained on the thrown {@link StorageException}.
     */
    private StorageException uploadFailure(Exception exception) {
        if (isUnavailable(exception)) {
            return sanitizedFailure(true, "upload", exception);
        }
        return sanitizedFailure(false, "upload", exception);
    }

    private StorageException readFailure(Exception exception) {
        if (isNotFound(exception)) {
            return sanitizedNotFound(exception);
        }
        return sanitizedFailure(true, "read", exception);
    }

    private StorageException deleteFailure(Exception exception) {
        if (isUnavailable(exception)) {
            return sanitizedFailure(true, "delete", exception);
        }
        return sanitizedFailure(false, "delete", exception);
    }

    private static StorageException sanitizedFailure(boolean unavailable, String operation,
            Exception exception) {
        LOGGER.error("Storage {} failed via provider type={} unavailable={}",
                operation, safeExceptionType(exception), unavailable);
        return unavailable
                ? new StorageUnavailableException("Storage service is unavailable.", null)
                : operation.equals("upload")
                        ? new StorageUploadException("Storage upload failed.", null)
                        : new StorageDeleteException("Storage delete failed.", null);

    }

    private static StorageException sanitizedNotFound(Exception exception) {
        LOGGER.error("Storage read failed via provider type={} notFound=true",
                safeExceptionType(exception));
        return new StorageObjectNotFoundException("Stored object was not found.", null);
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
        if (exception instanceof ErrorResponseException errorResponseException
                && errorResponseException.response() != null) {
            // A real S3/MinIO error response carries the factual HTTP status;
            // only 5xx is an availability failure. Client errors (4xx) stay
            // generic operation failures.
            return errorResponseException.response().code() >= 500;
        }
        return exception instanceof ServerException
                || exception instanceof java.io.IOException
                || exception instanceof java.net.ConnectException
                || exception instanceof java.net.SocketTimeoutException;
    }

    private static String safeExceptionType(Throwable exception) {
        return exception == null ? "unknown" : exception.getClass().getName();
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
