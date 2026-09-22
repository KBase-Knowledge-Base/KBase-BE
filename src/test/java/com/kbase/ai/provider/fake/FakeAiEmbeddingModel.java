package com.kbase.ai.provider.fake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.model.EmbeddingMode;
import com.kbase.ai.provider.port.AiEmbeddingModel;

/**
 * Deterministic 768-dimensional embedding fake for unit and integration tests.
 *
 * <p>Tests can register basis-vector fixtures to control semantic ordering.
 * Unregistered content uses a stable SHA-256-derived normalized vector, so no
 * random seed, provider credential, or network is involved.</p>
 */
public final class FakeAiEmbeddingModel implements AiEmbeddingModel {

    public static final int DIMENSIONS = 768;

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final CopyOnWriteArrayList<AiEmbeddingRequest> capturedRequests = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, List<Double>> fixtures = new ConcurrentHashMap<>();

    private volatile RuntimeException failure;
    private volatile FakeAiProviderException.Kind simulatedFailure;

    @Override
    public AiEmbeddingResult embed(AiEmbeddingRequest request) {
        AiEmbeddingRequest captured = Objects.requireNonNull(request, "request must not be null");
        capturedRequests.add(captured);
        invocationCount.incrementAndGet();

        if (simulatedFailure != null) {
            throw new FakeAiProviderException(simulatedFailure);
        }
        if (failure != null) {
            throw failure;
        }

        List<Double> vector = fixtures.get(fixtureKey(captured.mode(), captured.content()));
        if (vector == null) {
            vector = fixtures.get(fixtureKey(null, captured.content()));
        }
        if (vector == null) {
            vector = stableVector(canonicalInput(captured));
        }
        return new AiEmbeddingResult(vector, "fake-embedding");
    }

    public FakeAiEmbeddingModel registerFixture(String content, List<Double> vector) {
        return registerFixture(null, content, vector);
    }

    public FakeAiEmbeddingModel registerFixture(EmbeddingMode mode, String content, List<Double> vector) {
        Objects.requireNonNull(content, "content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        fixtures.put(fixtureKey(mode, content), normalizedVector(vector));
        return this;
    }

    public FakeAiEmbeddingModel registerFixture(String content, double[] vector) {
        return registerFixture(content, asList(vector));
    }

    public FakeAiEmbeddingModel registerFixture(EmbeddingMode mode, String content, double[] vector) {
        return registerFixture(mode, content, asList(vector));
    }

    /** Register a one-hot fixture, useful for deterministic nearest-neighbor tests. */
    public FakeAiEmbeddingModel registerLabel(String label, int axis) {
        return registerFixture(label, unitVector(axis));
    }

    public FakeAiEmbeddingModel unavailable() {
        simulatedFailure = FakeAiProviderException.Kind.UNAVAILABLE;
        failure = null;
        return this;
    }

    public FakeAiEmbeddingModel simulateUnavailable() {
        return unavailable();
    }

    public FakeAiEmbeddingModel timeout() {
        simulatedFailure = FakeAiProviderException.Kind.TIMEOUT;
        failure = null;
        return this;
    }

    public FakeAiEmbeddingModel simulateTimeout() {
        return timeout();
    }

    public FakeAiEmbeddingModel failWith(RuntimeException failure) {
        this.failure = Objects.requireNonNull(failure, "failure must not be null");
        simulatedFailure = null;
        return this;
    }

    public FakeAiEmbeddingModel clearFailure() {
        failure = null;
        simulatedFailure = null;
        return this;
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    public int getInvocationCount() {
        return invocationCount();
    }

    public List<AiEmbeddingRequest> capturedRequests() {
        return List.copyOf(capturedRequests);
    }

    public List<AiEmbeddingRequest> getCapturedRequests() {
        return capturedRequests();
    }

    public List<EmbeddingMode> capturedModes() {
        return capturedRequests.stream().map(AiEmbeddingRequest::mode).toList();
    }

    public void reset() {
        invocationCount.set(0);
        capturedRequests.clear();
        clearFailure();
    }

    public static List<Double> unitVector(int axis) {
        if (axis < 0 || axis >= DIMENSIONS) {
            throw new IllegalArgumentException("axis must be between 0 and " + (DIMENSIONS - 1));
        }
        List<Double> vector = new ArrayList<>(DIMENSIONS);
        for (int index = 0; index < DIMENSIONS; index++) {
            vector.add(index == axis ? 1.0 : 0.0);
        }
        return vector;
    }

    private static List<Double> stableVector(String value) {
        double[] values = new double[DIMENSIONS];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] source = value.getBytes(StandardCharsets.UTF_8);
            for (int index = 0; index < DIMENSIONS; index++) {
                digest.update(source);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(index).array());
                long bits = ByteBuffer.wrap(digest.digest()).getLong();
                values[index] = bits / (double) Long.MAX_VALUE;
            }
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
        return normalizedVector(values);
    }

    private static List<Double> normalizedVector(List<Double> vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.size() != DIMENSIONS) {
            throw new IllegalArgumentException("fake embedding vector must have exactly " + DIMENSIONS + " dimensions");
        }
        double[] values = new double[DIMENSIONS];
        for (int index = 0; index < DIMENSIONS; index++) {
            Double value = Objects.requireNonNull(vector.get(index), "vector values must not be null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("vector values must be finite");
            }
            values[index] = value;
        }
        return normalizedVector(values);
    }

    private static List<Double> normalizedVector(double[] values) {
        double norm = 0.0;
        for (double value : values) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new IllegalArgumentException("embedding vector must have a non-zero finite norm");
        }
        List<Double> normalized = new ArrayList<>(values.length);
        for (double value : values) {
            normalized.add(value / norm);
        }
        return List.copyOf(normalized);
    }

    private static List<Double> asList(double[] vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        List<Double> values = new ArrayList<>(vector.length);
        for (double value : vector) {
            values.add(value);
        }
        return values;
    }

    private static String fixtureKey(EmbeddingMode mode, String content) {
        return (mode == null ? "*" : mode.name()) + '\u0000' + content;
    }

    private static String canonicalInput(AiEmbeddingRequest request) {
        return request.mode().name() + "\n" + (request.title() == null ? "" : request.title())
                + "\n" + request.content();
    }
}
