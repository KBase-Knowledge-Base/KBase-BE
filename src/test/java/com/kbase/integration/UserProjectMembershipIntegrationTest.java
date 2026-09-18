package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.kbase.document.entity.Document;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.mail.service.MailService;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
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
 * User, project and membership authorization matrix against a real filter
 * chain on PostgreSQL. Invitations do not exist yet (M8), so extra
 * memberships are seeded directly through the repository.
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
class UserProjectMembershipIntegrationTest {

    private static final String PASSWORD = "ExamplePassword123";
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
    private DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    /** Registers, verifies and logs in a fresh user via public auth APIs. */
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
        String otp = otpCaptor.getValue();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
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
                login.getResponse().getCookie(REFRESH_COOKIE));
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

    private UUID createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Integration project"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    private ProjectMember seedMembership(Project project, User user, ProjectRole role) {
        return projectMemberRepository.save(new ProjectMember(project, user, role));
    }

    private Document seedDocument(Project project, User uploader) {
        return documentRepository.save(new Document(
                project, uploader, "spec.pdf", "spec.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 1024,
                "projects/%s/documents/%s.pdf".formatted(project.getId(), UUID.randomUUID())));
    }

    @Test
    void projectCreationMakesCreatorSingleOwnerWithinOneTransaction() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token(), "Owner Project " + UUID.randomUUID());

        var memberships = projectMemberRepository.findAllByProjectId(
                projectId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(memberships.getContent()).hasSize(1);
        assertThat(memberships.getContent().get(0).getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(memberships.getContent().get(0).getUser().getId())
                .isEqualTo(owner.user().getId());

        mockMvc.perform(get("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currentUserRole").value("OWNER"));

        mockMvc.perform(get("/api/v1/projects?role=MEMBER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void projectAccessMatrixEnforcesMemberOwnerAndAdminOverrides() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        VerifiedUser outsider = newVerifiedUser();
        AdminAccount admin = newAdmin();

        UUID projectId = createProject(owner.token(), "Matrix Project " + UUID.randomUUID());
        Project project = projectRepository.findById(projectId).orElseThrow();
        seedMembership(project, member.user(), ProjectRole.MEMBER);

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserRole").value("MEMBER"));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacked Name"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGEMENT_FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Project","description":"Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Project"));

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Admin updated"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserRole")
                        .value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/projects/" + projectId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membershipRemovalAndLeavePreserveUploadedDocuments() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        VerifiedUser outsider = newVerifiedUser();
        AdminAccount admin = newAdmin();

        UUID projectId = createProject(owner.token(), "Membership Project " + UUID.randomUUID());
        Project project = projectRepository.findById(projectId).orElseThrow();
        seedMembership(project, member.user(), ProjectRole.MEMBER);
        Document uploadedByMember = seedDocument(project, member.user());

        // MEMBER cannot remove other members.
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/"
                        + outsider.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGEMENT_FORBIDDEN"));

        // Removing an unknown membership maps to 404.
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/"
                        + outsider.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_NOT_FOUND"));

        // The project OWNER cannot be removed, even by an ADMIN override.
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/"
                        + owner.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_OWNER_REMOVAL_FORBIDDEN"));

        // OWNER removes the MEMBER; the uploaded document stays.
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/"
                        + member.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());
        assertThat(documentRepository.findById(uploadedByMember.getId())).isPresent();
        assertThat(uploadedByMember.getUploadedBy().getId()).isEqualTo(member.user().getId());

        // Former member now loses project access.
        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));

        // MEMBER can leave; OWNER cannot; documents remain.
        seedMembership(project, member.user(), ProjectRole.MEMBER);
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isNoContent());
        assertThat(documentRepository.findById(uploadedByMember.getId())).isPresent();

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE_PROJECT"));

        // The document row keeps referencing the User, never a ProjectMember.
        Document reloaded = documentRepository.findById(uploadedByMember.getId()).orElseThrow();
        assertThat(reloaded.getUploadedBy().getId()).isEqualTo(member.user().getId());
    }

    @Test
    void currentUserProfileAndPasswordBehaviors() throws Exception {
        VerifiedUser user = newVerifiedUser();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.user().getEmail()))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Renamed User"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed User"))
                .andExpect(jsonPath("$.email").value(user.user().getEmail()));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-current","newPassword":"NewPassword123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NewPassword123"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        // Password change revoked the existing refresh session.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_SESSION_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPassword123"}
                                """.formatted(user.user().getEmail())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(user.user().getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void adminUserManagementAppliesStatusAndDependencyRules() throws Exception {
        VerifiedUser target = newVerifiedUser();
        VerifiedUser regular = newVerifiedUser();
        AdminAccount admin = newAdmin();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/v1/admin/users?q=" + target.user().getEmail())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/admin/users/" + target.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(target.user().getEmail()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(patch("/api/v1/admin/users/" + target.user().getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_STATUS"));

        // Disable revokes sessions: refresh of the target fails with the
        // revoked-session contract (the user-status 403 path is covered when
        // sessions are not revoked first).
        mockMvc.perform(patch("/api/v1/admin/users/" + target.user().getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(target.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_SESSION_REVOKED"));

        // Dependency rules in order: clean delete works; OWNER and MEMBER blocked.
        mockMvc.perform(delete("/api/v1/admin/users/" + target.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/users/" + target.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token(), "Owned " + UUID.randomUUID());
        mockMvc.perform(delete("/api/v1/admin/users/" + owner.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_OWNS_PROJECT"));

        Project project = projectRepository.findById(projectId).orElseThrow();
        seedMembership(project, regular.user(), ProjectRole.MEMBER);
        mockMvc.perform(delete("/api/v1/admin/users/" + regular.user().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_DEPENDENCIES"));
    }

    @Test
    void adminProjectListingShowsAllProjectsWithoutFakeRoles() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        createProject(owner.token(), "Admin Listed " + UUID.randomUUID());
        AdminAccount admin = newAdmin();

        mockMvc.perform(get("/api/v1/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].currentUserRole")
                        .value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isForbidden());
    }
}
