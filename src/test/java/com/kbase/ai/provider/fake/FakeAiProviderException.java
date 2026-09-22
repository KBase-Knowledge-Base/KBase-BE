package com.kbase.ai.provider.fake;

/** Controlled failure raised by the deterministic provider fakes. */
public final class FakeAiProviderException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        TIMEOUT
    }

    private final Kind kind;

    public FakeAiProviderException(Kind kind) {
        super("fake AI provider failure: " + kind);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
