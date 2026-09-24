# KBase AI Chatbot v1 – M6 Semantic Retrieval / Grounding / Citations

**Status:** DONE – M6 Gate PASS
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1, M2, M3, M4 and M5 AI slices  
**Scope:** provider-neutral query retrieval, strict evidence selection, grounded prompt construction, source-label validation and citation mapping behind an application boundary.  
**Current step:** completed; handoff to M7 planning only

## M6 decisions (locked before implementation, 2026-09-23)

- Similarity is `1 - (embedding <=> queryVector)`; higher is better. Configured threshold is an inclusive minimum (`similarity >= threshold`). A null threshold applies no numeric filter. The configured candidate limit (default 10) bounds SQL rows; the configured final-context limit (default 6) bounds constituent chunks sent to chat, including merged blocks.
- Suppress duplicates by `(documentId, contentHash)`, retaining the first/highest-ranked candidate. Maintain SQL relevance order, with chunk ID as a stable tie-breaker. Greedily merge a later candidate only into an earlier block with the same project/document/version, compatible known page/slide/section, and an immediately contiguous chunk index. Preserve all constituent rows and their original ranks. Preserve full chunk text separated by newlines: removing an apparent overlap without provenance could corrupt evidence.
- Retain at most the most recent eight USER/ASSISTANT turns totaling at most 4,000 characters; remove oldest whole turns first and preserve chronological order. Exclude SYSTEM and any oversized single turn. Use the retained turns only as delimited query continuity and prompt conversation, never as evidence.
- Internal result is `(AiAnswerType, text, modelId, ordered cited EvidenceSource list)`; no public DTO, vector, hash, URL or storage key. `NO_EVIDENCE` has the fixed Vietnamese refusal, empty sources and zero chat calls.
- Labels are exact `SOURCE_1` etc. Parse bracketed references in first-mention order, deduplicate repeated labels and reject the entire generated response as `AiProviderException(INVALID_RESPONSE)` if any reference is unknown/malformed or if no valid label is present. Only cited backend-owned blocks contribute source rows; merged blocks contribute every constituent chunk. Citation `source_order` is zero-based and follows validated first-reference order, then constituent rank order; duplicate chunk IDs are emitted once.
- Citation mapper accepts a caller-supplied existing assistant message ID and copies document name/location/score from selected backend rows. It creates no message and no durable state itself. After live document deletion, nullable document/chunk FKs become null while snapshot fields survive; availability requires live FK plus current document authorization at future source-open time.

## M6 execution log (2026-09-23)

| Task | Status | Implementation and executable evidence |
|---|---|---|
| AI-RAG-01 | DONE, targeted | `ProjectEvidenceRetriever` authorizes before `QUERY` embedding, validates 768 finite float values, then calls project-scoped `AiVectorRepository` with configured candidate limit. SQL retains `project_id`, READY/active-version predicates and joins current `documents` for display name. Real pgvector Project B closer-vector trap and document/version/status tests pass. |
| AI-RAG-02 | DONE, targeted | `EvidenceSelector` uses inclusive minimum similarity when configured, `(documentId, contentHash)` dedup, forward contiguous/compatible adjacent merge with all source identities, deterministic rank order and final limit counted as constituent chunks. Unit and real pgvector top-K/score tests pass. |
| AI-RAG-03 | DONE, targeted | `ProjectRagService` returns backend-owned fixed Vietnamese refusal, `NO_EVIDENCE`, empty sources and zero chat calls after a current-access recheck. History-only and threshold-filtered paths pass. |
| AI-RAG-04 | DONE, targeted | `ConversationContextPolicy` keeps latest eight whole USER/ASSISTANT turns within 4,000 characters; query is delimited when context is present. `GroundedPromptBuilder` keeps system policy, conversation, evidence and current question in separate `AiChatRequest` fields. Real cross-project injection trap and captured fake request pass. |
| AI-RAG-05 | DONE, targeted | `AiChatModel` runs only with selected evidence and a pre-chat authorization recheck. Exact `[SOURCE_n]` references are validated; any unknown/malformed or zero-label response is `INVALID_RESPONSE`, never `NO_EVIDENCE`. Access is rechecked after chat; unit and real membership-removal while fake chat blocked pass. Provider errors propagate unchanged. |
| AI-RAG-06 | DONE, targeted | `CitationSnapshotMapper` maps only validated constituent `EvidenceSource` rows into `AiMessageSource` for a caller-supplied assistant ID. Zero-based order, current document name, location and score are copied from backend rows. Real PostgreSQL save/delete test proves live document/chunk FKs become NULL and historical snapshot survives. No URL/storage key is mapped. |

