# Hướng dẫn Tích hợp

## Mục đích

Tài liệu này quy định cách frontend, backend, database và dịch vụ bên ngoài giao tiếp với nhau.

## Phạm vi Hiện tại

Core v1 integrations dưới đây đã được triển khai/xác minh. AI v1 backend hiện là active implementation phase; frontend/streaming vẫn deferred. Bảng đầu tiên mô tả Core runtime hiện tại:

| Nguồn | Đích | Mục đích | Persistence | Boundary |
|---|---|---|---|---|
| Spring Boot backend | PostgreSQL | Persistent business metadata, refresh sessions, invitation hashes | Durable qua `postgres_data` | Repository / JPA |
| Spring Boot backend | Redis | Email verification OTP state, TTL, attempts, resend cooldown | Ephemeral, không durable volume | `OtpStore -> RedisOtpStore` |
| Spring Boot backend | MinIO | Binary document storage | Durable qua `minio_data` | `StorageService -> MinioStorageService` |
| Spring Boot backend | Gmail SMTP | Gửi verification OTP và project invitation | External provider | `MailService -> SmtpMailService` |

Nguồn thiết kế chi tiết: `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md`, `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md`, `docs/design-docs/KBase - Core v1 MinIO Integration Design.md`.

## Thông tin bắt buộc cho mỗi integration

Mỗi integration quan trọng phải mô tả:

- Hệ thống nguồn và hệ thống đích.
- Mục đích.
- Giao thức.
- Authentication.
- Contract dữ liệu.
- Timeout.
- Retry.
- Idempotency.
- Error mapping.
- Logging và monitoring.
- Verification.
- Cách tắt, fallback hoặc rollback.

## Frontend và Backend

- Frontend hiện chưa thuộc phase implementation. Không tạo frontend integration chỉ vì tài liệu này có section frontend.
- Khi frontend được mở lại, contract API là nguồn sự thật chung.
- Frontend không suy đoán field hoặc status không được định nghĩa.
- Backend phải giữ response và error format nhất quán.
- Thay đổi contract phải được kiểm tra ở cả hai phía khi frontend tồn tại.
- Authentication, token refresh, session và CORS phải được mô tả rõ.
- UI phải xử lý loading, empty, error, permission denied và retry khi phù hợp.

## Backend và Database

- PostgreSQL chạy qua local Docker path chuẩn; không phụ thuộc PostgreSQL cài trên host.
- Flyway migration được đóng gói với backend và chạy vào PostgreSQL; Hibernate validate schema sau migration.
- `postgres_data` giữ database data/Flyway history qua container recreation.
- Truy cập database qua Repository/JPA boundary được kiến trúc cho phép.
- Transaction boundary phải rõ ràng.
- Không dựa vào thứ tự dữ liệu nếu database không bảo đảm.
- Query quan trọng phải được kiểm tra hiệu năng.
- Thay đổi schema phải tuân theo `docs/DATABASE.md`.

## Backend và Redis OTP

- Redis chỉ lưu registration email-verification OTP state ngắn hạn.
- Redis không lưu refresh session hoặc durable business state.
- Raw OTP không được lưu; Redis giữ protected/hash state, attempt count và TTL/cooldown state theo design.
- Redis container không cần named volume; mất pending OTP sau Redis recreation là chấp nhận được và user có thể resend.
- Redis unavailable phải map sang error contract phù hợp, ví dụ `OTP_SERVICE_UNAVAILABLE`.
- OTP TTL, resend cooldown và max attempts phải được test bằng Redis integration test.

M5 implementation details:

- Client là `LettuceConnectionFactory` + `StringRedisTemplate`; không dùng Java serialization.
- State key: `kbase:otp:email-verification:{userId}`. Hash fields: `otp_hash` (hex HMAC-SHA-256) và `attempts` (decimal string). State key luôn có TTL.
- Cooldown key: `kbase:otp:email-verification:cooldown:{userId}`, value cố định và TTL bằng cooldown duration.
- Save/replace/reset và attempt increment dùng Redis Lua script; replacement kiểm tra cooldown và ghi state mới trong cùng một operation.
- Default command timeout là `kbase.redis.timeout=2s`; không retry mù quáng OTP writes. Khi Redis không khả dụng, adapter trả `OTP_SERVICE_UNAVAILABLE` và không trả Redis host/key/command/hash.
- Local Compose chạy Redis bằng `--save "" --appendonly no` và mount `/data` vào `tmpfs`; cấu hình không tạo named Redis volume.

