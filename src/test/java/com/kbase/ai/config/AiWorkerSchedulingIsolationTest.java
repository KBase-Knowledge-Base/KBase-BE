package com.kbase.ai.config;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated evidence for the manual Real Gemini smoke isolation contract:
 * the scheduling infrastructure is production-default ON, and setting
 * {@code kbase.ai.worker.scheduling-enabled=false} mechanically removes it so
 * {@code AiJobScheduler.scheduledPoll()} can never fire in the smoke context
 * (no pending DOCUMENT_INDEX/GUIDE_REINDEX job can be claimed, hence no
 * background provider traffic). The smoke clock bean stays available either
 * way. No Gemini network is involved.
 */
class AiWorkerSchedulingIsolationTest {

    private static final String SCHEDULING_PROCESSOR_BEAN =
            org.springframework.scheduling.config.TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiWorkerConfiguration.class, AiWorkerSchedulingConfiguration.class);

    @Test
    void schedulingIsEnabledByDefaultSoProductionBehaviorIsUnchanged() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AiWorkerSchedulingConfiguration.class);
            assertThat(context.containsBean(SCHEDULING_PROCESSOR_BEAN)).isTrue();
            assertThat(context).hasBean("aiClock");
        });
    }

    @Test
    void schedulingPropertyGoneRemovesTheSchedulingInfrastructureEntirely() {
        runner.withPropertyValues("kbase.ai.worker.scheduling-enabled=false").run(context -> {
            // With @EnableScheduling absent from the context, no
            // ScheduledAnnotationBeanPostProcessor exists and no @Scheduled
            // method (AiJobScheduler.scheduledPoll) can ever be triggered.
            assertThat(context.containsBean(SCHEDULING_PROCESSOR_BEAN)).isFalse();
            // The smoke still has the shared worker clock and its configuration.
            assertThat(context).hasBean("aiClock");
        });
    }

    @Test
    void enablingPropertyExplicitlyAlsoKeepsScheduling() {
        runner.withPropertyValues("kbase.ai.worker.scheduling-enabled=true").run(context ->
                assertThat(context.containsBean(SCHEDULING_PROCESSOR_BEAN)).isTrue());
    }
}
