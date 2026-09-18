package com.kbase.security.handler;

import java.io.IOException;

import com.kbase.shared.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns the standard 403 JSON error when an authenticated principal lacks
 * the required system role. Domain-level denials are raised from services.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final RestSecurityErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(ErrorCode.ACCESS_DENIED, request, response);
    }
}
