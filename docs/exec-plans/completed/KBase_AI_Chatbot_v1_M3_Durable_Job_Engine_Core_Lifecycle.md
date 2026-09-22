# KBase AI Chatbot v1 – M3 Durable Job Engine & Core Lifecycle Hooks

**Status:** COMPLETE  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1 and M2 AI slices  
**Scope:** PostgreSQL-backed job claim/lease/retry, bounded scheduler boundary, document AI intent, delete safety, and membership retention hooks.  
**Current step:** Handoff to M4 – Gemini Provider Adapters

## 1. Source of truth and dependency gate

Required sources were reloaded before implementation:

- repository routing and policy: `AGENTS.md`, `ARCHITECTURE.md`, `.harness/source-doc-registry.json`;
- state/quality/process: `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`;
- backend/runtime rules: `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`;
- AI authority: SD-14, SD-15, SD-16, SD-18 and SD-19;
- completed dependency plans: M0, M1 and M2.

Dependency gate: M0 PASS, M1 PASS, M2 PASS. M2 evidence is the verified V4 schema, 18 persistent tables, pgvector/HNSW/JPA/JDBC boundaries, targeted 36/36 and final 241/241 suite. V4 and Core V1–V3 are immutable for this slice.

## 2. Approved M3 tasks

### AI-JOB-01 – Durable claim / lease / retry

- Claim only allowed/executable types and only due `PENDING`/`RETRY` or stale `PROCESSING` rows.
- Use PostgreSQL `FOR UPDATE SKIP LOCKED` in a short transaction; increment attempts on successful claim.
- Store a unique worker identity plus per-claim lease token in `locked_by`.
- Complete/retry/fail only with `id + PROCESSING + current locked_by` predicates.
- Mark stale processing rows with exhausted attempts `FAILED`; never retry indefinitely.
- Implement concurrency-safe active dedup through a PostgreSQL transaction advisory lock keyed by `dedup_key`, followed by active-state lookup/insert. V4 remains unchanged; the proof and all enqueue callers are recorded below.

### AI-JOB-02 – Bounded worker scheduler boundary

- Poll durable PostgreSQL state with the configured poll interval, batch size, lease timeout, retry backoff and max attempts.
- Registry-supported job types define what production can claim; M3 registers no production execution handlers for later DOCUMENT_INDEX/CONVERSATION_PURGE/GUIDE_REINDEX behavior.
- Keep handler execution outside the claim transaction and keep AI disabled-by-default startup healthy.
- Provide reusable provider-neutral SUCCESS/RETRY/FAILURE outcomes without Gemini taxonomy or network calls.

### AI-JOB-03 – Document indexing intent / upload lifecycle

- After Core document persistence and tags, create one AI index row in the same transaction.
- Supported `pdf`, `doc`, `docx`, `ppt`, `pptx`, `md`, `txt`: `PENDING` plus one active `DOCUMENT_INDEX` intent.
- Other Core-accepted types: `UNSUPPORTED` and no indexing job.
- Use M0 snapshots (chunking `chunk-v1`, embedding model and 768 dimensions) and deterministic document dedup keys.
- Preserve upload response, StorageService boundary, compensation and batch rollback semantics; no provider/binary read.

### AI-JOB-04 – Document/project delete safety

- Rely on verified V4 document/project FK cascades for AI index/chunk/job cleanup and preserve Core storage-first deletion.
- Verify pending and claimed document jobs cannot resurrect deleted documents through conditional stale-owner transitions.
- Verify project deletion removes project AI state/jobs and invalidates late project-scoped worker transitions.

### AI-JOB-05 – Membership retention hooks

- On member removal or leave, schedule one active `CONVERSATION_PURGE` job at membership-loss time plus `P7D` in the same PostgreSQL transaction.
- Use `conversation-purge:{projectId}:{userId}` active dedup semantics.
- On invitation accept/rejoin, cancel or neutralize pending/active purge intent in the same transaction.
- Allow a later loss after rejoin to schedule a new future purge; do not implement destructive conversation purge in M3.
- Project deletion continues to use database cascade for project jobs/data.

## 3. Explicitly out of scope

- Gemini/Spring AI provider adapters, extraction, chunking, embedding execution, retrieval, grounding, citations or Guide indexing;
- Project Assistant/Guide REST endpoints, conversation runtime, manual retry API, rate guard or OpenAPI changes;
- actual conversation deletion/purge handler;
- frontend, streaming, OCR, multimodal, spreadsheet RAG, message brokers or deployment changes;
- edits to V1–V4 or generated API schema.

## 4. Design decisions / migration guard

