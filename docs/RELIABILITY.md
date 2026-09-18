# RELIABILITY.md

Tệp này định nghĩa cách hệ thống chứng minh nó khỏe mạnh và có thể khởi động lại.

## Đường dẫn Chuẩn

Các command chuẩn được định nghĩa và duy trì trong `docs/DEVELOPMENT.md`:

- bootstrap và cài đặt môi trường
- khởi động application hoặc service
- baseline verification
- debug và kiểm tra runtime
- reset môi trường local

Tài liệu này định nghĩa các yêu cầu về độ tin cậy và bằng chứng runtime; không sao chép command từ `docs/DEVELOPMENT.md`.

## Tín hiệu Runtime Bắt buộc

- log có cấu trúc cho khởi động và các luồng quan trọng
- `X-Request-Id`/request ID cho lỗi và luồng cần chẩn đoán
- health/readiness signal cho backend và dependency chính khi implementation hỗ trợ
- khả năng xác minh PostgreSQL, Redis và MinIO connectivity mà không log secret
- lỗi Gmail SMTP, Redis OTP và MinIO được map thành stable KBase error thay vì provider exception
- dữ liệu trace hoặc timing cho các đường dẫn chậm khi có sẵn
- trạng thái lỗi có thể nhìn thấy của API caller cho các thất bại có thể phục hồi

## Journey Vàng

- `Đăng ký → OTP được lưu trong Redis và gửi qua Gmail → verify email → login → refresh → logout`
- `OWNER tạo project → gửi invitation qua Gmail → user đã verify/login accept invitation → trở thành MEMBER`
- `MEMBER upload document → metadata PostgreSQL + binary MinIO → project member khác view/download → uploader/OWNER thực hiện mutation đúng quyền`
- `OWNER remove MEMBER → document của user vẫn tồn tại → former MEMBER mất toàn bộ project/document access`
- `OWNER hard-delete project → MinIO objects được xử lý theo design → PostgreSQL project-related data bị xóa theo rule`

- Mỗi journey vàng nên có đường dẫn xác minh có thể lặp lại và tín hiệu thất bại rõ ràng.
- Việc kiểm thử journey vàng phải tuân theo `docs/TESTING.md`.
- Journey liên quan nhiều hệ thống phải tuân theo `docs/INTEGRATION.md`.
- Nếu journey phụ thuộc API, endpoint và schema liên quan phải tồn tại và còn chính xác trong `docs/generated/api-schema.md` sau khi OpenAPI được triển khai.
- Kết quả verification làm thay đổi trạng thái tổng thể phải được phản ánh trong `docs/CURRENT_STATE.md`.

## Quy tắc Độ tin cậy

- Không có tính năng nào hoàn thành nếu backend không thể khởi động lại sạch sẽ sau đó.
- Recreate PostgreSQL/MinIO container không được làm mất dữ liệu khi named volume còn tồn tại.
- Redis OTP là ephemeral: restart Redis có thể làm mất pending OTP nhưng không được làm mất persistent user verification state hoặc refresh session.
- Các thất bại runtime nên có thể chẩn đoán từ các tín hiệu cục bộ repo.
- Nếu một chế độ thất bại lặp đi lặp lại xuất hiện, hãy thêm benchmark hoặc guardrail cho nó.
- Dọn dẹp và compensation là một phần của độ tin cậy, đặc biệt ở PostgreSQL ↔ MinIO và Redis/Gmail verification flow.
- Timeout, retry, idempotency và failure behavior giữa các hệ thống phải được định nghĩa trong `docs/INTEGRATION.md`.
- Lỗi runtime, blocker hoặc trạng thái không ổn định có ảnh hưởng đáng kể phải được ghi trong `docs/CURRENT_STATE.md`.
- `docs/CURRENT_STATE.md` không thay thế log, health check, test result hoặc runtime evidence.
- API schema lỗi thời được xem là một khoảng trống về độ tin cậy và khả năng chẩn đoán.
- Các thay đổi ảnh hưởng runtime production phải được đối chiếu với `docs/DEPLOYMENT.md`.

## Trạng thái M5 đã xác minh

- `RedisOtpStore` dùng Redis container, state/cooldown đều có TTL và Redis local Compose dùng `/data` `tmpfs` với persistence tắt; restart/recreate có thể làm mất pending OTP đúng policy ephemeral.
- Redis failures được dịch thành `OTP_SERVICE_UNAVAILABLE`; SMTP failures được dịch thành `EMAIL_SERVICE_UNAVAILABLE` trước khi tới API handler.
- SMTP timeout là configurable (`kbase.mail.timeout`, mặc định 10 giây); không retry tự động operation gửi mail không-idempotent.
- Redis Testcontainer 6/6 và fake SMTP/unit suite 5/5 đã pass; không gọi Gmail thật, không log raw OTP/invitation token/credential.
- Golden journey `registration → verify → login` vẫn chưa chạy vì registration/login thuộc M6.