Targeted command: `mvn -B -ntp "-Dtest=EvidenceSelectorTest,SourceLabelAndCitationTest,ConversationContextPolicyTest,ProjectRagServiceTest,AiPersistenceIntegrationTest" test` → `BUILD SUCCESS`, 33 tests, 0 failures/errors/skips (17 new unit and 16 real PostgreSQL/pgvector integration tests, including five new M6 integration cases). Initial M5 baseline `mvn -B -ntp clean verify` → `BUILD SUCCESS`, 307/307. `mvn -B -ntp -DskipTests compile`, `docker compose -f docker-compose.yml config --quiet` and `git diff --check` pass. Final `mvn -B -ntp clean verify` → `BUILD SUCCESS`, 329 tests, 0 failures, 0 errors, 0 skipped; package/repackage pass. No real Gemini key or public Gemini network was required or used. Shutdown-only Testcontainers/Hikari connection warnings appeared after passing tests and did not affect Surefire result.

Final static/scope audit: no new controller/API/OpenAPI contract, conversation runtime, Guide runtime, retention purge, frontend, streaming, schema migration or generated DB/API change. New M6 application code has no provider SDK imports, content logging, vector/result leakage, storage key or URL mapping. Provider work occurs outside a service transaction. The conservative adjacent merge preserves full overlapping chunk text; a later evaluated overlap-trimming rule may reduce prompt duplication without risking content corruption. No new blocker or technical debt was opened by M6.

## 1. Goal and dependency gate

Implement the evidence pipeline required before the private conversation API is exposed. A project question must be authorized, embedded as a `QUERY`, searched only against current active READY chunks for that project, and either produce valid backend-owned evidence or a deterministic `NO_EVIDENCE` result. Provider output and citation labels must never become an authorization boundary.

Dependency gate: PASS by completed M5 plan. M5 provides deterministic extraction/chunk metadata, active-version staging/activation, the KBase-owned `AiEmbeddingModel`/`AiChatModel` ports, and safe PostgreSQL/pgvector lifecycle behavior. Core v1 and Flyway V1–V4 remain frozen unless a separately approved design defect is proven.

## 2. Source of truth and required documents

