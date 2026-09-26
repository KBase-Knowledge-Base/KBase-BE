# Môi trường Phát triển

## Mục đích

Tài liệu này cung cấp quy trình chuẩn để cài đặt, cấu hình, chạy, kiểm tra và xử lý lỗi dự án trên môi trường local.

## Trạng thái Hiện tại

Backend foundation của KBase đã được bootstrap trong M1. M1 Gate đã pass ngày `2026-09-17`.

AI v1 M2 – pgvector / AI Persistence Schema đã hoàn tất ngày `2026-09-22`: Flyway V4 tạo 8 AI tables trên nền PostgreSQL 17 + pgvector, với vector `768`, HNSW cosine indexes, JPA mappings, KBase-owned vector JDBC boundary, FK/delete/status guards, quota lock và active-generation guard; targeted M2 suite 36/36 và full gate 241/241 pass.

AI v1 M3 – Durable Job Engine & Core Lifecycle Hooks đã hoàn tất ngày `2026-09-22`: PostgreSQL claim/lease/retry/stale recovery, bounded scheduler boundary, document AI intent, delete-race safety và membership retention hooks đã được verify; targeted M3 suites pass và full gate 268/268. M3 không gọi Gemini, không extraction/embedding execution, không public AI API và không destructive purge.

AI v1 M4 – Gemini Provider Adapters đã hoàn tất ngày `2026-09-22`: explicit disabled-by-default Spring AI/Google GenAI configuration, KBase-owned chat/embedding adapters, deterministic query/document preparation, strict vector `768`, safe provider error categories, request-timeout wiring và privacy/logging guards; targeted M4 suite 22/22 và full gate 290/290 pass. Real Gemini credential/public network không cần và không được gọi.

AI v1 M5 – Content Extraction / Chunking / Document Indexing đã hoàn tất ngày `2026-09-23`: exactly PDF/DOC/DOCX/PPT/PPTX/MD/TXT qua Tika adapter, proven source locations, deterministic `kbase-lex-v1`/`chunk-v1`, `StorageService`-only bounded/hash-checked reads, durable `DOCUMENT_INDEX` staging/atomic activation, bounded safe retry/failure, internal status/manual retry và PostgreSQL delete/stale-lease protection; targeted M5 suite 17/17 và full gate 307/307 pass. Không thêm migration, public API hoặc generated-doc change; real Gemini/live-provider Compose indexing chưa chạy.

AI v1 M6 – Semantic Retrieval / Grounding / Citations đã hoàn tất ngày `2026-09-23`: internal Project RAG với QUERY embedding, real pgvector project/READY/active/current-document retrieval, deterministic evidence selection, strict NO_EVIDENCE, bounded history/untrusted prompt, exact source labels, citation snapshot mapper và authorization rechecks. Targeted M6 suite 33/33 và full gate 329/329 pass. Không thêm schema/API/Guide/conversation runtime; active handoff là M7 planning-only. Real Gemini key/public network không cần cho automated gate.

AI v1 M7 – Project Assistant Conversations & REST API đã hoàn tất ngày `2026-09-24`: private creator-only conversation CRUD, max-five quota, one-active generation, short transaction split, grounded/NO_EVIDENCE/FAILED lifecycle, source availability và document index status/retry API-AI-001..009. Targeted 36/36 và full gate 346/346 pass; runtime OpenAPI 37 paths/56 operations. V1–V4/generated DB unchanged; active handoff là M8 planning-only. Abrupt JVM death có thể để lại PROCESSING marker, đã ghi trong technical-debt tracker. Automated gate không cần real Gemini credential/network.

AI v1 M8 – Membership Retention / Deletion / Security Races đã hoàn tất ngày `2026-09-24`: provider-independent `CONVERSATION_PURGE`, retention lifecycle và AI-disabled maintenance đã verify; full gate 352/352 pass.

AI v1 M9 – KBase Guide đã hoàn tất ngày `2026-09-25`: exact two-spec canonical Maven/Docker packaging, packaged SHA-256 reconciliation and last-good `GUIDE_REINDEX`, fenced-code-safe deterministic Guide chunks, SQL-bound allowlist retrieval proven against a rogue perfect vector, null-threshold fail-closed NO_EVIDENCE, and authenticated stateless API-AI-010. Runtime OpenAPI remains 38 paths/57 operations; automated gate uses deterministic fakes and no real Gemini credential/network.

AI v1 M10 – Usage Guard / OpenAPI / Observability / Hardening đã hoàn tất ngày `2026-09-25`: Redis 7.4 fixed-window per-user guard với atomic Lua `INCR` + first-request TTL, shared Project Assistant/Guide budget mặc định 20/1m, stable `AI_RATE_LIMIT_EXCEEDED`/429 và `AI_USAGE_GUARD_UNAVAILABLE`/503, retrieval threshold `0.70`, bounded Micrometer telemetry, safe logs, exact OpenAPI 38 paths/57 operations/15 tags, generated API snapshot sync và sensitive-data audit. Final `mvn -B -ntp clean verify` là 371/371; focused Redis 5/5, OpenAPI 22/22, Compose config và diff check pass. M10 không dùng real Gemini credential/network và không đổi V1–V4. *(M10-era handoff note: M11 full runtime verification/freeze là active handoff tại thời điểm đó — đã hoàn tất 2026-09-26, xem bullet dưới.)*

AI v1 M11 runtime repair ngày `2026-09-25` thêm deterministic provider adapter chỉ cho explicit `runtime-test` profile + acknowledgement; Gemini vẫn là default production mode. Clean isolated Compose pinned pgvector đã apply Flyway V1–V4/Hibernate và real HTTP Project Assistant/Guide grounded + no-evidence journeys pass; deterministic unavailable maps AI to 503 while Core project list remains 200. Không gọi real Gemini. M11 vẫn ACTIVE, chưa FROZEN: security, retention, restart/recovery, Redis-failure và final consistency evidence còn phải chạy.