## Backend và MinIO

- MinIO bucket private; client không truy cập bằng public permanent URL.
- Metadata ở PostgreSQL; binary ở MinIO; storage key do backend tạo.
- `minio_data` giữ binary qua container recreation.
- Upload/download/preview dùng streaming; MP4 range read phải theo MinIO design.
- PostgreSQL và MinIO không có distributed transaction; upload/delete compensation behavior phải giữ đúng Service/MinIO design.
- MinIO unavailable phải được translate tại adapter boundary và map sang stable KBase error.

M10 implementation details:

- `StorageService` is the authorization-free vendor-neutral port; only `MinioStorageService` and storage config import MinIO SDK classes.
- `MinioClient` is one Spring bean with endpoint, credentials, bucket, region and bounded connect/write/read timeouts from typed `kbase.storage` configuration.
- Local profile may create a missing configured bucket and validates it at startup. Base/production defaults leave `auto-create-bucket` and startup initialization off; the adapter never changes bucket policy, versioning, retention or object lock. Core v1 therefore retains a private unversioned bucket.
- `StorageKeyFactory` creates only `projects/{projectId}/documents/{documentId}.{extension}`. It does not use original filenames or folder/category/tag metadata.
- Upload and reads accept/return streams; `getRange(storageKey, offset, length)` passes the requested range to MinIO for future MP4 preview. Callers close returned read streams.
- `deleteAll` exhausts MinIO's per-object result iterable and surfaces any partial failure as an internal `StorageDeleteException`.

## Backend và Gmail SMTP

- Gmail SMTP dùng cho cả verification OTP và project invitation.
- Gmail username/App Password lấy từ environment/secret mechanism; không nằm trong source/log.
- Application service chỉ gọi `MailService`; Gmail/SMTP exception được translate trong mail adapter.
- Automated CI không gửi email thật; dùng mock hoặc fake/local SMTP khi cần integration test.
- Mail failure phải có timeout/failure mapping rõ và không làm lộ OTP, invitation token hoặc credential trong log.

M5 implementation details:

- `MailService` chỉ nhận application values cho verification OTP và invitation URL; `JavaMailSender` chỉ được dùng trong `SmtpMailService`/`MailConfig`.
- Gmail baseline là `smtp.gmail.com:587`, authentication + STARTTLS; username/App Password lấy từ `KBASE_GMAIL_SMTP_USERNAME` và `KBASE_GMAIL_SMTP_APP_PASSWORD`.
- Connection/read/write timeout mặc định là 10 giây qua `kbase.mail.timeout`; không bật retry tự động cho gửi mail không-idempotent.
- Provider/configuration/template failure được dịch thành `EMAIL_SERVICE_UNAVAILABLE`; response không chứa SMTP host, response, account, credential, raw OTP hoặc raw invitation token.
- Templates nằm tại `templates/mail/email-verification-otp.html` và `templates/mail/project-invitation.html`; dynamic HTML values được escape trước khi render.
- Verification dùng fake SMTP server trong automated tests; không cần credential thật và không gọi Gmail/public internet.

## Backend Authentication Wiring (M6)

- Registration/login flow phối hợp PostgreSQL (user row, `email_verified_at`, `refresh_sessions`), Redis (OTP state ngắn hạn) và Gmail SMTP (OTP delivery) qua các boundary `UserRepository`, `OtpService`/`OtpStore`, `MailService`.
- `EmailVerificationService` là orchestrator duy nhất của OTP issue/verify/resend. Gmail send thất bại sau Redis write → best-effort cleanup OTP state rồi rethrow `EMAIL_SERVICE_UNAVAILABLE`; Redis failure trước khi send → `OTP_SERVICE_UNAVAILABLE`.
- `AuthService.register` chạy trong một transaction: persist user rồi issue OTP; mail failure rollback user row nên retry sạch.
- Access JWT được ký HS256 với secret từ `KBASE_JWT_SECRET` (tối thiểu 256 bit); claims chỉ gồm `sub` (userId), `systemRole`, `iat`, `exp`, `jti`.
- `JwtAuthenticationFilter` load current User từ DB mỗi request có Bearer header; DB là source of truth cho status/systemRole nên disable account có hiệu lực ngay lập tức.
- Refresh token: opaque 32-byte `SecureRandom` URL-safe; client chỉ nhận raw token qua cookie; DB lưu SHA-256 hex hash trong `refresh_sessions`; validate theo thứ tự tồn tại → chưa revoke → chưa hết hạn → user ACTIVE → email verified.
- Refresh cookie: `HttpOnly`, `Secure` theo profile (prod `true`, local `false`), `SameSite=Lax`, `Path=/api/v1/auth`, `Max-Age` = refresh TTL; raw token không bao giờ nằm trong JSON body hoặc log.
- CSRF bị tắt cho toàn bộ API vì authentication dùng Bearer header; refresh/logout dùng cookie SameSite=Lax path-scoped theo security design; nếu deployment đổi SameSite/cross-site, CSRF cho refresh/logout phải xem xét lại.
- CORS chỉ cho origin đã cấu hình (`KBASE_ALLOWED_ORIGINS`); wildcard origin bị chặn khi `allow-credentials=true`.
- Security-layer 401/403 dùng `ApiErrorResponse` với `X-Request-Id` qua `RestSecurityErrorWriter`; filter chain stateless và không chứa project/document authorization.

