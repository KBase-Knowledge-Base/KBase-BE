# KBase AI Chatbot v1 – M11 Full Runtime Verification / AI v1 Freeze

**Status:** PASS – AI v1 FROZEN (2026-09-26; all AI-VERIFY-01..10 have executable evidence)
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
| AI-VERIFY-01 | Clean Maven build and exact totals | PASS — final gate `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 377 tests, 0 failures/errors/skips, jar repackage pass; teardown scheduler/Testcontainers connection warnings remain non-fatal |
| AI-VERIFY-02 | Fresh Compose startup, Flyway V1–V4, Hibernate validation, health/log evidence | PASS — clean isolated `kbase-m11runtime` Compose with mail double, pinned `pgvector/pgvector:0.8.6-pg17-bookworm`, profile `runtime-test`; Flyway V1–V4 and Hibernate validation completed; `/v3/api-docs` 200 |
| AI-VERIFY-03 | Project Assistant upload/index/grounded/no-evidence HTTP journey | PASS — real HTTP: Markdown upload with declared `text/markdown` → `READY` → `GROUNDED` with 1 source; unrelated question → `NO_EVIDENCE` with 0 sources |
| AI-VERIFY-04 | Provider/Redis failure isolation plus Core health | PASS — isolated Docker HTTP proves chat-only 503 after successful retrieval with USER retained/ASSISTANT FAILED, embedding-only upload retry exhaustion (`FAILED:3:AI_PROVIDER_UNAVAILABLE`/visible `AI_SERVICE_ERROR`), Redis guard 503/Core 200 isolation and shared runtime rate limit; comprehensive retained-log audit across grounded/NO_EVIDENCE/Guide/chat-failure/embedding-retry/Redis-outage/rate-429/job-failure/purge scenarios: 518 log lines, **0 sensitive hits** (question/answer/context/Guide evidence/vector/hash/storageKey/job payload/lease/JWT/OTP/password/credential/provider raw response/Redis rate key) and only category-only error lines |
| AI-VERIFY-05 | Cross-project, private conversation, prompt-injection, and source-authz evidence | PASS — real Docker HTTP matrix 37/37: cross-project vector trap (B perfect evidence unreachable from A; same question grounded inside B), private conversation privacy (MEMBER/OWNER/ADMIN non-creator 404 ×5 ops; foreign/former member 403 ×5; creator full access; wrong-project 403), prompt-injection text treated as plain evidence with zero B references and no payload echo, deleted-source lifecycle (old answer retained, source UNAVAILABLE with null documentId, new retrieval NO_EVIDENCE, index status 404), source authorization (former member cannot download/read cited document or citing conversation), Guide isolation (grounded from the 2 packaged READY sources only; project-only and private-conversation sentinels NO_EVIDENCE while the same content is grounded inside the project) |
| AI-VERIFY-06 | Remove/rejoin and retention/purge evidence | PASS — real HTTP + isolated DB postconditions: immediate 403 after normal remove; `CONVERSATION_PURGE` job scheduled `run_at = membershipLoss + P7D` (delta 7.00000 days, payload `purgeAfter` matching) without retention changes; rejoin before deadline restores the SAME conversation ID/messages/ownership, cancels the purge job (CANCELLED) and counts the retained conversation toward the 5 quota (409 at limit); in-flight revoke→rejoin race on the bounded delayed deterministic chat: old request returns 403 `PROJECT_ACCESS_FORBIDDEN`, assistant FAILED, zero citations, replacement membership lets a new request succeed (observation: persisted `failure_code` for the transient revoke+rejoin case is `AI_PROVIDER_UNAVAILABLE`, see freeze report); actual purge executed by `AiJobScheduler`+`ConversationPurgeJobHandler` after making only the same legitimate job's `run_at` due (verification fixture) — target conversations/messages deleted, unrelated conversations/documents/users/Guide corpus preserved; rejoin after purge: membership works, history does not return, list empty |
| AI-VERIFY-07 | Guide approved/unsupported question evidence | PASS — authenticated real HTTP query against packaged corpus: documented permission question → `GROUNDED` with 1 source; unrelated question → `NO_EVIDENCE` with 0 sources |
| AI-VERIFY-08 | Restart/stale-job resume and persistence/ephemerality evidence | PASS — worker-paused override proves DOCUMENT_INDEX PENDING survives backend-only stop and is processed by the same durable job after restart (DONE on first claim, binary preserved, exactly one active chunk set, no stale sets); stale `PROCESSING` claim fixture (expired lease, old `locked_by`, attempts<max, due) is reclaimed by the new worker (attempt 2, new lease, DONE, document READY; stale lease-token transition probe affects 0 rows); backend restart preserves conversations/messages/jobs/indexes and MinIO MD5; Redis recreation resets only the ephemeral rate key and guarded requests work again; abrupt chat JVM death reproduced inside the bounded chat delay: ASSISTANT PROCESSING persists with null content, new send 409 `AI_REQUEST_IN_PROGRESS`, verified safe workaround — creator DELETE removes the stuck turn and frees quota/lock, and other/new conversations remain usable |
| AI-VERIFY-09 | Docs/code/Flyway/OpenAPI/generated snapshot consistency audit | PASS — config audit: AiProperties = application.yml = .env.example = docker-compose.yml = DEPLOYMENT.md on every expected default (`AI_ENABLED=false`, mode gemini, 768, 0.70, 10, 6, 5s/10/2m/30s/3, 7d, `kbase:ai:rate`, 20, 1m; the previously fixed retry-backoff Compose drift confirmed resolved; runtime-test overrides are intentional verification-only values); DB audit: Flyway V1–V4 success, 18 persistent tables, pgvector, `vector(768)` on both chunk tables, HNSW cosine indexes, Hibernate validate at startup, `docs/generated/db-schema.md` consistent without rewrite; OpenAPI audit: runtime `/v3/api-docs` = 38 paths / 57 operations / 15 tags matching the contract test and `docs/generated/api-schema.md`, 51 bearer operations, exact 429/503 error contract on the three interactive AI operations, `NO_EVIDENCE` documented as a successful outcome, no streaming endpoints |
| AI-VERIFY-10 | Completed freeze report and synchronized living docs | PASS — this plan is the freeze report; living docs (CURRENT_STATE, QUALITY_SCORE, RELIABILITY, SECURITY, TESTING, INTEGRATION, DEVELOPMENT, BACKEND, PLANS, tech-debt tracker) synchronized with verified evidence; plan moved to `docs/exec-plans/completed/` |

## M11 execution log

The blocker was repaired with a packaged deterministic port adapter that requires all of: `kbase.ai.enabled=true`, explicit `kbase.ai.provider.mode=deterministic`, Spring profile `runtime-test`, and an acknowledgement flag. Gemini remains the default provider mode; no credential or public network is used. The local `.env` PostgreSQL image was corrected from Core-only `postgres:17-alpine` to the approved pgvector image; the M11 Compose override pins that same image for reproducibility. A companion failure override induces only safe provider unavailability.

The generated snapshots were inspected without modification: `docs/generated/db-schema.md` records 18 persistent tables and `docs/generated/api-schema.md` records 38 paths / 57 operations / 15 tags; Flyway source remains V1–V4. M11 is no longer blocked by provider injection, but it is not complete: AI-VERIFY-04 remaining cases and AI-VERIFY-05/06/08/09 must be executed before any freeze claim or M12.

### Continuation attempt — 2026-09-26

- The verification-only deterministic provider now isolates `CHAT_UNAVAILABLE`/`CHAT_TIMEOUT` from `EMBEDDING_UNAVAILABLE`/`EMBEDDING_TIMEOUT`; legacy `UNAVAILABLE`/`TIMEOUT` still affect both ports. The bounded `kbase.ai.provider.deterministic.chat-delay` (0–30 seconds, default `0s`) delays only deterministic chat and is unavailable unless the existing AI-enabled + `runtime-test` + explicit-acknowledgement guards have already activated the provider.
- Focused configuration/provider verification passed: `mvn -B -ntp "-Dtest=AiGeminiProviderConfigurationTest,AiPropertiesBindingTest" test` → 14 tests, 0 failures, 0 errors, 0 skipped. It proves profile/acknowledgement rejection, network-free deterministic ports without Google model beans, operation-specific failures, Gemini default wiring, and the delay bound.
- Added minimal M11-only Compose companions for chat failure, embedding failure, delayed-chat race and worker-paused restart preparation. Base Compose now passes `KBASE_AI_WORKER_RETRY_BACKOFF` unchanged at its production default `30s`, plus the inert deterministic delay default. Base, mail-test and every new override combination passed `docker compose ... config --quiet`; `git diff --check` passed.
- Required fresh baseline `mvn -B -ntp clean verify` could not complete in this local session because the Docker Desktop Linux engine was unavailable (`npipe:////./pipe/dockerDesktopLinuxEngine` did not exist). Surefire ran 249 tests before 24 Testcontainers integration tests failed solely with `Previous attempts to find a Docker environment failed`; there were no assertion failures. This is an environment blocker, not a failing build or M11 product defect. No Docker runtime, database fixture, destructive cleanup, Gemini credential or public provider call was attempted.
- Continue only after Docker Engine is running: re-run the clean baseline first, then use an isolated Compose project for AI-VERIFY-04 remaining cases, AI-VERIFY-05/06/08 and the final AI-VERIFY-09 runtime audit. AI-VERIFY-04 remains PARTIAL; AI-VERIFY-05/06/08/10 remain NOT RUN and AI v1 remains NOT FROZEN.

