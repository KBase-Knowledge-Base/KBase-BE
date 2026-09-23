package com.kbase.ai.extraction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.kbase.ai.config.AiProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Versioned structure-aware baseline chunker. Contiguous blocks with distinct
 * known source locations are never merged.
 */
@Component
public class StructureAwareDocumentChunker {

    public static final String VERSION = ChunkingVersions.DOCUMENT;

    private final KBaseLexTokenCounter tokenCounter;
    private final int targetTokens;
    private final int overlapPercent;

    @Autowired
    public StructureAwareDocumentChunker(KBaseLexTokenCounter tokenCounter, AiProperties properties) {
        this(tokenCounter, properties.getChunkTargetTokens(), properties.getChunkOverlapPercent());
    }

    public StructureAwareDocumentChunker(KBaseLexTokenCounter tokenCounter,
            int targetTokens, int overlapPercent) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter");
        if (targetTokens <= 0) {
            throw new IllegalArgumentException("targetTokens must be positive");
        }
        if (overlapPercent < 0 || overlapPercent >= 100) {
            throw new IllegalArgumentException("overlapPercent must be between 0 and 99");
        }
        this.targetTokens = targetTokens;
        this.overlapPercent = overlapPercent;
    }

    public ChunkedDocument chunk(ExtractedDocument document) {
        Objects.requireNonNull(document, "document");
        List<DocumentChunk> chunks = new ArrayList<>();
        List<ExtractedBlock> segment = new ArrayList<>();
        SourceLocation location = null;

        for (ExtractedBlock block : document.blocks()) {
            SourceLocation blockLocation = block.sourceLocation();
            if (!segment.isEmpty() && !Objects.equals(location, blockLocation)) {
                appendSegment(chunks, segment, location);
                segment.clear();
            }
            if (segment.isEmpty()) {
                location = blockLocation;
            }
            segment.add(block);
        }
        if (!segment.isEmpty()) {
            appendSegment(chunks, segment, location);
        }
        return new ChunkedDocument(VERSION, chunks);
    }

    private void appendSegment(List<DocumentChunk> output, List<ExtractedBlock> blocks,
            SourceLocation location) {
        String combined = blocks.stream().map(ExtractedBlock::text)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        List<KBaseLexTokenCounter.Lexeme> lexemes = tokenCounter.tokenize(combined);
        if (lexemes.isEmpty()) {
            return;
        }
        int overlapTokens = Math.min(targetTokens - 1,
                (int) Math.floor(targetTokens * (overlapPercent / 100.0)));
        int start = 0;
        while (start < lexemes.size()) {
            int end = Math.min(lexemes.size(), start + targetTokens);
            String content = combined.substring(lexemes.get(start).start(),
                    lexemes.get(end - 1).end()).trim();
            if (!content.isEmpty()) {
                output.add(new DocumentChunk(output.size(), content, location,
                        tokenCounter.count(content), sha256(content)));
            }
            if (end == lexemes.size()) {
                break;
            }
            int next = end - overlapTokens;
            start = next <= start ? end : next;
        }
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
