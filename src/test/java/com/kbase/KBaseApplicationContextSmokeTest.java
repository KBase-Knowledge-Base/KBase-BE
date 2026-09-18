package com.kbase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.kbase.config.properties.JwtProperties;
import com.kbase.security.jwt.JwtService;

import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application context smoke test. The context contains the full backend
 * wiring; the DataSource is declared but never contacted because Flyway is
 * disabled, schema validation is off and no bean opens a connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.web-application-type=none",
        "spring.datasource.url=jdbc:postgresql://localhost:59999/context-smoke",
        "spring.datasource.username=context-smoke-user",
        "spring.datasource.password=context-smoke-password",
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
class KBaseApplicationContextSmokeTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtService jwtService;

    @Test
    void applicationContextStartsWithoutExternalInfrastructure() {
        assertThat(jwtProperties.getAlgorithm()).isEqualTo("HS256");
        assertThat(jwtProperties.getSigningSecret()).isEqualTo("test-jwt-signing-secret-at-least-256-bits-long");
        assertThat(jwtService).isNotNull();
    }
}
