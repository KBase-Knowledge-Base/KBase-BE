# Hướng dẫn Backend

## Mục đích

Tài liệu này quy định cách tổ chức, phát triển và kiểm chứng mã nguồn backend. Các quy tắc cụ thể theo framework phải được bổ sung dựa trên công nghệ thực tế của dự án.

## Phạm vi

Áp dụng cho:

- API và controller.
- Application service hoặc use case.
- Business logic và domain logic.
- Repository và data access.
- Background job.
- Tích hợp dịch vụ bên ngoài.
- Cấu hình backend.


## Baseline KBase Hiện tại

- Core v1 backend đã frozen; phase active hiện tại triển khai backend AI v1 trên baseline đó. Frontend và streaming vẫn deferred.
- Kiến trúc: feature-first modular monolith với root package dự kiến `com.kbase` nếu repository thực tế không quy định package khác.
- Luồng chính: `Controller -> Service/Application -> Repository`.
- Project/document authorization phải được gom vào authorization service/component, không phân tán trong controller.
- External infrastructure đi qua port/adapter: `StorageService -> MinioStorageService`, `MailService -> SmtpMailService`, `OtpStore -> RedisOtpStore`.
- PostgreSQL lưu persistent metadata/session/invitation state; Redis chỉ lưu email-verification OTP state ngắn hạn; MinIO lưu binary file.
- Flyway là schema source of truth; JPA mapping phải validate với schema.
- OAuth, MFA, password reset, public share link và frontend không được tự thêm. AI/RAG chỉ được triển khai theo SD-14..SD-19; Project Chat/OCR/multimodal/XLSX RAG/streaming không thuộc AI v1.

Tài liệu Core nằm trong `docs/design-docs/index.md`; execution lịch sử Core ở `KBase_Core_v1_Implementation_Plan.md`. AI v1 dùng `KBase_AI_Chatbot_v1_Implementation_Plan.md` + active slice trong `docs/exec-plans/active/`.

## Phân chia trách nhiệm

### Controller hoặc API layer

- Tiếp nhận request.
- Xác thực định dạng input.
- Gọi application service hoặc use case.
- Chuyển kết quả thành response.
- Không chứa business logic phức tạp.
- Không truy cập database trực tiếp.

### Application hoặc Service layer

- Điều phối một use case hoàn chỉnh.
- Thực thi business rule.
- Xác định transaction boundary.
- Phối hợp repository và external adapter.
- Không phụ thuộc vào chi tiết giao diện người dùng.

### Domain layer

Nếu dự án có domain layer:

- Chứa business rule cốt lõi.
- Không phụ thuộc vào framework hoặc transport.
- Không truy cập trực tiếp database hoặc dịch vụ bên ngoài.

### Repository hoặc Data access layer

- Chịu trách nhiệm truy cập và lưu trữ dữ liệu.
- Không chứa business rule không liên quan đến persistence.
- Không để query hoặc persistence detail lan sang layer khác.

### Integration hoặc Adapter layer

- Bao bọc dịch vụ bên ngoài sau interface rõ ràng.
- Xử lý timeout, lỗi, retry và mapping dữ liệu.
- Không để model hoặc response của nhà cung cấp lan vào domain.

## Quy tắc chung

- Không trả persistence entity trực tiếp qua API nếu chưa được quy định.
- Dùng request/response model hoặc DTO tại boundary.
- Validation phía backend là bắt buộc.
- Exception phải được xử lý và ánh xạ nhất quán.
- Không bắt exception rồi bỏ qua.
- Không ghi secret, token hoặc dữ liệu nhạy cảm vào log.
- Không thêm dependency mới nếu chưa có lý do rõ ràng.
- Không tạo global mutable state nếu không thật sự cần.
- Operation quan trọng phải xem xét concurrency và idempotency.
- Mọi thay đổi lớn về cấu trúc phải được ghi trong `docs/design-docs/`.

## Error handling

- Lỗi nghiệp vụ, validation, authorization và system error phải được phân biệt.
- Client không được nhận stack trace hoặc implementation detail.
- Error response phải tuân theo `docs/API_CONVENTIONS.md`.
- Log lỗi phải có đủ context để chẩn đoán nhưng không làm lộ dữ liệu nhạy cảm.

## Transaction

- Transaction boundary phải nằm ở layer điều phối use case.
- Không giữ transaction mở trong lúc gọi dịch vụ mạng nếu có thể tránh.
- Các thao tác nhiều bước phải xác định hành vi rollback.
- Cần đánh giá duplicate request và concurrent update.

