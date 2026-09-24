package com.kbase.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.repository.DocumentAiChunkMatch;

import org.junit.jupiter.api.Test;

class EvidenceSelectorTest {
    private final EvidenceSelector selector = new EvidenceSelector();
    private final UUID project = UUID.randomUUID();
    private final UUID document = UUID.randomUUID();

    @Test
    void thresholdIsInclusiveMinimumAndNullDoesNotFilter() {
        List<DocumentAiChunkMatch> rows = List.of(
                row(document, 1, "a", "a", 0.90, 1, null, "A"),
                row(document, 3, "b", "b", 0.69, 1, null, "A"));
        assertThat(selector.select(rows, 0.70, 6)).extracting(EvidenceBlock::content)
                .containsExactly("a");
        assertThat(selector.select(rows, null, 6)).extracting(EvidenceBlock::content)
                .containsExactly("a", "b");
    }

    @Test
    void dedupKeepsHighestRankWithinDocumentButNotAcrossDocuments() {
        UUID other = UUID.randomUUID();
        List<EvidenceBlock> selected = selector.select(List.of(
                row(document, 1, "first", "hash", 0.9, null, null, null),
                row(document, 2, "duplicate", "hash", 0.8, null, null, null),
                row(other, 1, "other", "hash", 0.7, null, null, null)), null, 6);
        assertThat(selected).extracting(EvidenceBlock::content).containsExactly("first", "other");
        assertThat(selected).extracting(EvidenceBlock::label).containsExactly("SOURCE_1", "SOURCE_2");
        assertThat(selected.getFirst().sources().getFirst().rank()).isEqualTo(1);
        assertThat(selected.get(1).sources().getFirst().rank()).isEqualTo(3);
    }

    @Test
    void adjacentMergeRetainsBothRealChunksAndFinalLimitCountsChunks() {
        List<EvidenceBlock> selected = selector.select(List.of(
                row(document, 4, "alpha", "a", 0.9, 1, null, "A"),
                row(document, 5, "beta", "b", 0.8, 1, null, "A"),
                row(document, 6, "gamma", "c", 0.7, 1, null, "A")), null, 2);
        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().content()).isEqualTo("alpha\n\nbeta");
        assertThat(selected.getFirst().sources()).hasSize(2);
        assertThat(selected.getFirst().sources()).extracting(EvidenceSource::chunkIndex)
                .containsExactly(4, 5);
    }

    @Test
    void incompatibleLocationsAndMissingIndexDoNotMerge() {
        List<EvidenceBlock> selected = selector.select(List.of(
                row(document, 4, "a", "a", .9, 1, null, "A"),
                row(document, 6, "b", "b", .8, 1, null, "A"),
                row(document, 5, "c", "c", .7, 2, null, "A"),
                row(document, 5, "d", "d", .6, 1, 1, "A"),
                row(document, 5, "e", "e", .5, 1, null, "B")), null, 6);
        assertThat(selected).hasSize(5);
    }

    private DocumentAiChunkMatch row(UUID documentId, int index, String content,
            String hash, double similarity, Integer page, Integer slide, String section) {
        return new DocumentAiChunkMatch(UUID.randomUUID(), project, documentId, "Document",
                1, index, content, page, slide, section, 2, hash, similarity);
    }
}
