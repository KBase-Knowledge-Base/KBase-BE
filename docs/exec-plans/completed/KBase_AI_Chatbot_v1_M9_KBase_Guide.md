# KBase AI Chatbot v1 – M9 KBase Guide

**Status:** DONE – ready for handoff to M10
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed AI M0–M8; Core v1 remains frozen

## Goal

Implement a stateless authenticated KBase Guide grounded only on the approved Core v1 and AI v1 product specifications. It must never access project documents, project vectors, private conversation history, or unapproved internal documents.

## Approved scope

- package the exact allowlisted Markdown source files in the backend artifact;
- deterministic Guide source hashing and durable Guide reindex lifecycle;
- Guide-only retrieval and strict grounded/no-evidence result;
- stateless REST query with bounded caller-provided USER/ASSISTANT context;
- real PostgreSQL/pgvector and deterministic fake-provider evidence.

## Explicit exclusions

No project-data retrieval, persistent Guide conversation, frontend, streaming, rate guard, OCR/multimodal/XLSX, broker, or real Gemini automated network test.

## Required preflight

Reload AGENTS routing docs, SD-14 through SD-19, M8 completed evidence and current code. Confirm M8's 352-test baseline before modifying code. Run incremental tests and record exact final `mvn -B -ntp clean verify`, Compose config, diff check, OpenAPI and privacy evidence.

## M9 gate

- only canonical allowlisted source files are indexed;
- Guide cannot query project corpus or reveal internal plans;
- documented questions have grounded sources and unsupported questions refuse safely;
- no persistent Guide conversation or provider/network dependency is introduced;
- generated API/DB docs change only if verified source-of-truth requires it.

## Implementation record – 2026-09-24

- AI-GUIDE-01/02: `GuideSourceCatalog` hard-codes exactly the two approved source keys and safe titles. Maven packages only those canonical files into `classpath:/kbase-guide/`; Docker copies only those two source files into its build stage.
- AI-GUIDE-03: packaged bytes are SHA-256 hashed. Startup reconciliation creates/version-increments `ai_guide_sources` and enqueues semantic `GUIDE_REINDEX` payloads without raw content. Replacement activation preserves `READY` last-good chunks; failed initial indexes become `FAILED`, while failed replacements preserve `READY`.
- AI-GUIDE-04/05: fenced-code-safe Markdown heading parsing plus deterministic `kbase-lex-v1` chunking; Guide retrieval uses only `ai_guide_chunks`/`ai_guide_sources` and puts the immutable source-key predicate in SQL before ANN ordering/limit. A missing similarity threshold fails closed; strict NO_EVIDENCE never calls chat generation.
- AI-GUIDE-06: authenticated `POST /api/v1/ai/guide/query`, bounded USER/ASSISTANT context only, no persistent Guide conversation, no project route/repository access.
- Verification: M8 baseline `mvn -B -ntp clean verify` 352/352; focused Guide packaging/chunker/grounding/SQL-isolation 4/4; M7 regression + Guide startup 18/18; OpenAPI contract 20/20; final `mvn -B -ntp clean verify` 356/356, 0 failures/errors/skips. The final regression adds a null-threshold fail-closed test and a real pgvector rogue-perfect-vector test proving the SQL allowlist predicate is applied before ANN candidate limiting. Compose config and `git diff --check` pass. Local and final Docker artifact inspection each show exactly the two `BOOT-INF/classes/kbase-guide/` resources, and local resource SHA-256 matches canonical bytes.
