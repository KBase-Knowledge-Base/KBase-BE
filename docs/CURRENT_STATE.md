# Trạng thái Hiện tại

> File này cung cấp ảnh chụp ngắn gọn về trạng thái hiện tại của repository.
> Chỉ ghi thông tin đã được xác nhận. Không dùng file này để thay thế execution plan, product spec hoặc Git history.

## Cập nhật Lần cuối

* Ngày cập nhật: `2026-09-22`
* Người hoặc agent cập nhật: `ChatGPT - M4 Gemini Provider Adapters`
* Nhánh hiện tại: `feat-AI` (được tạo trực tiếp từ `dev`)
* M0 bắt đầu trên working tree sạch; Core baseline và AI compatibility evidence đã được xác nhận trên `feat-AI`. M1–M4 đã hoàn tất; active handoff là M5 content extraction/chunking/document indexing. Retrieval, conversation/Guide runtime, public API và real provider call vẫn chưa có.

## Trạng thái Tổng quan

| Khu vực          | Trạng thái    | Bằng chứng hoặc ghi chú |
| ---------------- | ------------- | ----------------------- |
| Build            | Ổn định Core + M4 / Gate PASS | M1 final: 228/228; M2 final: 241/241; M3 final: 268/268; M4 final `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 290 tests, 0 failures/errors/skips; Compose config và `git diff --check` PASS. |
| Frontend         | Không áp dụng | Frontend và streaming tiếp tục deferred khỏi AI v1 backend. |
| Backend          | Core v1 frozen / M4 provider boundary complete | **Core v1 FROZEN** + M16 maintenance complete. M1/M2 foundation, M3 durable lifecycle và M4 explicit Gemini/Spring AI adapters, safe error taxonomy, request-timeout wiring và privacy guards đã verify; M5 indexing, RAG behavior và endpoint vẫn chưa có. |
| Database         | Core + AI persistence ổn định | Flyway V1–V4 tạo 18 persistent tables (10 Core + 8 AI); pgvector extension, `vector(768)`, HNSW, FK/delete rules và Hibernate validate đã verify trên PostgreSQL 17.11. |
| API contract     | Core ổn định / AI chưa implement | Current runtime OpenAPI vẫn Core 32 paths/47 operations; chưa có AI endpoint. SD-17 là target contract, generated API chỉ cập nhật sau implementation/OpenAPI verification. |
| Integration      | Core + M4 provider boundary verified | Spring AI 2.0.1/Google GenAI 1.65.0 explicit configuration, deterministic chat/embedding adapters, request timeout via `HttpOptions`, pgvector compatibility, V1–V4 fresh/upgrade migration, M3 lifecycle races và Compose config pass; chưa gọi Gemini thật. |
| Unit test        | Core + M4 adapter boundary verified | M1 targeted 15/15; M2 targeted 36/36; M3 scheduler 5/5; M4 provider suite 22/22; deterministic tests không cần credential/network; full M4 gate 290/290. |
| Integration test | Core + AI lifecycle/provider contract verified | M3 PostgreSQL/pgvector suites pass (`AiJobEngineIntegrationTest` 9/9, document intent/rollback 8/8, retention 5/5); M4 provider tests dùng deterministic doubles, không public network. |
| End-to-end test  | Core ổn định / AI provider boundary verified | Core golden journeys đã verify M14/M15; M3 lifecycle/race journeys pass; chưa có extraction/indexing/provider connectivity/chat/API E2E. |
| Security checks  | Core + M4 privacy boundary verified | M2/M3 isolation/status/FK/lease guards giữ pass; M4 safe error categories, disabled-without-key, synthetic-key no-log và raw prompt/evidence/document/provider-body negative tests pass; authorization/RAG vẫn deferred. |
| Deployment       | Core + M4 configuration verified | Compose giữ service/healthcheck/`postgres_data` và `pgvector/pgvector:0.8.6-pg17-bookworm`; M4 không thêm migration/topology/API; AI disabled vẫn là mặc định. |

Trạng thái nên dùng:

* `Ổn định`
* `Đang thực hiện`
* `Bị chặn`
* `Có lỗi`
* `Chưa đánh giá`
* `Không áp dụng`

## Công việc Đang Hoạt động

### Ưu tiên Hiện tại

* Mục tiêu: `Triển khai KBase AI Chatbot v1 backend trên Core v1 đã frozen: Project Assistant private/project-scoped + KBase Guide grounded, không frontend/streaming/Project Chat.`
* Execution plan: `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md` (AI master roadmap M0–M11)
* Active slice: `docs/exec-plans/active/KBase_AI_Chatbot_v1_M5_Content_Extraction_Chunking_Document_Indexing.md`
* Product spec active: `docs/product-specs/KBase - AI Chatbot v1 Specification.md`; Core spec vẫn là source of truth cho frozen Core behavior
* Design sources active: `KBase - AI Chatbot RAG Architecture.md`, `KBase - AI Chatbot Persistence and Vector Search Design.md`, `KBase - AI Chatbot REST API Specification.md`, `KBase - AI Chatbot Testing Strategy.md`

### Bước Đang Thực hiện

* `M1 Gate PASS: dependencies, typed config, KBase provider ports, deterministic fakes và pgvector-capable local/test foundation đã hoàn tất. Không triển khai RAG behavior, AI schema hoặc endpoint trong M1.`
* `M2 Gate PASS: Flyway V4 AI persistence schema, pgvector repositories, JPA mappings, SQL project isolation, FK/delete rules, quota lock và active-generation guard đã được live-verified; không mở worker/provider/public API.`
* `M3 Gate PASS: durable job claiming/lease/retry, bounded scheduler, document AI intent/delete safety và membership retention hooks đã hoàn tất; không mở provider, extraction, retrieval, public API hoặc destructive purge.`
* `M4 Gate PASS: explicit Spring AI/Google GenAI Gemini chat/embedding adapters, deterministic query/document preparation, strict vector(768), provider-neutral error translation, request-timeout wiring và privacy/logging guards đã hoàn tất; không gọi real Gemini.`
* `M5 READY: handoff sang content extraction, structure-aware chunking và durable document indexing; chưa triển khai code M5 trong phiên này.`

## Đã Hoàn thành và Kiểm chứng

* `Bộ KBase Core v1 design documents đã được đồng bộ vào harness và được dùng làm source-of-truth cho Implementation Plan.`

  * Bằng chứng: `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/index.md`, `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

* `Phạm vi implementation hiện tại được khóa là backend-first; frontend optional được hoãn.`

  * Bằng chứng: `ARCHITECTURE.md`, `docs/PRODUCT_SENSE.md`

