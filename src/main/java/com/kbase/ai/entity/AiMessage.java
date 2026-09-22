package com.kbase.ai.entity;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.enums.AiAnswerType;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.AiMessageRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Persisted user message or assistant generation state. */
@Entity
@Table(name = "ai_messages")
public class AiMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Column(name = "conversation_id", nullable = false, insertable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AiMessageRole role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false, length = 20)
    private AiGenerationStatus generationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", length = 20)
    private AiAnswerType answerType;

    @Column(name = "model", length = 150)
    private String model;

    @Column(name = "failure_code", length = 120)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AiMessage() {
    }

    public AiMessage(AiConversation conversation, AiMessageRole role, String content,
            AiGenerationStatus generationStatus) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
        this.role = role;
        this.content = content;
        this.generationStatus = generationStatus;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AiConversation getConversation() {
        return conversation;
    }

    public void setConversation(AiConversation conversation) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public AiMessageRole getRole() {
        return role;
    }

    public void setRole(AiMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public AiGenerationStatus getGenerationStatus() {
        return generationStatus;
    }

    public void setGenerationStatus(AiGenerationStatus generationStatus) {
        this.generationStatus = generationStatus;
    }

    public AiAnswerType getAnswerType() {
        return answerType;
    }

    public void setAnswerType(AiAnswerType answerType) {
        this.answerType = answerType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
