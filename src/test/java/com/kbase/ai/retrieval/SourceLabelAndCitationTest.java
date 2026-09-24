package com.kbase.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.AiMessageSource;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;

import org.junit.jupiter.api.Test;

class SourceLabelAndCitationTest {
    private final SourceLabelValidator validator = new SourceLabelValidator();
    private final CitationSnapshotMapper mapper = new CitationSnapshotMapper();

    @Test
    void citedLabelsUseFirstReferenceOrderAndPreserveMergedConstituents() {
        EvidenceSource a = source(1);
        EvidenceSource b = source(2);
        EvidenceSource c = source(3);
        List<EvidenceBlock> blocks = List.of(
                new EvidenceBlock("SOURCE_1", "a b", List.of(a, b)),
                new EvidenceBlock("SOURCE_2", "c", List.of(c)));
        List<EvidenceSource> cited = validator.validate(
                "First [SOURCE_2], then [SOURCE_1], repeat [SOURCE_2].", blocks);
        assertThat(cited).containsExactly(c, a, b);
        List<AiMessageSource> snapshots = mapper.map(UUID.randomUUID(), cited);
        assertThat(snapshots).extracting(AiMessageSource::getSourceOrder).containsExactly(0, 1, 2);
        assertThat(snapshots).extracting(AiMessageSource::getChunkId)
                .containsExactly(c.chunkId(), a.chunkId(), b.chunkId());
        assertThat(snapshots.getFirst().getDocumentIdSnapshot()).isEqualTo(c.documentId());
        assertThat(snapshots.getFirst().getDocumentNameSnapshot()).isEqualTo(c.documentName());
        assertThat(snapshots.getFirst().getPageNumberSnapshot()).isEqualTo(3);
        assertThat(snapshots.getFirst().getSlideNumberSnapshot()).isEqualTo(1);
        assertThat(snapshots.getFirst().getSectionTitleSnapshot()).isEqualTo("Section");
        assertThat(snapshots.getFirst().getRetrievalScore()).isEqualTo(0.7);
    }

    @Test
    void unknownMixedMalformedAndMissingLabelsAreInvalidResponses() {
        List<EvidenceBlock> blocks = List.of(
                new EvidenceBlock("SOURCE_1", "a", List.of(source(1))));
        for (String answer : List.of("[SOURCE_999]", "[SOURCE_1] [SOURCE_999]",
                "[SOURCE_01]", "[source_1]", "uncited answer")) {
            assertThatThrownBy(() -> validator.validate(answer, blocks))
                    .isInstanceOfSatisfying(AiProviderException.class, error ->
                            assertThat(error.category()).isEqualTo(AiProviderErrorCategory.INVALID_RESPONSE));
        }
    }

    private EvidenceSource source(int index) {
        return new EvidenceSource(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Document " + index, 1, index, index, 0.7, 3, 1, "Section");
    }
}
