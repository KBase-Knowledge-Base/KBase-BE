package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kbase.auth.port.OtpStore;
import com.kbase.auth.repository.RefreshSessionRepository;
import com.kbase.mail.service.MailService;
import com.kbase.shared.exception.MailServiceUnavailableException;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end authentication and security contract against a real filter
 * chain with PostgreSQL and Redis Testcontainers. Mail delivery is mocked;
 * no real Gmail is contacted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "kbase.postgres.username=it-user",
        "kbase.postgres.password=it-password",
        "kbase.jwt.signing-secret=integration-test-jwt-signing-secret-256-bits!",
        "kbase.jwt.access-token-ttl=15m",
        "kbase.jwt.refresh-token-ttl=7d",
        "kbase.otp.length=6",
        "kbase.otp.ttl=5m",
        "kbase.otp.resend-cooldown=2s",
        "kbase.otp.max-attempts=5",
        "kbase.otp.hash-secret=integration-test-otp-hash-secret",
        "kbase.mail.username=it@example.invalid",
        "kbase.mail.app-password=it-mail-app-password",
        "kbase.storage.access-key=it-access-key",
        "kbase.storage.secret-key=it-storage-secret-key"
})
class AuthenticationSecurityIntegrationTest {

    private static final String REFRESH_COOKIE = "kbase_refresh_token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("kbase.redis.host", REDIS::getHost);
        registry.add("kbase.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @RestController
        static class ProbeController {
            @GetMapping("/api/v1/test/protected")
            String protectedProbe() {
                return "protected-ok";
            }

            @GetMapping("/api/v1/admin/ping")
            String adminPing() {
                return "admin-ok";
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private OtpStore otpStore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailService mailService;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String body(MvcResult result) throws java.io.IOException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** Registers a new user and returns the OTP delivered to MailService. */
    private String registerAndCaptureOtp(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Example User"}
                                """.formatted(email, DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendEmailVerificationOtp(anyString(), otpCaptor.capture(), any());
        String otp = otpCaptor.getAllValues().get(otpCaptor.getAllValues().size() - 1);
        assertThat(otp).matches(Pattern.compile("\\d{6}"));
        return otp;
    }

    /** Logs in an existing verified account and returns the access token. */
    private String loginAccessToken(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return Json.parse(objectMapper, body(login)).get("accessToken").asString();
    }

    private void verifyEmail(String email, String otp) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    /** Registers, verifies and logs in; returns the access token. */
    private String accessTokenForVerifiedUser(String email) throws Exception {
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);
        return loginAccessToken(email, DEFAULT_PASSWORD);
    }

    private static final String DEFAULT_PASSWORD = "ExamplePassword123";

    private String registerVerifiedAndLogin() throws Exception {
        String email = uniqueEmail();
        return accessTokenForVerifiedUser(email);
    }

    @Test
    void registerCreatesUnverifiedAccountAndIssuesOtpThroughMailService() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(passwordEncoder.matches("ExamplePassword123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).doesNotContain("ExamplePassword123");
        assertThat(otpStore.getVerificationOtp(user.getId())).isPresent();

        // Registration never auto-logs in and never returns tokens or OTP.
        // (Response shape asserted in registerAndCaptureOtp.)
        assertThat(otp).hasSize(6);
    }

    @Test
    void registerRejectsDuplicateEmailAndInvalidPayload() throws Exception {
        String email = uniqueEmail();
        registerAndCaptureOtp(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"AnotherPassword123","displayName":"Other"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(header().exists("X-Request-Id"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short@example.com","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void registerRollsBackAndMapsServiceUnavailableWhenMailFails() throws Exception {
        String email = uniqueEmail();
        org.mockito.Mockito.doThrow(new MailServiceUnavailableException())
                .when(mailService).sendEmailVerificationOtp(anyString(), anyString(), any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123","displayName":"Example User"}
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SERVICE_UNAVAILABLE"));

        assertThat(userRepository.existsByEmail(email)).isFalse();
        org.mockito.Mockito.clearInvocations(mailService);
    }

    @Test
    void verifyEmailMarksVerifiedPersistsTimestampAndInvalidatesOtpState() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        User user = userRepository.findByEmail(email).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true));

        assertThat(userRepository.findByEmail(email).orElseThrow().getEmailVerifiedAt()).isNotNull();
        assertThat(otpStore.getVerificationOtp(user.getId())).isEmpty();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void verifyEmailRejectsWrongExpiredAndExhaustedOtp() throws Exception {
        // Wrong OTP attempts exhaust to OTP_ATTEMPTS_EXCEEDED.
        String email = uniqueEmail();
        registerAndCaptureOtp(email);
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","otp":"000000"}
                                    """.formatted(email)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        }
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"000000"}
                                """.formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));

        // Expired OTP (state removed like a TTL expiry) maps to OTP_EXPIRED.
        String expiredEmail = uniqueEmail();
        registerAndCaptureOtp(expiredEmail);
        User expiredUser = userRepository.findByEmail(expiredEmail).orElseThrow();
        otpStore.deleteVerificationOtp(expiredUser.getId());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"123456"}
                                """.formatted(expiredEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendObeyCooldownReplacesOldOtpAndResetsAttempts() throws Exception {
        String email = uniqueEmail();
        String firstOtp = registerAndCaptureOtp(email);

        // Resend inside the cooldown window is throttled.
        mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));

        // Resend after cooldown succeeds and replaces the old OTP.
        Thread.sleep(2_100);
        org.mockito.Mockito.clearInvocations(mailService);
        mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendEmailVerificationOtp(anyString(), otpCaptor.capture(), any());
        String secondOtp = otpCaptor.getValue();
        assertThat(secondOtp).isNotEqualTo(firstOtp);

        // Old OTP is no longer valid; new OTP verifies.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, firstOtp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        verifyEmail(email, secondOtp);
    }

    @Test
    void resendRejectsUnknownAndAlreadyVerifiedEmails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);
        mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void loginBeforeVerificationIsRejectedWithEmailNotVerified() throws Exception {
        String email = uniqueEmail();
        registerAndCaptureOtp(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void loginAfterVerificationReturnsAccessJwtAndHttpOnlyRefreshCookie() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andExpect(jsonPath("$.user.systemRole").value("USER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andReturn();

        String responseBody = body(login).toLowerCase();
        assertThat(responseBody).doesNotContain("refresh");

        String accessToken = Json.parse(objectMapper, body(login)).get("accessToken").asText();
        assertThat(accessToken.split("\\.")).hasSize(3);

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains(REFRESH_COOKIE + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(setCookie).contains("Max-Age=604800");
        String rawRefreshCookie = extractCookieValue(setCookie);

        // Only the SHA-256 hash of the cookie value is persisted.
        var sessions = refreshSessionRepository.findAllByUserId(
                userRepository.findByEmail(email).orElseThrow().getId());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getTokenHash())
                .isEqualTo(sha256Hex(rawRefreshCookie))
                .isNotEqualTo(rawRefreshCookie);
        assertThat(sessions.get(0).getRevokedAt()).isNull();

        // The access JWT carries only the minimal claims.
        JsonNode claims = decodeJwtClaims(objectMapper, accessToken);
        assertThat(claims.properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("sub", "systemRole", "iat", "exp", "jti");
        assertThat(claims.get("systemRole").asString()).isEqualTo("USER");
    }

    @Test
    void loginRejectsUnknownEmailAndWrongPasswordWithGenericError() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);

        for (String payload : new String[] {
                """
                {"email":"unknown-%s@example.com","password":"ExamplePassword123"}
                """.formatted(UUID.randomUUID()),
                """
                {"email":"%s","password":"WrongPassword123"}
                """.formatted(email)}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void refreshAndLogoutCycleThroughPostgreSqlSession() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = login.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refreshCookie).isNotNull();

        // Refresh issues a new working access token without rotation.
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
        String refreshedToken = Json.parse(objectMapper, body(refresh)).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(refreshedToken)))
                .andExpect(status().isOk());

        // Missing/invalid cookie values map to distinct 401 codes.
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Logout revokes the session and clears the cookie.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        var sessions = refreshSessionRepository.findAllByUserId(
                userRepository.findByEmail(email).orElseThrow().getId());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getRevokedAt()).isNotNull();

