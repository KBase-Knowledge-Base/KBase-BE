package com.kbase.ai.provider.error;

/**
 * Provider-neutral categories used at the KBase AI boundary.
 *
 * <p>The categories intentionally do not mirror a vendor exception hierarchy.
 * Later job/application layers can use {@link #retryable()} without importing
 * Spring AI or Google GenAI types.</p>
 */
public enum AiProviderErrorCategory {

    TIMEOUT(true, "AI provider request timed out."),
    RATE_LIMITED(true, "AI provider rate limit was reached."),
    CONFIGURATION(false, "AI provider configuration is invalid."),
    UNAVAILABLE(true, "AI provider is temporarily unavailable."),
    INVALID_RESPONSE(false, "AI provider returned an invalid response.");

    private final boolean retryable;
    private final String safeMessage;

    AiProviderErrorCategory(boolean retryable, String safeMessage) {
        this.retryable = retryable;
        this.safeMessage = safeMessage;
    }

    public boolean retryable() {
        return retryable;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
