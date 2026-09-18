# Môi trường Phát triển

## Mục đích

Tài liệu này cung cấp quy trình chuẩn để cài đặt, cấu hình, chạy, kiểm tra và xử lý lỗi dự án trên môi trường local.

## Trạng thái Hiện tại

Backend foundation của KBase đã được bootstrap trong M1. M1 Gate đã pass ngày `2026-09-17`.

M2 – PostgreSQL / Flyway Schema đã hoàn tất ngày `2026-09-17`: 3 Flyway migrations tạo 10 persistent tables với đầy đủ constraint, partial/expression unique index và query index theo Physical Database Design; migration integrity test 12/12 pass trên PostgreSQL 17 Testcontainer; Hibernate `ddl-auto=validate` pass.

M3 – JPA Entities & Repositories đã hoàn tất ngày `2026-09-17`: 10 entity persistent, 5 enum, `DocumentTagId`, 10 feature-local repository, projection/query/fetch graph/lock và document specification đã được implement; mapping integration test 11/11 pass trên PostgreSQL 17 Testcontainer với Flyway từ database rỗng và Hibernate `ddl-auto=validate`. Không có OTP entity/repository.

M4 – Shared Error / Request Infrastructure đã hoàn tất ngày `2026-09-17`: `ErrorCode`, exception hierarchy, `ApiErrorResponse`, `RequestIdFilter`/MDC, `GlobalExceptionHandler` và PostgreSQL constraint translator đã được implement; targeted M4 suite 16/16, full suite 41/41 và `mvn clean verify` pass.

M5 – Redis OTP + Gmail Mail Infrastructure đã hoàn tất ngày `2026-09-17`: `OtpStore`/`RedisOtpStore`, `OtpService`, `MailService`/`SmtpMailService`, typed Redis/SMTP configuration và safe templates đã được implement; OTP unit 6/6, Redis 7.4 Testcontainer 6/6, fake SMTP 5/5 và full suite 58/58 pass. Registration/login/auth flow, security handler, MinIO, feature API và frontend vẫn deferred theo scope.

M6 – Spring Security + Authentication đã hoàn tất ngày `2026-09-17`: `SecurityConfig` stateless (6 public auth endpoints, `/api/v1/admin/**` → ADMIN, còn lại authenticated), `JwtService` HS256 (sub=userId, systemRole, iat, exp, jti), `JwtAuthenticationFilter` load User từ DB mỗi request, `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` với `ApiErrorResponse`, `RefreshSessionService` PostgreSQL hash-only, `EmailVerificationService` phối hợp Redis OTP + Gmail và `AuthService`/`AuthController` cho register/verify-email/resend/login/refresh/logout đã được implement; auth integration 14/14 trên PostgreSQL+Redis Testcontainers với real filter chain, unit 32/32 và full suite 104/104 pass. Feature API users/projects/documents, MinIO, OpenAPI runtime và frontend vẫn deferred theo scope.

M7 – User / Project / Membership đã hoàn tất ngày `2026-09-18`: current-user APIs (`GET/PATCH /users/me`, `PUT /users/me/password` kèm revoke refresh sessions), admin user APIs (list/get/status/delete theo dependency rules), project APIs (create Project+OWNER trong một transaction, list membership-only kể cả ADMIN, get/update với ADMIN override và `currentUserRole` nullable, admin listing), membership APIs (list/remove/leave với OWNER bất khả xâm và documents remain) và `ProjectAuthorizationService` đã được implement; authorization matrix integration 6/6 trên PostgreSQL Testcontainer, unit 20/20 và full suite 130/130 pass. Invitation (M8), folder/category/tag (M9), MinIO/document (M10/M11), project hard delete (M11), search (M12), OpenAPI runtime (M13) và frontend vẫn deferred theo scope tại thời điểm M7.

M8 – Invitation Lifecycle đã hoàn tất ngày `2026-09-18`: `POST /api/v1/projects/{projectId}/invitations`, `GET .../invitations`, `POST .../invitations/{id}/resend`, `DELETE .../invitations/{id}` và `POST /api/v1/invitations/accept` đã được implement qua `InvitationService` + `InvitationTokens`. Invitation dùng secure token riêng (không OTP): raw token chỉ trong email link (`KBASE_INVITATION_ACCEPT_URL`) và request accept; PostgreSQL chỉ lưu SHA-256 hash; resend thay token + reset expiry; cancel CANCELLED không physical delete; accept authenticated với PESSIMISTIC_WRITE tạo `ProjectMember(MEMBER)` + `ACCEPTED` + `acceptedAt`; mail fail khi create rollback invitation. Invitation integration 5/5 (gồm concurrency 2 thread), unit 8/8 và full suite 143/143 pass. M9 đã hoàn tất; MinIO/document (M10/M11), project hard delete (M11), search (M12), OpenAPI runtime (M13) và frontend vẫn deferred theo scope.

