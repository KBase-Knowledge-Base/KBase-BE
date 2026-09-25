# KBase AI Chatbot v1 – M10 Usage Guard / OpenAPI / Observability / Hardening

**Status:** DONE – M10 implementation and verification complete; M11 handoff ready
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed AI M0–M9; Core v1 remains frozen

## Goal

Finish the operational and public-contract hardening required before the AI v1 runtime verification/freeze milestone, without changing the established Core, Project Assistant, or KBase Guide product boundaries.

## In scope

- AI-HARD-01: approved configurable per-user usage/rate guard, preferably through the M0-selected Redis namespace, while preserving Core availability when AI rate state is unavailable;
- AI-HARD-02: safe operational metrics/logs for job depth, state, latency, provider error category and no-evidence results, with no raw content or secret material;
- AI-HARD-03: verified OpenAPI annotations and exact contract tests for implemented AI outcomes/errors;
- AI-HARD-04: synchronize `docs/generated/api-schema.md` from verified runtime OpenAPI;
- AI-HARD-05: DTO/OpenAPI/log leakage audit for vectors, hashes, job payloads/leases, provider material, storage keys and raw project content;
- AI-HARD-06: synchronize affected living documentation and execution evidence.

## Explicit exclusions

Do not implement frontend, streaming/SSE, persistent Guide conversations, Guide source management, project-corpus fallback for Guide, OCR/multimodal/XLSX RAG, a broker, schema changes unless an approved source explicitly requires one, or real Gemini automated network tests. Preserve M9's reviewed two-source Guide allowlist and stateless API-AI-010 boundary.

## Required source material

- `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, and this active plan;
- SD-14 through SD-19, especially the AI REST API and Testing Strategy;
- `docs/DEVELOPMENT.md`, `docs/BACKEND.md`, `docs/API_CONVENTIONS.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, and `docs/DEPLOYMENT.md`;
- completed M8/M9 plans and the master plan M10 section.

## Baseline and verification path

Before implementation, confirm the `feat-AI` branch and record pre-existing changes. The M9 baseline was re-run before implementation; the final gate includes clean verify, runtime OpenAPI contract verification, generated snapshot synchronization, security/log scan, `docker compose -f docker-compose.yml config --quiet`, and `git diff --check`.

## Risks and decisions to lock before code

- Lock the per-user guard algorithm, Redis key/TTL semantics, fail-open/fail-closed behavior and exact 429 error contract from SD-17/M0 before implementation.
- Ensure observability reports only bounded, non-sensitive categories and never changes AI data/corpus authorization boundaries.
- Any public contract alteration must update OpenAPI tests and `docs/generated/api-schema.md` in the same slice.
- A schema migration or a change to M9 Guide corpus/retrieval is out of scope and requires an explicit approved design decision.

## M10 technical decisions locked before implementation (2026-09-25)

| Concern | Locked decision |
|---|---|
| Usage algorithm | Fixed window with an injected `Clock`; `bucket = floor(epochMillis / windowMillis)`. Redis owns the counter; no JVM or PostgreSQL rate state. |
| Default policy | 20 accepted interactive AI requests per authenticated user per 1 minute. This is an operational v1 tuning default, not an immutable product limit. |
| Configuration | `kbase.ai.usage-rate-namespace`, `kbase.ai.usage-max-requests`, and `kbase.ai.usage-window`; all are typed and validated (`namespace` nonblank, max/window positive). |
| Redis key | `{namespace}:{userId}:{bucket}`, with the user ID only as identity; project, conversation, Guide, email, content and counters are not part of the identity. |
| Atomicity/TTL | One Redis Lua script performs `INCR`; only the first increment sets `PEXPIRE(windowMillis)`. Later requests never extend the TTL. |
| Guarded operations | `POST /api/v1/projects/{projectId}/ai/conversations`, `POST /api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages`, and `POST /api/v1/ai/guide/query`. No global filter and no asynchronous document-index/retry guard. |
| Ordering | Capability availability first; current project access and creator ownership preflight before charging; guard before conversation persistence/provider work. Guide availability precedes the guard. Persistence methods retain their authorization re-checks. |
| Redis failure | Fail closed for guarded AI requests only with `AI_USAGE_GUARD_UNAVAILABLE` / HTTP 503. Core endpoints and durable PostgreSQL AI state do not depend on this counter. Raw Redis details are never returned or logged. |
| Limit response | A counter above the configured maximum raises `AI_RATE_LIMIT_EXCEEDED` / HTTP 429. Accepted `NO_EVIDENCE` and provider failures consume one unit and are not refunded. |
| Retrieval threshold | M10 selects `0.70` as the production default after deterministic fixture evaluation; the property remains configurable. An explicit nullable override remains fail-closed for Guide and applies no numeric Project filter, preserving the existing null-threshold test behavior. No live-Gemini calibration is claimed. |
| Observability | KBase-owned Micrometer facade backed by the available `MeterRegistry` or `SimpleMeterRegistry`; no Actuator/public metrics endpoint. Only bounded operation/outcome/provider/job-state tags are allowed. |
| Metrics/signals | `kbase.ai.requests`, request latency, rate allowed/rejected/unavailable, provider latency/errors, retrieval candidate distribution, `NO_EVIDENCE`, job execution latency/outcome, job depth by state, and failed/stale job gauges. |
| Safe logging | Existing category-only job/provider/error logging is preserved and extended only with bounded categories/request IDs where already supported; never log prompts, chunks, answers, vectors, hashes, payloads, lease/worker IDs, Redis keys/counts or credentials. |
| Public contract | Guarded interactive operations document 429 `AI_RATE_LIMIT_EXCEEDED` and 503 `AI_USAGE_GUARD_UNAVAILABLE` alongside existing provider/auth errors. Runtime OpenAPI must remain 38 paths / 57 operations / 15 tags; generated API snapshot follows runtime verification. |

