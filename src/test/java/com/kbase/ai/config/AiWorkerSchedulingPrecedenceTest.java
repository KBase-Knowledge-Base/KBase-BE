package com.kbase.ai.config;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated evidence that the manual Real Gemini smoke's isolation override
 * wins on PRECEDENCE, not merely on the conditional annotation.
 *
 * <p>Spring Boot resolves command-line arguments as the highest-precedence
 * property source, above the OS environment. The test simulates a hostile
 * operator environment ({@code KBASE_AI_WORKER_SCHEDULING_ENABLED=true}
 * injected as the {@code systemEnvironment} property source of a real
 * {@link StandardEnvironment}) and proves:
 *
 * <ul>
 *   <li>without the smoke override, the hostile environment really enables
 *       scheduling (control case — the binding is real);</li>
 *   <li>with the smoke's command-line {@code --kbase.ai.worker.scheduling-enabled=false},
 *       the effective value is {@code false} and the scheduling infrastructure
 *       is absent, regardless of the environment variable.</li>
 * </ul>
 *
 * <p>No Gemini network is involved.
 */
class AiWorkerSchedulingPrecedenceTest {

    private static final String SCHEDULING_PROCESSOR_BEAN =
            TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

    @Configuration
    @Import({AiWorkerConfiguration.class, AiWorkerSchedulingConfiguration.class})
    static class PrecedenceTestConfig {
    }

    /** Real StandardEnvironment whose systemEnvironment source is hostile. */
    private static StandardEnvironment hostileEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("KBASE_AI_WORKER_SCHEDULING_ENABLED", "true")));
        return environment;
    }

    @Test
    void hostileEnvironmentVariableAloneWouldEnableScheduling() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(PrecedenceTestConfig.class)
                .web(WebApplicationType.NONE)
                .environment(hostileEnvironment())
                .run("--spring.main.banner-mode=off");
        try {
            assertThat(context.getEnvironment()
                    .getProperty("kbase.ai.worker.scheduling-enabled")).isEqualTo("true");
            assertThat(context.containsBean(SCHEDULING_PROCESSOR_BEAN)).isTrue();
        } finally {
            context.close();
        }
    }

    @Test
    void smokeCommandLineOverrideBeatsTheHostileEnvironmentVariable() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(PrecedenceTestConfig.class)
                .web(WebApplicationType.NONE)
                .environment(hostileEnvironment())
                .run(
                        "--kbase.ai.worker.scheduling-enabled=false",
                        "--spring.main.banner-mode=off");
        try {
            assertThat(context.getEnvironment()
                    .getProperty("kbase.ai.worker.scheduling-enabled")).isEqualTo("false");
            assertThat(context.containsBean(SCHEDULING_PROCESSOR_BEAN)).isFalse();
        } finally {
            context.close();
        }
    }
}