### Runtime continuation — Docker restored 2026-09-26

- Docker Desktop Linux engine was restored without touching the existing `kbase` Compose project. Fresh `mvn -B -ntp clean verify` passed with **377 tests, 0 failures, 0 errors, 0 skipped** and Spring Boot jar repackage. Surefire emitted known post-Testcontainers-teardown connection-refused warnings after successful tests.
- Isolated project `kbase-m11fix` used alternate host ports, `pgvector/pgvector:0.8.6-pg17-bookworm`, MinIO, Redis, Mailpit and the explicit runtime-test deterministic provider. Authenticated HTTP fixture upload reached `READY` in two polls.
- `CHAT_UNAVAILABLE` returned `503 AI_PROVIDER_UNAVAILABLE` for Project Assistant only after its query embedding/retrieval path. Database postcondition: `USER:COMPLETED`, `ASSISTANT:FAILED:AI_PROVIDER_UNAVAILABLE`, and zero citation rows.
- `EMBEDDING_UNAVAILABLE` still accepted the Core Markdown upload (`201`), exhausted the configured three `DOCUMENT_INDEX` attempts, and ended `FAILED:3:AI_PROVIDER_UNAVAILABLE` / visible `FAILED + AI_SERVICE_ERROR`. Core metadata list and binary download both remained `200`.
- With only isolated Redis stopped, a guarded Project Assistant create returned `503 AI_USAGE_GUARD_UNAVAILABLE`; authenticated Core project list remained `200` and `ai_messages` count was unchanged. Redis was restarted; only its expected ephemeral rate state was reset.
- Runtime rate override `max=2` proved an accepted Guide request and an accepted Project Assistant request share one owner budget; the next Guide request returned `429 AI_RATE_LIMIT_EXCEEDED`; another authenticated user remained `GROUNDED`. A backend log scan for synthetic question/answer/provider/storage/lease/JWT/password sentinels returned **0** matches.
- These results materially advance AI-VERIFY-04; its comprehensive retained-log scan across every scenario is still required. AI-VERIFY-05/06/08 and the remaining AI-VERIFY-09 runtime audit are also required. Do not freeze AI v1.

