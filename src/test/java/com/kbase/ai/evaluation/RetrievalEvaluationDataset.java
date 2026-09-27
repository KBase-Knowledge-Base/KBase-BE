package com.kbase.ai.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Synthetic, non-sensitive annotated evaluation corpus and query set for the
 * KBase Project Assistant retrieval pipeline.
 *
 * <p>Every query declares expected project/source behavior, never raw vector
 * values. The corpus uses topic-axis unit vectors so the deterministic
 * embedding fake controls similarity ordering exactly; similarity scores in
 * this dataset are an experimental construct, not a live-Gemini calibration.</p>
 *
 * <p>Splits: CALIBRATION queries may drive parameter decisions; HOLDOUT must
 * never be tuned on. Project B holds same-axis trap documents that are
 * semantically as strong as Project A evidence and must never surface in
 * Project A retrieval.</p>
 */
public final class RetrievalEvaluationDataset {

    public static final int CANDIDATE_LIMIT = 10;
    public static final int FINAL_CONTEXT_LIMIT = 6;
    public static final double SIMILARITY_THRESHOLD = 0.70;

    public enum Split { CALIBRATION, HOLDOUT }

    public enum Language { EN, VI, MIXED }

    public enum Category {
        DIRECT_FACT, PARAPHRASE, MIXED_WORDING, MULTI_CHUNK, SEMANTIC_DISTRACTOR,
        NO_EVIDENCE, CROSS_PROJECT_TRAP, DELETED_SOURCE, INACTIVE_SOURCE
    }

    /** A synthetic corpus chunk: content, its controlled topic vector and identity. */
    public record CorpusChunk(String documentKey, String displayName, String projectKey,
            int chunkIndex, String content, String contentHash, double[] vector) {
    }

    /** One annotated evaluation query with its expected source behavior. */
    public record EvalQuery(String id, String question, Language language, Category category,
            Split split, String projectKey, List<Double> queryVector,
            Set<String> expectedChunkHashes) {
    }

    private static final int DIMENSIONS = 768;

    private RetrievalEvaluationDataset() {
    }

    // Topic axes shared by corpus chunks and query fixtures.
    public static double[] axis(int axis) {
        double[] vector = new double[DIMENSIONS];
        vector[axis] = 1.0;
        return vector;
    }

    /** Two-topic query vector, normalized; per-axis cosine equals weight/norm. */
    public static double[] mix(int primaryAxis, double primaryWeight,
            int secondaryAxis, double secondaryWeight) {
        double[] vector = new double[DIMENSIONS];
        vector[primaryAxis] = primaryWeight;
        vector[secondaryAxis] = secondaryWeight;
        return normalize(vector);
    }

    /** Equal split over k axes: per-axis cosine is 1/sqrt(k) — always below 0.70 for k >= 3. */
    public static double[] flat(int... axes) {
        double[] vector = new double[DIMENSIONS];
        for (int axis : axes) {
            vector[axis] = 1.0;
        }
        return normalize(vector);
    }

    private static double[] normalize(double[] vector) {
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }

    public static final String PROJECT_A = "AURORA";
    public static final String PROJECT_B = "BOREALIS";

