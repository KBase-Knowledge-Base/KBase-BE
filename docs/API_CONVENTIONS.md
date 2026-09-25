# Quy ước API

## Mục đích

Tài liệu này định nghĩa chuẩn chung cho API do dự án sở hữu, nhằm giữ contract nhất quán giữa client, backend và các service.

## Tài liệu Contract Hiện tại

File này định nghĩa các quy ước mà API phải tuân theo.

Danh sách endpoint, request, response, error, enum, pagination và kiểu dữ liệu hiện tại được lưu tại:

- `docs/generated/api-schema.md`

Nếu dự án có OpenAPI, GraphQL schema, Protobuf hoặc contract máy đọc được khác, nguồn đó là nguồn sự thật ưu tiên. `docs/generated/api-schema.md` phải được sinh hoặc đồng bộ từ nguồn sự thật đó.

Phân biệt:

- `docs/API_CONVENTIONS.md`: API phải được thiết kế như thế nào.
- `docs/generated/api-schema.md`: API hiện tại đang có những endpoint và schema nào.


## Baseline KBase Core v1

- Base path: `/api/v1`.
- Protected endpoint dùng Bearer JWT access token; refresh token dùng HttpOnly cookie theo Security design.
- Registration email verification dùng public `verify-email`/`resend-verification-otp` endpoints theo REST spec; OTP không phải login credential.
- Pagination baseline: `page=0`, `size=20`, `max size=100`; sort phải dùng whitelist theo từng resource.
- Error response chuẩn gồm: `timestamp`, `status`, `code`, `message`, `path`, `requestId`, và `errors` khi validation cần field-level detail.
- Binary preview/download phải được mô tả là binary response; MP4 preview hỗ trợ single byte-range theo design.
- `storageKey`, password hash, token hash, OTP protected value và persistence-only field không được xuất hiện trong public DTO.
- Từ M13, OpenAPI runtime được sinh bởi springdoc tại `GET /v3/api-docs` và là contract máy đọc được; `docs/generated/api-schema.md` là snapshot Markdown đồng bộ từ runtime. Khi OpenAPI runtime và REST design spec mâu thuẫn, ưu tiên runtime đã được verify bởi contract tests và source code.

## Thiết kế endpoint

- URL phải nhất quán và mô tả tài nguyên hoặc hành động rõ ràng.
- HTTP method phải phản ánh đúng ý nghĩa thao tác.
- Không đưa implementation detail vào URL.
- Quy ước versioning phải được xác định nếu API cần duy trì tương thích lâu dài.
- Hành động không phù hợp CRUD phải được mô tả rõ trong contract.

## Request

- Request phải có schema hoặc model rõ ràng.
- Phân biệt field bắt buộc, tùy chọn và nullable.
- Validation phía server là bắt buộc.
- Không tin role, owner hoặc trạng thái bảo mật do client tự khai báo.
- Định dạng ngày giờ, timezone, số và enum phải thống nhất.
- Upload file phải có giới hạn và validation phù hợp.

## Response

- Response phải có cấu trúc nhất quán.
- Không trả field nội bộ hoặc dữ liệu nhạy cảm không cần thiết.
- Collection phải dùng một quy ước pagination thống nhất.
- Trạng thái trả về phải phản ánh đúng kết quả thực tế.
- Không trả persistence entity trực tiếp nếu chưa được chấp nhận trong kiến trúc.

## Error response

Error response nên cung cấp:

- Mã lỗi ổn định.
- Thông điệp có thể hiểu được.
- HTTP status phù hợp.
- Chi tiết field khi validation lỗi.
- Request hoặc correlation ID nếu hệ thống hỗ trợ.

KBase Core v1 chuẩn hóa thành `ApiErrorResponse` với các field bắt buộc
`timestamp`, `status`, `code`, `message`, `path`, `requestId`. Validation error
có thêm `errors` dạng map field → safe message. Mọi response được gắn
`X-Request-Id`, và giá trị này phải trùng với `requestId` trong error body.

Không trả:

- Stack trace.
- SQL hoặc query nội bộ.
- Secret.
- Thông tin hệ thống không cần thiết.

## Authentication và authorization

