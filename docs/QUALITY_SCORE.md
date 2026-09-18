# QUALITY_SCORE.md

Tài liệu này theo dõi liệu kho lưu trữ có đang trở nên mạnh hơn hay yếu hơn theo thời gian.

## Thang điểm

- `A`: đã xác minh, có thể đọc được, ổn định, ranh giới được thực thi
- `B`: hoạt động với các khoảng trống nhỏ
- `C`: hoạt động một phần, nhầm lẫn hoặc không ổn định đáng kể
- `D`: bị hỏng, không an toàn, hoặc cấu trúc không rõ ràng
- `-`: chưa có implementation/runtime để chấm; không được tự suy diễn thành điểm đạt

## Domain Sản phẩm

| Domain | Điểm | Xác minh | Khả năng đọc của Agent | Độ ổn định Test | Khoảng trống chính | Cập nhật lần cuối |
|--------|-------|-------------|-----------------|---------------|----------|-------------|
| Authentication & Email Verification | A | M6 registration/verify/resend/login/refresh/logout đã implement và verify qua `AuthenticationSecurityIntegrationTest` 14/14 (PostgreSQL+Redis Testcontainers, real filter chain) + unit 32/32; JWT/refresh/disabled-account/401-403 contract đã chứng minh | Cao - spec/security/service docs rõ | Auth unit + integration suite đã pass trong full suite 104/104 | Admin disable-user API và password change (M7) chưa triển khai để dùng revokeAllForUser; OTP Redis-unavailable mapping mới verify ở adapter/unit level | 2026-09-17 |
| Project / Membership / Invitation | A | M7 project/membership 6/6 + M8 invitation lifecycle 5/5 integration (gồm concurrency accept 2 thread chỉ 1 thắng) trên PostgreSQL Testcontainer với real filter chain; unit 28/28; full suite 161/161 | Cao - spec/service/security docs rõ | Project + membership + invitation suites đã pass lặp qua `mvn clean verify` | Project hard delete (M11) và document-level flows chưa có; invitation email manual smoke với Gmail thật thuộc M14 | 2026-09-18 |
| Folder / Category / Tag | A | M9 unit 12/12 + `OrganizationIntegrationTest` 6/6 trên PostgreSQL 17/Redis Testcontainers với real filter chain; full suite 161/161; folder/category/tag API contract đã đồng bộ | Cao - product/design/service/API rules rõ | Authorization, hierarchy, uniqueness, delete dependency và cross-project integrity đã được test | Document assignment/upload/lifecycle và project hard delete vẫn thuộc M11; OpenAPI runtime thuộc M13 | 2026-09-18 |
| Document / MinIO / Search | B | M10 `StorageService`/`MinioStorageService` streaming adapter, key factory, typed exception boundary và MinIO Testcontainer 2/2; full suite 169/169 | Cao | Storage port and MinIO boundary are covered; no SDK leaks from port | M11 document lifecycle/API/authorization, compensation and project hard delete; M12 search | 2026-09-18 |

## Lớp Kiến trúc

| Lớp | Điểm | Thực thi Ranh giới | Khả năng đọc của Agent | Khoảng trống chính | Cập nhật lần cuối |
|-------|-------|---------------------|-----------------|----------|-------------|
| Controller / API | B | 38 endpoints (6 auth + 15 user/project/membership + 5 invitation + 12 folder/category/tag) đã verify qua MockMvc security integration; shared `GlobalExceptionHandler`/`RequestIdFilter`/`PageResponse` và project-scoped organization controllers ổn định | Cao - REST/OpenAPI design có sẵn | OpenAPI runtime/schema generation thuộc M13; document/search controllers còn lại | 2026-09-18 |
| Service / Authorization | B | `ProjectAuthorizationService` là điểm vào project access/management duy nhất; auth, user/project/membership, invitation và `FolderService`/`CategoryService`/`TagService` đã implement + verify | Cao - Service/Security design có sẵn | DocumentAuthorizationService, document lifecycle và storage chưa triển khai | 2026-09-18 |
| Repository / Persistence | A | 10 JPA entity, 10 feature-local repository, projection/query/specification và Hibernate `ddl-auto=validate` đã verify trên PostgreSQL Testcontainer; Flyway vẫn là schema source-of-truth | Cao - DB/JPA design và integration evidence rõ | Chưa có service/business transaction ngoài auth | 2026-09-17 |
| Infrastructure Adapters | A | Redis OTP, Gmail SMTP và M10 MinIO adapters đã implement/verify sau port boundary; real MinIO Testcontainer kiểm tra stream/range/stat/delete | Cao - adapter boundaries rõ | Full backend runtime và Gmail SMTP manual smoke chưa triển khai | 2026-09-18 |
| UI | - | Không áp dụng phase hiện tại | Frontend optional | Hoãn | 2026-09-17 |

