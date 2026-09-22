# Kế hoạch Đã hoàn thành

## Kế hoạch đã hoàn thành

- `KBase_Core_v1_M0_Preflight.md`
  - Milestone: `M0 – Preflight & Execution Baseline`
  - Result: `M0 Gate PASS` ngày `2026-09-17`
  - Evidence: repository inventory, Maven dependency probe, locked technical baseline, optional-choice record và `.harness/source-doc-registry.json`

- `KBase_Core_v1_M1_Bootstrap.md`
  - Milestone: `M1 – Project Bootstrap + Local Runtime Skeleton`
  - Result: `M1 Gate PASS` ngày `2026-09-17`
  - Evidence: Maven build/test, dependency tree, typed-properties/context tests, feature-first skeleton và Compose volume inspection

- `KBase_Core_v1_M2_PostgreSQL_Flyway.md`
  - Milestone: `M2 – PostgreSQL / Flyway Schema`
  - Result: `M2 Gate PASS` ngày `2026-09-17`
  - Evidence: 3 Flyway migrations (10 persistent tables, constraint/partial/expression/query indexes), `FlywayMigrationIntegrityTest` 12/12 pass trên PostgreSQL 17 Testcontainer, Hibernate `ddl-auto=validate` pass, `docs/generated/db-schema.md` tái sinh

- `KBase_Core_v1_M3_JPA.md`
  - Milestone: `M3 – JPA Entities & Repositories`
  - Result: `M3 Gate PASS` ngày `2026-09-17`
  - Evidence: 10 entity, 5 enum, 10 feature-local repository, projection/query/specification và `JpaMappingRepositoryIntegrationTest` 11/11 pass trên PostgreSQL 17 Testcontainer; full suite 25/25; Hibernate `ddl-auto=validate` pass

- `KBase_Core_v1_M4_Shared_Error_Request.md`
  - Milestone: `M4 – Shared Error / Request Infrastructure`
  - Result: `M4 Gate PASS` ngày `2026-09-17`
  - Evidence: centralized `ErrorCode`, exception hierarchy, `ApiErrorResponse`, `RequestIdFilter`/MDC, `GlobalExceptionHandler`, PostgreSQL constraint translator; targeted suite 16/16 và full suite `mvn clean verify` 41/41 pass

- `KBase_Core_v1_M5_Redis_OTP_Gmail_Mail.md`
  - Milestone: `M5 – Redis OTP + Gmail Mail Infrastructure`
  - Result: `M5 Gate PASS` ngày `2026-09-17`
  - Evidence: `OtpStore`/`RedisOtpStore`, `OtpService` với HMAC-SHA-256 và `SecureRandom`, `MailService`/`SmtpMailService`, Redis 7.4 Testcontainer 6/6, fake SMTP 5/5 và full suite `mvn clean verify` 58/58 pass

- `KBase_Core_v1_M6_Spring_Security_Authentication.md`
  - Milestone: `M6 – Spring Security + Authentication`
  - Result: `M6 Gate PASS` ngày `2026-09-17`
  - Evidence: `SecurityConfig`/`JwtService`/`JwtAuthenticationFilter`/security handlers, `RefreshSessionService` (PostgreSQL hash-only), `EmailVerificationService`, `AuthService` + 6 auth endpoints; `AuthenticationSecurityIntegrationTest` 14/14 trên PostgreSQL/Redis Testcontainers với real filter chain; unit 32/32; full suite `mvn test` và `mvn clean verify` 104/104 pass; `docs/generated/api-schema.md` đồng bộ auth endpoints

- `KBase_Core_v1_M7_User_Project_Membership.md`
  - Milestone: `M7 – User / Project / Membership`
  - Result: `M7 Gate PASS` ngày `2026-09-18`
  - Evidence: `UserService`/`ProjectService`/`ProjectMemberService`/`ProjectAuthorizationService`, current-user + admin-user APIs, project create/list/get/update + admin listing, membership list/remove/leave; `UserProjectMembershipIntegrationTest` 6/6 trên PostgreSQL Testcontainer với real filter chain; unit 20/20; full suite `mvn test` và `mvn clean verify` 130/130 pass; `docs/generated/api-schema.md` đồng bộ 9 endpoints mới

