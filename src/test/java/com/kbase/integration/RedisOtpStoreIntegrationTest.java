package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kbase.auth.port.OtpStore;
import com.kbase.auth.service.OtpService;
import com.kbase.config.properties.RedisProperties;
import com.kbase.config.properties.OtpProperties;
import com.kbase.redis.config.RedisConfig;
import com.kbase.redis.otp.RedisOtpStore;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.OtpServiceUnavailableException;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Testcontainers
class RedisOtpStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisOtpStore store;

    @BeforeEach
    void setUp() {
        RedisProperties properties = new RedisProperties();
        properties.setHost(REDIS.getHost());
        properties.setPort(REDIS.getMappedPort(6379));
        properties.setTimeout(Duration.ofSeconds(2));
        connectionFactory = new RedisConfig().redisConnectionFactory(properties);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisOtpStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void otpPortContainsOnlyApplicationTypesAndRedisAdapterImplementsIt() {
        assertThat(OtpStore.class.isAssignableFrom(RedisOtpStore.class)).isTrue();
        for (var method : OtpStore.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName())
                    .doesNotStartWith("org.springframework.data.redis.")
                    .doesNotStartWith("com.kbase.redis.");
            assertThat(method.getParameterTypes())
                    .allSatisfy(type -> assertThat(type.getName())
                            .doesNotStartWith("org.springframework.data.redis.")
                            .doesNotStartWith("com.kbase.redis."));
        }
    }

    @Test
    void storesOnlyProtectedHashAndKeepsStateAndCooldownExpiring() {
        UUID userId = UUID.randomUUID();

        store.saveVerificationOtp(userId, "hmac-protected-value", Duration.ofSeconds(5), Duration.ofSeconds(2));

        Map<Object, Object> redisState = redisTemplate.opsForHash()
                .entries(RedisOtpStore.verificationKey(userId));
        assertThat(redisState)
                .containsEntry(RedisOtpStore.OTP_HASH_FIELD, "hmac-protected-value")
                .containsEntry(RedisOtpStore.ATTEMPTS_FIELD, "0")
                .doesNotContainValue("123456");
        assertThat(redisTemplate.getExpire(
                RedisOtpStore.verificationKey(userId), TimeUnit.MILLISECONDS))
                .isBetween(1_000L, 5_000L);
        assertThat(redisTemplate.getExpire(
                RedisOtpStore.cooldownKey(userId), TimeUnit.MILLISECONDS))
                .isBetween(1_000L, 2_000L);
        assertThat(store.getVerificationOtp(userId)).hasValueSatisfying(state -> {
            assertThat(state.getProtectedOtpHash()).isEqualTo("hmac-protected-value");
            assertThat(state.getAttempts()).isZero();
            assertThat(state.getRemainingTtl().isNegative()).isFalse();
            assertThat(state.getRemainingTtl().isZero()).isFalse();
        });

        assertThat(store.incrementAttempts(userId)).isEqualTo(1);
        assertThat(store.getVerificationOtp(userId)).hasValueSatisfying(state ->
                assertThat(state.getAttempts()).isEqualTo(1));

        Awaitility.await().atMost(Duration.ofSeconds(7)).untilAsserted(() ->
                assertThat(store.getVerificationOtp(userId)).isEmpty());
    }

    @Test
    void otpServiceStoresHmacProtectionInsteadOfRawCodeInRedis() {
        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setTtl(Duration.ofSeconds(30));
        properties.setResendCooldown(Duration.ofSeconds(2));
        properties.setMaxAttempts(5);
        properties.setHashSecret("test-only-otp-hash-secret");
        OtpService service = new OtpService(store, properties);
        UUID userId = UUID.randomUUID();

        String rawOtp = service.issueVerificationOtp(userId).getOtp();

        Map<Object, Object> redisState = redisTemplate.opsForHash()
                .entries(RedisOtpStore.verificationKey(userId));
        assertThat(redisState.get(RedisOtpStore.OTP_HASH_FIELD))
                .isNotEqualTo(rawOtp)
                .asString()
                .hasSize(64);
        assertThat(redisState.get(RedisOtpStore.ATTEMPTS_FIELD)).isEqualTo("0");

        service.verify(userId, rawOtp);
        assertThat(store.getVerificationOtp(userId)).isEmpty();
    }

    @Test
    void replacementIsAtomicWithCooldownAndResetsAttempts() {
        UUID userId = UUID.randomUUID();
        store.saveVerificationOtp(userId, "first", Duration.ofSeconds(5), Duration.ofSeconds(1));
        store.incrementAttempts(userId);

        assertThat(store.isResendCooldownActive(userId)).isTrue();
        assertThat(store.replaceVerificationOtp(userId, "second", Duration.ofSeconds(5), Duration.ofSeconds(1)))
                .isFalse();
        assertThat(store.getVerificationOtp(userId)).hasValueSatisfying(state -> {
            assertThat(state.getProtectedOtpHash()).isEqualTo("first");
            assertThat(state.getAttempts()).isEqualTo(1);
        });

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(store.isResendCooldownActive(userId)).isFalse());
        assertThat(store.replaceVerificationOtp(userId, "second", Duration.ofSeconds(5), Duration.ofSeconds(1)))
                .isTrue();
        assertThat(store.getVerificationOtp(userId)).hasValueSatisfying(state -> {
            assertThat(state.getProtectedOtpHash()).isEqualTo("second");
            assertThat(state.getAttempts()).isZero();
        });
    }

    @Test
    void deleteRemovesBothOtpStateAndCooldown() {
        UUID userId = UUID.randomUUID();
        store.saveVerificationOtp(userId, "protected", Duration.ofSeconds(30), Duration.ofSeconds(30));

        store.deleteVerificationOtp(userId);

        assertThat(redisTemplate.hasKey(RedisOtpStore.verificationKey(userId))).isFalse();
        assertThat(redisTemplate.hasKey(RedisOtpStore.cooldownKey(userId))).isFalse();
        assertThat(store.getVerificationOtp(userId)).isEmpty();
    }

    @Test
    void redisConnectivityFailureMapsToOtpServiceUnavailableWithoutProviderDetails() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }

        RedisProperties properties = new RedisProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(unavailablePort);
        properties.setTimeout(Duration.ofMillis(200));
        LettuceConnectionFactory unavailableFactory = new RedisConfig()
                .redisConnectionFactory(properties);
        unavailableFactory.afterPropertiesSet();
        try {
            RedisOtpStore unavailableStore = new RedisOtpStore(new StringRedisTemplate(unavailableFactory));

            assertThatThrownBy(() -> unavailableStore.isResendCooldownActive(UUID.randomUUID()))
                    .isInstanceOf(OtpServiceUnavailableException.class)
                    .extracting(exception -> ((OtpServiceUnavailableException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_SERVICE_UNAVAILABLE);
        } finally {
            unavailableFactory.destroy();
        }
    }
}