- V4 `ai_jobs` is the schema source of truth; no migration is expected.
- Active dedup is concurrency-safe only through the KBase-owned enqueue service. It acquires `pg_advisory_xact_lock(hashtextextended(dedup_key, 0))`, checks `PENDING/PROCESSING/RETRY`, and inserts only when no active row exists. The advisory lock is collision-safe (a collision serializes unrelated keys but cannot create duplicates) and is held until the surrounding PostgreSQL transaction commits. Real PostgreSQL concurrent-insert evidence is required.
- Claim transitions use JDBC/native SQL in a short `TransactionTemplate` boundary. Handlers run after that template returns. `locked_by` is never treated as a reusable process name.
- Core lifecycle services remain authorities. AI hooks are optional feature-local collaborators and never call StorageService/MinIO/provider code.

## 5. Verification path

Run targeted tests after each task, using real PostgreSQL Testcontainers for claim, dedup, lease, delete and retention persistence/races. Required final commands:

- `mvn -B -ntp clean verify`;
- `docker compose -f docker-compose.yml config --quiet`;
- `git diff --check`;
- static scope review for V4 immutability, no provider/network/extraction/RAG/API/purge implementation, no MinIO SDK import in AI code and no raw content/secret in job state.

## 6. Progress log

| Date | Task | Status | Evidence / next action |
|---|---|---|---|
| 2026-09-22 | Dependency gate | DONE | M0/M1/M2 completed plans and repository evidence reloaded; M2 final 241/241. |
| 2026-09-22 | Plan synchronization | DONE | Active plan now contains AI-JOB-01 through AI-JOB-05 and corresponding gates. |
| 2026-09-22 | AI-JOB-01 | DONE | `AiJobEngineIntegrationTest` 9/9 PASS on PostgreSQL 17.11/pgvector; claim exclusivity, `SKIP LOCKED` progress, due/retry timing, stale reclaim/token protection, exhausted attempts, terminal filtering, bounded batch and advisory-lock active dedup verified. |
| 2026-09-22 | AI-JOB-02 | DONE | `AiJobSchedulerTest` 5/5 and scheduler cases in `AiJobEngineIntegrationTest` PASS; supported-handler filtering, no-handler non-consumption, SUCCESS/RETRY/FAILURE/exception mapping, handler execution after claim transaction and disabled-AI context verified. |
| 2026-09-22 | AI-JOB-03 | DONE | `DocumentAiIntentIntegrationTest` 6/6 and `DocumentAiRollbackIntegrationTest` 2/2 PASS; supported/unsupported upload intent, deterministic metadata, active dedup, single/batch compensation and Core response preservation verified. |
| 2026-09-22 | AI-JOB-04 | DONE | `DocumentAiRollbackIntegrationTest` 2/2 PASS; document/project FK cleanup and pending/claimed late-transition no-resurrection behavior verified. |
| 2026-09-22 | AI-JOB-05 | DONE | `AiConversationRetentionIntegrationTest` 4/4 and `AiRetentionTransactionIntegrationTest` 1/1 PASS; remove/leave `+P7D`, same-transaction persistence, rejoin cancellation, loss-after-rejoin and project cleanup verified. |
| 2026-09-22 | Final M3 gate | DONE | `mvn -B -ntp test` completed with `BUILD SUCCESS`, 268/268 tests, 0 failures/errors/skips; final `clean verify`, Compose config, diff check and static scope review recorded below. |

## 7. Task evidence log

Each task must record status, files, transaction semantics, targeted commands/results, decisions, blockers and deviations before the next task begins. Do not mark a task DONE from static inspection alone.

### AI-JOB-01

- Status: DONE
- Files: `src/main/java/com/kbase/ai/job/`, `src/main/java/com/kbase/ai/repository/AiJobClaimRepository.java`, `src/main/java/com/kbase/ai/service/AiJobStore.java`, `src/main/java/com/kbase/ai/config/AiWorkerConfiguration.java`.
- Transaction semantics: claim, stale exhaustion, enqueue/advisory lock and conditional transitions use short `TransactionTemplate`/JDBC transactions; handlers are not called by the store.
- Verification: `AiJobEngineIntegrationTest` 9/9 PASS on real PostgreSQL; V4 unchanged.
- Decision: active dedup is proven without V5 by transaction-scoped `hashtextextended(dedup_key, 0)` advisory serialization.

### AI-JOB-02

- Status: DONE
- Files: `src/main/java/com/kbase/ai/job/`, `src/main/java/com/kbase/ai/service/AiJobHandlerRegistry.java`, `src/main/java/com/kbase/ai/service/AiJobScheduler.java`, `src/main/java/com/kbase/ai/config/AiWorkerConfiguration.java`.
- Transaction semantics: scheduler claims through `AiJobStore`'s short `TransactionTemplate`; handlers and conditional outcome transitions run after claim transaction closes. Production claims are limited to registry-supported types, and the empty M3 registry consumes no later-milestone job.
- Verification: `AiJobSchedulerTest` 5/5 plus full PostgreSQL integration suite; SUCCESS/RETRY/FAILURE/exception mapping, bounded batch, no-handler preservation, outside-transaction execution and disabled-AI startup pass.

