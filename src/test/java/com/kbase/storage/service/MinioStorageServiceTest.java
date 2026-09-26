package com.kbase.storage.service;

import java.util.List;

import com.kbase.config.properties.StorageProperties;
import com.kbase.storage.exception.StorageDeleteException;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.exception.StorageUnavailableException;
import com.kbase.storage.exception.StorageUploadException;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.ErrorResponse;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MinioStorageServiceTest {

    private static final String SECRET_STORAGE_KEY_SENTINEL = "SECRET_STORAGE_KEY_SENTINEL";
    private static final String PRIVATE_BUCKET_SENTINEL = "PRIVATE_BUCKET_SENTINEL";
    private static final String PROVIDER_BODY_SENTINEL = "PROVIDER_BODY_SENTINEL";

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
    void mapsMissingObjectWithoutRetainingSdkCause() throws Exception {
        when(minioClient.getObject(any())).thenThrow(noSuchKey());

        assertThatThrownBy(() -> service.get("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .hasMessage("Stored object was not found.")
                .hasNoCause();
    }

    @Test
    void mapsUnavailableStorageWithoutRetainingSdkCause() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new ServerException("unavailable", 503, "request"));

        assertThatThrownBy(() -> service.get("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Storage service is unavailable.")
                .hasNoCause();
    }

    @Test
    void serverErrorResponseUploadMapsToUnavailable() throws Exception {
        when(minioClient.putObject(any()))
                .thenThrow(errorResponse(503, "SlowDown", "reduce your request rate"));

        assertThatThrownBy(() -> service.upload(uploadRequest()))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Storage service is unavailable.")
                .hasNoCause();
    }

    @Test
    void serverErrorResponseDeleteMapsToUnavailable() throws Exception {
        org.mockito.Mockito.doThrow(errorResponse(503, "InternalError", "We encountered an internal error."))
                .when(minioClient).removeObject(any());

        assertThatThrownBy(() -> service.delete("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Storage service is unavailable.")
                .hasNoCause();
    }

    @Test
    void clientErrorResponseUploadStaysGenericOperationFailure() throws Exception {
        when(minioClient.putObject(any()))
                .thenThrow(errorResponse(400, "InvalidArgument", "bad argument"));

        assertThatThrownBy(() -> service.upload(uploadRequest()))
                .isInstanceOf(StorageUploadException.class)
                .hasMessage("Storage upload failed.")
                .hasNoCause();
    }

    @Test
    void clientErrorResponseDeleteStaysGenericOperationFailure() throws Exception {
        org.mockito.Mockito.doThrow(errorResponse(400, "InvalidArgument", "bad argument"))
                .when(minioClient).removeObject(any());

        assertThatThrownBy(() -> service.delete("projects/a/documents/b.pdf"))
                .isInstanceOf(StorageDeleteException.class)
                .hasMessage("Storage delete failed.")
                .hasNoCause();
    }

    @Test
    void providerFailureLogExcludesSentinels(CapturedOutput output) throws Exception {
        when(minioClient.putObject(any())).thenThrow(providerExceptionWithSentinels());

        assertThatThrownBy(() -> service.upload(uploadRequest()))
                .isInstanceOf(StorageUnavailableException.class);

        assertThat(output.getAll()).doesNotContain(
                SECRET_STORAGE_KEY_SENTINEL,
                PRIVATE_BUCKET_SENTINEL,
                PROVIDER_BODY_SENTINEL);
        assertThat(output.getAll()).contains("io.minio.errors.ErrorResponseException");
    }

    @Test
    void consumesEveryBatchDeleteResultAndReportsPartialFailureWithoutSdkCauses() {
        Result<?> success = new Result<>((io.minio.messages.DeleteResult.Error) null);
        Result<?> failure = new Result<>(new ServerException("delete failed " + PROVIDER_BODY_SENTINEL,
                500, "request"));
        @SuppressWarnings("unchecked")
        Iterable<Result<io.minio.messages.DeleteResult.Error>> results = (Iterable) List.of(success, failure);
        when(minioClient.removeObjects(any())).thenReturn(results);

        assertThatThrownBy(() -> service.deleteAll(List.of("first", "second")))
                .isInstanceOf(StorageDeleteException.class)
                .hasMessage("One or more storage objects could not be deleted.")
                .hasNoCause();
        verify(minioClient).removeObjects(any());
    }

    private static com.kbase.storage.model.StorageUploadRequest uploadRequest() {
        return new com.kbase.storage.model.StorageUploadRequest(
                "projects/a/documents/b.pdf",
                new java.io.ByteArrayInputStream(new byte[] {1}),
                1L, "text/plain");
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

    private static ErrorResponseException errorResponse(int httpStatus, String code, String message) {
        ErrorResponse error = new ErrorResponse(
                code, message, PRIVATE_BUCKET_SENTINEL, "object", "/object", "request", "host");
        Request request = new Request.Builder().url("http://storage.invalid/object").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(httpStatus)
                .message("status")
                .build();
        return new ErrorResponseException(error, response, "request");
    }

    private static Exception providerExceptionWithSentinels() {
        ErrorResponse error = new ErrorResponse(
                "InternalError", PROVIDER_BODY_SENTINEL, PRIVATE_BUCKET_SENTINEL,
                SECRET_STORAGE_KEY_SENTINEL, "/" + SECRET_STORAGE_KEY_SENTINEL, "request", "host");
        Request request = new Request.Builder()
                .url("http://storage.invalid/" + SECRET_STORAGE_KEY_SENTINEL).build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message(PROVIDER_BODY_SENTINEL)
                .build();
        return new ErrorResponseException(error, response, "request");
    }
}