AI v1 M11 – Full Runtime Verification / AI v1 Freeze đã hoàn tất ngày `2026-09-26` và **AI v1 backend FROZEN**: final gate 377/377; M11 runtime matrix (security 37/37, retention +P7D/restore/runtime-purge/race, restart/recovery resume+stale-reclaim+persistence+abrupt-chat-death classification với verified creator-DELETE workaround, comprehensive log audit 0 hits/518 dòng/9 scenario, config/DB/OpenAPI consistency audits) đã chạy qua isolated Compose project `kbase-m11fix` với real HTTP boundary và isolated DB postconditions. Không code change, không schema/API/generated-snapshot change, không real Gemini credential/network. Chi tiết: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M11_Full_Runtime_Verification_AI_v1_Freeze.md`.

Post-Freeze Final Codebase Audit đã PASS ngày `2026-09-26` (bảo trì sau freeze, không phải milestone): fix F-01 storage/provider exception log sanitization (raw cause bị loại khỏi StorageException/BucketInitializer/JobHandler theo precedent SMTP L-07; log chỉ safe exception type), F-02 invitation create `saveAndFlush` trước mail (unique-index reject trước side effect, concurrency regression proven fail-without-fix), F-03 bỏ `spring.profiles.default=local` (compose = `local` tường minh, prod = `prod`, artifact không profile fail-fast; `ProfileFailSafeTest` + Docker smoke), F-04 MinIO `ErrorResponseException` 5xx → `STORAGE_SERVICE_UNAVAILABLE` 503 cho upload/delete. Full gate sau audit **387/387**; OpenAPI 38/57/15 + Flyway V1–V4 + 18 tables không đổi. Chi tiết: `docs/exec-plans/completed/KBase_Post_Freeze_Final_Codebase_Audit.md`.

M2 – PostgreSQL / Flyway Schema đã hoàn tất ngày `2026-09-17`: 3 Flyway migrations tạo 10 persistent tables với đầy đủ constraint, partial/expression unique index và query index theo Physical Database Design; migration integrity test 12/12 pass trên PostgreSQL 17 Testcontainer; Hibernate `ddl-auto=validate` pass.

M3 – JPA Entities & Repositories đã hoàn tất ngày `2026-09-17`: 10 entity persistent, 5 enum, `DocumentTagId`, 10 feature-local repository, projection/query/fetch graph/lock và document specification đã được implement; mapping integration test 11/11 pass trên PostgreSQL 17 Testcontainer với Flyway từ database rỗng và Hibernate `ddl-auto=validate`. Không có OTP entity/repository.

M4 – Shared Error / Request Infrastructure đã hoàn tất ngày `2026-09-17`: `ErrorCode`, exception hierarchy, `ApiErrorResponse`, `RequestIdFilter`/MDC, `GlobalExceptionHandler` và PostgreSQL constraint translator đã được implement; targeted M4 suite 16/16, full suite 41/41 và `mvn clean verify` pass.

M5 – Redis OTP + Gmail Mail Infrastructure đã hoàn tất ngày `2026-09-17`: `OtpStore`/`RedisOtpStore`, `OtpService`, `MailService`/`SmtpMailService`, typed Redis/SMTP configuration và safe templates đã được implement; OTP unit 6/6, Redis 7.4 Testcontainer 6/6, fake SMTP 5/5 và full suite 58/58 pass. Registration/login/auth flow, security handler, MinIO, feature API và frontend vẫn deferred theo scope.

M6 – Spring Security + Authentication đã hoàn tất ngày `2026-09-17`: `SecurityConfig` stateless (6 public auth endpoints, `/api/v1/admin/**` → ADMIN, còn lại authenticated), `JwtService` HS256 (sub=userId, systemRole, iat, exp, jti), `JwtAuthenticationFilter` load User từ DB mỗi request, `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` với `ApiErrorResponse`, `RefreshSessionService` PostgreSQL hash-only, `EmailVerificationService` phối hợp Redis OTP + Gmail và `AuthService`/`AuthController` cho register/verify-email/resend/login/refresh/logout đã được implement; auth integration 14/14 trên PostgreSQL+Redis Testcontainers với real filter chain, unit 32/32 và full suite 104/104 pass. Feature API users/projects/documents, MinIO, OpenAPI runtime và frontend vẫn deferred theo scope.

M7 – User / Project / Membership đã hoàn tất ngày `2026-09-18`: current-user APIs (`GET/PATCH /users/me`, `PUT /users/me/password` kèm revoke refresh sessions), admin user APIs (list/get/status/delete theo dependency rules), project APIs (create Project+OWNER trong một transaction, list membership-only kể cả ADMIN, get/update với ADMIN override và `currentUserRole` nullable, admin listing), membership APIs (list/remove/leave với OWNER bất khả xâm và documents remain) và `ProjectAuthorizationService` đã được implement; authorization matrix integration 6/6 trên PostgreSQL Testcontainer, unit 20/20 và full suite 130/130 pass. Invitation (M8), folder/category/tag (M9), MinIO/document (M10/M11), project hard delete (M11), search (M12), OpenAPI runtime (M13) và frontend vẫn deferred theo scope tại thời điểm M7.

M8 – Invitation Lifecycle đã hoàn tất ngày `2026-09-18`: `POST /api/v1/projects/{projectId}/invitations`, `GET .../invitations`, `POST .../invitations/{id}/resend`, `DELETE .../invitations/{id}` và `POST /api/v1/invitations/accept` đã được implement qua `InvitationService` + `InvitationTokens`. Invitation dùng secure token riêng (không OTP): raw token chỉ trong email link (`KBASE_INVITATION_ACCEPT_URL`) và request accept; PostgreSQL chỉ lưu SHA-256 hash; resend thay token + reset expiry; cancel CANCELLED không physical delete; accept authenticated với PESSIMISTIC_WRITE tạo `ProjectMember(MEMBER)` + `ACCEPTED` + `acceptedAt`; mail fail khi create rollback invitation. Invitation integration 5/5 (gồm concurrency 2 thread), unit 8/8 và full suite 143/143 pass. M9 đã hoàn tất; MinIO/document (M10/M11), project hard delete (M11), search (M12), OpenAPI runtime (M13) và frontend vẫn deferred theo scope.

M9 – Folder / Category / Tag đã hoàn tất ngày `2026-09-18`: 12 project-scoped endpoints qua `FolderController`, `CategoryController` và `TagController`; folder nested hierarchy với parent cùng project, sibling uniqueness case-insensitive, ancestor-walk cycle prevention và delete chỉ khi không có child/document; category OWNER/ADMIN CRUD với uniqueness case-insensitive và `CATEGORY_IN_USE`; tag MEMBER create/list, OWNER/ADMIN rename/delete với DocumentTag-only cascade. Unit 12/12, `OrganizationIntegrationTest` 6/6, cross-project/migration regression 23/23 và full suite 161/161 pass. Không implement document upload/lifecycle, MinIO, frontend hoặc M10/M11 trong slice này.

M10 – MinIO Storage Infrastructure đã hoàn tất ngày `2026-09-18`: `StorageService` là port streaming không chứa authorization/document logic; `MinioStorageService` cô lập SDK, hỗ trợ upload/full get/range get/stat/delete/deleteAll, dịch lỗi provider thành internal storage exceptions và luôn consume kết quả batch-delete. `StorageKeyFactory` tạo `projects/{projectId}/documents/{documentId}.{extension}` từ UUID và extension lowercase đã validate. `MinioClient` là Spring singleton với timeout cấu hình; local profile có thể tạo/validate bucket, base/production mặc định không auto-create, test profile tắt startup validation. Adapter không đổi policy/versioning/retention/object lock; bucket test được xác nhận không versioning-enabled. Unit/config 7/7, MinIO Testcontainer 2/2 và full suite 169/169 pass qua `mvn test` và `mvn clean verify`. Không implement document lifecycle/API, project hard delete hoặc frontend/M11.

M12 – Document Search / Pagination / Sorting đã hoàn tất ngày `2026-09-18`: `GET /api/v1/projects/{projectId}/documents` dùng `DocumentSearchService` + `DocumentSearchCriteria`; query luôn authorize bằng `ProjectAuthorizationService.requireProjectAccess` và luôn có predicate `projectId`. Search chỉ metadata (`displayName`, `originalFilename`, `description`, category/tag name); tag dùng `EXISTS` để không duplicate document. Baseline `page=0`, `size=20`, clamp 100 và sort canonical whitelist `displayName`, `createdAt`, `updatedAt`, `sizeBytes` được giữ. `DocumentSearchIntegrationTest` 2/2 trên PostgreSQL Testcontainer real filter chain, focused regression 16/16, full suite 188/188 và `mvn clean verify` pass. Không đổi schema, lifecycle, MinIO, authorization baseline hay frontend.

M13 – OpenAPI / Swagger đã hoàn tất ngày `2026-09-18`: `config/OpenApiConfig` khai báo metadata "KBase Core API v1", security scheme `bearerAuth` (HTTP bearer + JWT), 12 tags canonical và `OperationCustomizer` thêm `401 AUTHENTICATION_REQUIRED` dùng chung `ApiErrorResponse` cho protected operations. 12 controllers / 47 operations được annotate (số 47 được đếm lại trực tiếp trên runtime spec trong M15; bản ghi M13 ban đầu ghi 48 là miscount): auth public endpoints (register/verify-email/resend-verification-otp/login/refresh + logout) không có security requirement, protected endpoints khai báo `bearerAuth` per-controller, ADMIN endpoints ghi "Requires SystemRole.ADMIN", project/document endpoints ghi rule MEMBER/OWNER/ADMIN và uploader. Multipart upload document part `file`/`files` binary + `metadata` JSON; download/preview là binary schema; preview document `Range`, `206` với `Content-Range` và `416`. Swagger UI bật mặc định local/dev qua `kbase.openapi.*` (prod mặc định tắt; SecurityConfig chỉ permit docs paths khi enabled). `OpenApiContractIntegrationTest` 19/19 + `OpenApiDisabledIntegrationTest` 2/2 verify spec, security requirements, schemas, error codes và việc che docs khi disabled; full suite 209/209 qua `mvn test` và `mvn clean verify`.

M14 – Full Docker Runtime Verification đã hoàn tất ngày `2026-09-18`: `Dockerfile` multi-stage (Maven build → `eclipse-temurin:21-jre`, non-root, stateless, env-driven) và `docker-compose.yml` đủ 4 services. Backend join network qua service name (`postgres:5432`, `redis:6379`, `http://minio:9000`), healthcheck-gated startup (`pg_isready`, `redis-cli ping`, MinIO health/live) không dùng sleep. Clean startup từ rỗng: Flyway apply V1–V3 trên PostgreSQL 17 container, Hibernate `ddl-auto=validate` pass. Golden journeys đã chạy qua containerized backend: register → OTP state trong Redis container (key + TTL) → email nhận bởi mail double (`docker-compose.mail-test.yml`, Gmail vẫn external) → verify → login/refresh/logout (cookie HttpOnly, Secure=false local) → project/folder/category/tag → upload multipart → download checksum khớp → MP4 preview `206`/`416` → search filter/sort → invitation accept → MEMBER permissions → remove member/documents remain → document + project hard delete storage-first. Persistence: `postgres_data`/`minio_data` giữ data qua backend restart và container force-recreate; Redis recreation làm mất pending OTP và resend vẫn hoạt động. M14 tìm và sửa một bug runtime từ M11: `ProjectService.deleteProject` OWNER-path fail `TransientPropertyValueException` trên Hibernate 7.4 (membership managed trong persistence context) — sửa bằng bulk delete query `ProjectRepository.deleteProjectCascade` giữ nguyên DB FK cascade và thêm regression test OWNER-path trong `DocumentApiIntegrationTest` (trước đó chỉ ADMIN path được test trên DB thật). Đồng thời exclude `UserDetailsServiceAutoConfiguration` để log không in generated password. Full suite 210/210 qua `mvn test` và `mvn clean verify`.

M15 – Full Verification / Core v1 Freeze đã hoàn tất ngày `2026-09-19` và **Core v1 đã FROZEN**: full release gate `mvn -B -ntp clean verify` 210/210; Docker runtime re-verification từ volume rỗng với mail double (Flyway V1–V3 + Hibernate validate trong container, đúng 10 persistent tables + `users.email_verified_at` + không OTP table); toàn bộ golden journeys chạy lại qua containerized backend (auth + Redis OTP lifecycle, organization, upload/download checksum, preview 206/416, search matrix, invitation + MEMBER permissions, remove-member, hard delete storage-first OWNER-path); persistence qua backend restart + postgres/minio force-recreate + Redis recreation (OTP loss acceptable, resend OK); log leak scan 0 hits; runtime `/v3/api-docs` = 32 paths / 47 operations khớp contract test + SD-04; static architecture/leakage audits clean. M15 chỉ sửa một documentation miscount (48→47 operations trong bản ghi M13) — không có code change. Chi tiết: `docs/exec-plans/completed/KBase_Core_v1_M15_Full_Verification_Freeze.md`.

Docker CLI và Docker daemon hiện khả dụng. Compose dependency startup, readiness và restart smoke của PostgreSQL/Redis/MinIO đã được xác minh ngày `2026-09-17`; PostgreSQL và Redis Testcontainers đã chạy cho migration/OTP verification; fake SMTP đã chạy cho mail verification; MinIO Testcontainers đã chạy cho storage verification (M10); full backend Docker runtime đã được xác minh trong M14 (xem M14 verification record).

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

Môi trường M0 ghi nhận Docker CLI `29.8.0` nhưng daemon chưa khả dụng tại thời điểm preflight. Sau khi Docker được bật, M1 đã xác minh local dependency runtime bằng Compose, M2/M3/M4 đã xác minh PostgreSQL Testcontainers, M5 đã xác minh Redis Testcontainer + fake SMTP, M10 đã xác minh MinIO Testcontainer và M14 đã xác minh full backend Docker runtime.

Không để thông tin thực tế của runtime chỉ tồn tại trong lịch sử chat hoặc trí nhớ cá nhân.

## Thiết lập ban đầu

M1 đã tạo Maven project và cấu hình typed properties/profile. Dependency local vẫn phải được cung cấp qua Docker Compose; secret phải được cấp từ environment hoặc secret mechanism, không commit vào repository.

Các command product đã tồn tại và đã chạy:

```text
BUILD_COMMAND=mvn -B -ntp clean verify
UNIT_TEST_COMMAND=mvn -B -ntp test
HOST_RUN_COMMAND=SPRING_PROFILES_ACTIVE=local mvn spring-boot:run (profile `local` phải được chọn tường minh từ khi `spring.profiles.default` bị bỏ ở post-freeze audit 2026-09-26; profile này tự đọc `.env` ở repo root qua `spring.config.import: optional:file:.env[.properties]` trong `application-local.yml`; yêu cầu deps đã chạy: `docker compose up -d postgres minio redis`; backend host dùng đúng `KBASE_POSTGRES_PORT` trong `.env` — hiện `5433` vì 5432 bị PostgreSQL native trên máy chiếm)
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
M13_OPENAPI_TEST_COMMAND=mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test
M14_DOC_TEST_COMMAND=mvn -B -ntp "-Dtest=DocumentApiIntegrationTest" test
M3_JOB_TEST_COMMAND=mvn -B -ntp "-Dtest=AiJobEngineIntegrationTest" test
M3_SCHEDULER_TEST_COMMAND=mvn -B -ntp "-Dtest=AiJobSchedulerTest" test
M3_DOCUMENT_INTENT_TEST_COMMAND=mvn -B -ntp "-Dtest=DocumentAiIntentIntegrationTest,DocumentAiRollbackIntegrationTest" test
M3_RETENTION_TEST_COMMAND=mvn -B -ntp "-Dtest=AiConversationRetentionIntegrationTest,AiRetentionTransactionIntegrationTest" test
AI_M5_EXTRACTION_INDEX_TEST_COMMAND=mvn -B -ntp "-Dtest=DocumentExtractionAndChunkingTest,DocumentIndexJobHandlerTest,DocumentAiIndexApplicationServiceTest,DocumentAiIndexPersistenceIntegrationTest" test
AI_M6_RAG_TEST_COMMAND=mvn -B -ntp "-Dtest=EvidenceSelectorTest,SourceLabelAndCitationTest,ConversationContextPolicyTest,ProjectRagServiceTest,AiPersistenceIntegrationTest" test
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

M1 đã tạo Compose skeleton cho dependency local; M14 đã hoàn tất backend Dockerfile và Compose wiring. Các lệnh dưới đây phản ánh command thật đã chạy:

Hai đường chạy backend local đã được xác minh (2026-09-19):

1. **Container (chuẩn)**: `docker compose up -d --build` — full topology, backend phục vụ tại 8080.
2. **Host Maven**: `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run` — profile `local` được chọn tường minh (post-freeze audit 2026-09-26 bỏ `spring.profiles.default`; chạy `mvn spring-boot:run` không profile sẽ fail-fast vì thiếu biến bắt buộc thay vì âm thầm dùng cấu hình local không an toàn). Profile `local` tự nạp `.env` (git-ignored) qua `spring.config.import`; không cần export biến thủ công khác. Yêu cầu deps Compose đang chạy; lưu ý Compose `postgres` map host port theo `KBASE_POSTGRES_PORT` trong `.env` (`5433` trên máy hiện tại vì PostgreSQL native chiếm `5432`), và muốn chạy host `mvn` thì phải `docker compose stop backend` trước để nhả 8080.

```text
BUILD_BACKEND_IMAGE=docker compose build backend
START_ALL_COMMAND=docker compose up -d (backend + postgres + minio + redis; cần .env với các biến :?required)
START_BACKEND_ONLY=docker compose up -d --build backend
START_DEPENDENCIES_COMMAND=docker compose -f docker-compose.yml up -d postgres minio redis (đã xác minh runtime dependency)
START_WITH_MAIL_DOUBLE=docker compose -f docker-compose.yml -f docker-compose.mail-test.yml up -d backend mail-test kèm KBASE_GMAIL_SMTP_HOST=mail-test KBASE_GMAIL_SMTP_PORT=1025 KBASE_GMAIL_SMTP_AUTH=false KBASE_GMAIL_SMTP_STARTTLS=false
STOP_ALL_COMMAND=docker compose down (giữ postgres_data/minio_data)
RESET_ALL_COMMAND=docker compose down -v (destructive: xóa cả hai named volume)
START_FRONTEND_COMMAND=<không áp dụng trong phase backend hiện tại>
```

Topologgie runtime local mục tiêu đã chạy thật:

```text
backend (image kbase-backend:local, port 8080)
postgres + postgres_data
minio + minio_data (+ console 9001)
redis (ephemeral OTP state, tmpfs)
Gmail SMTP là external integration và không chạy trong Docker Compose.
```

`docker-compose.mail-test.yml` là optional override chỉ dùng cho verification tự động (mailpit sink bắt OTP/invitation email để kiểm chứng MailService boundary); không thuộc designed topology và không bắt buộc khi chạy product.

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
GENERATE_API_SCHEMA_COMMAND=<thủ công - chạy backend, lấy GET /v3/api-docs và đồng bộ docs/generated/api-schema.md cùng thay đổi API contract>
VERIFY_API_SCHEMA_COMMAND=mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test
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

### AI v1 M10 verification record

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=RedisAiUsageGuardIntegrationTest" test` | Pass — 5/5 | Redis 7.4 Testcontainer; atomic limit, shared user isolation, controllable rollover, TTL semantics and unavailable mapping. |
| `mvn -B -ntp "-Dtest=AiObservabilityTest,GuideControllerTest,ProjectAssistantM7IntegrationTest,OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` | Pass | M10 bounded telemetry, Guide guard/provider boundary, Project Assistant guard ordering and exact runtime OpenAPI regression. |
| `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 371 tests, 0 failures/errors/skips | Non-fatal Testcontainers shutdown/placeholder PostgreSQL scheduler warnings were observed; Surefire completed successfully. |
| `docker compose -f docker-compose.yml config --quiet` + `git diff --check` | Pass | M10 env passthrough valid; no whitespace errors; no Flyway/generated DB change. |
| Sensitive-data/scope scan | Pass | No raw prompt/chunk/answer/vector/hash/storage key/job lease/worker/Redis/provider/credential emission; no real Gemini network. |

M10 automated verification uses deterministic fakes and does not require `KBASE_AI_GEMINI_API_KEY`. M11 remains responsible for provider-backed Docker golden journeys, restart/recovery and AI v1 freeze.

### AI v1 M11 verification record (AI v1 Freeze, 2026-09-26)

Chạy trên isolated Compose project `kbase-m11fix` (alternate host ports 18080/15432/16379/19000, pinned pgvector, Mailpit, `runtime-test` deterministic provider); project `kbase` chính không bị chạm đến:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| Final gate `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 377 tests, 0 failures/errors/skips; jar repackage | Teardown warnings non-fatal. |
| Compose `config --quiet` (base, base+mail-test, +mỗi M11 override: runtime-test, chat-failure, embedding-failure, race, rate, worker-paused) | Pass | Không đổi production defaults. |
| AI-VERIFY-05 security matrix | Pass — 37/37 | Cross-project vector trap; MEMBER/OWNER/ADMIN non-creator 404 ×5 ops; foreign/former 403 ×5; prompt injection; deleted-source lifecycle; source authz; Guide isolation (2 packaged READY sources; project/private sentinels NO_EVIDENCE). |
| AI-VERIFY-06 retention matrix | Pass — 15/15, 10/11 + observation, 16/16 | `run_at` = loss + 7.00000 days; rejoin restore + purge CANCELLED + quota inclusion; in-flight revoke→rejoin race 403/FAILED/0 citations; runtime purge qua due-time fixture chỉ trên job hợp lệ; no resurrection. |
| AI-VERIFY-08 restart/recovery | Pass — 6/6, 9/9, persistence, 7/7 | Pending job resume sau backend-only stop; stale PROCESSING reclaim (attempt 2, stale token transition 0 rows); PostgreSQL/MinIO persistence qua restart; Redis ephemerality; abrupt chat JVM death: PROCESSING stuck + 409 `AI_REQUEST_IN_PROGRESS` + creator DELETE workaround verified. |
| AI-VERIFY-04 comprehensive log audit | Pass — 0 sensitive hits / 518 dòng / 9 scenario | Question/answer/context/Guide evidence/vector/hash/storageKey/payload/lease/JWT/OTP/password/credential/provider raw response/Redis rate key: 0; error logs chỉ category-only. |
| AI-VERIFY-09 consistency audits | Pass | Config defaults đồng bộ 5 nguồn; Flyway V1–V4 + 18 tables + `vector(768)` + HNSW + Hibernate validate; runtime OpenAPI 38/57/15 = contract test = generated snapshot; 429/503 contract đúng 3 AI interactive ops. |
| `git diff --check` | Pass | Không whitespace error; working tree chỉ chứa doc updates cho review. |

Không có code/schema/API change trong M11 completion; `docs/generated/db-schema.md` và `docs/generated/api-schema.md` không cần tái sinh. Real Gemini credential/network không được dùng. **AI v1 backend FROZEN 2026-09-26.**

### M13 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18`:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` | Pass — 21/21 (19 + 2) | Full context profile `test` không cần external infrastructure (docs endpoints không chạm DB), real SecurityFilterChain: `/v3/api-docs` 200 và spec OpenAPI 3 "KBase Core API v1" với đúng 32 paths / 47 operations (đếm lại trên runtime trong M15); `bearerAuth` là HTTP bearer JWT; 6 auth public endpoints không có security requirement; mọi operation khác require `bearerAuth`; 4 admin operations ghi "Requires SystemRole.ADMIN" và tag `Admin - *`; 12 tags canonical theo đúng thứ tự; permission descriptions MEMBER/OWNER/ADMIN + storage-first + uploader rules; upload multipart `file` binary + `metadata` JSON, batch `files` array binary; download/preview binary string/format; `Range` header + `206` + `416` + `Content-Range` + `PREVIEW_NOT_SUPPORTED`; `ApiErrorResponse` schema đầy đủ và được tham chiếu >20 lần; OTP/Gmail/storage error codes đúng endpoint; không có `passwordHash`/`tokenHash`/`storageKey`/`refreshToken` hay schema "Entity"; không có AI/RAG paths; Swagger UI redirect trong khi `/api/v1/users/me` vẫn 401; khi `kbase.openapi.enabled=false` thì `/v3/api-docs` + swagger-ui 401 |
| `mvn -B -ntp test` (M13 final) | Pass — 209 tests, 0 failures, 0 errors, 0 skipped | Full suite M1–M13 (188 cũ + 21 OpenAPI) |
| `mvn -B -ntp clean verify` (M13 final) | Pass — BUILD SUCCESS; 209 tests | Compile/package và Spring Boot jar repackage pass |
| Static M13 scope/leakage review | Pass | Không endpoint mới, không đổi runtime contract, không nới SecurityConfig/CORS (chỉ permit docs paths khi flag bật), không đổi auth/OTP/Redis/Gmail/storage; refresh token không document thành bearer/JSON field; OTP chỉ là email verification; invitation giữ token riêng; JPA entity không xuất hiện trong spec |

M13 không thay đổi Flyway migration; `docs/generated/db-schema.md` không cần sinh lại. `docs/generated/api-schema.md` đã được cập nhật: runtime `/v3/api-docs` là contract máy đọc được từ milestone này.

### M14 verification record

Các kiểm tra sau đã chạy ngày `2026-09-18` trên Docker Desktop 29.8.0 + Compose v5.5.1:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `docker compose -f docker-compose.yml config --quiet` và với override mail-test | Pass | Config hợp lệ, các biến `:?required` bắt buộc từ `.env` (git-ignored) |
| Clean startup: `docker compose down -v` → `docker compose up -d --build` | Pass | Build image thành công; redis/minio/postgres lần lượt Healthy trước khi backend start (depends_on service_healthy, không sleep); `kbase_postgres_data`/`kbase_minio_data` tạo mới |
| Backend startup logs | Pass | Flyway: `Empty Schema → migrating V1, V2, V3 → successfully applied 3 migrations ... now at version v3` trên `jdbc:postgresql://postgres:5432/kbase`; Hibernate `ddl-auto=validate` pass; Tomcat 8080; bucket `kbase-documents` auto-created theo local config |
| Docker-internal wiring | Pass | Backend env: `KBASE_POSTGRES_HOST=postgres`, `KBASE_REDIS_HOST=redis`, `KBASE_STORAGE_ENDPOINT=http://minio:9000`; không nhắm localhost từ trong container |
| OpenAPI trong container | Pass | `GET /v3/api-docs` → 200; `/swagger-ui.html` → 302 redirect |
| Auth journey qua container (mail double `axllent/mailpit`) | Pass | register 201 (unverified); Redis OTP state key + TTL 287s; email "Verify your KBase email address" nhận tại mail double (MailService boundary); verify-email 200; Redis state bị xóa sau verify; login 200 (JWT HS256, expiresIn 900, cookie HttpOnly/Path=/api/v1/auth/Secure=false); refresh 200; logout 204; refresh sau logout 401 `REFRESH_SESSION_REVOKED` |
| Core flows qua container | Pass | project create 201 + OWNER; folder/category/tag 201; upload PDF/MP4 multipart 201; search `q`/tag filter/sort 200; PATCH metadata 200; download MD5 khớp file upload + `Content-Disposition: attachment`; preview PDF 200 inline; MP4 `Range: bytes=0-1023` → 206 + `Content-Range: bytes 0-1023/2097176`; invalid range → 416 + `bytes */2097176`; invitation create 201 → token từ email link → user thứ hai register/verify/login → accept 200 role MEMBER; MEMBER đọc/download 200 nhưng delete doc của OWNER 403 `DOCUMENT_MODIFICATION_FORBIDDEN`; remove member 204; former member search 403 `PROJECT_ACCESS_FORBIDDEN` |
| Persistence — backend restart | Pass | `docker compose restart backend`: Flyway "Schema is up to date. No migration necessary." (history persist trong `postgres_data`); project/document data nguyên vẹn, MD5 khớp |
| Persistence — postgres/minio force-recreate (giữ volume) | Pass | Data + JWT user + document binary còn nguyên; `flyway_schema_history` 3 rows success; MinIO object download được sau recreate |
| Persistence — Redis recreation | Pass | OTP state biến mất (key 0) sau recreate; verify bằng OTP cũ → 400 `OTP_EXPIRED`; resend 204 (cooldown cũng mất); verify OTP mới 200; login 200 |
| Hard delete storage-first | Pass | document delete 204 → object biến mất khỏi bucket; project delete (OWNER) 204 → project 404 + membership/folder/category/tag/document rows cascade + bucket rỗng |
| Log leak scan trên `docker compose logs backend` | Pass | Không password/JWT/refresh token/OTP/invitation token/App Password/MinIO secret; line "Using generated security password" đã loại bằng exclude `UserDetailsServiceAutoConfiguration` |
| `mvn -B -ntp clean verify` (M14 final, sau fix) | Pass — BUILD SUCCESS; 210 tests, 0 failures/errors/skipped | Compile/package/repackage pass |

Bug từ M11 đã sửa trong M14: `ProjectService.deleteProject` (OWNER path) fail `TransientPropertyValueException` trên Hibernate 7.4 vì membership của caller là managed entity khi flush delete project. Fix tối thiểu: `ProjectRepository.deleteProjectCascade` (bulk JPQL delete, DB FK cascade vẫn là lớp authoritative); unit test cập nhật; regression test mới `ownerProjectHardDeleteWorksWhenTheOwnerMembershipIsLoaded` trong `DocumentApiIntegrationTest` (4/4) — trước đó chỉ ADMIN path được test trên DB thật.

### M15 verification record (Core v1 Freeze)

Các kiểm tra sau đã chạy ngày `2026-09-19` (M15 — Full Verification / Core v1 Freeze; không có code change):

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp clean verify` (M15 release gate) | Pass — BUILD SUCCESS; 210 tests, 0 failures/errors/skipped | VERIFY-01/02/03/04/05/11: toàn bộ unit + PostgreSQL/Redis/MinIO Testcontainer + security + search + OpenAPI contract + error contract suites |
| `docker compose -f docker-compose.yml config --quiet` (base + mail-test override) | Pass | Compose config hợp lệ, `:?required` env bắt buộc |
| Clean Docker startup từ volume rỗng (`down -v` → `up -d --build` + mail double) | Pass | redis/postgres/minio Healthy trước backend; Flyway `Empty Schema → V1 → V2 → V3 → successfully applied 3 migrations`; Hibernate validate pass; api-docs 200 |
| DB catalog inspection trong container | Pass | Đúng 10 persistent tables (+ `flyway_schema_history`); `users.email_verified_at` timestamptz nullable; không OTP-like table; Flyway history 3 rows success |
| Auth journey qua container | Pass | register 201 unverified; Redis `kbase:otp:email-verification:{userId}` + cooldown, TTL 292s, hash structure (không raw OTP); OTP email qua mail double; verify 200 + Redis state xóa + `email_verified_at` set; login 200 (JWT HS256 chỉ jti/sub/systemRole/iat/exp, expiresIn 900, cookie HttpOnly Secure=false local); refresh 200; logout 204; refresh-sau-logout 401 `REFRESH_SESSION_REVOKED` |
| Core flows qua container | Pass | project 201 `currentUserRole:OWNER` (đúng 1 OWNER); folder/category/tag 201; upload PDF/MP4/MD 201 (metadata same-project, response không expose storageKey); download MD5 khớp + `Content-Disposition: attachment`; preview PDF 200 inline; MP4 `Range` 206 `bytes 0-1023/2000032`; invalid range 416; search q/fileKind/tagId+folderId/sort DESC/invalid sort 400/`size=1000` clamp 100 |
| Invitation + membership matrix qua container | Pass | invitation 201 PENDING (raw token chỉ trong email link); user2 register/verify/login → accept 200 MEMBER; MEMBER đọc + download 200 nhưng delete doc OWNER 403 `DOCUMENT_MODIFICATION_FORBIDDEN`, PATCH project 403 `PROJECT_MANAGEMENT_FORBIDDEN`; USER → `/admin/users` 403; anonymous 401; MEMBER upload/delete own doc OK; remove member 204 → former member 403 `PROJECT_ACCESS_FORBIDDEN`, documents remain |
| Hard delete storage-first qua container | Pass | document delete 204 + object biến mất; OWNER-path project delete 204 (regression M14 giữ đúng ở runtime) → project 404, bucket rỗng, mọi relational rows cascade |
| Persistence — backend restart | Pass | Flyway "Schema is up to date. No migration necessary." (history trong `postgres_data`); download checksum khớp |
| Persistence — postgres/minio force-recreate giữ volume | Pass | users + Flyway history survive; MinIO object download MD5 khớp |
| Persistence — Redis force-recreate | Pass | OTP state mất (chấp nhận được); stale verify 400 `OTP_EXPIRED`; resend 204; verify OTP mới 200; login 200 |
| Log leak scan + JWT probes | Pass | 0 hits cho password/JWT/refresh token/OTP/invitation token/App Password/MinIO secret/storageKey/SQL trong `docker compose logs backend`; tampered + garbage JWT → 401 |
| Static audits (VERIFY-09/10 + consistency) | Pass | Runtime `/v3/api-docs` = 32 paths / **47 operations** khớp `EXPECTED_PATHS` của contract test và 47 endpoint definitions SD-04; không forbidden endpoint; error catalog ↔ `ErrorCode` (68 codes) nhất quán; compose ↔ `DEPLOYMENT.md`; architecture violation scans clean |
| Sửa documentation miscount | Docs-only | "48 operations" từ bản ghi M13 là miscount; runtime thật 47. Đã sửa `api-schema.md`, `CURRENT_STATE.md`, `DEVELOPMENT.md`, `QUALITY_SCORE.md`, master plan M13/M15; correction note trong completed M13 plan. Không có code change, không cần regression thêm |