M9 – Folder / Category / Tag đã hoàn tất ngày `2026-09-18`: 12 project-scoped endpoints qua `FolderController`, `CategoryController` và `TagController`; folder nested hierarchy với parent cùng project, sibling uniqueness case-insensitive, ancestor-walk cycle prevention và delete chỉ khi không có child/document; category OWNER/ADMIN CRUD với uniqueness case-insensitive và `CATEGORY_IN_USE`; tag MEMBER create/list, OWNER/ADMIN rename/delete với DocumentTag-only cascade. Unit 12/12, `OrganizationIntegrationTest` 6/6, cross-project/migration regression 23/23 và full suite 161/161 pass. Không implement document upload/lifecycle, MinIO, frontend hoặc M10/M11 trong slice này.

M10 – MinIO Storage Infrastructure đã hoàn tất ngày `2026-09-18`: `StorageService` là port streaming không chứa authorization/document logic; `MinioStorageService` cô lập SDK, hỗ trợ upload/full get/range get/stat/delete/deleteAll, dịch lỗi provider thành internal storage exceptions và luôn consume kết quả batch-delete. `StorageKeyFactory` tạo `projects/{projectId}/documents/{documentId}.{extension}` từ UUID và extension lowercase đã validate. `MinioClient` là Spring singleton với timeout cấu hình; local profile có thể tạo/validate bucket, base/production mặc định không auto-create, test profile tắt startup validation. Adapter không đổi policy/versioning/retention/object lock; bucket test được xác nhận không versioning-enabled. Unit/config 7/7, MinIO Testcontainer 2/2 và full suite 169/169 pass qua `mvn test` và `mvn clean verify`. Không implement document lifecycle/API, project hard delete hoặc frontend/M11.

M12 – Document Search / Pagination / Sorting đã hoàn tất ngày `2026-09-18`: `GET /api/v1/projects/{projectId}/documents` dùng `DocumentSearchService` + `DocumentSearchCriteria`; query luôn authorize bằng `ProjectAuthorizationService.requireProjectAccess` và luôn có predicate `projectId`. Search chỉ metadata (`displayName`, `originalFilename`, `description`, category/tag name); tag dùng `EXISTS` để không duplicate document. Baseline `page=0`, `size=20`, clamp 100 và sort canonical whitelist `displayName`, `createdAt`, `updatedAt`, `sizeBytes` được giữ. `DocumentSearchIntegrationTest` 2/2 trên PostgreSQL Testcontainer real filter chain, focused regression 16/16, full suite 188/188 và `mvn clean verify` pass. Không đổi schema, lifecycle, MinIO, authorization baseline hay frontend; M13 OpenAPI runtime vẫn deferred.

Docker CLI và Docker daemon hiện khả dụng. Compose dependency startup, readiness và restart smoke của PostgreSQL/Redis/MinIO đã được xác minh ngày `2026-09-17`; PostgreSQL và Redis Testcontainers đã chạy cho migration/OTP verification; fake SMTP đã chạy cho mail verification; MinIO Testcontainers và full backend runtime vẫn chờ milestone tương ứng.

## Technical baseline đã chốt tại M0

M0 Gate đã pass ngày `2026-09-17`. Baseline dưới đây là baseline implementation cho backend Core v1; runtime dependency evidence được bổ sung trong M1 follow-up.

| Thành phần | Baseline đã chốt |
|---|---|
| Java | Release `21`; runtime được quan sát là `21.0.11` |
| Build tool | Maven `3.9.15`; chưa có Maven wrapper trong archive |
| Spring Boot | `4.1.1` |
| Web starter | `spring-boot-starter-webmvc` `4.1.1` |
| Hibernate ORM | `7.4.5.Final` (Boot-managed) |
| Spring Data Redis | `4.1.1` (Boot-managed) |
| Lettuce | `7.5.2.RELEASE` (Boot-managed) |
| springdoc | `3.1.1` (`springdoc-openapi-starter-webmvc-ui`) |
| Testcontainers | `2.0.5`; Redis/MinIO dùng `GenericContainer`, PostgreSQL dùng module tương ứng |
| MinIO Java SDK | `9.0.3` |
| JWT | JJWT `0.13.0` (`api`, `impl`, `jackson`), HMAC/HS256 với secret từ environment |
| PostgreSQL JDBC | `42.7.13` |
| Flyway | `12.4.0` |
| MIME detection | Apache Tika `3.3.2` |
| Mail | `spring-boot-starter-mail` `4.1.1`; Jakarta Mail API/Angus Mail do Boot quản lý |

