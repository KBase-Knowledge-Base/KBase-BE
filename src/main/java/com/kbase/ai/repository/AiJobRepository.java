package com.kbase.ai.repository;

import java.util.UUID;
import java.util.List;

import com.kbase.ai.entity.AiJob;

import org.springframework.data.jpa.repository.JpaRepository;

/** Passive durable job-state mapping; worker claiming belongs to AiJobStore. */
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    List<AiJob> findAllByProjectId(UUID projectId);

    List<AiJob> findAllByDocumentId(UUID documentId);
}