    /**
     * Corpus. DA8 additionally carries inactive version-2 chunks (hashes
     * DA8-v2-0/1) that must never be retrieved; DA9 is activated and then
     * deleted by the harness before queries run.
     */
    public static List<CorpusChunk> corpus() {
        List<CorpusChunk> chunks = new ArrayList<>();
        chunks.add(chunk("DA1", "Aurora Deployment Runbook", PROJECT_A, 0, axis(0),
                "Roll back an Aurora deployment with code AURORA-ROLLBACK-741 and restore the previous artifact."));
        chunks.add(chunk("DA1", "Aurora Deployment Runbook", PROJECT_A, 1, axis(0),
                "Roll forward is only allowed after the rollback health checks pass twice."));
        chunks.add(chunk("DA2", "Aurora Role Guide", PROJECT_A, 0, axis(1),
                "Phan quyen du an: OWNER quan ly thanh vien, MEMBER chi tai len tai lieu cua minh."));
        chunks.add(chunk("DA3", "Aurora Storage Policy", PROJECT_A, 0, axis(2),
                "Storage quota per project is 5 GB and hidden system files are rejected at upload."));
        chunks.add(chunk("DA4", "Aurora API Limits", PROJECT_A, 0, axis(3),
                "The assistant allows 20 AI requests per minute per user across projects."));
        chunks.add(chunk("DA5", "Aurora Operations Handbook", PROJECT_A, 0, axis(4),
                "Nightly backup runs at 02:00 and keeps 14 restore points."));
        chunks.add(chunk("DA5", "Aurora Operations Handbook", PROJECT_A, 1, axis(5),
                "Audit log retention is 180 days for compliance reviews."));
        chunks.add(chunk("DA6", "Aurora Notification Template", PROJECT_A, 0, axis(6),
                "Notification emails are sent from the KBase no-reply sender address."));
        chunks.add(chunk("DA7", "Aurora Secure DB Notes", PROJECT_A, 0, axis(7),
                "Ket noi co so du lieu an toan can xac thuc hai yeu tu cho tai khoan dich vu."));
        chunks.add(chunk("DA8", "Aurora Legacy Archive", PROJECT_A, 0, axis(6),
                "Legacy notification template still describes the no-reply sender address."));
        chunks.add(new CorpusChunk("DA8", "Aurora Legacy Archive", PROJECT_A, 0,
                "Stale rate limit text from an abandoned archive version.", "DA8-v2-0", axis(3)));
        chunks.add(new CorpusChunk("DA8", "Aurora Legacy Archive", PROJECT_A, 1,
                "Stale rollback text from an abandoned archive version.", "DA8-v2-1", axis(0)));
        chunks.add(chunk("DA9", "Aurora Deleted Notes", PROJECT_A, 0, axis(7),
                "Deleted secure database note that must never be resurrected."));
        chunks.add(chunk("DB1", "Borealis Deployment Mirror", PROJECT_B, 0, axis(0),
                "Borealis-only rollback mirror BOREALIS-LEAK-999; never use for Aurora."));
        chunks.add(chunk("DB2", "Borealis Role Mirror", PROJECT_B, 0, axis(1),
                "Borealis-only role mirror; never use for Aurora."));
        return chunks;
    }

    /** True for the two inactive version-2 rows of DA8. */
    public static boolean isInactiveStaging(CorpusChunk chunk) {
        return chunk.contentHash().startsWith("DA8-v2");
    }

    /** DA9 exists only to be deleted by the harness after activation. */
    public static boolean isDeletedDocument(CorpusChunk chunk) {
        return chunk.documentKey().equals("DA9");
    }

    private static CorpusChunk chunk(String documentKey, String displayName, String projectKey,
            int chunkIndex, double[] vector, String content) {
        return new CorpusChunk(documentKey, displayName, projectKey, chunkIndex, content,
                documentKey + "-c" + chunkIndex, vector);
    }

