# KBase AI Chatbot v1 – M0 Preflight & Technical Compatibility

**Status:** DONE – M0 Gate PASS
**Completed:** `2026-09-22`
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Handoff:** M1 active slice is READY in `../active/KBase_AI_Chatbot_v1_M1_Runtime_Foundation_and_Provider_Ports.md`
**Scope:** Documentation and executable compatibility verification only; no AI runtime behavior, schema, migration, endpoint, extraction pipeline, retrieval, or provider adapter was implemented.

---

## 1. Objective and source-of-truth

M0 proves the frozen Core baseline, resolves the Spring AI/Gemini and PostgreSQL/pgvector paths, scopes future extraction dependencies, and records the typed-configuration and deterministic-test-double contracts needed by M1.

The source set was reloaded and checked against the repository, not inferred from `docs/CURRENT_STATE.md` alone:

- `ARCHITECTURE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`, `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/API_CONVENTIONS.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`;
- SD-01, SD-14, SD-15, SD-16, SD-17, SD-18, SD-19;
- `pom.xml`, `docker-compose.yml`, `Dockerfile`, application profiles, Flyway migrations, current `com.kbase` source/tests;
- `.harness/source-doc-registry.json` and `docs/exec-plans/active/index.md`.

No source conflict or compatibility blocker was found. Core v1 remains frozen; the historical statement that AI/RAG was out of scope for Core v1 remains correct and does not block the separately approved AI v1 phase.

---

## 2. M0-01 – Reinspect Repository and Core Baseline

**Status:** DONE

### Commands and evidence

| Command/check | Result |
|---|---|
| `Get-Location` / repository-root check | `C:\Learning\Fsoft\KBase` |
| `git branch --show-current` | `feat-AI` |
| `git status --short` at M0 start | Empty; no pre-existing worktree changes to reconcile |
| `mvn -B -ntp clean verify` | `BUILD SUCCESS`; 213 tests, 0 failures, 0 errors, 0 skipped |
| `docker compose -f docker-compose.yml config --quiet` | PASS |
| Source/dependency/package boundary inspection | No `com.kbase.ai` implementation, AI migration, pgvector dependency, semantic search, or provider adapter exists in the current runtime |

The inspected baseline is Java 21, Spring Boot 4.1.1, PostgreSQL `postgres:17-alpine`, Redis `redis:7.4-alpine`, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Lettuce 7.5.2, and Apache Tika core 3.3.2. The application still has the Core V1–V3 / 10-table schema and the existing StorageService/MinIO boundary. Current Tika usage is MIME detection only.

M0 did not modify `pom.xml`, application configuration, Docker Compose, Java source, tests, Flyway migrations, or generated DB/API snapshots.

---

## 3. M0-02 – Spring AI / Gemini Compatibility

**Status:** DONE

### Locked decision

| Concern | M0 decision |
|---|---|
| Spring AI | `org.springframework.ai:spring-ai-bom:2.0.1`; compatible path for the current Java 21 / Spring Boot 4.1.1 baseline |
| Chat starter | `org.springframework.ai:spring-ai-starter-model-google-genai:2.0.1` |
| Embedding starter | `org.springframework.ai:spring-ai-starter-model-google-genai-embedding:2.0.1` |
| Transitive Google client | `com.google.genai:google-genai:1.65.0` |
| Chat model | `gemini-2.5-flash` |
| Embedding model | `gemini-embedding-2` |
| Embedding dimensions | `768`; adapter must reject any returned vector with a different length |
| Direct Gemini SDK adapter | **NO**. Spring AI remains the primary adapter path. The transitive Google client may be configured inside the Spring AI adapter/configuration boundary only; application services never call it directly. |

### Executable/source verification

- A disposable Java 21 Maven probe resolved Spring AI 2.0.1 and compiled/reran the Google GenAI embedding options. It passed with:

  ```text
  SPRING_AI_EMBEDDING_OPTIONS_PASS model=gemini-embedding-2 dimensions=768
  ```

