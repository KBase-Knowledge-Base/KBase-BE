# Hướng dẫn Database

## Mục đích

Tài liệu này quy định cách thiết kế, thay đổi, kiểm chứng và tài liệu hóa database.

## Nguồn sự thật

- Schema thực tế và migration là nguồn sự thật.
- `docs/generated/db-schema.md` là tài liệu được sinh từ nguồn sự thật.
- Không chỉnh sửa file generated để thay đổi database.


## Baseline KBase Core v1

- Database chính: PostgreSQL.
- Schema được quản lý bằng Flyway migration trong backend; Hibernate/JPA dùng `ddl-auto=validate`, không dùng `update` làm nguồn sự thật.
- Core v1 có 10 persistent tables: `users`, `refresh_sessions`, `projects`, `project_members`, `project_invitations`, `folders`, `categories`, `tags`, `documents`, `document_tags`.
- `users.email_verified_at` lưu kết quả verification bền vững.
- Không tạo bảng/entity OTP; OTP verification state ngắn hạn nằm trong Redis qua `OtpStore`.
- Refresh session vẫn ở PostgreSQL; Redis không thay `refresh_sessions`.
- Local Docker PostgreSQL dùng named volume `postgres_data`; dữ liệu không phụ thuộc filesystem của backend container.
- Các constraint quan trọng như single OWNER, unique membership, pending invitation uniqueness và same-project folder/category/tag integrity phải được bảo vệ ở database theo Physical Database Design.

Nguồn thiết kế chi tiết:

- `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`

## Thay đổi schema

- Mọi thay đổi schema phải đi qua migration có version.
- Không sửa migration đã được áp dụng ở môi trường dùng chung, trừ khi dự án có quy trình cho phép rõ ràng.
- Thay đổi phá vỡ tương thích phải có kế hoạch chuyển tiếp.
- Migration dữ liệu lớn phải đánh giá thời gian chạy, locking và ảnh hưởng vận hành.
- Không dựa vào cơ chế tự động cập nhật schema ở production.
- Mọi thay đổi có rủi ro phải có kế hoạch backup hoặc phục hồi.

## Quy tắc thiết kế

- Tên bảng, cột, constraint và index phải nhất quán.
- Mỗi bảng phải có primary key rõ ràng.
- Foreign key phải phản ánh quan hệ dữ liệu thực tế.
- Invariant quan trọng nên được bảo vệ bằng constraint khi phù hợp.
- Unique constraint phải được dùng khi tính duy nhất là yêu cầu nghiệp vụ.
- Index phải dựa trên query thực tế.
- Không tạo index dư thừa hoặc trùng lặp.
- Kiểu dữ liệu phải phản ánh đúng ý nghĩa của dữ liệu.
- Không lưu dữ liệu dẫn xuất nếu chưa có chiến lược đồng bộ.
- Soft delete chỉ được dùng khi có yêu cầu rõ ràng.
- Audit field phải dùng nhất quán nếu dự án yêu cầu.

## Transaction và concurrency

- Các thao tác nhiều bước phải xác định transaction boundary.
- Cần đánh giá lost update, duplicate record và race condition.
- Chọn isolation hoặc locking phù hợp với rủi ro thực tế.
- Không giữ transaction lâu hơn cần thiết.
- Tác vụ có thể chạy lại phải được thiết kế an toàn khi phù hợp.

## Dữ liệu nhạy cảm

- Không lưu secret dạng rõ.
- Dữ liệu nhạy cảm phải được hạn chế quyền truy cập.
- Không đưa dữ liệu production thật vào seed hoặc test fixture.
- Log không được chứa dữ liệu nhạy cảm ngoài phạm vi cho phép.

## Seed và test data

- Phân biệt dữ liệu development, test và production.
- Seed script nên có thể chạy lặp an toàn nếu được thiết kế cho mục đích đó.
- Test phải tự tạo và dọn dữ liệu cần thiết.
- Không phụ thuộc vào dữ liệu tồn tại thủ công trên máy một thành viên.

## Verification

Đối với thay đổi database:

