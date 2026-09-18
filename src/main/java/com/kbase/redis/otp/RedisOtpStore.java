package com.kbase.redis.otp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kbase.auth.port.OtpStore;
import com.kbase.auth.port.OtpVerificationState;
import com.kbase.shared.exception.OtpServiceUnavailableException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis implementation of the email-verification OTP port.
 *
 * <p>Only string/hash values are used. State and cooldown are both explicitly
 * expiring, so this adapter cannot turn Redis into durable business storage.</p>
 */
@Component
public class RedisOtpStore implements OtpStore {

    public static final String STATE_KEY_PREFIX = "kbase:otp:email-verification:";
    public static final String COOLDOWN_KEY_PREFIX = STATE_KEY_PREFIX + "cooldown:";
    public static final String OTP_HASH_FIELD = "otp_hash";
    public static final String ATTEMPTS_FIELD = "attempts";

    private static final String INITIAL_ATTEMPTS = "0";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('DEL', KEYS[1]); "
                    + "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5]); "
                    + "redis.call('SET', KEYS[2], '1', 'PX', ARGV[6]); "
                    + "return 1",
            Long.class);

    private static final DefaultRedisScript<Long> REPLACE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5]); "
                    + "redis.call('SET', KEYS[2], '1', 'PX', ARGV[6]); "
                    + "return 1",
            Long.class);

    private static final DefaultRedisScript<Long> INCREMENT_ATTEMPTS_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end; "
                    + "return redis.call('HINCRBY', KEYS[1], ARGV[1], 1)",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisOtpStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public void saveVerificationOtp(UUID userId, String protectedOtpHash, Duration ttl, Duration resendCooldown) {
        validateInputs(userId, protectedOtpHash, ttl, resendCooldown);
        executeWrite(SAVE_SCRIPT, userId, protectedOtpHash, ttl, resendCooldown);
    }

    @Override
    public Optional<OtpVerificationState> getVerificationOtp(UUID userId) {
        requireUserId(userId);
        try {
            String stateKey = verificationKey(userId);
            Map<Object, Object> values = redisTemplate.opsForHash().entries(stateKey);
            if (values.isEmpty()) {
                return Optional.empty();
            }

            Object hashValue = values.get(OTP_HASH_FIELD);
            Object attemptsValue = values.get(ATTEMPTS_FIELD);
            if (hashValue == null || attemptsValue == null) {
                throw new OtpServiceUnavailableException();
            }

            int attempts;
            try {
                attempts = Integer.parseInt(String.valueOf(attemptsValue));
            } catch (NumberFormatException exception) {
                throw new OtpServiceUnavailableException();
            }
            if (attempts < 0) {
                throw new OtpServiceUnavailableException();
            }

            Long remainingMillis = redisTemplate.getExpire(stateKey, TimeUnit.MILLISECONDS);
            if (remainingMillis == null || remainingMillis == -2L) {
                return Optional.empty();
            }
            if (remainingMillis == -1L) {
                // A state without expiry violates the OTP storage contract.
                throw new OtpServiceUnavailableException();
            }
            if (remainingMillis <= 0L) {
                return Optional.empty();
            }
            return Optional.of(new OtpVerificationState(
                    String.valueOf(hashValue),
                    attempts,
                    Duration.ofMillis(remainingMillis)));
        } catch (OtpServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public int incrementAttempts(UUID userId) {
        requireUserId(userId);
        try {
            Long attempts = redisTemplate.execute(
                    INCREMENT_ATTEMPTS_SCRIPT,
                    List.of(verificationKey(userId)),
                    ATTEMPTS_FIELD);
            if (attempts == null) {
                throw unavailable();
            }
            return attempts.intValue();
        } catch (OtpServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean isResendCooldownActive(UUID userId) {
        requireUserId(userId);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(userId)));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean replaceVerificationOtp(
            UUID userId,
            String protectedOtpHash,
            Duration ttl,
            Duration resendCooldown) {
        validateInputs(userId, protectedOtpHash, ttl, resendCooldown);
        try {
            Long replaced = redisTemplate.execute(
                    REPLACE_SCRIPT,
                    List.of(verificationKey(userId), cooldownKey(userId)),
                    OTP_HASH_FIELD,
                    protectedOtpHash,
                    ATTEMPTS_FIELD,
                    INITIAL_ATTEMPTS,
                    Long.toString(toPositiveMillis(ttl)),
                    Long.toString(toPositiveMillis(resendCooldown)));
            if (replaced == null) {
                throw unavailable();
            }
            return replaced == 1L;
        } catch (OtpServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public void deleteVerificationOtp(UUID userId) {
        requireUserId(userId);
        try {
            redisTemplate.delete(List.of(verificationKey(userId), cooldownKey(userId)));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public static String verificationKey(UUID userId) {
        return STATE_KEY_PREFIX + Objects.requireNonNull(userId, "userId");
    }

    public static String cooldownKey(UUID userId) {
        return COOLDOWN_KEY_PREFIX + Objects.requireNonNull(userId, "userId");
    }

    private void executeWrite(
            DefaultRedisScript<Long> script,
            UUID userId,
            String protectedOtpHash,
            Duration ttl,
            Duration resendCooldown) {
        try {
            Long result = redisTemplate.execute(
                    script,
                    List.of(verificationKey(userId), cooldownKey(userId)),
                    OTP_HASH_FIELD,
                    protectedOtpHash,
                    ATTEMPTS_FIELD,
                    INITIAL_ATTEMPTS,
                    Long.toString(toPositiveMillis(ttl)),
                    Long.toString(toPositiveMillis(resendCooldown)));
            if (result == null) {
                throw unavailable();
            }
        } catch (OtpServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static void validateInputs(
            UUID userId,
            String protectedOtpHash,
            Duration ttl,
            Duration resendCooldown) {
        requireUserId(userId);
        if (protectedOtpHash == null || protectedOtpHash.isBlank()
                || ttl == null || ttl.isZero() || ttl.isNegative()
                || resendCooldown == null || resendCooldown.isZero() || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("OTP state parameters are invalid");
        }
    }

    private static long toPositiveMillis(Duration duration) {
        long millis = duration.toMillis();
        return Math.max(1L, millis);
    }

    private static void requireUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
    }

    private static OtpServiceUnavailableException unavailable() {
        return new OtpServiceUnavailableException();
    }
}
