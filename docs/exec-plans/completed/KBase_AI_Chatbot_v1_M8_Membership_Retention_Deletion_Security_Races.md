# KBase AI Chatbot v1 – M8 Membership Retention / Deletion / Security Races

**Status:** DONE – M8 Gate PASS (2026-09-24)  
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

## M8 decision and evidence log

- **AI-RET-01 Immediate revoke — DONE.** Existing current-project authorization continues to deny retained conversation operations after remove/leave; rows remain until due time.
- **AI-RET-02 Purge handler — DONE.** `ConversationPurgeJobHandler` is provider-independent and ignores payload identity. Under the existing PostgreSQL advisory lock on `conversation-purge:{projectId}:{userId}`, it validates a current `PROCESSING` lease, rechecks membership, then bulk-deletes only the claimed project/user conversations. Zero rows, cancelled claims, expired/reclaimed leases and project cascade deletion are harmless.
- **AI-RET-03 Rejoin — DONE.** Remove/leave acquire the lifecycle lock before deletion/scheduling. Invitation acceptance acquires it before absence check/insert, then cancels PENDING/PROCESSING/RETRY purge. A rejoin before purge restores retained rows; a purge that linearized first is not resurrected.
- **AI-RET-04 Project delete — DONE.** Existing V4 project FKs immediately cascade conversations/messages/sources/jobs; stale claimed lease cannot transition a removed job.
- **AI-RET-05 Deleted source — DONE.** M7 executable citation lifecycle remains: document/chunk live FKs become null, snapshot is represented `UNAVAILABLE`, and deleted corpus is absent from new retrieval.
- **AI-RET-06 In-flight security — DONE.** `StartedTurn` carries the authenticated `project_members.id`; finalization requires the same current membership identity. Revoke→rejoin invalidates the old turn while a new request may use the replacement membership.

### Provider-disabled maintenance decision

`AiJobScheduler` is no longer conditional on `kbase.ai.enabled`. Handler registration remains the claim boundary: `DocumentIndexJobHandler` remains conditional on enabled AI, while `ConversationPurgeJobHandler` is always available and imports no chat, embedding, Spring AI, Google SDK, storage, or network type.

### Verification

- Targeted: `ConversationPurgeJobHandlerTest`, `AiConversationRetentionIntegrationTest`, `ProjectAssistantM7IntegrationTest`, `InvitationServiceTest`, and `ProjectMemberServiceTest` — **34/34 PASS** on the targeted run; the focused unit/smoke run was **9/9 PASS**.
- Final: `mvn -B -ntp clean verify` — **BUILD SUCCESS, 352 tests, 0 failures, 0 errors, 0 skipped**. Testcontainers shutdown emitted known Hikari closed-connection warnings after test completion; Surefire reported success.
- `docker compose -f docker-compose.yml config --quiet` — PASS. `git diff --check` — pending final working-tree check.
- No migration, REST endpoint, generated DB/API document, provider credential/network, Guide, rate guard, frontend, streaming, broker, OCR, multimodal or XLSX scope was added.

## Handoff state

M8 Gate PASS on `feat-AI`. V1–V4 and public runtime OpenAPI remain unchanged at 37 paths / 56 operations. Move this plan to `completed/`; M9 KBase Guide is the next active slice and has not been implemented.
