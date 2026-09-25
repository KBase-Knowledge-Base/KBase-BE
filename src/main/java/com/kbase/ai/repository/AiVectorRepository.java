package com.kbase.ai.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;

import com.kbase.ai.guide.GuideSourceCatalog;
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
                   d.display_name AS document_name,
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
            JOIN documents d
              ON d.id = c.document_id
             AND d.project_id = c.project_id
            WHERE c.project_id = ?
              AND i.status = 'READY'
              AND c.index_version = i.active_version
            ORDER BY c.embedding <=> ?::vector, c.id
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
              AND s.source_key IN (%s)
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final List<String> approvedGuideSourceKeys;

    public AiVectorRepository(JdbcTemplate jdbcTemplate, GuideSourceCatalog guideSources) {
        this.jdbcTemplate = jdbcTemplate;
        this.approvedGuideSourceKeys = List.copyOf(guideSources.sources().stream()
                .map(source -> source.sourceKey()).toList());
        if (approvedGuideSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("Guide source allowlist must not be empty");
        }
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
                                resultSet.getString("document_name"),
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
            try (PreparedStatement statement = connection.prepareStatement(guideSearchSql())) {
                PGvector queryVector = new PGvector(queryEmbedding);
                statement.setObject(1, queryVector);
                int parameter = 2;
                for (String sourceKey : approvedGuideSourceKeys) {
                    statement.setString(parameter++, sourceKey);
                }
                statement.setObject(parameter++, queryVector);
                statement.setInt(parameter, limit);
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

    private String guideSearchSql() {
        String placeholders = String.join(", ", java.util.Collections.nCopies(
                approvedGuideSourceKeys.size(), "?"));
        return GUIDE_SEARCH.formatted(placeholders);
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

    /**
     * Moves the lifecycle row into PROCESSING only for the currently leased
     * document job. The caller wraps this and the other workflow operations in
     * short transactions; provider calls never run inside these methods.
     */
    public int markDocumentIndexProcessing(UUID projectId, UUID documentId, long indexVersion,
            UUID jobId, String leaseToken, Instant now) {
        requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
        requireVersion(indexVersion);
        return jdbcTemplate.update("""
                UPDATE document_ai_indexes i
                   SET status = 'PROCESSING',
                       attempt_count = attempt_count + 1,
                       failure_reason = NULL,
                       last_error_code = NULL,
                       updated_at = ?
                 WHERE i.project_id = ?
                   AND i.document_id = ?
                   AND i.desired_version = ?
                   AND (
                       i.status IN ('PENDING', 'FAILED', 'PROCESSING')
                       OR (i.status = 'READY'
                           AND (i.active_version IS NULL OR i.active_version < i.desired_version))
                   )
                   AND EXISTS (
                       SELECT 1 FROM documents d
                        WHERE d.id = i.document_id AND d.project_id = i.project_id)
                   AND EXISTS (
                       SELECT 1 FROM ai_jobs j
                        WHERE j.id = ?
                          AND j.document_id = i.document_id
                          AND j.project_id = i.project_id
                          AND j.status = 'PROCESSING'
                          AND j.locked_by = ?
                          AND j.lease_until > ?)
                """, timestamp(now), projectId, documentId, indexVersion, jobId,
                leaseToken, timestamp(now));
    }

    /** Records a retry category without making a stale worker change state. */
    public int markDocumentIndexRetry(UUID projectId, UUID documentId, long indexVersion,
            UUID jobId, String leaseToken, String errorCode, Instant now) {
        requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
        requireVersion(indexVersion);
        return jdbcTemplate.update("""
                UPDATE document_ai_indexes i
                   SET status = 'PROCESSING',
                       failure_reason = NULL,
                       last_error_code = ?,
                       updated_at = ?
                 WHERE i.project_id = ?
                   AND i.document_id = ?
                   AND i.desired_version = ?
                   AND i.status = 'PROCESSING'
                   AND EXISTS (
                       SELECT 1 FROM ai_jobs j
                        WHERE j.id = ?
                          AND j.document_id = i.document_id
                          AND j.project_id = i.project_id
                          AND j.status = 'PROCESSING'
                          AND j.locked_by = ?
                          AND j.lease_until > ?)
                """, errorCode, timestamp(now), projectId, documentId, indexVersion, jobId,
                leaseToken, timestamp(now));
    }

    /** Marks a safe terminal failure while the current claim still owns the lease. */
    public int markDocumentIndexFailure(UUID projectId, UUID documentId, long indexVersion,
            UUID jobId, String leaseToken, String failureReason, String errorCode, Instant now) {
        requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
        requireVersion(indexVersion);
        return jdbcTemplate.update("""
                UPDATE document_ai_indexes i
                   SET status = 'FAILED',
                       failure_reason = ?,
                       last_error_code = ?,
                       updated_at = ?
                 WHERE i.project_id = ?
                   AND i.document_id = ?
                   AND i.desired_version = ?
                   AND i.status = 'PROCESSING'
                   AND EXISTS (
                       SELECT 1 FROM ai_jobs j
                        WHERE j.id = ?
                          AND j.document_id = i.document_id
                          AND j.project_id = i.project_id
                          AND j.status = 'PROCESSING'
                          AND j.locked_by = ?
                          AND j.lease_until > ?)
                """, failureReason, errorCode, timestamp(now), projectId, documentId,
                indexVersion, jobId, leaseToken, timestamp(now));
    }

    /** Unsupported jobs are terminal and never stage or embed content. */
    public int markDocumentIndexUnsupported(UUID projectId, UUID documentId, long indexVersion,
            UUID jobId, String leaseToken, Instant now) {
        requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
        requireVersion(indexVersion);
        return jdbcTemplate.update("""
                UPDATE document_ai_indexes i
                   SET status = 'UNSUPPORTED',
                       failure_reason = 'UNSUPPORTED_FILE_TYPE',
                       last_error_code = NULL,
                       updated_at = ?
                 WHERE i.project_id = ?
                   AND i.document_id = ?
                   AND i.desired_version = ?
                   AND i.status <> 'READY'
                   AND EXISTS (
                       SELECT 1 FROM ai_jobs j
                        WHERE j.id = ?
                          AND j.document_id = i.document_id
                          AND j.project_id = i.project_id
                          AND j.status = 'PROCESSING'
                          AND j.locked_by = ?
                          AND j.lease_until > ?)
                """, timestamp(now), projectId, documentId, indexVersion, jobId, leaseToken,
                timestamp(now));
    }

    /**
     * Replaces only a non-active staging version. This method must be called in
     * a short transaction after all provider calls have completed.
     */
    public boolean stageDocumentChunks(UUID projectId, UUID documentId, long indexVersion,
            List<DocumentAiChunkInsert> chunks) {
        return stageDocumentChunks(projectId, documentId, indexVersion, null, null, null, chunks);
    }

    /** Lease-aware staging used by the production worker. */
    public boolean stageDocumentChunks(UUID projectId, UUID documentId, long indexVersion,
            UUID jobId, String leaseToken, Instant now, List<DocumentAiChunkInsert> chunks) {
        Objects.requireNonNull(projectId, "projectId is mandatory");
        Objects.requireNonNull(documentId, "documentId is mandatory");
        requireVersion(indexVersion);
        Objects.requireNonNull(chunks, "chunks is mandatory");
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }
        for (DocumentAiChunkInsert chunk : chunks) {
            Objects.requireNonNull(chunk, "chunk is mandatory");
            if (!projectId.equals(chunk.projectId()) || !documentId.equals(chunk.documentId())
                    || chunk.indexVersion() != indexVersion) {
                throw new IllegalArgumentException("staged chunk scope does not match index scope");
            }
        }

        if (jobId != null || leaseToken != null || now != null) {
            requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
            List<UUID> documents = jdbcTemplate.query("""
                    SELECT id FROM documents
                     WHERE id = ? AND project_id = ?
                     FOR UPDATE
                    """, (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                    documentId, projectId);
            if (documents.isEmpty()) {
                return false;
            }
            List<LeaseState> leases = jdbcTemplate.query("""
                    SELECT lease_until FROM ai_jobs
                     WHERE id = ? AND document_id = ? AND project_id = ?
                       AND status = 'PROCESSING' AND locked_by = ?
                     FOR UPDATE
                    """, (resultSet, rowNum) -> new LeaseState(
                    resultSet.getTimestamp("lease_until") == null
                            ? null : resultSet.getTimestamp("lease_until").toInstant()),
                    jobId, documentId, projectId, leaseToken);
            if (leases.isEmpty() || leases.getFirst().leaseUntil() == null
                    || !leases.getFirst().leaseUntil().isAfter(now)) {
                return false;
            }
        }

        List<IndexState> states = jdbcTemplate.query("""
                SELECT status, active_version, desired_version
                  FROM document_ai_indexes
                 WHERE project_id = ? AND document_id = ?
                 FOR UPDATE
                """, (resultSet, rowNum) -> new IndexState(
                resultSet.getString("status"), nullableLong(resultSet, "active_version"),
                resultSet.getLong("desired_version")), projectId, documentId);
        if (states.isEmpty()) {
            return false;
        }
        IndexState state = states.getFirst();
        if ("READY".equals(state.status()) && Long.valueOf(indexVersion).equals(state.activeVersion())) {
            return false;
        }
        if (!"PROCESSING".equals(state.status()) || state.desiredVersion() != indexVersion) {
            return false;
        }

        jdbcTemplate.update(
                "DELETE FROM document_ai_chunks WHERE project_id = ? AND document_id = ? AND index_version = ?",
                projectId, documentId, indexVersion);
        for (DocumentAiChunkInsert chunk : chunks) {
            insertDocumentChunk(chunk);
        }
        return true;
    }

    /**
     * Locks the document, current job lease and index row in that order before
     * switching the active version. A deleted document or expired/reclaimed
     * lease returns false and cannot resurrect chunks.
     */
    public boolean activateDocumentVersion(UUID projectId, UUID documentId, long indexVersion,
            String sourceHash, UUID jobId, String leaseToken, Instant now) {
        requireWorkflowIds(projectId, documentId, jobId, leaseToken, now);
        requireVersion(indexVersion);
        if (sourceHash == null || sourceHash.isBlank()) {
            throw new IllegalArgumentException("sourceHash must be non-blank");
        }

        List<UUID> documents = jdbcTemplate.query("""
                SELECT id FROM documents
                 WHERE id = ? AND project_id = ?
                 FOR UPDATE
                """, (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                documentId, projectId);
        if (documents.isEmpty()) {
            return false;
        }

        List<LeaseState> leases = jdbcTemplate.query("""
                SELECT lease_until FROM ai_jobs
                 WHERE id = ? AND document_id = ? AND project_id = ?
                   AND status = 'PROCESSING' AND locked_by = ?
                 FOR UPDATE
                """, (resultSet, rowNum) -> new LeaseState(
                resultSet.getTimestamp("lease_until") == null
                        ? null : resultSet.getTimestamp("lease_until").toInstant()),
                jobId, documentId, projectId, leaseToken);
        if (leases.isEmpty() || leases.getFirst().leaseUntil() == null
                || !leases.getFirst().leaseUntil().isAfter(now)) {
            return false;
        }

        List<IndexState> states = jdbcTemplate.query("""
                SELECT status, active_version, desired_version
                  FROM document_ai_indexes
                 WHERE project_id = ? AND document_id = ?
                 FOR UPDATE
                """, (resultSet, rowNum) -> new IndexState(
                resultSet.getString("status"), nullableLong(resultSet, "active_version"),
                resultSet.getLong("desired_version")), projectId, documentId);
        if (states.isEmpty()) {
            return false;
        }
        IndexState state = states.getFirst();
        if ("READY".equals(state.status()) && Long.valueOf(indexVersion).equals(state.activeVersion())) {
            return true;
        }
        if (!"PROCESSING".equals(state.status()) || state.desiredVersion() != indexVersion) {
            return false;
        }

        Integer stagedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_ai_chunks
                 WHERE project_id = ? AND document_id = ? AND index_version = ?
                """, Integer.class, projectId, documentId, indexVersion);
        if (stagedCount == null || stagedCount <= 0) {
            return false;
        }

        int updated = jdbcTemplate.update("""
                UPDATE document_ai_indexes
                   SET status = 'READY', active_version = ?, source_hash = ?,
                       failure_reason = NULL, last_error_code = NULL,
                       indexed_at = ?, updated_at = ?
                 WHERE project_id = ? AND document_id = ?
                   AND status = 'PROCESSING' AND desired_version = ?
                """, indexVersion, sourceHash, timestamp(now), timestamp(now), projectId,
                documentId, indexVersion);
        if (updated != 1) {
            return false;
        }
        cleanupDocumentChunksExceptVersion(projectId, documentId, indexVersion);
        return true;
    }

    /** Removes inactive generations only after a successful activation. */
    public int cleanupDocumentChunksExceptVersion(UUID projectId, UUID documentId,
            long activeVersion) {
        Objects.requireNonNull(projectId, "projectId is mandatory");
        Objects.requireNonNull(documentId, "documentId is mandatory");
        requireVersion(activeVersion);
        return jdbcTemplate.update("""
                DELETE FROM document_ai_chunks
                 WHERE project_id = ? AND document_id = ? AND index_version <> ?
                """, projectId, documentId, activeVersion);
    }

    private static Integer getNullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static UUID required(UUID value, String name) {
        return Objects.requireNonNull(value, name + " is mandatory");
    }

    private static void requireWorkflowIds(UUID projectId, UUID documentId, UUID jobId,
            String leaseToken, Instant now) {
        Objects.requireNonNull(projectId, "projectId is mandatory");
        Objects.requireNonNull(documentId, "documentId is mandatory");
        Objects.requireNonNull(jobId, "jobId is mandatory");
        if (leaseToken == null || leaseToken.isBlank()) {
            throw new IllegalArgumentException("leaseToken must be non-blank");
        }
        Objects.requireNonNull(now, "now is mandatory");
    }

    private static void requireVersion(long indexVersion) {
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(Objects.requireNonNull(instant, "instant"));
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record IndexState(String status, Long activeVersion, long desiredVersion) {
    }

    private record LeaseState(Instant leaseUntil) {
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
