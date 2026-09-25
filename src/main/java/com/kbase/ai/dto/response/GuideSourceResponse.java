package com.kbase.ai.dto.response;

/** Safe citation metadata from the reviewed Guide catalog only. */
public record GuideSourceResponse(String sourceKey, String title, String section) {
}
