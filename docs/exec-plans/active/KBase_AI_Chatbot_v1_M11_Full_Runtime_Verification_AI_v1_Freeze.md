# KBase AI Chatbot v1 – M11 Full Runtime Verification / AI v1 Freeze

**Status:** ACTIVE – planning-only handoff; M11 implementation has not started
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md` (§20)  
**Depends on:** Completed AI M0–M10; Core v1 remains frozen

## Goal

Prove the implemented AI v1 behavior in a production-like local runtime, reconcile docs/code/schema/API evidence, and produce the AI v1 freeze report. This slice is verification and evidence-led correction only; it must not broaden the product boundary.

## Entry gate

M10 Gate is PASS on `2026-09-25`:

- `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 371 tests, 0 failures/errors/skips;
- Redis usage guard integration: 5/5; M10 focused regression and OpenAPI suites pass;
- runtime OpenAPI remains exactly 38 paths / 57 operations / 15 tags;
- `docker compose -f docker-compose.yml config --quiet` and `git diff --check` pass;
- no Flyway migration or `docs/generated/db-schema.md` change;
- no real Gemini credential or public Gemini network was used.

The worktree contains intentional M10 implementation and documentation changes. Preserve them; do not reset, commit, push, merge, rebase, cherry-pick, or change branches during this handoff.

## In scope — AI-VERIFY-01..10

### AI-VERIFY-01 — Clean build

Run `mvn -B -ntp clean verify` and record the exact test totals, failures, skips, packaging result, and any non-fatal infrastructure teardown warnings.

### AI-VERIFY-02 — Clean Docker startup

Verify the documented fresh KBase Compose topology with pgvector PostgreSQL, Redis, MinIO, and backend:

- validate the base and any approved mail-double Compose configuration;
- start from a verified clean KBase runtime state and wait on healthchecks, not sleeps;
- prove Flyway V1–V4 apply cleanly and Hibernate validation succeeds;
- verify AI configuration, Redis usage namespace/window, retrieval threshold, Guide resources, and service-name wiring;
- capture backend startup and shutdown evidence without secrets or raw AI content.

The runtime smoke must use a deterministic provider/test configuration that does not require a real Gemini credential or public network. If the current runtime has no safe injection path, stop and record the gap rather than claiming a provider-backed pass or silently changing production defaults.

### AI-VERIFY-03 — Project Assistant golden journey

Through the real HTTP/runtime boundary, prove:

```text
upload supported document
→ PENDING/PROCESSING
→ READY
→ grounded project question
→ answer + authorized source
→ unrelated/no-evidence question
→ deterministic NO_EVIDENCE refusal without chat generation
```

Also verify the M10 guard is charged only after capability/access/creator preflight, accepted failures consume one request, and the shared Project Assistant/Guide budget is enforced without persisting rate state in PostgreSQL.

### AI-VERIFY-04 — Failure isolation

Use a deterministic unavailable/timeout provider mode to prove that:

- document indexing retries or reaches a safe terminal failure with bounded error categories;
- chat/provider failure produces the stable public failure state/contract;
- Redis usage-guard failure is isolated to guarded AI requests with stable 503 behavior;
- Core upload, download, metadata search, and non-AI authorization remain healthy;
- no prompt, chunk, answer, vector, credential, storage key, lease, or raw provider detail enters responses or logs.

### AI-VERIFY-05 — Security journeys

Verify the complete boundary matrix in runtime evidence:

- cross-project vector trap cannot cross project retrieval boundaries;
- private conversation create/read/send/source access is correct for MEMBER, OWNER, ADMIN, non-member, former member, and creator/non-creator cases;
- prompt-injection text is treated as untrusted project content and cannot override grounding/source rules;
- source reads and finalization re-check current authorization after revoke/delete/document changes;
- Guide retrieval remains limited to the reviewed two-source allowlist and never falls back to project corpus.

### AI-VERIFY-06 — Retention journeys

With a controllable clock or the repository's approved test-time path, verify removal/rejoin and purge behavior:

- retention eligibility and `CONVERSATION_PURGE` behavior are deterministic;
- removed users cannot regain access to retained private conversation data merely by rejoining;
- purge removes the intended conversation/message/source data and leaves unrelated project/core data intact;
- the evidence distinguishes provider-independent purge from live provider behavior.

### AI-VERIFY-07 — Guide journey

Through `POST /api/v1/ai/guide/query`, prove that an approved product question returns a grounded answer/source from the packaged allowlisted corpus, while an unsupported/internal question returns deterministic `NO_EVIDENCE`/refusal. Verify the endpoint remains authenticated, stateless, project-free, and protected by the shared usage guard.

