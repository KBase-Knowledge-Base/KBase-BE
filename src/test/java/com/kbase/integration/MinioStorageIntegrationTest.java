package com.kbase.integration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.kbase.config.properties.StorageProperties;
import com.kbase.storage.config.MinioBucketInitializer;
import com.kbase.storage.config.MinioConfig;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.model.StoredResource;
import com.kbase.storage.service.MinioStorageService;
import com.kbase.storage.service.StorageKeyFactory;

import io.minio.GetBucketVersioningArgs;
import io.minio.MinioClient;
import io.minio.messages.VersioningConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real S3-compatible verification of the M10 storage adapter. */
@Testcontainers
class MinioStorageIntegrationTest {

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "kbase-storage-it";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("quay.io/minio/minio:latest")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000);

    private final StorageKeyFactory keyFactory = new StorageKeyFactory();

    private MinioClient client;
    private MinioStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000)));
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setBucket(BUCKET);
        properties.setRegion("us-east-1");
        properties.setAutoCreateBucket(true);
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setWriteTimeout(Duration.ofSeconds(10));
        client = new MinioConfig().minioClient(properties);
        new MinioBucketInitializer(client, properties).initialize();
        storage = new MinioStorageService(client, properties);
    }

    @Test
    void initializesUnversionedBucketAndStreamsFullAndRangedReads() throws Exception {
        String key = key("txt");
        byte[] payload = "0123456789".getBytes(StandardCharsets.UTF_8);

        storage.upload(new StorageUploadRequest(
                key, new ByteArrayInputStream(payload), payload.length, "text/plain"));

        assertThat(client.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(BUCKET).build()).status())
                .isNotEqualTo(VersioningConfiguration.Status.ENABLED);
        assertThat(storage.stat(key))
                .extracting(metadata -> metadata.sizeBytes(), metadata -> metadata.contentType())
                .containsExactly(10L, "text/plain");
        assertThat(read(storage.get(key))).isEqualTo("0123456789");
        assertThat(read(storage.getRange(key, 3, 4))).isEqualTo("3456");
    }

    @Test
    void deletesSingleAndBatchObjectsWithoutDeletingAnythingElse() {
        String single = key("txt");
        String first = key("pdf");
        String second = key("mp4");
        upload(single, "single");
        upload(first, "first");
        upload(second, "second");

        storage.delete(single);
        assertThatThrownBy(() -> storage.stat(single)).isInstanceOf(StorageObjectNotFoundException.class);

        storage.deleteAll(List.of(first, second));
        assertThatThrownBy(() -> storage.stat(first)).isInstanceOf(StorageObjectNotFoundException.class);
        assertThatThrownBy(() -> storage.stat(second)).isInstanceOf(StorageObjectNotFoundException.class);
    }

    private String key(String extension) {
        return keyFactory.documentObjectKey(UUID.randomUUID(), UUID.randomUUID(), extension);
    }

    private void upload(String key, String value) {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        storage.upload(new StorageUploadRequest(
                key, new ByteArrayInputStream(payload), payload.length, "application/octet-stream"));
    }

    private static String read(StoredResource resource) throws Exception {
        try (var inputStream = resource.inputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
