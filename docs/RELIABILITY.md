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

AI v1 target journeys (đã runtime-verified trong M11, 2026-09-26 — xem freeze report):

- `Upload supported document → Core upload thành công → durable indexing → READY → Project Assistant grounded answer + citation` — VERIFIED (isolated Docker runtime)
- `Question không có current evidence → deterministic NO_EVIDENCE, không general model fallback` — VERIFIED (deterministic provider path)
- `Gemini unavailable → AI fails/retries safely → Core upload/download/metadata search vẫn healthy` — VERIFIED ở deterministic unavailable/timeout modes; real Gemini connectivity vẫn intentionally outside automated evidence
- `MEMBER bị remove → AI access deny ngay → rejoin trong 7 ngày restore hoặc quá hạn hard-purge private conversations` — VERIFIED (+P7D schedule, rejoin cancel/quota, runtime purge, in-flight revoke→rejoin race)
- `Delete source document → new retrieval không dùng source → historical citation chuyển unavailable` — VERIFIED
- `Backend restart với pending/stale AI job → durable worker resume/recover` — VERIFIED (pending resume + stale-lease reclaim với lease-token guard)
- `KBase Guide → chỉ approved product-spec corpus → grounded answer/refusal; không project data` — VERIFIED (2 packaged READY sources; project/private sentinels NO_EVIDENCE)

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


## AI v1 Reliability Invariants

- Durable job state ở PostgreSQL; restart JVM không được làm mất index/purge intent.
- `PROCESSING` job phải có lease/stale recovery, không stuck vĩnh viễn.
- Duplicate job/worker delivery phải idempotent; không giả định exactly-once.
- Last active successful document index không bị xóa trước replacement activation.
- Deleted document/project không được resurrect bởi late worker.
- Provider timeout/retry bounded; Core runtime không phụ thuộc Gemini availability.
- AI observability dùng safe metadata (job state/latency/error category), không raw knowledge/prompt.
- M4 provider boundary maps timeout/rate-limit/unavailable/configuration/invalid-response to stable KBase categories; timeout request is wired through Google `HttpOptions`, while the selected SDK's missing independent connect-timeout surface is explicitly tracked rather than simulated.
- AI-disabled startup does not instantiate provider clients/adapters; provider availability is therefore not a Core startup dependency.

### M3 evidence đã xác minh

- PostgreSQL job engine: `AiJobEngineIntegrationTest` 9/9 chứng minh `SKIP LOCKED`, independent worker progress, unique lease token, stale reclaim, bounded attempts, terminal-state exclusion và active dedup.
- Bounded scheduler: registry-supported claim, không consume job không có handler, execution sau claim transaction và provider-neutral outcome mapping. Khi `kbase.ai.enabled=false`, scheduler vẫn chỉ consume `CONVERSATION_PURGE`; `DOCUMENT_INDEX`/provider work không đăng ký.
- Document lifecycle: upload chỉ ghi intent/job metadata trong transaction sau Core document + tags; V4 FK cascade và conditional lease transition chặn late worker resurrection; không đọc MinIO hoặc gọi provider.
- Membership retention: remove/leave enqueue `CONVERSATION_PURGE` tại `+P7D`, rejoin cancel active intent và lấy lifecycle advisory lock trước membership insert; due purge rechecks exact lease and current membership under the same lock before idempotent scoped hard-delete. Mất membership sau rejoin tạo intent mới; project delete bypasses grace by FK cascade.
- Rollback/race suites: `DocumentAiRollbackIntegrationTest` 2/2, `AiConversationRetentionIntegrationTest` 4/4 và `AiRetentionTransactionIntegrationTest` 1/1 pass trên PostgreSQL Testcontainers.

### M5 evidence đã xác minh

- `DocumentIndexJobHandler` chạy sau claim transaction, đọc binary duy nhất qua `StorageService`, kiểm tra bounded source size + SHA-256, extract/chunk/embed theo từng document và persist state qua các transaction ngắn.
- `document_ai_indexes` giữ last READY generation trong lúc replacement đang PROCESSING; staging chỉ được activate khi document/project còn tồn tại, desired version đúng và lease token còn hợp lệ. Activation lặp lại an toàn, stale worker không thể đổi state hoặc resurrect chunks sau document/project delete.
- Supported AI extraction allowlist là PDF/DOC/DOCX/PPT/PPTX/MD/TXT; unsupported/corrupt/no-text/invalid embedding/provider/storage failure trở thành safe terminal hoặc bounded retry category. Raw source, prompt, vector, provider body, credential và exception message không đi vào durable job/index state.
- `DocumentAiIndexPersistenceIntegrationTest` 6/6 trên PostgreSQL 17.11/pgvector và targeted M5 suite 17/17 pass; full `mvn -B -ntp clean verify` 307/307 pass. M5 không thêm endpoint, migration hoặc generated-doc change.
- Worker lease hiện không heartbeat trong lúc extraction/embedding dài; limitation được ghi trong M5 completion plan và technical-debt tracker. Stale-lease protection vẫn là authoritative safety behavior.

