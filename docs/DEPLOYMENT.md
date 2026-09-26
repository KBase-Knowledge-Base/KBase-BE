# Quy trình Triển khai

## Mục đích

Tài liệu này mô tả cách build, phát hành, triển khai, kiểm chứng và rollback hệ thống.


## Baseline KBase Hiện tại

- Phase hiện tại chỉ build backend; frontend optional được hoãn.
- Local runtime mục tiêu dùng Docker Compose với `backend`, `postgres`, `minio`, `redis`; Gmail SMTP là external provider.
- PostgreSQL dùng named volume `postgres_data`; MinIO dùng `minio_data`; Redis OTP không cần durable volume.
- Flyway migration nằm trong backend artifact/source và chạy vào PostgreSQL khi backend startup theo cấu hình đã chốt; Hibernate validate schema sau migration.
- Backend container phải stateless đối với durable business data.
- Production deployment target cụ thể chưa được khóa; agent không được tự chọn AWS/Kubernetes/Terraform chỉ vì chúng xuất hiện trong trainer optional stack.

### Trạng thái M1 đã xác minh

M1 đã tạo `docker-compose.yml` cho ba dependency local: `postgres`, `minio` và `redis`. PostgreSQL và MinIO dùng named volume; Redis OTP dùng `tmpfs` cho `/data` và tắt persistence. Backend Dockerfile/full Compose wiring được để dành cho M14. Compose configuration và readiness/restart smoke đã pass với Docker Desktop server `29.8.0`; PostgreSQL marker giữ được qua restart, MinIO readiness trở lại HTTP 200 và Redis key mất sau restart.

### Trạng thái M2 đã xác minh

M2 đã thêm ba Flyway migrations làm schema source-of-truth và xác minh chúng từ database rỗng trên PostgreSQL 17 Testcontainer. Flyway áp dụng V1–V3 thành công; constraint/index PostgreSQL-specific đã được kiểm tra bằng live catalog và Hibernate khởi động với `ddl-auto=validate`. Đây chưa phải backend Compose runtime: migration chưa được chạy qua backend vào `postgres_data` local, việc đó thuộc M14.

### Trạng thái AI v1 M2 đã xác minh

AI v1 M2 đã thêm Flyway V4 `V4__create_ai_persistence_schema.sql` sau Core V1–V3. V4 tạo pgvector extension, tám AI tables, composite project/document FK, cascade/`SET NULL` lifecycle, status checks, relational indexes, HNSW cosine indexes và partial unique active-generation guard. Fresh apply và Core V1–V3 upgrade path đều pass trên pgvector PostgreSQL 17.11 Testcontainer; Hibernate `ddl-auto=validate`, vector repository SQL isolation, quota lock và delete/status guards đều có integration evidence. Compose topology không đổi; clean Compose runtime với V4 được dành cho AI runtime milestone/final verification.

### Trạng thái M10 đã xác minh

M10 đã bổ sung storage adapter nhưng không thay đổi Docker topology. Local profile dùng `KBASE_STORAGE_AUTO_CREATE=true` và `KBASE_STORAGE_INITIALIZE_ON_STARTUP=true` để validate/tạo bucket cấu hình khi cần; base/production mặc định là `false`, yêu cầu bucket private được pre-provision. Runtime không thay bucket policy, versioning, retention hay object lock; Core v1 giữ hard-delete semantics với bucket unversioned. `KBASE_STORAGE_ENDPOINT`, access key, secret key, bucket, region và connect/write/read timeout đều externalized; access key/secret không được hard-code. Binary local vẫn nằm tại MinIO `/data` mount từ named volume `minio_data`, đã được giữ nguyên và static-verified; M10 MinIO Testcontainer xác minh stream/range/stat/delete nhưng full backend Compose runtime vẫn thuộc M14.

### Trạng thái M14 đã xác minh

M14 đã hoàn tất ngày `2026-09-18` và verify full topology trong Docker runtime thật:

- `Dockerfile` multi-stage: build với `maven:3.9-eclipse-temurin-21` (skip tests — tests chạy qua Maven/Testcontainers pipeline), runtime `eclipse-temurin:21-jre`, user non-root `kbase`, jar repackaged, entrypoint `java -XX:MaxRAMPercentage=75.0`; backend container stateless, mọi credential qua environment.
- `docker-compose.yml` đủ `backend`/`postgres`/`minio`/`redis`: backend dùng service name (`postgres:5432`, `redis:6379`, `http://minio:9000`), startup dependency bằng healthcheck (`pg_isready`, `redis-cli ping`, MinIO `minio/health/live`) qua `depends_on: service_healthy`, không dùng sleep; `postgres_data` và `minio_data` giữ nguyên; Redis vẫn tmpfs ephemeral.
- Secrets không hard-code: compose dùng `${VAR:?required}`; giá trị local nằm trong `.env` git-ignored; `.env.example` liệt kê tên biến.
- Flyway migrate từ database rỗng trong container; Hibernate validate pass; Flyway history persist trong `postgres_data` qua backend restart.
- Persistence đã chứng minh: data sống qua backend restart và force-recreate postgres/minio; Redis recreation làm mất pending OTP (resend vẫn hoạt động) đúng thiết kế.
- Gmail SMTP không bị Dockerize; `docker-compose.mail-test.yml` là optional mail double (mailpit) chỉ cho verification tự động, không thuộc designed topology.
- Log runtime không chứa password/JWT/token/OTP/credential; generated-password log của Boot đã loại bằng exclude `UserDetailsServiceAutoConfiguration` (JWT-only, không có in-memory user).
- Rollback application local: `docker compose up -d --build backend` sau khi revert code (volumes giữ nguyên); không có destructive migration trong Core v1 (chỉ 3 migrations create-only).

## Môi trường

Mô tả các môi trường:

- Development.
- Test.
- Staging.
- Production.

Với mỗi môi trường, ghi:

- Mục đích.
- Cách truy cập.
- Nguồn cấu hình.
- Nguồn secret.
- Database hoặc dependency liên quan.
- Người hoặc vai trò có quyền thay đổi.

## Pipeline tiêu chuẩn

Pipeline nên bao gồm:

1. Cài dependency.
2. Format, lint và type-check.
3. Build.
4. Unit test.
5. Integration hoặc contract test.
6. Security scan.
7. Tạo artifact.
8. Chạy migration theo chính sách.
9. Deploy.
10. Health check.
11. Smoke test.
12. Theo dõi sau triển khai.

Điều chỉnh theo dự án nhưng không bỏ verification mà không ghi lý do.

## Artifact và version

- Artifact phải gắn với commit hoặc version cụ thể.
- Ưu tiên dùng cùng một artifact qua các môi trường.
- Không đóng cứng cấu hình môi trường trong source code.
- Secret phải lấy từ cơ chế quản lý secret được phê duyệt.
- Có thể xác định chính xác phiên bản đang chạy.

## Database migration

- Migration phải tuân theo `docs/DATABASE.md`.
- Thay đổi rủi ro phải có backup hoặc recovery plan.
- Ưu tiên migration tương thích ngược.
- Có thể tách deploy code và cleanup schema thành nhiều giai đoạn.
- Không tự động chạy thao tác phá hủy nếu chưa được phê duyệt.

## Rollback

Mô tả:

- Điều kiện kích hoạt rollback.
- Cách rollback application.
- Cách xử lý migration không thể rollback.
- Cách khôi phục cấu hình.
- Cách xác nhận hệ thống đã hồi phục.
- Ai có quyền thực hiện.

## Post-deploy verification

- Health check.
- Smoke test.
- Golden journey quan trọng.
- Error rate.
- Log.
- Metrics.
- Alert.
- Compatibility với client và integration.

## Giới hạn đối với AI Agent

AI agent không được tự:

- Deploy production.
- Thay secret.
- Chạy destructive migration.
- Xóa dữ liệu.
- Thay quyền truy cập.
- Rollback production.

Các hành động trên chỉ được thực hiện khi có yêu cầu và phê duyệt rõ ràng.


## AI v1 Deployment Target (M7 Project Assistant runtime)

AI v1 không thêm microservice/broker trong initial phase.

Target topology vẫn một backend nhưng PostgreSQL runtime phải có pgvector extension capability:

```text
backend
pgvector-enabled PostgreSQL 17
redis
minio
Gmail SMTP external
Gemini external
```

M0/M1 phải khóa exact PostgreSQL+pgvector image và Spring AI/Gemini dependencies trước thay Docker runtime.

New secret/config family:

- `KBASE_AI_GEMINI_API_KEY`;
- chat/embedding model names;
- embedding dimensions;
- provider timeouts;
- worker/retrieval/rate tuning.

Không commit secret.

M4 deployment/configuration rules:

- `kbase.ai.enabled` mặc định `false`; Core startup không cần `KBASE_AI_GEMINI_API_KEY` và không tạo Gemini provider bean.
- Khi bật AI, API key/model/dimensions/timeouts đi qua typed `kbase.ai` configuration; key blank/missing fail safe as provider configuration error.
- `kbase.ai.provider.request-timeout` được áp dụng tại Google GenAI `HttpOptions`; Google GenAI 1.65.0 không có independent connect-timeout API trên selected client path, nên `connect-timeout` chưa được claim là active và được theo dõi cho future transport customization.
- M4 automated verification dùng synthetic key/test doubles; không có real Gemini connectivity smoke và không yêu cầu public provider network.
- M4 không đổi Compose topology, Flyway schema, generated DB/API docs hoặc public API. Provider availability không được làm Core startup fail khi AI disabled.