* `M0 – Preflight & Execution Baseline đã pass toàn bộ M0 Gate.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M0_Preflight.md`, `.harness/source-doc-registry.json`, `docs/DEVELOPMENT.md`

* `M1 – Project Bootstrap + Local Runtime Skeleton đã pass toàn bộ M1 Gate.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M1_Bootstrap.md`, `pom.xml`, `docker-compose.yml`, `docs/DEVELOPMENT.md`

* `M2 – PostgreSQL / Flyway Schema đã pass toàn bộ M2 Gate: 3 Flyway migrations tạo 10 persistent tables (có users.email_verified_at, không OTP table) với đầy đủ constraint/partial/expression/query index; Flyway apply thành công từ database rỗng trên PostgreSQL 17 Testcontainer; Hibernate ddl-auto=validate pass; FlywayMigrationIntegrityTest 12/12 pass.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M2_PostgreSQL_Flyway.md`, `src/main/resources/db/migration/`, `src/test/java/com/kbase/integration/FlywayMigrationIntegrityTest.java`, `docs/generated/db-schema.md`

* `M3 – JPA Entities & Repositories đã pass toàn bộ M3 Gate: 10 entity persistent, 5 enum, composite-key DocumentTag, 10 feature-local repository, query/projection/fetch graph/lock và project-scoped document specification; Hibernate validate và repository/mapping integration 11/11 pass trên PostgreSQL 17 Testcontainer.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M3_JPA.md`, `src/main/java/com/kbase/`, `src/test/java/com/kbase/integration/JpaMappingRepositoryIntegrationTest.java`, `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`

* `M4 – Shared Error / Request Infrastructure đã pass toàn bộ M4 Gate: centralized ErrorCode và exception hierarchy, ApiErrorResponse, RequestIdFilter/MDC, GlobalExceptionHandler, constraint-name translation và error contract tests.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M4_Shared_Error_Request.md`, `src/main/java/com/kbase/shared/exception/`, `src/main/java/com/kbase/shared/response/`, `src/test/java/com/kbase/shared/exception/`, `src/test/java/com/kbase/integration/ConstraintViolationTranslationIntegrationTest.java`

* `M5 – Redis OTP + Gmail Mail Infrastructure đã pass toàn bộ M5 Gate: OtpStore/RedisOtpStore, OtpService với HMAC-SHA-256 và SecureRandom, MailService/SmtpMailService, typed Redis/SMTP config và hai safe templates.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M5_Redis_OTP_Gmail_Mail.md`, `src/test/java/com/kbase/auth/service/OtpServiceTest.java`, `src/test/java/com/kbase/integration/RedisOtpStoreIntegrationTest.java`, `src/test/java/com/kbase/mail/service/SmtpMailServiceTest.java`

* `M6 – Spring Security + Authentication đã pass toàn bộ M6 Gate: SecurityConfig stateless với public auth endpoints + ADMIN route, JwtService HS256 (sub=userId, systemRole, iat, exp, jti), JwtAuthenticationFilter load User từ DB mỗi request, RestAuthenticationEntryPoint/AccessDeniedHandler với ApiErrorResponse, RefreshSessionService PostgreSQL hash-only, EmailVerificationService phối hợp Redis OTP + Gmail, AuthService và 6 auth endpoints; AuthenticationSecurityIntegrationTest 14/14 trên PostgreSQL/Redis Testcontainers; unit 32/32; full suite 104/104 qua mvn test và mvn clean verify.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M6_Spring_Security_Authentication.md`, `src/main/java/com/kbase/security/`, `src/main/java/com/kbase/auth/`, `src/test/java/com/kbase/integration/AuthenticationSecurityIntegrationTest.java`, `docs/generated/api-schema.md`

* `M7 – User / Project / Membership đã pass toàn bộ M7 Gate: UserService (me/profile/password + admin list/get/status/delete theo dependency rules), ProjectService (create Project+OWNER trong một transaction, list membership-only kể cả ADMIN, get/update với currentUserRole nullable cho ADMIN non-member, admin listing), ProjectAuthorizationService (requireProjectAccess/requireOwner + ADMIN override, không fake ProjectMember), ProjectMemberService (list/remove/leave; OWNER bất khả xâm, documents remain), shared PageResponse/PaginationParser; UserProjectMembershipIntegrationTest 6/6 trên PostgreSQL Testcontainer với real filter chain; unit 20/20; full suite 130/130 qua mvn test và mvn clean verify. Project hard delete (DELETE /projects/{projectId}) cố ý deferred sang M11 sau MinIO.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M7_User_Project_Membership.md`, `src/main/java/com/kbase/user/`, `src/main/java/com/kbase/project/`, `src/test/java/com/kbase/integration/UserProjectMembershipIntegrationTest.java`, `docs/generated/api-schema.md`

* `M8 – Invitation Lifecycle đã pass toàn bộ M8 Gate: InvitationService với create/list/resend/cancel/accept; secure invitation token riêng (32-byte SecureRandom, SHA-256 hash-only trong project_invitations, không OTP); MailService.sendProjectInvitation cho create/resend với rollback khi mail fail; resend thay token + reset expiry; cancel PENDING→CANCELLED không physical delete; accept authenticated với PESSIMISTIC_WRITE tạo ProjectMember(MEMBER) + ACCEPTED + acceptedAt; concurrency test 2 thread chỉ 1 accept thắng; InvitationLifecycleIntegrationTest 5/5 trên PostgreSQL Testcontainer; unit 8/8; full suite 143/143 qua mvn test và mvn clean verify.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M8_Invitation_Lifecycle.md`, `src/main/java/com/kbase/invitation/`, `src/test/java/com/kbase/integration/InvitationLifecycleIntegrationTest.java`, `docs/generated/api-schema.md`

* `M9 – Folder / Category / Tag đã pass toàn bộ M9 Gate: project-scoped folder hierarchy, category và tag; OWNER/ADMIN quản lý folder/category; MEMBER chỉ đọc folder/category; MEMBER được tạo tag; OWNER/ADMIN rename/delete tag; case-insensitive uniqueness; ancestor-walk cycle prevention; folder non-empty/category in-use protection; tag delete chỉ cascade DocumentTag. OrganizationIntegrationTest 6/6, unit 12/12, cross-project/migration regression 23/23; full suite 161/161 qua mvn test và mvn clean verify.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M9_Folder_Category_Tag.md`, `src/main/java/com/kbase/folder/`, `src/main/java/com/kbase/category/`, `src/main/java/com/kbase/tag/`, `src/test/java/com/kbase/integration/OrganizationIntegrationTest.java`, `docs/generated/api-schema.md`