### Security / retention / restart / audit completion and AI v1 freeze — 2026-09-26

Runtime environment: isolated Compose project `kbase-m11fix` (alternate host ports, pinned `pgvector/pgvector:0.8.6-pg17-bookworm`, MinIO, Redis, Mailpit, explicit `runtime-test` deterministic provider, usage max 100), recreated cleanly for the verification series; the developer `kbase` project was never touched. All HTTP evidence was produced through the real containerized backend; database evidence through the isolated PostgreSQL container. No real Gemini credential or network was used.

- **AI-VERIFY-05 (37/37 checks):** cross-project vector trap, private conversation privacy matrix (MEMBER/OWNER/system-ADMIN non-creator → 404 on all five conversation operations; foreign and former members → 403; creator keeps full access; conversation ID against a wrong project → 403), prompt injection as plain evidence, deleted-source lifecycle, source authorization after revoke, and Guide isolation all pass at runtime.
- **AI-VERIFY-06 (15/15 + 10/11 + 16/16):** immediate revoke, exact `+P7D` schedule (`run_at` − `created_at` = 7.00000 days; payload `purgeAfter` matches), rejoin-before-deadline restore + purge cancellation + quota inclusion, in-flight revoke→rejoin race (old request 403/FAILED/zero citations; new request under the replacement membership succeeds), real handler purge after making only the same legitimate job's `run_at` due, unrelated-data preservation, and no-resurrection after purge. Observation (not a contract violation; M7's regression test asserts only 403 + FAILED + zero sources): the transient revoke→rejoin turn persists `failure_code=AI_PROVIDER_UNAVAILABLE` instead of a revocation-specific code because `failTurn` only records `PROJECT_ACCESS_REVOKED` when the caller still lacks access at failure time.
- **AI-VERIFY-08 (6/6 + 9/9 + persistence + 7/7):** pending-job restart, stale-PROCESSING reclaim with lease-token guard, PostgreSQL/MinIO persistence across backend restart and Redis recreation ephemerality, and abrupt chat JVM death classified with the verified creator-DELETE workaround.
- **AI-VERIFY-04 completed:** the four failure scenarios were re-run fresh and a comprehensive retained-log audit (518 lines across grounded/NO_EVIDENCE/Guide/chat-failure/embedding-retry/Redis-outage/rate-429/job-failure/purge) found 0 sensitive hits; all failure logging is category-only.
- **AI-VERIFY-09 completed:** config, database and OpenAPI audits all match their verified source of truth; `docs/generated/db-schema.md` and `docs/generated/api-schema.md` required no rewrite.
- **Final gate:** `mvn -B -ntp clean verify` `BUILD SUCCESS`, 377 tests, 0 failures/errors/skips; base, mail-test and every M11 verification Compose combination pass `config --quiet`; `git diff --check` clean. No schema, API contract, generated-snapshot or production-default change was made in M11.
- **Debt classification:** abrupt ASSISTANT PROCESSING recovery MEDIUM (verified workaround, non-blocking); DOCUMENT_INDEX fixed lease/no heartbeat MEDIUM (correctness proven by lease-token guards; wasted duplicate computation possible, non-blocking); Gemini independent connect-timeout LOW (request timeout wired; SDK limitation recorded, non-blocking); transient revoke→rejoin `failure_code` precision LOW (observability only, non-blocking).
- **Freeze decision:** AI-VERIFY-01..10 all PASS with no unresolved HIGH/BLOCKER debt. **AI v1 backend is FROZEN as of 2026-09-26.** M11 moves to `docs/exec-plans/completed/`; no M12 is created.

