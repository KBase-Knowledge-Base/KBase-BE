# KBase Core v1 – Active Slice: M1 Project Bootstrap + Local Runtime Skeleton

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M0_Preflight.md` (`M0 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Tạo application foundation và local container topology cho backend Core v1 trước feature implementation.

## Phạm vi

Bao gồm:

- `BOOT-01 – Create / Normalize Spring Boot Project`
- `BOOT-02 – Create Feature-First Package Skeleton`
- `BOOT-03 – Create Typed Configuration Properties`
- `BOOT-04 – Configure Profiles`
- `BOOT-05 – Create Local Infrastructure Compose Skeleton`

Không bao gồm:

- PostgreSQL migration hoặc JPA entity implementation.
- Auth, OTP, Redis adapter, Gmail adapter, MinIO adapter hoặc business feature implementation.
- Frontend implementation.
- M1 đã được thực thi trong phiên được ủy quyền sau khi M0 Gate pass.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/CURRENT_STATE.md`
- `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`
- `docs/INTEGRATION.md`
- `docs/TESTING.md`
- `docs/SECURITY.md`
- `docs/DEPLOYMENT.md`
- `docs/design-docs/KBase - Core v1 Spring Boot Application Architecture.md`
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md`
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md`
- `docs/design-docs/KBase - Core v1 MinIO Integration Design.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `.harness/source-doc-registry.json` (SD-01..SD-13)

M0 technical baseline là source cho dependency selection: Java `21`, Spring Boot `4.1.1`, Hibernate `7.4.5.Final`, Spring Data Redis `4.1.1`, Lettuce `7.5.2.RELEASE`, springdoc `3.1.1`, Testcontainers `2.0.5`, MinIO `9.0.3`, JJWT `0.13.0`, Flyway `12.4.0`, PostgreSQL JDBC `42.7.13` và Tika `3.3.2`.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `BOOT-01` | `DONE` | Maven project dùng baseline đã khóa; app compile, test phase và package artifact đều pass |
| `BOOT-02` | `DONE` | Feature-first package skeleton tồn tại; chưa có business implementation |
| `BOOT-03` | `DONE` | Typed properties bind cho PostgreSQL, JWT, cookie, OTP, Redis, mail, invitation, upload, MinIO, CORS và OpenAPI |
| `BOOT-04` | `DONE` | Local/test/prod profile strategy tồn tại; secrets chỉ lấy từ environment; không có production secret trong source |
| `BOOT-05` | `DONE` | Compose skeleton khai báo postgres/minio/redis; `postgres_data` và `minio_data`; Redis `/data` dùng `tmpfs`, không có durable volume và tắt persistence |

## Ảnh hưởng repository và generated docs

- M1 đã tạo backend source/build/runtime skeleton; không thay đổi product semantics.
- `docs/generated/db-schema.md` không cập nhật ở M1 vì chưa có migration/schema change.
- `docs/generated/api-schema.md` không cập nhật ở M1 vì chưa có controller/API contract runtime.
- `docs/CURRENT_STATE.md` và `docs/DEVELOPMENT.md` phải cập nhật cùng phiên M1 sau khi command thật tồn tại và đã chạy.

## Verification path

Các command product đã được ghi lại trong `docs/DEVELOPMENT.md` sau khi project được tạo:

- `mvn -B -ntp clean verify` — compile, test phase, jar và Spring Boot repackage;
- `mvn -B -ntp test` — test phase;
- `mvn -B -ntp dependency:tree "-DoutputFile=target/dependency-tree.txt" "-DoutputType=text"` — dependency graph;
- `docker compose -f docker-compose.yml config --quiet` — Compose syntax/config check với required local variables được cấp trong process environment;
- `docker compose -f docker-compose.yml up -d postgres minio redis` — live dependency startup với process environment kiểm tra không phải production;
- Compose JSON inspection — xác nhận services và volume boundary;
- Compose readiness/restart smoke — xác nhận PostgreSQL/Redis health, MinIO readiness, PostgreSQL/MinIO named mounts và Redis ephemeral behavior;
- source inspection/secret scan — xác nhận secrets chỉ là `${...}`/empty example placeholders, không có production credential.

`mvn clean verify` và `mvn test` đều pass; dependency tree và Compose config resolve thành công. Initial Compose attempt gặp image registry cũ, sau đó đã sửa sang Quay và live dependency/restart smoke pass. Full backend runtime smoke vẫn thuộc M14.

## Mức kiểm thử yêu cầu

