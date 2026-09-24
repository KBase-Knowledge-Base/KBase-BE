# KBase AI Chatbot v1 – M7 Project Assistant Conversations & REST API

**Status:** READY – planning only; implementation has not started
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`
**Depends on:** Completed AI M0–M6; Core v1 remains frozen
**Scope:** Private non-streaming Project Assistant conversation and message lifecycle plus the SD-17 REST contract, using the completed M6 internal strict RAG boundary.

## Goal

Expose authorized, creator-private Project Assistant conversations with atomic max-five creation, one active generation, durable USER/ASSISTANT message transitions, strict M6 grounded/NO_EVIDENCE outcomes, current-access rechecks and structured source reads. No M7 behavior is implemented by this handoff.

## Source of truth and bootstrap

Before coding, re-read `AGENTS.md`, `ARCHITECTURE.md`, `.harness/source-doc-registry.json`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`, SD-14 product spec, SD-15 RAG architecture, SD-16 persistence design, SD-17 REST API specification, SD-18 testing strategy, SD-19 master plan and completed M0–M6 plans. Inspect current code and run the baseline `mvn -B -ntp clean verify` plus Compose config. Resolve any conflict in this plan before changing behavior.

## Approved tasks from master plan

- **AI-CHAT-01 – Conversation authorization/service:** creator-only privacy plus current project access; ADMIN project override must not bypass creator ownership.
- **AI-CHAT-02 – Atomic max-five create:** create with first message only, using the existing locked quota boundary and concurrent integration evidence.
- **AI-CHAT-03 – Conversation list/get/rename/delete:** own only, `updatedAt DESC`, title max 100, hard delete lifecycle.
- **AI-CHAT-04 – Send message lifecycle:** persist USER and ASSISTANT PROCESSING before M6 RAG call; one active generation; finalize COMPLETED or FAILED without holding a transaction across provider calls.
- **AI-CHAT-05 – Authorization recheck:** no completed answer/source persistence or return after in-flight access loss; preserve the M6 pre-chat and post-chat checks.
- **AI-CHAT-06 – Message/source read API:** deterministic pagination and historical snapshots with unavailable live sources; current document authorization on source open.
- **AI-CHAT-07 – Controller/DTO/error contract:** implement only approved SD-17 Project Assistant endpoints and stable errors; regenerate `docs/generated/api-schema.md` when the contract changes.

## Boundaries

- Reuse `ProjectRagService`, `GroundedResult`, `CitationSnapshotMapper` and existing persistence entities/repositories; M6 evidence/history/label rules remain authoritative.
- M7 may create/persist conversations and messages but must not use history as evidence, leak private conversations across creators, return generated content after membership loss, or expose storage keys/permanent URLs.
- Guide runtime, retention purge handler, frontend, streaming, OCR/multimodal/XLSX RAG, rate limiter and unrelated Core changes remain outside this slice unless source documents explicitly approve them.
- No schema migration by default; if a proven schema defect blocks M7, document it and resolve separately before changing Flyway.

## Required verification and completion rule

Run targeted unit/API/security/concurrency/PostgreSQL tests for privacy matrix, max-five under contention, one active generation, no-evidence 200 domain outcome, provider failure preserving USER message, revoke during generation, source availability/authorization and SD-17 contract. Then run `mvn -B -ntp clean verify`, `docker compose -f docker-compose.yml config --quiet`, `git diff --check` and a scope/privacy audit. Record actual evidence in this plan and living docs before marking M7 complete. Do not infer completion from M6's 329/329 baseline alone.

## Handoff state

M6 Gate PASS on `feat-AI`: targeted 33/33, full 329/329, real pgvector cross-project trap and citation deletion/revocation tests. M7 implementation: **NOT STARTED**. Next action: inspect SD-17 and the existing conversation persistence/authorization boundaries, resolve M7 lifecycle decisions, then implement AI-CHAT-01 as the first bounded task.
