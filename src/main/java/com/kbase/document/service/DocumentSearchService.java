package com.kbase.document.service;

import java.util.Objects;
import java.util.UUID;

import com.kbase.document.dto.response.DocumentSummaryResponse;
import com.kbase.document.entity.Document;
import com.kbase.document.mapper.DocumentMapper;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentSpecification;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.pagination.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorized, project-scoped discovery of document metadata only. */
@Service
public class DocumentSearchService {

    private final DocumentRepository documentRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final DocumentMapper documentMapper;

    public DocumentSearchService(
            DocumentRepository documentRepository,
            ProjectAuthorizationService projectAuthorizationService,
            DocumentMapper documentMapper) {
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.projectAuthorizationService = Objects.requireNonNull(
                projectAuthorizationService, "projectAuthorizationService");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper");
    }

    /**
     * Checks access before querying and always composes the required
     * {@code document.project.id = projectId} predicate. No binary-content,
     * full-text, embedding, or semantic search is involved.
     */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> search(
            UUID projectId,
            DocumentSearchCriteria criteria,
            CustomUserPrincipal principal,
            Pageable pageable) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(pageable, "pageable");

        projectAuthorizationService.requireProjectAccess(projectId, principal);
        Page<Document> page = documentRepository.findAll(
                DocumentSpecification.search(projectId, criteria.q(), criteria.folderId(),
                        criteria.categoryId(), criteria.tagId(), criteria.fileKind(),
                        criteria.uploadedBy(), criteria.createdFrom(), criteria.createdTo()),
                pageable);
        return PageResponse.from(page, documentMapper::toSummaryResponse);
    }
}
