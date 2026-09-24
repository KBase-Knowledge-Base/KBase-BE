package com.kbase.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.service.DocumentAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;

import org.junit.jupiter.api.Test;

class DocumentAiIndexApplicationServiceTest {

    @Test
    void manualRetryResetsSafeStateAndEnqueuesTheSameDesiredVersion() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentAuthorizationService authorization = mock(DocumentAuthorizationService.class);
        AiJobStore jobs = mock(AiJobStore.class);
        AiProperties properties = new AiProperties();
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Document document = mock(Document.class);
        DocumentAiIndex index = new DocumentAiIndex(documentId, projectId,
                DocumentAiIndexStatus.FAILED, 3, "chunk-v1", "gemini-embedding-2", 768);
        index.setFailureReason("AI_SERVICE_ERROR");
        index.setLastErrorCode("AI_PROVIDER_UNAVAILABLE");
        index.setAttemptCount(3);
        when(documents.findDetailById(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        com.kbase.project.entity.Project project = project(projectId);
        when(document.getProject()).thenReturn(project);
        com.kbase.user.entity.User uploadedBy = user();
        when(document.getUploadedBy()).thenReturn(uploadedBy);
        when(indexes.findForUpdateByDocumentIdAndProjectId(documentId, projectId))
                .thenReturn(Optional.of(index));
        when(jobs.now()).thenReturn(java.time.Instant.parse("2026-09-22T16:00:00Z"));

        DocumentAiIndexStatusView view = new DocumentAiIndexApplicationService(
                documents, indexes, authorization, jobs, properties)
                .requestManualRetry(documentId, mock(CustomUserPrincipal.class));

        assertThat(view.status()).isEqualTo(DocumentAiIndexStatus.PENDING);
        assertThat(view.desiredVersion()).isEqualTo(3);
        assertThat(index.getFailureReason()).isNull();
        assertThat(index.getLastErrorCode()).isNull();
        assertThat(index.getAttemptCount()).isZero();
        verify(indexes).saveAndFlush(index);
        verify(jobs).enqueueActive(any());
    }

    @Test
    void manualRetryIsOnlyAllowedFromFailed() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentAuthorizationService authorization = mock(DocumentAuthorizationService.class);
        AiJobStore jobs = mock(AiJobStore.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Document document = mock(Document.class);
        DocumentAiIndex index = new DocumentAiIndex(documentId, projectId,
                DocumentAiIndexStatus.READY, 1, "chunk-v1", "gemini-embedding-2", 768);
        when(documents.findDetailById(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        com.kbase.project.entity.Project project = project(projectId);
        when(document.getProject()).thenReturn(project);
        when(indexes.findForUpdateByDocumentIdAndProjectId(documentId, projectId))
                .thenReturn(Optional.of(index));

        DocumentAiIndexApplicationService service = new DocumentAiIndexApplicationService(
                documents, indexes, authorization, jobs, new AiProperties());
        assertThatThrownBy(() -> service.requestManualRetry(documentId, mock(CustomUserPrincipal.class)))
                .isInstanceOf(AiIndexRetryNotAllowedException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_INDEX_RETRY_NOT_ALLOWED);
    }

    private static com.kbase.project.entity.Project project(UUID id) {
        com.kbase.project.entity.Project project = mock(com.kbase.project.entity.Project.class);
        when(project.getId()).thenReturn(id);
        return project;
    }

    private static com.kbase.user.entity.User user() {
        com.kbase.user.entity.User user = mock(com.kbase.user.entity.User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        return user;
    }
}
