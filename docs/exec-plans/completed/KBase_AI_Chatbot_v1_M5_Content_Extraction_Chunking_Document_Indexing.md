# KBase AI Chatbot v1 – M5 Content Extraction / Chunking / Document Indexing

**Status:** DONE  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1, M2, M3 and M4 AI slices  
**Scope:** supported-document extraction, deterministic structure-aware chunking and durable document indexing through the existing Core storage/provider boundaries.  
**Completed:** 2026-09-23  
**Current step:** M5 gate complete; handoff to M6 semantic retrieval/grounding/citations

### Final gate evidence (2026-09-23)

The repository was verified on `feat-AI`. The required full gate completed with Docker/Testcontainers available: `mvn -B -ntp clean verify` reported `BUILD SUCCESS`, `307` tests, `0` failures, `0` errors and `0` skipped. `docker compose -f docker-compose.yml config --quiet` and `git diff --check` also passed. The targeted M5 suite reported `17/17` across extraction/chunking, handler, application retry/status and PostgreSQL persistence tests.

## 1. Goal and dependency gate

Make supported Core documents progress from the M3 `PENDING` document-index intent to an atomically activated, project-scoped READY index without changing the Core upload contract or opening retrieval/conversation/API behavior.

Dependency gate: PASS by handoff. M4 completed the provider boundary with full regression `290/290`, deterministic adapter tests `22/22`, strict `768` output validation, safe provider categories and disabled-by-default configuration. M5 may use the KBase-owned `AiEmbeddingModel` port but must not import Spring AI/Google GenAI types outside the existing provider adapter package.

## 2. Source of truth and required documents

