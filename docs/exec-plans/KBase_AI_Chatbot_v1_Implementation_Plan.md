# KBase – AI Chatbot v1
## Implementation Plan for Agent / Harness Execution

**Version:** Draft 1  
**Plan Type:** Execution Plan  
**Primary Use:** Coding Agent / Harness Orchestration  
**Scope:** KBase AI Chatbot v1 Backend on frozen Core v1  
**Backend:** Java 21 + Spring Boot (current repository baseline)  
**Database:** PostgreSQL + pgvector  
**AI Provider:** Gemini through KBase-owned ports; Spring AI primary adapter path  
**Object Source:** Existing MinIO through `StorageService`  
**Background Work:** PostgreSQL-backed durable jobs  
**Frontend:** Out of scope  
**Streaming:** Out of scope

---

# 1. Purpose

Tài liệu này chuyển AI v1 product/design set thành chuỗi milestone/task có thể được coding agent thực thi và verify.

Implementation Plan không thay thế product/design source of truth.

```text
Core v1 frozen baseline
+ AI source documents
        ↓
AI master plan
        ↓
one active milestone/slice
        ↓
agent loads only relevant sources
        ↓
inspect current repository
        ↓
implement smallest complete task
        ↓
tests/review
        ↓
milestone gate
        ↓
next milestone
```

---

# 2. Source Document Registry

Core source documents SD-01..SD-13 vẫn là authority cho Core behavior.

AI v1 bổ sung:

| Alias | Canonical Document | Authority |
|---|---|---|
| SD-14 | KBase – AI Chatbot v1 Specification | accepted AI product behavior |
| SD-15 | KBase – AI Chatbot RAG Architecture and Application Design | AI application/provider/indexing/Guide architecture |
| SD-16 | KBase – AI Chatbot Persistence and Vector Search Design | AI schema, pgvector, jobs, retention persistence |
| SD-17 | KBase – AI Chatbot REST API Specification | AI public REST contract target |
| SD-18 | KBase – AI Chatbot Testing Strategy | required AI verification |
| SD-19 | KBase – AI Chatbot v1 Implementation Plan | implementation order only |

Harness canonical registry remains `.harness/source-doc-registry.json`.

---

# 3. Source-of-Truth Precedence

For Core behavior:

```text
SD-01..SD-12 retain Core v1 authority.
```

For AI behavior:

```text
1. SD-14 product behavior
2. SD-15 architecture/integration boundary
3. SD-16 persistence/vector/job design
4. SD-17 public REST contract
5. relevant frozen Core design SD-02..SD-12
6. SD-18 required verification
7. SD-19 implementation order only
```

Core v1 statement "AI/RAG out of scope for Core v1" remains historically correct and does not block this separately approved AI v1 phase.

If AI design conflicts with frozen Core invariant outside explicitly opened AI scope:

```text
DO NOT silently change Core.
→ record conflict
→ mark task BLOCKED
→ obtain design resolution
```

---

# 4. No-Guess / Context-Depletion Rule

If an agent cannot recall an AI rule:

1. stop modifying affected behavior;
2. reload SD-14..SD-18 as relevant;
3. reload Core source documents that own the touched behavior;
4. inspect current code/migration/tests;
5. inspect completed/current plan state;
6. continue only when rule is recovered.

Generic RAG/framework best practice never overrides KBase accepted behavior.

---

# 5. AI Business Rules the Agent Must Never Reinterpret

Unless source docs are explicitly revised:

