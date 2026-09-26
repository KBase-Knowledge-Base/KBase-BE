package com.kbase.ai.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * KBase-owned configuration for the AI runtime boundary.
 *
 * <p>The API key is deliberately optional here. Provider adapters are enabled
 * in a later milestone and must enforce their own credential requirement only
 * when the AI path is explicitly enabled.</p>
 */
@Validated
@ConfigurationProperties(prefix = "kbase.ai")
public class AiProperties {

    private boolean enabled;

    @Valid
    private GeminiProperties gemini = new GeminiProperties();

    @Valid
    private ProviderProperties provider = new ProviderProperties();

    @Positive
    private int maxMessageChars = 8_000;

    @Positive
    private int chunkTargetTokens = 700;

    @Min(0)
    @Max(99)
    private int chunkOverlapPercent = 12;

    @Positive
    private int retrievalCandidateLimit = 10;

    @Positive
    private int retrievalFinalContextLimit = 6;

    private Double retrievalSimilarityThreshold = 0.70;

    @Valid
    private WorkerProperties worker = new WorkerProperties();

    @NotNull
    private Duration retention = Duration.ofDays(7);

    @NotBlank
    private String usageRateNamespace = "kbase:ai:rate";

    @Positive
    private int usageMaxRequests = 20;

    @NotNull
    private Duration usageWindow = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public GeminiProperties getGemini() {
        return gemini;
    }

    public void setGemini(GeminiProperties gemini) {
        this.gemini = gemini;
    }

    public ProviderProperties getProvider() {
        return provider;
    }

    public void setProvider(ProviderProperties provider) {
        this.provider = provider;
    }

    public int getMaxMessageChars() {
        return maxMessageChars;
    }

    public void setMaxMessageChars(int maxMessageChars) {
        this.maxMessageChars = maxMessageChars;
    }

    public int getChunkTargetTokens() {
        return chunkTargetTokens;
    }

    public void setChunkTargetTokens(int chunkTargetTokens) {
        this.chunkTargetTokens = chunkTargetTokens;
    }

    public int getChunkOverlapPercent() {
        return chunkOverlapPercent;
    }

    public void setChunkOverlapPercent(int chunkOverlapPercent) {
        this.chunkOverlapPercent = chunkOverlapPercent;
    }

    public int getRetrievalCandidateLimit() {
        return retrievalCandidateLimit;
    }

    public void setRetrievalCandidateLimit(int retrievalCandidateLimit) {
        this.retrievalCandidateLimit = retrievalCandidateLimit;
    }

    public int getRetrievalFinalContextLimit() {
        return retrievalFinalContextLimit;
    }

    public void setRetrievalFinalContextLimit(int retrievalFinalContextLimit) {
        this.retrievalFinalContextLimit = retrievalFinalContextLimit;
    }

    public Double getRetrievalSimilarityThreshold() {
        return retrievalSimilarityThreshold;
    }

    public void setRetrievalSimilarityThreshold(Double retrievalSimilarityThreshold) {
        this.retrievalSimilarityThreshold = retrievalSimilarityThreshold;
    }

    public WorkerProperties getWorker() {
        return worker;
    }

