# Trạng thái Hiện tại

> File này cung cấp ảnh chụp ngắn gọn về trạng thái hiện tại của repository.
> Chỉ ghi thông tin đã được xác nhận. Không dùng file này để thay thế execution plan, product spec hoặc Git history.

## Cập nhật Lần cuối

* Ngày cập nhật: `2026-09-18`
* Người hoặc agent cập nhật: `Codex - M11 Document Lifecycle + Project Hard Delete`
* Nhánh hiện tại: `N/A - archive harness chưa gắn với repository implementation`
* Commit gần nhất đã kiểm chứng: `N/A`

## Trạng thái Tổng quan

| Khu vực          | Trạng thái    | Bằng chứng hoặc ghi chú |
| ---------------- | ------------- | ----------------------- |
| Build            | Ổn định      | `mvn -B -ntp clean verify` và `mvn -B -ntp test` đã pass (184 tests) |
| Frontend         | Không áp dụng | Optional theo đề bài; hoãn khỏi phase implementation hiện tại |
| Backend          | Ổn định | M0–M11 đã hoàn tất; document lifecycle và storage-aware project hard delete đã xong; M12 search và M13 OpenAPI runtime chưa mở |
| Database         | Ổn định      | Flyway 3 migrations tạo 10 persistent tables; migration integrity test 12/12 pass trên PostgreSQL 17 Testcontainer; M2 Gate PASS; M8 và M9 không đổi schema |
| API contract     | Ổn định | M11 document/project-delete contract đã implement, API-test và đồng bộ thủ công vào `docs/generated/api-schema.md`; OpenAPI runtime thuộc M13 |
| Integration      | Đang thực hiện | PostgreSQL/Redis Testcontainers, fake SMTP, auth, user/project/membership, invitation, organization và MinIO adapter suite đã verify; full backend runtime còn ở M14 |
| Unit test        | Đang thực hiện | 169 tests pass qua full suite, gồm M10 storage/config unit tests |
| Integration test | Ổn định | PostgreSQL/Redis/fake SMTP suites, MinIO adapter 2/2, M11 lifecycle 1/1 và M11 real-filter-chain PostgreSQL+MinIO API matrix 3/3 đã pass |
| End-to-end test  | Không áp dụng | Frontend E2E hoãn; backend critical workflows dùng API/integration tests |
| Security checks  | Ổn định | M11 verify MEMBER read/own-write, OWNER/ADMIN override và former-member revocation qua API; storage key không lộ ra response |
| Deployment       | Đang thực hiện | M1 Compose dependency skeleton và M5 Redis ephemeral runtime đã verified; full backend wiring/Dockerfile thuộc M14 |

Trạng thái nên dùng:

* `Ổn định`
* `Đang thực hiện`
* `Bị chặn`
* `Có lỗi`
* `Chưa đánh giá`
* `Không áp dụng`

## Công việc Đang Hoạt động

### Ưu tiên Hiện tại

* Mục tiêu: `Triển khai KBase Core v1 backend theo source-of-truth và Implementation Plan đã duyệt.`
* Execution plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`
* Active slice: `M11 đã pass và sẽ được chuyển completed; M12 chưa được mở.`
* Product spec liên quan: `docs/product-specs/KBase - Core v1 Specification.md`
* Design document liên quan: `docs/design-docs/index.md`

### Bước Đang Thực hiện

* `M11 Gate đã pass ngày 2026-09-18. Dừng sau M11; không mở M12.`

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

## Đã Hoàn thành nhưng Chưa Kiểm chứng

* `Không có hạng mục M11 còn thiếu xác minh. M12 search/filter/pagination và M13 OpenAPI runtime không thuộc M11.`

## Blocker Hiện tại

| Blocker | Ảnh hưởng | Hướng xử lý | Trạng thái |
| ------- | --------- | ----------- | ---------- |
| Archive không có Git metadata | Không có branch/commit history để đối chiếu | Giữ bằng chứng trong execution plan và tiếp tục theo archive state | Mở - giới hạn harness |

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

Không ghi toàn bộ danh sách file đã sửa. Git history chịu trách nhiệm lưu thay đổi code chi tiết.

## Verification Gần nhất

| `mvn -B -ntp clean verify` (M11 final) | Đạt | 2026-09-18 | 184 tests, 0 failures/errors/skips; compile/package/repackage pass; M11 Gate pass |

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

* `Agent chạy trước M0 có thể tự đoán version hoặc command; phải tuân thủ source registry và active slice.`
  * `Generated API schema hiện chưa được sinh từ runtime; phần auth (M6), user/project/membership (M7), invitation (M8) và folder/category/tag (M9) đã đồng bộ thủ công từ source code đã verify và không được dùng thay design source-of-truth; generated DB schema đã đồng bộ với migration đã verify nhưng chưa có generator tự động.`
  * `M3 mapping dùng scalar FK + read-only association view để tương thích Hibernate 7; Flyway composite FK vẫn là lớp integrity authoritative và đã được negative-test.`
  * `Full backend runtime với Flyway trên Compose local vẫn thuộc M14; không coi migration/auth/authz Testcontainer verification là full application runtime smoke.`
  * `JWT access token không có revocation: logout chỉ revoke refresh; access token cũ còn hiệu lực đến khi hết hạn trừ khi filter chặn theo DB status (DISABLED). Đây là baseline Core v1 đã chốt trong SD-07.`
  * `M12 document metadata search/filter/pagination và M13 OpenAPI runtime vẫn là scope sau M11.`

## Bước Tiếp theo

1. `M11 đã hoàn thành; không bắt đầu M12 trong task này.`
2. `Khi mở M12, giữ DocumentService/StorageService, authorization và hard-delete baseline M11 qua regression tests.`
3. `Giữ frontend deferred.`

## Quy tắc Cập nhật

* Cập nhật khi trạng thái repository thay đổi đáng kể.
* Cập nhật trước khi kết thúc một phiên làm việc dài.
* Chỉ ghi trạng thái đã được xác nhận.
* Không sao chép toàn bộ execution plan vào file này.
* Không ghi quyết định thiết kế dài; liên kết đến `docs/design-docs/`.
* Không ghi chi tiết thay đổi code; dùng Git history.
* Khi có nhiều execution plan, phải chỉ rõ plan đang được ưu tiên.
