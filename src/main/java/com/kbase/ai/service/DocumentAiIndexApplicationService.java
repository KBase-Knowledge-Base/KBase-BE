package com.kbase.ai.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.service.DocumentAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for authorized status reads and bounded manual retry. */
@Service
public class DocumentAiIndexApplicationService {

    private final DocumentRepository documentRepository;
    private final DocumentAiIndexRepository indexRepository;
    private final DocumentAuthorizationService documentAuthorizationService;
    private final AiJobStore jobStore;
    private final AiProperties properties;

    public DocumentAiIndexApplicationService(DocumentRepository documentRepository,
            DocumentAiIndexRepository indexRepository,
            DocumentAuthorizationService documentAuthorizationService,
            AiJobStore jobStore,
            AiProperties properties) {
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.indexRepository = Objects.requireNonNull(indexRepository, "indexRepository");
        this.documentAuthorizationService = Objects.requireNonNull(
                documentAuthorizationService, "documentAuthorizationService");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Transactional(readOnly = true)
    public DocumentAiIndexStatusView getStatus(UUID documentId, CustomUserPrincipal principal) {
        Document document = requireDocument(documentId);
        documentAuthorizationService.requireReadPermission(document, principal);
        return view(document, requireIndex(document));
    }

    @Transactional(readOnly = true)
    public DocumentAiIndexStatusView getStatus(UUID projectId, UUID documentId,
            CustomUserPrincipal principal) {
        Document document = requireDocument(documentId);
        if (!Objects.equals(projectId, document.getProject().getId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        documentAuthorizationService.requireReadPermission(document, principal);
        return view(document, requireIndex(document));
    }

    @Transactional
    public DocumentAiIndexStatusView requestManualRetry(UUID documentId,
            CustomUserPrincipal principal) {
        Document document = requireDocument(documentId);
        documentAuthorizationService.requireModifyPermission(document, principal);
        DocumentAiIndex index = requireIndexForUpdate(document);
        if (index.getStatus() != DocumentAiIndexStatus.FAILED) {
            throw new AiIndexRetryNotAllowedException();
        }

        index.setStatus(DocumentAiIndexStatus.PENDING);
        index.setFailureReason(null);
        index.setLastErrorCode(null);
        index.setAttemptCount(0);
        indexRepository.saveAndFlush(index);
        Instant runAt = jobStore.now();
        jobStore.enqueueActive(new AiJobSchedule(
                AiJobType.DOCUMENT_INDEX,
                document.getProject().getId(), document.getId(), document.getUploadedBy().getId(),
                DocumentAiIntentService.documentIndexDedupKey(document.getId(), index.getDesiredVersion()),
                "{\"schemaVersion\":1,\"desiredVersion\":" + index.getDesiredVersion() + "}",
                runAt, properties.getWorker().getMaxAttempts()));
        return view(document, index);
    }

    @Transactional
    public DocumentAiIndexStatusView requestManualRetry(UUID projectId, UUID documentId,
            CustomUserPrincipal principal) {
        Document document = requireDocument(documentId);
        if (!Objects.equals(projectId, document.getProject().getId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        documentAuthorizationService.requireModifyPermission(document, principal);
        DocumentAiIndex index = requireIndexForUpdate(document);
        if (index.getStatus() != DocumentAiIndexStatus.FAILED) {
            throw new AiIndexRetryNotAllowedException();
        }
        index.setStatus(DocumentAiIndexStatus.PENDING);
        index.setFailureReason(null);
        index.setLastErrorCode(null);
        index.setAttemptCount(0);
        indexRepository.saveAndFlush(index);
        jobStore.enqueueActive(new AiJobSchedule(
                AiJobType.DOCUMENT_INDEX, projectId, documentId, document.getUploadedBy().getId(),
                DocumentAiIntentService.documentIndexDedupKey(documentId, index.getDesiredVersion()),
                "{\"schemaVersion\":1,\"desiredVersion\":" + index.getDesiredVersion() + "}",
                jobStore.now(), properties.getWorker().getMaxAttempts()));
        return view(document, index);
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findDetailById(Objects.requireNonNull(documentId, "documentId"))
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private DocumentAiIndex requireIndex(Document document) {
        return indexRepository.findByDocumentIdAndProjectId(
                        document.getId(), document.getProject().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private DocumentAiIndex requireIndexForUpdate(Document document) {
        return indexRepository.findForUpdateByDocumentIdAndProjectId(
                        document.getId(), document.getProject().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private static DocumentAiIndexStatusView view(Document document, DocumentAiIndex index) {
        return new DocumentAiIndexStatusView(
                document.getId(), document.getProject().getId(), index.getStatus(),
                index.getFailureReason(), index.getActiveVersion(), index.getDesiredVersion(),
                index.getAttemptCount(), index.getLastErrorCode(), index.getIndexedAt());
    }
}
