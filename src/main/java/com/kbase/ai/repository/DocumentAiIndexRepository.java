package com.kbase.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.DocumentAiIndexStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Relational persistence for document AI lifecycle state. */
public interface DocumentAiIndexRepository extends JpaRepository<DocumentAiIndex, UUID> {

    Optional<DocumentAiIndex> findByDocumentIdAndProjectId(UUID documentId, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from DocumentAiIndex i
            where i.documentId = :documentId
              and i.projectId = :projectId
            """)
    Optional<DocumentAiIndex> findForUpdateByDocumentIdAndProjectId(
            @Param("documentId") UUID documentId, @Param("projectId") UUID projectId);

    List<DocumentAiIndex> findAllByProjectIdAndStatus(
            UUID projectId, DocumentAiIndexStatus status);
}