Kết quả: **Core v1 FROZEN ngày 2026-09-19** (M0–M15 tất cả PASS). Freeze report đầy đủ: `docs/exec-plans/completed/KBase_Core_v1_M15_Full_Verification_Freeze.md`.

### AI v1 M2 verification record (pgvector / AI Persistence Schema)

Các kiểm tra sau đã chạy ngày `2026-09-22` trên nhánh `feat-AI` với Docker daemon khả dụng:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest,FlywayAiUpgradeIntegrationTest,AiPersistenceIntegrationTest" test` | Pass — 36/36, 0 failures, 0 errors, 0 skipped | `AiPersistenceIntegrationTest` 11/11; `FlywayAiUpgradeIntegrationTest` 1/1; `FlywayMigrationIntegrityTest` 13/13; `JpaMappingRepositoryIntegrationTest` 11/11 trên pgvector PostgreSQL 17.11 |
| `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 241 tests, 0 failures, 0 errors, 0 skipped | Full Core + AI regression, compile/package và Spring Boot repackage |
| `docker compose -f docker-compose.yml config --quiet` | Pass | Compose topology/config hợp lệ; pgvector image và named volumes giữ nguyên |
| `git diff --check` | Pass | Không có whitespace error |
| Fresh V1–V4 và Core V1–V3 → V4 upgrade | Pass | V4 additive sau Core V3; Core data giữ nguyên ở upgrade path; Hibernate `ddl-auto=validate` pass |
| Live catalog / scope review | Pass | `vector` extension, `vector(768)`, HNSW `vector_cosine_ops`, relational indexes, FK/delete/status checks, partial active-generation index và SQL project/active-version filters đã được verify; V1–V3 và `docs/generated/api-schema.md` không đổi |

M2 không thêm worker, provider adapter/call, extraction, indexing, retrieval/RAG orchestration, Guide behavior hoặc public AI endpoint. Full Compose runtime verification với V4 vẫn thuộc AI runtime milestone sau; M2 chỉ yêu cầu Compose config và PostgreSQL/Testcontainers evidence.

### AI v1 M3 verification record (Durable Job Engine & Core Lifecycle Hooks)

Các kiểm tra sau đã chạy ngày `2026-09-22` trên nhánh `feat-AI` với PostgreSQL 17.11/pgvector Testcontainers:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `M3_JOB_TEST_COMMAND` | Pass — 9/9 | Claim `FOR UPDATE SKIP LOCKED`, two-worker exclusivity/independent progress, due/retry timing, stale lease/token protection, bounded attempts, terminal filtering và advisory-lock active dedup |
| `M3_SCHEDULER_TEST_COMMAND` | Pass — 5/5 | Bounded batch/registry, no-handler preservation, handler ngoài claim transaction, SUCCESS/RETRY/FAILURE/exception mapping và AI-disabled context |
| `M3_DOCUMENT_INTENT_TEST_COMMAND` | Pass — 8/8 | Supported/unsupported upload intent, deterministic metadata, active idempotency, single/batch rollback và unchanged Core response |
| `M3_RETENTION_TEST_COMMAND` | Pass — 5/5 | Remove/leave `+P7D`, same-transaction persistence, rejoin cancellation/no-op, later-loss rescheduling và project cascade cleanup |
| `mvn -B -ntp test` | Pass — `BUILD SUCCESS`; 268 tests, 0 failures, 0 errors, 0 skipped | Full Core + AI M3 regression |
| `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 268 tests, 0 failures, 0 errors, 0 skipped | Compile/package/repackage và full release gate |
| `docker compose -f docker-compose.yml config --quiet` | Pass | Compose topology/config không đổi |
| `git diff --check` | Pass | Không có whitespace error |
| Static scope review | Pass | V4 và generated DB/API docs không đổi; AI không import MinIO/provider/network/extraction SDK; không có public AI controller hoặc destructive purge handler; job state chỉ metadata/category-safe |