- `KBase_Core_v1_M8_Invitation_Lifecycle.md`
  - Milestone: `M8 – Invitation Lifecycle`
  - Result: `M8 Gate PASS` ngày `2026-09-18`
  - Evidence: `InvitationService`/`InvitationTokens` + create/list/resend/cancel/accept endpoints; secure token (SHA-256 hash-only, không OTP), mail qua `MailService.sendProjectInvitation` với rollback khi fail, accept PESSIMISTIC_WRITE với concurrency test 2 thread chỉ 1 thắng; `InvitationLifecycleIntegrationTest` 5/5 trên PostgreSQL Testcontainer; unit 8/8; full suite `mvn test` và `mvn clean verify` 143/143 pass

- `KBase_Core_v1_M9_Folder_Category_Tag.md`
  - Milestone: `M9 – Folder / Category / Tag`
  - Result: `M9 Gate PASS` ngày `2026-09-18`
  - Evidence: 12 project-scoped folder/category/tag endpoints; OWNER/ADMIN folder/category management, MEMBER read-only folder/category access, MEMBER tag create, OWNER/ADMIN tag rename/delete; case-insensitive uniqueness, ancestor-walk cycle prevention, folder non-empty/category in-use protection and DocumentTag-only tag delete; unit 12/12, `OrganizationIntegrationTest` 6/6, migration/JPA regression 23/23, full suite `mvn test` và `mvn clean verify` 161/161 pass

- `KBase_Core_v1_M10_MinIO_Storage_Infrastructure.md`
  - Milestone: `M10 – MinIO Storage Infrastructure`
  - Result: `M10 Gate PASS` ngày `2026-09-18`
  - Evidence: vendor-neutral `StorageService`, singleton configured `MinioClient`, local-only bucket initializer, stream upload/full/range read/stat/single and batch delete, typed internal exception translation and backend-controlled object keys; storage/config unit 7/7, real MinIO Testcontainer 2/2, full suite `mvn test` và `mvn clean verify` 169/169 pass; private unversioned bucket boundary and `minio_data:/data` Compose volume preserved

- `KBase_Core_v1_M12_Document_Search.md`
  - Milestone: `M12 – Document Search / Pagination / Sorting`
  - Result: `M12 Gate PASS` ngày `2026-09-18`
  - Evidence: project-scoped `DocumentSearchService`/criteria/endpoint, all approved metadata filters, pagination/sort whitelist canonicalization, tag `EXISTS` duplicate guard and MEMBER/OWNER/ADMIN/non-member API matrix; PostgreSQL Testcontainer API 2/2, focused suite 16/16, full `mvn test` and `mvn clean verify` 188/188 pass

- `KBase_Core_v1_M13_OpenAPI_Swagger.md`
  - Milestone: `M13 – OpenAPI / Swagger`
  - Result: `M13 Gate PASS` ngày `2026-09-18`
  - Evidence: `config/OpenApiConfig` (metadata, `bearerAuth` HTTP Bearer JWT, 12 canonical tags, shared 401 customizer), 12 controllers / 47 operations annotated with correct public/protected/ADMIN security requirements and MEMBER/OWNER/ADMIN permission rules (số 47 được đếm lại trên runtime spec trong M15; bản ghi gốc ghi 48 là miscount), multipart `file`/`files` + `metadata` JSON parts, binary download/preview, MP4 `Range`/`206`/`416`, OTP/Gmail/Redis/storage error codes, exposure flags per environment; `OpenApiContractIntegrationTest` 19/19 + `OpenApiDisabledIntegrationTest` 2/2 trên real SecurityFilterChain; full `mvn test` and `mvn clean verify` 209/209 pass

- `KBase_Core_v1_M14_Docker_Runtime.md`
  - Milestone: `M14 – Full Docker Runtime Verification`
  - Result: `M14 Gate PASS` ngày `2026-09-18`
  - Evidence: `Dockerfile` multi-stage non-root + compose backend/postgres/minio/redis với healthcheck-gated startup; clean startup từ rỗng với Flyway V1–V3 + Hibernate validate trong container; golden journeys qua containerized backend (auth/invitation/organization/document/search/hard delete); `postgres_data`/`minio_data` persistence + Redis ephemeral verified; log leak scan clean; fix bug M11 OWNER-path project hard delete (bulk cascade delete + regression test); full `mvn clean verify` 210/210 pass

