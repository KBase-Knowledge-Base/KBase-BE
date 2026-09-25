# KBase AI Chatbot v1 – M10 Usage Guard / OpenAPI / Observability / Hardening

**Status:** READY – implementation has not started  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed AI M0–M9; Core v1 remains frozen

## Goal

Finish the operational and public-contract hardening required before the AI v1 runtime verification/freeze milestone, without changing the established Core, Project Assistant, or KBase Guide product boundaries.

## In scope

- AI-HARD-01: approved configurable per-user usage/rate guard, preferably through the M0-selected Redis namespace, while preserving Core availability when AI rate state is unavailable;
- AI-HARD-02: safe operational metrics/logs for job depth, state, latency, provider error category and no-evidence results, with no raw content or secret material;
- AI-HARD-03: verified OpenAPI annotations and exact contract tests for implemented AI outcomes/errors;
- AI-HARD-04: synchronize `docs/generated/api-schema.md` from verified runtime OpenAPI;
- AI-HARD-05: DTO/OpenAPI/log leakage audit for vectors, hashes, job payloads/leases, provider material, storage keys and raw project content;
- AI-HARD-06: synchronize affected living documentation and execution evidence.

## Explicit exclusions

Do not implement frontend, streaming/SSE, persistent Guide conversations, Guide source management, project-corpus fallback for Guide, OCR/multimodal/XLSX RAG, a broker, schema changes unless an approved source explicitly requires one, or real Gemini automated network tests. Preserve M9's reviewed two-source Guide allowlist and stateless API-AI-010 boundary.

## Required source material

- `ARCHITECTURE.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/PLANS.md`, and this active plan;
- SD-14 through SD-19, especially the AI REST API and Testing Strategy;
- `docs/DEVELOPMENT.md`, `docs/BACKEND.md`, `docs/API_CONVENTIONS.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, and `docs/DEPLOYMENT.md`;
- completed M8/M9 plans and the master plan M10 section.

## Baseline and verification path

Before implementation, confirm the `feat-AI` branch and record pre-existing changes. Re-run the M9 baseline: `mvn -B -ntp clean verify` (356 tests, zero failures/errors/skips in the M9 completion evidence), then run focused rate, observability, OpenAPI and leakage checks as they are added. Final gate must include clean verify, runtime OpenAPI contract verification, generated snapshot synchronization, security/log scan, `docker compose -f docker-compose.yml config --quiet`, and `git diff --check`.

## Risks and decisions to lock before code

- Lock the per-user guard algorithm, Redis key/TTL semantics, fail-open/fail-closed behavior and exact 429 error contract from SD-17/M0 before implementation.
- Ensure observability reports only bounded, non-sensitive categories and never changes AI data/corpus authorization boundaries.
- Any public contract alteration must update OpenAPI tests and `docs/generated/api-schema.md` in the same slice.
- A schema migration or a change to M9 Guide corpus/retrieval is out of scope and requires an explicit approved design decision.

## Progress log

| Item | Status | Evidence |
|---|---|---|
| Preflight | NOT STARTED | M9 completion evidence is 356/356; no M10 code or verification has been run. |
| AI-HARD-01 | NOT STARTED | Usage/rate guard remains deferred. |
| AI-HARD-02 | NOT STARTED | No M10 observability implementation started. |
| AI-HARD-03..06 | NOT STARTED | M9 OpenAPI is 38 paths/57 operations; M10 must verify any subsequent change. |

## Completion conditions

M10 is complete only when the rate guard is executable and safe, OpenAPI contract tests pass, the generated API snapshot is current, sensitive-field/log scans are clean, the full clean verification succeeds, and `CURRENT_STATE.md`/quality/execution evidence accurately state the verified result. Move this plan to `../completed/` only then.