Theo `docs/TESTING.md` và M1 Gate: app compile, test phase chạy, configuration binding được kiểm tra; chưa yêu cầu business/API/integration behavior của các milestone sau.

## Rủi ro, blocker và rollback

- Initial closeout khi Docker daemon chưa chạy được; sau khi daemon bật, live dependency startup và restart smoke đã pass.
- Archive không có Git metadata; phải giữ verification evidence trong plan/state.
- Không có migration trong M1; rollback chỉ cần loại bỏ các bootstrap artifacts do M1 tạo nếu task được dừng trước Gate, theo review của phiên đó.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | Tạo active M1 slice sau M0 Gate | `READY`; chưa thực thi BOOT-01..05 |
| 2026-09-17 | `BOOT-01` | Tạo `pom.xml`, `KBaseApplication` và Spring Boot Maven plugin theo baseline M0 |
| 2026-09-17 | `BOOT-02` | Tạo package markers cho các feature/infrastructure boundaries dưới `com.kbase` |
| 2026-09-17 | `BOOT-03` | Tạo 11 typed configuration-properties classes và property binding test |
| 2026-09-17 | `BOOT-04` | Tạo `application.yml`, local/test/prod profiles, `.env.example` và `.gitignore` secret guard |
| 2026-09-17 | `BOOT-05` | Tạo PostgreSQL/MinIO/Redis Compose skeleton với đúng volume boundary |
| 2026-09-17 | M1 verification | `mvn clean verify`, dependency tree, Compose config và final static checks pass |
| 2026-09-17 | M1 closeout re-verification (static) | `mvn -B -ntp clean verify`, `mvn -B -ntp test`, dependency tree, Compose topology, scope và secret checks đều pass tại checkpoint Docker daemon chưa available |
| 2026-09-17 | M1 live runtime follow-up — first attempt | FAIL — Docker daemon đã chạy nhưng `minio/minio:latest` bị Docker Hub từ chối; cần sửa image registry trước khi retry |
| 2026-09-17 | M1 live runtime follow-up — Redis boundary inspection | FAIL — image Redis tự khai báo anonymous `/data` volume; persistence flags vẫn tắt nhưng cần ép `tmpfs` để bảo đảm OTP state ephemeral |
| 2026-09-17 | M1 live runtime follow-up — registry/tmpfs fixes | PASS — dùng `quay.io/minio/minio:latest`; Redis `/data` là tmpfs; PostgreSQL/MinIO named mounts và Redis restart behavior đã xác minh |
| 2026-09-17 | M1 final re-verification after Docker became available | PASS — Compose readiness/restart smoke, Maven build/test, dependency tree, registry/path, scope và secret checks đều pass; không còn M1 blocker |

## Quyết định mở

Không có quyết định M0 nào mở. Mọi thay đổi dependency hoặc implementation choice phải quay lại source docs, ghi trong plan và không được suy đoán.

## M1 Gate

Status: `PASS` — ngày `2026-09-17`

- [x] App compiles với Java 21 và Spring Boot 4.1.1.
- [x] Test phase chạy thành công: 2 tests, 0 failures, 0 errors, 0 skipped.
- [x] Feature-first structure tồn tại dưới `com.kbase`: auth, user, project, invitation, folder, category, tag, document, security, storage, mail, redis, shared, config.
- [x] Typed configuration properties bind thành công cho toàn bộ nhóm M1.
- [x] Local/test/prod profile strategy tồn tại; test context start được không cần external infrastructure.
- [x] Compose config pass và khai báo `postgres`, `minio`, `redis`.
- [x] `postgres_data` và `minio_data` là named volumes.
- [x] Redis không dùng durable volume; `/data` là tmpfs và chạy với `--save "" --appendonly no`, phù hợp ephemeral OTP state.
- [x] Không có hard-coded production secret/credential; `.env` bị ignore và `.env.example` chỉ có tên/placeholder an toàn.

Live M1 dependency runtime đã xác minh: cả ba service start/restart được; PostgreSQL giữ marker qua restart, MinIO readiness trả HTTP 200, Redis mất key qua restart và `/data` dùng tmpfs. Full backend image/wiring và M14 persistence smoke vẫn chưa thực thi.

## Kết quả cuối cùng

M1 đã tạo backend foundation và local dependency skeleton, đồng thời xác minh live local dependencies. Chưa tạo migration, JPA entity, controller, service, repository, adapter, business feature, API schema, DB schema hoặc frontend. M2 chưa bắt đầu.
