package com.kbase.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.DocumentAiIndexStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/** Relational persistence for document AI lifecycle state. */
public interface DocumentAiIndexRepository extends JpaRepository<DocumentAiIndex, UUID> {

    Optional<DocumentAiIndex> findByDocumentIdAndProjectId(UUID documentId, UUID projectId);

    List<DocumentAiIndex> findAllByProjectIdAndStatus(
            UUID projectId, DocumentAiIndexStatus status);
}
