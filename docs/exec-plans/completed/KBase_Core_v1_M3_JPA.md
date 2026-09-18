# KBase Core v1 – Active Slice: M3 JPA Entities & Repositories

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M2_PostgreSQL_Flyway.md` (`M2 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Map đúng persistent model Core v1 đã được Flyway xác minh vào JPA/Hibernate và tạo repository/query baseline phục vụ các use case sau này, không đưa Redis OTP state vào PostgreSQL.

## Phạm vi M3

Bao gồm:

- `JPA-01` – Shared enums
- `JPA-02` – User + RefreshSession
- `JPA-03` – Project + ProjectMember
- `JPA-04` – ProjectInvitation
- `JPA-05` – Folder / Category / Tag
- `JPA-06` – Document
- `JPA-07` – DocumentTag + DocumentTagId
- `JPA-08` – Core repositories
- `JPA-09` – Repository queries / projections
- `JPA-10` – Document specification baseline
- `JPA-11` – PostgreSQL repository/mapping integration tests

Không bao gồm:

- M4 shared error/request infrastructure hoặc bất kỳ milestone sau M3.
- Controller, REST/API behavior, service/business orchestration, MinIO, Redis OTP, Gmail SMTP.
- Frontend.
- Schema/migration changes chỉ để làm mapping dễ hơn.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/PLANS.md`
- `docs/CURRENT_STATE.md`
- `docs/QUALITY_SCORE.md`
- `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`
- `docs/DATABASE.md`
- `docs/TESTING.md`
- `docs/INTEGRATION.md`
- `docs/RELIABILITY.md`
- `docs/SECURITY.md`
- `docs/DEPLOYMENT.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 Entity Analysis & ERD.md`
- `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`
- `docs/design-docs/KBase - Core v1 Spring Boot Application Architecture.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `.harness/source-doc-registry.json` (SD-01, SD-02, SD-03, SD-05, SD-06, SD-12, SD-13)

Flyway là schema source of truth; Hibernate chỉ `ddl-auto=validate`. Quan hệ mặc định `LAZY`, không dùng `CascadeType.ALL`, không expose Entity trực tiếp qua REST. `Project` không có `ownerId`; OWNER/MEMBER nằm ở `ProjectMember`; `Document.uploadedBy` trỏ tới `User`; Document–Tag dùng `DocumentTag`; OTP không có JPA entity/repository; `RefreshSession` vẫn persistent trong PostgreSQL.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `JPA-01` | `DONE` | Shared enums đầy đủ, map bằng `EnumType.STRING` |
| `JPA-02` | `DONE` | User map `email_verified_at`; RefreshSession map PostgreSQL; không OTP entity |
| `JPA-03` | `DONE` | Project không owner column; membership map đúng và giữ unique/OWNER DB rules |
| `JPA-04` | `DONE` | Invitation lưu token hash, status/time/email/project/inviter đúng schema |
| `JPA-05` | `DONE` | Folder/category/tag map LAZY và giữ same-project composite associations |
| `JPA-06` | `DONE` | Document map uploader User, optional folder/category composite joins, storage key |
| `JPA-07` | `DONE` | DocumentTag composite key và read-only association views đúng schema |
| `JPA-08` | `DONE` | 10 feature-local repositories; không tạo OtpRepository |
| `JPA-09` | `DONE` | Required queries, lock, fetch graphs, projection và dependency methods |
| `JPA-10` | `DONE` | Dynamic document metadata specification với `projectId` bắt buộc |
| `JPA-11` | `DONE` | PostgreSQL Testcontainer chứng minh validate, query, uniqueness, composite FK và cascade |

## Verification path

Required before closing M3:

- Targeted M3 repository/mapping integration test trên PostgreSQL Testcontainer mới, chạy Flyway từ database rỗng và Hibernate `ddl-auto=validate`.
- Các constraint/behavior: single OWNER, duplicate membership, pending invitation uniqueness, case-insensitive folder/category/tag uniqueness, cross-project parent/document/category/tag integrity, DocumentTag cascade.
- Repository query/projection/specification tests theo `SD-06` và `SD-12`.
- Static boundary review: không có OTP entity/repository, `@ManyToMany`, `CascadeType.ALL`, `Project.ownerId` hoặc frontend change; quan hệ entity đều khai báo `LAZY`.
- `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test` — PASS: 23 tests (M2 + M3 integration), 0 failures, 0 errors, 0 skipped.
- `mvn -B -ntp test` và `mvn -B -ntp clean verify`.

## Generated docs

- `docs/generated/db-schema.md` không thay đổi: M3 chỉ thêm ORM mapping/repository, không thay đổi Flyway schema.
- `docs/generated/api-schema.md` không thay đổi: M3 không tạo controller, endpoint hoặc API contract.

## Blocker / quyết định

- M2 Gate đã PASS; không có blocker M2.
- M0, M1 và M2 Gate đều đã PASS; không còn blocker từ predecessor.
- Archive không có Git metadata (`.git` không tồn tại), nên không có branch/commit history để đối chiếu; đây là giới hạn harness, không chặn M3. Bằng chứng được giữ qua source documents, workspace files và test results.
- Exact UUID generation strategy là implementation choice đang mở trong SD-06; M3 dùng application-side `UUID.randomUUID()` trước khi persist để không thêm DB default/schema coupling.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | M2 predecessor review | M2 Gate PASS được xác nhận; đủ điều kiện bắt đầu M3 |
| 2026-09-17 | M3 preflight | Đọc lại active/master plan, source registry, product/design/testing docs; bắt đầu triển khai |
| 2026-09-17 | `JPA-01..JPA-07` | Tạo 10 entity persistent, `DocumentTagId` và 5 enum; mapping timestamp/enum/LAZY/composite FK theo schema |
| 2026-09-17 | `JPA-08..JPA-10` | Tạo 10 feature-local repository, query/projection/fetch graph/lock và project-scoped document specification |
| 2026-09-17 | `JPA-11` | PostgreSQL Testcontainer chạy Flyway từ DB rỗng, Hibernate validate và 11 mapping/repository tests; tất cả pass |
| 2026-09-17 | M3 verification | `mvn test` 25/25 và `mvn clean verify` 25/25; static scope/boundary review pass |
| 2026-09-17 | M3 closeout | Cập nhật JPA design để ghi rõ Hibernate 7 scalar FK/read-only association refinement; archive plan sau khi gate pass |

## M3 Gate

Status: `PASS` — ngày `2026-09-17`.

- [x] Flyway/JPA mappings agree; Hibernate `ddl-auto=validate` pass sau khi Flyway áp dụng V1–V3 trên PostgreSQL 17 Testcontainer.
- [x] Repository/mapping tests pass trên PostgreSQL Testcontainer: 11/11 targeted M3 tests; full suite 25/25.
- [x] Không có OTP persistence entity/repository; OTP state vẫn ở boundary Redis-only của các milestone sau.
- [x] Persistent relationships follow design; composite project-isolation FKs không bị làm yếu và đã được kiểm tra bằng negative tests.
- [x] Không bắt đầu M4; frontend vẫn deferred.

## Kết quả cuối cùng

M3 đã hoàn thành toàn bộ `JPA-01..JPA-11`: 10 persistent entity, 5 enum, `DocumentTagId`, 10 feature-local repository, projection/query/fetch graph/lock và project-scoped document specification. PostgreSQL Testcontainer chứng minh Flyway schema và JPA mapping tương thích, các constraint PostgreSQL-specific vẫn được bảo vệ và DocumentTag cascade hoạt động. Không có OTP Entity/Repository, không có API/controller hoặc frontend change. M4 chưa bắt đầu.