## Dịch vụ bên ngoài

- Payload bên ngoài luôn được xem là không đáng tin.
- Mọi network call phải có timeout.
- Retry chỉ áp dụng cho lỗi có thể retry.
- Không retry mù quáng operation không idempotent.
- Dữ liệu provider phải được mapping tại adapter boundary.
- Hành vi khi provider lỗi phải được định nghĩa.

## Event, queue hoặc background job

Core v1 không yêu cầu message queue/background job. AI v1 **có** durable background jobs nhưng không cần broker; dùng PostgreSQL-backed `ai_jobs` + scheduled worker.

AI v1 job types baseline: `DOCUMENT_INDEX`, `DOCUMENT_REINDEX`, `CONVERSATION_PURGE`, `GUIDE_REINDEX`.

Required:

- Job payload/schema phải rõ ràng và không chứa raw document/prompt/secret.
- Worker phải xử lý duplicate/reclaim/idempotency.
- Retry bounded + run-at/lease/stale recovery phải được định nghĩa.
- Job phải có trạng thái và khả năng chẩn đoán.
- Claim baseline dùng PostgreSQL locking/`SKIP LOCKED`; không giả định exactly-once.
- Scheduler/JVM memory không phải durable source of truth.

## Verification

Tùy integration, chạy:

- Contract test.
- PostgreSQL Testcontainers cho migration/repository/transaction.
- Redis Testcontainers cho OTP TTL/attempt/cooldown.
- MinIO Testcontainers cho upload/read/range/delete/compensation.
- Mock/fake SMTP cho automated mail tests.
- Test invalid payload.
- Test timeout/failure mapping.
- Test duplicate/concurrent request khi business rule yêu cầu.
- Test authentication/authorization failure.
- Backend API/integration test cho golden journey quan trọng.

M5 verification đã chạy:

- Redis 7.4 Testcontainer: port abstraction, protected state, TTL, attempt counter, cooldown, replacement/reset, delete và unavailable mapping.

M10 verification đã chạy:

- MinIO Testcontainer: configured bucket initialization, disabled-versioning default, stream upload/full and offset/length reads, stat, single delete and batch delete.
- Fake SMTP: verification/invitation message content, HTML escaping, typed Gmail timeout/STARTTLS configuration, provider failure mapping và sensitive-log capture.

M6 verification đã chạy:

- AuthenticationSecurityIntegrationTest 14/14 với PostgreSQL 17 + Redis 7.4 Testcontainers và real SecurityFilterChain: register→verify→login→refresh→logout journey, cooldown/resend replace, OTP invalid/expired/exhausted, JWT valid/expired/tampered, disabled account (access cũ + refresh + login đều chặn), refresh session hash-only trong PostgreSQL, cookie attributes, ADMIN route protection và 401/403 error contract.
- Regression toàn suite 104 tests pass qua `mvn test` và `mvn clean verify`.

## Docker Runtime Wiring (M14)

- Backend container join compose network và kết nối dependency bằng service name với internal port: `postgres:5432`, `redis:6379`, `http://minio:9000`; không bao giờ nhắm `localhost` từ trong container. Host chỉ expose 8080 (backend), 5432/6379/9000/9001 cho dev.
- Startup dependency dùng healthcheck (`pg_isready`, `redis-cli ping`, MinIO `minio/health/live`) với `depends_on: service_healthy`; không dùng arbitrary sleep.
- Backend image không chứa secret; mọi credential/endpoint đến từ environment (`${VAR:?required}` trong compose, giá trị thật trong `.env` git-ignored).
- Gmail SMTP vẫn external. `docker-compose.mail-test.yml` là optional override (mailpit) chỉ dùng cho verification tự động để bắt OTP/invitation email; production giữ Gmail thật qua `KBASE_GMAIL_SMTP_*` và không dùng override này.
- Persistence đã verify trong runtime: `postgres_data`/`minio_data` giữ data qua backend restart và container force-recreate; Redis recreation làm mất pending OTP và resend vẫn hoạt động.


