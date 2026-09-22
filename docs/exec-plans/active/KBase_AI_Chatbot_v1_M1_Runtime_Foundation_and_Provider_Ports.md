# KBase AI Chatbot v1 – M1 Runtime Foundation & Provider Ports

**Status:** READY
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Depends on:** Completed M0 `../completed/KBase_AI_Chatbot_v1_M0_Preflight_and_Technical_Compatibility.md`
**Current step:** AI-FOUND-01 – add the M0-approved dependencies
**Scope:** Compile-safe AI module/config/provider boundaries and local/test pgvector capability. No RAG behavior, schema, indexing, retrieval, REST endpoint, or real-provider call in this slice.

---

## 1. Required source documents

Read before code changes and re-check the current repository:

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`;
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/DEPLOYMENT.md`;
- SD-14, SD-15, SD-16, SD-18, SD-19 and the completed M0 plan;
- current Core provider/config/test conventions before adding `com.kbase.ai`.

M0 is the authority for Spring AI 2.0.1, Google GenAI starters, `gemini-2.5-flash`, `gemini-embedding-2`, 768 dimensions, pgvector image/JDBC choices, Tika scope, and the provisional configuration values.

---

## 2. Tasks

### AI-FOUND-01 – Add M0-approved dependencies

- Add the Spring AI BOM `2.0.1` and Google GenAI chat/embedding starters.
- Add `com.pgvector:pgvector:0.1.6` only at the approved persistence boundary.
- Do not add a direct Google SDK adapter or Spring AI VectorStore production schema.
- Verify dependency resolution/tree and run the full Core suite.

### AI-FOUND-02 – Add `com.kbase.ai` package skeleton

Create the smallest feature-first package/config boundary required for compile-safe wiring. Do not create controllers, entities, repositories, migrations, indexing workers, extraction services, or retrieval services.

### AI-FOUND-03 – Add typed AI configuration

Bind the M0 configuration contract with `KBASE_AI_ENABLED=false` by default and secret-only API key input. Keep project filtering, strict grounding, privacy, and authorization re-checks as mandatory code rules rather than flags. Add safe names/defaults to `.env.example` only; never add a credential.

### AI-FOUND-04 – Add KBase-owned provider ports

Define vendor-neutral `AiChatModel` and `AiEmbeddingModel` request/result boundaries. No Spring AI, Google GenAI, provider response, raw prompt, or vector-store type may cross into application/domain contracts.

### AI-FOUND-05 – Add deterministic fake adapters

Implement the M0 fake contracts for unit/integration tests: deterministic chat response and capture/failure/timeout controls; fixed 768-dimensional embedding with controllable semantic ordering and query/document distinction. Normal tests must not need Gemini credentials or public network.

### AI-FOUND-06 – Add pgvector-capable local/test foundation

Update local/Testcontainers PostgreSQL wiring only as required to use `pgvector/pgvector:0.8.6-pg17-bookworm`, while preserving PostgreSQL 17 semantics, named Core volumes, Flyway ownership, and the Core runtime’s ability to start with AI disabled. Do not add the AI migration in M1.

---

## 3. Explicitly out of scope

- AI Flyway migration, entities, vector tables, HNSW production index, or generated DB schema;
- Project Assistant, KBase Guide, conversations, messages, citations, retrieval, prompt builder, chunking, extraction, indexing, durable jobs, retention, or rate limiter behavior;
- AI controllers/API/OpenAPI changes;
- real Gemini calls, provider network smoke, OCR, spreadsheets, multimodal, streaming, frontend, Project Chat, hybrid search/reranker, or Kafka/RabbitMQ.

---

## 4. M1 Gate

```text
[ ] M0-approved dependencies resolve without downgrading Core
[ ] full Core regression and package verification pass
[ ] typed AI configuration binds with AI disabled by default
[ ] KBase provider ports contain no vendor types
[ ] deterministic chat/embedding fakes run without network or credentials
[ ] local/test PostgreSQL can expose pgvector 0.8.6 on PostgreSQL 17
[ ] normal Core startup/tests do not invoke an AI provider when disabled
[ ] no migration, AI endpoint, or RAG behavior leaked into M1
```

---

## 5. Handoff log

| Date | Status | Evidence / next action |
|---|---|---|
| 2026-09-22 | READY | M0 Gate PASS; start AI-FOUND-01 with the completed M0 decision log and preserve the frozen Core baseline |
