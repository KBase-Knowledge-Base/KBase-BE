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

## Trạng thái M11 đã xác minh

- `DocumentService` dùng upload streaming qua `StorageService`; DB persistence failure sau upload gọi compensation delete best-effort và log internal IDs/key nếu cleanup cũng lỗi, không trả storage key qua API.
- `DocumentApiIntegrationTest` chạy qua real security filter chain với PostgreSQL + MinIO: upload/batch và reject contract, same-project metadata, MEMBER/OWNER/ADMIN/former-member matrix, stream attachment, Office rejection, MP4 single-range `206`/invalid `416`, và project hard-delete cascade.
- `DocumentController` dùng `InputStreamResource` trực tiếp từ `StorageService`, vì vậy không materialize file lớn trong JVM; download dùng attachment/displayName còn preview dùng inline và `Content-Range` khi MP4 range hợp lệ.
- `mvn -B -ntp test` và `mvn -B -ntp clean verify` đã pass 184 tests; M11 Gate pass.

## Trạng thái M12 đã xác minh

- `DocumentSearchService` authorize bằng `ProjectAuthorizationService.requireProjectAccess` trước khi gọi repository và luôn ghép predicate `document.project.id = projectId`; ADMIN giữ override, non-member nhận `PROJECT_ACCESS_FORBIDDEN`.
- PostgreSQL search dùng metadata fields được duyệt; tag filter và tag-name query dùng `EXISTS`, tránh duplicate result/count khi một document có nhiều tag khớp.
- `PaginationParser` canonicalize sort field từ whitelist trước khi tạo `Sort`, nên raw client path (kể cả `storageKey`) không chạm persistence; baseline page/size và max-size vẫn giữ nguyên.
- `DocumentSearchIntegrationTest` 2/2 và full regression 188/188 pass; không có content extraction, full-text content, vector, semantic, AI hoặc RAG behavior.

## Trạng thái M14 đã xác minh

- Golden journeys đã chạy thật trong Docker runtime (backend container + PostgreSQL/Redis/MinIO containers, mail double cho OTP): registration → Redis OTP state (TTL) → email tại MailService boundary → verify → login → refresh → logout; OWNER create project → invitation email → user thứ hai verify/login → accept → MEMBER; upload → metadata PostgreSQL + binary MinIO → search/download/preview range (206/416) → MEMBER read-only với document của OWNER → remove member → former member mất access nhưng documents remain; document + project hard delete storage-first với bucket về rỗng.
- Persistence boundaries: backend restart giữ data và Flyway history ("No migration necessary"); force-recreate postgres/minio không mất gì nhờ `postgres_data`/`minio_data`; Redis recreation làm mất pending OTP (verify cũ 400 `OTP_EXPIRED`) và resend/verify/login vẫn hoạt động — đúng policy ephemeral.
- Health/readiness: postgres `pg_isready`, redis `redis-cli ping`, MinIO `minio/health/live` gated backend startup qua `depends_on: service_healthy`; không sleep.
- Log runtime sạch (59 dòng/whole-smoke container): không password/JWT/token/OTP/credential; generated-password log của Boot đã loại.
- Fix runtime từ M11: OWNER-path project hard delete fail trên Hibernate 7.4 (`TransientPropertyValueException`); sửa bằng bulk cascade delete + regression test OWNER-path; full suite 210/210.
