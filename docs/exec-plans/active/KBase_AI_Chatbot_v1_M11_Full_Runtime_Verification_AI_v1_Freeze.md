# KBase AI Chatbot v1 – M11 Full Runtime Verification / AI v1 Freeze

**Status:** ACTIVE – IN PROGRESS (deterministic Docker path repaired 2026-09-25; AI v1 is not frozen)
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

## M11 preflight result (2026-09-25)

The entry build and configuration checks passed, but the mandatory deterministic Docker provider gate is blocked:

- `mvn -B -ntp clean verify` completed with `BUILD SUCCESS`, `371` tests, `0` failures, `0` errors, `0` skipped, and Spring Boot jar repackage success. Non-fatal Testcontainers/scheduler teardown connection warnings occurred after test contexts were shutting down.
- `docker compose -f docker-compose.yml config --quiet` and `docker compose -f docker-compose.yml -f docker-compose.mail-test.yml config --quiet` passed. `git diff --check` passed and the initial/final worktree status is clean apart from the documentation changes recorded by this plan.
- The executable jar contains `AiGeminiProviderConfiguration` and the Spring AI Gemini adapters, but no `FakeAiChatModel` or `FakeAiEmbeddingModel` classes. Those fakes are under `src/test/java` only.
- `application-test.yml` has no deterministic provider binding. Docker passes `KBASE_AI_ENABLED` and `KBASE_AI_GEMINI_API_KEY`; when AI is enabled, `AiGeminiProviderConfiguration` requires the key and constructs Google GenAI clients.
- No already-approved, explicit opt-in Docker test profile, packaged deterministic adapter, or other source-backed provider injection path was found.

The provider-backed Docker startup and all dependent runtime journeys were intentionally not started. No real Gemini key, public Gemini network, fake key, DNS interception, production fake, destructive Docker reset, or production-default change was used. The exact blocker is:

`BLOCKED_RUNTIME_PROVIDER_TEST_PATH_MISSING`

This historical preflight finding kept M11 active and prevented an AI v1 freeze claim.

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
- rejoining before `purgeAfter` restores access to the retained conversation under the authoritative M8 retention rule;
- rejoining after completed purge does not resurrect history;
- an in-flight request invalidated by membership revocation remains invalid even if the user rejoins before provider finalization;
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
| AI-VERIFY-01 | Clean Maven build and exact totals | PASS — post-fix `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 373 tests, 0 failures/errors/skips, jar repackage pass; teardown scheduler/Testcontainers connection warnings remain non-fatal |
| AI-VERIFY-02 | Fresh Compose startup, Flyway V1–V4, Hibernate validation, health/log evidence | PASS — clean isolated `kbase-m11runtime` Compose with mail double, pinned `pgvector/pgvector:0.8.6-pg17-bookworm`, profile `runtime-test`; Flyway V1–V4 and Hibernate validation completed; `/v3/api-docs` 200 |
| AI-VERIFY-03 | Project Assistant upload/index/grounded/no-evidence HTTP journey | PASS — real HTTP: Markdown upload with declared `text/markdown` → `READY` → `GROUNDED` with 1 source; unrelated question → `NO_EVIDENCE` with 0 sources |
| AI-VERIFY-04 | Provider/Redis failure isolation plus Core health | PARTIAL — deterministic `UNAVAILABLE` produces Guide 503 while authenticated Core project list remains 200; indexing retry, chat failure, Redis-unavailable and leak scans remain required |
| AI-VERIFY-05 | Cross-project, private conversation, prompt-injection, and source-authz evidence | NOT RUN — prior M6/M7 integration evidence is not substituted for Docker runtime evidence |
| AI-VERIFY-06 | Remove/rejoin and retention/purge evidence | NOT RUN — requires the approved controllable-time runtime path |
| AI-VERIFY-07 | Guide approved/unsupported question evidence | PASS — authenticated real HTTP query against packaged corpus: documented permission question → `GROUNDED` with 1 source; unrelated question → `NO_EVIDENCE` with 0 sources |
| AI-VERIFY-08 | Restart/stale-job resume and persistence/ephemerality evidence | NOT RUN — clean runtime is available; stale-job/restart evidence remains required; known chat abrupt-death limitation remains open |
| AI-VERIFY-09 | Docs/code/Flyway/OpenAPI/generated snapshot consistency audit | IN PROGRESS — source/config mismatch in local Core-only `.env` was isolated from M11 by an explicit pinned pgvector test override; generated snapshots still require final audit |
| AI-VERIFY-10 | Completed freeze report and synchronized living docs | NOT RUN — completion gate is not met and the plan remains active |

## M11 execution log

The blocker was repaired with a packaged deterministic port adapter that requires all of: `kbase.ai.enabled=true`, explicit `kbase.ai.provider.mode=deterministic`, Spring profile `runtime-test`, and an acknowledgement flag. Gemini remains the default provider mode; no credential or public network is used. The local `.env` PostgreSQL image was corrected from Core-only `postgres:17-alpine` to the approved pgvector image; the M11 Compose override pins that same image for reproducibility. A companion failure override induces only safe provider unavailability.

The generated snapshots were inspected without modification: `docs/generated/db-schema.md` records 18 persistent tables and `docs/generated/api-schema.md` records 38 paths / 57 operations / 15 tags; Flyway source remains V1–V4. M11 is no longer blocked by provider injection, but it is not complete: AI-VERIFY-04 remaining cases and AI-VERIFY-05/06/08/09 must be executed before any freeze claim or M12.

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