- Cơ chế xác thực phải được mô tả trong `docs/SECURITY.md`.
- Authorization phải được kiểm tra phía server.
- Endpoint nhạy cảm phải kiểm tra quyền và ownership khi cần.
- Lỗi authentication và authorization phải dùng status và response nhất quán.

## Idempotency và concurrency

- Operation có tác động khó hoàn tác phải xem xét idempotency.
- Request lặp hoặc retry phải có hành vi xác định.
- Concurrent update phải được xử lý bằng cơ chế phù hợp.
- Idempotency key, version field hoặc locking phải được tài liệu hóa khi sử dụng.

## Contract và compatibility

- OpenAPI hoặc schema tương đương nên là contract có thể kiểm chứng.
- Breaking change phải được nhận diện trước khi triển khai.
- Client và server phải được kiểm tra cùng nhau khi contract thay đổi.
- File contract được sinh nên đặt trong `docs/generated/`.
- Khi endpoint, method, path, request, response, enum, pagination hoặc error schema thay đổi, phải cập nhật hoặc sinh lại `docs/generated/api-schema.md`.
- `docs/generated/api-schema.md` không thay thế các quy tắc thiết kế trong file này.
- Nếu `docs/generated/api-schema.md` mâu thuẫn với contract máy đọc được hoặc source code đã được xác minh, file generated được xem là lỗi thời.
- Breaking change phải được ghi trong execution plan và trong phần breaking changes của `docs/generated/api-schema.md`.
- Endpoint hoặc field deprecated phải có hướng thay thế và thời gian loại bỏ nếu có.


## AI v1 – M9 Runtime Contract Conventions

AI v1 target contract nằm tại `docs/design-docs/KBase - AI Chatbot REST API Specification.md`.

Quy tắc bổ sung:

- Initial AI v1 là JSON request/response, không SSE/streaming.
- Project Assistant routes nằm dưới `/api/v1/projects/{projectId}/ai/**`.
- KBase Guide route không có projectId nhưng vẫn authenticated trong v1.
- Private conversation authorization enforce phía server; ADMIN override không cho đọc chat người khác.
- Assistant outcome phân biệt `GROUNDED` và `NO_EVIDENCE`; `NO_EVIDENCE` là successful domain response, không giả thành 5xx.
- Citation là structured backend source mapping; không parse/trust arbitrary citation do LLM invent.
- AI errors tiếp tục dùng `ApiErrorResponse` + stable `ErrorCode`.
- Không public vector, source hash, storage key, AI job payload/lease hoặc raw provider response.

M7 đã triển khai API-AI-001..009; M9 đã thêm API-AI-010; M10 xác minh exact runtime OpenAPI path/method/tag/security/DTO set và đồng bộ `docs/generated/api-schema.md` tại 38 paths/57 operations/15 tags. Guide query là authenticated và stateless, không nhận `projectId`, chỉ nhận bounded USER/ASSISTANT context, và không tạo conversation/message/source row. M10 bổ sung stable `AI_RATE_LIMIT_EXCEEDED`/429 và `AI_USAGE_GUARD_UNAVAILABLE`/503 cho đúng ba interactive POST operations: Project Assistant create/send và Guide query. Budget dùng chung theo user; conversation reads/rename/delete, document-index retry và background jobs không bị guard. Conversation GET trả metadata, messages dùng route phân trang; `NO_EVIDENCE` là 2xx và provider failure là safe 503 với USER/FAILED marker vẫn có thể đọc sau đó.

## AI v1 – M10 Guard and Contract Rules

- Usage charging occurs only after AI capability availability and the relevant project/creator authorization checks. A rejected request must not create a conversation, USER message or PROCESSING marker.
- `AI_RATE_LIMIT_EXCEEDED` means the configured counter was exceeded; `AI_USAGE_GUARD_UNAVAILABLE` means Redis rate state was unavailable. Neither response includes Redis keys, counts, hostnames or raw exception text.
- The guard is an application boundary, not a servlet filter, security-chain dependency or global `/api/v1/**` limiter. Core endpoints remain available when the AI rate Redis state is unavailable.
- `docs/generated/api-schema.md` follows verified `/v3/api-docs`; API contract changes must update annotations, exact contract tests and the snapshot in one slice. M10 made no database schema change, so `docs/generated/db-schema.md` remains unchanged.