M3 không thay đổi migration hoặc API contract, vì vậy không sinh lại `docs/generated/db-schema.md` hay `docs/generated/api-schema.md`. Hikari/Testcontainers có warning kết nối trong giai đoạn shutdown sau khi fork đã exit thành công; Surefire vẫn báo `BUILD SUCCESS` với 268/268.

### AI v1 M4 verification record (Gemini Provider Adapters)

Các kiểm tra sau đã chạy ngày `2026-09-22` trên nhánh `feat-AI`. Automated M4 không cần Gemini credential và không gọi public Gemini network:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn -B -ntp "-Dtest=AiGeminiProviderConfigurationTest,AiProviderErrorTranslatorTest,AiProviderPrivacyTest,SpringAiGeminiChatAdapterTest,SpringAiGeminiEmbeddingAdapterTest" test` | Pass — 22/22, 0 failures, 0 errors, 0 skipped | Disabled/enabled configuration, safe key validation, no-network synthetic key context, chat order/evidence isolation, query/document preparation, exact 768/non-finite validation, provider categories và privacy negative tests |
| `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 290 tests, 0 failures, 0 errors, 0 skipped | Full Core + AI regression, compile/package/repackage pass |
| `docker compose -f docker-compose.yml config --quiet` | Pass | Compose topology/config hợp lệ; M4 không đổi service, volume, migration hoặc API |
| `git diff --check` | Pass | Không có whitespace error |
| Static boundary/scope audit | Pass | Spring AI/Google GenAI imports chỉ trong `com.kbase.ai.provider.springai`; không có storage/controller/RAG/extraction leakage; không đổi generated DB/API docs |

