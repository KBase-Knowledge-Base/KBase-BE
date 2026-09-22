package com.kbase.ai.provider.fake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.model.AiEvidenceBlock;
import com.kbase.ai.provider.port.AiChatModel;

/**
 * Deterministic test double for {@link AiChatModel}.
 *
 * <p>The fake captures immutable KBase requests and never uses Spring AI,
 * Google SDK classes, credentials, a clock, or a network connection.</p>
 */
public final class FakeAiChatModel implements AiChatModel {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final CopyOnWriteArrayList<AiChatRequest> capturedRequests = new CopyOnWriteArrayList<>();

    private volatile AiChatResult responseFixture;
    private volatile RuntimeException failure;
    private volatile FakeAiProviderException.Kind simulatedFailure;

    public FakeAiChatModel() {
    }

    public FakeAiChatModel(String response) {
        this(new AiChatResult(Objects.requireNonNull(response, "response must not be null"), "fake-chat"));
    }

    public FakeAiChatModel(AiChatResult responseFixture) {
        this.responseFixture = Objects.requireNonNull(responseFixture, "responseFixture must not be null");
    }

    @Override
    public AiChatResult generate(AiChatRequest request) {
        AiChatRequest captured = Objects.requireNonNull(request, "request must not be null");
        capturedRequests.add(captured);
        invocationCount.incrementAndGet();

        if (simulatedFailure != null) {
            throw new FakeAiProviderException(simulatedFailure);
        }
        if (failure != null) {
            throw failure;
        }
        if (responseFixture != null) {
            return responseFixture;
        }
        return deterministicResponse(captured);
    }

    public FakeAiChatModel withResponse(AiChatResult responseFixture) {
        this.responseFixture = Objects.requireNonNull(responseFixture, "responseFixture must not be null");
        return this;
    }

    public FakeAiChatModel setResponse(AiChatResult responseFixture) {
        return withResponse(responseFixture);
    }

    public FakeAiChatModel unavailable() {
        simulatedFailure = FakeAiProviderException.Kind.UNAVAILABLE;
        failure = null;
        return this;
    }

    public FakeAiChatModel simulateUnavailable() {
        return unavailable();
    }

    public FakeAiChatModel timeout() {
        simulatedFailure = FakeAiProviderException.Kind.TIMEOUT;
        failure = null;
        return this;
    }

    public FakeAiChatModel simulateTimeout() {
        return timeout();
    }

    public FakeAiChatModel failWith(RuntimeException failure) {
        this.failure = Objects.requireNonNull(failure, "failure must not be null");
        simulatedFailure = null;
        return this;
    }

    public FakeAiChatModel clearFailure() {
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

    public List<AiChatRequest> capturedRequests() {
        return List.copyOf(capturedRequests);
    }

    public List<AiChatRequest> getCapturedRequests() {
        return capturedRequests();
    }

    public void reset() {
        invocationCount.set(0);
        capturedRequests.clear();
        clearFailure();
    }

    private static AiChatResult deterministicResponse(AiChatRequest request) {
        String canonical = canonicalRequest(request);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
        String suffix = HexFormat.of().formatHex(digest, 0, 12);
        return new AiChatResult("fake-chat-response:" + suffix, "fake-chat");
    }

    private static String canonicalRequest(AiChatRequest request) {
        StringBuilder value = new StringBuilder()
                .append("system=").append(request.systemInstructions()).append('\n')
                .append("question=").append(request.question()).append('\n');
        for (AiChatMessage message : request.conversation()) {
            value.append("message=").append(message.role()).append(':').append(message.content()).append('\n');
        }
        for (AiEvidenceBlock evidence : request.evidence()) {
            value.append("evidence=").append(evidence.label()).append(':').append(evidence.content()).append('\n');
        }
        return value.toString();
    }
}
