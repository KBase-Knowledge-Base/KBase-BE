package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.usage.RedisAiUsageGuard;
import com.kbase.config.properties.RedisProperties;
import com.kbase.redis.config.RedisConfig;
import com.kbase.shared.exception.AiRateLimitExceededException;
import com.kbase.shared.exception.AiUsageGuardUnavailableException;
import com.kbase.shared.exception.ErrorCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real-Redis proof for the AI-only fixed-window counter. */
@Testcontainers
class RedisAiUsageGuardIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private AdjustableClock clock;
    private AiProperties properties;
    private RedisAiUsageGuard guard;

    @BeforeEach
    void setUp() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost(REDIS.getHost());
        redisProperties.setPort(REDIS.getMappedPort(6379));
        redisProperties.setTimeout(Duration.ofSeconds(2));
        connectionFactory = new RedisConfig().redisConnectionFactory(redisProperties);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        properties = new AiProperties();
        properties.setUsageRateNamespace("kbase:ai:rate:test:" + UUID.randomUUID());
        properties.setUsageMaxRequests(5);
        properties.setUsageWindow(Duration.ofMinutes(1));
        clock = new AdjustableClock(Instant.parse("2026-09-25T00:00:00Z"));
        guard = new RedisAiUsageGuard(redisTemplate, properties, clock);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentRequestsUseOneAtomicCounterAndEnforceExactLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        int attempts = 20;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int index = 0; index < attempts; index++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        guard.consume(userId);
                        return true;
                    } catch (AiRateLimitExceededException rejected) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(properties.getUsageMaxRequests());
            assertThat(results).hasSize(attempts);
        }
    }

    @Test
    void usersAreIsolatedAndTheSameUserSharesOnlyItsBucket() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        properties.setUsageMaxRequests(1);

        guard.consume(first);
        assertThatThrownBy(() -> guard.consume(first))
                .isInstanceOf(AiRateLimitExceededException.class);
        guard.consume(second);

        String firstKey = key(first);
        assertThat(redisTemplate.opsForValue().get(firstKey)).isEqualTo("2");
        assertThat(redisTemplate.opsForValue().get(
                key(second))).isEqualTo("1");
    }

    @Test
    void ttlIsSetOnFirstIncrementAndIsNotResetOnLaterRequests() {
        UUID userId = UUID.randomUUID();
        String key = key(userId);

        guard.consume(userId);
        long firstTtl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        guard.consume(userId);
        long secondTtl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertThat(firstTtl).isBetween(1_000L, properties.getUsageWindow().toMillis());
        assertThat(secondTtl).isLessThanOrEqualTo(firstTtl);
    }

    @Test
    void movingToTheNextClockBucketAllowsARequestWithoutWaitingForRedisExpiry() {
        UUID userId = UUID.randomUUID();
        properties.setUsageMaxRequests(1);
        guard.consume(userId);
        assertThatThrownBy(() -> guard.consume(userId))
                .isInstanceOf(AiRateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(1));
        guard.consume(userId);

        assertThat(redisTemplate.opsForValue().get(key(userId))).isEqualTo("1");
    }

    @Test
    void RedisFailureBecomesAIOnly503WithoutRedisDetails() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        RedisProperties unavailableProperties = new RedisProperties();
        unavailableProperties.setHost("127.0.0.1");
        unavailableProperties.setPort(unavailablePort);
        unavailableProperties.setTimeout(Duration.ofMillis(200));
        LettuceConnectionFactory unavailableFactory = new RedisConfig()
                .redisConnectionFactory(unavailableProperties);
        unavailableFactory.afterPropertiesSet();
        try {
            RedisAiUsageGuard unavailable = new RedisAiUsageGuard(
                    new StringRedisTemplate(unavailableFactory), properties, clock);
            assertThatThrownBy(() -> unavailable.consume(UUID.randomUUID()))
                    .isInstanceOf(AiUsageGuardUnavailableException.class)
                    .extracting(exception -> ((AiUsageGuardUnavailableException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.AI_USAGE_GUARD_UNAVAILABLE);
        } finally {
            unavailableFactory.destroy();
        }
    }

    private static final class AdjustableClock extends Clock {
        private Instant current;

        private AdjustableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private String key(UUID userId) {
        long bucket = Math.floorDiv(clock.instant().toEpochMilli(),
                properties.getUsageWindow().toMillis());
        return properties.getUsageRateNamespace() + ":" + userId + ":" + bucket;
    }
}