M4 wires `kbase.ai.provider.request-timeout` through Google GenAI `HttpOptions`. Google GenAI `1.65.0` không có independent connect-timeout API trên selected client path; `kbase.ai.provider.connect-timeout` được giữ typed nhưng không claim là active. Hikari/Testcontainers shutdown warnings xuất hiện sau suite đã pass và không làm fail `BUILD SUCCESS`.

### AI v1 M5 verification record (Content Extraction / Chunking / Document Indexing)

Các kiểm tra sau đã chạy ngày `2026-09-23` trên nhánh `feat-AI` với Docker daemon khả dụng:

| Lệnh hoặc kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `AI_M5_EXTRACTION_INDEX_TEST_COMMAND` | Pass — 17/17, 0 failures, 0 errors, 0 skipped | Extraction/chunking 2/2; handler 7/7; status/manual retry 2/2; PostgreSQL persistence 6/6. Covers exact format allowlist, source locations, deterministic boundaries, empty/corrupt/no-text safety, unsupported no-op, partial embedding failure, retry exhaustion, stale lease, activation idempotency and document/project delete races. |
| `mvn -B -ntp clean verify` | Pass — `BUILD SUCCESS`; 307 tests, 0 failures, 0 errors, 0 skipped | Full Core + AI regression, compile/package/repackage pass. PostgreSQL/Redis/MinIO/Testcontainers suites completed. Shutdown-only Hikari/Testcontainers connection warnings did not change the successful result. |
| `docker compose -f docker-compose.yml config --quiet` | Pass | Compose topology/config remains valid; no new service, volume or migration. |
| `git diff --check` | Pass | No whitespace errors. |
| Static boundary/privacy audit | Pass | No MinIO SDK import in `com.kbase.ai`; Tika only in extraction adapter; Spring AI/Google GenAI only in provider adapter package; no public AI controller/retrieval/conversation/Guide behavior; generated DB/API docs unchanged. |

