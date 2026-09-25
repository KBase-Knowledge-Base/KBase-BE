package com.kbase.ai.guide;

/** Packaged canonical bytes and metadata. Hashes always cover the exact bytes. */
public record LoadedGuideSource(GuideSourceDefinition definition, byte[] bytes, String text,
        String contentHash) {
    public LoadedGuideSource {
        bytes = bytes.clone();
    }
}
