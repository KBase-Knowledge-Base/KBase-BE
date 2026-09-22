package com.kbase.ai.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.pgvector.PGvector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * KBase-owned PostgreSQL/pgvector boundary.
 *
 * <p>Project scope and active index-version selection are mandatory SQL predicates
 * in this repository. There is intentionally no global document-vector search method.</p>
 */
@Repository
public class AiVectorRepository {

    public static final int EMBEDDING_DIMENSIONS = 768;

    private static final String INSERT_DOCUMENT_CHUNK = """
            INSERT INTO document_ai_chunks (
                id, project_id, document_id, index_version, chunk_index, content,
                page_number, slide_number, section_title, token_count, content_hash, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
            """;

    private static final String INSERT_GUIDE_CHUNK = """
            INSERT INTO ai_guide_chunks (
                id, guide_source_id, index_version, chunk_index, content, heading_path,
                token_count, content_hash, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
            """;

    private static final String DOCUMENT_SEARCH = """
            SELECT c.id,
                   c.project_id,
                   c.document_id,
                   c.index_version,
                   c.chunk_index,
                   c.content,
                   c.page_number,
                   c.slide_number,
                   c.section_title,
                   c.token_count,
                   c.content_hash,
                   1 - (c.embedding <=> ?::vector) AS similarity
            FROM document_ai_chunks c
            JOIN document_ai_indexes i
              ON i.document_id = c.document_id
             AND i.project_id = c.project_id
            WHERE c.project_id = ?
              AND i.status = 'READY'
              AND c.index_version = i.active_version
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String GUIDE_SEARCH = """
            SELECT c.id,
                   c.guide_source_id,
                   c.index_version,
                   c.chunk_index,
                   c.content,
                   c.heading_path,
                   c.token_count,
                   c.content_hash,
                   1 - (c.embedding <=> ?::vector) AS similarity
            FROM ai_guide_chunks c
            JOIN ai_guide_sources s
              ON s.id = c.guide_source_id
            WHERE s.status = 'READY'
              AND c.index_version = s.active_version
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AiVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertDocumentChunk(DocumentAiChunkInsert chunk) {
        Objects.requireNonNull(chunk, "chunk is mandatory");
        validateVector(chunk.embedding());
        jdbcTemplate.execute((Connection connection) -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_DOCUMENT_CHUNK)) {
                statement.setObject(1, required(chunk.id(), "id"));
                statement.setObject(2, required(chunk.projectId(), "projectId"));
                statement.setObject(3, required(chunk.documentId(), "documentId"));
                statement.setLong(4, chunk.indexVersion());
                statement.setInt(5, chunk.chunkIndex());
                statement.setString(6, chunk.content());
                statement.setObject(7, chunk.pageNumber());
                statement.setObject(8, chunk.slideNumber());
                statement.setString(9, chunk.sectionTitle());
                statement.setObject(10, chunk.tokenCount());
                statement.setString(11, chunk.contentHash());
                statement.setObject(12, new PGvector(chunk.embedding()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void insertGuideChunk(GuideChunkInsert chunk) {
        Objects.requireNonNull(chunk, "chunk is mandatory");
        validateVector(chunk.embedding());
        jdbcTemplate.execute((Connection connection) -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_GUIDE_CHUNK)) {
                statement.setObject(1, required(chunk.id(), "id"));
                statement.setObject(2, required(chunk.guideSourceId(), "guideSourceId"));
                statement.setLong(3, chunk.indexVersion());
                statement.setInt(4, chunk.chunkIndex());
                statement.setString(5, chunk.content());
                statement.setString(6, chunk.headingPath());
                statement.setObject(7, chunk.tokenCount());
                statement.setString(8, chunk.contentHash());
                statement.setObject(9, new PGvector(chunk.embedding()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Finds only active, READY chunks within the supplied project. The project
     * predicate is deliberately in the SQL statement, before ANN ordering/limit.
     */
    public List<DocumentAiChunkMatch> findNearestDocumentChunks(
            UUID projectId, float[] queryEmbedding, int limit) {
        Objects.requireNonNull(projectId, "projectId is mandatory");
        validateLimit(limit);
        validateVector(queryEmbedding);
        return jdbcTemplate.execute((Connection connection) -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(DOCUMENT_SEARCH)) {
                PGvector queryVector = new PGvector(queryEmbedding);
                statement.setObject(1, queryVector);
                statement.setObject(2, projectId);
                statement.setObject(3, queryVector);
                statement.setInt(4, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<DocumentAiChunkMatch> matches = new ArrayList<>();
                    while (resultSet.next()) {
                        matches.add(new DocumentAiChunkMatch(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("project_id", UUID.class),
                                resultSet.getObject("document_id", UUID.class),
                                resultSet.getLong("index_version"),
                                resultSet.getInt("chunk_index"),
                                resultSet.getString("content"),
                                getNullableInt(resultSet, "page_number"),
                                getNullableInt(resultSet, "slide_number"),
                                resultSet.getString("section_title"),
                                getNullableInt(resultSet, "token_count"),
                                resultSet.getString("content_hash"),
                                resultSet.getDouble("similarity")));
                    }
                    return matches;
                }
            }
        });
    }

    /** Finds only active READY Guide chunks; Guide has no project partition. */
    public List<GuideChunkMatch> findNearestGuideChunks(float[] queryEmbedding, int limit) {
        validateLimit(limit);
        validateVector(queryEmbedding);
        return jdbcTemplate.execute((Connection connection) -> {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(GUIDE_SEARCH)) {
                PGvector queryVector = new PGvector(queryEmbedding);
                statement.setObject(1, queryVector);
                statement.setObject(2, queryVector);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<GuideChunkMatch> matches = new ArrayList<>();
                    while (resultSet.next()) {
                        matches.add(new GuideChunkMatch(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("guide_source_id", UUID.class),
                                resultSet.getLong("index_version"),
                                resultSet.getInt("chunk_index"),
                                resultSet.getString("content"),
                                resultSet.getString("heading_path"),
                                getNullableInt(resultSet, "token_count"),
                                resultSet.getString("content_hash"),
                                resultSet.getDouble("similarity")));
                    }
                    return matches;
                }
            }
        });
    }

    public int deleteDocumentChunksForVersion(UUID documentId, long indexVersion) {
        Objects.requireNonNull(documentId, "documentId is mandatory");
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        return jdbcTemplate.update(
                "DELETE FROM document_ai_chunks WHERE document_id = ? AND index_version = ?",
                documentId, indexVersion);
    }

    private static Integer getNullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static UUID required(UUID value, String name) {
        return Objects.requireNonNull(value, name + " is mandatory");
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    private static void validateVector(float[] vector) {
        Objects.requireNonNull(vector, "embedding is mandatory");
        if (vector.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "embedding must contain exactly " + EMBEDDING_DIMENSIONS + " dimensions");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
        }
    }
}
