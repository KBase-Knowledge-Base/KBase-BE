package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.kbase.invitation.entity.ProjectInvitation;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.invitation.service.InvitationService;
import com.kbase.mail.service.MailService;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Invitation lifecycle against a real filter chain on PostgreSQL with the
 * Gmail mail service mocked. Acceptance uses a real pessimistic lock, so the
 * concurrency test runs two parallel service calls against the database.
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
        "kbase.otp.hash-secret=integration-test-otp-hash-secret",
        "kbase.mail.username=it@example.invalid",
        "kbase.mail.app-password=it-mail-app-password",
        "kbase.storage.access-key=it-access-key",
        "kbase.storage.secret-key=it-storage-secret-key"
})
class InvitationLifecycleIntegrationTest {

    private static final String PASSWORD = "ExamplePassword123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(com.kbase.integration.support.PostgresTestSupport.IMAGE);

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MailService mailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectInvitationRepository invitationRepository;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private record VerifiedUser(User user, String token, Cookie refreshCookie) {
    }

    private record AdminAccount(User user, String token) {
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode json(MvcResult result) throws java.io.IOException {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private VerifiedUser newVerifiedUser() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"User %s"}
                                """.formatted(email, PASSWORD, email.substring(5, 13))))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendEmailVerificationOtp(org.mockito.ArgumentMatchers.anyString(),
                        otpCaptor.capture(), org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otpCaptor.getValue())))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new VerifiedUser(
                userRepository.findByEmail(email).orElseThrow(),
                json(login).get("accessToken").asString(),
                login.getResponse().getCookie("kbase_refresh_token"));
    }

    private AdminAccount newAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        User admin = new User(email, passwordEncoder.encode("AdminPassword123"),
                "Admin User", SystemRole.ADMIN, UserStatus.ACTIVE);
        admin.setEmailVerifiedAt(java.time.Instant.now());
        admin = userRepository.save(admin);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"AdminPassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new AdminAccount(admin, json(login).get("accessToken").asString());
    }

    private UUID createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invitation Project %s","description":"Integration"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    /** Creates an invitation and returns the raw token captured from the mail mock. */
    private String createInvitationAndCaptureToken(String ownerToken, UUID projectId, String email)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendProjectInvitation(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        urlCaptor.capture(),
                        org.mockito.ArgumentMatchers.any(java.time.Instant.class));
        String url = urlCaptor.getValue();
        assertThat(url).startsWith("http://localhost:3000/invitations/accept?token=");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    @Test
    void ownerCreatesInvitationWithGuardsAndMailDelivery() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        AdminAccount admin = newAdmin();
        UUID projectId = createProject(owner.token());
        Project project = projectRepository.findById(projectId).orElseThrow();
        projectMemberRepository.save(new ProjectMember(project, member.user(), ProjectRole.MEMBER));

        // MEMBER cannot create; ADMIN override can.
        String invitedEmail = uniqueEmail();
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(invitedEmail)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGEMENT_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").exists());

        // Duplicate PENDING for the same project+email → conflict.
        String firstToken = createInvitationAndCaptureToken(owner.token(), projectId, invitedEmail);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(invitedEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));

        // Inviting an existing member email → conflict.
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(member.user().getEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ALREADY_EXISTS"));

        // Raw token is not exposed: DB stores the hash only.
        MvcResult list = mockMvc.perform(get("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andReturn();
        assertThat(bodyOf(list)).doesNotContain(firstToken);
        ProjectInvitation stored = invitationRepository
                .findAllByProjectIdAndStatus(projectId, InvitationStatus.PENDING,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .filter(invitation -> invitation.getEmail().equals(invitedEmail))
                .findFirst().orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(firstToken);
    }

    private String bodyOf(MvcResult result) throws java.io.IOException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void resendInvalidatesOldTokenResetsExpiryAndDeliversNewLink() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        String invitedEmail = uniqueEmail();
        String oldToken = createInvitationAndCaptureToken(owner.token(), projectId, invitedEmail);
        UUID invitationId = invitationRepository
                .findAllByProjectIdAndStatus(projectId, InvitationStatus.PENDING,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .filter(invitation -> invitation.getEmail().equals(invitedEmail))
                .findFirst().orElseThrow().getId();
        java.time.Instant expiryBefore = invitationRepository.findById(invitationId)
                .orElseThrow().getExpiresAt();

        Thread.sleep(5);
        MvcResult resend = mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations/"
                        + invitationId + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        assertThat(bodyOf(resend)).doesNotContain(oldToken);

        ProjectInvitation reloaded = invitationRepository.findById(invitationId).orElseThrow();
        assertThat(reloaded.getExpiresAt()).isAfter(expiryBefore);

        // Old token no longer resolves; new token accepts.
        VerifiedUser invited = newVerifiedUserForEmail(invitedEmail);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(oldToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendProjectInvitation(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        urlCaptor.capture(),
                        org.mockito.ArgumentMatchers.any(java.time.Instant.class));
        List<String> urls = urlCaptor.getAllValues();
        String newToken = urls.get(urls.size() - 1)
                .substring(urls.get(urls.size() - 1).indexOf("token=") + "token=".length());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    /** Creates a verified, logged-in user for a fixed email address. */
    private VerifiedUser newVerifiedUserForEmail(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Invited"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendEmailVerificationOtp(org.mockito.ArgumentMatchers.anyString(),
                        otpCaptor.capture(), org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otpCaptor.getValue())))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new VerifiedUser(
                userRepository.findByEmail(email).orElseThrow(),
                json(login).get("accessToken").asString(),
                login.getResponse().getCookie("kbase_refresh_token"));
    }

    @Test
    void cancelExpiredMismatchAndDoubleAcceptAreRejected() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());

        // Cancel: PENDING → CANCELLED, no physical delete, token unusable.
        String email = uniqueEmail();
        String token = createInvitationAndCaptureToken(owner.token(), projectId, email);
        UUID invitationId = invitationRepository
                .findAllByProjectIdAndStatus(projectId, InvitationStatus.PENDING,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .filter(invitation -> invitation.getEmail().equals(email))
                .findFirst().orElseThrow().getId();
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/invitations/" + invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());
        assertThat(invitationRepository.findById(invitationId))
                .hasValueSatisfying(invitation ->
                        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED));

        VerifiedUser cancelTarget = newVerifiedUserForEmail(email);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cancelTarget.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));

        // Expired invitation: created via API, then backdated with raw SQL
        // because created_at is updatable=false on the entity and the schema
        // check (expires_at > created_at) applies on UPDATE as well.
        VerifiedUser lateUser = newVerifiedUser();
        String pendingEmailLate = uniqueEmail();
        String expiredToken = createInvitationAndCaptureToken(owner.token(), projectId, pendingEmailLate);
        UUID expiredId = invitationRepository
                .findAllByProjectIdAndStatus(projectId, InvitationStatus.PENDING,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .filter(invitation -> invitation.getEmail().equals(pendingEmailLate))
                .findFirst().orElseThrow().getId();
        jdbcTemplate.update(
                "update project_invitations set created_at = ?, expires_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(7_200)),
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3_600)),
                expiredId);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(lateUser.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(expiredToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
        // The EXPIRED transition must survive the rollback boundary so the
        // stored lifecycle matches the status model and the pending-invitation
        // uniqueness slot is freed for a replacement invitation.
        assertThat(invitationRepository.findById(expiredId))
                .hasValueSatisfying(invitation ->
                        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED));
        String replacementToken = createInvitationAndCaptureToken(owner.token(), projectId, pendingEmailLate);
        assertThat(replacementToken).isNotEqualTo(expiredToken);

        // Email mismatch → 403; wrong token → 404.
        VerifiedUser outsider = newVerifiedUser();
        String pendingEmail = uniqueEmail();
        String pendingToken = createInvitationAndCaptureToken(owner.token(), projectId, pendingEmail);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(pendingToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));

        // Successful accept: one MEMBER, ACCEPTED status, acceptedAt set.
        VerifiedUser invited = newVerifiedUserForEmail(pendingEmail);
        MvcResult accepted = mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(pendingToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinedAt").exists())
                .andReturn();
        UUID membershipId = UUID.fromString(json(accepted).get("membershipId").asString());
        assertThat(projectMemberRepository.findById(membershipId))
                .hasValueSatisfying(membership -> {
                    assertThat(membership.getRole()).isEqualTo(ProjectRole.MEMBER);
                    assertThat(membership.getUser().getId()).isEqualTo(invited.user().getId());
                });

        // Double accept → INVITATION_NOT_PENDING; membership count unchanged.
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(pendingToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        assertThat(projectMemberRepository.findAllByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2); // owner (OWNER) + invited member
    }

    @Test
    void mailFailureRollsBackInvitationRow() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        String email = uniqueEmail();

        org.mockito.Mockito.doThrow(new com.kbase.shared.exception.MailServiceUnavailableException())
                .when(mailService).sendProjectInvitation(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.time.Instant.class));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SERVICE_UNAVAILABLE"));

        assertThat(invitationRepository.existsByProjectIdAndEmailAndStatus(
                projectId, email, InvitationStatus.PENDING)).isFalse();
    }

    @Test
    void concurrentAcceptsProduceExactlyOneMembership() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        String invitedEmail = uniqueEmail();
        String token = createInvitationAndCaptureToken(owner.token(), projectId, invitedEmail);
        VerifiedUser invited = newVerifiedUserForEmail(invitedEmail);

        CustomUserPrincipal invitedPrincipal = new CustomUserPrincipal(
                invited.user().getId(), invited.user().getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);

        // Two truly concurrent accepts race on the pessimistic invitation lock.
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<java.util.Optional<ErrorCode>>> outcomes = new java.util.ArrayList<>();
            for (int index = 0; index < threads; index++) {
                outcomes.add(executor.submit(() -> {
                    start.await();
                    try {
                        invitationService.accept(token, invitedPrincipal);
                        return java.util.Optional.<ErrorCode>empty();
                    } catch (com.kbase.shared.exception.BusinessException exception) {
                        return java.util.Optional.of(exception.getErrorCode());
                    }
                }));
            }
            start.countDown();

            int successes = 0;
            var errors = new java.util.ArrayList<ErrorCode>();
            for (Future<java.util.Optional<ErrorCode>> outcome : outcomes) {
                var result = outcome.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (result.isEmpty()) {
                    successes++;
                } else {
                    errors.add(result.get());
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(errors).containsExactly(ErrorCode.INVITATION_NOT_PENDING);
            assertThat(projectMemberRepository.existsByProjectIdAndUserId(
                    projectId, invited.user().getId())).isTrue();

            ProjectInvitation invitation = invitationRepository
                    .findAllByProjectIdAndStatus(projectId, InvitationStatus.ACCEPTED,
                            org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent().stream().findFirst().orElseThrow();
            assertThat(invitation.getAcceptedAt()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCreateOfSamePendingInvitationFlushesConstraintBeforeMail() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        String invitedEmail = uniqueEmail();

        CustomUserPrincipal ownerPrincipal = new CustomUserPrincipal(
                owner.user().getId(), owner.user().getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);

        // Deterministic race: the winning transaction parks inside its mail
        // call (uncommitted) so the loser's exists-check runs against hidden
        // state and its flush must reject on the partial unique index BEFORE
        // any second mail side effect.
        CountDownLatch winnerMailEntered = new CountDownLatch(1);
        CountDownLatch winnerMailRelease = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            winnerMailEntered.countDown();
            winnerMailRelease.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).when(mailService).sendProjectInvitation(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<java.util.Optional<String>>> outcomes = new java.util.ArrayList<>();
            for (int index = 0; index < threads; index++) {
                outcomes.add(executor.submit(() -> {
                    start.await();
                    try {
                        invitationService.createInvitation(projectId,
                                new com.kbase.invitation.dto.request.CreateInvitationRequest(invitedEmail),
                                ownerPrincipal);
                        return java.util.Optional.<String>empty();
                    } catch (com.kbase.shared.exception.BusinessException exception) {
                        return java.util.Optional.of(exception.getErrorCode().name());
                    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                        return java.util.Optional.of("DATA_INTEGRITY_VIOLATION");
                    }
                }));
            }
            start.countDown();

            // The winner is parked inside mail with its row uncommitted; only
            // now can the loser's flush be waiting on the unique index.
            assertThat(winnerMailEntered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            winnerMailRelease.countDown();

            int successes = 0;
            var conflicts = new java.util.ArrayList<String>();
            for (Future<java.util.Optional<String>> outcome : outcomes) {
                var result = outcome.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (result.isEmpty()) {
                    successes++;
                } else {
                    conflicts.add(result.get());
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst()).isIn("INVITATION_ALREADY_PENDING", "DATA_INTEGRITY_VIOLATION");

            // Exactly one committed PENDING row and exactly one invitation mail:
            // the loser transaction failed at flush before sending anything.
            long pendingRows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM project_invitations WHERE project_id = ? AND email = ? AND status = 'PENDING'",
                    Long.class, projectId, invitedEmail);
            assertThat(pendingRows).isEqualTo(1);
            org.mockito.Mockito.verify(mailService, org.mockito.Mockito.times(1))
                    .sendProjectInvitation(
                            org.mockito.ArgumentMatchers.eq(invitedEmail),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        } finally {
            winnerMailRelease.countDown();
            executor.shutdownNow();
        }
    }
}
