package com.kbase.ai.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite identifier for an ordered assistant citation snapshot. */
@Embeddable
public class AiMessageSourceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "assistant_message_id", nullable = false)
    private UUID assistantMessageId;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    public AiMessageSourceId() {
    }

    public AiMessageSourceId(UUID assistantMessageId, int sourceOrder) {
        this.assistantMessageId = assistantMessageId;
        this.sourceOrder = sourceOrder;
    }

    public UUID getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(UUID assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public int getSourceOrder() {
        return sourceOrder;
    }

    public void setSourceOrder(int sourceOrder) {
        this.sourceOrder = sourceOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AiMessageSourceId that)) {
            return false;
        }
        return sourceOrder == that.sourceOrder
                && Objects.equals(assistantMessageId, that.assistantMessageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assistantMessageId, sourceOrder);
    }
}
