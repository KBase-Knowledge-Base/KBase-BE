package com.kbase.document.repository;

import java.util.List;
import java.util.UUID;

import com.kbase.document.entity.DocumentTag;
import com.kbase.document.entity.DocumentTagId;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Explicit document/tag junction persistence queries. */
public interface DocumentTagRepository extends JpaRepository<DocumentTag, DocumentTagId> {

    @EntityGraph(attributePaths = "tag")
    List<DocumentTag> findAllByIdDocumentId(UUID documentId);

    long deleteAllByIdDocumentId(UUID documentId);

    boolean existsByIdDocumentIdAndIdTagId(UUID documentId, UUID tagId);
}
