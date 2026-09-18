package com.kbase.folder.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.entity.Project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Hierarchical project folder with a same-project composite parent join. */
@Entity
@Table(name = "folders", uniqueConstraints = @UniqueConstraint(
        name = "uq_folders_id_project",
        columnNames = {"id", "project_id"}))
public class Folder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Read-only column view required by Hibernate for composite target joins. */
    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    /** Writable FK value; the parent association below is a read-only view. */
    @Column(name = "parent_id")
    private UUID parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "parent_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(name = "project_id", referencedColumnName = "project_id",
                insertable = false, updatable = false)
    })
    private Folder parent;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Folder() {
    }

    public Folder(Project project, Folder parent, String name) {
        this.project = project;
        this.parent = parent;
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        syncParentId();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        syncParentId();
        updatedAt = Instant.now();
    }

    private void syncParentId() {
        if (parent != null) {
            parentId = parent.getId();
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

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public Folder getParent() {
        return parent;
    }

    public void setParent(Folder parent) {
        this.parent = parent;
        this.parentId = parent == null ? null : parent.getId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
