package com.kbase.ai.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.AiGuideSourceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Allowlisted KBase Guide source metadata; Guide is separate from project corpus. */
@Entity
@Table(name = "ai_guide_sources", uniqueConstraints = @UniqueConstraint(
        name = "uq_ai_guide_sources_source_key", columnNames = "source_key"))
public class AiGuideSource {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_key", nullable = false, length = 1024, unique = true)
    private String sourceKey;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "active_version")
    private Long activeVersion;

    @Column(name = "desired_version", nullable = false)
    private long desiredVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiGuideSourceStatus status;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiGuideSource() {
    }

    public AiGuideSource(String sourceKey, String contentHash, long desiredVersion,
            AiGuideSourceStatus status) {
        this.sourceKey = sourceKey;
        this.contentHash = contentHash;
        this.desiredVersion = desiredVersion;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
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

    public AiGuideSourceStatus getStatus() {
        return status;
    }

    public void setStatus(AiGuideSourceStatus status) {
        this.status = status;
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