Guide accepted product specs phải được package reproducibly vào backend artifact từ canonical `docs/product-specs` files; runtime không phụ thuộc GitHub network.

AI rollback phải ưu tiên disable AI/provider path và giữ Core healthy. Additive AI Flyway schema không được destructive-drop tự động khi rollback application. M4 ban đầu chỉ thêm provider boundary; indexing/retrieval/conversation được triển khai ở các milestone M5–M7 sau đó.

M7 thêm private Project Assistant và document index REST endpoints; M8 thêm provider-independent retention purge; M9 thêm stateless Guide endpoint và packaged Guide corpus without a new migration, service, volume, secret family or broker. M10 thêm Redis-backed interactive usage guard, threshold default, bounded telemetry và API hardening without a topology or schema change. Flyway remains V1–V4. `kbase.ai.enabled=false` keeps Core startup independent of a real Gemini key; when AI is enabled outside tests, provider configuration needs environment credentials. M10 automated gates use deterministic fakes and do not verify public Gemini connectivity. M11 has since completed the full runtime verification and **AI v1 backend is FROZEN (2026-09-26)** using the explicit verification-only deterministic provider; real Gemini connectivity remains intentionally not exercised.

### M10 deployment and rollback rules

- `KBASE_AI_USAGE_RATE_NAMESPACE` defaults to `kbase:ai:rate`, `KBASE_AI_USAGE_MAX_REQUESTS` to `20`, and `KBASE_AI_USAGE_WINDOW` to `1m`. `KBASE_AI_RETRIEVAL_SIMILARITY_THRESHOLD` defaults to `0.70`; a deliberately blank typed override remains a fail-closed test configuration.
- The usage guard uses the existing Redis service and ephemeral state only. It adds no Redis volume, database table, Flyway migration or durable rate mirror. Redis failure fails only guarded interactive AI calls with `AI_USAGE_GUARD_UNAVAILABLE`/503; Core and durable PostgreSQL state remain independent.
- `docker-compose.yml` passes the M10 configuration through without adding a service. `docker compose -f docker-compose.yml config --quiet` passes. No real Gemini key is required for the M10 gate and no public Gemini network is called.
- Rollback can disable `KBASE_AI_ENABLED` or revert the application while preserving Core, PostgreSQL V1–V4, MinIO data and other Redis OTP behavior. Do not delete persistent volumes or run destructive migrations as a rate-guard rollback.
- M11 verified a fresh AI-enabled Docker startup, deterministic AI golden journeys, restart/recovery and operational consistency, and completed the AI v1 freeze on 2026-09-26 (verification matrix: security 37/37, retention, restart/recovery, failure isolation, 0-hit log audit; final Maven gate 377/377).
- M11 added `docker-compose.ai-runtime-test.yml` for an explicit local verification-only provider path. It pins pgvector and requires the runtime-test profile, deterministic mode and acknowledgement; the failure companions (`docker-compose.ai-runtime-failure.yml`, `docker-compose.ai-runtime-chat-failure.yml`, `docker-compose.ai-runtime-embedding-failure.yml`) plus the race/rate/worker-paused overrides induce only safe verification states. None of these overrides is a deployment configuration or a real Gemini connectivity claim. Production defaults remain `KBASE_AI_ENABLED=false` with provider mode `gemini`.

### Profile fail-safe (post-freeze audit 2026-09-26)

- `spring.profiles.default` đã bị bỏ khỏi `application.yml`: artifact chạy mà không đặt `SPRING_PROFILES_ACTIVE` không còn âm thầm kế thừa behavior local (refresh cookie `Secure=false`, storage auto-create/initialize) — cấu hình bắt buộc thiếu sẽ fail-fast.
- `docker-compose.yml` chọn `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}` một cách tường minh cho topology local; deployment production phải set `SPRING_PROFILES_ACTIVE=prod` (kèm toàn bộ biến prod) vì không còn default profile nào.
- Local host run dùng `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run` (xem `DEVELOPMENT.md`).
- Bằng chứng: `ProfileFailSafeTest` (no-profile → `Secure=true`/auto-create `false`; local → đúng behavior local; compose explicit), smoke `kbase-audit` Compose start với profile `local` active + Flyway V1–V4 + 18 tables, và `kbase-m11fix` rebuild với profile `runtime-test` vẫn khởi động deterministic provider.
- OpenAPI/Swagger cũng fail-closed: base `kbase.openapi.*` mặc định `false` nên artifact no-profile không expose `/v3/api-docs` hay Swagger UI (SecurityConfig chỉ permit docs paths khi enabled); `local`, `test` và `runtime-test` enable tường minh, `prod` giữ off trừ khi operator override.
