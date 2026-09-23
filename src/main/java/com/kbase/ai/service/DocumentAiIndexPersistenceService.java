package com.kbase.ai.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Short-transaction lifecycle boundary for document index staging and activation. */
@Service
public class DocumentAiIndexPersistenceService {

    private final AiVectorRepository vectorRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DocumentAiIndexPersistenceService(AiVectorRepository vectorRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("aiClock") Clock clock) {
        this.vectorRepository = Objects.requireNonNull(vectorRepository, "vectorRepository");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean beginProcessing(AiJobClaim claim, long indexVersion) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.markDocumentIndexProcessing(
                        claim.projectId(), claim.documentId(), indexVersion, claim.id(),
                        claim.leaseToken(), clock.instant()) == 1));
    }

    public boolean stage(AiJobClaim claim, long indexVersion, List<DocumentAiChunkInsert> chunks) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.stageDocumentChunks(
                        claim.projectId(), claim.documentId(), indexVersion, claim.id(),
                        claim.leaseToken(), clock.instant(), chunks)));
    }

    public boolean activate(AiJobClaim claim, long indexVersion, String sourceHash) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.activateDocumentVersion(
                        claim.projectId(), claim.documentId(), indexVersion, sourceHash,
                        claim.id(), claim.leaseToken(), clock.instant())));
    }

    public boolean recordRetry(AiJobClaim claim, long indexVersion, String errorCode) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.markDocumentIndexRetry(
                        claim.projectId(), claim.documentId(), indexVersion, claim.id(),
                        claim.leaseToken(), errorCode, clock.instant()) == 1));
    }

    public boolean markFailure(AiJobClaim claim, long indexVersion, String failureReason,
            String errorCode) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.markDocumentIndexFailure(
                        claim.projectId(), claim.documentId(), indexVersion, claim.id(),
                        claim.leaseToken(), failureReason, errorCode, clock.instant()) == 1));
    }

    public boolean markUnsupported(AiJobClaim claim, long indexVersion) {
        Objects.requireNonNull(claim, "claim");
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                vectorRepository.markDocumentIndexUnsupported(
                        claim.projectId(), claim.documentId(), indexVersion, claim.id(),
                        claim.leaseToken(), clock.instant()) == 1));
    }
}
