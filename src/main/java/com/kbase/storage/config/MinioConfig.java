package com.kbase.storage.config;

import java.time.Duration;

import com.kbase.config.properties.StorageProperties;

import io.minio.MinioClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures one reusable MinIO client from environment-backed settings. */
@Configuration(proxyBeanMethods = false)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        requireConfigured(properties.getEndpoint());
        requireConfigured(properties.getAccessKey());
        requireConfigured(properties.getSecretKey());
        requireConfigured(properties.getBucket());

        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
        client.setTimeout(
                timeoutMillis(properties.getConnectTimeout(), Duration.ofSeconds(5)),
                timeoutMillis(properties.getWriteTimeout(), Duration.ofSeconds(30)),
                timeoutMillis(properties.getReadTimeout(), Duration.ofSeconds(30)));
        return client;
    }

    private static void requireConfigured(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Storage configuration is incomplete.");
        }
    }

    private static long timeoutMillis(Duration value, Duration fallback) {
        return Math.max(1L, (value == null ? fallback : value).toMillis());
    }
}