1. Project Assistant is project-scoped.
2. Project Assistant conversations are private to creator.
3. OWNER/other MEMBER/ADMIN do not read another user's normal AI conversation.
4. Maximum 5 conversations per user per project.
5. Conversation is created with first message; no persistent empty chat required.
6. Conversation can be renamed/hard-deleted by creator.
7. One active generation per conversation.
8. User message persists before provider call.
9. Project Assistant uses current authorized evidence only.
10. Conversation history is not authoritative project knowledge.
11. No usable evidence → deterministic `NO_EVIDENCE`; no general-knowledge fallback.
12. Grounded answer requires structured sources.
13. Current project authorization is rechecked before completed answer is returned/persisted.
14. Document indexing is async/durable; Gemini outage does not fail Core document upload.
15. User-visible index states: PENDING/PROCESSING/READY/FAILED/UNSUPPORTED.
16. Project retrieval filters `project_id` inside SQL.
17. Supported v1 AI document types: PDF, DOC/DOCX, PPT/PPTX, MD, TXT.
18. XLS/XLSX, OCR, images/video multimodal are deferred.
19. Member loses AI access immediately on membership loss.
20. Private conversations retained 7 days, restored on rejoin within grace, hard-purged otherwise.
21. Project deletion deletes project AI data immediately without grace.
22. Deleted document leaves historical source snapshot but cannot be retrieved again.
23. KBase Guide uses approved existing product specs only; no full-repo ingestion.
24. Guide has no persistent conversation library in v1.
25. Guide cannot access project corpus/conversations.
26. Gemini primary; Spring AI primary integration path behind KBase ports.
27. Direct Gemini SDK only when documented technical need exists behind same ports.
28. PostgreSQL + pgvector is AI vector store; KBase owns schema.
29. Background jobs are PostgreSQL-durable; no Kafka/RabbitMQ required.
30. Initial AI v1 is non-streaming.
31. Human Project Chat is future scope.
32. Frontend remains out of scope.
33. AI endpoints have configurable usage/rate/resource guards.
34. Automated release gate does not require real Gemini credential.

---

# 6. Current Repository Baseline

At AI plan creation:

```text
branch source: dev
Core v1: frozen + M16 maintenance complete
Java: 21
Spring Boot: 4.1.1
Flyway migrations: V1–V3
Core persistent tables: 10
full verified suite baseline: 213 tests
PostgreSQL runtime: 17
Redis: 7.4
MinIO: existing StorageService adapter
Tika: MIME detection only
AI code/schema/dependency: none
pgvector: not present in current runtime
frontend: absent/deferred
```

Agent must re-run baseline in M0; this document does not substitute executable evidence.

---

# 7. Harness Execution Contract

Task states:

```text
TODO
READY
IN_PROGRESS
BLOCKED
IMPLEMENTED
REVIEW_FAILED
TEST_FAILED
DONE
```

Only one active AI milestone/slice is prioritized unless this master plan explicitly marks safe parallel work.

At end of every milestone:

- update active/completed plan state;
- update `docs/CURRENT_STATE.md`;
- update generated DB/API docs when source truth changed;
- update `docs/QUALITY_SCORE.md` when capability now has executable evidence;
- add deferred work to tech-debt tracker;
- do not mark gate pass without real command results.

---

# 8. Milestone Overview

```text
M0  AI Preflight & Technical Compatibility
M1  AI Runtime Foundation & Provider Ports
M2  pgvector / AI Persistence Schema
M3  Durable Job Engine & Core Lifecycle Hooks
M4  Gemini Provider Adapters
M5  Content Extraction / Chunking / Document Indexing
M6  Semantic Retrieval / Grounding / Citations
M7  Project Assistant Conversations & REST API
M8  Membership Retention / Deletion / Security Races
M9  KBase Guide
M10 Usage Guard / OpenAPI / Observability / Hardening
M11 Full Runtime Verification / AI v1 Freeze
```

Dependency graph:

```text
M0
 ↓
M1
 ↓
M2
 ↓
M3
 ↓
M4
 ↓
M5
 ↓
M6
 ↓
M7
 ↓
M8
 ↓
M9
 ↓
M10
 ↓
M11
```

Default execution is sequential. Do not optimize milestone order before baseline exists.

---

# 9. M0 – AI Preflight & Technical Compatibility

Goal:

```text
Prove current Core baseline and lock compatible AI/pgvector/provider dependency choices before code/schema changes.
```

## M0-01 – Reinspect Repository and Baseline

**Load:** SD-01, SD-14, SD-15, SD-19, AGENTS/ARCHITECTURE/CURRENT_STATE.

Verify current branch/code/docs against plan baseline.

Run:

```text
mvn -B -ntp clean verify
docker compose -f docker-compose.yml config --quiet
```

Acceptance:

- full Core baseline passes or existing failure is recorded/blocking;
- no uncommitted/unknown AI implementation assumed;
- current dependency tree captured.

