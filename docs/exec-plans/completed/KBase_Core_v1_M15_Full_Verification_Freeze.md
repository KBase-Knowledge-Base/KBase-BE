# M15 – Full Verification / Core v1 Freeze

## Objective

Prove Core v1 is stable before any AI/RAG work: run the full verification matrix, regression the whole Core v1 surface, audit docs/runtime/schema/API consistency, fix only evidence-backed bugs within Core v1 scope, and freeze the stable baseline when every gate passes (Master plan §31 VERIFY-01..VERIFY-12, SD-12 release gate).

## Entry gate

M14 Gate is PASS on `2026-09-18` (Docker runtime verified, full suite 210/210 via `mvn -B -ntp clean verify`, no M14 blocker remains). Environment at M15 open: Java 21.0.11, Maven 3.9.15, Docker 29.8.0, Compose v5.5.1, git clean at commit `41fbed0`.

## In scope (VERIFY mapping)

- VERIFY-01 Full unit suite — `mvn -B -ntp test` (all suites).
- VERIFY-02 PostgreSQL integration suite — Flyway/Hibernate validate/constraints/repositories/concurrency/cross-project integrity via Testcontainers suites.
- VERIFY-03 Redis OTP integration suite — TTL/attempts/cooldown/replacement/unavailable mapping.
- VERIFY-04 MinIO integration suite — upload/get/range/stat/delete/batch delete.
- VERIFY-05 Security/authorization matrix — non-member/MEMBER/OWNER/ADMIN + unverified login block + disabled-JWT block.
- VERIFY-07 Information leakage review — static scan + runtime log scan for password/hash/JWT/refresh token/OTP/invitation token/App Password/MinIO secret/storageKey/SQL/stack trace.
- VERIFY-09 OpenAPI review — contract tests + forbidden-endpoint scan (OTP login/MFA/password reset/AI/RAG/versioning/sharing/transfer/comments).
- VERIFY-10 Architecture review — static violation scan (Controller→Repository/MinIO/Redis/SMTP, authz in filter, OTP persistence, SDK leaks, Project.ownerId, @ManyToMany, CascadeType.ALL misuse, ddl-auto=update, host-local assumptions, AI code).
- Source-of-truth consistency audit — docs ↔ code, Flyway ↔ JPA, api-schema ↔ runtime, error catalog ↔ handlers, Docker config ↔ documented architecture.
- VERIFY-08 Docker persistence review — clean Compose startup, Flyway-in-container, golden journeys (mail double), postgres_data/minio_data durability, Redis ephemeral + resend, log scan.
- VERIFY-11 Release test gate — SD-12 full list via `mvn -B -ntp clean verify` + Docker smoke.
- VERIFY-12 Core v1 Freeze — only if all above pass.

## Out of scope

- AI/RAG, frontend, K8s/Terraform, any new feature/endpoint, large refactors, business rule changes (unless implementation is proven to deviate from source documents), silent source-of-truth edits.

## Fixed decisions

- Only evidence-backed fixes (test/runtime/contract mismatch or source-of-truth violation); each fix needs root cause + regression rerun of the affected area.
- Frontend stays deferred; Gmail real-delivery manual smoke stays optional (automated path uses mail double).
- Freeze requires: M0–M15 gates pass, critical suites green, no Core-v1 blocker, no known critical security/data-integrity issue, Docker runtime pass, docs/current-state reflect reality.

## Progress log

