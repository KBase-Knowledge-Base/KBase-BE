package com.kbase.ai.provider.springai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

/** Maps Spring AI/Google GenAI failures to safe KBase-owned categories. */
final class SpringAiGeminiErrorTranslator {

    private SpringAiGeminiErrorTranslator() {
    }

    static AiProviderException translate(Throwable failure) {
        if (failure instanceof AiProviderException providerException) {
            return providerException;
        }

        AiProviderErrorCategory category = classify(failure);
        return new AiProviderException(category);
    }

    private static AiProviderErrorCategory classify(Throwable failure) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = failure;
        AiProviderErrorCategory wrapperFallback = null;
        while (current != null && visited.add(current)) {
            if (current instanceof GenAiIOException || current instanceof TransientAiException) {
                // These wrappers can preserve a structured provider/transport cause.
                // Inspect it before falling back to the generic unavailable category.
                wrapperFallback = AiProviderErrorCategory.UNAVAILABLE;
                current = current.getCause();
                continue;
            }
            if (current instanceof NonTransientAiException) {
                // A malformed/non-transient Spring AI wrapper is configuration-like,
                // unless its cause provides a more precise structured category.
                wrapperFallback = AiProviderErrorCategory.CONFIGURATION;
                current = current.getCause();
                continue;
            }
            AiProviderErrorCategory category = classifySingle(current);
            if (category != null) {
                return category;
            }
            current = current.getCause();
        }
        return wrapperFallback == null ? AiProviderErrorCategory.UNAVAILABLE : wrapperFallback;
    }

    private static AiProviderErrorCategory classifySingle(Throwable failure) {
        if (failure instanceof ApiException apiException) {
            return classifyApiException(apiException);
        }
        if (failure instanceof TimeoutException
                || failure instanceof SocketTimeoutException
                || failure instanceof HttpTimeoutException) {
            return AiProviderErrorCategory.TIMEOUT;
        }
        if (failure instanceof ConnectException
                || failure instanceof UnknownHostException
                || failure instanceof IOException) {
            return AiProviderErrorCategory.UNAVAILABLE;
        }
        if (failure instanceof IllegalArgumentException) {
            return AiProviderErrorCategory.INVALID_RESPONSE;
        }
        if (failure instanceof IllegalStateException) {
            return AiProviderErrorCategory.CONFIGURATION;
        }
        return null;
    }

    private static AiProviderErrorCategory classifyApiException(ApiException failure) {
        int statusCode = failure.code();
        if (statusCode == 408 || statusCode == 504) {
            return AiProviderErrorCategory.TIMEOUT;
        }
        if (statusCode == 429) {
            return AiProviderErrorCategory.RATE_LIMITED;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return AiProviderErrorCategory.CONFIGURATION;
        }
        if (statusCode >= 500) {
            return AiProviderErrorCategory.UNAVAILABLE;
        }

        String status = failure.status();
        if (status == null) {
            return null;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "DEADLINE_EXCEEDED" -> AiProviderErrorCategory.TIMEOUT;
            case "RESOURCE_EXHAUSTED" -> AiProviderErrorCategory.RATE_LIMITED;
            case "UNAUTHENTICATED", "PERMISSION_DENIED", "INVALID_ARGUMENT",
                    "FAILED_PRECONDITION" -> AiProviderErrorCategory.CONFIGURATION;
            case "UNAVAILABLE", "ABORTED", "INTERNAL" -> AiProviderErrorCategory.UNAVAILABLE;
            default -> null;
        };
    }
}
