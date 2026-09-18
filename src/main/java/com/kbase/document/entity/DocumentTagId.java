package com.kbase.document.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite identifier for the explicit document/tag junction entity. */
@Embeddable
public class DocumentTagId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    public DocumentTagId() {
    }

    public DocumentTagId(UUID documentId, UUID tagId) {
        this.documentId = documentId;
        this.tagId = tagId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentTagId that)) {
            return false;
        }
        return Objects.equals(documentId, that.documentId)
                && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, tagId);
    }
}
