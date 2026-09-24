package com.kbase.ai.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;

import org.springframework.stereotype.Component;

/** Accepts only exact labels issued for this request; model metadata is ignored. */
@Component
public class SourceLabelValidator {
    private static final Pattern REFERENCE = Pattern.compile("\\[(?i:SOURCE)[^\\]\\r\\n]*\\]");
    private static final Pattern CANONICAL = Pattern.compile("SOURCE_[1-9][0-9]*");

    public List<EvidenceSource> validate(String answer, List<EvidenceBlock> evidence) {
        if (answer == null || answer.isBlank()) {
            throw invalid();
        }
        Map<String, EvidenceBlock> issued = new HashMap<>();
        for (EvidenceBlock block : evidence) {
            issued.put(block.label(), block);
        }
        List<EvidenceSource> cited = new ArrayList<>();
        Set<String> usedLabels = new HashSet<>();
        Set<java.util.UUID> usedChunks = new HashSet<>();
        Matcher matcher = REFERENCE.matcher(answer);
        while (matcher.find()) {
            String label = matcher.group().substring(1, matcher.group().length() - 1);
            EvidenceBlock block = issued.get(label);
            if (!CANONICAL.matcher(label).matches() || block == null) {
                throw invalid();
            }
            if (usedLabels.add(label)) {
                for (EvidenceSource source : block.sources()) {
                    if (usedChunks.add(source.chunkId())) {
                        cited.add(source);
                    }
                }
            }
        }
        if (cited.isEmpty()) {
            throw invalid();
        }
        return List.copyOf(cited);
    }

    private static AiProviderException invalid() {
        return new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
    }
}
