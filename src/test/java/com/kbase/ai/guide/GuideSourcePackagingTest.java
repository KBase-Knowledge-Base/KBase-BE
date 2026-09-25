package com.kbase.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class GuideSourcePackagingTest {
    @Test
    void loadsExactlyTheReviewedCanonicalSourcesWithMatchingBytes() throws Exception {
        GuideSourceCatalog catalog = new GuideSourceCatalog();
        GuideSourceLoader loader = new GuideSourceLoader(catalog, new DefaultResourceLoader());
        var loaded = loader.loadAll();
        assertThat(loaded).hasSize(2);
        assertThat(loaded).extracting(source -> source.definition().sourceKey()).containsExactly(
                "docs/product-specs/KBase - Core v1 Specification.md",
                "docs/product-specs/KBase - AI Chatbot v1 Specification.md");
        for (LoadedGuideSource source : loaded) {
            byte[] canonical = Files.readAllBytes(Path.of(source.definition().sourceKey()));
            assertThat(source.bytes()).containsExactly(canonical);
            assertThat(source.contentHash()).isEqualTo(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical)));
        }
    }
}
