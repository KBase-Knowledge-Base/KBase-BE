# ARCHITECTURE.md

Tệp này là bản đồ cấp cao nhất của hệ thống. Nó nên ngắn gọn và trỏ đến các tài liệu sâu hơn khi cần.

## Hình dạng Hệ thống

- Sản phẩm: `KBase – Knowledge Base`
- Workflow Core đã frozen: `Đăng ký → xác minh email bằng OTP → đăng nhập → tạo/tham gia project → tổ chức và quản lý tài liệu project → tìm kiếm metadata → preview/download`
- AI v1 workflow target: `upload supported knowledge → async index → Project Assistant hỏi/đáp có nguồn`; ngoài project có `KBase Guide` grounded trên approved product specs
- Bề mặt runtime hiện tại: `Spring Boot REST backend + PostgreSQL/pgvector + Redis + MinIO + Gmail SMTP`; AI persistence/pgvector V4, Gemini adapters, document indexing, internal Project RAG và M7 private Project Assistant conversation/REST runtime đã được triển khai; Guide runtime thuộc M9
- Frontend: `Optional và vẫn hoãn khỏi AI v1 backend phase`; streaming cũng deferred
- Nguồn sự thật sản phẩm: Core = `docs/product-specs/KBase - Core v1 Specification.md`; AI active = `docs/product-specs/KBase - AI Chatbot v1 Specification.md`

## Bản đồ Domain