- `KBase_Core_v1_M15_Full_Verification_Freeze.md`
  - Milestone: `M15 – Full Verification / Core v1 Freeze`
  - Result: `M15 Gate PASS` ngày `2026-09-19` — **Core v1 FROZEN**
  - Evidence: full suite `mvn -B -ntp clean verify` 210/210 (BUILD SUCCESS); Docker runtime re-verification từ volume rỗng (Flyway V1–V3 + Hibernate validate trong container, 10 persistent tables, không OTP table); toàn bộ golden journeys qua containerized backend (auth journey với Redis OTP state/TTL + mail double, project/organization/upload/download checksum/preview 206+416/search matrix/invitation + MEMBER permissions/hard delete storage-first OWNER-path); persistence qua backend restart + postgres/minio force-recreate + Redis recreation (OTP loss → resend OK); static architecture/leakage scans clean; runtime `/v3/api-docs` = 32 paths / 47 operations khớp contract test và SD-04; error catalog ↔ `ErrorCode` nhất quán; fix một documentation miscount (48→47 operations) với correction note; **không có code change trong M15**. Chi tiết đầy đủ: `KBase_Core_v1_M15_Full_Verification_Freeze.md` (Core v1 Freeze Report)

- `KBase_Core_v1_M16_Post_Audit_Fixes.md`
  - Milestone: `M16 – Post-Audit Fixes (maintenance sau freeze, owner-approved)`
  - Result: `M16 Gate PASS` ngày `2026-09-19`
  - Evidence: Full Codebase Audit cùng ngày cho kết quả 0 BLOCKER / 0 HIGH / 1 MEDIUM / 8 LOW / 12 INFO; slice này fix 1 MEDIUM (M-01: invitation accept-expired giờ persist `EXPIRED` qua `InvitationExpiredException` + `@Transactional(noRollbackFor=...)`, regression test assert DB status + khả năng tạo invitation thay thế) và 7 LOW (L-01 batch compensation mỗi key đúng 1 lần; L-03 runtime swagger annotation search 400 khớp code; L-04 bỏ `JwtProperties.algorithm` + javadoc `findAllByStatusAndExpiresAtBefore`; L-05 LIKE wildcard escape cho `q` ở documents/my-projects/tags/admin-users; L-06 folder move pessimistic lock 2 rows theo UUID order + concurrency test 2 thread ngược chiều; L-07 SMTP failure log sanitized chỉ exception type; L-08 hygiene: xóa file rỗng `Trạng`, cập nhật `index.md`, bỏ `KBASE_MAIL_TEST_ENABLED`). Targeted suites: unit 48/48; integration 26/26 trên PostgreSQL/Redis Testcontainers (Invitation 5/5, Search 3/3, Organization 7/7, JpaMapping 11/11); full gate `mvn -B -ntp clean verify` **213/213**. Không fix: L-02 (PATCH document null semantics — cần product decision) và các INFO item (known limitations/phase mới) — phản hồi owner và ghi `../tech-debt-tracker.md`. Không đổi API contract, Flyway schema hay docs generated.

- `KBase_AI_Chatbot_v1_M1_Runtime_Foundation_and_Provider_Ports.md`
  - Milestone: `M1 – AI Runtime Foundation & Provider Ports`
  - Result: `M1 Gate PASS` ngày `2026-09-22`
  - Evidence: Spring AI BOM 2.0.1 + approved Google GenAI starters + pgvector JDBC 0.1.6 resolve; typed disabled-by-default AI config and KBase-owned provider ports; deterministic chat/embedding fakes; pgvector PostgreSQL 17.11 compatibility test 1/1 with extension, `vector(768)`, JDBC binding, cosine ordering and HNSW; full `mvn -B -ntp clean verify` **228/228** with package/repackage; no AI migration, endpoint, RAG behavior, provider adapter/call, or generated DB/API change.

Di chuyển các kế hoạch đã hoàn thành ở đây thay vì xóa chúng. Các kế hoạch đã hoàn thành là một phần của bề mặt bộ nhớ kho lưu trữ và giúp các lần chạy agent sau hiểu tại sao mã trông như vậy.
