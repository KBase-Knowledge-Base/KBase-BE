package com.kbase.ai.observability;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.kbase.shared.exception.AiRateLimitExceededException;
import com.kbase.shared.exception.AiUsageGuardUnavailableException;
import com.kbase.shared.exception.KBaseException;
import com.kbase.ai.provider.error.AiProviderException;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * KBase-owned, best-effort AI telemetry boundary.
 *
 * <p>There is intentionally no public metrics controller here. Tags are
 * selected from finite vocabularies, and every recording operation is
 * non-fatal to the business request.</p>
 */
@Component
public final class AiObservability {

    public static final String REQUESTS = "kbase.ai.requests";
    public static final String REQUEST_DURATION = "kbase.ai.request.duration";
    public static final String RATE_OUTCOMES = "kbase.ai.rate.outcomes";
    public static final String PROVIDER_CALLS = "kbase.ai.provider.calls";
    public static final String PROVIDER_DURATION = "kbase.ai.provider.duration";
    public static final String RETRIEVAL_CANDIDATES = "kbase.ai.retrieval.candidates";
    public static final String NO_EVIDENCE = "kbase.ai.no_evidence";
    public static final String JOB_EXECUTIONS = "kbase.ai.jobs.executions";
    public static final String JOB_DURATION = "kbase.ai.jobs.duration";
    public static final String JOB_DEPTH = "kbase.ai.jobs.depth";
    public static final String JOB_SIGNALS = "kbase.ai.jobs.signals";

    private static final Set<String> OPERATIONS = Set.of(
            "PROJECT_ASSISTANT_CREATE", "PROJECT_ASSISTANT_SEND", "GUIDE_QUERY");
    private static final Set<String> REQUEST_OUTCOMES = Set.of(
            "SUCCESS", "RATE_LIMITED", "GUARD_UNAVAILABLE", "PROVIDER_UNAVAILABLE",
            "AUTHZ_REJECTED", "VALIDATION_REJECTED", "ERROR");
    private static final Set<String> PROVIDER_TYPES = Set.of("EMBEDDING", "CHAT");
    private static final Set<String> JOB_STATES = Set.of(
            "PENDING", "PROCESSING", "RETRY", "DONE", "FAILED", "CANCELLED");
    private static final Set<String> JOB_OUTCOMES = Set.of("SUCCESS", "RETRY", "FAILURE");

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> depthGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> signalGauges = new ConcurrentHashMap<>();