Evidence tương thích là Maven dependency probe tạm thời: `validate` và `dependency:tree` đều pass với exit code `0`. Probe đã được gỡ khỏi repository sau verification; không dùng nó như product project.

## Yêu cầu hệ thống

Baseline đã được chốt:

- Backend: Java `21` + Spring Boot `4.1.1`.
- Build tool: Maven `3.9.15`; wrapper được ưu tiên nếu project bootstrap tạo `mvnw`.
- Database: PostgreSQL.
- OTP store: Redis.
- Object storage: MinIO.
- Email delivery: Gmail SMTP bên ngoài Docker.
- Container runtime: Docker Engine/Desktop + Docker Compose.
- Frontend: Optional, chưa thuộc phase hiện tại.
- Port mặc định M1 được định nghĩa trong Docker Compose/application config; thay đổi runtime phải theo các giá trị cấu hình thực tế, không hard-code ngoài config.

Môi trường M0 ghi nhận Docker CLI `29.8.0` nhưng daemon chưa khả dụng tại thời điểm preflight. Sau khi Docker được bật, M1 đã xác minh local dependency runtime bằng Compose, M2/M3/M4 đã xác minh PostgreSQL Testcontainers, và M5 đã xác minh Redis Testcontainer + fake SMTP; MinIO Testcontainers và full backend runtime vẫn để cho milestone tương ứng.

Không để thông tin thực tế của runtime chỉ tồn tại trong lịch sử chat hoặc trí nhớ cá nhân.

## Thiết lập ban đầu

M1 đã tạo Maven project và cấu hình typed properties/profile. Dependency local vẫn phải được cung cấp qua Docker Compose; secret phải được cấp từ environment hoặc secret mechanism, không commit vào repository.

Các command product đã tồn tại và đã chạy:

```text
BUILD_COMMAND=mvn -B -ntp clean verify
UNIT_TEST_COMMAND=mvn -B -ntp test
DEPENDENCY_TREE_COMMAND=mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree.txt" "-DoutputType=text"
COMPOSE_CONFIG_COMMAND=docker compose -f docker-compose.yml config --quiet
M5_REDIS_TEST_COMMAND=mvn -B -ntp "-Dtest=RedisOtpStoreIntegrationTest" test
M5_MAIL_TEST_COMMAND=mvn -B -ntp "-Dtest=SmtpMailServiceTest" test
M6_AUTH_TEST_COMMAND=mvn -B -ntp "-Dtest=AuthenticationSecurityIntegrationTest" test
M6_UNIT_TEST_COMMAND=mvn -B -ntp "-Dtest=JwtServiceTest,RefreshSessionServiceTest,EmailVerificationServiceTest,AuthServiceTest" test
M7_AUTHZ_TEST_COMMAND=mvn -B -ntp "-Dtest=UserProjectMembershipIntegrationTest" test
M7_UNIT_TEST_COMMAND=mvn -B -ntp "-Dtest=UserServiceTest,ProjectAuthorizationServiceTest,ProjectServiceTest,ProjectMemberServiceTest" test
M8_INVITATION_TEST_COMMAND=mvn -B -ntp "-Dtest=InvitationLifecycleIntegrationTest" test
M8_UNIT_TEST_COMMAND=mvn -B -ntp "-Dtest=InvitationServiceTest" test
```

`.env.example` chỉ là danh sách tên biến và placeholder an toàn. Không tạo hoặc commit `.env` chứa credential thật.

Yêu cầu môi trường cho M6 verification: Docker daemon phải chạy vì auth integration test dùng PostgreSQL + Redis Testcontainers; mail dùng mock `MailService` nên không cần SMTP thật.

## Biến môi trường

- Dùng `.env.example` để liệt kê tên biến cần thiết cho M1 runtime skeleton và các milestone runtime tiếp theo.
- Không đưa secret thật vào repository.
- Mỗi biến phải có mô tả ngắn.
- Phân biệt biến bắt buộc và tùy chọn.
- Mô tả giá trị mặc định an toàn nếu có.
- Các nhóm config tối thiểu: PostgreSQL, Redis/OTP, Gmail SMTP, JWT/refresh cookie, MinIO, invitation expiry, upload limits, CORS/frontend base URL, OpenAPI flags.
- Gmail App Password, JWT secret, OTP hash secret/pepper, PostgreSQL password và MinIO secret phải đến từ environment/secret mechanism.

## Chạy dự án

M1 đã tạo Compose skeleton cho dependency local. Các lệnh dưới đây phản ánh command thật; dependency startup/restart đã được xác minh với process-only local placeholders:

