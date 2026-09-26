package com.kbase.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the profile fail-safe property after removing
 * {@code spring.profiles.default=local}: an artifact started with no explicit
 * profile must not silently inherit local-only development behavior
 * (Secure=false refresh cookie, storage auto-create), while the explicit
 * {@code local} profile keeps the documented local behavior.
 */
class ProfileFailSafeTest {

    private StandardEnvironment environmentFor(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        if (!properties.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("scenario", properties));
        }
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        new ConfigDataApplicationContextInitializer().initialize(context);
        return environment;
    }

    @Test
    void noActiveProfileDoesNotInheritLocalOnlyDevelopmentBehavior() {
        StandardEnvironment environment = environmentFor(Map.of());

        assertThat(environment.getProperty("kbase.refresh-cookie.secure")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.storage.auto-create-bucket")).isEqualTo("false");
        assertThat(environment.getProperty("kbase.storage.initialize-on-startup")).isEqualTo("false");
        // API docs and Swagger UI stay off for an artifact that never chose a profile.
        assertThat(environment.getProperty("kbase.openapi.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("kbase.openapi.swagger-ui-enabled")).isEqualTo("false");
    }

    @Test
    void explicitLocalProfileKeepsDocumentedLocalBehavior() {
        StandardEnvironment environment = environmentFor(Map.of(
                "spring.profiles.active", "local"));

        assertThat(environment.getProperty("kbase.refresh-cookie.secure")).isEqualTo("false");
        assertThat(environment.getProperty("kbase.storage.auto-create-bucket")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.storage.initialize-on-startup")).isEqualTo("true");
        // Local development intentionally exposes the documentation endpoints.
        assertThat(environment.getProperty("kbase.openapi.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.openapi.swagger-ui-enabled")).isEqualTo("true");
    }

    @Test
    void explicitProdProfileKeepsProductionSafeBehavior() {
        StandardEnvironment environment = environmentFor(Map.of(
                "spring.profiles.active", "prod"));

        assertThat(environment.getProperty("kbase.refresh-cookie.secure")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.openapi.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("kbase.openapi.swagger-ui-enabled")).isEqualTo("false");
    }

    @Test
    void runtimeTestProfileKeepsVerificationContract() {
        StandardEnvironment environment = environmentFor(Map.of(
                "spring.profiles.active", "runtime-test"));

        // M11 runtime verification reads /v3/api-docs through this profile.
        assertThat(environment.getProperty("kbase.openapi.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.refresh-cookie.secure")).isEqualTo("true");
        assertThat(environment.getProperty("kbase.storage.auto-create-bucket")).isEqualTo("false");
    }

    @Test
    void applicationYmlDeclaresNoDefaultProfile() throws Exception {
        String applicationYml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(applicationYml).doesNotContain("default: local");
    }

    @Test
    void localComposeSelectsTheLocalProfileExplicitly() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));
        assertThat(compose).contains("SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}");
    }
}