- Spring AI 2.0.1 source/bytecode inspection confirmed that an arbitrary model string is accepted and `dimensions(768)` is forwarded as Google `outputDimensionality`.
- The Spring AI model-name enum is older and does not list `gemini-embedding-2`; this is not a blocker because the model endpoint is resolved from the configured string.
- Spring AI 2.0.1 carries a task-type option in its API but the Google request builder does not forward it to the `gemini-embedding-2` request. Google’s current `gemini-embedding-2` documentation says `task_type` is not supported. KBase therefore owns the preparation contract in the embedding adapter:
  - query: `task: question answering | query: {content}` (or the documented search equivalent);
  - document: `title: {title} | text: {content}`; use `title: none` when no title exists.
- Provider request timeout is configured at the Google GenAI client boundary (`HttpOptions` supports a request timeout); M1 owns the Spring AI/client wiring and typed timeout binding. M0’s provisional KBase defaults are connect `PT10S` and request/read `PT60S`.

No Gemini API call, API key, billing, or public network call was used.

---

## 4. M0-03 – pgvector Runtime / JDBC Compatibility

**Status:** DONE

### Locked decision

| Concern | M0 decision |
|---|---|
| PostgreSQL image | `pgvector/pgvector:0.8.6-pg17-bookworm` |
| Observed image digest | `sha256:cf134a767f474095eeba57e0117be8e568e011a63f33fbf252f14c9b760f8e6f` |
| Extension | pgvector 0.8.6; PostgreSQL 17.11 was observed in the probe |
| JDBC library | `com.pgvector:pgvector:0.1.6` |
| Persistence boundary | KBase-owned `JdbcTemplate`/native PostgreSQL SQL repository; Spring AI `VectorStore` does not own the production schema |
| Binding | Call `PGvector.registerTypes(conn)` once per connection and bind a `PGvector` through `PreparedStatement.setObject` |
| Query/index | cosine distance `ORDER BY embedding <=> ?`; HNSW `USING hnsw (embedding vector_cosine_ops)` |

### Executable probe

A disposable Docker probe named `kbase-m0-pgvector-probe-20260922` started the selected image and verified:

1. PostgreSQL 17 startup;
2. `CREATE EXTENSION vector`;
3. a `vector(768)` table;
4. vector inserts;
5. cosine ordering with the expected nearest result (`near = 0.000000`, `far = 0.963916`);
6. HNSW index creation with `vector_cosine_ops`.

The probe emitted `PGVECTOR_PROBE_PASS`, then the temporary container was removed. No production Flyway migration or AI table was created, and no container with the probe name remains.

---

## 5. M0-04 – Extraction Dependency Baseline

**Status:** DONE

Current behavior is MIME detection through `tika-core:3.3.2`; no content-extraction pipeline exists.

M5 is authorized to add these exact Apache Tika 3.3.2 modules:

```text
org.apache.tika:tika-core:3.3.2
org.apache.tika:tika-parser-pdf-module:3.3.2
org.apache.tika:tika-parser-microsoft-module:3.3.2
org.apache.tika:tika-parser-text-module:3.3.2
```

The PDF module covers PDF; the Microsoft module provides the DOC/DOCX and PPT/PPTX parser path (with its transitive Office parser support); the text module covers TXT/MD. M5 must still enforce KBase’s explicit MIME/extension allowlist and may not expose spreadsheets merely because the Microsoft parser module can parse them.

M5 parser safeguards are part of the boundary decision: validate the allowed type and maximum binary size before parsing, bound parser work/resources, preserve source-location metadata when available, and never log raw extracted content. OCR, scanned-document OCR, XLS/XLSX, images, audio, and video/multimodal extraction remain deferred.

A temporary Maven dependency-resolution probe for the four Tika modules passed. No extraction implementation was added.

---

## 6. M0-05 – AI Configuration and Deterministic Test Doubles

**Status:** DONE

The following is the typed configuration baseline for M1. These values are provisional tuning defaults, not production-optimum claims; retrieval threshold and usage ceilings require evaluation in later milestones. Security invariants are intentionally not configuration toggles.