```text
START_ALL_COMMAND=<chưa khả dụng - full backend Compose wiring khóa tại M14>
START_FRONTEND_COMMAND=<không áp dụng trong phase backend hiện tại>
START_BACKEND_COMMAND=mvn -B -ntp spring-boot:run (chưa xác minh runtime)
START_DEPENDENCIES_COMMAND=docker compose -f docker-compose.yml up -d postgres minio redis (đã xác minh runtime dependency)
```

Compose skeleton M1 hiện chỉ khai báo dependency; backend chạy từ Maven trong local development. Runtime local mục tiêu sau khi implementation hoàn tất:

```text
backend
postgres + postgres_data
minio + minio_data
redis (ephemeral OTP state)
```

Gmail SMTP là external integration và không chạy trong Docker Compose.

## Baseline verification

```text
FORMAT_COMMAND=<chưa khả dụng - chưa có formatter được khóa>
LINT_COMMAND=<chưa khả dụng - chưa có linter được khóa>
TYPECHECK_COMMAND=<không áp dụng cho Java/Maven baseline hiện tại>
BUILD_COMMAND=mvn -B -ntp clean verify
UNIT_TEST_COMMAND=mvn -B -ntp test
INTEGRATION_TEST_COMMAND=mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest test
E2E_COMMAND=<không áp dụng cho frontend trong phase hiện tại>
GENERATE_DB_SCHEMA_COMMAND=<thủ công - tái sinh docs/generated/db-schema.md cùng thay đổi migration; generator tự động chưa được thiết lập>
VERIFY_DB_SCHEMA_COMMAND=mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test
GENERATE_API_SCHEMA_COMMAND=<chưa khả dụng - thiết lập sau OpenAPI>
VERIFY_API_SCHEMA_COMMAND=<chưa khả dụng - thiết lập sau OpenAPI contract tests>
```

M1 verification record bên dưới là baseline M1. Mỗi migration, integration behavior, Testcontainers hoặc schema generation chỉ được ghi là đã xác minh sau khi command tương ứng chạy thành công ở milestone sở hữu nó.

### M0 verification record (one-time)

Các lệnh probe dưới đây đã tồn tại trong archive tạm thời và đã chạy thành công; chúng không phải command build/test chuẩn của product vì probe đã được gỡ khỏi repository:

```text
mvn -f .m0-dependency-probe/pom.xml validate
mvn -f .m0-dependency-probe/pom.xml dependency:tree "-DoutputFile=dependency-tree.txt" "-DoutputType=text"
```

Kết quả tại M0: cả hai lệnh exit code `0`. Product build, Docker runtime, Flyway schema generation và OpenAPI generation khi đó chưa khả dụng vì backend project chưa tồn tại; product build hiện đã được cập nhật và xác minh trong M1 record bên dưới.

### M1 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả |
|---|---|
| `mvn -B -ntp clean verify` | Pass — build success, Spring Boot jar được repackage |
| `mvn -B -ntp test` | Pass — 2 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree.txt" "-DoutputType=text"` | Pass — dependency graph resolve thành công |
| `docker compose -f docker-compose.yml config --quiet` | Pass — kiểm tra với process environment placeholder không phải credential production |
| Compose JSON/source inspection | Pass — services `postgres`, `minio`, `redis`; named volumes `postgres_data`, `minio_data`; Redis `/data` dùng `tmpfs` và tắt persistence |
| Configuration-properties binding test | Pass — toàn bộ nhóm typed properties M1 bind đúng |
| Application context smoke test | Pass — profile `test` start không cần external infrastructure |
| Docker daemon check | Pass — Docker Desktop server 29.8.0 đã sẵn sàng |
| Docker Compose runtime — first attempt | Fail, đã xử lý — Docker Hub từ chối `minio/minio:latest`; image đã chuyển sang `quay.io/minio/minio:latest` |
| Docker Compose runtime — Redis boundary inspection | Pass, sau khi xử lý — `/data` thực tế là `tmpfs`; image-level anonymous-volume metadata không tạo durable mount |
| Docker Compose readiness/restart smoke | Pass — PostgreSQL/MinIO/Redis start/restart; PostgreSQL marker giữ được, MinIO readiness HTTP 200, Redis key mất sau restart |

M1 không tạo migration, entity, controller, service, repository, adapter, API behavior hoặc frontend; vì vậy generated DB/API schema không thay đổi.

