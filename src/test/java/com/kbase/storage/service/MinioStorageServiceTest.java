package com.kbase.storage.service;

import java.util.List;

import com.kbase.config.properties.StorageProperties;
import com.kbase.storage.exception.StorageDeleteException;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.exception.StorageUnavailableException;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.ErrorResponse;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioStorageServiceTest {

    private MinioClient minioClient;
    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        StorageProperties properties = new StorageProperties();
        properties.setBucket("test-bucket");
        service = new MinioStorageService(minioClient, properties);
    }

    @Test
    void portContainsNoMinioSdkTypesAndAdapterImplementsIt() {
        assertThat(StorageService.class.isAssignableFrom(MinioStorageService.class)).isTrue();
        for (var method : StorageService.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName()).doesNotStartWith("io.minio.");
            assertThat(method.getParameterTypes())
                    .allSatisfy(type -> assertThat(type.getName()).doesNotStartWith("io.minio."));
        }
    }

    @Test
    void mapsMissingObjectWithoutLeakingSdkException() throws Exception {
        when(minioClient.getObject(any())).thenThrow(noSuchKey());

        assertThatThrownBy(() -> service.get("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .hasMessage("Stored object was not found.")
                .hasCauseInstanceOf(ErrorResponseException.class);
    }

    @Test
    void mapsUnavailableStorageWithoutLeakingSdkException() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new ServerException("unavailable", 503, "request"));

        assertThatThrownBy(() -> service.get("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Storage service is unavailable.")
                .hasCauseInstanceOf(ServerException.class);
    }

    @Test
    void consumesEveryBatchDeleteResultAndReportsPartialFailure() {
        Result<?> success = new Result<>((io.minio.messages.DeleteResult.Error) null);
        Result<?> failure = new Result<>(new ServerException("delete failed", 500, "request"));
        @SuppressWarnings("unchecked")
        Iterable<Result<io.minio.messages.DeleteResult.Error>> results = (Iterable) List.of(success, failure);
        when(minioClient.removeObjects(any())).thenReturn(results);

        assertThatThrownBy(() -> service.deleteAll(List.of("first", "second")))
                .isInstanceOf(StorageDeleteException.class)
                .hasMessage("One or more storage objects could not be deleted.")
                .hasCauseInstanceOf(ServerException.class);
        verify(minioClient).removeObjects(any());
    }

    private static ErrorResponseException noSuchKey() {
        ErrorResponse error = new ErrorResponse(
                "NoSuchKey", "missing", "test-bucket", "object", "/object", "request", "host");
        Request request = new Request.Builder().url("http://storage.invalid/object").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .build();
        return new ErrorResponseException(error, response, "request");
    }
}
