package com.kbase.ai.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AiObservabilityTest {

    @Test
    void recordsOperationalMetricsWithExpectedNamesAndCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiObservability observability = new AiObservability(registry);

        observability.recordInteractiveRequest("PROJECT_ASSISTANT_CREATE", "SUCCESS", 1_000_000L);
        observability.recordRateOutcome(AiObservability.RateOutcome.ALLOWED);
        observability.recordRateOutcome(AiObservability.RateOutcome.REJECTED);
        observability.recordProviderCall("CHAT", "UNAVAILABLE", 2_000_000L);
        observability.recordRetrievalCandidates(4);
        observability.recordNoEvidence("GUIDE_QUERY");
        observability.recordJobExecution("DOCUMENT_INDEX", "RETRY", 3_000_000L);
        observability.recordJobDepth(Map.of("PENDING", 3L, "FAILED", 1L), 2L);

        assertThat(registry.get(AiObservability.REQUESTS).tag("operation", "PROJECT_ASSISTANT_CREATE")
                .tag("outcome", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.REQUEST_DURATION).tag("operation", "PROJECT_ASSISTANT_CREATE")
                .tag("outcome", "SUCCESS").timer().count()).isEqualTo(1L);
        assertThat(registry.get(AiObservability.RATE_OUTCOMES).tag("outcome", "ALLOWED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.RATE_OUTCOMES).tag("outcome", "REJECTED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.PROVIDER_CALLS).tag("provider", "CHAT")
                .tag("outcome", "UNAVAILABLE").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.PROVIDER_DURATION).tag("provider", "CHAT")
                .tag("outcome", "UNAVAILABLE").timer().count()).isEqualTo(1L);
        assertThat(registry.get(AiObservability.RETRIEVAL_CANDIDATES).summary().count()).isEqualTo(1L);
        assertThat(registry.get(AiObservability.NO_EVIDENCE).tag("operation", "GUIDE_QUERY")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.JOB_EXECUTIONS).tag("job_type", "DOCUMENT_INDEX")
                .tag("outcome", "RETRY").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.JOB_DURATION).tag("job_type", "DOCUMENT_INDEX")
                .tag("outcome", "RETRY").timer().count()).isEqualTo(1L);
        assertThat(registry.get(AiObservability.JOB_DEPTH).tag("state", "PENDING").gauge().value())
                .isEqualTo(3.0);
        assertThat(registry.get(AiObservability.JOB_SIGNALS).tag("kind", "failed").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.get(AiObservability.JOB_SIGNALS).tag("kind", "stale").gauge().value())
                .isEqualTo(2.0);
    }

    @Test
    void unknownInputsUseBoundedFallbackTagsAndNeverBecomeMetricLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiObservability observability = new AiObservability(registry);
        String promptSentinel = "user-123-prompt-sentinel";
        String contentSentinel = "chunk-content-sentinel";

        observability.recordInteractiveRequest(promptSentinel, contentSentinel, 1L);
        observability.recordProviderCall(promptSentinel, contentSentinel, 1L);
        observability.recordNoEvidence(promptSentinel);
        observability.recordJobExecution(promptSentinel, contentSentinel, 1L);
        observability.recordJobDepth(Map.of(promptSentinel, 99L), 7L);

        assertThat(registry.get(AiObservability.REQUESTS).tag("operation", "UNKNOWN")
                .tag("outcome", "ERROR").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.PROVIDER_CALLS).tag("provider", "UNKNOWN")
                .tag("outcome", "INTERNAL").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.NO_EVIDENCE).tag("operation", "UNKNOWN")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(AiObservability.JOB_EXECUTIONS).tag("job_type", "UNKNOWN")
                .tag("outcome", "FAILURE").counter().count()).isEqualTo(1.0);
        assertThat(registry.find(AiObservability.JOB_DEPTH).meters()).isEmpty();
        assertThat(registry.getMeters().stream().map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getValue())
                .noneMatch(value -> value.contains(promptSentinel) || value.contains(contentSentinel)))
                .isTrue();
    }

    @Test
    void providerOutcomeIsBoundedAndTelemetryFailureIsNonFatal() {
        assertThat(AiObservability.requestOutcome(
                new AiProviderException(AiProviderErrorCategory.TIMEOUT)))
                .isEqualTo("PROVIDER_UNAVAILABLE");

        MeterRegistry failingRegistry = mock(MeterRegistry.class);
        when(failingRegistry.counter(anyString(), any(String[].class)))
                .thenThrow(new IllegalStateException("telemetry failure"));

        AiObservability observability = new AiObservability(failingRegistry);
        observability.recordRateOutcome(AiObservability.RateOutcome.ALLOWED);
        observability.recordNoEvidence("GUIDE_QUERY");
    }
}