### Configuration contract

| Property family | Environment/key baseline | Default or policy |
|---|---|---|
| Enablement | `KBASE_AI_ENABLED` / `kbase.ai.enabled` | `false` |
| Gemini key | `KBASE_AI_GEMINI_API_KEY` / `kbase.ai.gemini.api-key` | Secret, no repository default/value |
| Chat model | `KBASE_AI_GEMINI_CHAT_MODEL` / `kbase.ai.gemini.chat-model` | `gemini-2.5-flash` |
| Embedding model | `KBASE_AI_GEMINI_EMBEDDING_MODEL` / `kbase.ai.gemini.embedding-model` | `gemini-embedding-2` |
| Embedding dimensions | `KBASE_AI_GEMINI_EMBEDDING_DIMENSIONS` / `kbase.ai.gemini.embedding-dimensions` | `768` |
| Provider timeout | `KBASE_AI_PROVIDER_CONNECT_TIMEOUT`, `KBASE_AI_PROVIDER_REQUEST_TIMEOUT` | `PT10S`, `PT60S` provisional |
| Max user message | `KBASE_AI_MAX_MESSAGE_CHARS` | `8000` chars provisional |
| Chunk target | `KBASE_AI_CHUNK_TARGET_TOKENS` | `700` tokens provisional, within SD-15’s 600–800 range |
| Chunk overlap | `KBASE_AI_CHUNK_OVERLAP_PERCENT` | `12` percent provisional, within SD-15’s 10–15% range |
| Retrieval candidates | `KBASE_AI_RETRIEVAL_CANDIDATE_LIMIT` | `10` provisional, within SD-15’s 8–12 range |
| Final context | `KBASE_AI_RETRIEVAL_FINAL_CONTEXT_LIMIT` | `6` chunks provisional, within SD-15’s 5–8 range |
| Similarity threshold | `KBASE_AI_RETRIEVAL_SIMILARITY_THRESHOLD` | Configurable; no numeric production default is locked in M0; M10 evaluation must determine it |
| Worker poll/batch | `KBASE_AI_WORKER_POLL_INTERVAL`, `KBASE_AI_WORKER_BATCH_SIZE` | `PT5S`, `10` provisional |
| Worker lease/retry | `KBASE_AI_WORKER_LEASE_TIMEOUT`, `KBASE_AI_WORKER_MAX_ATTEMPTS` | `PT2M`, `3` provisional; backoff remains configurable and is finalized with M3 failure classification |
| Conversation retention | `KBASE_AI_RETENTION` | `P7D` (product rule; not shortened by tuning) |
| Usage/rate guard | `KBASE_AI_USAGE_*`; Redis namespace `kbase:ai:rate` | Per-user configurable AI-only guard; exact windows/ceilings are M10 decisions. Redis loss may reset counters but must not affect Core endpoints or durable AI state. |

Never add switches for project filtering, strict grounding, conversation privacy, or authorization re-checks. Those are mandatory behavior.

### Deterministic fake contracts

- `FakeAiChatModel` (implementation in M1 test support): returns a deterministic response derived from a canonical request digest or a test-supplied fixture; captures immutable requests; exposes invocation count; supports controlled provider failure, unavailable, and timeout behavior; performs no network access.
- `FakeAiEmbeddingModel`: returns exactly 768 normalized dimensions; records query/document mode; uses a seeded label-to-vector codebook so tests can deliberately make one document nearer than another; unknown text falls back to a stable SHA-256-derived vector, padded/truncated and normalized; no API key or network is involved.
- Both fakes must be injectable behind KBase-owned ports, must not leak Spring AI/Google types into application code, and must be usable in normal automated tests and Docker smoke without Gemini credentials.

Full fake implementation belongs to M1; M0 locks the contract only.

---

## 7. M0-06 – Harness and Source Revalidation

**Status:** DONE

Validation passed for `.harness/source-doc-registry.json`:

