package com.kbase.ai.dto.response;

import java.util.UUID;

import com.kbase.ai.entity.AiMessageSource;

public record AiSourceResponse(int order, UUID documentId, String documentName,
        Integer pageNumber, Integer slideNumber, String sectionTitle,
        Availability availability) {

    public enum Availability { AVAILABLE, UNAVAILABLE }

    public static AiSourceResponse from(AiMessageSource source) {
        boolean live = source.getDocumentId() != null && source.getChunkId() != null;
        return new AiSourceResponse(source.getSourceOrder() + 1,
                live ? source.getDocumentId() : null,
                source.getDocumentNameSnapshot(), source.getPageNumberSnapshot(),
                source.getSlideNumberSnapshot(), source.getSectionTitleSnapshot(),
                live ? Availability.AVAILABLE : Availability.UNAVAILABLE);
    }
}