    public void setWorker(WorkerProperties worker) {
        this.worker = worker;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public String getUsageRateNamespace() {
        return usageRateNamespace;
    }

    public void setUsageRateNamespace(String usageRateNamespace) {
        this.usageRateNamespace = usageRateNamespace;
    }

    public int getUsageMaxRequests() {
        return usageMaxRequests;
    }

    public void setUsageMaxRequests(int usageMaxRequests) {
        this.usageMaxRequests = usageMaxRequests;
    }

    public Duration getUsageWindow() {
        return usageWindow;
    }

    public void setUsageWindow(Duration usageWindow) {
        this.usageWindow = usageWindow;
    }

    /**
     * M1 keeps the v1 embedding contract fixed at 768 dimensions. M10 selects
     * a conservative production default, while an explicit null remains a
     * supported fail-closed override for Guide retrieval.
     */
    @AssertTrue(message = "retrieval-similarity-threshold must be between 0 and 1 when configured")
    public boolean isRetrievalSimilarityThresholdValid() {
        return retrievalSimilarityThreshold == null
                || (Double.isFinite(retrievalSimilarityThreshold)
                        && retrievalSimilarityThreshold >= 0.0
                        && retrievalSimilarityThreshold <= 1.0);
    }

    @AssertTrue(message = "AI retention must be positive")
    public boolean isRetentionPositive() {
        return retention != null && !retention.isZero() && !retention.isNegative();
    }

    @AssertTrue(message = "AI usage window must be positive and contain at least one millisecond")
    public boolean isUsageWindowPositive() {
        return usageWindow != null && !usageWindow.isZero() && !usageWindow.isNegative()
                && positiveMillis(usageWindow);
    }

    @AssertTrue(message = "AI usage rate namespace must not be blank")
    public boolean isUsageRateNamespaceValid() {
        return usageRateNamespace != null && !usageRateNamespace.isBlank();
    }

    private static boolean positiveMillis(Duration value) {
        try {
            return value.toMillis() > 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    public static class GeminiProperties {

        private String apiKey;

        @NotBlank
        private String chatModel = "gemini-2.5-flash";

        @NotBlank
        private String embeddingModel = "gemini-embedding-2";

        @Min(768)
        @Max(768)
        private int embeddingDimensions = 768;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public int getEmbeddingDimensions() {
            return embeddingDimensions;
        }

        public void setEmbeddingDimensions(int embeddingDimensions) {
            this.embeddingDimensions = embeddingDimensions;
        }
    }

    public static class ProviderProperties {

        /**
         * Gemini is the only production provider mode. DETERMINISTIC exists
         * solely for the explicitly acknowledged runtime-test profile so the
         * Docker verification suite never needs a public provider or a key.
         */
        @NotNull
        private ProviderMode mode = ProviderMode.GEMINI;

        @Valid
        private DeterministicProperties deterministic = new DeterministicProperties();

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(10);

        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(60);

        public ProviderMode getMode() {
            return mode;
        }

        public void setMode(ProviderMode mode) {
            this.mode = mode;
        }

        public DeterministicProperties getDeterministic() {
            return deterministic;
        }

        public void setDeterministic(DeterministicProperties deterministic) {
            this.deterministic = deterministic;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        @AssertTrue(message = "AI provider timeouts must be positive")
        public boolean isPositive() {
            return isPositive(connectTimeout) && isPositive(requestTimeout);
        }

        private static boolean isPositive(Duration value) {
            return value != null && !value.isZero() && !value.isNegative();
        }
    }

    public enum ProviderMode {
        GEMINI,
        DETERMINISTIC
    }

    /** Test-only controls, guarded again by the runtime-test Spring profile. */
    public static class DeterministicProperties {

        private boolean runtimeTestAcknowledged;

        @NotNull
        private DeterministicFailureMode failureMode = DeterministicFailureMode.NONE;

        public boolean isRuntimeTestAcknowledged() {
            return runtimeTestAcknowledged;
        }

        public void setRuntimeTestAcknowledged(boolean runtimeTestAcknowledged) {
            this.runtimeTestAcknowledged = runtimeTestAcknowledged;
        }

        public DeterministicFailureMode getFailureMode() {
            return failureMode;
        }

        public void setFailureMode(DeterministicFailureMode failureMode) {
            this.failureMode = failureMode;
        }
    }

    public enum DeterministicFailureMode {
        NONE,
        UNAVAILABLE,
        TIMEOUT
    }

    public static class WorkerProperties {

        @NotNull
        private Duration pollInterval = Duration.ofSeconds(5);

        @Positive
        private int batchSize = 10;

        @NotNull
        private Duration leaseTimeout = Duration.ofMinutes(2);

        @NotNull
        private Duration retryBackoff = Duration.ofSeconds(30);

        @Positive
        private int maxAttempts = 3;

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getLeaseTimeout() {
            return leaseTimeout;
        }

        public void setLeaseTimeout(Duration leaseTimeout) {
            this.leaseTimeout = leaseTimeout;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        @AssertTrue(message = "AI worker durations must be positive")
        public boolean isPositive() {
            return isPositive(pollInterval) && isPositive(leaseTimeout)
                    && isPositive(retryBackoff);
        }

        private static boolean isPositive(Duration value) {
            return value != null && !value.isZero() && !value.isNegative();
        }
    }
}
