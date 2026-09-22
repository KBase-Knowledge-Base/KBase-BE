package com.kbase.ai.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Historical citation snapshot; live source IDs may be nulled by source deletion. */
@Entity
@Table(name = "ai_message_sources")
public class AiMessageSource {

    @EmbeddedId
    private AiMessageSourceId id;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id_snapshot", nullable = false)
    private UUID documentIdSnapshot;

    @Column(name = "document_name_snapshot", nullable = false, length = 255)
    private String documentNameSnapshot;

    @Column(name = "page_number_snapshot")
    private Integer pageNumberSnapshot;

    @Column(name = "slide_number_snapshot")
    private Integer slideNumberSnapshot;

    @Column(name = "section_title_snapshot", length = 255)
    private String sectionTitleSnapshot;

    @Column(name = "retrieval_score")
    private Double retrievalScore;

    public AiMessageSource() {
    }

    public AiMessageSource(UUID assistantMessageId, int sourceOrder, UUID documentId,
            UUID chunkId, UUID documentIdSnapshot, String documentNameSnapshot) {
        this.id = new AiMessageSourceId(assistantMessageId, sourceOrder);
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.documentIdSnapshot = documentIdSnapshot;
        this.documentNameSnapshot = documentNameSnapshot;
    }

    public AiMessageSourceId getId() {
        return id;
    }

    public void setId(AiMessageSourceId id) {
        this.id = id;
    }

    public UUID getAssistantMessageId() {
        return id == null ? null : id.getAssistantMessageId();
    }

    public int getSourceOrder() {
        return id == null ? 0 : id.getSourceOrder();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getDocumentIdSnapshot() {
        return documentIdSnapshot;
    }

    public void setDocumentIdSnapshot(UUID documentIdSnapshot) {
        this.documentIdSnapshot = documentIdSnapshot;
    }

    public String getDocumentNameSnapshot() {
        return documentNameSnapshot;
    }

    public void setDocumentNameSnapshot(String documentNameSnapshot) {
        this.documentNameSnapshot = documentNameSnapshot;
    }

    public Integer getPageNumberSnapshot() {
        return pageNumberSnapshot;
    }

    public void setPageNumberSnapshot(Integer pageNumberSnapshot) {
        this.pageNumberSnapshot = pageNumberSnapshot;
    }

    public Integer getSlideNumberSnapshot() {
        return slideNumberSnapshot;
    }

    public void setSlideNumberSnapshot(Integer slideNumberSnapshot) {
        this.slideNumberSnapshot = slideNumberSnapshot;
    }

    public String getSectionTitleSnapshot() {
        return sectionTitleSnapshot;
    }

    public void setSectionTitleSnapshot(String sectionTitleSnapshot) {
        this.sectionTitleSnapshot = sectionTitleSnapshot;
    }

    public Double getRetrievalScore() {
        return retrievalScore;
    }

    public void setRetrievalScore(Double retrievalScore) {
        this.retrievalScore = retrievalScore;
    }
}