## Verification

Tùy phạm vi thay đổi, phải chạy:

- Build hoặc compile.
- Static analysis.
- Unit test.
- Integration test.
- Repository test.
- API hoặc contract test.
- Security test cho thay đổi nhạy cảm.
- Performance test cho đường dẫn quan trọng.

Các lệnh cụ thể được khai báo trong `docs/DEVELOPMENT.md`.


## Baseline AI v1 Active

- AI là feature/domain riêng dưới `com.kbase.ai`; không nhét orchestration vào `DocumentService`.
- Provider đi qua KBase-owned `AiChatModel` / `AiEmbeddingModel`; Spring AI/Gemini nằm sau adapter.
- M4 provider implementation nằm trong `com.kbase.ai.provider.springai`: `AiGeminiProviderConfiguration`, `SpringAiGeminiChatAdapter`, `SpringAiGeminiEmbeddingAdapter`, deterministic embedding preparation và error translator; service/domain/application code không import vendor types.
- Gemini configuration được tạo explicit chỉ khi `kbase.ai.enabled=true`; disabled path không yêu cầu key và không tạo provider bean. `kbase.ai.provider.request-timeout` được áp dụng tại Google `HttpOptions`; connect-timeout chỉ là typed future transport setting vì selected SDK không có independent surface.
- Chat adapter chỉ map KBase request/result: system riêng, conversation giữ thứ tự, evidence là untrusted user data riêng và current question ở cuối; adapter không authorize, retrieve, persist hoặc parse citations.
- Embedding adapter sở hữu query/document preparation và từ chối output khác chính xác `768`; không pad, truncate hoặc re-embed.
- Core document binary chỉ đọc qua `StorageService`; AI không import MinIO SDK.
- M5 `DOCUMENT_INDEX` handler đọc source qua `StorageService`, giữ extraction/chunking/provider calls ngoài claim transaction và chỉ ghi staging/activation qua KBase-owned persistence services; Tika types không lan vào application/domain code.
- M5 indexing allowlist là `pdf`, `doc`, `docx`, `ppt`, `pptx`, `md`, `txt`; unsupported Core files kết thúc ở `UNSUPPORTED`, còn parser/provider/storage failures đi qua safe bounded retry/terminal categories.
- Index replacement giữ last-good READY generation cho tới atomic activation; document/project liveness và lease-token predicates chặn stale worker resurrection. Index status/manual retry hiện là application boundary nội bộ, chưa có public controller.
- Project Assistant dùng `ProjectAuthorizationService` hiện tại và private conversation authorization riêng.
- Vector query/repository bắt buộc project-scoped ở SQL.
- Background indexing/retention là PostgreSQL-durable job; `@Async`/in-memory timer không đủ.
- M3 job claiming thuộc `AiJobClaimRepository`/`AiJobStore`: PostgreSQL `FOR UPDATE SKIP LOCKED`, batch bounded, lease token riêng cho từng claim, stale recovery và transition có điều kiện theo `id + PROCESSING + locked_by`.
- `AiJobScheduler` chỉ claim job type có handler trong registry; M3 registry ban đầu rỗng, còn M5 đăng ký `DOCUMENT_INDEX` handler có điều kiện khi AI enabled. `CONVERSATION_PURGE` và job thuộc milestone sau vẫn không được consume. Scheduler chỉ bật khi `kbase.ai.enabled=true`.
- Handler chạy sau transaction claim; outcome dùng taxonomy provider-neutral `SUCCESS`/`RETRY`/`FAILURE`, error state chỉ là safe category code.
- Document upload ghi AI intent/job sau document và tags trong cùng transaction; supported extension là `pdf`, `doc`, `docx`, `ppt`, `pptx`, `md`, `txt`, còn file Core-accepted khác nhận `UNSUPPORTED` không tạo job.
- Member removal/leave enqueue `CONVERSATION_PURGE` theo `+P7D`; invitation rejoin cancel active purge trong cùng transaction. M3 chỉ ghi retention intent, không thực hiện destructive purge.
- Network provider call không giữ DB transaction mở nếu có thể tránh; persist state trước/sau qua transaction ngắn.
- No-evidence là domain outcome; provider error là infrastructure/error outcome.
- AI request phải re-check project access trước khi completed answer được trả/persist.