### AI-JOB-03

- Status: DONE
- Files: `src/main/java/com/kbase/ai/service/DocumentAiIntentService.java`, `src/main/java/com/kbase/document/service/DocumentService.java`, `src/main/java/com/kbase/ai/service/AiDocumentSupportPolicy.java`.
- Transaction semantics: the Core upload transaction persists document, tags, `DocumentAiIndex` intent and active job together; storage compensation remains in `DocumentService` and no AI binary/provider call is made.
- Verification: `DocumentAiIntentIntegrationTest` 6/6 and `DocumentAiRollbackIntegrationTest` 2/2; supported extensions become `PENDING` with one `DOCUMENT_INDEX`, unsupported Core files become `UNSUPPORTED` with no job, repeated intent is idempotent, and single/batch rollback leaves no AI orphan.

### AI-JOB-04

- Status: DONE
- Files: `src/main/java/com/kbase/document/service/DocumentService.java`, `src/main/java/com/kbase/ai/repository/AiJobClaimRepository.java`, V4 FK rules (unchanged).
- Transaction semantics: Core storage-first deletion remains authoritative; V4 cascades remove document/project AI rows, while lease-token predicates reject late worker transitions after ownership/deletion changes.
- Verification: `DocumentAiRollbackIntegrationTest` 2/2 on real PostgreSQL; pending and claimed document/project delete races do not resurrect AI state.

### AI-JOB-05

- Status: DONE
- Files: `src/main/java/com/kbase/ai/service/AiConversationRetentionService.java`, `src/main/java/com/kbase/project/service/ProjectMemberService.java`, `src/main/java/com/kbase/invitation/service/InvitationService.java`.
- Transaction semantics: membership delete plus `CONVERSATION_PURGE` enqueue, and invitation accept plus active purge cancellation, participate in the surrounding PostgreSQL transaction.
- Verification: `AiConversationRetentionIntegrationTest` 4/4 and `AiRetentionTransactionIntegrationTest` 1/1; `+P7D`, active dedup, rejoin cancellation/no-op, later-loss rescheduling and project cascade pass. Destructive purge remains out of scope.

## 8. Gate checklist

The M3 gate is PASS. Every item has executable evidence:

- [x] AI-JOB-01 PostgreSQL `SKIP LOCKED`, two-worker exclusivity, independent progress, due filtering, retry timing, lease expiry, unique lease ownership, stale-owner rejection, bounded attempts, terminal-state exclusion and active dedup;
- [x] AI-JOB-02 bounded durable scheduler, no unhandled-job consumption, execution outside claim transaction, disabled-AI health and no provider credential/network;
- [x] AI-JOB-03 supported/unsupported intent, deterministic metadata, one active job, duplicate idempotency, single/batch rollback and unchanged Core response;
- [x] AI-JOB-04 document/project cascades, pending/claimed delete races and no resurrection;
- [x] AI-JOB-05 remove/leave +P7D, same-transaction persistence, rejoin cancellation/no-op, loss-after-rejoin and project-delete cleanup;
- [x] Core V1–V4 integrity, full clean verify, Compose config and diff check;
- [x] no M4+ provider/extraction/RAG/API/purge behavior leaked.

### Final evidence

- Targeted M3 suites: `AiJobEngineIntegrationTest` 9/9, `AiJobSchedulerTest` 5/5, `DocumentAiIntentIntegrationTest` 6/6, `DocumentAiRollbackIntegrationTest` 2/2, `AiConversationRetentionIntegrationTest` 4/4 and `AiRetentionTransactionIntegrationTest` 1/1.
- Full regression: `mvn -B -ntp test` — `BUILD SUCCESS`, 268/268, 0 failures, 0 errors, 0 skipped.
- Final gates: `mvn -B -ntp clean verify` — `BUILD SUCCESS`, 268/268; `docker compose -f docker-compose.yml config --quiet` — PASS; `git diff --check` — PASS.
- Scope audit: no migration/generated DB/API diff; no provider/network/extraction execution, public AI controller, destructive purge handler or MinIO SDK import in `com.kbase.ai`; job payload/error state remains metadata/category-only.
- Shutdown-only Hikari/Testcontainers connection warnings appeared after the successful full-suite fork exit; Surefire still reported `BUILD SUCCESS` and 268/268 with no test failures/errors/skips.

Generated DB/API docs remain unchanged unless a separately justified forward migration is proven necessary and approved; any such deviation must stop implementation and update this plan first.