- Chạy migration trên database mới.
- Kiểm tra nâng cấp từ phiên bản schema trước.
- Kiểm tra constraint, foreign key và index.
- Chạy integration test liên quan.
- Kiểm tra rollback hoặc recovery plan nếu cần.
- Sinh lại `docs/generated/db-schema.md`.


## AI v1 – M2 Persistence Schema (Đã triển khai)

AI v1 đã mở rộng PostgreSQL bằng Flyway V4 trên pgvector-compatible PostgreSQL 17. Migration V4 là nguồn sự thật cho tám bảng AI; Core migrations V1–V3 không bị sửa.

Source design:

- `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`
- `src/main/resources/db/migration/V4__create_ai_persistence_schema.sql`

Đã triển khai và xác minh:

- `CREATE EXTENSION IF NOT EXISTS vector` và hai cột `vector(768)`.
- `document_ai_indexes` + `document_ai_chunks` với composite document/project FK và cascade khi document bị xóa.
- private `ai_conversations`, `ai_messages`, `ai_message_sources` với retention-friendly user/project FK, message/citation cascade và citation live FK `SET NULL`.
- PostgreSQL-durable `ai_jobs` với job/status vocabulary, JSONB payload và project/document/user delete rules.
- separate `ai_guide_sources` + `ai_guide_chunks`, HNSW cosine indexes và source cascade.
- relational indexes cho project/document/version/status/job/conversation paths.
- partial unique index `uq_ai_messages_active_generation` cho tối đa một assistant generation PROCESSING mỗi conversation.
- `AiConversationQuotaRepository` khóa row `users` trước count-then-insert; `AiVectorRepository` bắt buộc project predicate, READY/active-version predicate và 768 dimensions trong SQL boundary.

Security invariant: project document vector retrieval phải có `project_id` trong SQL. Vector table không được framework auto-create làm production source of truth.

M6 bổ sung `JOIN documents d ON d.id = c.document_id AND d.project_id = c.project_id` vào retrieval SQL để lấy `display_name` hiện tại và loại document không còn tồn tại. `READY`, `active_version` và `project_id` vẫn là SQL predicates; order dùng cosine distance rồi chunk ID để tie-break. Citation snapshots dùng V4 `ai_message_sources` và live FK `ON DELETE SET NULL`; M6 không đổi schema hoặc generated DB snapshot.

Verification:

- `FlywayMigrationIntegrityTest` 13/13: fresh V1–V4, catalog constraints/indexes/HNSW/vector dimensions và Hibernate validate.
- `FlywayAiUpgradeIntegrationTest` 1/1: dữ liệu Core V1–V3 tồn tại sau khi apply V4.
- `AiPersistenceIntegrationTest` 11/11: vector dimension, cross-project SQL trap, active-version filtering, FK/delete semantics, status vocabulary, JPA/JSONB mapping và concurrent quota lock.

`docs/generated/db-schema.md` đã được cập nhật sau live migration/catalog verification ngày `2026-09-22`. `docs/generated/api-schema.md` không đổi vì M2 không thêm endpoint/API contract.

## AI v1 – M3 Durable Jobs và Lifecycle Transactions

M3 không tạo migration mới và không sửa V4. `ai_jobs` của V4 tiếp tục là nguồn sự thật cho claim/lease/retry:

- Claim chỉ lấy job type được handler registry hỗ trợ, lọc `PENDING`/`RETRY` đến hạn hoặc `PROCESSING` hết lease bằng `FOR UPDATE SKIP LOCKED` trong transaction ngắn.
- Mỗi claim ghi lease token riêng vào `locked_by`; completion/retry/failure chỉ thành công khi còn đúng `id`, `PROCESSING` và token hiện tại. Stale job hết attempts chuyển `FAILED`, không retry vô hạn.
- Active dedup dùng `pg_advisory_xact_lock(hashtextextended(dedup_key, 0))` + lookup active state trong transaction; không cần V5 partial unique index cho M3.
- Document/project FK cascade của V4 dọn AI index/chunk/job; membership loss và rejoin dùng enqueue/cancel cùng transaction với `project_members` lifecycle.

Verification: các integration tests M3 chạy trên PostgreSQL 17.11/pgvector Testcontainers và xác nhận delete/race/retention persistence; V4, `docs/generated/db-schema.md` và API snapshot không thay đổi.
