package com.kbase.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.kbase.shared.exception.ConstraintViolationTranslator;
import com.kbase.shared.exception.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** ERR-06 verification against the real PostgreSQL constraints created by M2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class ConstraintViolationTranslationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("kbase.postgres.host", POSTGRES::getHost);
        registry.add("kbase.postgres.port", () -> POSTGRES.getFirstMappedPort());
        registry.add("kbase.postgres.database", POSTGRES::getDatabaseName);
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "test-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "test-otp-hash-secret");
        registry.add("kbase.mail.username", () -> "test@example.invalid");
        registry.add("kbase.mail.app-password", () -> "test-mail-app-password");
        registry.add("kbase.storage.access-key", () -> "test-access-key");
        registry.add("kbase.storage.secret-key", () -> "test-storage-secret-key");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConstraintViolationTranslator translator;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE "
                + "document_tags, documents, categories, folders, project_invitations, "
                + "project_members, refresh_sessions, tags, projects, users "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    void realPostgresUniqueConstraintsTranslateToDesignedCodes() {
        String duplicateEmail = "duplicate-" + UUID.randomUUID() + "@example.com";
        insertUser(duplicateEmail);
        assertConstraint(ErrorCode.EMAIL_ALREADY_EXISTS, () -> insertUser(duplicateEmail));

        UUID membershipUser = insertUser("membership-" + UUID.randomUUID() + "@example.com");
        UUID membershipProject = insertProject("membership-" + UUID.randomUUID());
        insertProjectMember(membershipProject, membershipUser, "MEMBER");
        assertConstraint(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS,
                () -> insertProjectMember(membershipProject, membershipUser, "MEMBER"));

        UUID firstOwner = insertUser("owner-one-" + UUID.randomUUID() + "@example.com");
        UUID secondOwner = insertUser("owner-two-" + UUID.randomUUID() + "@example.com");
        UUID ownerProject = insertProject("owner-" + UUID.randomUUID());
        insertProjectMember(ownerProject, firstOwner, "OWNER");
        assertConstraint(ErrorCode.PROJECT_OWNER_ALREADY_EXISTS,
                () -> insertProjectMember(ownerProject, secondOwner, "OWNER"));

        UUID inviter = insertUser("inviter-" + UUID.randomUUID() + "@example.com");
        UUID invitationProject = insertProject("invitation-" + UUID.randomUUID());
        String invitedEmail = "pending-" + UUID.randomUUID() + "@example.com";
        insertInvitation(invitationProject, inviter, invitedEmail, "token-hash-one-" + UUID.randomUUID());
        assertConstraint(ErrorCode.INVITATION_ALREADY_PENDING,
                () -> insertInvitation(invitationProject, inviter, invitedEmail,
                        "token-hash-two-" + UUID.randomUUID()));

        UUID folderProject = insertProject("folder-" + UUID.randomUUID());
        UUID rootFolder = insertFolder(folderProject, null, "Root");
        assertConstraint(ErrorCode.FOLDER_NAME_ALREADY_EXISTS,
                () -> insertFolder(folderProject, null, "root"));
        insertFolder(folderProject, rootFolder, "Child");
        assertConstraint(ErrorCode.FOLDER_NAME_ALREADY_EXISTS,
                () -> insertFolder(folderProject, rootFolder, "child"));

        UUID categoryProject = insertProject("category-" + UUID.randomUUID());
        insertCategory(categoryProject, "Security");
        assertConstraint(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                () -> insertCategory(categoryProject, "security"));

        UUID tagProject = insertProject("tag-" + UUID.randomUUID());
        insertTag(tagProject, "Java");
        assertConstraint(ErrorCode.TAG_NAME_ALREADY_EXISTS,
                () -> insertTag(tagProject, "java"));
    }

    @Test
    void aRealUnknownConstraintFallsBackToInternalError() {
        UUID projectId = insertProject("unknown-" + UUID.randomUUID());
        UUID otherProjectId = insertProject("unknown-other-" + UUID.randomUUID());
        UUID parent = insertFolder(otherProjectId, null, "Other root");

        assertConstraint(ErrorCode.INTERNAL_SERVER_ERROR,
                () -> insertFolder(projectId, parent, "cross-project"));
    }

    private void assertConstraint(ErrorCode expected, SqlAction action) {
        try {
            action.run();
        } catch (DataIntegrityViolationException exception) {
            assertThat(translator.extractConstraintName(exception)).isPresent();
            assertThat(translator.translate(exception)).isEqualTo(expected);
            return;
        }
        throw new AssertionError("Expected a PostgreSQL integrity violation for " + expected);
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, email, "test-password-hash", "Test User", "USER", "ACTIVE");
        return id;
    }

    private UUID insertProject(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private void insertProjectMember(UUID projectId, UUID userId, String role) {
        jdbcTemplate.update(
                "INSERT INTO project_members (id, project_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), projectId, userId, role);
    }

    private void insertInvitation(UUID projectId, UUID inviterId, String email, String tokenHash) {
        jdbcTemplate.update(
                "INSERT INTO project_invitations "
                        + "(id, project_id, invited_by_user_id, email, token_hash, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), projectId, inviterId, email, tokenHash, "PENDING",
                Timestamp.from(Instant.now().plusSeconds(3600)));
    }

    private UUID insertFolder(UUID projectId, UUID parentId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO folders (id, project_id, parent_id, name) VALUES (?, ?, ?, ?)",
                id, projectId, parentId, name);
        return id;
    }

    private void insertCategory(UUID projectId, String name) {
        jdbcTemplate.update(
                "INSERT INTO categories (id, project_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), projectId, name);
    }

    private void insertTag(UUID projectId, String name) {
        jdbcTemplate.update(
                "INSERT INTO tags (id, project_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), projectId, name);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run();
    }
}