### M2 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test` | Pass — 12/12 | Trên PostgreSQL 17 Testcontainer (`postgres:17-alpine`) khởi động mới: Flyway apply 3/3 migration success từ database rỗng; Hibernate `ddl-auto=validate` pass trong context thật; đúng 10 persistent tables; `users.email_verified_at` là `timestamp with time zone` nullable; không có OTP table; 46 constraint name khớp `pg_constraint`; 19 index khớp `pg_indexes`; indexdef xác nhận partial WHERE và LOWER() expression; duplicate email (`uq_users_email`), email chưa normalize (`ck_users_email_normalized`), OWNER thứ hai (`uq_project_members_single_owner`), cross-project tag (`fk_document_tags_tag_same_project`) đều bị từ chối trên PostgreSQL thật |
| `mvn -B -ntp clean verify` | Pass — 14 tests (12 M2 + 2 M1), 0 failures, 0 errors, 0 skipped | Build success kèm Spring Boot repackage |
| `mvn -B -ntp test` | Pass — 14 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ suite M1+M2 |
| M2 review: `mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test` + `mvn -B -ntp clean verify` | Pass — 12/12 integrity + 14 full-suite tests | Integrity test ép profile `local`, Flyway enabled và Hibernate `ddl-auto=validate`; schema/migration không thay đổi |

Lưu ý: Docker daemon phải đang chạy vì integrity test dùng Testcontainers. Migration chưa chạy trên Compose `postgres_data` local (backend runtime wiring thuộc M14).

### M3 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp -Dtest=JpaMappingRepositoryIntegrationTest test` | Pass — 11/11 | PostgreSQL 17 Testcontainer mới; Flyway apply V1–V3 từ database rỗng; Hibernate `ddl-auto=validate`; kiểm tra mapping, repository queries/projection/lock/fetch graph/specification, uniqueness, composite FK và DocumentTag cascade |
| `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test` | Pass — 23 tests, 0 failures, 0 errors, 0 skipped | Chuẩn integration command hiện tại cho M2 + M3 |
| `mvn -B -ntp test` | Pass — 25 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ suite M1 + M2 + M3 |
| `mvn -B -ntp clean verify` | Pass — 25 tests, 0 failures, 0 errors, 0 skipped | Build success và Spring Boot jar repackage |
| Static architecture/scope review | Pass | Không có OTP Entity/Repository, `@ManyToMany`, `CascadeType.ALL`, `Project.ownerId` hoặc frontend change; mọi `@ManyToOne` đều khai báo `LAZY` |

M3 không thay đổi migration/database schema hoặc API contract; `docs/generated/db-schema.md` và `docs/generated/api-schema.md` vì vậy không cần sinh lại.

### M4 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=ConstraintViolationTranslationIntegrationTest,GlobalExceptionHandlerTest,ConstraintViolationTranslatorTest" test` | Pass — 16 tests, 0 failures, 0 errors, 0 skipped | MockMvc kiểm tra KBase exception, validation, malformed JSON, invalid UUID/parameter, method/media type, multipart, request ID/MDC, known/unknown constraint và leakage; PostgreSQL Testcontainer trigger constraint thật |
| `mvn -B -ntp test` | Pass — 41 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M4 suite |
| `mvn -B -ntp clean verify` | Pass — 41 tests, 0 failures, 0 errors, 0 skipped | Compile/package và Spring Boot jar repackage pass |
| Static M4 scope/leakage review | Pass | Không có M5 adapter, frontend, OTP Entity/Repository hoặc credential literal; API fallback không trả SQL, stack trace hay Java exception detail |

M4 không thay đổi Flyway migration hoặc `docs/generated/db-schema.md`; `docs/generated/api-schema.md` chỉ được đồng bộ thủ công cho shared error schema vì OpenAPI runtime chưa thuộc milestone này.

