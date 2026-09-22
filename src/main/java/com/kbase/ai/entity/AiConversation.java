package com.kbase.ai.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Private project-scoped conversation; membership is intentionally not mapped as an FK. */
@Entity
@Table(name = "ai_conversations")
public class AiConversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Read-only scalar view used by project/user-scoped queries. */
    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    /** Read-only scalar view used by quota and ownership queries. */
    @Column(name = "created_by_user_id", nullable = false, insertable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AiConversation() {
    }

    public AiConversation(Project project, User createdByUser, String title) {
        this.project = project;
        this.projectId = project == null ? null : project.getId();
        this.createdByUser = createdByUser;
        this.createdByUserId = createdByUser == null ? null : createdByUser.getId();
        this.title = title;
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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
        this.projectId = project == null ? null : project.getId();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
        this.createdByUserId = createdByUser == null ? null : createdByUser.getId();
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
