package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import com.kbase.ai.guide.GuideSourceCatalog;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.GuideChunkInsert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves the Guide allowlist is a SQL predicate before ANN ordering and limit. */
@Testcontainers
class GuideVectorRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase").withUsername("kbase").withPassword("kbase");

    private JdbcTemplate jdbc;
    private AiVectorRepository vectors;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource());
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("DROP TABLE IF EXISTS ai_guide_chunks");
        jdbc.execute("DROP TABLE IF EXISTS ai_guide_sources");
        jdbc.execute("""
                CREATE TABLE ai_guide_sources (
                    id uuid PRIMARY KEY, source_key varchar(1024) NOT NULL,
                    status varchar(20) NOT NULL, active_version bigint)
                """);
        jdbc.execute("""
                CREATE TABLE ai_guide_chunks (
                    id uuid PRIMARY KEY, guide_source_id uuid NOT NULL, index_version bigint NOT NULL,
                    chunk_index integer NOT NULL, content text NOT NULL, heading_path text,
                    token_count integer, content_hash varchar(128), embedding vector(768) NOT NULL)
                """);
        vectors = new AiVectorRepository(jdbc, new GuideSourceCatalog());
    }

    @Test
    void excludesRoguePerfectVectorBeforeCandidateLimit() {
        UUID approved = UUID.randomUUID();
        UUID rogue = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_guide_sources (id, source_key, status, active_version) VALUES (?, ?, 'READY', 1)",
                approved, "docs/product-specs/KBase - Core v1 Specification.md");
        jdbc.update("INSERT INTO ai_guide_sources (id, source_key, status, active_version) VALUES (?, ?, 'READY', 1)",
                rogue, "docs/internal/secret-plan.md");
        vectors.insertGuideChunk(chunk(approved, "approved", unitVector(1)));
        vectors.insertGuideChunk(chunk(rogue, "rogue", unitVector(0)));

        var matches = vectors.findNearestGuideChunks(unitVector(0), 1);

        assertThat(matches).extracting(match -> match.content()).containsExactly("approved");
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static GuideChunkInsert chunk(UUID sourceId, String content, float[] embedding) {
        return new GuideChunkInsert(UUID.randomUUID(), sourceId, 1, 0, content,
                "KBase > Test", 1, "a".repeat(64), embedding);
    }

    private static float[] unitVector(int axis) {
        float[] vector = new float[768];
        vector[axis] = 1.0f;
        return vector;
    }
}
