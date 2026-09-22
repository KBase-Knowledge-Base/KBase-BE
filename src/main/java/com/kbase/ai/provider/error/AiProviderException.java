package com.kbase.ai.provider.error;

import java.util.Objects;

/**
 * Safe, provider-neutral failure raised by an AI provider adapter.
 *
 * <p>The exception deliberately stores only the normalized category. Vendor
 * messages, response bodies, prompts, credentials and vectors must not become
 * durable state or ordinary exception text.</p>
 */
public final class AiProviderException extends RuntimeException {

    private final AiProviderErrorCategory category;

    public AiProviderException(AiProviderErrorCategory category) {
        super(Objects.requireNonNull(category, "category must not be null").safeMessage());
        this.category = category;
    }

    public AiProviderErrorCategory category() {
        return category;
    }

    public boolean retryable() {
        return category.retryable();
    }
}