## AI v1 Integrations (M10 hardening complete; M11 runtime verification next)

| Nguồn | Đích | Mục đích | Persistence | Boundary |
|---|---|---|---|---|
| AI application | Gemini | grounded chat generation | External provider, không durable | `AiChatModel -> SpringAiGeminiChatAdapter` explicit M4 adapter |
| AI indexing/retrieval | Gemini embedding | document/query embeddings | External provider | `AiEmbeddingModel -> SpringAiGeminiEmbeddingAdapter` explicit M4 adapter |
| AI repositories | PostgreSQL + pgvector | chunks/vectors/conversations/jobs/Guide corpus | Durable qua existing PostgreSQL volume | KBase repository + Flyway V4 schema; verified M2 |
| AI worker | MinIO | đọc binary source để extract/index | Binary vẫn durable trong MinIO | existing `StorageService` only; không import MinIO SDK |
| AI usage guard | Redis | ephemeral per-user interactive AI usage/cost guard | Ephemeral; không business source of truth | `AiUsageGuard -> RedisAiUsageGuard`, namespace `kbase:ai:rate`, atomic Lua counter |

Gemini integration requirements:

- API key/model/timeouts qua typed config + environment; không hard-code.
- Project text/chunks gửi sang provider là external data boundary; không log raw payload.
- Provider timeout/rate/error translate tại adapter; Core endpoint không phụ thuộc provider health.
- Không retry mù quáng chat generation. Embedding/index job retry chỉ cho transient categories và phải idempotent/bounded.
- Spring AI là primary integration path; direct Google SDK chỉ khi M0 ghi rõ gap và vẫn nằm sau KBase port.
- M4 explicit configuration chỉ tạo vendor clients/adapters khi `kbase.ai.enabled=true`; disabled Core startup không yêu cầu API key và không gọi provider.
- Chat mapping giữ system/conversation/evidence/question boundary; evidence được gửi như untrusted user data, không làm system instruction.
- Embedding preparation tập trung tại adapter: `QUERY` dùng `task: question answering | query: ...`, `DOCUMENT` dùng `title: ... | text: ...`; output bắt buộc `768`.
- `kbase.ai.provider.request-timeout` đi qua Google GenAI `HttpOptions`; Google GenAI `1.65.0` không có independent connect-timeout surface trên selected client path, nên typed connect-timeout chưa được claim là active.

pgvector requirements:

- Flyway owns extension/schema.
- embedding baseline `gemini-embedding-2`, dimension 768.
- vector retrieval project-scoped trong SQL.
- real pgvector-enabled PostgreSQL Testcontainer verification.

M2 verification đã chạy:

- fresh Flyway V1–V4 apply và Hibernate `ddl-auto=validate` trên pgvector PostgreSQL 17.11;
- Core V1–V3 upgrade-to-V4 path giữ nguyên user/project data;
- live catalog xác nhận extension, `vector(768)`, HNSW cosine indexes, relational indexes và FK/delete rules;
- `AiVectorRepository` giữ project filter và active-version predicate trong SQL; không global-search rồi filter bằng Java.

M4 đã triển khai provider adapter/configuration/error/privacy boundary; M5 dùng embedding port trong worker, M6 dùng query embedding/chat port trong Project RAG, M7 expose private conversation cùng document index REST, M9 dùng cùng provider-neutral ports cho Guide DOCUMENT indexing/QUERY retrieval, và M10 thêm guard/observability/contract hardening. M11 vẫn giữ full provider-backed runtime verification.

## AI v1 M3 Durable Job và Core Lifecycle Hooks

M3 đã triển khai durable async boundary nhưng chưa triển khai provider execution:

