package com.kbase.ai.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.AiJob;
import com.kbase.ai.enums.AiJobStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/** Durable job-state persistence; worker claiming is deferred to M3. */
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    List<AiJob> findAllByStatusAndRunAtLessThanEqualOrderByRunAtAsc(
            AiJobStatus status, Instant now);

    List<AiJob> findAllByProjectId(UUID projectId);

    List<AiJob> findAllByDocumentId(UUID documentId);

    boolean existsByDedupKeyAndStatusIn(String dedupKey, List<AiJobStatus> statuses);
}