        // Revoked session can no longer refresh.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_SESSION_REVOKED"));

        // Logout without a cookie is effectively idempotent.
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void protectedEndpointsHandleMissingValidExpiredAndTamperedTokens() throws Exception {
        String accessToken = registerVerifiedAndLogin();

        // Missing token on a protected endpoint.
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").exists());

        // Valid token authenticates.
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        // Expired token.
        String expiredToken = TestTokens.expired();
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));

        // Tampered signature.
        String tampered = accessToken.substring(0, accessToken.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        // Garbage bearer value.
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        // Public endpoints still work without any token.
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"));
    }

    @Test
    void adminRoutesRequireAdminSystemRole() throws Exception {
        String userEmail = uniqueEmail();
        String userToken = accessTokenForVerifiedUser(userEmail);

        mockMvc.perform(get("/api/v1/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String adminEmail = uniqueEmail();
        User admin = new User(
                adminEmail,
                passwordEncoder.encode("AdminPassword123"),
                "Admin User",
                SystemRole.ADMIN,
                UserStatus.ACTIVE);
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);

        String adminToken = loginAccessToken(adminEmail, "AdminPassword123");
        mockMvc.perform(get("/api/v1/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountIsBlockedWithOldAccessTokenRefreshAndLogin() throws Exception {
        String email = uniqueEmail();
        String otp = registerAndCaptureOtp(email);
        verifyEmail(email, otp);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = Json.parse(objectMapper, body(login)).get("accessToken").asText();
        Cookie refreshCookie = login.getResponse().getCookie(REFRESH_COOKIE);

        // Admin disable is simulated directly on the account row.
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        // Old, still-unexpired access token must stop working immediately.
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ExamplePassword123"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    private static String extractCookieValue(String setCookieHeader) {
        int start = setCookieHeader.indexOf('=') + 1;
        int end = setCookieHeader.indexOf(';', start);
        return end < 0 ? setCookieHeader.substring(start) : setCookieHeader.substring(start, end);
    }

    private static JsonNode decodeJwtClaims(ObjectMapper objectMapper, String token) throws Exception {
        String[] parts = token.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
    }

    /** Small helpers kept out of the test body for readability. */
    private static final class Json {
        static JsonNode parse(ObjectMapper objectMapper, String value) throws java.io.IOException {
            return objectMapper.readTree(value);
        }
    }

    /** Signs tokens with the integration test secret for expiration cases. */
    private static final class TestTokens {
        private static final io.jsonwebtoken.security.MacAlgorithm ALGORITHM = io.jsonwebtoken.Jwts.SIG.HS256;

        static String expired() {
            javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    "integration-test-jwt-signing-secret-256-bits!".getBytes(StandardCharsets.UTF_8));
            return io.jsonwebtoken.Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(UUID.randomUUID().toString())
                    .claim("systemRole", "USER")
                    .issuedAt(java.util.Date.from(Instant.now().minusSeconds(3_600)))
                    .expiration(java.util.Date.from(Instant.now().minusSeconds(1_800)))
                    .signWith(key, ALGORITHM)
                    .compact();
        }
    }
}
