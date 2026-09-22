# Chiến lược Kiểm thử

## Mục tiêu

Kiểm thử phải cung cấp bằng chứng rằng hành vi quan trọng đúng, thay đổi không phá vỡ hệ thống và lỗi có thể được phát hiện sớm.


## Phạm vi KBase Hiện tại

- Phase hiện tại là backend-only AI v1 trên Core v1 frozen; frontend component/E2E và streaming test được hoãn cho đến khi frontend được mở lại.
- Unit/API/security test dùng Java/Spring test stack được khóa tại M0.
- PostgreSQL integration phải dùng PostgreSQL Testcontainers cho Flyway, constraint, repository và transaction behavior.
- Redis OTP integration phải dùng Redis Testcontainers cho TTL, attempts, cooldown, replacement và failure mapping.
- MinIO integration phải dùng MinIO Testcontainers cho streaming upload/read/range/delete và compensation behavior.
- Automated mail tests không gửi Gmail thật; dùng mock `MailService` hoặc fake/local SMTP tùy test layer.
- Authorization matrix `non-member / MEMBER / OWNER / ADMIN`, former-member access, cross-project isolation và unverified-user login là critical regression coverage.
- Chi tiết đầy đủ nằm trong `docs/design-docs/KBase - Core v1 Testing Strategy.md`.

## Các cấp kiểm thử

### Unit test

Dùng cho:

- Logic nhỏ và deterministic.
- Business rule cô lập.
- Mapping hoặc validation phức tạp.
- Utility có hành vi quan trọng.

### Integration test

Dùng cho:

- Database và repository.
- Framework configuration.
- External adapter.
- Queue, cache hoặc file storage.
- Transaction behavior.

### API hoặc Contract test

Dùng cho:

- Endpoint.
- Request và response schema.
- Validation.
- Error format.
- Authentication và authorization.
- Compatibility.

### Frontend test

Dùng cho:

- Component behavior.
- Form.
- State transition.
- Loading và error state.
- Interaction quan trọng.

### End-to-end test

Dùng cho:

- Golden journey.
- Luồng xuyên qua nhiều layer.
- Tích hợp frontend, backend và database.
- Luồng có rủi ro nghiệp vụ cao.

### Non-functional test

Khi cần:

- Security.
- Performance.
- Accessibility.
- Reliability.
- Migration.
- Compatibility.

## Nguyên tắc

- Test hành vi thay vì implementation detail khi có thể.
- Test phải độc lập và có thể chạy lặp.
- Không phụ thuộc vào thứ tự chạy.
- Dữ liệu test phải được kiểm soát.
- Mock chỉ dùng tại boundary hợp lý.
- Bug fix nên có regression test khi khả thi.
- Không bỏ qua test lỗi mà không ghi lý do.
- Flaky test phải được sửa hoặc theo dõi như technical debt.
- Không dùng retry vô hạn để che flaky test.

## Ma trận verification tối thiểu

| Loại thay đổi | Verification tối thiểu |
|---|---|
| Logic đơn lẻ | Unit test |
| Repository hoặc database | Integration test và migration check |
| API contract | API hoặc contract test |
| UI behavior | Frontend test hoặc E2E |
| Cross-stack flow | Integration test và E2E |
| Security-sensitive | Negative test và authorization test |
| Performance-sensitive | Benchmark hoặc load test phù hợp |

## Bằng chứng

Execution plan hoặc kết quả task phải ghi:

- Test đã thêm hoặc sửa.
- Lệnh đã chạy.
- Kết quả.
- Phần chưa kiểm chứng.
- Lý do nếu không thể chạy.

Các command chuẩn phải được khai báo trong `docs/DEVELOPMENT.md`.


## AI v1 Required Baseline

AI-specific source: `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`.

- Chat/embedding provider automated tests dùng deterministic fake/mock; normal suite không gọi Gemini thật.
- PostgreSQL vector/migration/retrieval/job tests phải dùng **real pgvector-enabled PostgreSQL Testcontainer**, không H2.
- Cross-project semantic trap test là release-critical: Project B có vector gần hơn vẫn không được xuất hiện trong Project A candidate/context/source.
- No-evidence test phải assert chat provider không được gọi.
- Prompt-injection test phải chứng minh retrieved instruction không bypass authorization/corpus.
- Conversation tests cover private owner matrix, max-5 concurrency và one-active-generation.
- Membership retention dùng controllable clock: immediate deny, rejoin <7d restore, >7d purge, purge race recheck.
- Worker tests cover `SKIP LOCKED`, duplicate/idempotency, bounded retry, stale lease recovery và deleted-document no-resurrection.
- Citation tests cover snapshot + live FK deletion + current document authorization.
- Guide tests prove exact allowlist and zero project-corpus access.
- Final AI gate vẫn chạy toàn bộ Core regression; không disable Core tests để làm AI pass.

### AI v1 M3 verification đã chạy

- `AiJobEngineIntegrationTest` 9/9 dùng PostgreSQL Testcontainer thật: `SKIP LOCKED`, hai worker, due/retry timing, lease expiry/reclaim, stale-owner rejection, bounded attempts, terminal filtering và transaction-advisory-lock dedup.
- `AiJobSchedulerTest` 5/5 và scheduler integration cases: handler registry boundary, no-handler preservation, handler ngoài claim transaction, `SUCCESS`/`RETRY`/`FAILURE`/exception mapping và AI-disabled startup.
- `DocumentAiIntentIntegrationTest` 6/6 + `DocumentAiRollbackIntegrationTest` 2/2: supported/unsupported intent, one active job, deterministic metadata, duplicate idempotency, single/batch rollback, pending/claimed delete race và no resurrection.
- `AiConversationRetentionIntegrationTest` 4/4 + `AiRetentionTransactionIntegrationTest` 1/1: remove/leave `+P7D`, same-transaction persistence, rejoin cancel/no-op, loss-after-rejoin scheduling và project cascade cleanup.
- `mvn -B -ntp test` và `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 268 tests, 0 failures, 0 errors, 0 skipped. Các job/lease tests không dùng H2 hoặc `Thread.sleep`; clock được inject/điều khiển.

### AI v1 M4 provider verification đã chạy

- `AiGeminiProviderConfigurationTest`, `AiProviderErrorTranslatorTest`, `AiProviderPrivacyTest`, `SpringAiGeminiChatAdapterTest` và `SpringAiGeminiEmbeddingAdapterTest`: 22/22 pass với deterministic Spring AI doubles; không cần credential thật hoặc public Gemini network.
- Configuration tests chứng minh `kbase.ai.enabled=false` khởi động không có key/provider bean; enabled synthetic key chỉ tạo explicit models, không gọi network; blank key map safe configuration error.
- Chat contract tests chứng minh system riêng, conversation ordered, evidence labelled/separate user data, current question cuối, model fallback và invalid response category.
- Embedding contract tests chứng minh query/document preparation tập trung, options `gemini-embedding-2`/`768`, 767/769 reject, 768 accept và non-finite reject.
- Error/privacy tests chứng minh timeout/rate-limit/configuration/unavailable mapping và không lộ raw provider message, prompt, evidence, document, vector, response body hoặc key sentinel.
- `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 290 tests, 0 failures, 0 errors, 0 skipped; Compose config và `git diff --check` pass.
- Static audit chứng minh vendor imports chỉ ở `com.kbase.ai.provider.springai`, không có M5+ extraction/indexing/RAG/API leakage. Real-provider connectivity chưa được test theo M4 gate.
