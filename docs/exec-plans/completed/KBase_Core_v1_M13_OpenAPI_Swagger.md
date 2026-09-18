# M13 – OpenAPI / Swagger

## Objective

Make the implemented Core v1 REST contract accurately discoverable through springdoc-openapi from SD-04, SD-07, SD-10, SD-11 and SD-12, without documenting behavior that does not exist at runtime.

## Entry gate

M12 Gate is PASS on `2026-09-18`: project-scoped metadata search/filter/pagination/sorting passed the PostgreSQL Testcontainer API matrix and full regression (188 tests) via `mvn -B -ntp test` and `mvn -B -ntp clean verify`. No M12 functional blocker remains.

## In scope

- `config/OpenApiConfig` with KBase Core API metadata, `bearerAuth` HTTP Bearer JWT security scheme, the 12 feature tags and a shared 401 operation customizer.
- Operation documentation for all 48 implemented endpoints: public auth endpoints (register/verify-email/resend-verification-otp/login/refresh + logout), ADMIN role rules, project MEMBER/OWNER/ADMIN and document ownership rules.
- Multipart upload documentation (`file`/`files` binary parts + optional `metadata` JSON part), binary download/preview schemas, MP4 `Range` header with `206`/`416`.
- Shared `ApiErrorResponse` schema and important per-endpoint error codes (OTP/Gmail/Redis/storage included).
- Environment exposure flags already created at M1 (`kbase.openapi.*`), Swagger UI enabled local/dev, disabled by default in prod profile.
- `/v3/api-docs` contract tests covering security requirements, multipart/binary/range schemas, error codes and sensitive-field absence.

## Out of scope

- M14 Docker runtime, frontend, AI/RAG endpoints, new REST endpoints or contract changes.
- SecurityConfig/CORS loosening for Swagger; multiple OpenAPI groups; contract-first YAML.

## Fixed decisions

- springdoc stays at the M0-locked `3.1.1` (`springdoc-openapi-starter-webmvc-ui`) for Spring Boot `4.1.1`.
- Base path remains `/api/v1`; documentation is generated from runtime controllers/DTOs, never handwritten YAML.
- bearerAuth represents the JWT access token only; refresh token stays HttpOnly cookie `kbase_refresh_token` and is never a JSON field or bearer scheme.
- OTP is documented as registration email verification only (Redis-backed, Gmail-delivered, 6 digits, TTL 5m, cooldown 60s, max 5 attempts); invitation keeps its own separate token.
- DTOs are the API contract; no JPA entity, `passwordHash`, token hash, `storageKey` or credential appears in schemas.
- Security requirements are per-controller/per-operation, never globally applied.

## Progress log

- 2026-09-18: M13 opened after re-checking M12 Gate and a clean 188-test baseline.
- 2026-09-18: Implemented OpenApiConfig, annotated 12 controllers / 48 operations, DTO schema hints, Swagger UI UX properties.
- 2026-09-18: Added `OpenApiContractIntegrationTest` (19 tests, enabled) and `OpenApiDisabledIntegrationTest` (2 tests, prod-style disabled flags); fixed tag duplication with a canonical `OpenApiCustomizer` and completed ADMIN role descriptions.
- 2026-09-18: Focused OpenAPI suite passed 21/21; `mvn -B -ntp test` and `mvn -B -ntp clean verify` both passed 209 tests with 0 failures/errors/skips. API schema, state and quality records were synchronized; no migration was needed.

## Verification plan

- `mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` — passed 21/21.
- `mvn -B -ntp test` (full suite regression) — passed 209/209.
- `mvn -B -ntp clean verify` (build + repackage) — passed, Spring Boot jar repackaged.
- `/v3/api-docs` and `/swagger-ui.html` behavior is exercised by the MockMvc contract tests against the real filter chain (spec 200 + UI redirect; disabled flags yield 401).

## Completion gate (M13 Gate)

- `/v3/api-docs` returns a valid spec containing bearerAuth.
- Public auth endpoints are not marked Bearer-required; protected endpoints carry bearerAuth; ADMIN endpoints state the role requirement.
- Project/document permission descriptions match the implemented authorization matrix.
- Multipart/binary/Range/206/416 documentation matches runtime.
- `ApiErrorResponse` schema and OTP/Gmail/Redis/storage error codes documented in the right places.
- No sensitive or internal fields exposed; no AI/RAG endpoints present.
- Swagger UI works under local/dev flags; production exposure stays configurable (prod default off).

## Outcome

- M13 Gate: **PASS** on 2026-09-18.
- All M13 acceptance criteria are proven by `OpenApiContractIntegrationTest` (19 tests) and `OpenApiDisabledIntegrationTest` (2 tests) on the real security filter chain, plus full regression 209/209.
- No runtime API contract, schema, security or authorization behavior changed; documentation-only milestone.
- No blocker remains beyond the archive-level absence of Git implementation history recorded in `CURRENT_STATE.md`.
- M14 was not started.