### M6 evidence đã xác minh

- Project RAG trả `NO_EVIDENCE` cố định khi retrieval hiện tại không có usable evidence, không gọi chat; provider timeout/unavailable/invalid response vẫn là lỗi riêng. Access được kiểm tra trước embedding, trước chat và sau chat, nên in-flight membership removal không trả completed result.
- Query candidate top-K và final constituent-chunk budget bị chặn bởi `AiProperties`; lịch sử hội thoại chỉ giữ tối đa tám turns/4,000 ký tự, không là evidence. Citation snapshot giữ metadata khi live source bị xóa; no public URL được lưu.
- Targeted M6 33/33 pass gồm real PostgreSQL 17.11/pgvector cross-project/active/deleted/citation/revocation cases. Full gate được ghi tại M6 plan; không yêu cầu real Gemini connectivity.

### M7 conversation lifecycle

- M7 tạo USER và ASSISTANT PROCESSING trong transaction ngắn trước provider; failure/revoke chuyển marker thành FAILED an toàn trong transaction riêng, giữ câu hỏi đã chấp nhận. V4 partial unique index và durable user-row quota lock bảo vệ concurrency qua nhiều JVM; delete conversation không thể bị late provider completion tái tạo.
- Grounded answer và citations được commit cùng transaction sau current-access recheck; NO_EVIDENCE là successful completed result. Source snapshot vẫn đọc được khi live document/chunk bị xóa, nhưng `availability=UNAVAILABLE` và không có live document ID. API-AI-008/009 chỉ đọc trạng thái hoặc enqueue retry; không gọi provider đồng bộ.

### M10 usage guard and observability invariants

- The interactive AI budget is cross-instance because Redis owns the fixed-window counter; injected clock buckets make rollover deterministic and do not depend on TTL expiration.
- Redis Lua increments and first-request TTL assignment are one atomic operation. A later request cannot extend the window; an old bucket cannot affect the next bucket. Redis restart may reset only ephemeral rate state and cannot remove PostgreSQL conversations, messages, jobs or Guide index state.
- Capability and authorization checks precede charging. A 429 therefore leaves no partial conversation/message state, and Redis failure becomes a guarded-AI-only 503 while Core endpoints remain available.
- Accepted `NO_EVIDENCE` and provider failures consume one request unit because the policy is request-based; no refund/reconciliation path can create an unbounded provider escape.
- Telemetry is bounded and best effort. Micrometer recording cannot fail business requests, and job depth/stale/failed signals use finite job-state tags without raw content or provider material. No public metrics endpoint was added in M10.

M10 verification: Redis guard 5/5, observability 3/3, Guide controller 1/1, Project Assistant/API contract focused regression pass, OpenAPI 38/57/15 and final clean verify 371/371. M11 now has a safe deterministic Docker path and proves clean V1–V4 startup, Project Assistant/Guide grounded and no-evidence paths, plus provider-unavailable AI 503 while Core remains healthy. Restart/recovery and the remaining runtime matrix are still unverified; real Gemini connectivity remains intentionally absent.

### M11 runtime verification and freeze (2026-09-26)

- M11 runtime matrix verified qua isolated Compose project với real HTTP boundary: security 37/37, retention (+P7D exact schedule, rejoin restore/cancel/quota, runtime purge qua due-time fixture trên job hợp lệ, no resurrection, in-flight revoke→rejoin race), restart/recovery (pending job resume, stale-PROCESSING reclaim với lease-token guard, PostgreSQL/MinIO persistence, Redis ephemerality), và comprehensive log audit 0 sensitive hits trên 518 dòng/9 scenario. Final gate 377/377; **AI v1 backend FROZEN**.
- Abrupt JVM death giữa chat generation có thể để lại ASSISTANT PROCESSING vô thời hạn (reproduced runtime): marker giữ null content, send mới trên conversation đó 409 `AI_REQUEST_IN_PROGRESS`; verified workaround — creator DELETE conversation xóa rows và giải phóng quota + generation lock. Debt MEDIUM, tracked, không chặn freeze.
- Worker lease cố định không heartbeat: correctness được giữ bởi lease-token guards (stale owner không activate/DONE/RETRY/FAILED — verified 0-row transition), nhưng reclaim có thể gây duplicate computation. Debt MEDIUM, tracked, không chặn freeze.
- Transient revoke→rejoin turn persist `failure_code=AI_PROVIDER_UNAVAILABLE` (observability precision, LOW) — contract M7/M8 chỉ assert 403/FAILED/0 sources.
