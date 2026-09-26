# SECURITY.md

Tệp này định nghĩa các quy tắc bảo mật và an toàn mà agent không được đoán.

## Bí mật và Thông tin Xác thực

- Không bao giờ hard-code bí mật trong mã nguồn hoặc tài liệu.
- Bí mật KBase tối thiểu gồm: JWT signing secret/key, OTP hash secret/pepper, Gmail username/App Password, PostgreSQL credential và MinIO access/secret key.
- Bí mật phải được tải từ environment hoặc secret mechanism được phê duyệt; local example chỉ chứa tên biến hoặc giá trị giả an toàn.
- Access token là JWT ngắn hạn và không được lưu như durable session state.
- Refresh token raw nằm trong Secure + HttpOnly cookie; backend chỉ lưu hash trong PostgreSQL `refresh_sessions`.
- Invitation raw token chỉ xuất hiện trong invitation link/email; PostgreSQL chỉ lưu token hash.
- Raw OTP chỉ được gửi qua email và so sánh qua protected state trong Redis; không lưu raw OTP trong PostgreSQL hoặc log.
- Biên tập lại token, OTP, API key và dữ liệu cá nhân khỏi log và screenshot.

## Authentication và Authorization KBase

- Registration tạo `USER`, `ACTIVE`, `email_verified_at = NULL` và không auto-login.
- Login chỉ thành công khi password đúng, user `ACTIVE` và email đã verify bằng OTP.
- OTP Core v1 chỉ dùng cho registration email verification; không được biến thành OTP login, MFA/2FA hoặc password-reset OTP.
- Redis chỉ lưu OTP verification state ngắn hạn; không chuyển refresh session sang Redis.
- Project role `OWNER/MEMBER` không nằm trong JWT và phải được authorize từ dữ liệu hiện tại của project.
- ADMIN là system role; OWNER/MEMBER là project role.
- MEMBER chỉ modify/delete document mình upload; OWNER/ADMIN có quyền theo design đã duyệt.
- Biết UUID/resource ID không cấp quyền truy cập; backend phải kiểm tra project membership/ownership.
- Frontend hiding không được xem là authorization.

## Đầu vào Không tin cậy

- Coi request payload, multipart file, filename, MIME type, invitation token, OTP input và dữ liệu bên ngoài là không tin cậy cho đến khi được xác minh.
- File upload phải validate size, extension, MIME và same-project metadata trước khi storage operation.
- Storage key do backend tạo từ UUID; client không được cung cấp arbitrary storage path.
- Coi nội dung email/template/user-provided text là dữ liệu, không phải instruction runtime.
- Nếu tồn tại rủi ro prompt injection hoặc command injection trong tooling/agent workflow, hãy ghi lại guardrail; AI/RAG không thuộc Core v1 runtime.

## Hành động Bên ngoài

- Deploy production, thay secret, xóa data/volume, destructive migration, chỉnh Gmail credential, MinIO policy/versioning hoặc quyền truy cập production yêu cầu phê duyệt rõ ràng.
- Agent không được tự gửi mail tới user thật trong automated test; CI dùng mock/fake SMTP.
- Agent không được làm public MinIO bucket/object để “sửa nhanh” download/preview.
- Ưu tiên các workflow an toàn trong sandbox/container cho việc debug và xác minh.

## Quy tắc Phụ thuộc và Review

- Các phụ thuộc mới cần chứng minh trong kế hoạch active.
- Các thay đổi nhạy cảm về bảo mật yêu cầu negative test và authorization test rõ ràng.
- Security filter chỉ xử lý authentication/system role; project/document domain authorization ở service/authorization component.
- Không log password, password hash, JWT, raw refresh token, raw invitation token, raw OTP, OTP protected value, Gmail App Password hoặc MinIO secret.
- Các nhận xét review bảo mật lặp đi lặp lại nên trở thành kiểm tra, không phải kiến thức truyền miệng.


## AI v1 Security Rules