## Progress log

| Item | Status | Evidence |
|---|---|---|
| Preflight | DONE | Branch remained `feat-AI`; pre-existing worktree changes were recorded and preserved. M9 baseline evidence was 356/356. |
| AI-HARD-01 | DONE | `AiUsageGuard`/`RedisAiUsageGuard`: fixed-window per-user Redis Lua `INCR` + first-request `PEXPIRE`, shared Project Assistant/Guide budget, 429 limit response, guarded-only 503 fail-closed response, configuration validation, concurrency/isolation/rollover/failure tests. |
| AI-HARD-02 | DONE | `AiObservability` Micrometer facade with bounded operation/outcome/provider/job tags, request/provider/job latency, retrieval/no-evidence, rate outcomes, depth and stale/failed gauges; telemetry is best effort and no public metrics endpoint was added. |
| AI-HARD-03 | DONE | Project Assistant and Guide annotations document stable 429/503 responses; `OpenApiContractIntegrationTest` and disabled-docs regression pass at 38 paths / 57 operations / 15 tags. |
| AI-HARD-04 | DONE | `docs/generated/api-schema.md` synchronized to the verified M10 runtime contract; `docs/generated/db-schema.md` unchanged because no migration/schema change occurred. |
| AI-HARD-05 | DONE | DTO/OpenAPI/source/log review covers prompts, chunks, answers, vectors, hashes, storage keys, job payload/lease/worker data, Redis state, provider material and credentials; no sensitive leakage finding remains in the M10 scope. |
| AI-HARD-06 | DONE | CURRENT_STATE, QUALITY_SCORE, BACKEND, API_CONVENTIONS, INTEGRATION, TESTING, SECURITY, RELIABILITY and DEPLOYMENT synchronized; M11 handoff plan created without implementing M11. |

## Implementation and verification record

### Delivered behavior

- Default policy is 20 accepted interactive AI requests per authenticated user per one-minute fixed window, keyed only by user ID and bucket under `kbase:ai:rate`.
- The guard is charged only after capability and project/creator authorization checks. It protects Project Assistant create/send and Guide query; reads, document-index retry and background jobs remain uncharged.
- Redis failures become `AI_USAGE_GUARD_UNAVAILABLE` / HTTP 503 for guarded AI requests only. `AI_RATE_LIMIT_EXCEEDED` is reserved for an actual exceeded counter / HTTP 429. Accepted `NO_EVIDENCE` and provider failures consume one unit.
- Deterministic evaluation selected retrieval similarity default `0.70`. A deliberately blank nullable override remains fail-closed for Guide tests and does not add a numeric Project-RAG filter.
- No real Gemini credential or public Gemini network was used. No Flyway migration, persistent rate state, broker, frontend, streaming, Guide corpus, or new product behavior was added.

### Final evidence (2026-09-25)

| Command / check | Result |
|---|---|
| `mvn -B -ntp clean verify` | `BUILD SUCCESS`; 371 tests, 0 failures, 0 errors, 0 skipped. Testcontainers shutdown/placeholder PostgreSQL scheduler warnings were non-fatal; Surefire completed successfully. |
| `mvn -B -ntp "-Dtest=RedisAiUsageGuardIntegrationTest" test` | 5/5 pass on Redis 7.4 Testcontainer, including atomic limit, user isolation/shared budget, window rollover and unavailable mapping. |
| `mvn -B -ntp "-Dtest=AiObservabilityTest,GuideControllerTest,ProjectAssistantM7IntegrationTest,OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` | 46/46 pass: AiObservability 3/3, Guide controller 1/1, Project Assistant 20/20, OpenAPI contract 20/20 and disabled-docs 2/2. |
| `docker compose -f docker-compose.yml config --quiet` | Pass. |
| `git diff --check` | Pass. |
| Sensitive-data and scope scans | Pass; no source/log path emits raw AI content, vector/hash/storage/job/Redis/provider/credential material. |

### Known limits handed to M11

- Full provider-backed Docker AI golden journeys, restart/recovery with pending/stale AI work, and production Gemini connectivity remain M11 scope and are intentionally not claimed here.
- The existing document-index fixed-lease/no-heartbeat limitation and abrupt chat-generation recovery debt remain tracked in `docs/exec-plans/tech-debt-tracker.md`.

## Completion conditions

M10 is complete: the rate guard is executable and safe, OpenAPI contract tests pass, the generated API snapshot is current, sensitive-field/log scans are clean, the full clean verification succeeds, and CURRENT_STATE/quality/execution evidence are synchronized. This plan is ready to move to `../completed/`; M11 is the next active slice.
