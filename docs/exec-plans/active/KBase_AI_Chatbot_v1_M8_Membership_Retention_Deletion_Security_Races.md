# KBase AI Chatbot v1 – M8 Membership Retention / Deletion / Security Races

**Status:** READY – planning only; M8 implementation has not started  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed AI M0–M7; Core v1 remains frozen  
**Scope:** Seven-day private conversation retention/purge and lifecycle/security races, using M3 durable retention intent, M7 private conversation runtime and V4 persistence.

## Goal

Prove immediate denial after membership loss, safe rejoin restoration, due-time hard purge only while membership remains absent, project/document deletion behavior and in-flight generation races. M7 already implements immediate access checks and basic revoke-before-finalize safety; M8 owns the destructive purge handler and deeper race matrix.

## Sources and bootstrap

Before code, follow `AGENTS.md` startup, read `.harness/source-doc-registry.json`, `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, `docs/DEVELOPMENT.md`, this active slice, SD-14..SD-19 and completed M3/M7 plans. Inspect actual membership hooks, job registry/lease handling, conversation/source FK behavior and M7 transaction boundaries. Run baseline `mvn -B -ntp clean verify` plus Compose config. If code or verified evidence contradicts these sources, block the affected work and record the conflict.

## Approved tasks from AI master plan

- **AI-RET-01 – Immediate revoke:** removing/leaving a project denies retained conversation list/get/send/messages immediately through current project authorization.
- **AI-RET-02 – Purge handler:** at `membershipLostAt + P7D`, recheck current membership and hard-delete only that user's conversations for that project while still absent; preserve durable job lease/idempotency rules.
- **AI-RET-03 – Rejoin restore:** invitation acceptance or current membership creation neutralizes pending purge; same user/project conversation access and quota resume before deadline, independent of new role.
- **AI-RET-04 – Project delete cascade:** project hard delete bypasses retention grace and removes its AI data/jobs without resurrecting late work.
- **AI-RET-05 – Deleted document historical sources:** live document/chunk FKs become null while safe snapshot remains UNAVAILABLE; new retrieval cannot use deleted source.
- **AI-RET-06 – In-flight revoke race:** blocked deterministic provider, revoke before completion, no completed answer/source returned or persisted; test tighter interleavings around finalization and rejoin.

## Boundaries and decisions to resolve in M8

- Use the M3 PostgreSQL durable job engine and V4 schema. Do not add a broker, in-memory purge timer or frontend/streaming path.
- Retention is exactly seven 24-hour days; tests use a controllable clock/job run time, not seven-day sleeps. Recheck membership at execution time and make duplicate/stale deliveries harmless.
- Normal conversation access remains creator-private even for OWNER/ADMIN. ADMIN project override does not authorize reading another user's retained conversations.
- Do not implement Guide (M9), usage/rate guard (M10), or unapproved Core changes. No migration by default; any proven schema blocker requires source/plan reconciliation before Flyway change.
- M7 known limitation: abrupt JVM death may leave a PROCESSING assistant; recorded in the tech-debt tracker for approved hardening. M8 must preserve M7's normal failure/revoke safety and should not invent a chat-generation job outside approved scope.

## Verification and completion rule

Add focused unit and real PostgreSQL/pgvector integration tests for due-time purge, membership recheck, rejoin cancellation, project/document deletion, duplicate/stale job delivery and concurrent revoke/finalization. Reuse deterministic `FakeAiChatModel` and `FakeAiEmbeddingModel`; no real Gemini credential/network. Run targeted suites, then `mvn -B -ntp clean verify`, `docker compose -f docker-compose.yml config --quiet`, `git diff --check` and privacy/scope audit. Update generated DB/API snapshots only if their verified source of truth changes. Record exact command results and known limits before declaring M8 PASS.

## Handoff state

M7 Gate PASS on `feat-AI` (2026-09-24): targeted 36/36, full 346/346, V1–V4 unchanged, 37 runtime OpenAPI paths / 56 operations, private Project Assistant and document index REST verified. M8 implementation: **NOT STARTED**. Next action: inspect M3 retention enqueue/cancel and job registry against SD-14/SD-16, lock purge transaction and race decisions, then implement AI-RET-01..06 as bounded tasks.