* `M10 – MinIO Storage Infrastructure đã pass toàn bộ M10 Gate: vendor-neutral streaming StorageService, singleton MinioClient configuration, local conditional bucket initializer, typed internal storage exception translation, backend-owned key format và batch-delete per-object result handling. MinIO Testcontainer chứng minh upload/full read/range read/stat/single+batch delete và bucket không versioning-enabled. Unit/config 7/7, MinIO integration 2/2, full suite 169/169 qua mvn test và mvn clean verify.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M10_MinIO_Storage_Infrastructure.md`, `src/main/java/com/kbase/storage/`, `src/test/java/com/kbase/storage/`, `src/test/java/com/kbase/integration/MinioStorageIntegrationTest.java`, `docker-compose.yml`

* `M11 – Document Lifecycle + Project Hard Delete đã pass toàn bộ M11 Gate: single/batch upload qua StorageService với validation/same-project metadata, compensation khi DB persistence fail, document metadata/get/update/download/preview MP4 range, storage-first document hard delete và OWNER/ADMIN project storage-first hard delete. MEMBER đọc mọi document khi còn membership nhưng chỉ modify/delete document của chính mình; OWNER/ADMIN manage toàn bộ; former member mất quyền dù uploadedBy vẫn là User cũ. API matrix 3/3 và lifecycle 1/1 chạy với PostgreSQL+MinIO Testcontainers; full suite 184/184 qua mvn test và mvn clean verify.`

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M11_Document_Lifecycle_Project_Hard_Delete.md`, `src/main/java/com/kbase/document/`, `src/test/java/com/kbase/integration/DocumentApiIntegrationTest.java`, `src/test/java/com/kbase/integration/DocumentLifecycleStorageIntegrationTest.java`, `docs/generated/api-schema.md`

* M12 – Document Search / Pagination / Sorting đã pass toàn bộ M12 Gate: `GET /api/v1/projects/{projectId}/documents` dùng `DocumentSearchCriteria` và `DocumentSearchService`, chỉ search metadata `displayName`, `originalFilename`, `description`, category/tag name; tất cả filter, combined filter, pagination baseline, sort ASC/DESC/whitelist, non-member denial, MEMBER/OWNER/ADMIN access và Project A/B isolation được chứng minh qua PostgreSQL Testcontainer real filter chain. Tag predicates dùng `EXISTS` nên không duplicate document rows; full suite 188/188 qua mvn test và mvn clean verify.

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M12_Document_Search.md`, `src/main/java/com/kbase/document/service/DocumentSearchService.java`, `src/test/java/com/kbase/integration/DocumentSearchIntegrationTest.java`, `docs/generated/api-schema.md`

* M13 – OpenAPI / Swagger đã pass toàn bộ M13 Gate: `config/OpenApiConfig` với metadata "KBase Core API v1", security scheme `bearerAuth` (HTTP bearer JWT), 12 tags canonical và shared 401 customizer dùng `ApiErrorResponse`; 12 controllers / 47 operations được annotate (auth public endpoints không Bearer-required, protected endpoints có security requirement đúng, ADMIN ghi SystemRole.ADMIN, project/document permission rules rõ ràng; con số operations đã được đếm lại trên runtime spec trong M15: 47, không phải 48 như bản ghi M13 ban đầu); multipart `file`/`files` binary + `metadata` JSON, download/preview binary, preview `Range`/`206`/`416` được document đúng; OTP chỉ là email verification OTP, refresh token chỉ là HttpOnly cookie, invitation giữ token riêng; Swagger UI bật local/dev và prod mặc định tắt qua `kbase.openapi.*` không nới `/api/v1/**`; `OpenApiContractIntegrationTest` 19/19 + `OpenApiDisabledIntegrationTest` 2/2; full suite 209/209 qua `mvn test` và `mvn clean verify`.

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M13_OpenAPI_Swagger.md`, `src/main/java/com/kbase/config/OpenApiConfig.java`, `src/test/java/com/kbase/integration/OpenApiContractIntegrationTest.java`, `docs/generated/api-schema.md`

* M14 – Full Docker Runtime Verification đã pass toàn bộ M14 Gate: `Dockerfile` multi-stage non-root stateless + `docker-compose.yml` đủ backend/postgres/minio/redis với healthcheck-gated startup; clean startup từ rỗng với Flyway V1–V3 + Hibernate validate trong container; auth journey (register → Redis OTP state → email qua mail double → verify → login/refresh/logout), core flows (project/organization/upload/download checksum khớp/preview 206+416/search/invitation/MEMBER permissions/hard delete storage-first) chạy qua containerized backend; `postgres_data`/`minio_data` giữ data qua backend restart và force-recreate, Redis recreation mất OTP pending và resend hoạt động; log runtime không leak secret. M14 tìm và fix một bug M11: OWNER-path project hard delete `TransientPropertyValueException` (Hibernate 7.4) → `ProjectRepository.deleteProjectCascade` + regression test OWNER-path; loại generated-password log bằng exclude `UserDetailsServiceAutoConfiguration`; full suite 210/210.

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M14_Docker_Runtime.md`, `Dockerfile`, `docker-compose.yml`, `src/test/java/com/kbase/integration/DocumentApiIntegrationTest.java`, `docs/DEVELOPMENT.md` (M14 verification record)

