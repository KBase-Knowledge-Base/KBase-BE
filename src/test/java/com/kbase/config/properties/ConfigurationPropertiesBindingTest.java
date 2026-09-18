package com.kbase.config.properties;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "kbase.postgres.host=postgres",
                    "kbase.postgres.port=5432",
                    "kbase.postgres.database=kbase_test",
                    "kbase.postgres.username=integration-user",
                    "kbase.postgres.password=integration-password",
                    "kbase.jwt.signing-secret=test-jwt-signing-secret",
                    "kbase.jwt.algorithm=HS256",
                    "kbase.jwt.access-token-ttl=20m",
                    "kbase.jwt.refresh-token-ttl=14d",
                    "kbase.refresh-cookie.name=test_refresh",
                    "kbase.refresh-cookie.secure=false",
                    "kbase.refresh-cookie.same-site=Strict",
                    "kbase.refresh-cookie.max-age=14d",
                    "kbase.otp.length=6",
                    "kbase.otp.ttl=5m",
                    "kbase.otp.resend-cooldown=60s",
                    "kbase.otp.max-attempts=5",
                    "kbase.otp.hash-secret=test-otp-hash-secret",
                    "kbase.redis.host=redis",
                    "kbase.redis.port=6379",
                    "kbase.redis.password=",
                    "kbase.redis.timeout=3s",
                    "kbase.mail.host=smtp.gmail.com",
                    "kbase.mail.port=587",
                    "kbase.mail.username=test@example.invalid",
                    "kbase.mail.app-password=test-mail-app-password",
                    "kbase.mail.auth=true",
                    "kbase.mail.start-tls=true",
                    "kbase.mail.timeout=12s",
                    "kbase.invitation.expiration=72h",
                    "kbase.upload.document-max-size=64MB",
                    "kbase.upload.image-max-size=24MB",
                    "kbase.upload.video-max-size=512MB",
                    "kbase.upload.max-batch-files=8",
                    "kbase.storage.endpoint=http://minio:9000",
                    "kbase.storage.access-key=test-access-key",
                    "kbase.storage.secret-key=test-storage-secret-key",
                    "kbase.storage.bucket=test-documents",
                    "kbase.storage.region=us-east-1",
                    "kbase.storage.auto-create-bucket=true",
                    "kbase.storage.initialize-on-startup=true",
                    "kbase.storage.connect-timeout=2s",
                    "kbase.storage.write-timeout=20s",
                    "kbase.storage.read-timeout=25s",
                    "kbase.cors.allowed-origins=https://app.example.invalid,https://admin.example.invalid",
                    "kbase.cors.allow-credentials=true",
                    "kbase.openapi.enabled=true",
                    "kbase.openapi.swagger-ui-enabled=false",
                    "kbase.openapi.api-docs-path=/v3/api-docs",
                    "kbase.openapi.swagger-ui-path=/swagger-ui.html");

    @Test
    void bindsTypedCorePropertiesWithoutStartingExternalInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            PostgresProperties postgres = context.getBean(PostgresProperties.class);
            assertThat(postgres.getHost()).isEqualTo("postgres");
            assertThat(postgres.getPort()).isEqualTo(5432);
            assertThat(postgres.getDatabase()).isEqualTo("kbase_test");

            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.getSigningSecret()).isEqualTo("test-jwt-signing-secret");
            assertThat(jwt.getAlgorithm()).isEqualTo("HS256");
            assertThat(jwt.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(20));
            assertThat(jwt.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(14));

            RefreshCookieProperties cookie = context.getBean(RefreshCookieProperties.class);
            assertThat(cookie.getName()).isEqualTo("test_refresh");
            assertThat(cookie.isSecure()).isFalse();
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));

            OtpProperties otp = context.getBean(OtpProperties.class);
            assertThat(otp.getLength()).isEqualTo(6);
            assertThat(otp.getTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(otp.getResendCooldown()).isEqualTo(Duration.ofSeconds(60));
            assertThat(otp.getMaxAttempts()).isEqualTo(5);
            assertThat(otp.getHashSecret()).isEqualTo("test-otp-hash-secret");

            RedisProperties redis = context.getBean(RedisProperties.class);
            assertThat(redis.getHost()).isEqualTo("redis");
            assertThat(redis.getPort()).isEqualTo(6379);
            assertThat(redis.getTimeout()).isEqualTo(Duration.ofSeconds(3));

            MailProperties mail = context.getBean(MailProperties.class);
            assertThat(mail.getHost()).isEqualTo("smtp.gmail.com");
            assertThat(mail.getPort()).isEqualTo(587);
            assertThat(mail.isAuth()).isTrue();
            assertThat(mail.isStartTls()).isTrue();
            assertThat(mail.getTimeout()).isEqualTo(Duration.ofSeconds(12));

            assertThat(context.getBean(InvitationProperties.class).getExpiration())
                    .isEqualTo(Duration.ofHours(72));

            UploadProperties upload = context.getBean(UploadProperties.class);
            assertThat(upload.getDocumentMaxSize()).isEqualTo(DataSize.ofMegabytes(64));
            assertThat(upload.getImageMaxSize()).isEqualTo(DataSize.ofMegabytes(24));
            assertThat(upload.getVideoMaxSize()).isEqualTo(DataSize.ofMegabytes(512));
            assertThat(upload.getMaxBatchFiles()).isEqualTo(8);

            StorageProperties storage = context.getBean(StorageProperties.class);
            assertThat(storage.getEndpoint()).isEqualTo("http://minio:9000");
            assertThat(storage.getBucket()).isEqualTo("test-documents");
            assertThat(storage.getRegion()).isEqualTo("us-east-1");
            assertThat(storage.isAutoCreateBucket()).isTrue();
            assertThat(storage.isInitializeOnStartup()).isTrue();
            assertThat(storage.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(storage.getWriteTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(storage.getReadTimeout()).isEqualTo(Duration.ofSeconds(25));

            CorsProperties cors = context.getBean(CorsProperties.class);
            assertThat(cors.getAllowedOrigins())
                    .containsExactly("https://app.example.invalid", "https://admin.example.invalid");
            assertThat(cors.isAllowCredentials()).isTrue();

            OpenApiProperties openApi = context.getBean(OpenApiProperties.class);
            assertThat(openApi.isEnabled()).isTrue();
            assertThat(openApi.isSwaggerUiEnabled()).isFalse();
            assertThat(openApi.getApiDocsPath()).isEqualTo("/v3/api-docs");
            assertThat(openApi.getSwaggerUiPath()).isEqualTo("/swagger-ui.html");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            PostgresProperties.class,
            JwtProperties.class,
            RefreshCookieProperties.class,
            OtpProperties.class,
            RedisProperties.class,
            MailProperties.class,
            InvitationProperties.class,
            UploadProperties.class,
            StorageProperties.class,
            CorsProperties.class,
            OpenApiProperties.class
    })
    static class PropertiesConfiguration {
    }
}