### AI-VERIFY-08 — Restart/recovery

Restart the backend while a durable AI indexing/reindex job is pending or stale and prove the documented claim/retry/resume behavior, including stale-owner protection and last-good Guide state where applicable. Verify PostgreSQL/MinIO persistence and Redis ephemerality according to the runtime contract.

Chat `PROCESSING` after abrupt JVM death is a separate known limitation unless an approved recovery policy already exists. Do not infer recovery from ordinary provider failure; either prove the approved policy or record the exact limitation and its freeze classification.

### AI-VERIFY-09 — Consistency audit

Perform a source-of-truth audit across:

- product/design documents and the active/completed execution plans;
- Java configuration, controllers, services, repositories, provider ports, and logging;
- Flyway V1–V4, Hibernate validation, and `docs/generated/db-schema.md`;
- runtime `/v3/api-docs`, OpenAPI contract tests, and `docs/generated/api-schema.md`;
- error catalog, HTTP mappings, Compose environment passthrough, and documented startup/reset commands.

Any correction must be evidence-backed and limited to the affected source/documentation; do not reinterpret Core v1 or add a feature to make the audit pass.

### AI-VERIFY-10 — Freeze report

When AI-VERIFY-01..09 have evidence, complete this plan and move it to `docs/exec-plans/completed/`. Record:

- a pass/fail matrix with commands, runtime conditions, and artifact/log/database evidence;
- all non-blocking limitations, including no real Gemini smoke, fixed worker lease/no heartbeat, and any chat abrupt-recovery policy result;
- final API/schema counts and generated snapshot status;
- updates to `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/RELIABILITY.md`, affected testing/development/integration/security/deployment docs, and the technical-debt tracker when work is intentionally deferred.

## Explicit exclusions

- frontend, streaming/SSE, Project Chat, broker, OCR/multimodal, XLS/XLSX RAG, new Guide corpus/source management, persistent Guide conversations, or any unapproved product/API behavior;
- real Gemini credentials, public Gemini network calls, or claims of production-provider calibration;
- schema/migration changes unless a separately approved source-of-truth decision proves one is required;
- changing Core v1 behavior, reopening completed AI slices, or replacing deterministic evidence with intuition;
- commit, push, PR, merge, rebase, cherry-pick, branch changes, or destructive cleanup outside explicitly verified KBase runtime volumes.

## Verification record

Populate this table during M11 execution; do not mark an item PASS without runnable evidence.

| Item | Required evidence | Status |
|---|---|---|
| AI-VERIFY-01 | Clean Maven build and exact totals | NOT STARTED |
| AI-VERIFY-02 | Fresh Compose startup, Flyway V1–V4, Hibernate validation, health/log evidence | NOT STARTED |
| AI-VERIFY-03 | Project Assistant upload/index/grounded/no-evidence HTTP journey | NOT STARTED |
| AI-VERIFY-04 | Provider/Redis failure isolation plus Core health | NOT STARTED |
| AI-VERIFY-05 | Cross-project, private conversation, prompt-injection, and source-authz evidence | NOT STARTED |
| AI-VERIFY-06 | Remove/rejoin and retention/purge evidence | NOT STARTED |
| AI-VERIFY-07 | Guide approved/unsupported question evidence | NOT STARTED |
| AI-VERIFY-08 | Restart/stale-job resume and persistence/ephemerality evidence | NOT STARTED |
| AI-VERIFY-09 | Docs/code/Flyway/OpenAPI/generated snapshot consistency audit | NOT STARTED |
| AI-VERIFY-10 | Completed freeze report and synchronized living docs | NOT STARTED |

## Completion gate

M11 may be marked PASS only when all of the following are true:

- AI-VERIFY-01..09 have reproducible evidence and no unexplained critical failure;
- Core + AI full suite and clean Compose runtime are green;
- no cross-project or source-authorization leakage is observed;
- strict `NO_EVIDENCE` behavior is verified for Project Assistant and Guide;
- failure, retention, usage-guard, restart/recovery, and privacy claims match runtime evidence;
- generated API/DB snapshots and living docs match their verified source of truth;
- accepted limitations are explicit, tracked, and not misreported as verified behavior;
- the completed M11 freeze report leaves the repository restartable with a clear next action.

## Handoff notes

- M10's Redis guard, OpenAPI, observability, and leakage audit are implementation evidence, not a substitute for this runtime gate.
- The M10 plan is archived beside the completed M0–M9 AI plans; this file is the only active AI slice.
- No M11 implementation has been performed in the handoff that created this plan.