- `docs/product-specs/KBase - AI Chatbot v1 Specification.md`;
- `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`;
- `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md`;
- completed M0–M4 plans;
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md` and `docs/DEPLOYMENT.md`;
- `docs/DEVELOPMENT.md` for bootstrap and verification commands.

If the source documents conflict with this handoff, stop and update the plan before coding. Do not reinterpret Core v1 or M0–M4 decisions.

## 3. Approved M5 tasks

### AI-IDX-01 – Extraction port and source-location model

Define the smallest KBase-owned extraction boundary and a source-location model that preserves document/page/slide/section provenance needed by later citation work. Do not expose parser-library types to application/domain code.

### AI-IDX-02 – Format extraction adapters

Support exactly PDF, DOC, DOCX, PPT, PPTX, MD and TXT using the locked Tika/parser dependency set. Unsupported Core-accepted formats remain `UNSUPPORTED` and must not invoke extraction or embedding. Do not add OCR, image, video or spreadsheet extraction.

### AI-IDX-03 – Structure-aware chunker

Implement a deterministic, versioned chunking baseline of approximately 600–800 tokens with approximately 10–15% overlap, preserving page/slide/section metadata. Define behavior for empty, corrupt and no-text input. Chunking must not mutate the persisted source document content.

### AI-IDX-04 – `DOCUMENT_INDEX` handler

Implement the durable handler behind the M3 scheduler registry. Read source bytes only through `StorageService`, hash/validate the source, extract, chunk, call the KBase `AiEmbeddingModel` with semantic `DOCUMENT` requests, and persist a staging index version before atomic activation. The handler must check document/project liveness and prevent late-worker resurrection after delete.

### AI-IDX-05 – Retry and failure classification

Map transient provider/storage failures to bounded retry using M4 retryable categories. Map unsupported, corrupt, empty/no-text and permanent failures to safe terminal state/reason codes. Do not store raw exception messages, prompts, chunks, vectors, credentials or provider bodies in durable job/error state.

### AI-IDX-06 – Index status and manual retry application boundary

Add only the application/service behavior required to read index status and request a bounded manual retry. No synchronous reindex in upload, no public AI REST endpoint unless a separately approved contract slice exists, and no retrieval/conversation behavior.

## 4. Boundaries and invariants

- Core upload remains provider-independent and must not call Tika, embeddings or Gemini synchronously.
- `StorageService` is the only binary-read boundary; no MinIO SDK import may appear in extraction/indexing code.
- `AiEmbeddingModel` and M4 provider-neutral models remain the only embedding dependency for M5.
- Index writes are project/document scoped and must use the existing V4 schema/FK/status rules; update generated DB docs only if a verified schema change is approved.
- Staging activation must be atomic and must retain the last active successful version until replacement activation succeeds.
- Document/project deletion and late worker completion must not resurrect chunks or vectors.
- Jobs execute outside the short claim transaction; lease token and idempotency rules from M3 remain authoritative.
- Provider, storage and parser failures are translated to safe categories; raw external payloads never enter logs or durable state.
- AI disabled remains healthy and must not require a Gemini key. M5 automated tests use deterministic fakes/mocks; no public Gemini network or real credential.

## 5. Explicitly out of scope

- semantic retrieval, project query authorization, grounding, citation validation or no-evidence chat policy (M6);
- Project Assistant, Guide runtime, conversations, REST/OpenAPI/frontend/streaming (M7+);
- OCR, multimodal, spreadsheet, image or video extraction;
- direct Google SDK use, provider-specific types outside the M4 adapter boundary;
- destructive conversation purge or membership-retention behavior beyond the existing M3 intent;
- changing Core v1 API behavior or frozen migrations without an approved design defect.

## 6. Required verification

Before implementation, inspect the resolved Tika/parser APIs and existing V4 chunk/index entities/repositories. Then add deterministic tests for:

- each supported format and source-location metadata;
- unsupported format no-extraction/no-embedding behavior;
- deterministic chunk boundaries, overlap and version;
- corrupt/empty/no-text safe terminal outcomes;
- staging/activation idempotency and last-active preservation;
- bounded retry classification and safe durable error state;
- delete/late-worker no-resurrection and duplicate delivery;
- storage access only through `StorageService`;
- disabled-AI startup and no provider network dependency.

Required gate commands, after targeted tests:

```text
mvn -B -ntp clean verify
docker compose -f docker-compose.yml config --quiet
git diff --check
```

Use PostgreSQL 17.11/pgvector Testcontainers for migration, transaction, FK, vector and activation behavior. Run the full Core regression; do not disable unrelated tests to make M5 pass.

## 7. Generated documentation and rollback

M5 should use the existing V4 schema. If a verified design-approved schema change becomes necessary, update Flyway and regenerate `docs/generated/db-schema.md` in the same slice. M5 does not add public endpoints, so `docs/generated/api-schema.md` must remain unchanged unless an explicitly approved API slice is added.

Rollback is application/provider disable first. Do not drop V4 tables, chunks or the vector extension automatically. Preserve durable job state and document Core behavior when the provider is unavailable.

## 8. Risks and open decisions

- Tokenization and source-location normalization must follow SD-15/SD-18; do not choose a new chunking contract from intuition.
- Parser behavior for malformed files must be bounded and deterministic; fixture evidence is required before marking the gate complete.
- Provider connect-timeout remains an M4 tracked limitation; M5 must not claim it is solved.
- Exact status/error reason names must remain compatible with the existing V4 model and future M6/M7 contracts.

Open decisions to resolve before implementation: final token-counting mechanism, parser-to-source-location mapping for each supported format, and exact terminal reason codes. Record decisions in this plan and relevant design docs before changing behavior.

### Resolved M5 decisions (2026-09-22)

- Token counting uses the KBase-owned deterministic `kbase-lex-v1` estimator. Unicode letter/mark/number runs count as one token; each non-whitespace punctuation/symbol code point counts as one token. It is provider-independent and deterministic for Vietnamese and English. Persisted `token_count` is diagnostic metadata from this estimator and is not claimed to be Gemini-exact tokenization.
- Source locations use parser evidence only: PDF SAX `div.page` boundaries map to 1-based `page_number`; PPT/PPTX SAX `div.slide-content` boundaries map to 1-based `slide_number`; DOC/DOCX preserve `section_title` only when an explicit heading/style signal is emitted, otherwise it is `NULL`; MD uses a KBase-owned deterministic ATX/setext heading parser and maintains the heading path/title; TXT has `section_title = NULL`. Unknown or unproven fields remain `NULL`, and chunks never merge across distinct known pages/slides.
- Terminal `DocumentAiIndex.failureReason` values are `UNSUPPORTED_FILE_TYPE`, `EXTRACTION_FAILED`, `AI_SERVICE_ERROR` and `PROCESSING_ERROR`. Operational job/index error codes use normalized uppercase values such as `AI_PROVIDER_TIMEOUT`, `AI_PROVIDER_RATE_LIMITED`, `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_CONFIGURATION`, `AI_PROVIDER_INVALID_RESPONSE`, `STORAGE_UNAVAILABLE`, `STORAGE_NOT_FOUND`, `EXTRACTION_FAILED` and `PROCESSING_ERROR`. Raw exception messages, parser/provider bodies, prompts, document content, vectors, credentials and unnecessary filenames are never persisted or logged.

## 9. Handoff acceptance checklist

- [x] extraction and chunking ports stay KBase-owned;
- [x] exactly PDF/DOC/DOCX/PPT/PPTX/MD/TXT are supported;
- [x] unsupported/corrupt/no-text behavior is safe and tested;
- [x] chunks preserve source location and deterministic versioned boundaries;
- [x] `DOCUMENT_INDEX` runs durably outside the claim transaction;
- [x] `StorageService` is the only binary-read dependency;
- [x] staging activation, idempotency and delete races are verified on PostgreSQL/pgvector;
- [x] retries use bounded provider-neutral categories and safe durable state;
- [x] no M6+ retrieval, grounding, conversation, Guide or public API behavior is implemented;
- [x] full regression, Compose config, diff check and scope audit pass;
- [x] `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md` and this plan are updated with executable evidence before completion.

## 10. Progress and task evidence

| Date | Task | Status | Evidence |
|---|---|---|---|
| 2026-09-22 | Dependency and source-of-truth gate | DONE | M0–M4 completed plans, AI product/design/testing documents and existing V4 repositories/configuration were reloaded before implementation. |
| 2026-09-22 | AI-IDX-01 | DONE | KBase-owned extraction request/result/block/location models and extractor port added; parser-library types remain behind the Tika adapter. |
| 2026-09-22 | AI-IDX-02 | DONE | Tika adapter supports exactly PDF, DOC, DOCX, PPT, PPTX, MD and TXT; unsupported Core file types do not invoke extraction or embedding. |
| 2026-09-22 | AI-IDX-03 | DONE | `kbase-lex-v1` token estimator and `chunk-v1` structure-aware chunker added with 700-token target, 12% overlap, bounded input and deterministic location/hash metadata. |
| 2026-09-23 | AI-IDX-04 | DONE | `DOCUMENT_INDEX` handler reads through `StorageService`, bounds and hashes source bytes, extracts/chunks/embeds outside the claim transaction, stages rows and atomically activates the ready version. |
| 2026-09-23 | AI-IDX-05 | DONE | Provider/storage retry categories, bounded exhaustion and safe terminal reasons implemented; raw exception text, content, vectors, prompts, credentials and provider bodies are not durable state. |
| 2026-09-23 | AI-IDX-06 | DONE | Internal index status/manual-retry application boundary added with pessimistic locking; no public REST endpoint or synchronous upload reindex was added. |

### AI-IDX-01 / AI-IDX-02 — extraction boundary and format behavior

- Files: `src/main/java/com/kbase/ai/extraction/`, `src/main/java/com/kbase/ai/extraction/tika/TikaDocumentContentExtractor.java`, `src/main/java/com/kbase/ai/service/AiDocumentSupportPolicy.java`.
- PDF page and PPT/PPTX slide locations are derived only from proven SAX boundaries; DOC/DOCX section titles require explicit heading/style evidence; Markdown heading paths are deterministic; unknown TXT locations remain null.
- Empty, malformed and no-text inputs become safe extraction failures. OCR, image, video and spreadsheet extraction remain out of scope.

### AI-IDX-03 — deterministic chunking

- Files: `KBaseLexTokenCounter`, `StructureAwareDocumentChunker`, `MarkdownStructureParser`, `ChunkingVersions`, `DocumentChunk` and source-location models.
- The persisted diagnostic token count uses deterministic `kbase-lex-v1`; it is not claimed to be Gemini-exact. Chunking uses shared `chunk-v1`, keeps known page/slide/section boundaries, uses 700 tokens and 12% overlap by default, and never mutates Core source content.

### AI-IDX-04 / AI-IDX-05 — durable indexing and safe failure

- Files: `DocumentIndexJobHandler`, `DocumentAiIndexPersistenceService`, `BoundedDigestInputStream`, `AiVectorRepository`, `DocumentAiIndexRepository`, `AiJobExecutionOutcome` and handler registry wiring.
- PostgreSQL staging/activation locks the document, current index and lease in a short transaction; replacement failure leaves the last READY version active; repeated activation is harmless; deleted documents/projects and stale lease owners cannot resurrect rows.
- Source size and digest are checked against Core metadata; embedding results must be finite and exactly 768 dimensions. Transient provider/storage categories use bounded `RETRY`; unsupported, extraction, invalid-provider-response and processing failures use safe terminal state/reason values.

### AI-IDX-06 — internal status and retry boundary

- Files: `DocumentAiIndexApplicationService`, `DocumentAiIndexStatusView`, `AiIndexRetryNotAllowedException` and the pessimistic-lock repository query.
- Manual retry is allowed only from `FAILED`, preserves/reuses the existing desired version for the failed attempt and is idempotently bounded. `PENDING`, `PROCESSING`, `READY` and `UNSUPPORTED` are rejected without exposing persistence entities or adding an API contract.

## 11. Verification evidence

| Command or check | Result | Coverage |
|---|---|---|
| `mvn -B -ntp "-Dtest=DocumentExtractionAndChunkingTest,DocumentIndexJobHandlerTest,DocumentAiIndexApplicationServiceTest,DocumentAiIndexPersistenceIntegrationTest" test` | PASS — `17` tests, `0` failures, `0` errors, `0` skipped | Supported-format/location extraction, deterministic boundaries, empty/corrupt behavior, unsupported no-op, partial embedding failure, retry exhaustion, stale lease, duplicate activation and document/project deletion races. |
| `mvn -B -ntp clean verify` | PASS — `BUILD SUCCESS`; `307` tests, `0` failures, `0` errors, `0` skipped | Full Core + AI regression, compile, package and Spring Boot repackage. PostgreSQL/Testcontainers integration suites ran successfully. |
| `docker compose -f docker-compose.yml config --quiet` | PASS | Compose topology/config remains valid; M5 adds no service, volume or migration change. |
| `git diff --check` | PASS | No whitespace errors. |
| Static boundary/privacy audit | PASS | No MinIO SDK import in `com.kbase.ai`; Tika is confined to the extraction adapter; Spring AI/Google GenAI imports remain in `com.kbase.ai.provider.springai`; no public AI controller, retrieval, conversation, Guide or frontend behavior; generated DB/API docs unchanged. |

M5 automated verification uses deterministic extractor/embedding doubles and PostgreSQL 17.11/pgvector Testcontainers where persistence behavior is under test. Real Gemini credential/network connectivity and a full Compose indexing journey with a live provider were intentionally not run. Hikari/Testcontainers connection-refused warnings observed during JVM shutdown occurred after the successful Surefire result and did not change the `307/307` gate.

## 12. Known limitations and handoff

- The worker claim lease is fixed for the execution and is not renewed while a long extraction or multi-chunk embedding call is in progress. The configured default is two minutes; a future transport/worker hardening slice should add heartbeat/lease sizing evidence for worst-case provider latency. This is recorded in `docs/exec-plans/tech-debt-tracker.md` and is not a reason to weaken stale-lease protection.
- Exact provider tokenization is intentionally not promised; reindexing after a future tokenizer/chunker strategy change must use a new version string.
- M6 must build retrieval on active READY versions only, enforce the project predicate in SQL, implement strict no-evidence behavior and map citations from backend-owned chunk metadata. M6 is planning-only at handoff.

## 13. Completion report

**Task:** Implement M5 — Content Extraction / Chunking / Document Indexing  
**Status:** DONE  
**Core business rules changed:** NO  
**Public REST/API contract changed:** NO  
**Flyway/generated DB/API docs changed:** NO  
**Next active slice:** `KBase_AI_Chatbot_v1_M6_Semantic_Retrieval_Grounding_Citations.md`