* M15 – Full Verification / Core v1 Freeze đã pass toàn bộ M15 Gate ngày `2026-09-19`; **Core v1 FROZEN**. Full release gate `mvn -B -ntp clean verify` 210/210 (VERIFY-01..05/11); Docker runtime re-verification từ volume rỗng với mail double (VERIFY-08): Flyway V1–V3 + Hibernate validate trong container, đúng 10 persistent tables + `email_verified_at` + không OTP table, toàn bộ golden journeys qua containerized backend (auth + OTP Redis lifecycle, organization, upload/download checksum, preview 206/416, search matrix, invitation + MEMBER permissions, remove-member, hard delete storage-first OWNER-path), persistence qua restart + force-recreate + Redis recreation (OTP loss acceptable, resend OK), log leak scan 0 hits; VERIFY-09: runtime `/v3/api-docs` = 32 paths / 47 operations khớp contract test + SD-04, không forbidden endpoint; VERIFY-10: static architecture scans clean; VERIFY-07: leakage review pass. M15 tìm và sửa một documentation miscount (48→47 operations trong bản ghi M13) — không có code change.

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M15_Full_Verification_Freeze.md` (Core v1 Freeze Report), `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md` §31, `docs/generated/api-schema.md`

* M16 – Post-Audit Fixes đã pass ngày `2026-09-19` (maintenance slice được owner duyệt sau Full Codebase Audit): fix MEDIUM M-01 (invitation accept-expired giờ persist `EXPIRED` qua `InvitationExpiredException` + `@Transactional(noRollbackFor=...)` khớp design §40; regression test assert DB status + khả năng tạo invitation thay thế) và 7 LOW: L-01 batch upload compensation xóa mỗi key đúng 1 lần (unit test verify count), L-03 runtime OpenAPI description search 400 khớp code, L-04 xóa `JwtProperties.algorithm` unused knob + javadoc `findAllByStatusAndExpiresAtBefore`, L-05 LIKE wildcard escape cho `q` ở documents/my-projects/tags/admin-users (search literal; regression test `%`/`_` qua HTTP), L-06 folder move lock pessimistic cả moved folder + target parent theo UUID order + concurrency test 2 thread ngược chiều (1 thắng, 1 `FOLDER_CYCLE_DETECTED`, không tạo cycle), L-07 SMTP failure log sanitized (chỉ exception type) + negative test, L-08 hygiene (xóa file rỗng `Trạng`, cập nhật `index.md` root, bỏ `KBASE_MAIL_TEST_ENABLED` khỏi `.env.example`). Full gate `mvn -B -ntp clean verify` 213/213. KHÔNG fix: L-02 (PATCH document null semantics — cần product decision) và các INFO item (known limitations/phase mới) — đã phản hồi owner và ghi tech-debt tracker.

  * Bằng chứng: `docs/exec-plans/completed/KBase_Core_v1_M16_Post_Audit_Fixes.md`, full gate log 213/213 trong phiên, `docs/exec-plans/tech-debt-tracker.md`

* `M0 – AI Preflight & Technical Compatibility đã PASS ngày 2026-09-22`: Core baseline re-run pass 213/213 và Compose config pass; Spring AI BOM 2.0.1 + Google GenAI starters, `gemini-2.5-flash`, `gemini-embedding-2`/768, pgvector PostgreSQL 17 image/JDBC strategy và Tika 3.3.2 parser scope đã được khóa; deterministic fake contracts và AI config provisional baseline đã ghi; harness SD-14..SD-19 revalidated. Không có AI runtime/schema/API change.

  * Bằng chứng: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M0_Preflight_and_Technical_Compatibility.md`, Spring AI options probe, pgvector Docker probe (`PGVECTOR_PROBE_PASS`), Tika dependency probe, `.harness/source-doc-registry.json`

* `M1 – AI Runtime Foundation & Provider Ports đã PASS ngày 2026-09-22`: dependencies resolve, AI config bind với disabled-by-default/API key optional, KBase-owned chat/embedding ports không rò vendor types, deterministic fakes pass, pgvector PostgreSQL 17.11 compatibility pass và Core regression pass 228/228. Không có AI migration, entity, endpoint, RAG behavior, provider adapter/call hoặc generated DB/API change.

  * Bằng chứng: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M1_Runtime_Foundation_and_Provider_Ports.md`, `src/main/java/com/kbase/ai/`, `src/test/java/com/kbase/ai/`, `src/test/java/com/kbase/integration/PgVectorCompatibilityIntegrationTest.java`

* `M2 – AI pgvector / Persistence Schema đã PASS ngày 2026-09-22`: Flyway V4 additive migration tạo pgvector extension và 8 AI tables; composite same-project FK, cascade/`SET NULL` lifecycle, status vocabulary, relational/HNSW indexes và active-generation partial unique guard đã được kiểm tra trên live catalog. JPA mappings/repositories, JSONB job mapping, KBase-owned vector JDBC boundary với project/active-version SQL predicates và user-row quota lock đã được verify. Không có public API, worker, provider call hoặc RAG behavior.

  * Bằng chứng: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M2_PgVector_AI_Persistence_Schema.md`, `src/main/resources/db/migration/V4__create_ai_persistence_schema.sql`, `src/test/java/com/kbase/integration/FlywayMigrationIntegrityTest.java`, `src/test/java/com/kbase/integration/FlywayAiUpgradeIntegrationTest.java`, `src/test/java/com/kbase/integration/AiPersistenceIntegrationTest.java`, `docs/generated/db-schema.md`

