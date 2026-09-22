# KBase AI Chatbot v1 – M4 Gemini Provider Adapters

**Status:** DONE  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1, M2 and M3 AI slices  
**Scope:** provider-neutral chat/embedding adapter implementation, safe provider error mapping, privacy/logging guards and adapter contract tests.  
**Completed:** 2026-09-22

## 1. Source of truth and dependency gate

Required sources for M4:

- `docs/product-specs/KBase - AI Chatbot v1 Specification.md`;
- `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`;
- `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md`;
- completed M0/M1/M2 plans and `KBase_AI_Chatbot_v1_M3_Durable_Job_Engine_Core_Lifecycle.md`;
- `docs/BACKEND.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md` and `docs/DEPLOYMENT.md`.

Dependency gate: PASS. M0–M3 are complete and authoritative. M3 full regression was 268/268 before M4. Core v1 and Flyway V1–V4 remain frozen/unchanged.

Locked provider baseline:

- Spring AI BOM: `2.0.1`;
- Google GenAI client: `1.65.0` transitively through the approved Spring AI starters;
- chat model: `gemini-2.5-flash`;
- embedding model: `gemini-embedding-2`;
- embedding output: exactly `768` dimensions.

## 2. Approved M4 tasks and result

### AI-PROV-01 – Spring AI Gemini chat adapter — PASS

Implemented `SpringAiGeminiChatAdapter` in `com.kbase.ai.provider.springai`. It implements the KBase-owned `AiChatModel` port and keeps Spring AI types inside the adapter/configuration boundary.

Deterministic mapping:

- nonblank system instructions become one provider system message only;
- ordered KBase USER/ASSISTANT conversation messages retain their order;
- evidence becomes a separate labelled user data message and is never merged into system instructions;
- the current question is always the final user message;
- provider text and returned model metadata map to `AiChatResult`, with the configured model as a safe fallback;
- missing/blank provider content becomes provider-neutral `INVALID_RESPONSE`.

### AI-PROV-02 – Spring AI Gemini embedding adapter — PASS

Implemented `SpringAiGeminiEmbeddingAdapter` and centralized `SpringAiGeminiEmbeddingPreparation`.

Provider input preparation is owned by the adapter:

- `QUERY`: `task: question answering | query: {content}`;
- `DOCUMENT`: `title: {title-or-none} | text: {content}`.

The adapter configures `gemini-embedding-2` and `dimensions=768`, then rejects every provider vector whose dimension is not exactly 768. It does not pad, truncate or re-embed. KBase `AiEmbeddingResult` continues to enforce non-empty finite values.

### AI-PROV-03 – Provider error translation — PASS

Added KBase-owned `AiProviderErrorCategory` and `AiProviderException`. The Spring AI/Google GenAI translator maps transport and provider failures to `TIMEOUT`, `RATE_LIMITED`, `CONFIGURATION`, `UNAVAILABLE` or `INVALID_RESPONSE`. `TIMEOUT`, `RATE_LIMITED` and `UNAVAILABLE` are retryable; configuration and invalid response failures are not.

The exception deliberately retains no raw provider message, cause, request/response body, prompt, evidence, credential or vector.

### AI-PROV-04 – Provider privacy and logging — PASS

Added negative tests covering raw chat prompt/evidence, document content, provider body and synthetic API-key values. The explicit configuration validates a key only when `kbase.ai.enabled=true`; disabled AI creates no KBase or vendor provider beans and starts without a key. Tests use a synthetic sentinel only and do not call Gemini or any public provider network.

### AI-PROV-05 – Adapter contract tests — PASS

Added deterministic mock/test-double tests for configuration, chat mapping/result fallback, evidence isolation/order, query/document preparation, exact 768 dimensions, non-finite values, provider error categories and privacy behavior.

## 3. Provider configuration and timeout decision

`AiGeminiProviderConfiguration` is explicitly guarded by `kbase.ai.enabled=true`; vendor Google GenAI auto-configuration remains excluded from the normal disabled path. Enabled configuration creates the Google clients and Spring AI models required by the two KBase adapters and validates a blank/missing key as a safe `CONFIGURATION` failure.

`kbase.ai.provider.request-timeout` is applied through Google GenAI `HttpOptions.timeout` using the resolved SDK API. Google GenAI `1.65.0` does not expose an independent connect-timeout setting on this selected client path. `kbase.ai.provider.connect-timeout` remains typed configuration for future transport customization, but M4 does not claim it is active or fake its application. This limitation is recorded in the technical-debt tracker.

## 4. Explicitly out of scope and scope audit

M4 did not add or modify:

