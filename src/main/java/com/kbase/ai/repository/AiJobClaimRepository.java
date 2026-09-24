package com.kbase.ai.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.job.AiJobEnqueueResult;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.enums.AiJobType;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-specific durable job boundary.
 *
 * <p>All worker selection is performed here so callers cannot accidentally
 * replace row locking with a Java-side find/filter/update loop.</p>
 */
@Repository
public class AiJobClaimRepository {

    private static final String ACTIVE_STATUSES = "('PENDING', 'PROCESSING', 'RETRY')";

    private static final String CLAIM_SELECT = """
            SELECT id, job_type, project_id, document_id, user_id, dedup_key,
                   payload, run_at, attempt_count, max_attempts, lease_until,
                   created_at
            FROM ai_jobs
            WHERE job_type IN (:jobTypes)
              AND (
                    (status IN ('PENDING', 'RETRY')
                     AND run_at <= :now
                     AND attempt_count < max_attempts)
                    OR
                    (status = 'PROCESSING'
                     AND lease_until <= :now
                     AND attempt_count < max_attempts)
                  )
            ORDER BY CASE WHEN status = 'PROCESSING' THEN 0 ELSE 1 END,
                     run_at, created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """;

    private static final String CLAIM_UPDATE = """
            UPDATE ai_jobs
               SET status = 'PROCESSING',
                   attempt_count = attempt_count + 1,
                   lease_until = :leaseUntil,
                   locked_by = :leaseToken,
                   last_error_code = NULL,
                   completed_at = NULL,
                   updated_at = :now
             WHERE id = :id
            """;

    private static final String FAIL_EXHAUSTED_STALE = """
            UPDATE ai_jobs
               SET status = 'FAILED',
                   lease_until = NULL,
                   locked_by = NULL,
                   completed_at = :now,
                   last_error_code = 'MAX_ATTEMPTS_EXHAUSTED',
                   updated_at = :now
             WHERE job_type IN (:jobTypes)
               AND status = 'PROCESSING'
               AND lease_until <= :now
               AND attempt_count >= max_attempts
            """;

    private static final String ACTIVE_BY_DEDUP = """
            SELECT id
            FROM ai_jobs
            WHERE dedup_key = :dedupKey
              AND status IN ('PENDING', 'PROCESSING', 'RETRY')
            ORDER BY created_at, id
            LIMIT 1
            """;