### M5 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả |
|---|---|
| `mvn -B -ntp "-Dtest=OtpServiceTest" test` | Pass — 6/6; 6-digit secure generation, HMAC protection, constant-time verification, invalid/expired/max-attempt/cooldown/replacement behavior và Redis error contract |
| `mvn -B -ntp "-Dtest=RedisOtpStoreIntegrationTest" test` | Pass — 6/6 với Redis `7.4-alpine` Testcontainer; `OtpStore` boundary, String serialization, TTL, attempts, cooldown, atomic replacement/reset, delete, HMAC value và unavailable mapping |
| `mvn -B -ntp "-Dtest=SmtpMailServiceTest" test` | Pass — 5/5 với fake SMTP; verification/invitation content, HTML escaping, Gmail config/timeout/STARTTLS, `EMAIL_SERVICE_UNAVAILABLE` và sensitive-log capture |
| `docker compose -f docker-compose.yml config --format json` | Pass — sau khi cấp các biến bắt buộc bằng process-only local placeholders; services `postgres`, `minio`, `redis`; named volumes chỉ `postgres_data`/`minio_data`; Redis không có explicit service volume, `/data` là `tmpfs` |
| Live Compose Redis check | Pass — Redis healthy, `PONG`, `appendonly=no`, `save` rỗng, `/data` là `tmpfs`; Redis container được recreate theo policy ephemeral |
| Static scope/boundary/leakage review | Pass — Redis client types chỉ ở `redis` config/adapter, JavaMail chỉ ở `mail` config/adapter; không có raw OTP/token/credential logging, OTP entity/repository, auth flow, M6 hoặc frontend |
| `mvn -B -ntp test` | Pass — full suite 58 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp clean verify` | Pass — full suite 58 tests, 0 failures, 0 errors, 0 skipped; Spring Boot jar repackage thành công; final rerun exit code 0 |
| `mvn -B -ntp "-Dtest=OtpServiceTest,RedisOtpStoreIntegrationTest,SmtpMailServiceTest" test` | Pass — M5 targeted rerun 17 tests, 0 failures, 0 errors, 0 skipped; Redis Testcontainer và fake SMTP pass |

M5 không thay đổi Flyway migration hoặc API endpoint/contract; `docs/generated/db-schema.md` và `docs/generated/api-schema.md` không cần sinh lại.

### M6 verification record

Các kiểm tra sau đã chạy ngày `2026-09-17`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp test` (baseline trước khi sửa mã) | Pass — 58 tests, 0 failures | Xác nhận M5 Gate pass và không blocker trước M6 |
| `mvn -B -ntp "-Dtest=JwtServiceTest,RefreshSessionServiceTest,EmailVerificationServiceTest,AuthServiceTest" test` | Pass — 32/32 | JWT generate/parse/claims/expired/tampered/short-secret reject; refresh create hash-only/validate order/revoke/revokeAll; OTP issue/verify/resend + mail-fail cleanup + Redis-failure propagate; register unverified + duplicate + race, login generic 401/unverified/disabled, refresh account-state, logout idempotent |
| `mvn -B -ntp "-Dtest=AuthenticationSecurityIntegrationTest" test` | Pass — 14/14 | PostgreSQL 17 + Redis 7.4 Testcontainers, real SecurityFilterChain, mail mock: register 201 unverified + OTP captured, duplicate 409, validation 400, mail-fail 503 + rollback, verify set `email_verified_at` + invalidate Redis + 409 re-verify, wrong OTP → INVALID_OTP, exhausted → 429, expired state → OTP_EXPIRED, resend cooldown 429 + replace + old OTP invalid, login before verify 403 EMAIL_NOT_VERIFIED, login 200 + JWT claims (chỉ sub/systemRole/iat/exp/jti) + HttpOnly/Secure/SameSite=Lax/Path=/api/v1/auth cookie + DB chỉ lưu SHA-256 hash, unknown email/wrong password generic 401, refresh → new token hoạt động + missing/invalid/revoked 401 codes, logout 204 + Max-Age=0 + idempotent, JWT valid/expired/tampered/garbage trên protected endpoint, ADMIN route (USER 403 / ADMIN 200 / anonymous 401), disabled account chặn access cũ + refresh + login |
| `mvn -B -ntp test` (M6 final) | Pass — 104 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M6 suite |
| `mvn -B -ntp clean verify` (M6 final) | Pass — 104 tests, 0 failures, 0 errors, 0 skipped | Compile/package và Spring Boot jar repackage pass |
| Static M6 scope/leakage/boundary review | Pass | Không log password/JWT/raw refresh token/OTP/cookie value; toString redacted cho LoginResult/CreatedRefreshSession/GeneratedOtp; không OWNER/MEMBER/ProjectMember trong security package; Redis/JavaMail types chỉ trong adapter/config; principal không giữ password; không frontend, không M7 API, không refresh rotation |
| JWT placeholder secrets trong tests cũ nâng ≥256 bit | Pass — test-only | JwtService enforce RFC 7518 HS256 ≥256 bit; các test M2/M3/M4 + context smoke dùng placeholder dài hơn; production secret vẫn từ `KBASE_JWT_SECRET` |

M6 không thay đổi Flyway migration; `docs/generated/db-schema.md` không cần sinh lại. `docs/generated/api-schema.md` đã được đồng bộ thủ công với 6 auth endpoints, security error codes và UserResponse/SystemRole/UserStatus từ source code đã verify (OpenAPI runtime vẫn thuộc M13).

