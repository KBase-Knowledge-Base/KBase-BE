# KBase AI Chatbot v1 – M0 Preflight & Technical Compatibility

**Status:** READY  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Current step:** M0-01 – Reinspect Repository and Baseline  
**Scope:** Documentation/verification/dependency decision only; no feature implementation before M0 Gate

---

# 1. Objective

Khóa technical baseline AI v1 trên current `feat-AI`/development code trước khi thêm AI dependency, migration hoặc runtime behavior.

M0 phải chứng minh:

- Core v1 baseline vẫn sạch;
- Spring AI/Gemini path tương thích current Spring Boot;
- pgvector PostgreSQL 17 runtime/test path hợp lệ;
- extraction dependency set đúng scope;
- AI config/test-double choices được ghi lại;
- không có source-doc conflict.

---

# 2. Required Source Documents

Load:

```text
AGENTS.md
ARCHITECTURE.md
docs/CURRENT_STATE.md
docs/DEVELOPMENT.md
docs/PLANS.md

SD-01  Core v1 Specification
SD-14  AI Chatbot v1 Specification
SD-15  AI RAG Architecture
SD-16  AI Persistence and Vector Search Design
SD-17  AI REST API Specification
SD-18  AI Testing Strategy
SD-19  AI Implementation Plan
```

Also inspect current:

```text
pom.xml
docker-compose.yml
Dockerfile
src/main/resources/application*.yml
src/main/resources/db/migration/**
src/main/java/com/kbase/**
src/test/java/com/kbase/**
```

Do not rely only on `docs/CURRENT_STATE.md`.

---

# 3. M0-01 – Reinspect Repository and Baseline

**Status:** READY

Actions:

1. confirm current branch/worktree source;
2. inspect dependency tree/package/module boundaries;
3. confirm no AI implementation already present;
4. run Core baseline:
   ```text
   mvn -B -ntp clean verify
   docker compose -f docker-compose.yml config --quiet
   ```
5. record actual test count/result and any existing failure.

Acceptance:

- baseline passes; or exact pre-existing failure is recorded and dependent task BLOCKED;
- Core v1 invariants still match SD-01/frozen design;
- no generated docs modified.

---

# 4. M0-02 – Spring AI / Gemini Compatibility

**Depends on:** M0-01 DONE

Resolve exact:

- Spring AI version;
- Google GenAI Spring AI artifact;
- Gemini chat model default;
- `gemini-embedding-2` support;
- 768 embedding output support/config;
- query vs document retrieval task support;
- provider timeout API;
- required Google SDK transitive/direct dependency.

Decision rule:

```text
Spring AI works
→ use SpringAiGeminiChatAdapter + SpringAiGeminiEmbeddingAdapter

one capability missing/incompatible
→ document exact reason
→ use direct Gemini SDK only for that capability behind KBase port

whole Spring AI path conflicts with Core Spring Boot
→ do not downgrade Core silently
→ record BLOCKED/design resolution or justified direct adapter path
```

Verification:

- Maven dependency resolution;
- minimal compile/config test where needed;
- no public Gemini call required.

Record choices in this plan Progress/Decision Log before marking done.

---

# 5. M0-03 – pgvector Runtime Compatibility

**Depends on:** M0-01 DONE

Resolve exact:

- PostgreSQL 17 + pgvector image/tag;
- Testcontainers image use;
- extension creation;
- `vector(768)`;
- HNSW cosine support;
- JDBC/native binding approach.

Verification should create a temporary test or reproducible probe, not modify production migration yet if task can prove compatibility separately.

Acceptance:

- selected image starts;
- `CREATE EXTENSION vector` succeeds;
- vector(768) table + insert + cosine query succeeds;
- HNSW index creation succeeds.

---

# 6. M0-04 – Extraction Dependency Baseline

**Depends on:** M0-01 DONE

Current Tika usage is MIME detection only.

Select minimal dependencies for:

```text
PDF
DOC/DOCX
PPT/PPTX
MD
TXT
```

Reject scope creep:

```text
XLS/XLSX
OCR
images/video multimodal
```

Record parsing strategy and any parser-specific security/size guard.

No full extraction implementation in M0.

---

# 7. M0-05 – AI Configuration / Test Double Baseline

**Depends on:** M0-02, M0-03

Record final key names/defaults for:

- AI enabled flag;
- Gemini key/model;
- embedding dimensions = 768;
- timeouts;
- max message length;
- chunk size/overlap;
- retrieval candidate/final context limits;
- threshold;
- worker poll/batch/lease/retry;
- retention 7d;
- usage/rate policy.

Define deterministic fake chat/embedding behavior usable in automated tests and Docker smoke.

Do not commit secret or call real Gemini to make tests pass.

---

# 8. M0-06 – Harness Revalidation

**Depends on:** M0-01

Verify:

- `.harness/source-doc-registry.json` contains SD-14..SD-19;
- precedence matches master plan;
- Core v1 remains frozen baseline;
- active release is AI v1;
- `docs/exec-plans/active/index.md` points to this M0 slice;
- no stale statement says AI cannot begin.

---

# 9. M0 Gate Checklist

```text
[ ] M0-01 Core baseline verified
[ ] M0-02 AI provider dependencies locked
[ ] M0-03 pgvector runtime locked
[ ] M0-04 extraction dependency set locked
[ ] M0-05 AI config/test doubles locked
[ ] M0-06 harness sources revalidated
[ ] no unresolved source conflict
[ ] no product behavior changed
[ ] no AI feature implementation leaked into M0
```

When all pass:

1. update this plan with command evidence/decisions;
2. update `docs/CURRENT_STATE.md`;
3. move this file to `../completed/`;
4. create M1 active slice from master plan;
5. update `active/index.md`.

---

# 10. Progress Log

| Date | Task | Status | Evidence / Decision |
|---|---|---|---|
| 2026-09-21 | AI v1 documentation baseline created | READY | Product/design/master plan approved for execution; M0 implementation verification has not run yet |

---

# 11. Decision Log

No implementation-level M0 decisions have been executed yet.

Agent must fill exact dependency/image/model/config choices here with verification evidence rather than guessing from this documentation-creation session.
