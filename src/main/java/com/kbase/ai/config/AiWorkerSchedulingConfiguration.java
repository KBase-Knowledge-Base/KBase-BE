package com.kbase.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the Spring scheduling infrastructure for the durable AI worker.
 *
 * <p>Production and every documented runtime keep the default (property
 * absent → scheduling enabled). The manual Real Gemini provider smoke sets
 * {@code kbase.ai.worker.scheduling-enabled=false} so its context mechanically
 * cannot execute background AI jobs (DOCUMENT_INDEX/GUIDE_REINDEX claims and
 * provider calls) — the smoke performs only its two explicit adapter
 * invocations. Disabling this property is a manual-verification choice and is
 * never set by any committed runtime configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "kbase.ai.worker", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class AiWorkerSchedulingConfiguration {
}