* `M3 – Durable Job Engine & Core Lifecycle Hooks đã PASS ngày 2026-09-22`: PostgreSQL `SKIP LOCKED` claim/lease/retry/stale recovery, bounded handler registry/scheduler, supported/unsupported document indexing intent, delete-race no-resurrection và membership `CONVERSATION_PURGE` retention hooks đã được verify. Full gate 268/268; không có migration/API/provider/extraction/RAG/destructive purge change.

  * Bằng chứng: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M3_Durable_Job_Engine_Core_Lifecycle.md`, `src/main/java/com/kbase/ai/repository/AiJobClaimRepository.java`, `src/main/java/com/kbase/ai/service/`, `src/test/java/com/kbase/integration/AiJobEngineIntegrationTest.java`, `src/test/java/com/kbase/integration/DocumentAiIntentIntegrationTest.java`, `src/test/java/com/kbase/integration/DocumentAiRollbackIntegrationTest.java`, `src/test/java/com/kbase/integration/AiConversationRetentionIntegrationTest.java`, `src/test/java/com/kbase/integration/AiRetentionTransactionIntegrationTest.java`

## Đã Hoàn thành nhưng Chưa Kiểm chứng

* `Gmail SMTP delivery thật (manual smoke với App Password thật) chưa chạy — automated verification dùng mail double; thuộc optional production smoke.`
* `Frontend vẫn deferred; không có hạng mục M14 còn thiếu xác minh trong backend.`
* `Real Gemini API credential/network smoke chưa chạy — đây là chủ ý và không thuộc M0 release gate; M1+ automated tests phải dùng deterministic fakes.`

## Blocker Hiện tại

| Blocker | Ảnh hưởng | Hướng xử lý | Trạng thái |
| ------- | --------- | ----------- | ---------- |
| (không có blocker Core v1) | — | — | — |

Blocker cũ "Archive không có Git metadata" đã được xử lý: repository hiện có Git history đầy đủ trên nhánh `dev` (commits theo milestone M0–M15); không còn cản trở đối chiếu.

## Thay đổi Quan trọng Gần đây

* `Core v1 dùng email verification OTP lưu short-lived state trong Redis; login chỉ cho phép email đã verify.`
* `Gmail SMTP là mail provider cho OTP verification và project invitation.`
* `PostgreSQL và MinIO local Docker dùng named volume; Redis OTP là ephemeral và không cần durable volume.`
* `Frontend được hoãn; harness hiện được tối ưu cho backend implementation trước.`
* `M0 đã khóa Java 21, Spring Boot 4.1.1 và dependency/implementation baseline; registry SD-01..SD-13 đã được tạo.`
* `M1 đã tạo Maven backend foundation, typed configuration, profiles và Compose dependency skeleton; frontend vẫn deferred.`
* `M2 đã tạo 3 Flyway migrations làm schema source-of-truth (V1 core tables, V2 partial/expression unique indexes, V3 query indexes) và migration integrity test trên PostgreSQL Testcontainer; Hibernate giữ ddl-auto=validate.`
* `M3 đã map 10 bảng vào JPA với quan hệ LAZY, enum STRING, explicit DocumentTag và scalar FK/read-only association views cho Hibernate 7; không thay đổi schema hoặc API.`
* `M4 đã thêm centralized error/request baseline: ErrorCode, exception hierarchy, ApiErrorResponse, RequestIdFilter/MDC, GlobalExceptionHandler và PostgreSQL constraint translation.`
* `M5 đã thêm Redis OTP và Gmail mail infrastructure: Redis protected state/TTL/attempt/cooldown/replacement, OtpService, MailService/SmtpMailService, typed SMTP/Redis settings và safe templates.`
* `M6 đã thêm Spring Security + authentication: SecurityConfig stateless (public auth + ADMIN route), JwtService/JwtAuthenticationFilter (DB user lookup mỗi request), security 401/403 handlers, RefreshSessionService (PostgreSQL SHA-256 hash), EmailVerificationService (Redis OTP + Gmail), AuthService và AuthController cho register/verify-email/resend/login/refresh/logout; test starters webmvc-test/security-test được thêm ở test scope.`
* `M7 đã thêm User/Project/Membership domain: current-user + admin-user APIs (password change revoke sessions, delete theo dependency rules USER_OWNS_PROJECT → USER_HAS_DEPENDENCIES), ProjectAuthorizationService làm điểm vào authorization duy nhất, project create (Project + OWNER membership trong một transaction)/list membership-only/get/update/admin listing, membership list/remove/leave (OWNER bất khả xâm, documents remain); shared PageResponse/PaginationParser với sort whitelist.`
* `M8 đã thêm invitation lifecycle: OWNER/ADMIN tạo invitation qua MailService.sendProjectInvitation với secure token riêng (không OTP); PostgreSQL chỉ lưu SHA-256 token hash; một PENDING per project+email; resend thay token + reset expiry; cancel CANCELLED không physical delete; accept authenticated với PESSIMISTIC_WRITE tạo MEMBER + ACCEPTED + acceptedAt; mail fail khi create rollback invitation; KBASE_INVITATION_ACCEPT_URL cấu hình invitation link.`
* `M9 đã thêm folder/category/tag organization APIs: 12 project-scoped endpoints, service-owned authorization, nested folder hierarchy với ancestor walk chống cycle, case-insensitive uniqueness, child/document non-empty folder delete, category in-use protection và DocumentTag-only tag delete; không đổi Flyway schema.`
* `M10 đã thêm storage infrastructure không public API: StorageService/MinioStorageService stream binary sau boundary adapter; StorageKeyFactory tạo projects/{projectId}/documents/{documentId}.{extension}; local-only auto-create/validation, production-safe defaults, range read và checked batch deletion; không đổi Flyway/API/auth/organization behavior.`
* `M11 đã thêm DocumentController/DocumentService/DocumentAuthorizationService và project hard delete; binary luôn qua StorageService, response không expose storageKey, rename/move không đổi key và preview/download streaming không buffer whole file.`
* `M12 đã thêm project-scoped metadata search endpoint/service/criteria; `DocumentSpecification` bắt buộc project predicate và tag `EXISTS`, `PaginationParser` canonicalize sort whitelist trước persistence, không thêm content/full-text/vector/semantic/AI/RAG search.`
* `M13 đã bật OpenAPI runtime qua springdoc 3.1.1: OpenApiConfig (metadata, bearerAuth, 12 tags canonical, 401 customizer), 12 controllers / 47 operations annotated với security requirements và permission rules, multipart/binary/Range documentation, Swagger UI flags per environment; không đổi runtime API contract.`
* `M14 đã Dockerize runtime: Dockerfile multi-stage, Compose backend service với healthcheck gating, env-driven secrets (`.env` git-ignored); fix bug M11 OWNER-path project hard delete bằng bulk cascade delete + regression test; loại generated-password log.`
* `M15 đã freeze Core v1: full verification matrix (210/210 + Docker runtime re-verification) pass; sửa một documentation miscount 48→47 operations trong bản ghi M13; không có code change; không mở AI/RAG/frontend.`
* `M16 post-audit fixes: invitation EXPIRED giờ persist khi accept hết hạn (resend trên invitation EXPIRED trả 409 INVITATION_NOT_PENDING; recovery = tạo invitation mới vì partial unique index được giải phóng); search `q` trở thành literal matching (%, _, \ được escape) ở documents/projects/tags/admin-users; folder move dùng pessimistic lock 2 rows theo UUID order chống race cycle; batch upload cleanup idempotent-per-key; SMTP failure log chỉ exception type.`
* `M0 AI Preflight (2026-09-22) đã khóa Spring AI 2.0.1 + Google GenAI starters, chat `gemini-2.5-flash`, embedding `gemini-embedding-2`/768 với task-prefix do adapter sở hữu, pgvector `0.8.6-pg17-bookworm` + `com.pgvector:pgvector:0.1.6`, Tika parser modules 3.3.2, config provisional và deterministic fake contracts; M0 decision log vẫn là authority.`
* `M1 AI Runtime Foundation (2026-09-22) đã hoàn tất: typed config, disabled startup, KBase-owned provider ports, deterministic fakes, shared pgvector Testcontainers image và Compose passthrough đều được verify; active slice chuyển sang M2 persistence schema.`
* `M2 AI Persistence (2026-09-22) đã hoàn tất: Flyway V4, 8 AI tables, pgvector/HNSW, JPA repositories, SQL project isolation, FK/delete/status guards và concurrent quota lock đã verify; active slice chuyển sang M3 durable jobs.`
* `M3 AI Durable Jobs (2026-09-22) đã hoàn tất: PostgreSQL claim/lease/retry/stale recovery + advisory-lock active dedup, bounded scheduler registry, document upload intent/delete safety và membership retention `+P7D`/rejoin cancellation đã verify; active slice chuyển sang M4 provider adapters.`
* `M4 AI Provider Adapters (2026-09-22) đã hoàn tất: explicit disabled-by-default Gemini/Spring AI configuration, chat/embedding adapters, deterministic preparation, strict 768 validation, provider-neutral error taxonomy, request-timeout wiring và privacy/logging negative tests; active slice chuyển sang M5 extraction/chunking/indexing.`
* `Enable host-run path (owner yêu cầu, 2026-09-19): `application-local.yml` thêm `spring.config.import: optional:file:.env[.properties]` để `mvn spring-boot:run` tự đọc `.env` (container/prod/test không bị ảnh hưởng — no-op khi không có file, prod/test không load import này); `.env` thêm `KBASE_POSTGRES_PORT=5433` vì PostgreSQL native trên máy chiếm 5432 (Compose map `${KBASE_POSTGRES_PORT:-5432}:5432` — không đổi docker-compose.yml). Cả hai đường chạy đã verify: container `docker compose up -d --build` phục vụ 8080, host `mvn spring-boot:run` Started + api-docs 200 (đã ghi vào DEVELOPMENT.md).`

Không ghi toàn bộ danh sách file đã sửa. Git history chịu trách nhiệm lưu thay đổi code chi tiết.

## Verification Gần nhất

| Lệnh hoặc kiểm tra | Kết quả       | Thời điểm | Ghi chú |
| ------------------ | ------------- | --------- | ------- |
| `mvn -B -ntp "-Dtest=AiGeminiProviderConfigurationTest,AiProviderErrorTranslatorTest,AiProviderPrivacyTest,SpringAiGeminiChatAdapterTest,SpringAiGeminiEmbeddingAdapterTest" test` (M4 targeted) | Đạt | 2026-09-22 | 22/22: disabled/enabled configuration, safe key validation/no network, chat mapping/evidence isolation, query/document preparation, strict 768/non-finite rejection, error categories và privacy negative tests |
| `mvn -B -ntp clean verify` (M4 final) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 290 tests, 0 failures/errors/skips; compile/package/repackage pass; shutdown Hikari/Testcontainers warnings không làm fail suite |
| `docker compose -f docker-compose.yml config --quiet` + `git diff --check` (M4) | Đạt | 2026-09-22 | Compose hợp lệ và không có whitespace error; không đổi topology, migration hoặc generated DB/API docs |
| `mvn -B -ntp "-Dtest=PgVectorCompatibilityIntegrationTest" test` (M1 final) | Đạt | 2026-09-22 | 1/1 trên pgvector 0.8.6 / PostgreSQL 17.11: extension, `vector(768)`, JDBC `PGvector` binding, cosine ordering và HNSW `vector_cosine_ops` pass |
| M1 AI targeted suite | Đạt | 2026-09-22 | 15/15: `AiPropertiesBindingTest`, `AiProviderModelTest`, `FakeAiChatModelTest`, `FakeAiEmbeddingModelTest`, `KBaseApplicationContextSmokeTest`; disabled path không tạo Spring AI chat/embedding model bean |
| `mvn -B -ntp clean verify` (M1 final) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 228 tests, 0 failures/errors/skips; jar/package và Spring Boot repackage pass |
| `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest,FlywayAiUpgradeIntegrationTest,AiPersistenceIntegrationTest" test` (M2 targeted) | Đạt | 2026-09-22 | 36 tests, 0 failures/errors/skips: integrity 13/13, mapping 11/11, upgrade 1/1, AI persistence 11/11 |
| `mvn -B -ntp clean verify` (M2 gate, trước bổ sung regression assertions) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 238 tests, 0 failures/errors/skips; package/repackage pass |
| `docker compose -f docker-compose.yml config --quiet` + `git diff --check` (M2) | Đạt | 2026-09-22 | Compose exit 0; diff check không có whitespace error |
| `mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree-m1-01.txt" "-DoutputType=text"` | Đạt | 2026-09-22 | Spring AI BOM 2.0.1, Google GenAI starters, transitive Google GenAI 1.65.0 và pgvector JDBC 0.1.6 resolve |
| `docker compose -f docker-compose.yml config --quiet` (M1 final) | Đạt | 2026-09-22 | Compose hợp lệ; pgvector default, named volume/healthcheck và AI environment passthrough resolve |
| M3 targeted suites | Đạt | 2026-09-22 | `AiJobEngineIntegrationTest` 9/9, `AiJobSchedulerTest` 5/5, `DocumentAiIntentIntegrationTest` 6/6, `DocumentAiRollbackIntegrationTest` 2/2, `AiConversationRetentionIntegrationTest` 4/4, `AiRetentionTransactionIntegrationTest` 1/1 trên PostgreSQL 17.11/pgvector Testcontainers |
| `mvn -B -ntp test` (M3 final) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 268 tests, 0 failures/errors/skips |
| `mvn -B -ntp clean verify` (M3 final) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 268 tests, 0 failures/errors/skips; compile/package/repackage pass |
| M3 scope/gate audit | Đạt | 2026-09-22 | Compose config + `git diff --check` pass; V4/generated docs unchanged; no provider/network/extraction/RAG/API/purge behavior or MinIO SDK import in AI code |
| `mvn -B -ntp clean verify` (AI M0 baseline) | Đạt | 2026-09-22 | `BUILD SUCCESS`; 213 tests, 0 failures/errors/skips trên `feat-AI` |
| `docker compose -f docker-compose.yml config --quiet` (AI M0 baseline) | Đạt | 2026-09-22 | Compose hiện tại hợp lệ; không thay đổi topology trong M0 |
| Spring AI/Gemini options probe | Đạt | 2026-09-22 | Spring AI 2.0.1 compile/probe giữ `gemini-embedding-2` và `dimensions=768`; không gọi API thật |
| pgvector Docker compatibility probe | Đạt | 2026-09-22 | `pgvector/pgvector:0.8.6-pg17-bookworm`; extension, `vector(768)`, cosine ordering và HNSW `vector_cosine_ops` pass; temporary container đã xóa |
| Tika dependency-resolution probe | Đạt | 2026-09-22 | Tika 3.3.2 core + PDF/Microsoft/text parser modules resolve; chưa có extraction implementation |
| Harness/source registry revalidation | Đạt | 2026-09-22 | 19 docs, SD-14..SD-19/path/precedence/scope guard pass; không stale current blocker |
| `mvn -B -ntp clean verify` (M16 final) | Đạt | 2026-09-19 | BUILD SUCCESS; 213 tests, 0 failures/errors/skips (210 baseline + 3 regression: batch cleanup, wildcard-literal search, folder-move concurrency) |
| Targeted M16 suites | Đạt | 2026-09-19 | Unit 48/48 (Invitation/Document/SmtpMail/ConfigBinding/Folder/Tag/Project/JwtService/User/ContextSmoke); Integration 26/26 (InvitationLifecycle 5/5 gồm assert DB EXPIRED + tạo invitation thay thế, DocumentSearch 3/3 gồm literal wildcard, Organization 7/7 gồm concurrentOppositeFolderMovesCreateNoCycle, JpaMapping 11/11) |
| `mvn -B -ntp clean verify` (M15 final) | Đạt | 2026-09-19 | BUILD SUCCESS; 210 tests, 0 failures/errors/skips; M15 release gate VERIFY-01/02/03/04/05/11 |
| Docker runtime verification (M15) | Đạt | 2026-09-19 | Clean startup từ volume rỗng (compose config base+mail-test OK; healthcheck-gated); Flyway V1–V3 + Hibernate validate trong container; 10 tables + `email_verified_at` + không OTP table; auth journey (Redis OTP key/TTL/HMAC + mail double + verify + login/refresh/logout + refresh-sau-logout 401); core flows (project OWNER, folder/category/tag, upload PDF/MP4/MD 201 không expose storageKey, download MD5 khớp, preview inline 200, MP4 206/416, search q/filter/combined/sort/whitelist 400/clamp 100); invitation accept MEMBER; MEMBER matrix + admin 403 + anonymous 401; remove-member access loss + documents remain; document + OWNER-path project hard delete storage-first, bucket rỗng, cascade đủ |
| Persistence (M15) | Đạt | 2026-09-19 | backend restart: Flyway "Schema is up to date", download checksum khớp; postgres/minio force-recreate giữ volume: data + Flyway history + object download MD5 khớp; Redis recreate: OTP state mất, stale verify 400 OTP_EXPIRED, resend 204, verify + login 200 |
| Log leak scan + JWT probes (M15) | Đạt | 2026-09-19 | `docker compose logs backend`: 0 hits cho password/JWT/refresh token/OTP/invitation token/App Password/MinIO secret/storageKey/SQL; tampered + garbage JWT → 401 |
| Static audits (M15) | Đạt | 2026-09-19 | VERIFY-09/10 + consistency audit: runtime spec 32 paths/47 operations = contract test = SD-04; error catalog ↔ ErrorCode 68 codes; compose ↔ DEPLOYMENT; db-schema ↔ migrations; architecture scans clean |
| Docker runtime smoke (M14) | Đạt | 2026-09-18 | Clean build + startup từ rỗng; Flyway V1–V3 + Hibernate validate trong container; auth journey + core flows + hard delete storage-first qua containerized backend; persistence `postgres_data`/`minio_data` qua backend restart + force-recreate; Redis ephemeral + resend; log leak scan 0 hits |
| `mvn -B -ntp "-Dtest=ProjectServiceTest,DocumentApiIntegrationTest" test` (sau fix M14) | Đạt | 2026-09-18 | 10/10 gồm regression OWNER-path project delete trên PostgreSQL+MinIO Testcontainers |