Real Gemini credential/network and live-provider Compose indexing are intentionally not part of the M5 automated gate. The worker's fixed lease/no-heartbeat limitation is recorded in the M5 completion plan and technical-debt tracker.

### AI v1 M7 verification record (Project Assistant Conversations & REST API)

M7 targeted command on `feat-AI`:

```text
mvn -B -ntp "-Dtest=ProjectAssistantM7IntegrationTest,ProjectAssistantTitleTest,OpenApiContractIntegrationTest" test
```

Result: 36/36 PASS, 0 failures, 0 errors, 0 skipped on 2026-09-24. Real PostgreSQL 17.11/pgvector and SecurityFilterChain exercise the nine M7 operations with deterministic fake chat/embedding ports; no real Gemini key or public network. Exact OpenAPI path/operation assertions pass at 37 paths / 56 operations. Full `mvn -B -ntp clean verify`: BUILD SUCCESS, 346 tests, 0 failures/errors/skips; Compose config and diff check PASS. The first full run failed one stale M5 exception-message assertion; it now checks stable `AI_INDEX_RETRY_NOT_ALLOWED` ErrorCode/409, and the full rerun passed.

## Tài liệu Generated

| Tài liệu | Nguồn sự thật | Cách cập nhật | Cách xác minh |
|---|---|---|---|
| `docs/generated/db-schema.md` | Flyway migration + PostgreSQL schema thực tế | `Thủ công trong cùng thay đổi migration; generator tự động chưa được thiết lập` | `mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test (Flyway từ DB rỗng + Hibernate validate + PostgreSQL catalog inspection)` |
| `docs/generated/api-schema.md` | springdoc runtime `/v3/api-docs` sinh từ controllers/DTO đã verify | `Thủ công cùng thay đổi API contract; runtime spec là contract máy đọc được từ M13` | `mvn -B -ntp "-Dtest=OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest" test` và đối chiếu REST spec |

