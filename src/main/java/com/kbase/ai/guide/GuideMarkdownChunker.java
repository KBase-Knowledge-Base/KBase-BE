package com.kbase.ai.guide;

import java.util.List;

import com.kbase.ai.extraction.DocumentChunk;
import com.kbase.ai.extraction.MarkdownStructureParser;
import com.kbase.ai.extraction.StructureAwareDocumentChunker;

import org.springframework.stereotype.Component;

/** M9's documented chunking version is chunk-v1/kbase-lex-v1; no DB field exists for it. */
@Component
public final class GuideMarkdownChunker {
    public static final String VERSION = "guide-markdown-chunk-v1";
    private final MarkdownStructureParser parser = new MarkdownStructureParser();
    private final StructureAwareDocumentChunker chunker;

    public GuideMarkdownChunker(StructureAwareDocumentChunker chunker) {
        this.chunker = chunker;
    }

    public List<GuideChunk> chunk(String markdown) {
        return chunker.chunk(parser.parseMarkdown(markdown)).chunks().stream()
                .map(this::toGuideChunk).toList();
    }

    private GuideChunk toGuideChunk(DocumentChunk chunk) {
        String heading = chunk.sourceLocation().sectionTitle();
        return new GuideChunk(chunk.chunkIndex(), chunk.content(),
                heading == null || heading.isBlank() ? "KBase Guide" : heading,
                chunk.tokenCount(), chunk.contentHash());
    }
}