- 2026-09-19: M15 opened after re-checking M14 Gate and environment (Java 21.0.11 / Maven 3.9.15 / Docker 29.8.0 / Compose v5.5.1); git clean at `41fbed0`.
- 2026-09-19: VERIFY-01/11 — `mvn -B -ntp clean verify` BUILD SUCCESS, **210 tests, 0 failures, 0 errors, 0 skipped** (covers all unit, PostgreSQL/Redis/MinIO Testcontainer, security, search, OpenAPI contract, error-contract suites). Evidence: Maven log recorded in session, totals line `Tests run: 210, Failures: 0, Errors: 0, Skipped: 0`.
- 2026-09-19: VERIFY-10 — static architecture scans all clean: no controller→repository imports; MinIO SDK only in `storage`; Spring Data Redis/Lettuce only in `redis`; JavaMail only in `mail`; no `Project.ownerId`; no `@ManyToMany`/`CascadeType.ALL`; no OTP entity/repository/table (code + migrations + live catalog); `ddl-auto=validate` (base+prod, `none` test-only); `JwtAuthenticationFilter` has no project/role logic; services inject ports (`MailService`/`OtpStore`/`StorageService`) only; `Document.uploadedBy` is `User`; compose backend wires service names (`postgres`/`redis`/`minio`), never localhost; no AI/RAG/embedding/vector/transcription code (only descriptions stating their absence).
- 2026-09-19: VERIFY-09 — runtime `/v3/api-docs` (HTTP 200): OpenAPI 3, `bearerAuth` HTTP-bearer-JWT; **32 paths** identical to contract-test `EXPECTED_PATHS`; **47 operations**; zero forbidden endpoints (no OTP login/MFA/password-reset/sharing/transfer/versioning/comments/AI).
- 2026-09-19: Consistency audit — error catalog: all SD-10 catalog codes present in `ErrorCode` (68 codes; `INTERNAL_DEPENDENCY_UNAVAILABLE` is explicitly "could be added later" in SD-10, superseded by the three specific `*_SERVICE_UNAVAILABLE` codes that SD-01/SD-04 require); endpoints 47/47 1:1 with SD-04; `docker-compose.yml` ↔ `DEPLOYMENT.md` ↔ `ARCHITECTURE.md` consistent (4 services, healthcheck gating, `postgres_data`/`minio_data`, Redis tmpfs ephemeral, `:?required` secrets); `application-prod.yml` matches contract (`validate`, Secure cookie, OpenAPI off, `include-message: never`); config baselines match spec (OTP 6/5m/60s/5, JWT 15m/7d, invitation 72h, upload 50/20/500MB batch 10, page size 20 max 100); `docs/generated/db-schema.md` matches migrations (10 tables, 19 indexes incl. partial/expression).
- 2026-09-19: **Finding (documentation, not code)** — M13 records claimed "32 paths / **48** operations"; runtime spec contains **47 operations** (triple-checked: operationId count in runtime JSON, controller mapping count, @Operation annotation count; 47 also equals SD-04's endpoint definitions). Fixed the number in `docs/generated/api-schema.md`, `CURRENT_STATE.md` (3 places), `DEVELOPMENT.md` (2), `QUALITY_SCORE.md` M13 row; added a correction note to the completed M13 plan instead of rewriting its original log. Root cause: manual miscount when writing the M13 record; contract tests never asserted a hard total. No code change, no regression needed (docs-only fix).
- 2026-09-19: VERIFY-08 — Docker runtime from empty volumes: `down -v` → `up -d --build` with mail double (`docker-compose.mail-test.yml` + `KBASE_GMAIL_SMTP_HOST=mail-test`, port 1025, no auth/TLS). Healthcheck-gated startup (redis/postgres/minio healthy → backend). Flyway `Empty Schema → V1 → V2 → V3 → successfully applied 3 migrations`; Hibernate validate pass; Tomcat 8080; api-docs 200 after 18s.
- 2026-09-19: Container DB inspection: exactly 10 persistent tables (+ `flyway_schema_history`), `users.email_verified_at` = timestamptz nullable, no OTP-like table, Flyway history 3 rows success.
- 2026-09-19: Golden journeys via containerized backend — auth: register 201 (`emailVerified:false`) → Redis keys `kbase:otp:email-verification:{userId}` + cooldown, TTL 292s, hash structure (no raw OTP) → OTP email "Verify your KBase email address" captured by mail double → verify 200 → Redis state removed → DB `email_verified_at` set → login 200 (JWT HS256 claims jti/sub/systemRole/iat/exp only, expiresIn 900, HttpOnly cookie `kbase_refresh_token`, Path=/api/v1/auth, Secure=false local) → refresh 200 → logout 204 → refresh-after-logout 401 `REFRESH_SESSION_REVOKED` → `/users/me` 200.
- 2026-09-19: Core flows — project create 201 `currentUserRole:OWNER` (members list shows single OWNER); folder/category/tag 201; upload PDF 201 (metadata folder/category/tag resolved same-project; response exposes `uploadedBy` user and no `storageKey`), MP4 201, MD 201; download MD5 `e5c74eeb69de5f0f4c47ce06d6f9835b` matches source, `Content-Disposition: attachment`; PDF preview 200 inline; MP4 `Range: bytes=0-1023` → 206 `bytes 0-1023/2000032`; invalid range → 416 `bytes */2000032`; search `q` (displayName/description), `fileKind`, combined `tagId+folderId`, sort `sizeBytes,desc` (2000032→221→51), invalid sort → 400 `VALIDATION_ERROR`, `size=1000` clamped to 100.
- 2026-09-19: Invitation + membership — invitation create 201 PENDING, raw token only in email link (43-char token); register/verify/login user2 → accept 200 role MEMBER; MEMBER reads 3 docs + downloads OWNER's PDF 200; MEMBER delete OWNER's doc → 403 `DOCUMENT_MODIFICATION_FORBIDDEN`; MEMBER patch project → 403 `PROJECT_MANAGEMENT_FORBIDDEN`; USER on `/admin/users` → 403 `ACCESS_DENIED`; anonymous → 401 `AUTHENTICATION_REQUIRED`; MEMBER uploads own PNG 201 and deletes own 204 (MinIO object removed); OWNER removes MEMBER 204 → former member search 403 `PROJECT_ACCESS_FORBIDDEN` while documents remain (totalElements 3).
- 2026-09-19: Hard delete — document delete 204 with object removed from bucket; **OWNER-path project hard delete 204** (M14 regression holds at runtime), project then 404 `PROJECT_NOT_FOUND`, bucket empty, all relational rows cascaded (members/documents/folders/tags/invitations = 0).
- 2026-09-19: Persistence — backend restart: Flyway "Schema is up to date. No migration necessary", download checksum unchanged. postgres/minio force-recreate keeping volumes: 4 users survive, Flyway history 1|t 2|t 3|t, MinIO object downloads with matching MD5. Redis force-recreate: OTP state gone, stale verify → 400 `OTP_EXPIRED`, resend 204, verify new OTP 200, login 200 (documented acceptable ephemerality).
- 2026-09-19: VERIFY-07 runtime log scan over `docker compose logs backend`: 0 hits for passwords/test-passwords, JWT (`eyJ`), refresh-token cookie values, OTP codes, invitation raw token, App Password/MinIO secret patterns, storage keys, SQL/PSQL exceptions. Tampered and garbage JWT probes → 401.
- 2026-09-19: Stack torn down with `docker compose down` (named volumes `kbase_postgres_data`/`kbase_minio_data` preserved; restartable state). No code files changed in M15 (`git status` shows docs/plan files only).

## Verification plan

- `mvn -B -ntp clean verify` (full release gate). — passed 210/210
- Focused suites per VERIFY-02..05 are all included in the full suite (Testcontainers-based). — passed
- Static architecture/leakage/OpenAPI/consistency audits. — passed (commands in progress log)
- Docker runtime: `docker compose config --quiet` (base + mail-test override), clean `up -d --build`, Flyway/Hibernate log check, golden journeys via mail double, persistence (restart + force-recreate + Redis recreation), log leak grep. — passed
- Doc updates per harness rules at freeze. — done

## Completion gate (M15 Gate)

- VERIFY-01..VERIFY-11 all PASS with evidence. — PASS
- No Core v1 blocker or known critical security/data-integrity issue open. — PASS
- Documentation reflects verified implementation (api-schema operation-count correction applied). — PASS

## Outcome — Core v1 Freeze Report (2026-09-19)

- M15 Gate: **PASS**. Core v1 is **frozen/completed**; AI/RAG design may start as a separate phase. Frontend remains deferred.
- No code changes were required in M15; the only defect found was a documentation miscount (48→47 operations), fixed with traceable correction.

### Milestone status M0–M15

| Milestone | Gate | Date | Evidence |
|---|---|---|---|
| M0 Preflight & Execution Baseline | PASS | 2026-09-17 | locked baseline, source registry (completed/M0 plan) |
| M1 Bootstrap + Runtime Skeleton | PASS | 2026-09-17 | Maven foundation, Compose skeleton, profiles (completed/M1) |
| M2 PostgreSQL / Flyway Schema | PASS | 2026-09-17 | 3 migrations, 10 tables, integrity 12/12 (completed/M2) |
| M3 JPA Entities & Repositories | PASS | 2026-09-17 | 10 entities/10 repositories, mapping 11/11 (completed/M3) |
| M4 Shared Error / Request | PASS | 2026-09-17 | error contract, request ID, constraint translation (completed/M4) |
| M5 Redis OTP + Gmail Mail | PASS | 2026-09-17 | OtpStore/OtpService/MailService, Redis+fake SMTP suites (completed/M5) |
| M6 Spring Security + Auth | PASS | 2026-09-17 | 6 auth endpoints, auth integration 14/14 (completed/M6) |
| M7 User / Project / Membership | PASS | 2026-09-18 | authorization matrix 6/6 (completed/M7) |
| M8 Invitation Lifecycle | PASS | 2026-09-18 | lifecycle 5/5 incl. concurrency (completed/M8) |
| M9 Folder / Category / Tag | PASS | 2026-09-18 | organization 6/6 (completed/M9) |
| M10 MinIO Storage Infrastructure | PASS | 2026-09-18 | storage adapter + MinIO Testcontainer 2/2 (completed/M10) |
| M11 Document Lifecycle + Project Hard Delete | PASS | 2026-09-18 | API matrix 3/3 + lifecycle 1/1 (completed/M11) |
| M12 Document Search / Pagination / Sorting | PASS | 2026-09-18 | search suites, full 188/188 (completed/M12) |
| M13 OpenAPI / Swagger | PASS | 2026-09-18 | contract 21/21, full 209/209 (completed/M13) |
| M14 Full Docker Runtime Verification | PASS | 2026-09-18 | topology + persistence + golden journeys, full 210/210 (completed/M14) |
| M15 Full Verification / Core v1 Freeze | PASS | 2026-09-19 | this report; full 210/210 + Docker re-verification |

### Build / test summary

- `mvn -B -ntp clean verify`: BUILD SUCCESS — **210 tests, 0 failures, 0 errors, 0 skipped**; compile/package/Spring Boot repackage pass.
- Suite coverage (all Testcontainer-based where applicable): FlywayMigrationIntegrityTest 12; JpaMappingRepositoryIntegrationTest 11; RedisOtpStoreIntegrationTest 6; MinioStorageIntegrationTest 2; AuthenticationSecurityIntegrationTest 14; UserProjectMembershipIntegrationTest 6; InvitationLifecycleIntegrationTest 5; OrganizationIntegrationTest 6; DocumentApiIntegrationTest 4; DocumentLifecycleStorageIntegrationTest 1; DocumentSearchIntegrationTest 2; OpenApiContractIntegrationTest 19 + OpenApiDisabledIntegrationTest 2; ConstraintViolationTranslation/error-contract suites; unit suites for every service/adapter.
- Coverage measurement: not configured in this repository (no JaCoCo baseline); SD-12 release gate is satisfied by the suite list above, not by a coverage percentage.
- Docker runtime: verified per progress log (this session).

### Final API / module inventory

- Runtime API: 12 controllers, **32 paths, 47 operations**, `bearerAuth` HTTP-bearer-JWT, `ApiErrorResponse` shared; public auth ops: register/verify-email/resend-verification-otp/login/refresh/logout.
- Modules (`com.kbase`): auth (controller/service/port/entity/repository), user, project, invitation, folder, category, tag, document, security (jwt/config/handler/principal/service), storage (config/service/model/exception), mail (config/service), redis (config/otp), shared (exception/response/pagination/util/validation), config (properties/OpenApi).
- No endpoint exists for OTP login, MFA, password reset, forgot-password, AI/RAG, versioning, sharing, ownership transfer, comments.

### Final Docker topology (verified)

```text
backend  (kbase-backend:local, eclipse-temurin 21-jre, non-root, port 8080, stateless)
postgres (postgres:17-alpine, healthcheck pg_isready, volume postgres_data)
minio    (quay.io/minio/minio, health /minio/health/live, volume minio_data, console 9001)
redis    (redis:7.4-alpine, --save "" --appendonly no, /data tmpfs — ephemeral OTP only)
Gmail SMTP: external to Docker; docker-compose.mail-test.yml = optional mailpit mail double for automated verification only.
```

### Persistent tables / infra summary

- PostgreSQL: 10 persistent tables (`users`, `refresh_sessions`, `projects`, `project_members`, `project_invitations`, `folders`, `categories`, `tags`, `documents`, `document_tags`) + `flyway_schema_history`; `users.email_verified_at` timestamptz NULL; no OTP table; 19 indexes incl. partial/expression unique indexes; Flyway V1–V3.
- Redis: `kbase:otp:email-verification:{userId}` + cooldown keys, TTL ~5m, HMAC-protected state, ephemeral.
- MinIO: private bucket `kbase-documents`, backend-owned keys `projects/{projectId}/documents/{documentId}.{extension}`, unversioned.

### Bugs found in M15 and resolution

1. Documentation miscount (not a code bug): "48 operations" in M13-era docs vs runtime **47**. Fixed in `api-schema.md`, `CURRENT_STATE.md`, `DEVELOPMENT.md`, `QUALITY_SCORE.md`; correction note appended to completed M13 plan. Root cause: manual miscount in the M13 record; no runtime/contract impact (contract tests assert the exact path set, not a total). Docs-only fix; no regression rerun required beyond the already-green full suite.
2. No runtime, security, data-integrity, schema, or contract defects were found in M15.

### Regression executed in M15

- Full suite `mvn -B -ntp clean verify` 210/210 (covers auth/OTP/JWT/authorization/service/file-validation/mappers/storage/mail/error-translation unit tests and all PostgreSQL/Redis/MinIO/security/API/search/OpenAPI Testcontainer suites).
- Full Docker runtime re-verification of every golden journey listed in `docs/RELIABILITY.md`, including the M14 OWNER-path project hard delete regression scenario.

### Remaining known limitations (accepted, not blockers)

- Gmail real-delivery manual smoke not run (automated path uses mail double at the MailService boundary; credential is environmental).
- JWT access tokens are not revocable server-side before expiry; logout revokes only the refresh session; disabled users are blocked at request time by the DB lookup in `JwtAuthenticationFilter` (baseline locked in SD-07).
- `docs/generated/api-schema.md` field-level Markdown remains a manual snapshot (runtime `/v3/api-docs` is the machine-readable contract; 21 contract tests guard security/multipart/binary/error-code drift).
- Docker smoke remains a documented manual procedure (not scripted).
- No automated DB/API schema generators (documented manual sync process).

### Deferred scope (unchanged)

- Frontend (optional per requirements), AI/RAG, vector/semantic search, content indexing, transcription/STT, Kubernetes/Terraform, S3 adapter, OAuth/Google login.

### Remaining technical debt (non-blocking)

- See `docs/exec-plans/tech-debt-tracker.md`: manual schema/api snapshot sync, manual Docker smoke procedure, Gmail manual smoke, milestone-sized git commits (plan §38 prefers task-level commits).
