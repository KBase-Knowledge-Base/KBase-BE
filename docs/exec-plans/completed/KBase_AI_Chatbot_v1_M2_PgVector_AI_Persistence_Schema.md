# KBase AI Chatbot v1 – M2 pgvector / AI Persistence Schema

**Status:** DONE
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Depends on:** Completed M0 and M1 (`../completed/KBase_AI_Chatbot_v1_M0_Preflight_and_Technical_Compatibility.md`, `../completed/KBase_AI_Chatbot_v1_M1_Runtime_Foundation_and_Provider_Ports.md`)
**Current step:** Complete; next slice is M3 – Durable Job Engine & Core Lifecycle Hooks
**Scope:** SD-16 persistence schema, pgvector indexes, persistence mappings/repositories, and database-level quota/generation guards. Preserve Core v1 and keep provider calls, workers, retrieval behavior, and public AI API out of this slice.

## 1. Required source documents

Read and re-check before code changes:

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`;
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/DEPLOYMENT.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- the accepted AI RAG architecture and testing strategy documents;
- the completed M0 and M1 plans and the AI master implementation plan.

SD-16 is the authority for table intent, foreign keys, deletion semantics, vector dimensions, SQL project isolation, and the approved JDBC/native SQL boundary. Core migrations V1–V3 remain immutable.

## 2. Tasks

### AI-DB-01 – Create the AI migration

- Add the pgvector extension and the AI tables required by SD-16 in a new migration after Core V3.
- Preserve Core FK targets and same-project composite integrity.
- Encode status/enumeration constraints, lifecycle timestamps, delete behavior, and the partial unique active-generation guard in the database.
- Do not edit Core migrations V1–V3.

### AI-DB-02 – Add relational indexes and HNSW indexes

- Add project/document/version/job/status indexes required by the design.
- Add cosine HNSW indexes for document and Guide vectors with `vector(768)`.
- Verify the PostgreSQL catalog rather than relying only on migration text.

### AI-DB-03 – Add persistence mappings and repositories

- Add feature-local JPA mappings/repositories for relational AI state where appropriate.
- Keep vector insert/query operations behind a KBase-owned JDBC/native SQL repository boundary using the M1 pgvector JDBC dependency.
- Prove that vector retrieval filters `project_id` inside SQL and only returns the active index version.
- Keep embedding/vector fields out of public DTOs.

### AI-DB-04 – Add quota and generation database guards

- Add the query/locking support needed for the five-conversation quota and one active assistant generation invariant.
- Verify concurrent attempts cannot bypass the database backstop.

### AI-DB-05 – Upgrade migration integrity verification

- Update existing tests that assert exactly three migrations or ten Core business tables so they distinguish immutable Core V1–V3 from the AI migration.
- Verify fresh apply and Core-upgrade-to-AI paths on pgvector PostgreSQL 17.

### AI-DB-06 – Synchronize generated schema documentation

- Update or regenerate `docs/generated/db-schema.md` only after live migration/catalog verification.
- Record the verified AI tables, constraints, indexes, vector dimensions, and deletion behavior in the plan and current state.
- Leave `docs/generated/api-schema.md` unchanged because M2 has no endpoint or API contract change.

## 3. Explicitly out of scope

- durable worker scheduling/claiming, document lifecycle hooks, extraction, chunking, indexing, or provider calls (M3+);
- retrieval/application services, prompt construction, conversations/messages HTTP behavior, Guide behavior, citations, rate limiting, or public AI endpoints (later slices);
- frontend, streaming, Project Chat, OCR/multimodal, spreadsheet RAG, brokers, and real Gemini credentials/network calls;
- changes to Core migrations, Core API behavior, or the Core generated contract beyond evidence needed to update the generated database snapshot.

## 4. M2 Gate

```text
[x] fresh pgvector PostgreSQL 17 applies Core V1–V3 plus the AI migration
[x] vector extension and every vector(768) column are verified live
[x] all AI FK/delete rules, status constraints, and partial unique guards are verified
[x] relational and HNSW indexes are verified in the PostgreSQL catalog
[x] Hibernate validate and persistence/repository integration tests pass
[x] cross-project vector repository query proves SQL project filtering and active-version filtering
[x] concurrent quota/generation guard tests pass
[x] docs/generated/db-schema.md matches the verified schema
[x] no public API, worker, provider, or RAG behavior leaked into M2
```

## 5. Verification path

Use the commands documented in `docs/DEVELOPMENT.md`, including:

- `docker compose -f docker-compose.yml config --quiet`;
- targeted migration, mapping, vector repository, and integrity tests on Docker Testcontainers;
- `mvn -B -ntp clean verify`;
- static scans for Core migration immutability, SQL project predicates, vendor leakage, public DTO exposure, and M2 scope leakage.

## 6. Migration and rollback

Use one forward-only migration after V3. Do not rewrite or renumber Core migrations. If verification exposes a migration defect before handoff, fix the new migration and rerun fresh/upgrade paths; do not reset the repository or mutate an existing applied Core migration. Any post-apply correction requires a separately reviewed forward migration and an explicit plan update.

## 7. Initial risks and decisions

- Exact AI migration/table/constraint names must follow SD-16 and current Core naming conventions; do not infer names from the conceptual design without checking source conventions.
- Generated DB documentation must remain Core-only until the live AI migration is verified.
- Vector SQL must keep project filtering in the database even when service authorization is present.
- No provider adapter is introduced in M2; M1's disabled-by-default startup contract remains required.

## 8. Progress log

| Date | Status | Evidence / next action |
|---|---|---|
| 2026-09-22 | READY | M1 Gate PASS: full suite 228/228, pgvector compatibility 1/1, dependency tree and Compose config pass. Read SD-16 before starting AI-DB-01. |
| 2026-09-22 | DONE | V4 schema, JPA mappings/repositories, vector SQL boundary, quota/generation guards and generated DB schema completed. Fresh/upgrade migration, catalog, Hibernate and persistence evidence pass; targeted M2 set 36/36. Final `mvn -B -ntp clean verify` recorded `BUILD SUCCESS`, 241/241 tests, 0 failures/errors/skips; Compose config and `git diff --check` pass. |

## 9. Final result

M2 is complete. V4 is an additive Flyway source-of-truth migration after immutable Core V1–V3. The verified schema contains eight AI tables, pgvector `vector(768)`, HNSW cosine indexes, relational query indexes, lifecycle/status checks, same-project composite FKs, citation `SET NULL`, conversation/project/document cascades, a partial unique active-generation guard and durable job state. The implementation deliberately stops before workers, provider adapters, indexing, retrieval, conversation behavior and public API.

Evidence:

- `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest,FlywayAiUpgradeIntegrationTest,AiPersistenceIntegrationTest" test` — 36/36;
- `mvn -B -ntp clean verify` — `BUILD SUCCESS`, 241 tests, 0 failures, 0 errors, 0 skipped; final full Core + AI gate;
- `docker compose -f docker-compose.yml config --quiet` and `git diff --check` — pass.