## M0-02 – Lock Spring AI / Gemini Integration Versions

**Load:** SD-14, SD-15, current `pom.xml`.

Resolve and record:

- exact Spring AI version compatible with Spring Boot 4.1.1;
- exact Google GenAI integration artifact(s);
- exact Gemini chat model default;
- `gemini-embedding-2` + output dimension 768 support;
- provider timeout/config API;
- whether direct Gemini SDK is needed for any capability.

Rules:

- prefer Spring AI path if compatible;
- do not downgrade Spring Boot/Core silently;
- if Spring AI cannot satisfy a capability, use direct SDK only behind KBase port and record reason;
- if 768 baseline cannot be achieved, mark dependent work BLOCKED pending design resolution.

## M0-03 – Lock pgvector Runtime / JDBC Choices

Resolve:

- pgvector-enabled PostgreSQL 17 image for local/Testcontainers;
- pgvector extension version;
- JDBC/native binding approach;
- HNSW support;
- migration compatibility.

Do not replace PostgreSQL with separate vector database.

## M0-04 – Lock Extraction Dependencies

Inspect existing Tika dependency.

Select minimum parser dependencies required for PDF, DOC/DOCX, PPT/PPTX, MD/TXT extraction.

Do not add OCR/multimodal/spreadsheet scope.

## M0-05 – Lock AI Config and Test Doubles

Record typed config keys/defaults for:

- provider models/dimensions/timeouts;
- chunk/retrieval tuning;
- worker polling/lease/retry;
- 7-day retention;
- max message length;
- usage/rate guard.

Define deterministic `AiChatModel` and `AiEmbeddingModel` test doubles.

## M0-06 – Revalidate Harness Sources

Verify SD-14..SD-19 paths/precedence and active M0 plan.

No code modification may proceed if registry/source conflict exists.

### M0 Gate

```text
✓ Core clean verify passes
✓ exact AI dependency versions resolve
✓ Spring AI/direct SDK decision recorded
✓ pgvector PostgreSQL 17 path resolves
✓ extraction dependency set scoped
✓ AI config/test-double baseline recorded
✓ no unresolved compatibility blocker
```

---

# 10. M1 – AI Runtime Foundation & Provider Ports

Goal: add compile-safe AI module/config boundaries without implementing RAG behavior.

Tasks:

## AI-FOUND-01 – Add AI dependencies approved in M0

Update Maven only with locked versions.

Run dependency tree and clean verify.

## AI-FOUND-02 – Add `com.kbase.ai` package skeleton

Create config/provider port/domain model boundaries.

No controller or provider network call yet unless needed for compile/test.

## AI-FOUND-03 – Typed AI configuration

Add `AiProperties` or appropriately split typed properties.

Secrets only through environment.

Add safe `.env.example` variable names; never value.

## AI-FOUND-04 – KBase AI ports

Implement provider-neutral chat/embedding interfaces and result/request models.

No Spring AI/Google type outside adapter/config package.

## AI-FOUND-05 – Deterministic fake adapters

Test profile/unit fixtures must run without Gemini credential/public network.

## AI-FOUND-06 – pgvector-capable local/test runtime foundation

Update local PostgreSQL image/test infrastructure only as required by M0 choice, preserving PostgreSQL 17 semantics and `postgres_data`.

### M1 Gate

- compile and full Core regression pass;
- typed config binding tests;
- provider ports contain no vendor type;
- local/test DB image can expose pgvector extension capability;
- normal app can disable/not invoke AI provider during non-AI Core tests.

---

# 11. M2 – pgvector / AI Persistence Schema

Goal: implement SD-16 with Flyway as authority.

Tasks:

## AI-DB-01 – Create AI migration

Add pgvector extension + AI tables/constraints.

Do not edit V1–V3.

## AI-DB-02 – Add relational indexes + HNSW

Create required project/document/job indexes and cosine HNSW vector indexes.

## AI-DB-03 – Persistence mappings/repositories

Implement JPA repositories for state/conversation/jobs where appropriate.

Implement vector repository with approved JDBC/native SQL boundary.

## AI-DB-04 – Quota/generation DB guards

Implement locking/query support and partial unique index for one active generation.