## Năng lực Kỹ thuật

| Năng lực | Điểm | Bằng chứng | Khoảng trống chính | Cập nhật lần cuối |
|---|---|---|---|---|
| Backend | B | M0–M10 gồm storage infrastructure MinIO streaming sau `StorageService`; `mvn clean verify` 169 tests | Document lifecycle/API, project hard delete và search (M11–M12) | 2026-09-18 |
| Frontend | - | `docs/FRONTEND.md` | Optional và hoãn khỏi phase hiện tại | 2026-09-17 |
| Database và migration | B | 3 Flyway migrations (V1 tables, V2 partial/expression unique, V3 query indexes), Flyway integrity 12/12 và JPA mapping/repository 11/11 pass trên PostgreSQL 17 Testcontainer; Hibernate validate pass; `docs/generated/db-schema.md` đã đối chiếu | Chưa có generator schema tự động; chưa chạy migration trên Compose runtime thật (M14) | 2026-09-17 |
| API contract | B | REST/OpenAPI design, shared `ApiErrorResponse`, 38 endpoints (6 auth + 15 user/project/membership + 5 invitation + 12 folder/category/tag) đã implement/verify và đồng bộ vào `docs/generated/api-schema.md` | Chưa generate endpoint schema từ springdoc/runtime (M13); document/search endpoints còn lại | 2026-09-18 |
| Tích hợp hệ thống | B | Compose MinIO `minio_data`, typed storage config, real MinIO Testcontainer stream/range/stat/delete plus prior PostgreSQL/Redis/fake-SMTP integration suites | Full backend runtime và Gmail SMTP manual smoke chưa triển khai | 2026-09-18 |
| Kiểm thử | B | M1–M10 unit/config/integration suite: 169 tests pass qua `mvn clean verify`, gồm MinIO real-container adapter verification | M11 document lifecycle and M13 OpenAPI runtime contract | 2026-09-18 |
| Thiết lập development | B | Maven commands, Compose dependency smoke, PostgreSQL/Redis/MinIO Testcontainers và fake SMTP đã được verify | Backend startup runtime và Gmail SMTP manual smoke chưa verify | 2026-09-18 |
| Reliability | - | `docs/RELIABILITY.md` | Golden journey chưa chạy | 2026-09-17 |
| Deployment và rollback | B | M1 `docker-compose.yml` với PostgreSQL/MinIO/Redis, named-volume boundary, Redis ephemeral policy và live dependency smoke | Backend Dockerfile/full Compose wiring, application runtime và rollback procedure chưa triển khai | 2026-09-17 |
| Quản lý trạng thái repository | B | `docs/CURRENT_STATE.md`, completed/active execution plans, source registry | Chưa có Git implementation history; archive không có `.git` | 2026-09-17 |

### Quy tắc Đánh giá Năng lực

- Việc một file tài liệu tồn tại không tự động làm tăng điểm.
- `docs/CURRENT_STATE.md` chỉ là bằng chứng tốt khi phản ánh đúng trạng thái đã được xác minh.
- `docs/generated/api-schema.md` chỉ là bằng chứng tốt khi đồng bộ với source of truth.
- Không chấm `A` nếu verification không thể chạy lặp.
- Khi bằng chứng lỗi thời hoặc không thể tái tạo, phải giảm điểm hoặc ghi rõ khoảng trống.
- Không chấm điểm implementation cho một domain chỉ vì design document đã hoàn chỉnh.

