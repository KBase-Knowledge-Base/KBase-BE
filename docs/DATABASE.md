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
