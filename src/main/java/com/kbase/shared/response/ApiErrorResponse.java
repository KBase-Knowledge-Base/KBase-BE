package com.kbase.shared.response;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Stable, implementation-safe REST error response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        Map<String, String> errors) {

    public ApiErrorResponse {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        path = Objects.requireNonNull(path, "path");
        requestId = Objects.requireNonNull(requestId, "requestId");
        errors = errors == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