## AI-DB-05 – Migration integrity upgrade

Update current tests that assume exactly 3 migrations/10 business tables.

Verify clean + upgrade path.

## AI-DB-06 – Generated schema sync

Update `docs/generated/db-schema.md` only after live schema verification.

### M2 Gate

- vector extension and vector(768) verified;
- all AI FK/delete rules verified;
- HNSW catalog verified;
- Hibernate validate passes;
- cross-project vector repository query test proves SQL filter;
- generated DB schema current.

---

# 12. M3 – Durable Job Engine & Core Lifecycle Hooks

Goal: create durable async execution before any provider-heavy indexing.

Tasks:

## AI-JOB-01 – Job repository/claiming

Implement due-job claim with `FOR UPDATE SKIP LOCKED`, lease, attempts, retry, stale recovery.

## AI-JOB-02 – Worker scheduler

`@Scheduled` polls durable jobs; no in-memory-only task source.

## AI-JOB-03 – Document index intent

After successful Core document persistence, record index state + durable job:

- supported → PENDING + DOCUMENT_INDEX;
- unsupported → UNSUPPORTED.

No provider call in upload request.

Batch upload rollback must not leave orphan AI job/state.

## AI-JOB-04 – Document/project delete safety

Verify DB cascade and queued/processing job no-resurrection behavior.

## AI-JOB-05 – Membership retention hooks

Remove/leave schedules `CONVERSATION_PURGE` at +7d.

Invitation accept/rejoin cancels/no-ops purge.

All durable state changes use same PostgreSQL transaction where required.

### M3 Gate

- two workers cannot claim same job;
- stale lease recovery passes;
- Core upload path remains provider-independent;
- unsupported document state works without provider;
- delete/race tests pass;
- retention schedule/cancel persistence verified.

---

# 13. M4 – Gemini Provider Adapters

Goal: implement provider network boundary only after ports/jobs/schema are stable.

Tasks:

## AI-PROV-01 – Spring AI Gemini chat adapter

Implement `AiChatModel` adapter using M0-locked integration.

## AI-PROV-02 – Spring AI Gemini embedding adapter

Implement query/document embedding modes and enforce 768 output.

If M0 approved direct SDK for embedding, implement it behind same KBase port instead.

## AI-PROV-03 – Error translation

Map timeout/rate/provider/config failures to safe internal categories and `AI_PROVIDER_UNAVAILABLE` where public.

## AI-PROV-04 – Provider privacy/logging

No raw prompt/chunk/response/API key logging.

## AI-PROV-05 – Adapter contract tests

Use mocks/test doubles; no real Gemini required.

### M4 Gate

- service/domain imports no provider type;
- 768 validation passes;
- timeout/error mapping stable;
- sensitive log negative tests pass;
- full regression pass.

---

# 14. M5 – Content Extraction / Chunking / Document Indexing

Goal: make supported Core documents eventually READY.

Tasks:

## AI-IDX-01 – Extraction model/port

Implement `DocumentContentExtractor` and source-location model.

## AI-IDX-02 – Format extraction adapters

Support exactly PDF, DOC/DOCX, PPT/PPTX, MD, TXT.

No OCR/XLSX/image/video.

## AI-IDX-03 – Structure-aware chunker

Implement versioned chunking baseline ~600–800 tokens, ~10–15% overlap, preserving page/slide/section.

## AI-IDX-04 – DOCUMENT_INDEX handler

Read via `StorageService`, hash source, extract, chunk, embed, persist staging version, activate atomically.

## AI-IDX-05 – Retry/failure classification

Transient errors → bounded RETRY; exhausted/permanent → FAILED with safe reason.

## AI-IDX-06 – AI index status/read + manual retry application service

No synchronous reindex.

### M5 Gate

- all supported fixture types reach READY;
- unsupported types remain UNSUPPORTED without embed call;
- corrupt/no-text file gets safe FAILED reason;
- retry/recovery/idempotency pass;
- document delete while worker active cannot resurrect chunks;
- MinIO accessed only through `StorageService`.

---

# 15. M6 – Semantic Retrieval / Grounding / Citations

Goal: implement strict evidence pipeline before exposing full conversation API.

Tasks:

## AI-RAG-01 – Query embedding + project SQL retrieval

