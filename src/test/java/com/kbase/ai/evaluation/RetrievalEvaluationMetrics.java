package com.kbase.ai.evaluation;

import java.util.List;

/**
 * Pure metric aggregation for the retrieval evaluation harness. Metrics are
 * computed from per-query outcomes so they stay machine-checkable and unit
 * testable without a database.
 */
public final class RetrievalEvaluationMetrics {

    /** Outcome of one evaluated query against the live pipeline. */
    public record PerQuery(String id, RetrievalEvaluationDataset.Category category,
            boolean expectsEvidence, int expectedCount, int recalledCandidates,
            boolean selectedHit, boolean producedNoEvidence, boolean leaked,
            boolean citationValid) {

        public double recallAtK() {
            return expectedCount == 0 ? 1.0 : (double) recalledCandidates / expectedCount;
        }
    }

    public record Report(int queryCount, double meanRecallAtK, double selectedContextHitRate,
            int noEvidenceFalsePositives, int noEvidenceFalseNegatives,
            int crossProjectLeakageCount, double citationSourceValidity) {
    }

    private RetrievalEvaluationMetrics() {
    }

    public static Report aggregate(List<PerQuery> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("at least one per-query result is required");
        }
        double recallSum = 0.0;
        int hitQueries = 0;
        int evidenceQueries = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int leakage = 0;
        int validQueries = 0;
        for (PerQuery result : results) {
            recallSum += result.recallAtK();
            if (result.expectsEvidence()) {
                evidenceQueries++;
                if (result.selectedHit()) {
                    hitQueries++;
                }
                if (result.producedNoEvidence()) {
                    falseNegatives++;
                }
            } else if (!result.producedNoEvidence()) {
                falsePositives++;
            }
            if (result.leaked()) {
                leakage++;
            }
            if (result.citationValid()) {
                validQueries++;
            }
        }
        return new Report(results.size(), recallSum / results.size(),
                evidenceQueries == 0 ? 1.0 : (double) hitQueries / evidenceQueries,
                falsePositives, falseNegatives, leakage,
                (double) validQueries / results.size());
    }
}