    private static final String INSERT_JOB = """
            INSERT INTO ai_jobs (
                id, job_type, status, project_id, document_id, user_id,
                dedup_key, payload, run_at, attempt_count, max_attempts,
                lease_until, locked_by, last_error_code, created_at,
                updated_at, completed_at)
            VALUES (
                :id, :jobType, 'PENDING', :projectId, :documentId, :userId,
                :dedupKey, CAST(:payload AS jsonb), :runAt, 0, :maxAttempts,
                NULL, NULL, NULL, :now, :now, NULL)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiJobClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Claims a bounded batch while the caller's short transaction is active.
     * The returned snapshots are safe to execute after the transaction closes.
     */
    public List<AiJobClaim> claimDueJobs(Set<AiJobType> allowedTypes, int batchSize,
            Instant now, Duration leaseTimeout, String workerIdentity) {
        List<String> jobTypes = jobTypeNames(allowedTypes);
        validateBatchSize(batchSize);
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseTimeout, "leaseTimeout");
        if (leaseTimeout.isZero() || leaseTimeout.isNegative()) {
            throw new IllegalArgumentException("leaseTimeout must be positive");
        }
        if (jobTypes.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("jobTypes", jobTypes)
                .addValue("now", timestamp(now))
                .addValue("batchSize", batchSize);
        List<JobRow> rows = jdbcTemplate.query(CLAIM_SELECT, parameters, AiJobClaimRepository::mapRow);
        Instant leaseUntil = now.plus(leaseTimeout);
        List<AiJobClaim> claims = new ArrayList<>(rows.size());
        for (JobRow row : rows) {
            String leaseToken = Objects.requireNonNull(workerIdentity, "workerIdentity")
                    + ":" + UUID.randomUUID();
            MapSqlParameterSource update = new MapSqlParameterSource()
                    .addValue("id", row.id())
                    .addValue("leaseUntil", timestamp(leaseUntil))
                    .addValue("leaseToken", leaseToken)
                    .addValue("now", timestamp(now));
            int updated = jdbcTemplate.update(CLAIM_UPDATE, update);
            if (updated != 1) {
                continue;
            }
            claims.add(new AiJobClaim(
                    row.id(), row.jobType(), row.projectId(), row.documentId(), row.userId(),
                    row.dedupKey(), row.payload(), row.runAt(), row.attemptCount() + 1,
                    row.maxAttempts(), leaseUntil, leaseToken));
        }
        return claims;
    }

    /** Marks stale processing rows with no remaining attempt as terminal. */
    public int failExhaustedStaleJobs(Set<AiJobType> allowedTypes, Instant now) {
        List<String> jobTypes = jobTypeNames(allowedTypes);
        if (jobTypes.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(FAIL_EXHAUSTED_STALE, new MapSqlParameterSource()
                .addValue("jobTypes", jobTypes)
                .addValue("now", timestamp(now)));
    }

    /** Enqueues one active semantic job while holding a transaction-scoped key lock. */
    public AiJobEnqueueResult enqueueActive(AiJobSchedule schedule, Instant now) {
        validateSchedule(schedule);
        Objects.requireNonNull(now, "now");
        lockDedupKey(schedule.dedupKey());

        UUID activeId = jdbcTemplate.query(ACTIVE_BY_DEDUP,
                new MapSqlParameterSource("dedupKey", schedule.dedupKey()),
                (resultSet, rowNum) -> resultSet.getObject("id", UUID.class))
                .stream().findFirst().orElse(null);
        if (activeId != null) {
            return new AiJobEnqueueResult(activeId, false);
        }

        UUID jobId = UUID.randomUUID();
        Instant runAt = schedule.runAt() == null ? now : schedule.runAt();
        jdbcTemplate.update(INSERT_JOB, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("jobType", schedule.jobType().name())
                .addValue("projectId", schedule.projectId())
                .addValue("documentId", schedule.documentId())
                .addValue("userId", schedule.userId())
                .addValue("dedupKey", schedule.dedupKey())
                .addValue("payload", schedule.payload())
                .addValue("runAt", timestamp(runAt))
                .addValue("maxAttempts", schedule.maxAttempts())
                .addValue("now", timestamp(now)));
        return new AiJobEnqueueResult(jobId, true);
    }

    public int markDone(UUID jobId, String leaseToken, Instant now) {
        return jdbcTemplate.update("""
                UPDATE ai_jobs
                   SET status = 'DONE', completed_at = :now, lease_until = NULL,
                       locked_by = NULL, last_error_code = NULL, updated_at = :now
                 WHERE id = :id AND status = 'PROCESSING' AND locked_by = :leaseToken
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("leaseToken", leaseToken)
                .addValue("now", timestamp(now)));
    }

    public int markRetry(UUID jobId, String leaseToken, Instant nextRunAt,
            String errorCode, Instant now) {
        return jdbcTemplate.update("""
                UPDATE ai_jobs
                   SET status = 'RETRY', run_at = :nextRunAt, lease_until = NULL,
                       locked_by = NULL, completed_at = NULL,
                       last_error_code = :errorCode, updated_at = :now
                 WHERE id = :id AND status = 'PROCESSING' AND locked_by = :leaseToken
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("leaseToken", leaseToken)
                .addValue("nextRunAt", timestamp(nextRunAt))
                .addValue("errorCode", errorCode)
                .addValue("now", timestamp(now)));
    }

    public int markFailed(UUID jobId, String leaseToken, String errorCode, Instant now) {
        return jdbcTemplate.update("""
                UPDATE ai_jobs
                   SET status = 'FAILED', completed_at = :now, lease_until = NULL,
                       locked_by = NULL, last_error_code = :errorCode, updated_at = :now
                 WHERE id = :id AND status = 'PROCESSING' AND locked_by = :leaseToken
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("leaseToken", leaseToken)
                .addValue("errorCode", errorCode)
                .addValue("now", timestamp(now)));
    }

    /** Lifecycle cancellation may neutralize a worker job; handler transitions remain lease-conditional. */
    public int cancelActiveByDedupKey(String dedupKey, Instant now) {
        return jdbcTemplate.update("""
                UPDATE ai_jobs
                   SET status = 'CANCELLED', lease_until = NULL, locked_by = NULL,
                       completed_at = COALESCE(completed_at, :now),
                       last_error_code = NULL, updated_at = :now
                 WHERE dedup_key = :dedupKey
                   AND status IN ('PENDING', 'PROCESSING', 'RETRY')
                """, new MapSqlParameterSource()
                .addValue("dedupKey", dedupKey)
                .addValue("now", timestamp(now)));
    }

    /**
     * Serializes one semantic job/lifecycle key for the lifetime of the current
     * PostgreSQL transaction. This is intentionally reusable by the retention
     * rejoin and purge paths: a Java-side mutex would not protect another node.
     */
    public void lockDedupKey(String dedupKey) {
        Objects.requireNonNull(dedupKey, "dedupKey");
        jdbcTemplate.getJdbcOperations().execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, dedupKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        // The function returns void; consuming the row keeps the
                        // driver protocol in a clean state for the next query.
                    }
                }
            }
            return null;
        });
    }

    /**
     * Checks that a destructive worker still owns the exact processing lease.
     * The caller must already hold the corresponding lifecycle advisory lock.
     */
    public boolean hasCurrentConversationPurgeLease(UUID jobId, UUID projectId, UUID userId,
            String leaseToken, Instant now) {
        if (jobId == null || projectId == null || userId == null || leaseToken == null
                || leaseToken.isBlank() || now == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM ai_jobs
                WHERE id = :id
                  AND job_type = 'CONVERSATION_PURGE'
                  AND status = 'PROCESSING'
                  AND project_id = :projectId
                  AND user_id = :userId
                  AND document_id IS NULL
                  AND locked_by = :leaseToken
                  AND lease_until > :now
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("projectId", projectId)
                .addValue("userId", userId)
                .addValue("leaseToken", leaseToken)
                .addValue("now", timestamp(now)), Integer.class);
        return count != null && count == 1;
    }

    private static JobRow mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new JobRow(
                resultSet.getObject("id", UUID.class),
                AiJobType.valueOf(resultSet.getString("job_type")),
                uuid(resultSet, "project_id"), uuid(resultSet, "document_id"), uuid(resultSet, "user_id"),
                resultSet.getString("dedup_key"), resultSet.getString("payload"),
                instant(resultSet, "run_at"), resultSet.getInt("attempt_count"),
                resultSet.getInt("max_attempts"), instant(resultSet, "lease_until"));
    }

    private static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(Objects.requireNonNull(instant, "instant"));
    }

    private static List<String> jobTypeNames(Set<AiJobType> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return List.of();
        }
        return allowedTypes.stream().filter(Objects::nonNull)
                .map(Enum::name).sorted().toList();
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    private static void validateSchedule(AiJobSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(schedule.jobType(), "jobType");
        if (schedule.dedupKey() == null || schedule.dedupKey().isBlank()
                || schedule.dedupKey().length() > 512) {
            throw new IllegalArgumentException("dedupKey must be non-blank and at most 512 characters");
        }
        if (schedule.maxAttempts() <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    private record JobRow(
            UUID id,
            AiJobType jobType,
            UUID projectId,
            UUID documentId,
            UUID userId,
            String dedupKey,
            String payload,
            Instant runAt,
            int attemptCount,
            int maxAttempts,
            Instant leaseUntil) {
    }
}
