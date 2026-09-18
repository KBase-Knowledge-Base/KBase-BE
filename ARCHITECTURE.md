# ARCHITECTURE.md

Tệp này là bản đồ cấp cao nhất của hệ thống. Nó nên ngắn gọn và trỏ đến các tài liệu sâu hơn khi cần.

## Hình dạng Hệ thống

- Sản phẩm: `KBase – Knowledge Base`
- Workflow người dùng chính: `Đăng ký → xác minh email bằng OTP → đăng nhập → tạo/tham gia project → tổ chức và quản lý tài liệu project → tìm kiếm metadata → preview/download`
- Bề mặt runtime hiện tại: `Spring Boot REST backend + PostgreSQL + Redis + MinIO + Gmail SMTP`
- Frontend: `Optional theo đề bài và được hoãn khỏi phase implementation hiện tại`
- Nguồn sự thật cho hành vi sản phẩm: `docs/product-specs/KBase - Core v1 Specification.md`

## Bản đồ Domain

| Domain | Mục đích | Điểm đầu vào chính | Spec liên quan |
|--------|---------|----------------------|----------------|
| Authentication & Email Verification | Đăng ký, OTP verify email, login, refresh, logout | `/api/v1/auth/**`, `auth`, `security`, `redis`, `mail` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md` |
| User | Profile hiện tại và quản trị user cấp hệ thống | `/api/v1/users/**`, `/api/v1/admin/users/**`, `user` | `docs/design-docs/KBase - Core v1 REST API Specification.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |
| Project & Membership | Project boundary, OWNER/MEMBER và membership lifecycle | `/api/v1/projects/**`, `project` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md` |
| Invitation | Mời thành viên bằng email và invitation token | `/api/v1/projects/{projectId}/invitations/**`, `/api/v1/invitations/accept`, `invitation`, `mail` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |
| Knowledge Organization | Folder, category và tag trong project | `/api/v1/projects/{projectId}/folders|categories|tags`, `folder`, `category`, `tag` | `docs/design-docs/KBase - Core v1 REST API Specification.md`, `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md` |
| Document & Search | Upload, metadata, preview, download, hard delete và metadata search | `/api/v1/projects/{projectId}/documents`, `/api/v1/documents/**`, `document`, `storage` | `docs/design-docs/KBase - Core v1 MinIO Integration Design.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |

## Mô hình Lớp

Sử dụng mô hình định hướng cố định để agent không tự phát minh ra kiến trúc ad hoc:

`Controller -> Service/Application -> Repository`

Các integration đi qua port/adapter rõ ràng:

`Service/Application -> Port (StorageService / MailService / OtpStore) -> Adapter (MinIO / Gmail SMTP / Redis)`

Security filter chịu trách nhiệm authentication cấp hệ thống. Project role và document ownership authorization nằm trong service/authorization component, không được đẩy vào JWT filter.

Frontend không thuộc phase implementation hiện tại; không tạo UI hoặc client chỉ vì `docs/FRONTEND.md` tồn tại.

## Quy tắc Phụ thuộc Cứng

- Controller không truy cập Repository trực tiếp.
- Controller không truy cập Redis, MinIO hoặc Gmail SMTP trực tiếp.
- Service/Application sở hữu business rule, transaction boundary và orchestration.
- Truy cập PostgreSQL đi qua Repository.
- `AuthService` / `EmailVerificationService` sử dụng `OtpStore` và `MailService`, không phụ thuộc Redis client hoặc Gmail implementation trực tiếp.
- Document flow sử dụng `StorageService`, không phụ thuộc MinIO SDK trực tiếp.
- JWT không chứa OWNER/MEMBER; project role được đọc từ `ProjectMember` khi authorize.
- `Project` không có `owner_id`; OWNER nằm trong `ProjectMember.role`.
- `Document.uploadedBy` tham chiếu `User`, không tham chiếu `ProjectMember`.
- JPA Entity không được trả trực tiếp qua REST API.
- Flyway là nguồn sự thật schema; Hibernate dùng `ddl-auto=validate` theo thiết kế Core v1.
- Các tiện ích dùng chung phải là chung chung và không được tích lũy logic domain.
- Các phụ thuộc mới nên được chứng minh trong kế hoạch hoặc tài liệu thiết kế phù hợp.

## Giao diện Xuyên suốt

| Mối quan tâm | Ranh giới được phê duyệt | Ghi chú |
|---|---|---|
| Logging và tracing | `shared` + request ID/MDC | Log có cấu trúc; không log secret, raw token hoặc raw OTP |
| Auth | `security` + `auth` | JWT access token; refresh-session hash trong PostgreSQL; email verification OTP trong Redis |
| OTP Store | `OtpStore -> RedisOtpStore` | OTP verification only; short-lived; Redis không phải durable business store |
| Email | `MailService -> SmtpMailService` | Gmail SMTP cho OTP và invitation; credential từ environment |
| Object Storage | `StorageService -> MinioStorageService` | Private bucket; binary ở MinIO; metadata ở PostgreSQL |
| API contract | `docs/API_CONVENTIONS.md` và `docs/generated/api-schema.md` | Quy ước thiết kế API và contract hiện tại |
| Database | `docs/DATABASE.md` và `docs/generated/db-schema.md` | PostgreSQL + Flyway; 10 persistent tables |
| Cross-system integration | `docs/INTEGRATION.md` | PostgreSQL, Redis, MinIO, Gmail SMTP |
| Testing | `docs/TESTING.md` | Unit + PostgreSQL/Redis/MinIO Testcontainers + API/security contract |
| Deployment | `docs/DEPLOYMENT.md` | Backend-first; local Docker persistence và runtime dependency |

## Điểm Nóng Hiện tại

- Authorization theo project/document ownership và chống cross-project data leakage.
- Consistency giữa PostgreSQL và MinIO khi upload/delete vì không có distributed transaction.
- Registration phụ thuộc Redis OTP + Gmail SMTP trong khi persistent verification state nằm ở PostgreSQL.
- Hard delete project/document phải giữ đúng rule storage/database đã chốt.

## Danh sách Kiểm tra Thay đổi

Khi bạn chạm vào mã liên quan đến kiến trúc:

1. Cập nhật tệp này nếu bản đồ domain hoặc ranh giới được phép thay đổi.
2. Cập nhật tài liệu thiết kế liên quan trong `docs/design-docs/` nếu lý luận thay đổi.
3. Thêm hoặc cập nhật kiểm tra có thể thực thi nếu quy tắc nên được thực thi cơ học.
4. Cập nhật `docs/API_CONVENTIONS.md` nếu quy ước thiết kế API thay đổi; cập nhật hoặc sinh lại `docs/generated/api-schema.md` nếu API contract thực tế thay đổi.
5. Cập nhật `docs/DATABASE.md` và sinh lại `docs/generated/db-schema.md` nếu cấu trúc dữ liệu thay đổi.
6. Cập nhật `docs/INTEGRATION.md` nếu boundary hoặc external integration thay đổi.
7. Cập nhật `docs/TESTING.md` nếu thay đổi yêu cầu một loại verification mới.
8. Cập nhật `docs/DEPLOYMENT.md` nếu thay đổi ảnh hưởng build, migration, rollout hoặc rollback.
