package com.kbase.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.extraction.ChunkedDocument;
import com.kbase.ai.extraction.DocumentChunk;
import com.kbase.ai.extraction.DocumentContentExtractor;
import com.kbase.ai.extraction.DocumentExtractionException;
import com.kbase.ai.extraction.DocumentExtractionRequest;
import com.kbase.ai.extraction.ExtractedBlock;
import com.kbase.ai.extraction.ExtractedDocument;
import com.kbase.ai.extraction.SourceLocation;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.service.AiDocumentSupportPolicy;
import com.kbase.ai.service.DocumentAiIndexPersistenceService;
import com.kbase.config.properties.UploadProperties;
import com.kbase.document.entity.Document;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.project.entity.Project;
import com.kbase.storage.model.StoredResource;
import com.kbase.storage.service.StorageService;
import com.kbase.user.entity.User;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class DocumentIndexJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-22T16:00:00Z");

    @Test
    void successfulDocumentIndexReadsThroughStorageEmbedsDocumentsAndClosesStream() throws IOException {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        byte[] source = "source bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TrackingInputStream input = new TrackingInputStream(source);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Document document = document(documentId, projectId, source.length, "txt");
        DocumentAiIndex index = new DocumentAiIndex(documentId, projectId,
                DocumentAiIndexStatus.PENDING, 1, "chunk-v1", "gemini-embedding-2", 768);
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(index));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(persistence.stage(any(), anyLong(), any())).thenReturn(true);
        when(persistence.activate(any(), anyLong(), any())).thenReturn(true);
        when(storage.get(document.getStorageKey())).thenReturn(new StoredResource(input, source.length, "text/plain"));
        when(extractor.extract(any(), any(DocumentExtractionRequest.class)))
                .thenReturn(new ExtractedDocument(List.of(new ExtractedBlock("hello", SourceLocation.none()))));
        DocumentChunk chunk = new DocumentChunk(0, "hello", SourceLocation.none(), 1, "hash");
        when(chunker.chunk(any(ExtractedDocument.class))).thenReturn(new ChunkedDocument("chunk-v1", List.of(chunk)));
        when(embeddings.embed(any(AiEmbeddingRequest.class)))
                .thenReturn(new AiEmbeddingResult(Collections.nCopies(768, 0.25)));

        DocumentIndexJobHandler handler = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence);
        AiJobExecutionResult result = handler.handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.SUCCESS);
        assertThat(input.closed).isTrue();
        verify(embeddings).embed(any(AiEmbeddingRequest.class));
        verify(persistence).activate(any(), anyLong(), any());
    }

    @Test
    void unsupportedExtensionNeverCallsExtractorOrEmbedding() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Document document = document(documentId, projectId, 3, "xlsx");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.FAILURE);
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_FILE_TYPE");
        verify(extractor, never()).extract(any(), any());
        verify(embeddings, never()).embed(any());
        verify(storage, never()).get(any());
    }

    @Test
    void retryableProviderFailureUsesSafeBoundedCode() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] source = "retry".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Document document = document(documentId, projectId, source.length, "txt");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(storage.get(any())).thenReturn(new StoredResource(new ByteArrayInputStream(source), source.length, "text/plain"));
        when(extractor.extract(any(), any())).thenReturn(new ExtractedDocument(List.of(
                new ExtractedBlock("retry text", SourceLocation.none()))));
        when(chunker.chunk(any())).thenReturn(new ChunkedDocument("chunk-v1", List.of(
                new DocumentChunk(0, "retry text", SourceLocation.none(), 2, "hash"))));
        when(embeddings.embed(any())).thenThrow(new AiProviderException(AiProviderErrorCategory.UNAVAILABLE));

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.RETRY);
        assertThat(result.errorCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
        verify(persistence).recordRetry(any(), anyLong(), org.mockito.ArgumentMatchers.eq("AI_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void emptyExtractionBecomesTerminalFailureWithoutEmbedding() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] source = "blank source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Document document = document(documentId, projectId, source.length, "txt");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(storage.get(any())).thenReturn(new StoredResource(new ByteArrayInputStream(source), source.length,
                "text/plain"));
        when(extractor.extract(any(), any())).thenReturn(new ExtractedDocument(List.of()));
        when(chunker.chunk(any())).thenReturn(new ChunkedDocument("chunk-v1", List.of()));

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.FAILURE);
        assertThat(result.errorCode()).isEqualTo("EXTRACTION_FAILED");
        verify(embeddings, never()).embed(any());
        verify(persistence).markFailure(any(), anyLong(), org.mockito.ArgumentMatchers.eq("EXTRACTION_FAILED"),
                org.mockito.ArgumentMatchers.eq("EXTRACTION_FAILED"));
    }

    @Test
    void corruptExtractionBecomesTerminalFailureWithoutEmbedding() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] source = new byte[] {1, 2, 3};
        Document document = document(documentId, projectId, source.length, "pdf");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(storage.get(any())).thenReturn(new StoredResource(new ByteArrayInputStream(source), source.length,
                "application/pdf"));
        when(extractor.extract(any(), any())).thenThrow(new DocumentExtractionException());

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.FAILURE);
        assertThat(result.errorCode()).isEqualTo("EXTRACTION_FAILED");
        verify(chunker, never()).chunk(any());
        verify(embeddings, never()).embed(any());
    }

    @Test
    void partialEmbeddingFailureDoesNotStageOrActivatePartialChunks() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] source = "two chunks".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Document document = document(documentId, projectId, source.length, "txt");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(storage.get(any())).thenReturn(new StoredResource(new ByteArrayInputStream(source), source.length,
                "text/plain"));
        when(extractor.extract(any(), any())).thenReturn(new ExtractedDocument(List.of(
                new ExtractedBlock("first", SourceLocation.none()))));
        DocumentChunk first = new DocumentChunk(0, "first", SourceLocation.none(), 1, "first-hash");
        DocumentChunk second = new DocumentChunk(1, "second", SourceLocation.none(), 1, "second-hash");
        when(chunker.chunk(any())).thenReturn(new ChunkedDocument("chunk-v1", List.of(first, second)));
        when(embeddings.embed(any())).thenReturn(new AiEmbeddingResult(Collections.nCopies(768, 0.25)))
                .thenThrow(new AiProviderException(AiProviderErrorCategory.UNAVAILABLE));

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 1, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.RETRY);
        verify(persistence, never()).stage(any(), anyLong(), any());
        verify(persistence, never()).activate(any(), anyLong(), any());
    }

    @Test
    void retryableProviderFailureAtMaxAttemptsBecomesTerminalFailure() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentAiIndexRepository indexes = mock(DocumentAiIndexRepository.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        StructureAwareDocumentChunker chunker = mock(StructureAwareDocumentChunker.class);
        AiEmbeddingModel embeddings = mock(AiEmbeddingModel.class);
        StorageService storage = mock(StorageService.class);
        DocumentAiIndexPersistenceService persistence = mock(DocumentAiIndexPersistenceService.class);
        UUID documentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] source = "final retry".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Document document = document(documentId, projectId, source.length, "txt");
        when(documents.findByIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(document));
        when(indexes.findByDocumentIdAndProjectId(documentId, projectId)).thenReturn(java.util.Optional.of(
                new DocumentAiIndex(documentId, projectId, DocumentAiIndexStatus.PENDING, 1,
                        "chunk-v1", "gemini-embedding-2", 768)));
        when(persistence.beginProcessing(any(), anyLong())).thenReturn(true);
        when(storage.get(any())).thenReturn(new StoredResource(new ByteArrayInputStream(source), source.length,
                "text/plain"));
        when(extractor.extract(any(), any())).thenReturn(new ExtractedDocument(List.of(
                new ExtractedBlock("final retry", SourceLocation.none()))));
        when(chunker.chunk(any())).thenReturn(new ChunkedDocument("chunk-v1", List.of(
                new DocumentChunk(0, "final retry", SourceLocation.none(), 2, "hash"))));
        when(embeddings.embed(any())).thenThrow(new AiProviderException(AiProviderErrorCategory.TIMEOUT));

        AiJobExecutionResult result = handler(documents, indexes, extractor, chunker, embeddings,
                storage, persistence).handle(claim(documentId, projectId, 3, 3));

        assertThat(result.outcome()).isEqualTo(AiJobExecutionOutcome.FAILURE);
        assertThat(result.errorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
        verify(persistence).markFailure(any(), anyLong(), org.mockito.ArgumentMatchers.eq("AI_SERVICE_ERROR"),
                org.mockito.ArgumentMatchers.eq("AI_PROVIDER_TIMEOUT"));
        verify(persistence, never()).recordRetry(any(), anyLong(), any());
    }

    private DocumentIndexJobHandler handler(DocumentRepository documents, DocumentAiIndexRepository indexes,
            DocumentContentExtractor extractor, StructureAwareDocumentChunker chunker,
            AiEmbeddingModel embeddings, StorageService storage,
            DocumentAiIndexPersistenceService persistence) {
        return new DocumentIndexJobHandler(documents, indexes, new AiDocumentSupportPolicy(), extractor,
                chunker, embeddings, storage, persistence, new UploadProperties(), new AiProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiJobClaim claim(UUID documentId, UUID projectId, int attempt, int maxAttempts) {
        return new AiJobClaim(UUID.randomUUID(), AiJobType.DOCUMENT_INDEX, projectId, documentId,
                UUID.randomUUID(), "document-index:" + documentId + ":v1", "{}", NOW, attempt,
                maxAttempts, NOW.plusSeconds(120), "worker:lease");
    }

    private static Document document(UUID documentId, UUID projectId, long size, String extension) {
        Document document = mock(Document.class);
        Project project = mock(Project.class);
        User user = mock(User.class);
        when(project.getId()).thenReturn(projectId);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(document.getId()).thenReturn(documentId);
        when(document.getProject()).thenReturn(project);
        when(document.getSizeBytes()).thenReturn(size);
        when(document.getExtension()).thenReturn(extension);
        when(document.getStorageKey()).thenReturn("projects/" + projectId + "/documents/" + documentId + "." + extension);
        when(document.getOriginalFilename()).thenReturn("fixture." + extension);
        when(document.getMimeType()).thenReturn("text/plain");
        when(document.getDisplayName()).thenReturn("Fixture");
        when(document.getUploadedBy()).thenReturn(user);
        return document;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