Authorize project then execute vector SQL with mandatory `project_id`.

## AI-RAG-02 – Evidence selection

Implement configurable candidate limit, threshold, dedup and adjacent merge.

## AI-RAG-03 – Strict no-evidence policy

No usable evidence → deterministic result, zero chat-provider calls.

## AI-RAG-04 – Prompt builder

Separate system/context/evidence/question and mark evidence untrusted.

## AI-RAG-05 – Chat generation + source label validation

Only valid retrieved labels can become sources.

## AI-RAG-06 – Source snapshot mapper

Create structured citation persistence model with live FK + snapshots.

### M6 Gate

- cross-project semantic trap test passes on real pgvector;
- no-evidence provider invocation = 0;
- prompt injection cannot change retrieval/auth;
- grounded result always has valid source;
- deleted/inactive chunks not retrieved.

---

# 16. M7 – Project Assistant Conversations & REST API

Goal: expose private non-streaming Project Assistant.

Tasks:

## AI-CHAT-01 – Conversation authorization/service

Private creator ownership + current project access.

## AI-CHAT-02 – Atomic max-five create

Create-with-first-message only; locked quota enforcement.

## AI-CHAT-03 – Conversation list/get/rename/delete

List own only, updatedAt DESC; title max 100; hard delete.

## AI-CHAT-04 – Send message lifecycle

Persist USER + assistant PROCESSING, one active generation, call RAG, finalize COMPLETED/FAILED.

## AI-CHAT-05 – Authorization recheck

Recheck before final answer persistence/response; revoked request cannot deliver completed answer.

## AI-CHAT-06 – Message/source read API

Paginated deterministic order; unavailable sources represented safely.

## AI-CHAT-07 – Controller/DTO/error contract

Implement SD-17 Project Assistant endpoints and stable errors.

### M7 Gate

- max 5 incl. concurrency;
- privacy matrix MEMBER/OWNER/ADMIN;
- one active generation constraint;
- provider failure preserves user message;
- no-evidence response 200 domain outcome;
- source authorization preserved;
- API/security tests pass.

---

# 17. M8 – Membership Retention / Deletion / Security Races

Goal: fully prove 7-day lifecycle and destructive/race paths.

Tasks:

## AI-RET-01 – Immediate revoke behavior

Existing project auth denies retained conversations after membership loss.

## AI-RET-02 – Purge handler

At due time, recheck membership; purge only if still absent.

## AI-RET-03 – Rejoin restore

Invitation accept/current membership creation neutralizes purge.

## AI-RET-04 – Project delete cascade

No 7-day grace for project hard delete.

## AI-RET-05 – Deleted document historical sources

Live FKs null, snapshots remain unavailable.

## AI-RET-06 – In-flight revoke race

Fake long provider; revoke before completion; no completed answer returned/persisted.

Use controllable clock; do not sleep seven days in tests.

### M8 Gate

All SD-14 retention/deletion acceptance scenarios pass on real PostgreSQL.

---

# 18. M9 – KBase Guide

Goal: add stateless grounded product-help assistant without project-data access.

Tasks:

## AI-GUIDE-01 – Exact source allowlist

Initial canonical sources only:

- Core v1 Product Spec;
- AI Chatbot v1 Product Spec.

## AI-GUIDE-02 – Build-time source packaging

Configure Maven/Docker build to package only allowlisted source Markdown; no duplicate manual copies; no GitHub runtime dependency.

## AI-GUIDE-03 – Guide source hash/reindex

Create/refresh Guide index from packaged source; skip unchanged source.

## AI-GUIDE-04 – Guide retrieval

Query only Guide vector tables.

## AI-GUIDE-05 – Strict Guide grounding

Undocumented/off-topic → deterministic refusal/no-evidence; no general fallback.

## AI-GUIDE-06 – Stateless REST query

Implement SD-17 Guide endpoint with bounded client-supplied USER/ASSISTANT context; no SYSTEM role, no persistent conversation.

### M9 Gate

- only allowlisted files indexed;
- exec-plan/internal docs excluded;
- Guide cannot hit project vector/document repo;
- documented questions grounded with source heading;
- no-evidence works without invention;
- Docker artifact includes exact approved sources only.

