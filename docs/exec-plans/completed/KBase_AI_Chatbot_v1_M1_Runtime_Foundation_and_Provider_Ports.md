# KBase AI Chatbot v1 – M1 Runtime Foundation & Provider Ports

**Status:** DONE
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Depends on:** Completed M0 `../completed/KBase_AI_Chatbot_v1_M0_Preflight_and_Technical_Compatibility.md`
**Current step:** Complete; next slice is M2 – pgvector / AI Persistence Schema
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
[x] M0-approved dependencies resolve without downgrading Core
[x] full Core regression and package verification pass
[x] typed AI configuration binds with AI disabled by default
[x] KBase provider ports contain no vendor types
[x] deterministic chat/embedding fakes run without network or credentials
[x] local/test PostgreSQL can expose pgvector 0.8.6 on PostgreSQL 17
[x] normal Core startup/tests do not invoke an AI provider when disabled
[x] no migration, AI endpoint, or RAG behavior leaked into M1
```

---

## 5. Handoff log

### M1 preflight note

The required baseline was attempted before implementation while Docker Desktop was unavailable. `docker compose -f docker-compose.yml config --quiet` passed, and the initial `mvn -B -ntp clean verify` recorded 12 Testcontainers environment errors. Docker Desktop was started for the final gate; the previous environment-only errors did not reproduce.

| Date | Status | Evidence / next action |
|---|---|---|
| 2026-09-22 | DONE | M1 Gate PASS after Docker became available; final `mvn -B -ntp clean verify` passed 228/228 and the pgvector compatibility integration test passed |

## 6. Progress log

### AI-FOUND-01 – Dependencies

**Status:** DONE

Added the M0-approved Spring AI BOM (`org.springframework.ai:spring-ai-bom:2.0.1`), Google GenAI chat and text-embedding starters, and `com.pgvector:pgvector:0.1.6` to `pom.xml`. The dependency tree resolves Spring AI `2.0.1`, transitive `com.google.genai:google-genai:1.65.0`, pgvector `0.1.6`, Spring Boot `4.1.1`, and Java release `21` without a framework downgrade.

Verification:

- `mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree-m1-01.txt" "-DoutputType=text"` — PASS (`BUILD SUCCESS`)
- `mvn -B -ntp -DskipTests compile` — PASS (`BUILD SUCCESS`)

No direct Google SDK dependency, Spring AI VectorStore, migration, schema, endpoint, or provider call was added. Proceed to AI-FOUND-02.

### AI-FOUND-02 – `com.kbase.ai` foundation

**Status:** DONE

Added the minimal feature-first `com.kbase.ai.config` and `com.kbase.ai.provider.{port,model}` packages. The foundation contains only configuration and provider-neutral request/result boundaries; no controller, application service, repository, entity, migration, indexing, retrieval, conversation, or Guide class was added.

Verification: `mvn -B -ntp -DskipTests compile` — PASS (`BUILD SUCCESS`, Java release 21).

### AI-FOUND-03 – Typed configuration and disabled startup

**Status:** DONE

Added `com.kbase.ai.config.AiProperties` with the M0 defaults, secret-only optional API-key handling, 768-dimension contract validation, positive duration/limit validation, nullable similarity threshold, worker/retention defaults, and the non-toggleable security policy left out of configuration. Added explicit environment-backed bindings to `application.yml` and safe names/defaults to `.env.example`.

The base configuration sets `spring.ai.model.chat=none` and `spring.ai.model.embedding.text=none` and excludes the Google GenAI connection/model auto-configurations while M1 has no provider adapter. This prevents credential validation or provider activity during normal disabled startup; no dummy credential is used.

Verification:

- `mvn -B -ntp "-Dtest=AiPropertiesBindingTest" test` — PASS (4 tests; defaults, explicit binding, invalid dimensions, invalid durations/limits)
- `mvn -B -ntp "-Dtest=KBaseApplicationContextSmokeTest" test` — PASS (1 test; no Gemini credential, no Spring AI chat/embedding model beans)

### AI-FOUND-04 – KBase-owned provider ports

**Status:** DONE

Added `AiChatModel` and `AiEmbeddingModel` plus immutable KBase request/result models. Chat requests support system instructions, bounded generic conversation turns, generic evidence blocks, and a current question. Embeddings explicitly distinguish `QUERY` and `DOCUMENT`; results expose immutable vectors and dimensions. No Spring AI, Google GenAI, provider DTO, prompt/response, or vector-store type crosses the port boundary.

Verification: `mvn -B -ntp "-Dtest=AiProviderModelTest" test` — PASS (4 tests).

### AI-FOUND-05 – Deterministic fakes

**Status:** DONE

Added reusable test-scope `FakeAiChatModel` and `FakeAiEmbeddingModel`. Chat captures immutable requests, counts invocations, supports fixtures and controlled unavailable/timeout/failure modes, and otherwise returns a SHA-256-derived deterministic response. Embedding captures query/document modes, supports one-hot semantic fixtures, returns exactly 768 normalized dimensions, and uses stable SHA-256-derived vectors for unknown content. Neither fake imports a provider SDK or performs network I/O.

Verification: `mvn -B -ntp "-Dtest=FakeAiChatModelTest,FakeAiEmbeddingModelTest" test` — PASS (6 tests; determinism, capture/count, failure controls, 768 dimensions, normalized vectors, controlled cosine ordering, QUERY/DOCUMENT capture).

### AI-FOUND-06 – pgvector local/test foundation

**Status:** DONE

Updated `docker-compose.yml` and `.env.example` to use the locked `pgvector/pgvector:0.8.6-pg17-bookworm` default while preserving the PostgreSQL service name, credentials, healthcheck, and `postgres_data` volume. Centralized the image string for existing PostgreSQL Testcontainers and added `PgVectorCompatibilityIntegrationTest` to verify `CREATE EXTENSION vector`, `vector(768)`, JDBC `PGvector` binding, cosine ordering, and HNSW capability without adding a Flyway migration. Compose now passes the nullable similarity-threshold binding as well.

Verification:

- `mvn -B -ntp "-Dtest=PgVectorCompatibilityIntegrationTest" test` — PASS (1 test on PostgreSQL 17.11 with pgvector 0.8.6)
- final M1 targeted suite (`AiPropertiesBindingTest`, `AiProviderModelTest`, `FakeAiChatModelTest`, `FakeAiEmbeddingModelTest`, `KBaseApplicationContextSmokeTest`, `PgVectorCompatibilityIntegrationTest`) — PASS (16 tests)
- `mvn -B -ntp clean verify` — PASS (228 tests, 0 failures/errors/skips; package and Spring Boot repackage pass)
- `mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree-m1-01.txt" "-DoutputType=text"` — PASS
- `docker compose -f docker-compose.yml config --quiet` — PASS
- `git diff --check` — PASS

Final scope review: no AI migration, entity, repository, controller, endpoint, indexing, retrieval, conversation, Guide, real Gemini adapter/call, or generated DB/API documentation change was introduced. `KBASE_AI_ENABLED=false` remains the default, and the Core context smoke test confirms no Spring AI chat/embedding model bean is created in the disabled path.

M1 Gate: PASS. Move this plan to `docs/exec-plans/completed/` and begin the separately scoped M2 schema slice only after reviewing SD-16 and the M2 active plan.
