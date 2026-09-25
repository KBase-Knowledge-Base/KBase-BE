package com.kbase.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.kbase.ai.extraction.KBaseLexTokenCounter;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;

import org.junit.jupiter.api.Test;

class GuideMarkdownChunkerTest {
    @Test
    void preservesHeadingPathsAndDoesNotTreatFencedCodeAsHeading() {
        GuideMarkdownChunker chunker = new GuideMarkdownChunker(new StructureAwareDocumentChunker(
                new KBaseLexTokenCounter(), 700, 12));
        var chunks = chunker.chunk("# KBase\n\n## Permissions\n\nMEMBER may read.\n\n```md\n# not a heading\n```\n\nOWNER may manage.");
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.headingPath()).startsWith("KBase");
            assertThat(chunk.tokenCount()).isPositive();
            assertThat(chunk.contentHash()).hasSize(64);
        });
        assertThat(chunks).extracting(GuideChunk::headingPath).contains("KBase > Permissions")
                .noneMatch(path -> path.contains("not a heading"));
    }
}
