package com.kbase.document.entity;

import java.util.UUID;

import com.kbase.tag.entity.Tag;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Explicit document/tag relation retaining the same-project integrity column. */
@Entity
@Table(name = "document_tags")
public class DocumentTag {

    @EmbeddedId
    private DocumentTagId id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "document_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(name = "project_id", referencedColumnName = "project_id",
                insertable = false, updatable = false)
    })
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "tag_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(name = "project_id", referencedColumnName = "project_id",
                insertable = false, updatable = false)
    })
    private Tag tag;

    public DocumentTag() {
    }

    public DocumentTag(DocumentTagId id, UUID projectId) {
        this.id = id;
        this.projectId = projectId;
    }

    public DocumentTag(Document document, Tag tag, UUID projectId) {
        this(new DocumentTagId(document == null ? null : document.getId(),
                tag == null ? null : tag.getId()), projectId);
        this.document = document;
        this.tag = tag;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null && document != null && tag != null) {
            id = new DocumentTagId(document.getId(), tag.getId());
        }
        if (projectId == null && document != null && document.getProject() != null) {
            projectId = document.getProject().getId();
        }
    }

    public DocumentTagId getId() {
        return id;
    }

    public void setId(DocumentTagId id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }
}
