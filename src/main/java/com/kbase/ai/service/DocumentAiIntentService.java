package com.kbase.ai.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.document.entity.Document;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records upload-time AI intent without reading the binary or invoking a provider. */
@Service
public class DocumentAiIntentService {

    public static final long INITIAL_INDEX_VERSION = 1L;
    public static final String CHUNKING_VERSION = "chunk-v1";
    public static final int EMBEDDING_DIMENSIONS = 768;

    private final DocumentAiIndexRepository indexRepository;
    private final AiJobStore jobStore;
    private final AiDocumentSupportPolicy supportPolicy;
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public DocumentAiIntentService(
            DocumentAiIndexRepository indexRepository,
            AiJobStore jobStore,
            AiDocumentSupportPolicy supportPolicy,
            AiProperties properties,
            @Qualifier("aiClock") Clock clock) {
        this.indexRepository = Objects.requireNonNull(indexRepository, "indexRepository");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore");
        this.supportPolicy = Objects.requireNonNull(supportPolicy, "supportPolicy");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Must be called after the Core document row and tags are persisted while
     * the upload transaction is still open.
     */
    @Transactional
    public void recordUploadIntent(Document document) {
        Objects.requireNonNull(document, "document");
        Instant now = clock.instant();
        boolean supported = supportPolicy.supports(document.getExtension());
        DocumentAiIndex index = indexRepository.findByDocumentIdAndProjectId(
                document.getId(), document.getProject().getId()).orElse(null);

        if (index == null) {
            index = new DocumentAiIndex(
                    document.getId(), document.getProject().getId(),
                    supported ? DocumentAiIndexStatus.PENDING : DocumentAiIndexStatus.UNSUPPORTED,
                    INITIAL_INDEX_VERSION, CHUNKING_VERSION,
                    properties.getGemini().getEmbeddingModel(), EMBEDDING_DIMENSIONS);
            index.setCreatedAt(now);
            index.setUpdatedAt(now);
            if (!supported) {
                index.setFailureReason("UNSUPPORTED_FILE_TYPE");
            }
            indexRepository.saveAndFlush(index);
        } else if (!supported) {
            // A repeated lifecycle callback must stay idempotent and must not
            // turn an unsupported Core file into a durable worker job.
            if (index.getStatus() != DocumentAiIndexStatus.UNSUPPORTED) {
                index.setStatus(DocumentAiIndexStatus.UNSUPPORTED);
                index.setFailureReason("UNSUPPORTED_FILE_TYPE");
                index.setLastErrorCode(null);
                indexRepository.saveAndFlush(index);
            }
            return;
        } else if (index.getStatus() == DocumentAiIndexStatus.PENDING) {
            // Ensure a retried callback repairs a missing active intent without
            // creating a duplicate active job.
            index.setUpdatedAt(now);
            indexRepository.saveAndFlush(index);
        } else {
            return;
        }

        if (supported) {
            jobStore.enqueueActive(new AiJobSchedule(
                    AiJobType.DOCUMENT_INDEX,
                    document.getProject().getId(),
                    document.getId(),
                    document.getUploadedBy().getId(),
                    documentIndexDedupKey(document.getId(), INITIAL_INDEX_VERSION),
                    "{\"schemaVersion\":1,\"desiredVersion\":1}",
                    now,
                    properties.getWorker().getMaxAttempts()));
        }
    }

    public static String documentIndexDedupKey(java.util.UUID documentId, long desiredVersion) {
        return "document-index:" + Objects.requireNonNull(documentId, "documentId")
                + ":v" + desiredVersion;
    }
}
