package com.kbase.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit evidence for the harness metric math (no database involved). */
class RetrievalEvaluationMetricsTest {

    @Test
    void aggregatesRecallHitRateNoEvidenceLeakageAndValidity() {
        var evidenceHit = new RetrievalEvaluationMetrics.PerQuery("C01",
                RetrievalEvaluationDataset.Category.DIRECT_FACT, true, 2, 2, true, false, false, true);
        var evidencePartial = new RetrievalEvaluationMetrics.PerQuery("C02",
                RetrievalEvaluationDataset.Category.MULTI_CHUNK, true, 2, 1, true, false, false, true);
        var evidenceMissed = new RetrievalEvaluationMetrics.PerQuery("C03",
                RetrievalEvaluationDataset.Category.PARAPHRASE, true, 1, 0, false, true, false, true);
        var noEvidenceCorrect = new RetrievalEvaluationMetrics.PerQuery("C04",
                RetrievalEvaluationDataset.Category.NO_EVIDENCE, false, 0, 0, false, true, false, true);
        var noEvidenceFalsePositive = new RetrievalEvaluationMetrics.PerQuery("C05",
                RetrievalEvaluationDataset.Category.NO_EVIDENCE, false, 0, 1, false, false, false, true);
        var leakage = new RetrievalEvaluationMetrics.PerQuery("C06",
                RetrievalEvaluationDataset.Category.CROSS_PROJECT_TRAP, true, 1, 1, true, false, true, false);

        RetrievalEvaluationMetrics.Report report = RetrievalEvaluationMetrics.aggregate(
                List.of(evidenceHit, evidencePartial, evidenceMissed, noEvidenceCorrect,
                        noEvidenceFalsePositive, leakage));

        assertThat(report.queryCount()).isEqualTo(6);
        // (1.0 + 0.5 + 0.0 + 1.0 + 1.0 + 1.0) / 6
        assertThat(report.meanRecallAtK()).isEqualTo(0.75);
        // Four evidence queries (the trap query expects evidence too), three hit.
        assertThat(report.selectedContextHitRate()).isEqualTo(0.75);
        assertThat(report.noEvidenceFalsePositives()).isEqualTo(1);
        assertThat(report.noEvidenceFalseNegatives()).isEqualTo(1);
        assertThat(report.crossProjectLeakageCount()).isEqualTo(1);
        assertThat(report.citationSourceValidity()).isEqualTo(5.0 / 6.0);
    }

    @Test
    void emptyResultsAreRejected() {
        assertThatThrownBy(() -> RetrievalEvaluationMetrics.aggregate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