- `sourceDocumentCount = 19`;
- SD-01 through SD-19 are present and every registered path exists;
- SD-14 through SD-19 point to the AI product, architecture, persistence, REST, testing, and implementation sources;
- `activeRelease = AI v1`, `coreV1Frozen = true`, `implementationMode = backend-only`, and frontend is optional/deferred;
- precedence keeps SD-14 as AI product authority, SD-15/16 as AI architecture/persistence authority, SD-17 as API target, SD-18 as verification authority, and SD-19 as implementation order only.

`docs/exec-plans/active/index.md` pointed to this M0 slice during execution. Repository search found only valid historical Core statements such as “AI/RAG out of scope for Core v1”; no current routing statement incorrectly blocked AI v1.

---

## 8. M0 Gate

```text
PASS  M0-01 Core baseline verified: 213/213; Compose config PASS
PASS  M0-02 Spring AI/Gemini versions, models, 768 dimensions, timeout boundary, and SDK decision locked
PASS  M0-03 PostgreSQL 17 + pgvector image, extension, vector(768), cosine, HNSW, and JDBC boundary verified
PASS  M0-04 Tika parser modules and deferred extraction scope locked
PASS  M0-05 typed configuration baseline and deterministic fake contracts locked
PASS  M0-06 harness registry, precedence, active release, and Core freeze revalidated
PASS  no unresolved source or technical compatibility blocker
PASS  no product behavior changed
PASS  no AI feature implementation leaked into M0
```

Generated DB/API snapshots are unchanged because M0 created no schema or API source of truth. The repository is ready for M1; M1 must add only the compile-safe foundation and provider ports described in its active slice.

---

## 9. Progress Log

| Date | Task | Status | Evidence |
|---|---|---|---|
| 2026-09-22 | M0-01 | DONE | `feat-AI`, clean starting worktree, `mvn -B -ntp clean verify` 213/213, Compose config PASS |
| 2026-09-22 | M0-02 | DONE | Spring AI 2.0.1 options probe PASS; model/dimension/task-prefix and direct-SDK decision recorded |
| 2026-09-22 | M0-03 | DONE | pgvector Docker probe PASS; image, digest, extension, cosine, HNSW, and JDBC binding recorded |
| 2026-09-22 | M0-04 | DONE | Tika 3.3.2 dependency-resolution probe PASS; parser scope and guards recorded |
| 2026-09-22 | M0-05 | DONE | Typed configuration and deterministic fake contracts recorded; no secret or network call |
| 2026-09-22 | M0-06 | DONE | Registry/path/scope/precedence checks PASS; no stale current blocker |
| 2026-09-22 | M0 Gate | PASS | M0 completed; M1 active slice created as READY |

---

## 10. Decision Log and official references

Primary references checked for the decisions above:

- Spring AI getting started: <https://docs.spring.io/spring-ai/reference/getting-started.html>
- Spring AI Google GenAI chat: <https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html>
- Spring AI Google GenAI embeddings: <https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html>
- Spring AI releases: <https://github.com/spring-projects/spring-ai/releases>
- Google embeddings guide: <https://ai.google.dev/gemini-api/docs/embeddings>
- Google `gemini-embedding-2`: <https://ai.google.dev/gemini-api/docs/models/gemini-embedding-2>
- Google `gemini-2.5-flash`: <https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash>
- pgvector project: <https://github.com/pgvector/pgvector>
- pgvector image tags: <https://hub.docker.com/r/pgvector/pgvector/tags>
- pgvector Java binding: <https://github.com/pgvector/pgvector-java>
- Apache Tika downloads: <https://tika.apache.org/download>
- Apache Tika 3.3.2: <https://tika.apache.org/3.3.2/index.html>
- Apache Tika Maven artifacts: <https://repo1.maven.org/maven2/org/apache/tika/>

No product spec, Core invariant, migration, or public API was changed to accommodate a library. Any future incompatibility must be recorded against this decision log and resolved through the AI source-of-truth process.
