# KBase – Real Gemini Provider Smoke Hardening

**Status:** PASS – COMPLETED (2026-09-26)
**Baseline:** `feat-AI` @ `684ca93551af7731dae75372b108f8b99441350b` ("Review adapter")
**Type:** Narrow post-smoke hardening/maintenance — KHÔNG phải M12, KHÔNG phải AI v2, KHÔNG phải RAG phase.

## Goal

1. Harden manual Real Gemini smoke để mechanically guarantee không chạy background AI jobs.
2. Enforce exact candidate models trước mọi live call.
3. Sửa stale living docs (F-03/F-04) + consistency scan.
4. Re-verify (automated + real re-smoke) và hoàn tất baseline trước phase "Real Gemini RAG Golden Journey".

## Verification record

| Item | Required evidence | Status |
|---|---|---|
| F-01 isolation | Scheduling infrastructure không thể chạy trong smoke context; Guide sync không embedding inline; pending job không thể claim | PASS — `@EnableScheduling` tách sang `AiWorkerSchedulingConfiguration` với `@ConditionalOnProperty(kbase.ai.worker.scheduling-enabled, matchIfMissing=true)`; harness set `false` + runtime assert `backgroundSchedulingActive=false`; `GuideSourceSynchronizationService` chỉ reconcile hash/enqueue intent (không có `AiEmbeddingModel` dependency — code-verified); DB postcondition `jobs_updated_recently=0` sau smoke |
| F-01 automated evidence | Context-runner test chứng minh default ON + property OFF removes processor | PASS — `AiWorkerSchedulingIsolationTest` 3/3: default → `internalScheduledAnnotationProcessor` present; property false → absent; explicit true → present; `aiClock` luôn có |
| F-02 model guards | Preflight fail-before-provider-call trên mọi drift | PASS — `candidateViolations()` pure check chạy TRƯỚC mọi provider call; sai mode/chat model/embedding model/dims → RESULT=FAIL không gửi request; `RealGeminiSmokePreflightTest` 6/6 |
| Harness hardening | Bean là expected Gemini adapter; response trim-tolerant; safe failure logging (category/type, không raw vendor body) | PASS — instanceof check cả 2 adapter; `AiProviderException.category()` logged, không raw message |
| Real Gemini re-smoke | 1 explicit chat + 1 explicit embedding, profile local, candidate đúng | PASS — preflight violations=0; backgroundSchedulingActive=false; CHAT_OK `gemini-3.5-flash-lite` → "KBASE_GEMINI_OK" (15 chars); EMBED_OK `gemini-embedding-2` 768 allFinite; RESULT=PASS |
| F-03 QUALITY_SCORE | "not exercised/not performed" → phân biệt adapter smoke vs RAG journey | PASS — 2 rows cập nhật "provider-adapter smoke verified; full live RAG golden journey not yet exercised (pending owner approval)"; debt không đổi |
| F-04 CURRENT_STATE metadata | "Người cập nhật" phản ánh state hiện tại | PASS — "Real Gemini Provider Smoke & Final Handoff" |
| Living-doc scan | 0 stale current-state phrase | PASS |
| Full regression | `mvn -B -ntp clean verify` | PASS — **398 tests, 0 failures/errors/skipped** (389 baseline + 3 scheduling isolation + 6 preflight); jar repackage; compose config PASS; diff-check PASS |
| Secret audit | Diff/log 0 credential | PASS — 0 AIza/header hits trong smoke log; tracked diff 0 secret; `.env` vẫn ignored/untracked |

## Execution log

### 2026-09-26 — hardening + re-smoke

- Baseline đầu tiên (background) bị **vô hiệu do race condition từ agent**: focused test chạy song song với full verify dùng chung `target/` → 4 class context-load fail (`class ... cannot be opened because it does not exist`). Không phải product regression — full clean verify chạy lại một mình: **398/398 PASS**.
- Isolation mechanism: `@EnableScheduling` di chuyển từ `AiWorkerConfiguration` sang `AiWorkerSchedulingConfiguration` mới với property-gate (`matchIfMissing=true` → production/runtime mặc định KHÔNG đổi; không runtime config nào trong repo set false). Harness là nơi duy nhất set `false`.
- Request-count integrity: 2 explicit adapter invocations duy nhất trong harness; scheduler không thể fire; Guide sync không gọi embedding trực tiếp; DB postcondition xác nhận 0 job executed → claim "2 explicit provider requests; background provider execution disabled" defensible.

## Final result

**PASS.** Manual Real Gemini smoke giờ là operator-only verification có isolation cơ học; automated gate vẫn 0 Gemini call và 0 skipped. **Full Real Gemini RAG Golden Journey has NOT been executed** — pending owner approval.
