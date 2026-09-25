package com.kbase.ai.guide;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.GuideChunkInsert;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Lease-aware staging/activation for Guide generations, preserving last-good chunks. */
@Service
public class GuideIndexPersistenceService {
    private final JdbcTemplate jdbc;
    private final AiVectorRepository vectors;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public GuideIndexPersistenceService(JdbcTemplate jdbc, AiVectorRepository vectors,
            PlatformTransactionManager transactionManager, @Qualifier("aiClock") Clock clock) {
        this.jdbc = jdbc;
        this.vectors = vectors;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public boolean begin(AiJobClaim claim, long version) {
        return Boolean.TRUE.equals(transactions.execute(status -> jdbc.update("""
                UPDATE ai_guide_sources s SET status = CASE WHEN s.active_version IS NULL
                    THEN 'PROCESSING' ELSE 'READY' END, updated_at = ?
                 WHERE s.id = ? AND s.desired_version = ?
                   AND EXISTS (SELECT 1 FROM ai_jobs j WHERE j.id = ? AND j.job_type = 'GUIDE_REINDEX'
                     AND j.status = 'PROCESSING' AND j.locked_by = ? AND j.lease_until > ?)
                """, ts(clock.instant()), claimPayloadId(claim), version, claim.id(), claim.leaseToken(), ts(clock.instant())) == 1));
    }

    public boolean stage(AiJobClaim claim, long version, List<GuideChunkInsert> chunks) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            if (chunks.isEmpty() || !currentLease(claim) || !desired(claimPayloadId(claim), version)) return false;
            jdbc.update("DELETE FROM ai_guide_chunks WHERE guide_source_id = ? AND index_version = ?", claimPayloadId(claim), version);
            chunks.forEach(vectors::insertGuideChunk);
            return true;
        }));
    }

    public boolean activate(AiJobClaim claim, long version) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            java.util.UUID sourceId = claimPayloadId(claim);
            if (!currentLease(claim) || !desired(sourceId, version)) return false;
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_guide_chunks WHERE guide_source_id = ? AND index_version = ?", Integer.class, sourceId, version);
            if (count == null || count == 0) return false;
            int changed = jdbc.update("UPDATE ai_guide_sources SET status = 'READY', active_version = ?, indexed_at = ?, updated_at = ? WHERE id = ? AND desired_version = ?", version, ts(clock.instant()), ts(clock.instant()), sourceId, version);
            if (changed == 1) jdbc.update("DELETE FROM ai_guide_chunks WHERE guide_source_id = ? AND index_version <> ?", sourceId, version);
            return changed == 1;
        }));
    }

    public void fail(AiJobClaim claim, long version) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE ai_guide_sources SET status = CASE WHEN active_version IS NULL THEN 'FAILED' ELSE 'READY' END,
                    updated_at = ? WHERE id = ? AND desired_version = ?
                    AND EXISTS (SELECT 1 FROM ai_jobs j WHERE j.id = ? AND j.status = 'PROCESSING'
                      AND j.locked_by = ? AND j.lease_until > ?)
                """, ts(clock.instant()), claimPayloadId(claim), version, claim.id(), claim.leaseToken(), ts(clock.instant())));
    }

    private boolean desired(java.util.UUID sourceId, long version) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_guide_sources WHERE id = ? AND desired_version = ?", Integer.class, sourceId, version);
        return count != null && count == 1;
    }
    private boolean currentLease(AiJobClaim claim) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE id = ? AND status = 'PROCESSING' AND locked_by = ? AND lease_until > ?", Integer.class, claim.id(), claim.leaseToken(), ts(clock.instant()));
        return count != null && count == 1;
    }
    private static java.util.UUID claimPayloadId(AiJobClaim claim) {
        if (claim == null || claim.payload() == null) throw new IllegalArgumentException("Guide claim payload missing");
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(claim.payload(), GuideReindexPayload.class).guideSourceId(); }
        catch (Exception exception) { throw new IllegalArgumentException("Guide claim payload invalid"); }
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(Objects.requireNonNull(value)); }
}