    @Autowired
    public AiObservability(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider.getIfAvailable(SimpleMeterRegistry::new));
    }

    /** Focused-test and adapter compatibility constructor. */
    public AiObservability() {
        this(new SimpleMeterRegistry());
    }

    public AiObservability(MeterRegistry registry) {
        this.registry = registry == null ? new SimpleMeterRegistry() : registry;
    }

    public void recordInteractiveRequest(String operation, String outcome, long durationNanos) {
        safe(() -> {
            String boundedOperation = bounded(operation, OPERATIONS, "UNKNOWN");
            String boundedOutcome = bounded(outcome, REQUEST_OUTCOMES, "ERROR");
            registry.counter(REQUESTS, "operation", boundedOperation, "outcome", boundedOutcome)
                    .increment();
            Timer.builder(REQUEST_DURATION)
                    .tags("operation", boundedOperation, "outcome", boundedOutcome)
                    .register(registry)
                    .record(Math.max(0L, durationNanos), java.util.concurrent.TimeUnit.NANOSECONDS);
        });
    }

    public void recordRateOutcome(RateOutcome outcome) {
        safe(() -> registry.counter(RATE_OUTCOMES, "outcome", outcome == null
                ? RateOutcome.UNAVAILABLE.name() : outcome.name()).increment());
    }

    public void recordProviderCall(String providerType, String outcome, long durationNanos) {
        safe(() -> {
            String boundedType = bounded(providerType, PROVIDER_TYPES, "UNKNOWN");
            String boundedOutcome = providerOutcome(outcome);
            registry.counter(PROVIDER_CALLS, "provider", boundedType, "outcome", boundedOutcome)
                    .increment();
            Timer.builder(PROVIDER_DURATION)
                    .tags("provider", boundedType, "outcome", boundedOutcome)
                    .register(registry)
                    .record(Math.max(0L, durationNanos), java.util.concurrent.TimeUnit.NANOSECONDS);
        });
    }

    public void recordRetrievalCandidates(int count) {
        safe(() -> DistributionSummary.builder(RETRIEVAL_CANDIDATES)
                .register(registry)
                .record(Math.max(0, count)));
    }

    public void recordNoEvidence(String operation) {
        safe(() -> registry.counter(NO_EVIDENCE, "operation",
                bounded(operation, OPERATIONS, "UNKNOWN")).increment());
    }

    public void recordJobExecution(String jobType, String outcome, long durationNanos) {
        safe(() -> {
            String boundedType = boundedJobType(jobType);
            String boundedOutcome = bounded(outcome, JOB_OUTCOMES, "FAILURE");
            registry.counter(JOB_EXECUTIONS, "job_type", boundedType, "outcome", boundedOutcome)
                    .increment();
            Timer.builder(JOB_DURATION)
                    .tags("job_type", boundedType, "outcome", boundedOutcome)
                    .register(registry)
                    .record(Math.max(0L, durationNanos), java.util.concurrent.TimeUnit.NANOSECONDS);
        });
    }

    public void recordJobDepth(Map<String, Long> depths, long staleJobs) {
        safe(() -> {
            if (depths != null) {
                depths.forEach((state, count) -> {
                    String boundedState = bounded(state, JOB_STATES, "UNKNOWN");
                    if (!"UNKNOWN".equals(boundedState)) {
                        gauge(depthGauges, JOB_DEPTH, boundedState).set(Math.max(0L, value(count)));
                    }
                });
            }
            gauge(signalGauges, JOB_SIGNALS, "failed").set(Math.max(0L, value(
                    depths == null ? null : depths.get("FAILED"))));
            gauge(signalGauges, JOB_SIGNALS, "stale").set(Math.max(0L, staleJobs));
        });
    }

    public static String requestOutcome(Throwable failure) {
        if (failure instanceof AiRateLimitExceededException) {
            return "RATE_LIMITED";
        }
        if (failure instanceof AiUsageGuardUnavailableException) {
            return "GUARD_UNAVAILABLE";
        }
        if (failure instanceof AiProviderException) {
            return "PROVIDER_UNAVAILABLE";
        }
        if (failure instanceof KBaseException known) {
            return switch (known.getErrorCode()) {
                case PROJECT_ACCESS_FORBIDDEN, AI_CONVERSATION_NOT_FOUND -> "AUTHZ_REJECTED";
                case VALIDATION_ERROR, INVALID_REQUEST_BODY, INVALID_PARAMETER -> "VALIDATION_REJECTED";
                case AI_PROVIDER_UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
                default -> "ERROR";
            };
        }
        return "ERROR";
    }

    public enum RateOutcome {
        ALLOWED,
        REJECTED,
        UNAVAILABLE
    }

    private static String providerOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return "INTERNAL";
        }
        return switch (outcome) {
            case "TIMEOUT", "RATE_LIMITED", "CONFIGURATION", "UNAVAILABLE", "INVALID_RESPONSE",
                    "SUCCESS", "INTERNAL" -> outcome;
            default -> "INTERNAL";
        };
    }

    private static String boundedJobType(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return switch (value) {
            case "DOCUMENT_INDEX", "GUIDE_REINDEX", "CONVERSATION_PURGE" -> value;
            default -> "UNKNOWN";
        };
    }

    private static String bounded(String value, Set<String> allowed, String fallback) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private AtomicLong gauge(Map<String, AtomicLong> gauges, String name, String tag) {
        String key = name + "\u0000" + tag;
        AtomicLong value = gauges.computeIfAbsent(key, ignored -> {
            AtomicLong created = new AtomicLong();
            io.micrometer.core.instrument.Gauge.builder(name, created, AtomicLong::get)
                    .tag(name.equals(JOB_DEPTH) ? "state" : "kind", tag)
                    .register(registry);
            return created;
        });
        return value;
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Telemetry must never make an AI or Core request fail.
        }
    }
}
