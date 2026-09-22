package com.kbase.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pgvector.PGvector;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves that the shared PostgreSQL 17 test foundation exposes pgvector. */
@Testcontainers
class PgVectorCompatibilityIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @Test
    void exposesVectorExtensionDimensionCosineOrderingAndHnsw() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
                statement.execute("CREATE TEMP TABLE m1_vectors (id integer PRIMARY KEY, embedding vector(768))");
            }

            PGvector.registerTypes(connection);
            float[] near = unitVector(0);
            float[] far = unitVector(1);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO m1_vectors (id, embedding) VALUES (?, ?::vector)")) {
                insert.setInt(1, 1);
                insert.setObject(2, new PGvector(near));
                insert.addBatch();
                insert.setInt(1, 2);
                insert.setObject(2, new PGvector(far));
                insert.addBatch();
                insert.executeBatch();
            }

            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id FROM m1_vectors ORDER BY embedding <=> ?::vector LIMIT 1")) {
                query.setObject(1, new PGvector(near));
                try (ResultSet result = query.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("id")).isEqualTo(1);
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX m1_vectors_embedding_hnsw "
                        + "ON m1_vectors USING hnsw (embedding vector_cosine_ops)");
            }
        }
    }

    private static float[] unitVector(int axis) {
        float[] vector = new float[768];
        vector[axis] = 1.0f;
        return vector;
    }
}
