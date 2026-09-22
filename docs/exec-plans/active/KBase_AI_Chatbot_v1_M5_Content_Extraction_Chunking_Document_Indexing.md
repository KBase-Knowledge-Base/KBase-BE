# KBase AI Chatbot v1 – M5 Content Extraction / Chunking / Document Indexing

**Status:** READY  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1, M2, M3 and M4 AI slices  
**Scope:** supported-document extraction, deterministic structure-aware chunking and durable document indexing through the existing Core storage/provider boundaries.  
**Current step:** dependency handoff; implementation not started

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

## 9. Handoff acceptance checklist

- [ ] extraction and chunking ports stay KBase-owned;
- [ ] exactly PDF/DOC/DOCX/PPT/PPTX/MD/TXT are supported;
- [ ] unsupported/corrupt/no-text behavior is safe and tested;
- [ ] chunks preserve source location and deterministic versioned boundaries;
- [ ] `DOCUMENT_INDEX` runs durably outside the claim transaction;
- [ ] `StorageService` is the only binary-read dependency;
- [ ] staging activation, idempotency and delete races are verified on PostgreSQL/pgvector;
- [ ] retries use bounded provider-neutral categories and safe durable state;
- [ ] no M6+ retrieval, grounding, conversation, Guide or public API behavior is implemented;
- [ ] full regression, Compose config, diff check and scope audit pass;
- [ ] `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md` and this plan are updated with executable evidence before completion.
