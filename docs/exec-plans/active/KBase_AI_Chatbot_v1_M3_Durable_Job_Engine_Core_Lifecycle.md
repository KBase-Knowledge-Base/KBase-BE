# KBase AI Chatbot v1 – M3 Durable Job Engine & Core Lifecycle Hooks

**Status:** READY  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1 and M2 AI slices  
**Scope:** PostgreSQL-backed job claiming/lease/retry and Core document lifecycle hooks only.  
**Next step:** AI-JOB-01 – implement a durable, concurrency-safe job claim primitive.

## 1. Required source documents

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`;
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`;
- `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`;
- completed M0, M1 and M2 AI plans plus the AI master implementation plan.

M2 V4 schema and `ai_jobs` are the source of truth. Do not rewrite V1–V4 or add provider behavior in this slice.

## 2. Tasks

### AI-JOB-01 – Durable claim / lease / retry

- Add repository-level claim support using `FOR UPDATE SKIP LOCKED`.
- Claim only due `PENDING`/`RETRY` jobs and recover stale `PROCESSING` jobs according to a bounded attempt policy.
- Persist `PROCESSING`, `lease_until` and `locked_by` in a short transaction before execution.
- Keep completion/failure transitions idempotent and safe against stale workers.

### AI-JOB-02 – Worker scheduler boundary

- Add a bounded scheduler that polls PostgreSQL durable state.
- Do not make JVM memory, an in-process queue or scheduler state the source of truth.
- Keep handler execution outside the claim transaction.
- Provider calls and extraction remain out of scope until their dedicated milestones.

### AI-JOB-03 – Core document lifecycle hooks

- After successful Core document persistence, create/update document AI intent and a durable indexing job.
- Make document deletion prevent late job resurrection through database/state checks.
- Keep the existing StorageService boundary; do not import MinIO SDK into AI code.
- Define idempotency/dedup behavior for repeated lifecycle events.

## 3. Explicitly out of scope

- Gemini/Spring AI provider adapters or network calls;
- extraction, chunking, embedding, retrieval, grounding, citations or Guide indexing;
- Project Assistant/Guide REST endpoints, conversation behavior, rate guard or OpenAPI changes;
- frontend, streaming, OCR, multimodal, spreadsheet RAG and message brokers;
- changes to Core migrations V1–V4 or generated API schema.

## 4. M3 Gate

```text
[ ] two concurrent workers cannot claim the same due job
[ ] SKIP LOCKED allows independent jobs to progress
[ ] lease expiry/stale recovery and bounded attempts are verified with a controllable clock
[ ] DONE/CANCELLED jobs are not reprocessed; retry run_at is respected
[ ] job deduplication is idempotent
[ ] document create/delete lifecycle hooks persist durable state without late resurrection
[ ] Core regression and existing M2 persistence tests remain green
[ ] no provider, extraction, retrieval or public API behavior leaks into M3
```

## 5. Verification path

- targeted PostgreSQL Testcontainer concurrency/lease/retry tests;
- document lifecycle integration tests with real Core authorization and StorageService boundary;
- `mvn -B -ntp clean verify`;
- `docker compose -f docker-compose.yml config --quiet`;
- static scans for `SKIP LOCKED`, bounded retry, no provider/network call, no direct MinIO SDK and no API contract drift.

## 6. Migration and rollback

M3 uses the existing additive V4 schema. If a new column/index is proven necessary, add a separately reviewed forward migration; never rewrite V1–V4. Application rollback must leave durable job rows and additive schema intact; do not destructive-drop AI tables.

## 7. Initial risks and decisions

- Claim/complete transitions must distinguish stale worker tokens from current lease owners.
- Document delete and job completion can race; the handler must re-check current document/index state before activating anything.
- No provider work is allowed until M4; deterministic fake boundaries remain the only AI execution path in automated tests.

## 8. Progress log

| Date | Status | Evidence / next action |
|---|---|---|
| 2026-09-22 | READY | M2 Gate PASS: Flyway V4, pgvector/JPA persistence, live catalog, fresh/upgrade migration, targeted 36/36 and final full gate 241/241 verified. Begin AI-JOB-01 only after owner continues this active slice. |