### M7 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp test` (baseline M6 trước khi sửa mã) | Pass — 104 tests, 0 failures | Xác nhận M6 Gate pass và không blocker trước M7 |
| `mvn -B -ntp "-Dtest=UserServiceTest,ProjectAuthorizationServiceTest,ProjectServiceTest,ProjectMemberServiceTest" test` | Pass — 20/20 | Profile/password (verify current → hash mới → revoke sessions), admin status parse `INVALID_USER_STATUS` + revoke khi disable, delete dependency order (OWNER → documents/memberships/invitations), authorization contracts 404/403, ADMIN override `currentUserRole` null, create OWNER atomically, remove/leave matrix |
| `mvn -B -ntp "-Dtest=UserProjectMembershipIntegrationTest" test` | Pass — 6/6 | PostgreSQL 17 Testcontainer + real SecurityFilterChain: creator thành OWNER trong cùng transaction (đúng 1 membership), `GET /projects` membership-only kể cả ADMIN + filter role, non-member 403 `PROJECT_ACCESS_FORBIDDEN`, MEMBER đọc được nhưng không update, OWNER/ADMIN update, ADMIN non-member `currentUserRole` null, MEMBER không remove được, OWNER remove MEMBER 204, OWNER không bị remove (kể cả bởi ADMIN) 409, MEMBER leave 204, OWNER leave 409, document upload bởi member tồn tại sau remove/leave với `uploadedBy` vẫn tham chiếu User, password change revoke refresh session, admin users q/pagination/status/delete, admin projects không fake role |
| `mvn -B -ntp test` (M7 final) | Pass — 130 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M7 suite |
| `mvn -B -ntp clean verify` (M7 final) | Pass — 130 tests, 0 failures, 0 errors, 0 skipped | Compile/package và Spring Boot jar repackage pass |
| Static M7 scope/boundary review | Pass | Không `Project.ownerId`, không fake ADMIN ProjectRole, không controller→repository trực tiếp, user delete không cascade project knowledge (chỉ refresh_sessions cascade theo schema), không đổi auth/OTP/Redis/Gmail; response không expose passwordHash |
| Implementation notes ghi nhận | — | Sort trên "my projects" được remap sang path `project.*` (query root là ProjectMember); `:q` dùng sentinel `''` thay vì null vì PostgreSQL không infer kiểu null string trong OR predicate; refresh sau admin-disable trả 401 `REFRESH_SESSION_REVOKED` (session bị revoke trước khi check status) |

M7 không thay đổi Flyway migration; `docs/generated/db-schema.md` không cần sinh lại. `docs/generated/api-schema.md` đã được đồng bộ thủ công với 9 endpoints user/project/membership mới và pagination envelope (OpenAPI runtime vẫn thuộc M13).

### M8 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp test` (baseline M7 trước khi sửa mã) | Pass — 130 tests, 0 failures | Xác nhận M7 Gate pass và không blocker trước M8 |
| `mvn -B -ntp "-Dtest=InvitationServiceTest" test` | Pass — 8/8 | create hash-only + mail URL, member email/pending conflicts, mail fail propagate, resend replace hash + reset expiry + non-pending/member 409, cancel không physical delete, accept success + not found/not pending/expired→EXPIRED/mismatch/already-member |
| `mvn -B -ntp "-Dtest=InvitationLifecycleIntegrationTest" test` | Pass — 5/5 | PostgreSQL 17 Testcontainer + real SecurityFilterChain, mail mock: OWNER 201 PENDING với mail URL chứa raw token (body/DB không lộ token/hash), MEMBER 403, ADMIN override 201, member email/duplicate PENDING 409, resend token cũ 404 + token mới accept 200 + expiresAt reset, cancel CANCELLED còn row + accept 409, expired 409 (backdate qua JdbcTemplate vì `created_at` updatable=false và check `expires_at > created_at`), email mismatch 403, accept tạo đúng 1 MEMBER + ACCEPTED + acceptedAt, double accept 409, mail fail 503 + rollback, concurrency 2 thread chỉ 1 thắng với `INVITATION_NOT_PENDING` |
| `mvn -B -ntp test` (M8 final) | Pass — 143 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M8 suite |
| `mvn -B -ntp clean verify` (M8 final) | Pass — 143 tests, 0 failures, 0 errors, 0 skipped | Compile/package và Spring Boot jar repackage pass |
| Static M8 scope/leakage review | Pass | Invitation không dùng OTP (chỉ javadoc); không log/expose raw token/hash; cancel không physical delete; không controller→repository; không đổi auth/OTP/Redis/Gmail flow M5–M7 |

M8 không thay đổi Flyway migration; `docs/generated/db-schema.md` không cần sinh lại. `docs/generated/api-schema.md` đã được đồng bộ thủ công với 5 invitation endpoints (OpenAPI runtime vẫn thuộc M13).

### M9 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=FolderServiceTest,CategoryServiceTest,TagServiceTest" test` | Pass — 12/12 | Authorization, case-insensitive uniqueness, same-project parent, folder cycle/non-empty rules, category in-use và tag mutation rules |
| `mvn -B -ntp "-Dtest=OrganizationIntegrationTest" test` | Pass — 6/6 | PostgreSQL 17 + Redis Testcontainers + real SecurityFilterChain; list matrix, OWNER/ADMIN/MEMBER rules, cross-project parent, self/descendant cycle, non-empty/in-use delete, tag relation cascade |
| `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test` | Pass — 23/23 | Same-project composite FK, self-parent CHECK, uniqueness/index catalog và Hibernate mapping regression |
| `mvn -B -ntp test` (M9 final) | Pass — 161 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M9 suite |
| `mvn -B -ntp clean verify` (M9 final) | Pass — BUILD SUCCESS; 161 tests | Compile/package và Spring Boot jar repackage pass |
| Static M9 scope/boundary review | Pass | Không frontend/M10/M11, migration hoặc controller→repository; không đổi auth/project/membership/invitation; DB same-project constraints được giữ nguyên |