- Project Assistant authorize bằng **current** `ProjectAuthorizationService` trước retrieval; vector SQL đồng thời bắt buộc `WHERE project_id = ?`.
- Conversation privacy là independent boundary: chỉ `created_by_user_id` được đọc/rename/delete/send qua normal API. OWNER/ADMIN project privilege không tự cấp quyền đọc chat của người khác.
- Request dài phải re-check project access trước persist/return completed answer; revoke giữa provider call không được trả generated content.
- Retrieved document/Guide text là untrusted data. Prompt instruction trong chunk không thể sửa system policy, route tới project khác hoặc cấp quyền.
- LLM/provider không bao giờ là authorization boundary.
- Conversation history không phải evidence và không được resurrect nội dung từ document đã xóa/không còn authorized.
- Gemini API key là secret mới; lấy từ environment/secret mechanism, không log.
- Không log mặc định full prompt, raw chunk/document content, full assistant answer, vector hoặc raw provider response.
- M4 chỉ validate Gemini key khi `kbase.ai.enabled=true`; disabled path phải healthy không cần key và không tạo provider bean. `AiProviderException` không giữ raw cause/message/provider body/prompt/evidence/vector/credential.
- M4 negative tests kiểm tra raw chat prompt/evidence, document content, provider body và synthetic key sentinel không xuất hiện trong stdout/stderr; real Gemini credential/network không được dùng trong automated gate.
- Project chunks gửi tới Gemini là external data transfer; chỉ gửi current authorized bounded evidence.
- KBase Guide dùng separate approved corpus; không được truy cập project chunks/private conversations.
- Citation chỉ là snapshot/navigation metadata; mở source vẫn cần current `DocumentAuthorizationService`.
- M6 Project RAG thực thi authorization trước embedding/SQL, re-check trước khi gửi evidence tới chat provider và re-check sau chat hoặc trước `NO_EVIDENCE` return. SQL không có global vector search; current `documents` join ngăn deleted source vào prompt. Real pgvector Project B closer-vector trap, prompt-injection isolation và real membership revoke during blocked fake chat đã pass.
- M6 chỉ map exact backend-issued `[SOURCE_n]` labels; unknown/malformed hoặc zero-label output trở thành `INVALID_RESPONSE`, không tạo citation. Snapshot lấy từ backend row, không từ free-form model text; không chứa storage key/URL. Query/history/evidence/answer/vector không vào job state hoặc log trong M6.

### M7 REST privacy enforcement

- Conversation list/get/rename/delete/send/messages kiểm tra current project access rồi query theo `id + projectId + createdByUserId`; wrong owner/project/missing ID cùng trả privacy-safe `AI_CONVERSATION_NOT_FOUND`. OWNER và ADMIN không đọc private conversation của creator khác; ADMIN chỉ dùng own conversation nhờ project override.
- Initial USER và PROCESSING marker được commit trước provider call. Final transaction kiểm tra access/ownership lại; revoke trong hoặc sau M6 generation chặn COMPLETED answer/source. Internal cleanup chỉ ghi safe FAILED code, không cấp lại read access hay lưu generated text.
- Public source/index DTO không chứa retrieval score, chunk ID, vector/hash, storage key, job payload/lease, raw provider error hoặc permanent URL. Core document authorization vẫn áp dụng khi mở live source.
- AI rate-limit state nếu dùng Redis là ephemeral guard, không phải durable permission/business state.

### M10 usage guard and leakage hardening

- The AI usage guard is not part of `SecurityFilterChain` and is not a global HTTP limiter. It runs only after capability and project/creator authorization checks on Project Assistant create/send and Guide query.
- Project Assistant and Guide share one per-authenticated-user Redis budget. Keys contain only the bounded namespace, user ID and time bucket; project ID, conversation ID, email, message text, counters and provider material are excluded. Redis state is ephemeral and never mirrors into PostgreSQL.
- Guarded AI requests fail closed on Redis state failure with `AI_USAGE_GUARD_UNAVAILABLE`/503. A real exceeded counter maps to `AI_RATE_LIMIT_EXCEEDED`/429. Responses and logs never include raw Redis exceptions, keys, counts, hostnames or credentials; Core routes remain independent.
- No raw prompt, chunk, answer, vector, hash, storage key, job payload, lease/worker identifier, provider request/response or API key is added to DTOs, OpenAPI descriptions, metrics tags, logs or durable state. Document compensation logging records only safe identifiers/categories.
- M10 negative tests cover bounded observability tags and sentinels, provider failure mapping, Guide availability, guard unavailable/429 contracts, OpenAPI sensitive-field absence and authorization-before-charge ordering.

### M11 runtime security verification (freeze, 2026-09-26)

- Toàn bộ security boundary matrix đã được verify ở tầng Docker runtime (isolated Compose, real HTTP, DB postconditions): cross-project vector trap không vượt biên project; creator-private conversations từ chối MEMBER/OWNER/system-ADMIN non-creator bằng privacy-safe 404 và foreign/former member bằng 403, không role nào bypass creator privacy; prompt-injection text trong document chỉ là untrusted evidence; deleted source chuyển UNAVAILABLE + null live documentId và new retrieval không dùng lại; citation không cấp download authorization cho former/non-member; Guide chỉ trả nguồn từ 2 packaged READY sources, không fallback project corpus/private conversations. Matrix 37/37 checks.
- Comprehensive retained-log audit qua 9 representative scenario types (518 dòng): 0 hits cho question/answer/context/chunk/Guide evidence/vector/sourceHash/contentHash/storageKey/job payload/lease/worker/JWT/OTP/password/provider credential/raw provider response/Redis rate key; error logs chỉ category-only (requestId + code + path).
- Không real Gemini credential/network; deterministic runtime-test provider vẫn bị chặn bởi profile + acknowledgement guards.