- extraction, chunking, embedding execution orchestration or document index handlers;
- semantic retrieval, grounding, citations, conversation runtime or Guide runtime;
- Project Assistant/Guide REST endpoints, OpenAPI changes or frontend/streaming;
- destructive conversation purge;
- migrations, persistence schema, generated DB/API snapshots or public API behavior;
- MinIO/storage calls, database calls, `ai_jobs` handling or direct Gemini SDK adapters.

Static review passed: vendor imports are confined to `com.kbase.ai.provider.springai`; no provider type leaks into services/domain/application ports; no storage/controller/RAG/extraction scope leaked into M4.

## 5. Verification evidence

| Command or check | Result | Evidence |
|---|---|---|
| `mvn -B -ntp "-Dtest=AiGeminiProviderConfigurationTest,AiProviderErrorTranslatorTest,AiProviderPrivacyTest,SpringAiGeminiChatAdapterTest,SpringAiGeminiEmbeddingAdapterTest" test` | PASS | 22 tests, 0 failures, 0 errors, 0 skipped; deterministic tests only |
| `mvn -B -ntp clean verify` | PASS | `BUILD SUCCESS`; 290 tests, 0 failures, 0 errors, 0 skipped; compile/package/repackage pass |
| `docker compose -f docker-compose.yml config --quiet` | PASS | Compose configuration remains valid; no topology/schema change |
| `git diff --check` | PASS | No whitespace errors |
| Disabled/enabled provider context tests | PASS | Disabled AI starts without key and creates no provider beans; enabled synthetic key builds explicit models without network; blank key fails with safe configuration error |
| Static boundary/privacy audit | PASS | Vendor imports confined to adapter/config package; no raw sensitive values in provider error/log paths; no M5+ implementation or generated DB/API changes |

Real Gemini credential: **not required**. Public Gemini network: **not required**. Real-provider connectivity: **not verified by design**.

## 6. Gate checklist

- [x] service/domain code imports no provider type;
- [x] chat adapter uses the approved KBase port and typed configuration;
- [x] embedding adapter enforces 768 dimensions and query/document modes;
- [x] timeout/rate/config/provider error mapping is stable and provider-neutral;
- [x] sensitive logging/durable-state negative tests pass;
- [x] disabled-AI startup remains healthy without credential/network;
- [x] full regression, Compose config and diff check pass;
- [x] no M5+ extraction/indexing, retrieval, API or purge behavior is implemented.

## 7. Completion report

**Task:** Implement M4 – Gemini Provider Adapters  
**Status:** DONE

**Files created:**

- `src/main/java/com/kbase/ai/provider/error/AiProviderErrorCategory.java`;
- `src/main/java/com/kbase/ai/provider/error/AiProviderException.java`;
- `src/main/java/com/kbase/ai/provider/springai/AiGeminiProviderConfiguration.java`;
- `src/main/java/com/kbase/ai/provider/springai/SpringAiGeminiChatAdapter.java`;
- `src/main/java/com/kbase/ai/provider/springai/SpringAiGeminiEmbeddingAdapter.java`;
- `src/main/java/com/kbase/ai/provider/springai/SpringAiGeminiEmbeddingPreparation.java`;
- `src/main/java/com/kbase/ai/provider/springai/SpringAiGeminiErrorTranslator.java`;
- the five M4 test classes under `src/test/java/com/kbase/ai/provider/springai/`.

**Files modified:** living execution/status/quality/integration documentation listed in the final handoff; no application port, migration or generated API/DB file was modified.

**Behavior implemented:** explicit disabled-by-default Gemini/Spring AI boundary, deterministic chat and embedding mapping, strict 768-vector validation, safe provider error categories, request timeout wiring and privacy safeguards.

**Tests added:** 22 M4 adapter/configuration/error/privacy contract tests.

**Tests executed:** targeted M4 suite, full `mvn -B -ntp clean verify`, Compose config, `git diff --check` and static boundary audits.

**Test result:** all PASS; final full suite 290/290.

**Source documents used:** M0–M3 completed plans, AI v1 product specification, RAG architecture, persistence/vector design, testing strategy, backend/integration/testing/security/reliability/deployment living docs and master AI implementation plan.

**Generated docs updated:** none; M4 changes no database schema or REST contract.

**Known limitations:** no real Gemini connectivity test; selected Google GenAI client path has no independent connect-timeout API; extraction/indexing and later AI runtime milestones remain unimplemented.

**New unresolved questions:** none for M4; connect-timeout transport customization is tracked as technical debt.

**Core business rules changed:** NO  
**AI product rules changed:** NO