Quy tắc:

- Không chỉnh tay tài liệu được sinh tự động sau khi generator đã được thiết lập.
- Trước khi generator tồn tại, file generated phải tự khai báo rõ trạng thái placeholder/not-generated.
- Generated documentation phải được cập nhật trong cùng thay đổi với source of truth.
- Không dùng generated documentation lỗi thời làm căn cứ triển khai.
- Không đưa secret hoặc dữ liệu production vào generated documentation.

## Reset môi trường

Docker runtime M14 đã có thật; reset command chuẩn:

- `docker compose down`: dừng và xóa containers/network, giữ `postgres_data` + `minio_data` (non-destructive).
- `docker compose down -v`: reset hoàn toàn — xóa cả hai named volume; Flyway sẽ chạy lại từ database rỗng ở lần start sau (destructive, chỉ dùng khi chủ ý reset development data).
- Restart riêng: `docker compose restart backend|postgres|minio|redis`.
- Reset Redis OTP: recreate container là đủ; mất pending OTP là chấp nhận được vì Redis OTP là ephemeral và user có thể resend.
- Reset MinIO development objects: xóa bucket qua MinIO Console (host port 9001) hoặc `docker compose down -v` khi muốn reset cả database.
- Xóa build artifact: `mvn clean`, image `kbase-backend:local` xóa bằng `docker rmi`.

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
