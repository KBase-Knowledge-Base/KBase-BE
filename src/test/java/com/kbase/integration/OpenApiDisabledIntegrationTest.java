package com.kbase.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With the OpenAPI flags off, neither the generated spec nor Swagger UI is
 * reachable, while API security is unchanged. This is the production-style
 * exposure baseline driven purely by configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
        "kbase.storage.secret-key=test-storage-secret-key",
        "kbase.openapi.enabled=false",
        "kbase.openapi.swagger-ui-enabled=false"
})
class OpenApiDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAndSwaggerUiAreNotExposedWhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiAuthorizationIsUnaffectedByTheDocumentationFlags() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/login")).andExpect(status().is4xxClientError());
    }
}
