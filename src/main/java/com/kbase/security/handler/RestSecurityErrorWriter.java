package com.kbase.security.handler;

import java.io.IOException;
import java.time.Instant;

import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.RequestIdFilter;
import com.kbase.shared.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes the standard {@link ApiErrorResponse} JSON for security-layer
 * rejections. The body never contains provider, token or exception details.
 */
@Component
public class RestSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public RestSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(ErrorCode errorCode, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String requestId = RequestIdFilter.ensureRequestId(request, response);
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                errorCode.getStatus(),
                errorCode.getCode(),
                errorCode.getDefaultMessage(),
                path,
                requestId,
                null);
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
