package com.kbase.ai.dto.response;

import java.util.List;

import com.kbase.ai.enums.AiAnswerType;

public record GuideQueryResponse(String answer, AiAnswerType answerType,
        List<GuideSourceResponse> sources) {
    public GuideQueryResponse { sources = sources == null ? List.of() : List.copyOf(sources); }
}
