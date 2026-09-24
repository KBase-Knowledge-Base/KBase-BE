package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * OpenAPI contract tests against the real springdoc runtime and the real
 * security filter chain. The documentation endpoints never touch the database,
 * so the context boots without external infrastructure exactly like the
 * application context smoke test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "kbase.postgres.username=test-user",
        "kbase.postgres.password=test-password",
        "kbase.jwt.signing-secret=test-jwt-signing-secret-at-least-256-bits-long",
        "kbase.otp.hash-secret=test-otp-hash-secret",
        "kbase.mail.username=test@example.invalid",
        "kbase.mail.app-password=test-mail-app-password",
        "kbase.storage.access-key=test-access-key",
        "kbase.storage.secret-key=test-storage-secret-key"
})
class OpenApiContractIntegrationTest {

    private static final Set<String> EXPECTED_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/users/me",
            "/api/v1/users/me/password",
            "/api/v1/admin/users",
            "/api/v1/admin/users/{userId}",
            "/api/v1/admin/users/{userId}/status",
            "/api/v1/projects",
            "/api/v1/projects/{projectId}",
            "/api/v1/admin/projects",
            "/api/v1/projects/{projectId}/members",
            "/api/v1/projects/{projectId}/members/me",
            "/api/v1/projects/{projectId}/members/{userId}",
            "/api/v1/projects/{projectId}/invitations",
            "/api/v1/projects/{projectId}/invitations/{invitationId}/resend",
            "/api/v1/projects/{projectId}/invitations/{invitationId}",
            "/api/v1/invitations/accept",
            "/api/v1/projects/{projectId}/folders",
            "/api/v1/projects/{projectId}/folders/{folderId}",
            "/api/v1/projects/{projectId}/categories",
            "/api/v1/projects/{projectId}/categories/{categoryId}",
            "/api/v1/projects/{projectId}/tags",
            "/api/v1/projects/{projectId}/tags/{tagId}",
            "/api/v1/projects/{projectId}/documents",
            "/api/v1/projects/{projectId}/documents/batch",
            "/api/v1/documents/{documentId}",
            "/api/v1/documents/{documentId}/download",
            "/api/v1/documents/{documentId}/preview",
            "/api/v1/projects/{projectId}/ai/conversations",
            "/api/v1/projects/{projectId}/ai/conversations/{conversationId}",
            "/api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages",
            "/api/v1/projects/{projectId}/documents/{documentId}/ai-index",
            "/api/v1/projects/{projectId}/documents/{documentId}/ai-index/retry");

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode spec;

    @BeforeAll
    void loadApiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andReturn();
        spec = objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void specDescribesKBaseCoreV1WithExactlyTheImplementedPaths() {
        assertThat(spec.get("info").get("title").asString()).isEqualTo("KBase API");
        assertThat(spec.get("info").get("version").asString()).isEqualTo("v1");
        assertThat(spec.get("openapi").asString()).startsWith("3.");

        Set<String> documentedPaths = new java.util.HashSet<>(spec.get("paths").propertyNames());
        assertThat(documentedPaths).containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
        int operations = 0;
        for (String path : documentedPaths) {
            for (String method : spec.get("paths").get(path).propertyNames()) {
                if (!method.equals("parameters")) operations++;
            }
        }
        assertThat(operations).isEqualTo(56);
    }

    @Test
    void bearerAuthSecuritySchemeIsHttpBearerJwt() {
        JsonNode scheme = spec.get("components").get("securitySchemes").get("bearerAuth");
        assertThat(scheme).isNotNull();
        assertThat(scheme.get("type").asString()).isEqualTo("http");
        assertThat(scheme.get("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asString()).isEqualTo("JWT");
    }

    @Test
    void allFourteenFeatureTagsAreDeclared() {
        List<String> tagNames = new java.util.ArrayList<>();
        spec.get("tags").forEach(tag -> tagNames.add(tag.get("name").asString()));
        assertThat(tagNames).containsExactly(
                "Authentication", "Users", "Admin - Users", "Projects", "Admin - Projects",
                "Project Members", "Project Invitations", "Invitations",
                "Folders", "Categories", "Tags", "Documents",
                "AI - Project Assistant", "AI - Document Indexing");
    }

    @Test
    void publicAuthEndpointsCarryNoBearerRequirement() {
        for (String path : PUBLIC_AUTH_PATHS) {
            JsonNode operation = operation(path, "post");
            assertThat(operation.has("security"))
                    .as("public auth endpoint %s must not declare a security requirement", path)
                    .isFalse();
            assertThat(operation.get("tags").get(0).asString()).isEqualTo("Authentication");
        }
    }

    @Test
    void everyOtherOperationRequiresBearerAuth() {
        for (String path : spec.get("paths").propertyNames()) {
            if (PUBLIC_AUTH_PATHS.contains(path)) {
                continue;
            }
            JsonNode pathItem = spec.get("paths").get(path);
            for (String method : pathItem.propertyNames()) {
                if (!method.equals("parameters")) {
                    JsonNode operation = pathItem.get(method);
                    JsonNode security = operation.get("security");
                    assertThat(security).as("operation %s %s must declare bearerAuth", method, path).isNotNull();
                    assertThat(security.get(0).has("bearerAuth"))
                            .as("operation %s %s must require bearerAuth", method, path)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void adminOperationsStateTheSystemRoleRequirement() {
        List<String> adminPaths = List.of(
                "/api/v1/admin/users",
                "/api/v1/admin/users/{userId}",
                "/api/v1/admin/users/{userId}/status",
                "/api/v1/admin/projects");
        for (String path : adminPaths) {
            JsonNode pathItem = spec.get("paths").get(path);
            for (String method : pathItem.propertyNames()) {
                if (method.equals("parameters")) {
                    continue;
                }
                JsonNode operation = pathItem.get(method);
                String description = textOrEmpty(operation.get("description"));
                assertThat(description)
                        .as("operation %s %s must document the ADMIN role", method, path)
                        .contains("SystemRole.ADMIN");
                assertThat(operation.get("tags").get(0).asString()).startsWith("Admin - ");
            }
        }
    }

    @Test
    void projectAndDocumentPermissionRulesAreDocumented() {
        assertThat(textOrEmpty(operation("/api/v1/projects/{projectId}", "get").get("description")))
                .contains("MEMBER", "OWNER", "ADMIN", "null");
        assertThat(textOrEmpty(operation("/api/v1/projects/{projectId}/members/{userId}", "delete").get("description")))
                .contains("OWNER", "documents remain");
        assertThat(textOrEmpty(operation("/api/v1/projects/{projectId}/invitations", "post").get("description")))
                .contains("OWNER or system ADMIN", "raw token");
        assertThat(textOrEmpty(operation("/api/v1/documents/{documentId}", "patch").get("description")))
                .contains("MEMBER", "uploaded", "OWNER", "ADMIN");
        assertThat(textOrEmpty(operation("/api/v1/documents/{documentId}", "delete").get("description")))
                .contains("storage-first");
        assertThat(textOrEmpty(operation("/api/v1/invitations/accept", "post").get("description")))
                .contains("PENDING", "invitation email");
    }

    @Test
    void singleUploadDocumentsBinaryFileAndMetadataJsonParts() {
        JsonNode requestBody = operation("/api/v1/projects/{projectId}/documents", "post").get("requestBody");
        JsonNode multipart = requestBody.get("content").get("multipart/form-data");
        assertThat(multipart).isNotNull();

        JsonNode filePart = multipart.get("schema").get("properties").get("file");
        assertThat(filePart.get("type").asString()).isEqualTo("string");
        assertThat(filePart.get("format").asString()).isEqualTo("binary");

        JsonNode metadataPart = multipart.get("schema").get("properties").get("metadata");
        assertThat(metadataPart).isNotNull();

        JsonNode required = multipart.get("schema").get("required");
        assertThat(required.toString()).contains("file");
    }

    @Test
    void batchUploadDocumentsArrayOfBinaryFiles() {
        JsonNode requestBody = operation("/api/v1/projects/{projectId}/documents/batch", "post").get("requestBody");
        JsonNode filesPart = requestBody.get("content").get("multipart/form-data")
                .get("schema").get("properties").get("files");
        assertThat(filesPart.get("type").asString()).isEqualTo("array");
        assertThat(filesPart.get("items").get("type").asString()).isEqualTo("string");
        assertThat(filesPart.get("items").get("format").asString()).isEqualTo("binary");
    }

    @Test
    void downloadIsADocumentBinaryNotAResponseDto() {
        JsonNode response = operation("/api/v1/documents/{documentId}/download", "get")
                .get("responses").get("200");
        JsonNode schema = response.get("content").get("application/octet-stream").get("schema");
        assertThat(schema.get("type").asString()).isEqualTo("string");
        assertThat(schema.get("format").asString()).isEqualTo("binary");
        assertThat(response.get("headers").get("Content-Disposition")).isNotNull();
    }

    @Test
    void previewDocumentsRangeHeaderWith206And416() {
        JsonNode operation = operation("/api/v1/documents/{documentId}/preview", "get");

        JsonNode rangeParameter = null;
        for (JsonNode parameter : operation.get("parameters")) {
            if ("Range".equals(parameter.get("name").asString())
                    && "header".equals(parameter.get("in").asString())) {
                rangeParameter = parameter;
            }
        }
        assertThat(rangeParameter).as("preview must document the optional Range header").isNotNull();
        assertThat(rangeParameter.get("description").asString()).contains("bytes=0-1048575");

        JsonNode responses = operation.get("responses");
        for (String code : new String[] {"200", "206"}) {
            JsonNode schema = responses.get(code).get("content").get("application/octet-stream").get("schema");
            assertThat(schema.get("type").asString()).as("preview %s must be binary", code).isEqualTo("string");
            assertThat(schema.get("format").asString()).as("preview %s must be binary", code).isEqualTo("binary");
        }
        assertThat(responses.get("206").get("headers").get("Content-Range")).isNotNull();
        assertThat(responses.get("416").get("headers").get("Content-Range")).isNotNull();
        assertThat(textOrEmpty(responses.get("415").get("description"))).contains("PREVIEW_NOT_SUPPORTED");
    }

    @Test
    void sharedApiErrorResponseSchemaExistsAndIsReferencedByEndpointErrors() {
        JsonNode schema = spec.get("components").get("schemas").get("ApiErrorResponse");
        assertThat(schema).isNotNull();
        for (String property : List.of("timestamp", "status", "code", "message", "path", "requestId")) {
            assertThat(schema.get("properties").has(property))
                    .as("ApiErrorResponse must expose %s", property)
                    .isTrue();
        }
        assertThat(schema.get("properties").has("errors")).isTrue();

        String specText = spec.toString();
        int errorReferences = specText.split("\\$ref[^,}]*ApiErrorResponse", -1).length - 1;
        assertThat(errorReferences).as("endpoint error responses must share ApiErrorResponse").isGreaterThan(20);
    }

    @Test
    void otpEmailAndInfrastructureErrorCodesAreDocumentedOnTheRightEndpoints() {
        JsonNode verifyEmail = operation("/api/v1/auth/verify-email", "post").get("responses");
        assertThat(textOrEmpty(verifyEmail.get("400").get("description"))).contains("INVALID_OTP", "OTP_EXPIRED");
        assertThat(textOrEmpty(verifyEmail.get("409").get("description"))).contains("EMAIL_ALREADY_VERIFIED");
        assertThat(textOrEmpty(verifyEmail.get("429").get("description"))).contains("OTP_ATTEMPTS_EXCEEDED");
        assertThat(textOrEmpty(verifyEmail.get("503").get("description"))).contains("OTP_SERVICE_UNAVAILABLE");
        assertThat(textOrEmpty(operation("/api/v1/auth/verify-email", "post").get("description")))
                .contains("Gmail SMTP", "email verification");

        JsonNode resend = operation("/api/v1/auth/resend-verification-otp", "post").get("responses");
        assertThat(textOrEmpty(resend.get("429").get("description"))).contains("OTP_RESEND_COOLDOWN");
        assertThat(textOrEmpty(resend.get("503").get("description"))).contains("EMAIL_SERVICE_UNAVAILABLE");

        JsonNode upload = operation("/api/v1/projects/{projectId}/documents", "post").get("responses");
        assertThat(textOrEmpty(upload.get("413").get("description"))).contains("FILE_TOO_LARGE");
        assertThat(textOrEmpty(upload.get("415").get("description"))).contains("UNSUPPORTED_FILE_TYPE", "MIME_TYPE_MISMATCH");
        assertThat(textOrEmpty(upload.get("500").get("description"))).contains("FILE_UPLOAD_FAILED");
        assertThat(textOrEmpty(upload.get("503").get("description"))).contains("STORAGE_SERVICE_UNAVAILABLE");
    }

    @Test
    void searchEndpointDocumentsMetadataFiltersPaginationAndSortWhitelist() {
        JsonNode operation = operation("/api/v1/projects/{projectId}/documents", "get");
        Set<String> parameterNames = new java.util.HashSet<>();
        operation.get("parameters").forEach(parameter -> parameterNames.add(parameter.get("name").asString()));
        assertThat(parameterNames).contains(
                "q", "folderId", "categoryId", "tagId", "fileKind", "uploadedBy",
                "createdFrom", "createdTo", "page", "size", "sort");

        JsonNode sortParameter = null;
        for (JsonNode parameter : operation.get("parameters")) {
            if ("sort".equals(parameter.get("name").asString())) {
                sortParameter = parameter;
            }
        }
        assertThat(sortParameter.get("description").asString()).contains("displayName", "sizeBytes");

        String qDescription = "";
        for (JsonNode parameter : operation.get("parameters")) {
            if ("q".equals(parameter.get("name").asString())) {
                qDescription = parameter.get("description").asString();
            }
        }
        assertThat(qDescription).contains("metadata");

        JsonNode okSchema = firstJsonSchema(operation.get("responses").get("200").get("content"));
        assertThat(okSchema.get("$ref").asString()).contains("PageResponse", "DocumentSummaryResponse");
    }

    /** List endpoints do not declare produces, so springdoc documents them under '*'; resolve the JSON schema. */
    private static JsonNode firstJsonSchema(JsonNode content) {
        assertThat(content).isNotNull();
        for (String mediaType : content.propertyNames()) {
            JsonNode schema = content.get(mediaType).get("schema");
            if (schema != null && schema.has("$ref")) {
                return schema;
            }
        }
        throw new AssertionError("No JSON schema found in response content");
    }

    @Test
    void paginationEnvelopeIsShared() {
        JsonNode schema = spec.get("components").get("schemas");
        JsonNode projectPage = null;
        for (String name : schema.propertyNames()) {
            if (name.startsWith("PageResponse") && name.contains("ProjectResponse")) {
                projectPage = schema.get(name);
            }
        }
        assertThat(projectPage).isNotNull();
        for (String property : List.of("content", "page", "size", "totalElements", "totalPages", "first", "last")) {
            assertThat(projectPage.get("properties").has(property))
                    .as("pagination envelope must expose %s", property)
                    .isTrue();
        }
    }

    @Test
    void sensitiveAndInternalFieldsAreAbsentFromEverySchema() {
        List<String> forbidden = List.of(
                "passwordHash", "tokenHash", "storageKey", "refreshToken",
                "rawToken", "otpHash", "appPassword", "secretKey", "accessKey",
                "retrievalScore", "chunkId", "sourceHash", "activeVersion", "desiredVersion",
                "attemptCount", "lastErrorCode", "lockedBy", "payload");
        JsonNode schemas = spec.get("components").get("schemas");
        for (String schemaName : schemas.propertyNames()) {
            JsonNode properties = schemas.get(schemaName).get("properties");
            if (properties == null) {
                continue;
            }
            for (String propertyName : properties.propertyNames()) {
                assertThat(propertyName)
                        .as("schema %s must not expose %s", schemaName, propertyName)
                        .isNotIn(forbidden);
            }
        }
        assertThat(schemas.propertyNames().toString()).doesNotContain("Entity");

        String specText = spec.toString();
        for (String token : List.of("passwordHash", "tokenHash", "storageKey", "smtp.gmail.com")) {
            assertThat(specText).doesNotContain(token);
        }
    }

    @Test
    void noUnapprovedAiOrRagEndpointsAreDocumented() {
        for (String path : EXPECTED_PATHS) {
            assertThat(path).doesNotContain("/chat", "/ask", "/embedding", "/rag", "/semantic", "/guide");
        }
    }

    @Test
    void aiPathsMethodsAndSchemasMatchTheImplementedM7Boundary() {
        String conversations = "/api/v1/projects/{projectId}/ai/conversations";
        String conversation = conversations + "/{conversationId}";
        String messages = conversation + "/messages";
        String index = "/api/v1/projects/{projectId}/documents/{documentId}/ai-index";
        assertThat(spec.get("paths").get(conversations).propertyNames())
                .containsExactlyInAnyOrder("get", "post");
        assertThat(spec.get("paths").get(conversation).propertyNames())
                .containsExactlyInAnyOrder("get", "patch", "delete");
        assertThat(spec.get("paths").get(messages).propertyNames())
                .containsExactlyInAnyOrder("get", "post");
        assertThat(spec.get("paths").get(index).propertyNames()).containsExactly("get");
        assertThat(spec.get("paths").get(index + "/retry").propertyNames()).containsExactly("post");

        assertThat(operation(conversations, "post").get("tags").get(0).asString())
                .isEqualTo("AI - Project Assistant");
        assertThat(operation(index, "get").get("tags").get(0).asString())
                .isEqualTo("AI - Document Indexing");
        assertThat(textOrEmpty(operation(conversations, "post").get("responses").get("409")
                .get("description"))).contains("AI_CONVERSATION_LIMIT_REACHED");
        assertThat(textOrEmpty(operation(messages, "post").get("responses").get("409")
                .get("description"))).contains("AI_REQUEST_IN_PROGRESS");
        assertThat(textOrEmpty(operation(messages, "post").get("responses").get("503")
                .get("description"))).contains("AI_PROVIDER_UNAVAILABLE");
        assertThat(operation(index + "/retry", "post").get("responses").has("202")).isTrue();
        assertThat(spec.toString()).doesNotContain("AI_RATE_LIMIT_EXCEEDED");

        JsonNode createRequest = firstJsonSchema(operation(conversations, "post")
                .get("requestBody").get("content"));
        JsonNode sendRequest = firstJsonSchema(operation(messages, "post")
                .get("requestBody").get("content"));
        assertThat(createRequest.get("$ref").asString()).contains("CreateAiConversationRequest");
        assertThat(sendRequest.get("$ref").asString()).contains("SendAiMessageRequest");
        assertThat(firstJsonSchema(operation(conversations, "post").get("responses")
                .get("201").get("content")).get("$ref").asString())
                .contains("CreateAiConversationResponse");
        assertThat(firstJsonSchema(operation(messages, "post").get("responses")
                .get("200").get("content")).get("$ref").asString())
                .contains("AiTurnResponse");
        assertThat(firstJsonSchema(operation(index + "/retry", "post").get("responses")
                .get("202").get("content")).get("$ref").asString())
                .contains("DocumentAiIndexResponse");

        JsonNode schemas = spec.get("components").get("schemas");
        for (String name : List.of("AiConversationResponse", "AiMessageResponse", "AiSourceResponse",
                "DocumentAiIndexResponse")) {
            assertThat(schemas.has(name)).as("M7 DTO %s", name).isTrue();
        }
    }

    @Test
    void importantErrorCodesAreVisibleSomewhereInTheContract() {
        String specText = spec.toString();
        for (String code : List.of("VALIDATION_ERROR", "INVALID_OTP", "OTP_EXPIRED", "OTP_ATTEMPTS_EXCEEDED",
                "OTP_RESEND_COOLDOWN", "OTP_SERVICE_UNAVAILABLE", "EMAIL_SERVICE_UNAVAILABLE",
                "STORAGE_SERVICE_UNAVAILABLE", "PROJECT_ACCESS_FORBIDDEN", "DOCUMENT_MODIFICATION_FORBIDDEN")) {
            assertThat(specText).contains(code);
        }
    }

    @Test
    void swaggerUiRedirectsWhileProtectedApiStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private JsonNode operation(String path, String method) {
        JsonNode pathItem = spec.get("paths").get(path);
        assertThat(pathItem).as("path %s must be documented", path).isNotNull();
        JsonNode operation = pathItem.get(method);
        assertThat(operation).as("operation %s %s must be documented", method, path).isNotNull();
        return operation;
    }

    private static String textOrEmpty(JsonNode node) {
        return node == null ? "" : node.asString();
    }
}