## Snapshot Benchmark

| Ngày | Biến thể Harness | Tỷ lệ Hoàn thành | Thử lại | Lỗi trước Review | Ghi chú |
|------|-----------------|----------------|--------|-----------------------|---------|
| 2026-09-17 | `KBase backend harness - pre-implementation` | 0% implementation | N/A | N/A | Source docs + master plan + active M0 slice đã sẵn sàng |
| 2026-09-17 | `KBase backend harness - M0 Gate` | 0% implementation | N/A | N/A | M0 Gate pass; Java 21/Spring Boot 4.1.1 và dependency baseline đã khóa; M1 plan READY; runtime chưa verify |
| 2026-09-17 | `KBase backend harness - M1 Gate` | M1 foundation | N/A | N/A | M1 Gate pass; Maven project, feature-first package skeleton, typed properties, profiles, Compose dependency skeleton và live dependency restart smoke đã verified; M2 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M2 Gate` | M2 schema | N/A | N/A | M2 Gate pass; 3 Flyway migrations tạo 10 persistent tables với đầy đủ PostgreSQL-specific constraint/index; migration integrity 12/12 pass trên PostgreSQL 17 Testcontainer; Hibernate validate pass; M3 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M3 Gate` | M3 persistence baseline | N/A | N/A | M3 Gate pass; 10 entity, 5 enum, 10 repository, projection/query/specification và PostgreSQL mapping/repository integration 11/11 pass; full suite 25/25; M4 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M4 Gate` | M4 shared error/request baseline | N/A | N/A | M4 Gate pass; centralized error contract, request ID/MDC, constraint translation và MockMvc/PostgreSQL verification; full suite 41/41; M5 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M5 Gate` | M5 Redis OTP + Gmail mail infrastructure | N/A | M5 Gate pass; RedisOtpStore/OtpService, MailService/SmtpMailService, protected OTP state, Redis 7.4 Testcontainer, fake SMTP và full suite 58/58; M6 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M6 Gate` | M6 Spring Security + authentication | N/A | M6 Gate pass; SecurityConfig/JwtService/JwtAuthenticationFilter, refresh session PostgreSQL hash-only, EmailVerificationService + AuthService, 6 auth endpoints; auth integration 14/14 với PostgreSQL/Redis Testcontainers; full suite 104/104; M7 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M7 Gate` | M7 User / Project / Membership | N/A | M7 Gate pass; ProjectAuthorizationService, user/project/membership APIs, single-OWNER + ADMIN override + documents remain; authorization matrix integration 6/6 trên PostgreSQL; full suite 130/130; M8 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M8 Gate` | M8 Invitation Lifecycle | N/A | M8 Gate pass; InvitationService với secure token (hash-only, không OTP), MailService reuse + rollback, cancel/resend lifecycle, accept PESSIMISTIC_WRITE với concurrency test 2 thread; integration 5/5 trên PostgreSQL; full suite 143/143; M9 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M9 Gate` | M9 Folder / Category / Tag | N/A | M9 Gate pass; 12 project-scoped endpoints, authorization matrix, nested folder cycle prevention, case-insensitive uniqueness, non-empty/in-use deletes, DocumentTag-only tag delete; unit 12/12, integration 6/6, integrity regression 23/23, full suite 161/161 |
| 2026-09-18 | `KBase backend harness - M10 Gate` | M10 MinIO Storage Infrastructure | N/A | M10 Gate pass; private unversioned bucket boundary, singleton MinIO adapter behind streaming port, range read and safe batch-delete result handling; unit/config 7/7, MinIO Testcontainer 2/2, full suite 169/169 |

## Nhật ký Đơn giản hóa

| Ngày | Thành phần Đã xóa | Kết quả | Quyết định |
|------|-------------------|---------|------------|
| 2026-09-17 | `Frontend khỏi active implementation scope` | `Giảm context và giữ đúng optional scope của đề` | `Giữ frontend deferred cho phase sau` |
