package com.kbase.ai.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared clock and scheduling infrastructure for the durable M3 worker
 * boundary. The {@code @EnableScheduling} switch itself lives in
 * {@link AiWorkerSchedulingConfiguration} so the manual Real Gemini smoke can
 * disable background job execution without touching production defaults.
 */
@Configuration(proxyBeanMethods = false)
public class AiWorkerConfiguration {

    @Bean(name = "aiClock")
    @ConditionalOnMissingBean(name = "aiClock")
    Clock aiClock() {
        return Clock.systemUTC();
    }
}