## Freeze report (AI v1 backend, 2026-09-26)

| Area | Result | Evidence level |
|---|---|---|
| Build | 377/377 clean verify, jar repackage | full Maven gate |
| Runtime startup | isolated pgvector Compose, Flyway V1–V4, Hibernate validate, healthcheck-gated | real Docker runtime |
| Project Assistant golden journey | upload→READY→grounded→NO_EVIDENCE | real HTTP |
| Failure isolation | chat 503, embedding retry exhaustion, Redis guard 503, shared rate limit, Core unaffected | real HTTP + DB + logs |
| Sensitive-data log audit | 0 hits across 9 scenario types (518 lines) | retained-log scan |
| Security matrix | 37/37 (trap, privacy, injection, deleted source, source authz, Guide isolation) | real HTTP + DB postconditions |
| Retention matrix | +P7D schedule, rejoin restore, runtime purge, no resurrection, in-flight race | real HTTP + isolated DB fixtures |
| Restart/recovery | pending job resume, stale reclaim, persistence, Redis ephemerality, abrupt chat death classified | real Docker restarts + DB postconditions |
| Consistency audit | config/DB/OpenAPI/generated snapshots aligned | runtime `/v3/api-docs` + live catalog + source inspection |
| Known limitations | no real Gemini smoke; fixed worker lease/no heartbeat; abrupt PROCESSING recovery debt (MEDIUM, workaround verified); connect-timeout SDK limitation; transient revoke→rejoin failure-code precision | tracked in tech-debt tracker |

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

*(Notes from the handoff that created this plan, kept as chronological evidence; M11 has since been completed and AI v1 frozen 2026-09-26.)*

- M10's Redis guard, OpenAPI, observability, and leakage audit are implementation evidence, not a substitute for this runtime gate.
- The M10 plan is archived beside the completed M0–M9 AI plans; at handoff time this file was the only active AI slice (now completed — no active AI v1 milestone remains).
- No M11 implementation had been performed in the handoff that created this plan.
