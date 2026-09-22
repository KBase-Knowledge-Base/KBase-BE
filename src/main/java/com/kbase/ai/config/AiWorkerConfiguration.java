package com.kbase.ai.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Shared clock and scheduling infrastructure for the durable M3 worker boundary. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AiWorkerConfiguration {

    @Bean(name = "aiClock")
    @ConditionalOnMissingBean(name = "aiClock")
    Clock aiClock() {
        return Clock.systemUTC();
    }
}
