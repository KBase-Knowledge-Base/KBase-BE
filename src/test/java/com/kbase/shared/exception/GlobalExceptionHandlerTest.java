package com.kbase.shared.exception;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String INTERNAL_DETAIL = "SELECT password_hash FROM users; IllegalStateException";

    private MockMvc mockMvc;
    private TestController controller;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ConstraintViolationTranslator()))
                .addFilters(new RequestIdFilter())
                .build();
        MDC.clear();
    }

    @Test
    void businessExceptionUsesItsStableStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("The user is already a project member."))
                .andExpect(jsonPath("$.path").value("/test/errors/business"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void resourceForbiddenAndInfrastructureExceptionsMapToTheirCodes() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
        mockMvc.perform(get("/test/errors/infrastructure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SERVICE_UNAVAILABLE"));
    }

    @Test
    void validationResponseContainsOneSafeMessagePerField() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.errors.email").value("Invalid email address"))
                .andExpect(jsonPath("$.errors.password")
                        .value("Password must contain between 8 and 64 characters"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void malformedJsonIsMappedWithoutJacksonDetails() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message")
                        .value("Request body is malformed or contains invalid values."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void invalidPathParameterIsMappedToInvalidParameter() throws Exception {
        mockMvc.perform(get("/test/errors/uuid/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message")
                        .value("One or more request parameters are invalid."));
    }

    @Test
    void unsupportedMethodAndMediaTypeUseStableTransportCodes() throws Exception {
        mockMvc.perform(post("/test/errors/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<request/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void requestIdIsGeneratedReusedInBodyAndHeaderAndMdcIsCleared() throws Exception {
        var result = mockMvc.perform(get("/test/errors/request-id"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andReturn();

        String responseRequestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(responseRequestId).isNotBlank();
        assertThat(RequestIdFilter.isValidRequestId(responseRequestId)).isTrue();
        assertThat(controller.requestIdSeenByController.get()).isEqualTo(responseRequestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        UUID suppliedRequestId = UUID.randomUUID();
        mockMvc.perform(get("/test/errors/business")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, suppliedRequestId.toString()))
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, suppliedRequestId.toString()))
                .andExpect(jsonPath("$.requestId").value(suppliedRequestId.toString()));

        mockMvc.perform(get("/test/errors/business")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "unsafe request id"))
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void knownDatabaseConstraintUsesStableBusinessCode() throws Exception {
        mockMvc.perform(get("/test/errors/constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasKey("requestId")));
    }

    @Test
    void unknownDatabaseConstraintFallsBackToInternalWithoutTechnicalDetails() throws Exception {
        String body = mockMvc.perform(get("/test/errors/unknown-constraint"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("fk_documents_folder_same_project", "DataIntegrityViolationException");
    }

    @Test
    void multipartLimitUsesFileTooLargeContract() throws Exception {
        mockMvc.perform(post("/test/errors/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void unknownExceptionReturnsGenericResponseWithoutTechnicalDetails() throws Exception {
        String body = mockMvc.perform(get("/test/errors/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(INTERNAL_DETAIL, "IllegalStateException", "password_hash", "SELECT");
        assertThat(body).doesNotContain("GlobalExceptionHandler", "DataIntegrityViolationException");
    }

    @RestController
    @RequestMapping("/test/errors")
    static class TestController {

        private final AtomicReference<String> requestIdSeenByController = new AtomicReference<>();

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenOperationException(ErrorCode.PROJECT_ACCESS_FORBIDDEN);
        }

        @GetMapping("/infrastructure")
        void infrastructure() {
            throw new InfrastructureException(ErrorCode.EMAIL_SERVICE_UNAVAILABLE,
                    new IllegalStateException("SMTP details stay server-side"));
        }

        @PostMapping(value = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
        String validation(@Valid @RequestBody ValidationRequest request) {
            return "ok";
        }

        @GetMapping("/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/request-id")
        String requestId() {
            requestIdSeenByController.set(MDC.get(RequestIdFilter.MDC_KEY));
            return "ok";
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint \"uq_users_email\"");
        }

        @GetMapping("/unknown-constraint")
        void unknownConstraint() {
            throw new DataIntegrityViolationException(
                    "ERROR: insert or update violates foreign key constraint "
                            + "\"fk_documents_folder_same_project\"");
        }

        @PostMapping("/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1L);
        }

        @GetMapping("/unknown")
        void unknown() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }
    }

    record ValidationRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Invalid email address")
            String email,
            @NotBlank(message = "Password is required.")
            @Size(min = 8, max = 64, message = "Password must contain between 8 and 64 characters")
            String password) {
    }
}
