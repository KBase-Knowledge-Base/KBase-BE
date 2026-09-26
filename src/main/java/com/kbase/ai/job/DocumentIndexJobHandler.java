package com.kbase.ai.job;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.extraction.ChunkedDocument;
import com.kbase.ai.extraction.DocumentChunk;
import com.kbase.ai.extraction.DocumentContentExtractor;
import com.kbase.ai.extraction.DocumentExtractionException;
import com.kbase.ai.extraction.DocumentExtractionRequest;
import com.kbase.ai.extraction.ExtractedDocument;
import com.kbase.ai.extraction.DocumentSourceReadException;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.service.AiDocumentSupportPolicy;
import com.kbase.ai.service.BoundedDigestInputStream;
import com.kbase.ai.service.DocumentAiIndexPersistenceService;
import com.kbase.ai.service.DocumentTooLargeException;
import com.kbase.document.entity.Document;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.storage.exception.StorageException;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.exception.StorageUnavailableException;
import com.kbase.storage.model.StoredResource;
import com.kbase.storage.service.StorageService;
import com.kbase.config.properties.UploadProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Durable asynchronous handler for the M5 DOCUMENT_INDEX job. */
@Component
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public final class DocumentIndexJobHandler implements AiJobHandler {

    public static final String FAILURE_UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
    public static final String FAILURE_EXTRACTION_FAILED = "EXTRACTION_FAILED";
    public static final String FAILURE_AI_SERVICE_ERROR = "AI_SERVICE_ERROR";
    public static final String FAILURE_PROCESSING_ERROR = "PROCESSING_ERROR";

    private static final String CODE_STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
    private static final String CODE_STORAGE_NOT_FOUND = "STORAGE_NOT_FOUND";
    private static final String CODE_PROCESSING_ERROR = "PROCESSING_ERROR";

    private final DocumentRepository documentRepository;
    private final DocumentAiIndexRepository indexRepository;
    private final AiDocumentSupportPolicy supportPolicy;
    private final DocumentContentExtractor extractor;
    private final StructureAwareDocumentChunker chunker;
    private final AiEmbeddingModel embeddingModel;
    private final StorageService storageService;
    private final DocumentAiIndexPersistenceService indexPersistence;
    private final UploadProperties uploadProperties;
    private final AiProperties aiProperties;
    private final Clock clock;

    public DocumentIndexJobHandler(DocumentRepository documentRepository,
            DocumentAiIndexRepository indexRepository,
            AiDocumentSupportPolicy supportPolicy,
            DocumentContentExtractor extractor,
            StructureAwareDocumentChunker chunker,
            AiEmbeddingModel embeddingModel,
            StorageService storageService,
            DocumentAiIndexPersistenceService indexPersistence,
            UploadProperties uploadProperties,
            AiProperties aiProperties,
            @Qualifier("aiClock") Clock clock) {
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.indexRepository = Objects.requireNonNull(indexRepository, "indexRepository");
        this.supportPolicy = Objects.requireNonNull(supportPolicy, "supportPolicy");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.storageService = Objects.requireNonNull(storageService, "storageService");
        this.indexPersistence = Objects.requireNonNull(indexPersistence, "indexPersistence");
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AiJobType jobType() {
        return AiJobType.DOCUMENT_INDEX;
    }

    @Override
    public AiJobExecutionResult handle(AiJobClaim claim) {
        if (claim == null || claim.jobType() != AiJobType.DOCUMENT_INDEX
                || claim.projectId() == null || claim.documentId() == null) {
            return AiJobExecutionResult.failure(CODE_PROCESSING_ERROR);
        }

        Document document = documentRepository.findByIdAndProjectId(
                claim.documentId(), claim.projectId()).orElse(null);
        if (document == null) {
            // Document/project deletion is an expected late-worker no-op. The
            // lease-conditional scheduler transition will harmlessly miss.
            return AiJobExecutionResult.success();
        }
        DocumentAiIndex index = indexRepository.findByDocumentIdAndProjectId(
                claim.documentId(), claim.projectId()).orElse(null);
        if (index == null) {
            return AiJobExecutionResult.success();
        }

        long indexVersion = index.getDesiredVersion();
        if (index.getStatus() == com.kbase.ai.enums.DocumentAiIndexStatus.READY
                && index.getActiveVersion() != null
                && index.getActiveVersion() >= indexVersion) {
            return AiJobExecutionResult.success();
        }
        if (!supportPolicy.supports(document.getExtension())) {
            indexPersistence.markUnsupported(claim, indexVersion);
            return AiJobExecutionResult.failure(FAILURE_UNSUPPORTED_FILE_TYPE);
        }
        if (!indexPersistence.beginProcessing(claim, indexVersion)) {
            // Another current worker may have activated the version, or this
            // snapshot may have lost its lease. In either case it must not
            // write a late staging/activation result.
            return AiJobExecutionResult.success();
        }

        try {
            SourcePayload source = readAndExtract(document);
            ChunkedDocument chunked = chunker.chunk(source.extractedDocument());
            if (!chunked.hasChunks()) {
                throw new DocumentExtractionException();
            }

            List<DocumentAiChunkInsert> rows = embed(document, claim, indexVersion, chunked);
            if (!indexPersistence.stage(claim, indexVersion, rows)) {
                return AiJobExecutionResult.success();
            }
            if (!indexPersistence.activate(claim, indexVersion, source.sourceHash())) {
                return AiJobExecutionResult.success();
            }
            return AiJobExecutionResult.success();
        } catch (AiProviderException exception) {
            return providerFailure(claim, indexVersion, exception);
        } catch (StorageUnavailableException exception) {
            return retryableFailure(claim, indexVersion, CODE_STORAGE_UNAVAILABLE,
                    FAILURE_PROCESSING_ERROR);
        } catch (StorageObjectNotFoundException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_PROCESSING_ERROR,
                    CODE_STORAGE_NOT_FOUND);
        } catch (DocumentSourceReadException exception) {
            return retryableFailure(claim, indexVersion, CODE_STORAGE_UNAVAILABLE,
                    FAILURE_PROCESSING_ERROR);
        } catch (StorageException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_PROCESSING_ERROR,
                    CODE_PROCESSING_ERROR);
        } catch (DocumentExtractionException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_EXTRACTION_FAILED,
                    FAILURE_EXTRACTION_FAILED);
        } catch (DocumentTooLargeException | DocumentProcessingException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_PROCESSING_ERROR,
                    CODE_PROCESSING_ERROR);
        } catch (InvalidEmbeddingException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_AI_SERVICE_ERROR,
                    "AI_PROVIDER_INVALID_RESPONSE");
        } catch (DataAccessException exception) {
            return terminalFailure(claim, indexVersion, FAILURE_PROCESSING_ERROR,
                    CODE_PROCESSING_ERROR);
        } catch (RuntimeException exception) {
            // No raw parser/provider/storage exception is persisted or logged.
            return terminalFailure(claim, indexVersion, FAILURE_PROCESSING_ERROR,
                    CODE_PROCESSING_ERROR);
        }
    }

    private SourcePayload readAndExtract(Document document) {
        long maxBytes = uploadProperties.getDocumentMaxSize().toBytes();
        if (maxBytes <= 0 || document.getSizeBytes() <= 0 || document.getSizeBytes() > maxBytes) {
            throw new DocumentProcessingException();
        }

        StoredResource resource = storageService.get(document.getStorageKey());
        if (resource == null || resource.contentLength() > maxBytes
                || resource.contentLength() != document.getSizeBytes()) {
            closeQuietly(resource);
            throw new DocumentProcessingException();
        }

        try (InputStream input = resource.inputStream()) {
            MessageDigest digest = sha256Digest();
            BoundedDigestInputStream bounded = new BoundedDigestInputStream(input, digest, maxBytes);
            ExtractedDocument extracted = extractor.extract(bounded,
                    new DocumentExtractionRequest(document.getExtension(),
                            document.getOriginalFilename(), document.getMimeType(),
                            document.getSizeBytes()));
            bounded.drain();
            if (bounded.count() != document.getSizeBytes()) {
                throw new DocumentProcessingException();
            }
            if (extracted == null) {
                throw new DocumentExtractionException();
            }
            return new SourcePayload(extracted, HexFormat.of().formatHex(bounded.digest()));
        } catch (DocumentExtractionException | DocumentProcessingException exception) {
            throw exception;
        } catch (IOException exception) {
            // Provider stream errors can embed the object URL in their message;
            // keep the category and drop the raw cause like the storage adapter.
            throw new StorageUnavailableException(
                    "Storage service is unavailable. (" + exception.getClass().getSimpleName() + ")", null);
        }
    }

    private List<DocumentAiChunkInsert> embed(Document document, AiJobClaim claim,
            long indexVersion, ChunkedDocument chunked) {
        List<DocumentAiChunkInsert> rows = new java.util.ArrayList<>(chunked.chunks().size());
        for (DocumentChunk chunk : chunked.chunks()) {
            AiEmbeddingResult result = embeddingModel.embed(
                    AiEmbeddingRequest.document(document.getDisplayName(), chunk.content()));
            float[] vector = toVector(result);
            rows.add(new DocumentAiChunkInsert(
                    java.util.UUID.randomUUID(), claim.projectId(), claim.documentId(), indexVersion,
                    chunk.chunkIndex(), chunk.content(), chunk.sourceLocation().pageNumber(),
                    chunk.sourceLocation().slideNumber(), chunk.sourceLocation().sectionTitle(),
                    chunk.tokenCount(), chunk.contentHash(), vector));
        }
        return List.copyOf(rows);
    }

    private float[] toVector(AiEmbeddingResult result) {
        if (result == null || result.vector() == null
                || result.dimensions() != aiProperties.getGemini().getEmbeddingDimensions()
                || result.dimensions() != 768) {
            throw new InvalidEmbeddingException();
        }
        float[] values = new float[result.dimensions()];
        for (int index = 0; index < values.length; index++) {
            Double value = result.vector().get(index);
            if (value == null || !Double.isFinite(value)) {
                throw new InvalidEmbeddingException();
            }
            values[index] = value.floatValue();
            if (!Float.isFinite(values[index])) {
                throw new InvalidEmbeddingException();
            }
        }
        return values;
    }

    private AiJobExecutionResult providerFailure(AiJobClaim claim, long indexVersion,
            AiProviderException exception) {
        String code = providerCode(exception.category());
        if (exception.retryable()) {
            return retryableFailure(claim, indexVersion, code, FAILURE_AI_SERVICE_ERROR);
        }
        return terminalFailure(claim, indexVersion, FAILURE_AI_SERVICE_ERROR, code);
    }

    private AiJobExecutionResult retryableFailure(AiJobClaim claim, long indexVersion,
            String errorCode, String terminalReason) {
        if (claim.attemptCount() >= claim.maxAttempts()) {
            return terminalFailure(claim, indexVersion, terminalReason, errorCode);
        }
        indexPersistence.recordRetry(claim, indexVersion, errorCode);
        return AiJobExecutionResult.retry(clock.instant().plus(aiProperties.getWorker().getRetryBackoff()),
                errorCode);
    }

    private AiJobExecutionResult terminalFailure(AiJobClaim claim, long indexVersion,
            String failureReason, String errorCode) {
        indexPersistence.markFailure(claim, indexVersion, failureReason, errorCode);
        return AiJobExecutionResult.failure(errorCode);
    }

    private static String providerCode(AiProviderErrorCategory category) {
        return switch (category) {
            case TIMEOUT -> "AI_PROVIDER_TIMEOUT";
            case RATE_LIMITED -> "AI_PROVIDER_RATE_LIMITED";
            case UNAVAILABLE -> "AI_PROVIDER_UNAVAILABLE";
            case CONFIGURATION -> "AI_PROVIDER_CONFIGURATION";
            case INVALID_RESPONSE -> "AI_PROVIDER_INVALID_RESPONSE";
        };
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void closeQuietly(StoredResource resource) {
        if (resource == null || resource.inputStream() == null) {
            return;
        }
        try {
            resource.inputStream().close();
        } catch (IOException ignored) {
            // The source is rejected before parsing; no raw close detail is useful.
        }
    }

    private record SourcePayload(ExtractedDocument extractedDocument, String sourceHash) {
    }

    private static final class InvalidEmbeddingException extends RuntimeException {
        private InvalidEmbeddingException() {
            super("Embedding response was invalid.");
        }
    }

    private static final class DocumentProcessingException extends RuntimeException {
        private DocumentProcessingException() {
            super("Document processing failed.");
        }
    }
}
