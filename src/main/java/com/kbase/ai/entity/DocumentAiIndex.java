package com.kbase.ai.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.DocumentAiIndexStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Relational lifecycle state for one document AI index. Vector chunks use JDBC. */
@Entity
@Table(name = "document_ai_indexes")
public class DocumentAiIndex {

    @Id
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentAiIndexStatus status;

    @Column(name = "failure_reason", length = 120)
    private String failureReason;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "active_version")
    private Long activeVersion;

    @Column(name = "desired_version", nullable = false)
    private long desiredVersion;

    @Column(name = "chunking_version", nullable = false, length = 100)
    private String chunkingVersion;

    @Column(name = "embedding_model", nullable = false, length = 150)
    private String embeddingModel;

    @Column(name = "embedding_dimensions", nullable = false)
    private int embeddingDimensions;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DocumentAiIndex() {
    }

    public DocumentAiIndex(UUID documentId, UUID projectId, DocumentAiIndexStatus status,
            long desiredVersion, String chunkingVersion, String embeddingModel,
            int embeddingDimensions) {
        this.documentId = documentId;
        this.projectId = projectId;
        this.status = status;
        this.desiredVersion = desiredVersion;
        this.chunkingVersion = chunkingVersion;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (attemptCount < 0) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public DocumentAiIndexStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentAiIndexStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public Long getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(Long activeVersion) {
        this.activeVersion = activeVersion;
    }

    public long getDesiredVersion() {
        return desiredVersion;
    }

    public void setDesiredVersion(long desiredVersion) {
        this.desiredVersion = desiredVersion;
    }

    public String getChunkingVersion() {
        return chunkingVersion;
    }

    public void setChunkingVersion(String chunkingVersion) {
        this.chunkingVersion = chunkingVersion;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