---

# 19. M10 – Usage Guard / OpenAPI / Observability / Hardening

Goal: finish operational and contract quality before release verification.

Tasks:

## AI-HARD-01 – AI usage/rate guard

Implement M0-selected configurable per-user guard, preferred Redis namespace if approved.

Core endpoint availability must not depend on AI rate state.

## AI-HARD-02 – AI observability

Safe metrics/logs for job depth/state/latency/provider errors/no-evidence without raw content.

## AI-HARD-03 – OpenAPI annotations/contract tests

Document SD-17 endpoints/outcomes/errors; update expected path set.

## AI-HARD-04 – Generated API sync

Update `docs/generated/api-schema.md` from verified runtime.

## AI-HARD-05 – Security/leakage audit

Scan DTO/OpenAPI/logs for:

- vector;
- source hash;
- job payload/lease;
- provider request/response/API key;
- storage key;
- raw project content.

## AI-HARD-06 – Documentation sync

Update living docs from planned → implemented state.

### M10 Gate

- rate guard verified;
- OpenAPI contract tests pass;
- generated API current;
- sensitive-field/log scan clean;
- full clean verify passes.

---

# 20. M11 – Full Runtime Verification / AI v1 Freeze

Goal: prove production-like local behavior and freeze AI v1.

## AI-VERIFY-01 – Clean build

`mvn -B -ntp clean verify`.

## AI-VERIFY-02 – Clean Docker startup

Fresh pgvector-enabled PostgreSQL + existing Redis/MinIO/backend topology.

Flyway Core + AI migrations and Hibernate validation pass.

## AI-VERIFY-03 – Project Assistant golden journey

```text
upload supported doc
→ PENDING/PROCESSING
→ READY
→ ask grounded question
→ answer + source
→ no-evidence question
→ deterministic refusal
```

Use deterministic provider adapter/config for automated runtime smoke.

## AI-VERIFY-04 – Failure isolation

Simulate provider unavailable:

- AI call/index retries/fails safely;
- Core upload/download/metadata search still healthy.

## AI-VERIFY-05 – Security journeys

Cross-project vector trap, private conversation matrix, prompt injection, source authorization.

## AI-VERIFY-06 – Retention journeys

Remove/rejoin and purge with controllable time/test path.

## AI-VERIFY-07 – Guide journey

Guide answers approved product question and refuses unsupported/internal question.

## AI-VERIFY-08 – Restart/recovery

Restart backend with pending/stale job and prove durable resume.

## AI-VERIFY-09 – Consistency audit

Docs ↔ code ↔ Flyway ↔ OpenAPI ↔ generated snapshots.

## AI-VERIFY-10 – Freeze report

Create completed M11 report and update current state/quality/reliability.

### M11 Gate

```text
✓ Core+AI full suite pass
✓ AI Docker golden journeys pass
✓ zero cross-project leakage
✓ strict no-evidence verified
✓ durable job restart verified
✓ retention lifecycle verified
✓ Guide corpus boundary verified
✓ generated docs current
✓ no unresolved BLOCKER/HIGH issue
✓ AI v1 FROZEN
```

---

# 21. Generated Documentation

| Document | Update milestone |
|---|---|
| `docs/generated/db-schema.md` | M2 after verified Flyway AI migration |
| `docs/generated/api-schema.md` | M10 after verified runtime OpenAPI |

Do not edit generated snapshots in documentation-only pre-implementation phase.

---

# 22. Rollback / Migration Policy

AI migrations add new tables/extension; Core tables must remain backward-compatible.

Application rollback before AI is used may leave additive AI schema in DB; do not automatically destructive-drop tables/vector extension.

No destructive rollback migration without explicit approval.

Provider config can disable AI capability without disabling Core runtime if implementation needs emergency fallback.

---

# 23. Completion Report Format

Each task/slice report:

```text
Task:
Status:

Files created:
Files modified:

Behavior implemented:

Tests added:
Tests executed:
Test result:

Source documents used:

Generated docs updated:
Known limitations:
New unresolved questions:

Core business rules changed:
YES / NO
AI product rules changed:
YES / NO
```

Any unapproved product-rule change blocks normal task completion.
