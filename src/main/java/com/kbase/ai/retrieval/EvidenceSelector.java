package com.kbase.ai.retrieval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.repository.DocumentAiChunkMatch;

import org.springframework.stereotype.Component;

/** Deterministic threshold, deduplication and bounded adjacent selection. */
@Component
public class EvidenceSelector {
    public List<EvidenceBlock> select(List<DocumentAiChunkMatch> candidates,
            Double minimumSimilarity, int finalChunkLimit) {
        Objects.requireNonNull(candidates, "candidates");
        if (finalChunkLimit <= 0) {
            throw new IllegalArgumentException("finalChunkLimit must be positive");
        }
        List<MutableBlock> blocks = new ArrayList<>();
        Set<DedupKey> seen = new HashSet<>();
        int selectedChunks = 0;
        for (int i = 0; i < candidates.size() && selectedChunks < finalChunkLimit; i++) {
            DocumentAiChunkMatch match = candidates.get(i);
            if (match == null || match.content() == null || match.content().isBlank()
                    || !Double.isFinite(match.similarity())
                    || minimumSimilarity != null && match.similarity() < minimumSimilarity) {
                continue;
            }
            if (!seen.add(new DedupKey(match.documentId(), match.contentHash()))) {
                continue;
            }
            EvidenceSource source = new EvidenceSource(match.id(), match.projectId(),
                    match.documentId(), match.documentName(), match.indexVersion(),
                    match.chunkIndex(), i + 1, match.similarity(), match.pageNumber(),
                    match.slideNumber(), match.sectionTitle());
            MutableBlock target = null;
            for (MutableBlock block : blocks) {
                if (canAppend(block, source)) {
                    target = block;
                    break;
                }
            }
            if (target == null) {
                blocks.add(new MutableBlock(match.content(), source));
            }
            else {
                target.content.append("\n\n").append(match.content());
                target.sources.add(source);
            }
            selectedChunks++;
        }
        List<EvidenceBlock> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            MutableBlock block = blocks.get(i);
            result.add(new EvidenceBlock("SOURCE_" + (i + 1), block.content.toString(), block.sources));
        }
        return List.copyOf(result);
    }

    private static boolean canAppend(MutableBlock block, EvidenceSource next) {
        EvidenceSource last = block.sources.getLast();
        return Objects.equals(last.projectId(), next.projectId())
                && Objects.equals(last.documentId(), next.documentId())
                && last.indexVersion() == next.indexVersion()
                && next.chunkIndex() == last.chunkIndex() + 1
                && Objects.equals(last.pageNumber(), next.pageNumber())
                && Objects.equals(last.slideNumber(), next.slideNumber())
                && Objects.equals(last.sectionTitle(), next.sectionTitle());
    }

    private record DedupKey(UUID documentId, String contentHash) { }

    private static final class MutableBlock {
        private final StringBuilder content;
        private final List<EvidenceSource> sources = new ArrayList<>();

        private MutableBlock(String content, EvidenceSource source) {
            this.content = new StringBuilder(content);
            this.sources.add(source);
        }
    }
}