- `AiJobClaimRepository` dùng PostgreSQL `FOR UPDATE SKIP LOCKED` trong transaction ngắn; `AiJobStore` quản lý lease/retry/stale recovery, bounded attempts, advisory-lock active dedup và transition conditional theo lease token.
- `AiJobScheduler` poll theo `kbase.ai.worker.*`, chỉ claim các type có trong handler registry và không tiêu thụ job chưa có production handler. Scheduler luôn có mặt cho durable maintenance: khi AI disabled, chỉ `CONVERSATION_PURGE` provider-independent được claim; `DOCUMENT_INDEX` không đăng ký và không thể bị consume.
- `DocumentService` giữ StorageService/compensation authority và gọi `DocumentAiIntentService` sau document + tags. Supported files tạo `PENDING` index và một `DOCUMENT_INDEX`; unsupported Core files tạo `UNSUPPORTED` không có worker job. AI hook không đọc binary và không gọi network.
- `ProjectMemberService` và `InvitationService` dùng `AiConversationRetentionService` để enqueue/cancel `CONVERSATION_PURGE` với dedup key `conversation-purge:{projectId}:{userId}` tại `membershipLostAt + P7D`. PostgreSQL advisory lock trên cùng key serialize loss/rejoin/purge; purge chỉ hard-delete scoped conversations khi exact current lease còn `PROCESSING` và membership vẫn absent. Project/document delete dựa trên V4 FK cascade và lease-conditional transitions để chặn resurrection.

Verification: `AiJobEngineIntegrationTest` 9/9, `AiJobSchedulerTest` 5/5, `DocumentAiIntentIntegrationTest` 6/6, `DocumentAiRollbackIntegrationTest` 2/2, `AiConversationRetentionIntegrationTest` 4/4 và `AiRetentionTransactionIntegrationTest` 1/1 trên PostgreSQL Testcontainers; full suite 268/268 pass. Không có Gemini provider call, extraction, public AI API hoặc destructive purge.

## AI v1 M4 Provider Boundary

M4 đã thêm explicit Spring AI/Google GenAI wiring và hai adapter sau KBase-owned ports:

- `AiGeminiProviderConfiguration` guarded by `kbase.ai.enabled=true`, validates the key only when enabled, and keeps vendor auto-configuration excluded from the disabled path;
- `SpringAiGeminiChatAdapter` maps system, ordered conversation, separate evidence data and final question without authorization, retrieval or persistence policy;
- `SpringAiGeminiEmbeddingAdapter` owns deterministic query/document preparation and rejects any vector whose dimension is not exactly 768;
- `AiProviderException`/`AiProviderErrorCategory` remove provider-specific exception and payload details before errors leave the boundary;
- raw prompts, evidence, document content, vectors, provider bodies and keys are not logged or retained by the provider exception.

M4 verification: targeted adapter/configuration/error/privacy suite 22/22 and full `mvn -B -ntp clean verify` 290/290 pass. Tests use deterministic doubles and synthetic key values only; real Gemini credential/public network are intentionally not required. Request timeout is active through Google `HttpOptions`; connect-timeout remains a typed future transport setting because Google GenAI 1.65.0 exposes no independent setting on this path.

## AI v1 M5 Content Extraction / Chunking / Document Indexing

M5 adds the first production-shaped indexing execution behind the existing boundaries:

- the KBase-owned `DocumentContentExtractor`/source-location models keep Tika types inside `TikaDocumentContentExtractor`;
- the allowlist is exactly PDF, DOC, DOCX, PPT, PPTX, MD and TXT. Unsupported Core-accepted files remain `UNSUPPORTED` without extraction or embedding;
- `kbase-lex-v1` and `chunk-v1` provide deterministic structure-aware chunks with known page/slide/section metadata, 700-token target and 12% overlap defaults;
- `DocumentIndexJobHandler` reads only through `StorageService`, bounds and SHA-256 checks the source against Core metadata, uses `AiEmbeddingModel` with `DOCUMENT` requests and never calls a provider in the Core upload transaction;
- PostgreSQL staging and atomic activation preserve the last READY generation until replacement succeeds. Lease-token, document/project liveness and active-version predicates prevent stale workers from resurrecting chunks;
- provider/storage failures use bounded retry categories; terminal state and error values are safe categories only. Internal status/manual retry is an application service, not a public endpoint.

M5 verification: targeted extraction/handler/status/persistence suite 17/17 and full `mvn -B -ntp clean verify` 307/307 pass; `DocumentAiIndexPersistenceIntegrationTest` 6/6 runs on PostgreSQL 17.11/pgvector. No migration, topology or generated DB/API change. Automated tests use deterministic doubles; real Gemini connectivity and live-provider Compose indexing are intentionally not required. The fixed worker lease/no-heartbeat limitation is tracked in `docs/exec-plans/tech-debt-tracker.md`.

## AI v1 M6 Project RAG integration

