package com.kbase.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-05 migration integrity test (KBase Core v1 M2).
 *
 * Boots the real application context against a fresh PostgreSQL Testcontainer so that:
 * - Flyway applies every migration from an empty database,
 * - Hibernate runs with ddl-auto=validate against the migrated schema,
 * - the expected tables, constraint names, partial/expression indexes exist,
 * - the PostgreSQL-specific semantics (partial unique index, LOWER() expression index,
 *   composite same-project foreign keys) actually behave as designed.
 *
 * H2 must not be used here: partial indexes, expression indexes and composite FKs
 * cannot be represented faithfully (Testing Strategy section 6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class FlywayMigrationIntegrityTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        // Keep the migration gate independent from future default/profile changes.
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

    private static final Set<String> PERSISTENT_TABLES = Set.of(
            "users",
            "refresh_sessions",
            "projects",
            "project_members",
            "project_invitations",
            "folders",
            "categories",
            "tags",
            "documents",
            "document_tags");

    private static final Map<String, Set<String>> EXPECTED_CONSTRAINTS = Map.ofEntries(
            Map.entry("users", Set.of(
                    "uq_users_email",
                    "ck_users_email_normalized",
                    "ck_users_display_name",
                    "ck_users_system_role",
                    "ck_users_status")),
            Map.entry("refresh_sessions", Set.of(
                    "uq_refresh_sessions_token_hash",
                    "fk_refresh_sessions_user",
                    "ck_refresh_sessions_expiration")),
            Map.entry("projects", Set.of(
                    "ck_projects_name")),
            Map.entry("project_members", Set.of(
                    "uq_project_members_project_user",
                    "fk_project_members_project",
                    "fk_project_members_user",
                    "ck_project_members_role")),
            Map.entry("project_invitations", Set.of(
                    "uq_project_invitations_token_hash",
                    "fk_project_invitations_project",
                    "fk_project_invitations_inviter",
                    "ck_project_invitations_email",
                    "ck_project_invitations_status",
                    "ck_project_invitations_expiration",
                    "ck_project_invitations_accepted_at")),
            Map.entry("folders", Set.of(
                    "uq_folders_id_project",
                    "fk_folders_project",
                    "fk_folders_parent_same_project",
                    "ck_folders_name",
                    "ck_folders_not_self_parent")),
            Map.entry("categories", Set.of(
                    "uq_categories_id_project",
                    "fk_categories_project",
                    "ck_categories_name")),
            Map.entry("tags", Set.of(
                    "uq_tags_id_project",
                    "fk_tags_project",
                    "ck_tags_name")),
            Map.entry("documents", Set.of(
                    "uq_documents_storage_key",
                    "uq_documents_id_project",
                    "fk_documents_project",
                    "fk_documents_uploader",
                    "fk_documents_folder_same_project",
                    "fk_documents_category_same_project",
                    "ck_documents_display_name",
                    "ck_documents_original_filename",
                    "ck_documents_file_kind",
                    "ck_documents_extension",
                    "ck_documents_size",
                    "ck_documents_storage_key")),
            Map.entry("document_tags", Set.of(
                    "pk_document_tags",
                    "fk_document_tags_document_same_project",
                    "fk_document_tags_tag_same_project")));

    private static final Set<String> EXPECTED_INDEXES = Set.of(
            "uq_project_members_single_owner",
            "uq_project_pending_invitation_email",
            "uq_folders_root_name",
            "uq_folders_child_name",
            "uq_categories_project_name",
            "uq_tags_project_name",
            "idx_refresh_sessions_user_id",
            "idx_refresh_sessions_expires_at",
            "idx_project_members_user_id",
            "idx_project_members_project_id",
            "idx_project_invitations_project_status",
            "idx_project_invitations_expires_at",
            "idx_documents_project_created_at",
            "idx_documents_project_folder",
            "idx_documents_project_category",
            "idx_documents_project_file_kind",
            "idx_documents_uploaded_by",
            "idx_document_tags_tag_id",
            "idx_document_tags_project_id");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void flywayAppliesAllMigrationsOnEmptyDatabase() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(3);
        assertThat(history)
                .extracting(row -> String.valueOf(row.get("version")))
                .containsExactly("1", "2", "3");
        assertThat(history)
                .allSatisfy(row -> assertThat((Boolean) row.get("success")).isTrue());
    }

    @Test
    void exactlyTheTenPersistentTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        Set<String> businessTables = tables.stream()
                .filter(name -> !"flyway_schema_history".equals(name))
                .collect(Collectors.toSet());
        assertThat(businessTables).isEqualTo(PERSISTENT_TABLES);
    }

    @Test
    void usersEmailVerifiedAtColumnExistsAsNullableTimestamptz() {
        Map<String, Object> column = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND column_name = 'email_verified_at'");

        assertThat(column.get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(column.get("is_nullable")).isEqualTo("YES");
    }

    @Test
    void noOtpTableExists() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables)
                .noneMatch(name -> name.toLowerCase().contains("otp"));
    }

    @Test
    void allExplicitlyNamedConstraintsExistOnTheirDesignedTables() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT conrelid::regclass::text AS table_name, conname FROM pg_constraint "
                        + "WHERE connamespace = 'public'::regnamespace AND conrelid <> 0");

        Map<String, Set<String>> constraintsByTable = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.get("table_name")),
                        Collectors.mapping(
                                row -> String.valueOf(row.get("conname")),
                                Collectors.toSet())));

        EXPECTED_CONSTRAINTS.forEach((table, expectedNames) ->
                assertThat(constraintsByTable.get(table))
                        .as("constraints on table %s", table)
                        .containsAll(expectedNames));
    }

    @Test
    void allPartialExpressionAndQueryIndexesExist() {
        Set<String> indexNames = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class));

        assertThat(indexNames).containsAll(EXPECTED_INDEXES);
    }

    @Test
    void postgresSpecificIndexDefinitionsArePreserved() {
        Map<String, String> definitions = jdbcTemplate.queryForList(
                        "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'")
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("indexname")),
                        row -> String.valueOf(row.get("indexdef"))));

        assertThat(definitions.get("uq_project_members_single_owner"))
                .contains("ON public.project_members USING btree (project_id)")
                .contains("WHERE ((role)::text = 'OWNER'::text)");
        assertThat(definitions.get("uq_project_pending_invitation_email"))
                .contains("WHERE ((status)::text = 'PENDING'::text)");
        assertThat(definitions.get("uq_folders_root_name"))
                .contains("lower((name)::text)")
                .contains("WHERE (parent_id IS NULL)");
        assertThat(definitions.get("uq_folders_child_name"))
                .contains("lower((name)::text)")
                .contains("WHERE (parent_id IS NOT NULL)");
        assertThat(definitions.get("uq_categories_project_name"))
                .contains("lower((name)::text)");
        assertThat(definitions.get("uq_tags_project_name"))
                .contains("lower((name)::text)");
        assertThat(definitions.get("idx_documents_project_created_at"))
                .contains("(project_id, created_at DESC)");
    }

    @Test
    void hibernateSchemaValidationPassesAgainstMigratedSchema() {
        assertThat(entityManager).isNotNull();
        Object userCount = entityManager
                .createNativeQuery("SELECT count(*) FROM users")
                .getSingleResult();
        assertThat(userCount).isInstanceOf(Number.class);
    }

    @Test
    void duplicateEmailRejectedByUniqueConstraint() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        insertUser(email);

        assertThatThrownBy(() -> insertUser(email))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_users_email");
    }

    @Test
    void nonNormalizedEmailRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insertUser("User@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_email_normalized");
    }

    @Test
    void secondOwnerRejectedByPartialUniqueIndexButMemberAllowed() {
        UUID ownerUserId = insertUser("owner-" + UUID.randomUUID() + "@example.com");
        UUID secondUserId = insertUser("second-" + UUID.randomUUID() + "@example.com");
        UUID projectId = insertProject("owner-project-" + UUID.randomUUID());

        insertProjectMember(projectId, ownerUserId, "OWNER");

        assertThatThrownBy(() -> insertProjectMember(projectId, secondUserId, "OWNER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_project_members_single_owner");

        insertProjectMember(projectId, secondUserId, "MEMBER");
    }

    @Test
    void crossProjectTagLinkRejectedByCompositeForeignKey() {
        UUID uploaderId = insertUser("uploader-" + UUID.randomUUID() + "@example.com");
        UUID projectAId = insertProject("project-a-" + UUID.randomUUID());
        UUID projectBId = insertProject("project-b-" + UUID.randomUUID());
        UUID documentAId = insertDocument(projectAId, uploaderId, "doc-a-" + UUID.randomUUID());
        UUID tagBId = insertTag(projectBId, "cross-tag-" + UUID.randomUUID());

        assertThatThrownBy(() -> insertDocumentTag(documentAId, tagBId, projectAId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_document_tags_tag_same_project");

        UUID tagAId = insertTag(projectAId, "same-tag-" + UUID.randomUUID());
        insertDocumentTag(documentAId, tagAId, projectAId);
    }

    private UUID insertUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                userId, email, "password-hash", "Test User", "USER", "ACTIVE");
        return userId;
    }

    private UUID insertProject(String name) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, name) VALUES (?, ?)",
                projectId, name);
        return projectId;
    }

    private void insertProjectMember(UUID projectId, UUID userId, String role) {
        jdbcTemplate.update(
                "INSERT INTO project_members (id, project_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), projectId, userId, role);
    }

    private UUID insertDocument(UUID projectId, UUID uploaderId, String storageKey) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                        + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                documentId, projectId, uploaderId, "Document A", "document-a.pdf",
                "DOCUMENT", "pdf", "application/pdf", 1L,
                "projects/" + projectId + "/documents/" + storageKey + ".pdf");
        return documentId;
    }

    private UUID insertTag(UUID projectId, String name) {
        UUID tagId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tags (id, project_id, name) VALUES (?, ?, ?)",
                tagId, projectId, name);
        return tagId;
    }

    private void insertDocumentTag(UUID documentId, UUID tagId, UUID projectId) {
        jdbcTemplate.update(
                "INSERT INTO document_tags (document_id, tag_id, project_id) VALUES (?, ?, ?)",
                documentId, tagId, projectId);
    }
}
