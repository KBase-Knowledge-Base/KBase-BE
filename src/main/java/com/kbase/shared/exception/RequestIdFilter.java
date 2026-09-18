package com.kbase.shared.exception;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a request correlation ID to the servlet request, response and MDC. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static final String MDC_KEY = "requestId";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = ensureRequestId(request, response);
        try {
            MDC.put(MDC_KEY, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Resolves the current request ID or creates one when the filter is used
     * by a test or an exception path without a prior filter invocation.
     */
    public static String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = findRequestId(request);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(MDC_KEY, requestId);
        if (response != null) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
        }
        return requestId;
    }

    /** Returns only a valid UUID request ID from trusted request context. */
    public static String findRequestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String value && isValidRequestId(value)) {
            return value;
        }

        String mdcValue = MDC.get(MDC_KEY);
        if (isValidRequestId(mdcValue)) {
            return mdcValue;
        }

        String headerValue = request.getHeader(REQUEST_ID_HEADER);
        return isValidRequestId(headerValue) ? headerValue : null;
    }

    public static boolean isValidRequestId(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }
}