| Domain | Mục đích | Điểm đầu vào chính | Spec liên quan |
|--------|---------|----------------------|----------------|
| Authentication & Email Verification | Đăng ký, OTP verify email, login, refresh, logout | `/api/v1/auth/**`, `auth`, `security`, `redis`, `mail` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md` |
| User | Profile hiện tại và quản trị user cấp hệ thống | `/api/v1/users/**`, `/api/v1/admin/users/**`, `user` | `docs/design-docs/KBase - Core v1 REST API Specification.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |
| Project & Membership | Project boundary, OWNER/MEMBER và membership lifecycle | `/api/v1/projects/**`, `project` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md` |
| Invitation | Mời thành viên bằng email và invitation token | `/api/v1/projects/{projectId}/invitations/**`, `/api/v1/invitations/accept`, `invitation`, `mail` | `docs/product-specs/KBase - Core v1 Specification.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |
| Knowledge Organization | Folder, category và tag trong project | `/api/v1/projects/{projectId}/folders|categories|tags`, `folder`, `category`, `tag` | `docs/design-docs/KBase - Core v1 REST API Specification.md`, `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md` |
| Document & Search | Upload, metadata, preview, download, hard delete và metadata search | `/api/v1/projects/{projectId}/documents`, `/api/v1/documents/**`, `document`, `storage` | `docs/design-docs/KBase - Core v1 MinIO Integration Design.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` |
| Project Assistant (AI v1 M7) | Private project-scoped grounded RAG conversations + citations | `/api/v1/projects/{projectId}/ai/**`, `ai` | `docs/product-specs/KBase - AI Chatbot v1 Specification.md`, `docs/design-docs/KBase - AI Chatbot RAG Architecture.md` |
| KBase Guide (AI v1 target) | Grounded product/help assistant from approved KBase product specs; no project data | `/api/v1/ai/guide/**`, `ai.guide` | `docs/product-specs/KBase - AI Chatbot v1 Specification.md`, `docs/design-docs/KBase - AI Chatbot REST API Specification.md` |

## Mô hình Lớp

Sử dụng mô hình định hướng cố định để agent không tự phát minh ra kiến trúc ad hoc:

`Controller -> Service/Application -> Repository`

Các integration đi qua port/adapter rõ ràng:

`Service/Application -> Port (StorageService / MailService / OtpStore) -> Adapter (MinIO / Gmail SMTP / Redis)`

Security filter chịu trách nhiệm authentication cấp hệ thống. Project role và document ownership authorization nằm trong service/authorization component, không được đẩy vào JWT filter.

Frontend không thuộc phase implementation hiện tại; không tạo UI/client/streaming chỉ vì `docs/FRONTEND.md` tồn tại.

AI v1 tiếp tục mô hình port/adapter:

`AI Service -> AiChatModel / AiEmbeddingModel -> Spring AI Gemini adapter -> Gemini`

Vector persistence/query thuộc KBase repository/Flyway schema; không để framework tự sở hữu production vector table. Durable background work dùng PostgreSQL-backed job state; scheduler chỉ poll job, không phải nguồn sự thật.

M6 internal Project RAG đi qua `ProjectRagService -> ProjectEvidenceRetriever / EvidenceSelector / GroundedPromptBuilder / SourceLabelValidator / CitationSnapshotMapper`; SQL retrieval luôn project-scoped, active READY và join current document. M7 bọc pipeline đó bằng `ProjectAssistantConversationService -> ProjectAssistantPersistenceService` với transaction ngắn trước/sau provider và public controllers/DTO; document index REST dùng M5 application service.

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
- AI Project Assistant phải authorize current project membership trước retrieval và re-check trước khi persist/return completed answer.
- Vector retrieval phải filter `project_id` trong SQL; không global-search rồi filter sau.
- Private AI conversation chỉ creator đọc qua normal AI API; ADMIN override không bypass conversation ownership.
- Conversation history không phải authoritative evidence; deleted/currently unauthorized knowledge không được resurrect qua chat history.
- Retrieved content là untrusted data; LLM không phải security boundary.
- Core document upload không gọi Gemini/network indexing synchronously.

## Giao diện Xuyên suốt

| Mối quan tâm | Ranh giới được phê duyệt | Ghi chú |
|---|---|---|
| Logging và tracing | `shared` + request ID/MDC | Log có cấu trúc; không log secret, raw token hoặc raw OTP |
| Auth | `security` + `auth` | JWT access token; refresh-session hash trong PostgreSQL; email verification OTP trong Redis |
| OTP Store | `OtpStore -> RedisOtpStore` | OTP verification only; short-lived; Redis không phải durable business store |
| Email | `MailService -> SmtpMailService` | Gmail SMTP cho OTP và invitation; credential từ environment |
| Object Storage | `StorageService -> MinioStorageService` | Private bucket; binary ở MinIO; metadata ở PostgreSQL |
| API contract | `docs/API_CONVENTIONS.md` và `docs/generated/api-schema.md` | Quy ước thiết kế API và contract hiện tại |
| Database | `docs/DATABASE.md` và `docs/generated/db-schema.md` | PostgreSQL + pgvector + Flyway; 18 persistent tables (10 Core + 8 AI) |
| Cross-system integration | `docs/INTEGRATION.md` | PostgreSQL, Redis, MinIO, Gmail SMTP |
| Testing | `docs/TESTING.md` | Unit + PostgreSQL/Redis/MinIO Testcontainers + API/security contract |
| Deployment | `docs/DEPLOYMENT.md` | Backend-first; local Docker persistence và runtime dependency |
| AI Provider (AI v1 target) | `AiChatModel` / `AiEmbeddingModel` KBase ports | Spring AI primary Gemini adapter; direct SDK only if M0 documents a gap |
| Vector Search (AI v1 target) | KBase AI repository + Flyway-owned pgvector schema | cosine vector(768), mandatory project filter for project corpus |
| Background Jobs (AI v1 target) | PostgreSQL durable job table + worker | indexing, retention purge, Guide reindex; no broker required v1 |

## Điểm Nóng Hiện tại

- Authorization theo project/document ownership và chống cross-project data leakage.
- Consistency giữa PostgreSQL và MinIO khi upload/delete vì không có distributed transaction.
- Registration phụ thuộc Redis OTP + Gmail SMTP trong khi persistent verification state nằm ở PostgreSQL.
- Hard delete project/document phải giữ đúng rule storage/database đã chốt.
- AI cross-project vector leakage, private conversation ownership và in-flight membership revocation.
- PostgreSQL durable job idempotency/restart recovery; indexing không được stuck/resurrect deleted documents.
- Prompt injection trong project documents/Guide corpus và provider data privacy.

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