| `mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` | Đạt | 2026-09-18 | 21/21; spec 32 paths/47 operations (đếm lại trên runtime trong M15), bearerAuth, public/protected/ADMIN requirements, multipart/binary/Range, ApiErrorResponse, sensitive-field absence, AI-free, disabled-flags 401 |
| `mvn -B -ntp test` (M13 final) | Đạt | 2026-09-18 | Full suite 209 tests, 0 failures/errors/skips |
| `mvn -B -ntp clean verify` (M13 final) | Đạt | 2026-09-18 | BUILD SUCCESS; 209 tests; compile/package/repackage pass; M13 Gate pass |

| `mvn -B -ntp clean verify` (M12 final) | Đạt | 2026-09-18 | 188 tests, 0 failures/errors/skips; compile/package/repackage pass; M12 Gate pass |

| Lệnh hoặc kiểm tra | Kết quả       | Thời điểm | Ghi chú |
| ------------------ | ------------- | --------- | ------- |
| `mvn -B -ntp test` (baseline M7 trước khi sửa mã) | Đạt | 2026-09-18 | 130 tests, 0 failures — M7 Gate xác nhận pass, không blocker |
| `mvn -B -ntp "-Dtest=InvitationServiceTest" test` | Đạt | 2026-09-18 | M8 unit 8/8: create hash-only + mail link, member/pending conflicts, mail fail propagate, resend replace+reset, cancel không physical delete, accept full error matrix (not found/not pending/expired→EXPIRED/mismatch) |
| `mvn -B -ntp "-Dtest=InvitationLifecycleIntegrationTest" test` | Đạt | 2026-09-18 | 5/5 trên PostgreSQL 17 Testcontainer với real SecurityFilterChain: OWNER 201 PENDING + mail URL chứa token + body/DB không lộ token/hash, MEMBER 403, ADMIN override 201, member/duplicate 409, resend token cũ vô hiệu + reset expiry, cancel CANCELLED còn row, expired/mismatch/double-accept đúng contract, mail fail 503 + rollback, concurrency 2 thread chỉ 1 accept thắng |
| `mvn -B -ntp "-Dtest=UserServiceTest,ProjectAuthorizationServiceTest,ProjectServiceTest,ProjectMemberServiceTest" test` | Đạt | 2026-09-18 | M7 unit 20/20: user profile/password/status/delete dependency order, authorization contracts (404/403, ADMIN override role null), create OWNER atomically, remove/leave matrix |
| `mvn -B -ntp "-Dtest=UserProjectMembershipIntegrationTest" test` | Đạt | 2026-09-18 | 6/6 trên PostgreSQL 17 Testcontainer với real SecurityFilterChain: creator OWNER single trong cùng tx, GET /projects membership-only + role filter, non-member 403, MEMBER đọc không sửa được, OWNER/ADMIN update, ADMIN override currentUserRole null, remove/leave rules + documents remain + uploadedBy vẫn là User, me/profile/password (revoke session), admin users (q/pagination/INVALID_USER_STATUS/disable revoke/delete order), admin projects không fake role |
| `mvn -B -ntp test` (M8 final) | Đạt | 2026-09-18 | Full suite 143 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp clean verify` (M8 final) | Đạt | 2026-09-18 | Full suite 143 tests, 0 failures, 0 errors, 0 skipped; compile/package và Spring Boot jar repackage pass |
| Static M8 scope/leakage review | Đạt | 2026-09-18 | Invitation không dùng OTP (chỉ javadoc ghi rõ); không log/expose raw token/hash; cancel không physical delete; không controller→repository; không cascade project knowledge khi xóa user; không đổi auth/OTP/Redis/Gmail flow M5–M7; response không expose passwordHash |
| `mvn -B -ntp "-Dtest=FolderServiceTest,CategoryServiceTest,TagServiceTest" test` | Đạt | 2026-09-18 | M9 unit 12/12: authorization, case-insensitive duplicate, parent isolation, cycle prevention, non-empty folder, category in-use/delete và MEMBER tag create/mutation rules |
| `mvn -B -ntp "-Dtest=OrganizationIntegrationTest" test` | Đạt | 2026-09-18 | 6/6 trên PostgreSQL 17 + Redis Testcontainers với real SecurityFilterChain; list matrix, folder/category/tag permissions, uniqueness, cross-project parent, self/descendant cycle, non-empty/in-use delete và DocumentTag-only cascade |
| `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test` | Đạt | 2026-09-18 | 23/23; PostgreSQL/JPA same-project composite FK, self-parent CHECK, indexes và mapping regression vẫn pass |
| `mvn -B -ntp test` (M9 final) | Đạt | 2026-09-18 | Full suite 161 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp clean verify` (M9 final) | Đạt | 2026-09-18 | BUILD SUCCESS; 161 tests pass, compile/package và Spring Boot jar repackage pass |
| Static M9 scope/leakage review | Đạt | 2026-09-18 | Không frontend/M10/M11, không migration, không controller→repository, không weakening same-project constraints, không đổi auth/project/membership/invitation; document upload/lifecycle chưa implement |
| `mvn -B -ntp "-Dtest=StorageKeyFactoryTest,MinioStorageServiceTest,ConfigurationPropertiesBindingTest" test` | Đạt | 2026-09-18 | M10 unit/config 7/7: port không rò MinIO SDK, key safety, typed missing/unavailable mapping, per-object batch failure và config timeout/init binding |
| `mvn -B -ntp "-Dtest=MinioStorageIntegrationTest" test` | Đạt | 2026-09-18 | 2/2 với real `quay.io/minio/minio:latest` Testcontainer: bucket init/unversioned, stream upload/full/range read, stat, single/batch delete |
| `mvn -B -ntp test` (M10 final) | Đạt | 2026-09-18 | Full suite 169 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp clean verify` (M10 final) | Đạt | 2026-09-18 | BUILD SUCCESS; 169 tests, Spring Boot jar repackage pass |
| Static M10 scope/boundary review | Đạt | 2026-09-18 | SDK chỉ trong storage adapter/config; no document API/M11/frontend/schema; no bucket policy/versioning/retention mutation; `minio_data:/data` preserved |
| `mvn -B -ntp "-Dtest=DocumentApiIntegrationTest" test` | Đạt | 2026-09-18 | 3/3 PostgreSQL+MinIO+real SecurityFilterChain: upload/batch all-or-fail validation, same-project metadata, ownership/former-member/OWNER/ADMIN, stream/preview/range và project cascade |
| `mvn -B -ntp "-Dtest=DocumentLifecycleStorageIntegrationTest" test` | Đạt | 2026-09-18 | 1/1 PostgreSQL+MinIO: upload PDF → stream → storage-first document delete → DB/DocumentTag cascade/object absent |

## Rủi ro và Technical Debt Liên quan

* Technical debt tracker: `docs/exec-plans/tech-debt-tracker.md`
* Rủi ro hiện tại:

 * `M0, M1, M2, M3 và M4 đã đóng; agent tiếp theo phải dùng completed plans và active M5 slice, không tự đoán lại dependency/model/image/schema/provider contract.`
  * `Generated API schema hiện chưa được sinh từ runtime; phần auth (M6), user/project/membership (M7), invitation (M8) và folder/category/tag (M9) đã đồng bộ thủ công từ source code đã verify và không được dùng thay design source-of-truth; generated DB schema đã đồng bộ với migration đã verify nhưng chưa có generator tự động.`
  * `M3 mapping dùng scalar FK + read-only association view để tương thích Hibernate 7; Flyway composite FK vẫn là lớp integrity authoritative và đã được negative-test.`
  * `Full backend runtime với Flyway trên Compose local đã được verify trong M14 và re-verify trong M15; không coi migration/auth/authz Testcontainer verification là full application runtime smoke.`
  * `JWT access token không có revocation: logout chỉ revoke refresh; access token cũ còn hiệu lực đến khi hết hạn trừ khi filter chặn theo DB status (DISABLED). Đây là baseline Core v1 đã chốt trong SD-07.`
  * `OpenAPI Markdown snapshot (docs/generated/api-schema.md) vẫn được đồng bộ thủ công từ runtime /v3/api-docs; contract tests chặn drift ở mức security/multipart/binary/error-code nhưng chi tiết field-level trong Markdown phụ thuộc kỷ luật sync cùng thay đổi API.`
  * `Gmail SMTP delivery thật (manual smoke với credential thật) chưa chạy; automated path dùng mail double.`
  * `Google GenAI 1.65.0 trên selected Spring AI client path chỉ cung cấp request timeout qua HttpOptions, chưa có independent connect-timeout surface; typed connect-timeout vẫn được giữ cho future transport customization và không được claim là active.`
  * `Core v1 đã FROZEN ngày 2026-09-19 (M15 pass). Mọi thay đổi kế tiếp cần phase/plan mới được duyệt.`

## Bước Tiếp theo

1. `M5 AI-IDX-01..06: content extraction, structure-aware chunking và durable document indexing theo active handoff plan; chưa mở retrieval, conversation/Guide runtime hoặc public API.`
2. `Giữ Core v1 frozen; M5 dùng KBase-owned ports, StorageService và deterministic tests; không gọi real Gemini trong automated tests.`
3. `Không sửa generated DB/API snapshot trong M4; chỉ cập nhật DB snapshot nếu M5 có schema change được phê duyệt và verify.`
4. `Core tech-debt cũ (PATCH null semantics, CI, Gmail production smoke, Redis observability) vẫn theo tracker; không kéo vào AI scope nếu active plan không yêu cầu.`


## Quy tắc Cập nhật

* Cập nhật khi trạng thái repository thay đổi đáng kể.
* Cập nhật trước khi kết thúc một phiên làm việc dài.
* Chỉ ghi trạng thái đã được xác nhận.
* Không sao chép toàn bộ execution plan vào file này.
* Không ghi quyết định thiết kế dài; liên kết đến `docs/design-docs/`.
* Không ghi chi tiết thay đổi code; dùng Git history.
* Khi có nhiều execution plan, phải chỉ rõ plan đang được ưu tiên.
