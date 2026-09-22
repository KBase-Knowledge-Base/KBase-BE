# KBase AI Chatbot v1 – M4 Gemini Provider Adapters

**Status:** READY  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed M0, M1, M2 and M3 AI slices  
**Scope:** provider-neutral chat/embedding adapter implementation, safe provider error mapping, privacy/logging guards and adapter contract tests.  
**Current step:** dependency handoff; implementation not started

## 1. Source of truth and dependency gate

Required sources for M4:

- `docs/product-specs/KBase - AI Chatbot v1 Specification.md`;
- `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`;
- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`;
- `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`;
- `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md`;
- completed M0/M1/M2 plans and `KBase_AI_Chatbot_v1_M3_Durable_Job_Engine_Core_Lifecycle.md`.

Dependency gate: PASS. M3 completed the PostgreSQL durable job engine and Core lifecycle hooks with targeted 27/27 and full `mvn -B -ntp clean verify` 268/268. Core v1 and Flyway V1–V4 remain frozen/authoritative.

## 2. Approved M4 tasks

### AI-PROV-01 – Spring AI Gemini chat adapter

- Implement the `AiChatModel` adapter behind the KBase-owned provider port.
- Keep vendor types out of service/domain packages.
- Preserve model, timeout and credential configuration behind typed `kbase.ai.gemini` properties.

### AI-PROV-02 – Spring AI Gemini embedding adapter

- Implement query/document embedding modes behind `AiEmbeddingModel`.
- Enforce the locked 768-dimensional output contract.
- Preserve document/query task-prefix ownership at the adapter boundary.

### AI-PROV-03 – Provider error translation

- Map timeout, rate-limit, configuration and provider failures to safe internal categories.
- Keep public `AI_PROVIDER_UNAVAILABLE` mapping for the later API milestone; do not add public API in M4.

### AI-PROV-04 – Provider privacy and logging

- Never log API keys, raw prompts, document chunks, embeddings or raw provider responses.
- Add negative tests for sensitive values in logs and durable job/error state.

### AI-PROV-05 – Adapter contract tests

- Use deterministic mocks/test doubles for automated verification.
- Do not require real Gemini credentials or network access for the M4 gate.

## 3. Explicitly out of scope

- extraction, chunking, embedding execution orchestration or document index handler;
- semantic retrieval, grounding, citations, conversation runtime or Guide runtime;
- Project Assistant/Guide REST endpoints, OpenAPI changes or frontend/streaming;
- destructive conversation purge;
- schema/migration changes unless a separately approved design defect is proven.

## 4. Boundary and safety rules

- `com.kbase.ai.provider` ports/models remain vendor-neutral; only adapter/configuration code may import Spring AI/Google GenAI types.
- Provider calls must not hold the durable job claim transaction open.
- Provider errors are safe category codes; raw exception messages and provider payloads never enter logs or `ai_jobs`.
- `KBASE_AI_ENABLED=false` remains a healthy default and must not require a Gemini credential.
- M3 scheduler registry remains the execution boundary; M4 does not add a document or conversation handler.

## 5. Required verification

- Unit/contract tests for chat and embedding request/result mapping, 768 dimensions and error categories.
- Negative log scan for credentials, prompts, raw document content, embeddings and provider responses.
- Disabled-AI application context without provider credentials/network.
- Full regression: `mvn -B -ntp clean verify`.
- Compose config and `git diff --check`.
- Static scope review proving no extraction/RAG/API/purge behavior leaked.

## 6. Gate checklist

- [ ] service/domain code imports no provider type;
- [ ] chat adapter uses the approved KBase port and typed configuration;
- [ ] embedding adapter enforces 768 dimensions and query/document modes;
- [ ] timeout/rate/config/provider error mapping is stable and provider-neutral;
- [ ] sensitive logging/durable-state negative tests pass;
- [ ] disabled-AI startup remains healthy without credential/network;
- [ ] full regression, Compose config and diff check pass;
- [ ] no M5+ extraction/indexing, retrieval, API or purge behavior is implemented.
