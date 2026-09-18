package com.kbase.document.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.category.entity.Category;
import com.kbase.document.enums.FileKind;
import com.kbase.folder.entity.Folder;
import com.kbase.project.entity.Project;
import com.kbase.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Persistent document metadata; binary content is kept in MinIO, outside this entity. */
@Entity
@Table(name = "documents", uniqueConstraints = {
    @UniqueConstraint(name = "uq_documents_storage_key", columnNames = "storage_key"),
    @UniqueConstraint(name = "uq_documents_id_project", columnNames = {"id", "project_id"})
})
public class Document {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Read-only column view required by Hibernate for composite target joins. */
    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    /** Writable FK value; the composite associations below are read-only views. */
    @Column(name = "folder_id")
    private UUID folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "folder_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(name = "project_id", referencedColumnName = "project_id",
                insertable = false, updatable = false)
    })
    private Folder folder;

    /** Writable FK value; the composite association below is a read-only view. */
    @Column(name = "category_id")
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "category_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(name = "project_id", referencedColumnName = "project_id",
                insertable = false, updatable = false)
    })
    private Category category;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, length = 20)
    private FileKind fileKind;

    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 1024, unique = true)
    private String storageKey;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Document() {
    }

    public Document(Project project, User uploadedBy, String displayName,
            String originalFilename, FileKind fileKind, String extension,
            String mimeType, long sizeBytes, String storageKey) {
        this.project = project;
        this.uploadedBy = uploadedBy;
        this.displayName = displayName;
        this.originalFilename = originalFilename;
        this.fileKind = fileKind;
        this.extension = extension;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        syncRelatedIds();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        syncRelatedIds();
        updatedAt = Instant.now();
    }

    private void syncRelatedIds() {
        if (folder != null) {
            folderId = folder.getId();
        }
        if (category != null) {
            categoryId = category.getId();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
        this.folderId = folder == null ? null : folder.getId();
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
        this.categoryId = category == null ? null : category.getId();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public FileKind getFileKind() {
        return fileKind;
    }

    public void setFileKind(FileKind fileKind) {
        this.fileKind = fileKind;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
