package com.kbase.redis.config;

import java.time.Duration;

import com.kbase.config.properties.RedisProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Explicit Redis client configuration for the container-backed OTP store. */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            server.setPassword(RedisPassword.of(properties.getPassword()));
        }

        Duration timeout = properties.getTimeout();
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(timeout == null ? Duration.ofSeconds(2) : timeout)
                .build();
        return new LettuceConnectionFactory(server, client);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
