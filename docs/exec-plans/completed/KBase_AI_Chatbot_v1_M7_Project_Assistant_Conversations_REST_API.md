# KBase AI Chatbot v1 – M7 Project Assistant Conversations & REST API

**Status:** DONE – M7 Gate PASS, handoff to M8
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Depends on:** Completed AI M0–M6; Core v1 remains frozen
**Scope:** Private non-streaming Project Assistant conversation and message lifecycle plus the SD-17 REST contract, using the completed M6 internal strict RAG boundary.

## Goal

Expose authorized, creator-private Project Assistant conversations with atomic max-five creation, one active generation, durable USER/ASSISTANT message transitions, strict M6 grounded/NO_EVIDENCE outcomes, current-access rechecks and structured source reads. No M7 behavior is implemented by this handoff.

## Source of truth and bootstrap

Before coding, re-read `AGENTS.md`, `ARCHITECTURE.md`, `.harness/source-doc-registry.json`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`, SD-14 product spec, SD-15 RAG architecture, SD-16 persistence design, SD-17 REST API specification, SD-18 testing strategy, SD-19 master plan and completed M0–M6 plans. Inspect current code and run the baseline `mvn -B -ntp clean verify` plus Compose config. Resolve any conflict in this plan before changing behavior.

## Approved tasks from master plan

- **AI-CHAT-01 – Conversation authorization/service:** creator-only privacy plus current project access; ADMIN project override must not bypass creator ownership.
- **AI-CHAT-02 – Atomic max-five create:** create with first message only, using the existing locked quota boundary and concurrent integration evidence.
- **AI-CHAT-03 – Conversation list/get/rename/delete:** own only, `updatedAt DESC`, title max 100, hard delete lifecycle.
- **AI-CHAT-04 – Send message lifecycle:** persist USER and ASSISTANT PROCESSING before M6 RAG call; one active generation; finalize COMPLETED or FAILED without holding a transaction across provider calls.
- **AI-CHAT-05 – Authorization recheck:** no completed answer/source persistence or return after in-flight access loss; preserve the M6 pre-chat and post-chat checks.
- **AI-CHAT-06 – Message/source read API:** deterministic pagination and historical snapshots with unavailable live sources; current document authorization on source open.
- **AI-CHAT-07 – Controller/DTO/error contract:** implement only approved SD-17 Project Assistant endpoints and stable errors; regenerate `docs/generated/api-schema.md` when the contract changes.

## Boundaries

- Reuse `ProjectRagService`, `GroundedResult`, `CitationSnapshotMapper` and existing persistence entities/repositories; M6 evidence/history/label rules remain authoritative.
- M7 may create/persist conversations and messages but must not use history as evidence, leak private conversations across creators, return generated content after membership loss, or expose storage keys/permanent URLs.
- Guide runtime, retention purge handler, frontend, streaming, OCR/multimodal/XLSX RAG, rate limiter and unrelated Core changes remain outside this slice unless source documents explicitly approve them.
- No schema migration by default; if a proven schema defect blocks M7, document it and resolve separately before changing Flyway.

## Required verification and completion rule

Run targeted unit/API/security/concurrency/PostgreSQL tests for privacy matrix, max-five under contention, one active generation, no-evidence 200 domain outcome, provider failure preserving USER message, revoke during generation, source availability/authorization and SD-17 contract. Then run `mvn -B -ntp clean verify`, `docker compose -f docker-compose.yml config --quiet`, `git diff --check` and a scope/privacy audit. Record actual evidence in this plan and living docs before marking M7 complete. Do not infer completion from M6's 329/329 baseline alone.

## Handoff state

M6 Gate PASS on `feat-AI`: targeted 33/33, full 329/329, real pgvector cross-project trap and citation deletion/revocation tests. M7 implementation began 2026-09-24 after the 329/329 baseline and source/ownership reconciliation below.

## M7 decision log (2026-09-24, before code changes)

- Endpoint ownership: M7 implements SD-17 API-AI-001..009. API-AI-008/009 expose the existing M5 document-index application boundary; no later milestone owns them. API-AI-010 Guide belongs to M9. M10 owns the usage/rate guard, so M7 runtime and OpenAPI will not claim `AI_RATE_LIMIT_EXCEEDED`/429.
- Title: strip leading/trailing Unicode whitespace from the first valid USER question, then take at most 100 Unicode code points without splitting a surrogate pair. Internal whitespace is preserved. No provider, clock or random input participates.
- Conversation GET returns bounded metadata only: `id`, `projectId`, `title`, `createdAt`, `updatedAt`. List returns `PageResponse` of the same DTO ordered by `updatedAt DESC, id DESC`; messages have a separate paginated route ordered `createdAt ASC, id ASC`.
- Create returns 201 with `{conversation, message, sources}`; send returns 200 with `{message, sources}`. `message` has `id`, `role`, nullable `content`, nullable `answerType`, `generationStatus`, nullable safe `failureCode`, `createdAt`, nullable `completedAt`. Message list uses `PageResponse` of the send shape. Public source fields are one-based `order`, nullable live `documentId`, snapshot `documentName`, page/slide/section and `AVAILABLE`/`UNAVAILABLE`; no score, chunk ID, storage key or permanent URL. Source persistence remains zero-based.
- Service split: a nontransactional orchestration service calls short transactional persistence methods for initial create/send and finalization. The existing user-row `FOR UPDATE` lock plus count enforce max five. The V4 partial unique index is the final one-active-generation guard; USER and PROCESSING inserts share one transaction and flush before commit. RAG runs after this transaction has committed. The final transaction rechecks current project access and scoped creator/conversation/assistant ownership before completing the existing marker and persisting citations atomically.
- History is read from prior persisted textual USER and completed ASSISTANT messages only, excluding the new question, PROCESSING markers and FAILED assistant messages; M6 `ConversationContextPolicy` applies the final eight-turn/4,000-character bound. Failure or authorization loss marks an existing PROCESSING marker FAILED with a stable safe code and no answer/source content, when the marker still exists. Provider categories map to public `AI_PROVIDER_UNAVAILABLE`/503; authorization takes precedence over a provider failure.
- OpenAPI tags: `AI - Project Assistant` and `AI - Document Indexing`. The exact runtime path/method set and generated API snapshot must be verified after implementation. No V5 migration is planned.

## Baseline verification (2026-09-24)

- Initial branch: `feat-AI`; initial `git status --short` empty.
- Completed M0–M6 plans and code agree on PASS, V1–V4, 18 persistent tables, M6 internal RAG, and no public AI endpoints.
- `mvn -B -ntp clean verify`: BUILD SUCCESS, 329 tests, 0 failures, 0 errors, 0 skipped. Existing test-container shutdown warnings occurred after tests; no baseline failure.
- `docker compose -f docker-compose.yml config --quiet`: exit 0.

## Progress and verification log (2026-09-24)

| Task | State | Evidence |
|---|---|---|
| AI-CHAT-01 authorization/privacy | IMPLEMENTED | Creator-scoped repository lookup after current project access; real JWT MEMBER/OWNER/ADMIN matrix, ADMIN own conversation and revoked membership tests pass. |
| AI-CHAT-02 atomic max five | IMPLEMENTED | Existing durable User row lock + count inside TX1; real PostgreSQL concurrent 4→5 test gives exactly one fifth and one 409, no sixth; delete frees quota, project/user independence tests pass. |
| AI-CHAT-03 CRUD | IMPLEMENTED | Create on first message, Unicode-safe deterministic title, DB pageable list `updatedAt DESC,id DESC`, metadata-only GET, rename/update timestamp, hard delete/cascade; route tests pass. |
| AI-CHAT-04 generation lifecycle | IMPLEMENTED | USER and PROCESSING committed before fake provider; test verifies no provider transaction and marker visible. V4 unique guard plus app precheck; same-conversation second send 409/no USER row, distinct conversations concurrent. GROUNDED+citation and NO_EVIDENCE persistence tests pass; create/send provider failure leaves safe FAILED marker. |
| AI-CHAT-05 final recheck | IMPLEMENTED | M6 pre/post checks retained; M7 final transaction checks project/creator/marker. Blocked fake tests revoke during model and in seam after M6 return: no completed content/sources, safe FAILED marker; delete during model cannot resurrect conversation. |
| AI-CHAT-06 messages/sources | IMPLEMENTED | DB pagination fixed `createdAt ASC,id ASC`, page and source mapping tests pass; one-based public order; source deletion yields snapshot UNAVAILABLE/null live ID; no score/hash/vector/storage key in DTO/OpenAPI. |
| AI-CHAT-07 REST and API-AI-008/009 | IMPLEMENTED | Nine operations use Bearer, DTOs and stable AI codes; M5 status/retry boundary reused, 202 async retry, uploader/OWNER/ADMIN and non-uploader tests pass; runtime OpenAPI exact 37 paths/56 operations, 14 tags and request/response schemas pass. |

Targeted command `mvn -B -ntp "-Dtest=ProjectAssistantM7IntegrationTest,ProjectAssistantTitleTest,OpenApiContractIntegrationTest" test`: 36/36 PASS. Including the adjusted M5 index-service regression test: 38/38 PASS. The first full `mvn -B -ntp clean verify` ran all tests but failed one stale M5 assertion that required the old exception message `AI_INDEX_RETRY_NOT_ALLOWED`; M7 correctly maps it through `BusinessException` to a safe message and stable `ErrorCode`/409. The assertion was updated to verify `ErrorCode.AI_INDEX_RETRY_NOT_ALLOWED`, then targeted 38/38 and the full gate rerun passed.

## Final M7 gate and handoff

- Full rerun `mvn -B -ntp clean verify`: BUILD SUCCESS, 346 tests, 0 failures, 0 errors, 0 skipped; Java compile/package/repackage pass. The earlier failed run is documented above and no longer reproduces.
- `docker compose -f docker-compose.yml config --quiet`: PASS. `git diff --check`: PASS. Runtime OpenAPI contract: exact 37 paths / 56 operations, 14 canonical tags, nine M7 operations with Bearer/request/response/error schemas; API-AI-010 absent.
- Static scope/privacy scan: no Guide route/handler, purge handler, rate limiter, streaming/frontend, real Gemini test dependency, public vector/hash/score/storage key or new migration. V1–V4 and `docs/generated/db-schema.md` unchanged. `docs/generated/api-schema.md` synchronized manually with verified runtime.
- Known limitation: abrupt JVM death between TX1 and finalization can leave a PROCESSING assistant; M7 has no durable chat-generation job or startup recovery. Ordinary provider/application failures are finalized FAILED. This is recorded in `docs/exec-plans/tech-debt-tracker.md` for approved hardening before production AI rollout.
- AI-CHAT-01..07 and API-AI-008/009: DONE. **M7 Gate: PASS.** Next active slice is M8 membership retention/deletion/security races; no M8 implementation is part of this change.