- `docs/product-specs/KBase - AI Chatbot v1 Specification.md`;
- `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`;
- `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md`;
- completed M0–M5 plans;
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md` and `docs/DEVELOPMENT.md`.

If these sources conflict, stop and resolve the conflict in this plan before coding. Do not reinterpret Core v1, M0–M5 decisions or the product's strict grounding rules.

## 3. Approved M6 tasks

### AI-RAG-01 – Query embedding and project-scoped SQL retrieval

Use the KBase `AiEmbeddingModel` with semantic `QUERY` preparation. Authorize project access before retrieval and query `document_ai_chunks` through the KBase repository with mandatory `project_id`, `active_version`, `READY` and current-document predicates in SQL. Never retrieve globally and filter in Java.

### AI-RAG-02 – Evidence selection

Add configurable candidate limit, optional similarity threshold, duplicate suppression and bounded adjacent-chunk merging. Preserve document/chunk identity, rank, score and page/slide/section metadata. Only active READY generations can enter final evidence.

### AI-RAG-03 – Strict no-evidence policy

If no candidate survives authorization, active-version, threshold and selection rules, return a deterministic `NO_EVIDENCE` domain result with no sources and make zero chat-provider calls. Do not let conversation history or general model knowledge fill the gap.

### AI-RAG-04 – Grounded prompt builder

Build provider-neutral prompt input with separate system instructions, bounded non-authoritative conversation context, labelled retrieved evidence and the current question. Mark retrieved text as untrusted data and explicitly prohibit following instructions found inside evidence.

### AI-RAG-05 – Chat generation and source-label validation

For usable evidence, call only the KBase `AiChatModel`. Map internal labels such as `[SOURCE_1]` to the exact retrieved chunk set. Reject, ignore or safely downgrade invented/unknown labels; provider output must not introduce document IDs, locations or authorization decisions.

### AI-RAG-06 – Citation snapshot mapper

Map valid backend-selected sources to the existing citation persistence model using live document/chunk foreign keys plus immutable snapshots and retrieval diagnostics. Preserve source order and availability semantics for later conversation/API work. Do not expose source URLs or bypass document authorization.

## 4. Boundaries and invariants

- No public REST endpoint, conversation CRUD, generation concurrency/quota API, Guide runtime or frontend/streaming behavior is opened in M6; those belong to later slices.
- Project authorization occurs before embedding/retrieval, and current access is rechecked before a completed grounded result or citation state is returned/persisted.
- Retrieval SQL must constrain `project_id` and the active successful index version. Deleted, inactive, unsupported, failed or stale staged chunks are not evidence.
- Query embedding and document embedding remain distinct KBase semantic requests; embedding output must remain exactly 768 finite dimensions.
- Conversation history is context only. It cannot substitute for current retrieved project evidence.
- Evidence is untrusted document data. Prompt injection inside a chunk cannot change retrieval, authorization, source mapping or persistence behavior.
- No-evidence is an application/domain outcome and must short-circuit the chat provider.
- Citation metadata comes from the selected backend rows, never from free-form model claims.
- Provider/network failures are translated through existing safe categories; raw prompts, evidence, vectors, provider bodies and credentials are not logged or persisted.
- Network calls occur outside database transactions where possible; persistence uses short, explicit transaction boundaries.

## 5. Explicitly out of scope

- Project Assistant conversation creation/list/rename/delete, message persistence and REST/OpenAPI contract;
- five-conversation quota, one-active-generation enforcement and membership retention/purge execution;
- KBase Guide indexing/retrieval/runtime;
- OCR, multimodal, spreadsheet, image, video or attachment chat;
- changing Core upload/search/download behavior;
- direct Google SDK use outside the existing provider adapter boundary;
- migration changes without a verified schema gap and design approval;
- real Gemini credential/network smoke as a normal automated gate.

## 6. Required verification

Use deterministic fake chat/embedding models for normal automated tests and real PostgreSQL 17.11/pgvector Testcontainers for retrieval, transaction and citation lifecycle behavior. Add tests for:

- Project B's closer vector never appearing in Project A candidates/context/source;
- inactive, failed, staged and deleted document generations never being retrieved;
- unauthorized/non-member access denied before embedding or SQL retrieval;
- no usable evidence producing deterministic `NO_EVIDENCE` and zero chat calls;
- candidate threshold, duplicate suppression, adjacent merge, rank/score and location preservation;
- prompt sections/order, untrusted evidence treatment and prompt-injection resistance;
- valid source labels mapping only to retrieved chunks, with invented labels rejected safely;
- ordered citations with live FK plus snapshot fields, including document/chunk deletion and current authorization semantics;
- membership/access recheck preventing a completed result after in-flight authorization loss;
- disabled AI startup and provider/storage privacy boundaries remaining intact.

Required final commands, after targeted tests:

```text
mvn -B -ntp clean verify
docker compose -f docker-compose.yml config --quiet
git diff --check
```

Do not disable unrelated Core tests to make M6 pass. No generated DB/API documentation may be changed unless source truth actually changes and the corresponding generation/catalog verification is run.

## 7. Persistence and generated documentation guard

M6 should use the existing V4 `document_ai_chunks`, `document_ai_indexes` and `ai_message_sources` schema. The citation design already provides nullable live document/chunk FKs, immutable document/location snapshots and retrieval score/order fields. If implementation discovers a mismatch, stop, document the design gap and obtain an approved migration plan before changing Flyway or `docs/generated/db-schema.md`.

M6 adds no public endpoint, so `docs/generated/api-schema.md` must remain unchanged. Internal application results are not an API contract.

## 8. Risks and decisions to resolve before implementation

- Select and record the exact similarity direction/threshold semantics against the existing cosine SQL contract; do not mix distance and similarity silently.
- Define deterministic adjacent-merge rules that preserve source order and do not cross document or known page/slide boundaries.
- Define the structured chat result shape and unknown-source-label policy before wiring `AiChatModel`; keep invalid provider output safe and category-only.
- Bound conversational context independently from authoritative evidence and document the token/character budget.
- Decide how the source mapper represents a deleted live document while retaining historical snapshots; align with the existing `ai_message_sources` FK/availability design.
- Revisit M5's fixed worker lease only if M6 introduces a long-running retrieval/generation worker; the M5 lease-renewal limitation is already tracked as technical debt.

## 9. Handoff acceptance checklist

- [x] query embedding uses KBase-owned `QUERY` semantics;
- [x] retrieval SQL is project-scoped and active-READY-version scoped;
- [x] unauthorized, deleted, failed and inactive knowledge cannot enter evidence;
- [x] candidate selection is deterministic, bounded and metadata-preserving;
- [x] no-evidence returns deterministic `NO_EVIDENCE` without a chat-provider call;
- [x] prompt builder separates system/history/evidence/question and treats evidence as untrusted;
- [x] only backend-owned valid source labels become citations;
- [x] citation snapshots and live-FK deletion semantics are verified;
- [x] in-flight authorization loss cannot return/persist a completed result;
- [x] no M7+ conversation/API/Guide/frontend behavior is implemented;
- [x] real pgvector semantic trap, full regression, Compose config, diff check and scope audit pass;
- [x] `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, living reliability/integration/testing docs and this plan are updated with executable evidence.

## 10. Completion rule

Do not mark M6 `DONE` from code inspection or unit tests alone. The gate requires the real pgvector cross-project semantic trap, no-evidence zero-call assertion, prompt-injection negative test, citation deletion/authorization tests, full Core regression and updated repository handoff documents.
