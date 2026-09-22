package com.kbase.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves an existing Core V1-V3 database can receive additive AI V4 safely. */
@Testcontainers
class FlywayAiUpgradeIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @Test
    void coreV1ToV3DataSurvivesApplyingAiV4() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();

        try (Connection connection = connection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO users (id, email, password_hash, display_name, system_role, status) "
                                + "VALUES ('" + userId + "', 'upgrade@example.com', 'hash', "
                                + "'Upgrade User', 'USER', 'ACTIVE')");
                statement.executeUpdate(
                        "INSERT INTO projects (id, name) VALUES ('" + projectId + "', 'Upgrade Project')");
            }
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(count(connection, "users", userId)).isEqualTo(1);
            assertThat(count(connection, "projects", projectId)).isEqualTo(1);

            List<String> versions = queryStrings(connection,
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank");
            assertThat(versions).containsExactly("1", "2", "3", "4");

            assertThat(queryStrings(connection,
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name IN "
                            + "('document_ai_indexes', 'document_ai_chunks', 'ai_conversations', "
                            + "'ai_messages', 'ai_message_sources', 'ai_jobs', "
                            + "'ai_guide_sources', 'ai_guide_chunks')"))
                    .containsExactlyInAnyOrder(
                            "document_ai_indexes", "document_ai_chunks", "ai_conversations",
                            "ai_messages", "ai_message_sources", "ai_jobs", "ai_guide_sources",
                            "ai_guide_chunks");
            assertThat(queryStrings(connection,
                    "SELECT extname FROM pg_extension WHERE extname = 'vector'"))
                    .containsExactly("vector");
            assertThat(queryStrings(connection,
                    "SELECT format_type(a.atttypid, a.atttypmod) "
                            + "FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid "
                            + "WHERE c.relname IN ('document_ai_chunks', 'ai_guide_chunks') "
                            + "AND a.attname = 'embedding' AND a.attnum > 0 "
                            + "ORDER BY c.relname"))
                    .containsExactly("vector(768)", "vector(768)");
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long count(Connection connection, String table, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM " + table + " WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private List<String> queryStrings(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }
}
