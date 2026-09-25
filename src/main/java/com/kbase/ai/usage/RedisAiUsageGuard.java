package com.kbase.ai.usage;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.observability.AiObservability;
import com.kbase.shared.exception.AiRateLimitExceededException;
import com.kbase.shared.exception.AiUsageGuardUnavailableException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis-backed fixed-window implementation of the KBase AI usage port. */
@Component
public final class RedisAiUsageGuard implements AiUsageGuard {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;
    private final Clock clock;
    private final AiObservability observability;

    @Autowired
    public RedisAiUsageGuard(StringRedisTemplate redisTemplate, AiProperties properties,
            @Qualifier("aiClock") Clock clock, AiObservability observability) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observability = Objects.requireNonNull(observability, "observability");
        validate(properties);
    }

    /** Compatibility constructor for focused adapter tests. */
    public RedisAiUsageGuard(StringRedisTemplate redisTemplate, AiProperties properties,
            Clock clock) {
        this(redisTemplate, properties, clock, new AiObservability());
    }

    @Override
    public void consume(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        long count;
        try {
            count = Objects.requireNonNull(redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    List.of(key(userId)),
                    Long.toString(windowMillis())),
                    "Redis usage counter returned no result");
        } catch (RuntimeException failure) {
            observability.recordRateOutcome(AiObservability.RateOutcome.UNAVAILABLE);
            throw new AiUsageGuardUnavailableException();
        }

        if (count > properties.getUsageMaxRequests()) {
            observability.recordRateOutcome(AiObservability.RateOutcome.REJECTED);
            throw new AiRateLimitExceededException();
        }
        observability.recordRateOutcome(AiObservability.RateOutcome.ALLOWED);
    }

    /** Package-visible for deterministic Redis adapter tests without exposing it to the app port. */
    String key(UUID userId) {
        long bucket = Math.floorDiv(clock.instant().toEpochMilli(), windowMillis());
        return properties.getUsageRateNamespace() + ":" + userId + ":" + bucket;
    }

    private long windowMillis() {
        return properties.getUsageWindow().toMillis();
    }

    private static void validate(AiProperties properties) {
        if (properties.getUsageRateNamespace() == null
                || properties.getUsageRateNamespace().isBlank()
                || properties.getUsageMaxRequests() <= 0
                || properties.getUsageWindow() == null
                || properties.getUsageWindow().isZero()
                || properties.getUsageWindow().isNegative()
                || positiveMillis(properties.getUsageWindow()) <= 0) {
            throw new IllegalArgumentException("AI usage guard configuration is invalid");
        }
    }

    private static long positiveMillis(Duration duration) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException exception) {
            return -1;
        }
    }
}
