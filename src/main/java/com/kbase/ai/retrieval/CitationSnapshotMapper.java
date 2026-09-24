package com.kbase.ai.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.entity.AiMessageSource;

import org.springframework.stereotype.Component;

/** Prepares snapshots for a caller-supplied existing assistant message. */
@Component
public class CitationSnapshotMapper {
    public List<AiMessageSource> map(UUID assistantMessageId, List<EvidenceSource> cited) {
        Objects.requireNonNull(assistantMessageId, "assistantMessageId");
        Objects.requireNonNull(cited, "cited");
        List<AiMessageSource> snapshots = new ArrayList<>(cited.size());
        for (int i = 0; i < cited.size(); i++) {
            EvidenceSource source = cited.get(i);
            AiMessageSource snapshot = new AiMessageSource(assistantMessageId, i,
                    source.documentId(), source.chunkId(), source.documentId(),
                    source.documentName());
            snapshot.setPageNumberSnapshot(source.pageNumber());
            snapshot.setSlideNumberSnapshot(source.slideNumber());
            snapshot.setSectionTitleSnapshot(source.sectionTitle());
            snapshot.setRetrievalScore(source.similarity());
            snapshots.add(snapshot);
        }
        return List.copyOf(snapshots);
    }
}