- Authorized project question đi qua `AiEmbeddingModel` ở `QUERY` mode, rồi `AiVectorRepository` với project predicate, active READY version và current `documents` join trong SQL. Candidate top-K là `kbase.ai.retrieval-candidate-limit`; nullable similarity threshold là inclusive minimum của `1 - cosine distance`.
- Evidence selector giữ final constituent chunks theo `retrieval-final-context-limit`; prompt gửi sang `AiChatModel` chỉ sau authorization recheck. Completed result được re-check lại sau provider call; `NO_EVIDENCE` bỏ qua chat. Citation mapper chuẩn bị `AiMessageSource` từ selected backend rows, không tạo message hay URL.
- M6 Testcontainers/fakes chứng minh Project B có perfect vector vẫn không xuất hiện trong Project A prompt/citations, READY/active/deleted filters, source snapshot sau delete và membership removal trong lúc chat. Targeted 33/33 pass trên PostgreSQL 17.11/pgvector; full gate ghi trong M6 plan. Real Gemini credential/public network không cần cho automated gate.

## AI v1 M7 conversation và REST integration

- JWT filter cấp current principal; service kiểm tra project access trước creator-scoped conversation query. M5 `DocumentAiIndexApplicationService` tiếp tục sở hữu document read/modify permission cho status/retry routes.
- PostgreSQL transaction đầu tiên persist USER và ASSISTANT PROCESSING, dùng row lock/partial unique index; M6 retrieval/embedding/chat chạy sau commit. Transaction cuối recheck current project access và persist COMPLETED answer/citations atomically. Provider failure chuyển marker FAILED, giữ USER; revoke trả current authorization error và không lưu completed content/sources.
- API source DTO lấy availability từ live document/chunk FK, chỉ giữ snapshot metadata khi source bị delete. Mở document tiếp tục dùng Core document route và current authorization. Automated M7 tests dùng deterministic `FakeAiChatModel`/`FakeAiEmbeddingModel` với real PostgreSQL 17/pgvector, không gọi public Gemini network.

Guide source integration:

- runtime không gọi GitHub để đọc docs;
- Maven/Docker build package exact allowlisted accepted product specs;
- content hash drives durable `GUIDE_REINDEX`;
- Guide query SQL joins only Guide tables and applies the immutable source-key allowlist before ANN ordering/limit; it cannot fall back to project chunks;
- missing Guide similarity threshold fails closed to `NO_EVIDENCE` without a chat call;
- không package/index toàn bộ internal docs.

## AI v1 M10 usage guard / observability integration

- `RedisAiUsageGuard` dùng fixed one-minute window mặc định, `bucket = floor(epochMillis / windowMillis)`, `kbase:ai:rate:{userId}:{bucket}` và one-shot Lua `INCR`/`PEXPIRE`. Clock injection cho phép rollover test không cần sleep; old bucket expiry không ảnh hưởng correctness.
- Project Assistant create/send và Guide query share one per-user budget (default 20). Authorization/capability checks precede charging; reads, rename/delete, document-index retry and background jobs do not charge. `NO_EVIDENCE` and provider failures consume one accepted request.
- Redis errors map to `AI_USAGE_GUARD_UNAVAILABLE`/503 only for guarded AI operations. `AI_RATE_LIMIT_EXCEEDED`/429 is used only for a real exceeded counter. Core endpoint availability and PostgreSQL durable state do not depend on the rate guard.
- `AiObservability` records bounded Micrometer counters/timers/summaries/gauges for interactive requests, rate outcomes, provider calls, retrieval candidates, no-evidence, job execution/depth and failed/stale signals. It uses finite tags and is best effort; no public metrics endpoint was added.
- M10 selected retrieval similarity default `0.70` using deterministic fixture score ranges. The configuration remains typed; an explicit blank nullable override continues to fail closed for Guide tests and does not bypass source allowlists.
- Focused evidence: Redis 5/5, observability 3/3, Guide controller 1/1, Project Assistant/OpenAPI regression pass; final clean verify 371/371. No real Gemini credential or public network was used.
- M11 runtime repair adds a packaged KBase port adapter for deterministic Docker verification. It requires `kbase.ai.enabled=true`, explicit `kbase.ai.provider.mode=deterministic`, the `runtime-test` profile and an acknowledgement; Gemini remains the default. The isolated Compose override pins the approved pgvector image and mail double, while the failure companion returns only safe provider unavailability. It has verified AI-enabled startup, Project Assistant/Guide HTTP journeys and Core isolation; the remaining M11 security/retention/recovery matrix is still active work.
