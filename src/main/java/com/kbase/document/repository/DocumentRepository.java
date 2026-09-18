package com.kbase.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.document.entity.Document;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence and metadata-search entry point for documents. */
public interface DocumentRepository
        extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndProjectId(UUID documentId, UUID projectId);

    boolean existsByFolderId(UUID folderId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByUploadedById(UUID userId);

    long countByProjectId(UUID projectId);

    @Query("""
            select d.id as id,
                   d.storageKey as storageKey
            from Document d
            where d.project.id = :projectId
            """)
    List<DocumentStorageKeyProjection> findStorageKeysByProjectId(
            @Param("projectId") UUID projectId);

    @EntityGraph(attributePaths = {"uploadedBy", "folder", "category"})
    @Query("""
            select d
            from Document d
            where d.id = :documentId
            """)
    Optional<Document> findDetailById(@Param("documentId") UUID documentId);
}
