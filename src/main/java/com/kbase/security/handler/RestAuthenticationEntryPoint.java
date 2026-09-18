package com.kbase.security.handler;

import java.io.IOException;

import com.kbase.security.jwt.JwtAuthenticationFilter;
import com.kbase.shared.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns the standard 401 JSON error for unauthenticated access. When a
 * Bearer token was present but failed validation, the more specific token
 * error code is used instead of the generic authentication-required code.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RestSecurityErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(RestSecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        Object failure = request.getAttribute(JwtAuthenticationFilter.TOKEN_VALIDATION_FAILURE_ATTRIBUTE);
        errorWriter.write(
                failure instanceof ErrorCode errorCode ? errorCode : ErrorCode.AUTHENTICATION_REQUIRED,
                request,
                response);
    }
}