    /** The 36 annotated queries; 20 calibration + 16 holdout. */
    public static List<EvalQuery> queries() {
        List<EvalQuery> queries = new ArrayList<>();
        double[] rollback = axis(0);
        double[] roles = axis(1);
        double[] storage = axis(2);
        double[] rate = axis(3);
        double[] audit = axis(5);
        double[] notify = axis(6);
        double[] secureDb = axis(7);
        double[] rollbackPara = mix(0, 0.9, 1, 0.35);
        double[] rolesPara = mix(1, 0.9, 0, 0.2);
        double[] storageRollback = mix(2, 0.8, 0, 0.45);
        double[] backupAudit = mix(4, 0.9, 5, 0.45);
        double[] opsBoth = mix(4, 0.7071, 5, 0.7071);
        double[] rateNotify = mix(3, 0.93, 6, 0.36);
        double[] weakEcho = flat(0, 1, 2);
        double[] backupNotifyPara = mix(6, 0.9, 4, 0.35);
        double[] rollbackStorage = mix(0, 0.8, 2, 0.45);
        double[] rolesStoragePara = mix(1, 0.9, 2, 0.3);
        double[] noEvidence = flat(5, 6, 7);

        String da1Both = "DA1-c0,DA1-c1";
        String da5Both = "DA5-c0,DA5-c1";
        String da6AndDa8 = "DA6-c0,DA8-c0";

        queries.add(q("C01", "How do I roll back an Aurora deployment?", Language.EN,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, rollback, da1Both));
        queries.add(q("C02", "Phan quyen du an hoat dong the nao?", Language.VI,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, roles, "DA2-c0"));
        queries.add(q("C03", "What is the storage quota for a project?", Language.EN,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, storage, "DA3-c0"));
        queries.add(q("C04", "How many AI requests are allowed per minute?", Language.EN,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, rate, "DA4-c0"));
        queries.add(q("C05", "Ket noi co so du lieu an toan nhu the nao?", Language.VI,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, secureDb, "DA7-c0"));
        queries.add(q("C06", "Can you tell me the way to restore the previous Aurora artifact?",
                Language.EN, Category.PARAPHRASE, Split.CALIBRATION, PROJECT_A, rollbackPara, da1Both));
        queries.add(q("C07", "Ai co the quan ly thanh vien trong du an Aurora?", Language.VI,
                Category.PARAPHRASE, Split.CALIBRATION, PROJECT_A, rolesPara, "DA2-c0"));
        queries.add(q("C08", "Storage limits and rollback codes both matter for the release.",
                Language.MIXED, Category.MIXED_WORDING, Split.CALIBRATION, PROJECT_A,
                storageRollback, "DA3-c0"));
        queries.add(q("C09", "Backup va audit log duoc luu bao lau?", Language.VI,
                Category.MIXED_WORDING, Split.CALIBRATION, PROJECT_A, backupAudit, "DA5-c0"));
        queries.add(q("C10", "Aurora operations: backup schedule and audit retention", Language.EN,
                Category.MULTI_CHUNK, Split.CALIBRATION, PROJECT_A, opsBoth, da5Both));
        queries.add(q("C11", "Rate limit checklist for the assistant", Language.EN,
                Category.SEMANTIC_DISTRACTOR, Split.CALIBRATION, PROJECT_A, rateNotify, "DA4-c0"));
        queries.add(q("C12", "What is the cafeteria Wi-Fi password?", Language.EN,
                Category.NO_EVIDENCE, Split.CALIBRATION, PROJECT_A, noEvidence, ""));
        queries.add(q("C13", "Tell me about rollback", Language.EN,
                Category.NO_EVIDENCE, Split.CALIBRATION, PROJECT_A, weakEcho, ""));
        queries.add(q("C14", "What is the rollback code?", Language.EN,
                Category.CROSS_PROJECT_TRAP, Split.CALIBRATION, PROJECT_A, rollback, da1Both));
        queries.add(q("C15", "Lam sao de quan ly vai trò thành viên?", Language.VI,
                Category.CROSS_PROJECT_TRAP, Split.CALIBRATION, PROJECT_A, roles, "DA2-c0"));
        queries.add(q("C16", "Secure database connections in Aurora?", Language.MIXED,
                Category.DELETED_SOURCE, Split.CALIBRATION, PROJECT_A, secureDb, "DA7-c0"));
        queries.add(q("C17", "How are notification emails sent?", Language.EN,
                Category.INACTIVE_SOURCE, Split.CALIBRATION, PROJECT_A, notify, da6AndDa8));
        queries.add(q("C18", "Audit log retention period?", Language.EN,
                Category.DIRECT_FACT, Split.CALIBRATION, PROJECT_A, audit, "DA5-c1"));
        queries.add(q("C19", "Where do notification messages come from?", Language.EN,
                Category.PARAPHRASE, Split.CALIBRATION, PROJECT_A, backupNotifyPara, da6AndDa8));
        queries.add(q("C20", "Who won the football match yesterday?", Language.EN,
                Category.NO_EVIDENCE, Split.CALIBRATION, PROJECT_A, noEvidence, ""));

        queries.add(q("H01", "Restore the last good Aurora build, how?", Language.EN,
                Category.DIRECT_FACT, Split.HOLDOUT, PROJECT_A, rollback, da1Both));
        queries.add(q("H02", "Vai tro OWNER va MEMBER khac nhau the nao?", Language.VI,
                Category.DIRECT_FACT, Split.HOLDOUT, PROJECT_A, roles, "DA2-c0"));
        queries.add(q("H03", "Project storage capacity?", Language.EN,
                Category.DIRECT_FACT, Split.HOLDOUT, PROJECT_A, storage, "DA3-c0"));
        queries.add(q("H04", "AI usage cap per minute?", Language.EN,
                Category.DIRECT_FACT, Split.HOLDOUT, PROJECT_A, rate, "DA4-c0"));
        queries.add(q("H05", "Xac thuc hai yeu tu khi ket noi database?", Language.VI,
                Category.DIRECT_FACT, Split.HOLDOUT, PROJECT_A, secureDb, "DA7-c0"));
        queries.add(q("H06", "What code do I use to bring back the old release?", Language.EN,
                Category.PARAPHRASE, Split.HOLDOUT, PROJECT_A, mix(0, 0.88, 1, 0.47), da1Both));
        queries.add(q("H07", "Thanh vien nao duoc xoa tai lieu?", Language.VI,
                Category.PARAPHRASE, Split.HOLDOUT, PROJECT_A, rolesStoragePara, "DA2-c0"));
        queries.add(q("H08", "Rollback plan and storage quota for the release", Language.MIXED,
                Category.MIXED_WORDING, Split.HOLDOUT, PROJECT_A, rollbackStorage, da1Both));
        queries.add(q("H09", "Aurora ops runbook: audit trail and nightly backups", Language.EN,
                Category.MULTI_CHUNK, Split.HOLDOUT, PROJECT_A, opsBoth, da5Both));
        queries.add(q("H10", "Old rate limit archive lookup", Language.EN,
                Category.SEMANTIC_DISTRACTOR, Split.HOLDOUT, PROJECT_A, rateNotify, "DA4-c0"));
        queries.add(q("H11", "What is the CEO's home address?", Language.EN,
                Category.NO_EVIDENCE, Split.HOLDOUT, PROJECT_A, noEvidence, ""));
        queries.add(q("H12", "Aurora rollback brief", Language.EN,
                Category.NO_EVIDENCE, Split.HOLDOUT, PROJECT_A, weakEcho, ""));
        queries.add(q("H13", "Rollback code lookup", Language.EN,
                Category.CROSS_PROJECT_TRAP, Split.HOLDOUT, PROJECT_A, rollback, da1Both));
        queries.add(q("H14", "Quan ly vai trò?", Language.VI,
                Category.CROSS_PROJECT_TRAP, Split.HOLDOUT, PROJECT_A, roles, "DA2-c0"));
        queries.add(q("H15", "Notification sender address?", Language.EN,
                Category.INACTIVE_SOURCE, Split.HOLDOUT, PROJECT_A, notify, da6AndDa8));
        queries.add(q("H16", "Aurora secure DB two-factor note?", Language.MIXED,
                Category.DELETED_SOURCE, Split.HOLDOUT, PROJECT_A, secureDb, "DA7-c0"));
        return queries;
    }

    private static EvalQuery q(String id, String question, Language language, Category category,
            Split split, String projectKey, double[] queryVector, String expectedHashes) {
        Set<String> expected = new LinkedHashSet<>();
        for (String hash : expectedHashes.split(",")) {
            if (!hash.isBlank()) {
                expected.add(hash.trim());
            }
        }
        List<Double> vector = new ArrayList<>(queryVector.length);
        for (double value : queryVector) {
            vector.add(value);
        }
        return new EvalQuery(id, question, language, category, split, projectKey, vector, expected);
    }
}