M9 không thay đổi Flyway migration; `docs/generated/db-schema.md` tiếp tục là schema source-of-truth đã verify. `docs/generated/api-schema.md` đã được đồng bộ thủ công với 12 organization endpoints.

### M10 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=StorageKeyFactoryTest,MinioStorageServiceTest,ConfigurationPropertiesBindingTest" test` | Pass — 7/7 | Key backend-controlled/extension validation, port không rò SDK, missing/unavailable translation, partial batch-delete result và typed storage config |
| `mvn -B -ntp "-Dtest=MinioStorageIntegrationTest" test` | Pass — 2/2 | Real `quay.io/minio/minio:latest` Testcontainer: bucket init, unversioned status, stream upload/full/range read, stat, single và batch delete |
| `mvn -B -ntp test` (M10 final) | Pass — 169 tests, 0 failures, 0 errors, 0 skipped | Toàn bộ M1–M10 suite |
| `mvn -B -ntp clean verify` (M10 final) | Pass — BUILD SUCCESS; 169 tests | Compile/package và Spring Boot jar repackage pass |
| Static M10 scope/boundary review | Pass | SDK chỉ trong `storage` adapter/config; không storage byte-array buffering, public API, migration, policy/versioning/retention/object-lock mutation hay M11 lifecycle; Compose vẫn mount `minio_data:/data` |

M10 không thay đổi Flyway migration, REST endpoint hay generated API/DB schema; vì vậy `docs/generated/db-schema.md` và `docs/generated/api-schema.md` không cần tái sinh.

## Tài liệu Generated

| Tài liệu | Nguồn sự thật | Cách cập nhật | Cách xác minh |
|---|---|---|---|
| `docs/generated/db-schema.md` | Flyway migration + PostgreSQL schema thực tế | `Thủ công trong cùng thay đổi migration; generator tự động chưa được thiết lập` | `mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test (Flyway từ DB rỗng + Hibernate validate + PostgreSQL catalog inspection)` |
| `docs/generated/api-schema.md` | Spring controller/DTO + springdoc OpenAPI | `Chưa tự động hóa; thiết lập trong OpenAPI milestone` | `/v3/api-docs` contract test và đối chiếu REST spec |

Quy tắc:

- Không chỉnh tay tài liệu được sinh tự động sau khi generator đã được thiết lập.
- Trước khi generator tồn tại, file generated phải tự khai báo rõ trạng thái placeholder/not-generated.
- Generated documentation phải được cập nhật trong cùng thay đổi với source of truth.
- Không dùng generated documentation lỗi thời làm căn cứ triển khai.
- Không đưa secret hoặc dữ liệu production vào generated documentation.

## Reset môi trường

Khi Docker runtime đã được tạo, phải tài liệu hóa command thật để:

- Dừng process/container.
- Xóa build artifact.
- Xóa cache local an toàn.
- Reset PostgreSQL development/test khi có chủ ý.
- Chạy lại Flyway migration.
- Xóa/recreate MinIO development data khi có chủ ý.
- Restart Redis OTP store; mất pending OTP là chấp nhận được vì Redis OTP là ephemeral.
- Khởi động lại dependency.

Không xóa `postgres_data` hoặc `minio_data` như một bước reset mặc định. Không đưa destructive command cho production vào phần này.

## Debug và xử lý lỗi

Khi backend được bootstrap, cập nhật:

- Vị trí log.
- Health endpoint.
- Debug command.
- Cách kiểm tra port.
- Cách kiểm tra PostgreSQL connection.
- Cách xác nhận Flyway migration version.
- Cách kiểm tra Redis connectivity/TTL mà không làm lộ OTP.
- Cách kiểm tra MinIO bucket/object.
- Cách kiểm tra Gmail SMTP configuration mà không in App Password.
- Các lỗi setup thường gặp.

## Yêu cầu chất lượng

- Setup phải có thể tái tạo.
- Command phải được kiểm tra thực tế trước khi ghi là baseline.
- Không phụ thuộc host-local PostgreSQL, Redis hoặc MinIO trong local Docker path chuẩn.
- Một thành viên mới hoặc agent mới phải có thể khởi động backend chỉ bằng repository sau khi M14 hoàn tất.
