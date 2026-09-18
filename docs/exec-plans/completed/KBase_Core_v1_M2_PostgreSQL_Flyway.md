# KBase Core v1 – Active Slice: M2 PostgreSQL / Flyway Schema

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M1_Bootstrap.md` (`M1 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Tạo persistent PostgreSQL schema được phê duyệt và xác minh Flyway trước khi bắt đầu JPA entity/repository implementation.

## Phạm vi M2

Bao gồm:

- `DB-01 – Configure PostgreSQL + Flyway`
- `DB-02 – Implement Core Tables Migration`
- `DB-03 – Implement Constraints / Partial / Expression Indexes`
- `DB-04 – Implement Query Indexes`
- `DB-05 – Migration Integrity Test`

Không bao gồm:

- JPA entity hoặc repository implementation của M3.
- Redis OTP, Gmail SMTP, MinIO adapter, API, frontend hoặc business feature implementation.
- Full Docker runtime verification của M14.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/PLANS.md`
- `docs/CURRENT_STATE.md`
- `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`
- `docs/DATABASE.md`
- `docs/TESTING.md`
- `docs/INTEGRATION.md`
- `docs/DEPLOYMENT.md`
- `docs/generated/db-schema.md`
- `docs/design-docs/KBase - Core v1 Entity Analysis & ERD.md`
- `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `.harness/source-doc-registry.json` (SD-02, SD-03, SD-06, SD-12)

M1 Gate là dependency bắt buộc và đã pass. Flyway là nguồn sự thật schema; Hibernate phải dùng `ddl-auto=validate`. Không tạo OTP table hoặc Redis session table.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `DB-01` | `DONE` | PostgreSQL datasource, Flyway (`classpath:db/migration`) và Hibernate `ddl-auto=validate` đã cấu hình đúng từ M1; được verify bằng context thật chạy Flyway + validate trên Testcontainer, không cần sửa config |
| `DB-02` | `DONE` | `V1__create_core_tables.sql` tạo đúng 10 persistent tables với `users.email_verified_at TIMESTAMPTZ NULL`; không có OTP/Redis session table; UUID không có DB-side default; enum VARCHAR + CHECK |
| `DB-03` | `DONE` | Toàn bộ CHECK/UNIQUE/FK inline theo Physical Design; same-project composite FK (folders parent, documents→folder/category, document_tags→document/tag); `V2__create_unique_partial_and_expression_indexes.sql` tạo 6 partial/expression unique indexes |
| `DB-04` | `DONE` | `V3__create_query_indexes.sql` tạo 13 query indexes cho membership, invitation, documents, uploader, document_tags, refresh sessions |
| `DB-05` | `DONE` | `FlywayMigrationIntegrityTest` (12 tests) chạy trên PostgreSQL 17 Testcontainer mới: Flyway apply từ DB rỗng, Hibernate validate pass, đúng tên constraint/index trên live catalog, và chứng minh hành vi PostgreSQL-specific |

## Generated docs

- `docs/generated/db-schema.md` đã được tái sinh từ migration đã verify và đối chiếu live PostgreSQL catalog ngày `2026-09-17` (chưa có generator tự động).
- `docs/generated/api-schema.md` không bị ảnh hưởng bởi M2.

## Verification path

Command đã chạy ngày `2026-09-17`:

- `mvn -B -ntp clean verify` — PASS: 14 tests (12 M2 + 2 M1), 0 failures, 0 errors, 0 skipped; build success.
- `mvn -B -ntp test` — PASS: 14 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -B -ntp -Dtest=FlywayMigrationIntegrityTest test` — PASS: 12/12 trên PostgreSQL 17 Testcontainer.

Chi tiết verification đã ghi vào `docs/DEVELOPMENT.md` (M2 verification record).

## Rủi ro, blocker và rollback

- Không còn blocker M2. Migration chỉ áp dụng trên database mới trong verification; local Compose PostgreSQL chưa chạy migration thật (backend runtime wiring thuộc M14) — `postgres_data` local vẫn trống.
- Hibernate validate ở M2 có surface trống vì chưa có JPA entity; chính integrity test này sẽ tự động kiểm chứng mapping khi M3 thêm entity.
- Rollback trước Gate: xóa `src/main/resources/db/migration/V1..V3` và `src/test/java/com/kbase/integration/FlywayMigrationIntegrityTest.java`; không sửa generated schema độc lập với source-of-truth.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | M1 Gate pass | Đủ dependency để đăng ký M2; M2 chưa thực thi |
| 2026-09-17 | `DB-01` | Xác nhận datasource + Flyway + `ddl-auto=validate` từ M1 khớp source-of-truth; không cần thay đổi config |
| 2026-09-17 | `DB-02` | Tạo `V1__create_core_tables.sql` — 10 tables + toàn bộ inline constraint theo Physical Design |
| 2026-09-17 | `DB-03` | Tạo `V2__create_unique_partial_and_expression_indexes.sql` — 6 partial/expression unique indexes |
| 2026-09-17 | `DB-04` | Tạo `V3__create_query_indexes.sql` — 13 query indexes |
| 2026-09-17 | `DB-05` | Tạo `FlywayMigrationIntegrityTest`; run đầu: 10/12 pass, 2 assertion của test sai (indexdef text-cast của PostgreSQL, test phụ thuộc thứ tự) — đã sửa test, không sửa schema |
| 2026-09-17 | M2 verification | `clean verify` + `test` + integrity test đều PASS; `docs/generated/db-schema.md` tái sinh; M2 Gate PASS |
| 2026-09-17 | M2 review | Không phát hiện schema/migration defect; test gate được harden để ép profile `local`, Flyway enabled và `ddl-auto=validate`; integrity 12/12 + `clean verify` 14 tests pass; ghi chép stale về migration/Testcontainers đã được sửa |

## M2 Gate

Status: `PASS` — ngày `2026-09-17`.

- [x] Đủ 10 persistent tables tồn tại (`exactlyTheTenPersistentTablesExist`).
- [x] `users.email_verified_at` tồn tại, kiểu `timestamp with time zone`, nullable (`usersEmailVerifiedAtColumnExistsAsNullableTimestamptz`).
- [x] Không có OTP table (`noOtpTableExists`).
- [x] Migration pass trên PostgreSQL database mới (3/3 migration success trong `flyway_schema_history` trên Testcontainer).
- [x] Hibernate validate pass (context khởi động với `ddl-auto=validate` sau Flyway; persistence unit chạy native query thành công).

Bằng chứng bổ sung: 46 constraint name khớp live `pg_constraint` theo đúng bảng; 19 index khớp `pg_indexes`; indexdef xác nhận partial/expression semantics; hành vi thật được chứng minh: duplicate email bị chặn (`uq_users_email`), email chưa normalize bị chặn (`ck_users_email_normalized`), OWNER thứ hai bị chặn nhưng MEMBER được phép (`uq_project_members_single_owner`), gắn tag khác project bị chặn nhưng cùng project được phép (`fk_document_tags_tag_same_project`).

## Kết quả cuối cùng

M2 đã tạo và xác minh persistent PostgreSQL schema Core v1: 3 Flyway migrations, 10 persistent tables, đầy đủ constraint/FK/partial/expression/query index theo Physical Database Design, không có OTP table. Flyway là source of truth schema; Hibernate giữ `ddl-auto=validate`. Frontend vẫn deferred; M3 chưa bắt đầu.
