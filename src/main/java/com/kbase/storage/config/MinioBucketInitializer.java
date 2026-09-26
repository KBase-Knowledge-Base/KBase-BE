package com.kbase.storage.config;

import java.util.Objects;

import com.kbase.config.properties.StorageProperties;
import com.kbase.storage.exception.StorageUnavailableException;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Validates the configured bucket and may create it for local development only. */
@Component
@ConditionalOnProperty(
        prefix = "kbase.storage",
        name = "initialize-on-startup",
        havingValue = "true")
public class MinioBucketInitializer implements ApplicationRunner {

    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioBucketInitializer(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    /** Exposed for container-backed verification without starting an application context. */
    public void initialize() {
        try {
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
                return;
            }
            if (!properties.isAutoCreateBucket()) {
                throw new StorageUnavailableException("Configured storage bucket is unavailable.", null);
            }
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .region(properties.getRegion())
                    .build());
        } catch (StorageUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            // Startup-failure logging prints the whole cause chain; discard the
            // raw provider cause so its bucket/endpoint details stay out of logs.
            throw new StorageUnavailableException(
                    "Configured storage bucket is unavailable. (" + exception.getClass().getSimpleName() + ")",
                    null);
        }
    }
}
