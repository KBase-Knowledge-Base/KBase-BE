package com.kbase.ai.guide;

import java.util.List;

import org.springframework.stereotype.Component;

/** The Guide corpus is deliberately a source-code allowlist, never configuration. */
@Component
public final class GuideSourceCatalog {
    private static final List<GuideSourceDefinition> SOURCES = List.of(
            new GuideSourceDefinition(
                    "docs/product-specs/KBase - Core v1 Specification.md",
                    "classpath:/kbase-guide/KBase - Core v1 Specification.md",
                    "KBase – Core v1 Specification"),
            new GuideSourceDefinition(
                    "docs/product-specs/KBase - AI Chatbot v1 Specification.md",
                    "classpath:/kbase-guide/KBase - AI Chatbot v1 Specification.md",
                    "KBase – AI Chatbot v1 Specification"));

    public List<GuideSourceDefinition> sources() {
        return SOURCES;
    }

    public GuideSourceDefinition bySourceKey(String sourceKey) {
        return SOURCES.stream().filter(source -> source.sourceKey().equals(sourceKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Guide source key"));
    }
}
