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
- Guide tests prove the exact allowlist, zero project-corpus access, and null-threshold fail-closed NO_EVIDENCE without a chat call.
- Final AI gate vẫn chạy toàn bộ Core regression; không disable Core tests để làm AI pass.

### AI v1 M3 verification đã chạy

- `AiJobEngineIntegrationTest` 9/9 dùng PostgreSQL Testcontainer thật: `SKIP LOCKED`, hai worker, due/retry timing, lease expiry/reclaim, stale-owner rejection, bounded attempts, terminal filtering và transaction-advisory-lock dedup.
- `AiJobSchedulerTest` 5/5 và scheduler integration cases: handler registry boundary, no-handler preservation, handler ngoài claim transaction, `SUCCESS`/`RETRY`/`FAILURE`/exception mapping và AI-disabled startup.
- `DocumentAiIntentIntegrationTest` 6/6 + `DocumentAiRollbackIntegrationTest` 2/2: supported/unsupported intent, one active job, deterministic metadata, duplicate idempotency, single/batch rollback, pending/claimed delete race và no resurrection.
- `ConversationPurgeJobHandlerTest` 3/3 và `AiConversationRetentionIntegrationTest` 6/6 + `AiRetentionTransactionIntegrationTest` 1/1: malformed/stale lease safety, exact pre-due protection, scoped idempotent purge, AI-disabled purge execution, same-transaction loss scheduling, rejoin cancellation/no-op, loss-after-rejoin scheduling và project cascade cleanup on PostgreSQL.
- `mvn -B -ntp test` và `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 268 tests, 0 failures, 0 errors, 0 skipped. Các job/lease tests không dùng H2 hoặc `Thread.sleep`; clock được inject/điều khiển.

### AI v1 M4 provider verification đã chạy

- `AiGeminiProviderConfigurationTest`, `AiProviderErrorTranslatorTest`, `AiProviderPrivacyTest`, `SpringAiGeminiChatAdapterTest` và `SpringAiGeminiEmbeddingAdapterTest`: 22/22 pass với deterministic Spring AI doubles; không cần credential thật hoặc public Gemini network.
- Configuration tests chứng minh `kbase.ai.enabled=false` khởi động không có key/provider bean; enabled synthetic key chỉ tạo explicit models, không gọi network; blank key map safe configuration error.
- Chat contract tests chứng minh system riêng, conversation ordered, evidence labelled/separate user data, current question cuối, model fallback và invalid response category.
- Embedding contract tests chứng minh query/document preparation tập trung, options `gemini-embedding-2`/`768`, 767/769 reject, 768 accept và non-finite reject.
- Error/privacy tests chứng minh timeout/rate-limit/configuration/unavailable mapping và không lộ raw provider message, prompt, evidence, document, vector, response body hoặc key sentinel.
- `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 290 tests, 0 failures, 0 errors, 0 skipped; Compose config và `git diff --check` pass.
- Static audit chứng minh vendor imports chỉ ở `com.kbase.ai.provider.springai`, không có M5+ extraction/indexing/RAG/API leakage. Real-provider connectivity chưa được test theo M4 gate.

### AI v1 M5 extraction/indexing verification đã chạy

- `DocumentExtractionAndChunkingTest` 2/2 chứng minh đúng allowlist/unsupported behavior, PDF/PPT/PPTX/Markdown source locations, empty/corrupt/no-text safety, deterministic `kbase-lex-v1` token counts, `chunk-v1` boundaries, overlap and location preservation.
- `DocumentIndexJobHandlerTest` 7/7 dùng deterministic `StorageService`, extractor và embedding doubles để chứng minh unsupported no-op, bounded source read/hash, successful embedding/activation path, partial embedding failure, provider retry/exhaustion and safe category mapping.
- `DocumentAiIndexApplicationServiceTest` 2/2 chứng minh status view không expose entity và manual retry chỉ được phép từ terminal `FAILED` state với pessimistic-lock boundary.
- `DocumentAiIndexPersistenceIntegrationTest` 6/6 dùng PostgreSQL 17.11/pgvector Testcontainer thật để chứng minh initial `PENDING → READY`, staged replacement/atomic activation, last-good preservation, idempotent activation, stale lease rejection và document/project delete races.
- `mvn -B -ntp "-Dtest=DocumentExtractionAndChunkingTest,DocumentIndexJobHandlerTest,DocumentAiIndexApplicationServiceTest,DocumentAiIndexPersistenceIntegrationTest" test`: `17/17`, 0 failures, 0 errors, 0 skipped.
- `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 307 tests, 0 failures, 0 errors, 0 skipped. Compose config, `git diff --check` and static boundary/privacy scans pass; no generated DB/API docs changed. Real Gemini network and live-provider Compose indexing remain intentionally untested.

### AI v1 M6 retrieval/grounding/citation verification

- `EvidenceSelectorTest`, `ConversationContextPolicyTest`, `SourceLabelAndCitationTest`, `ProjectRagServiceTest`: 17 deterministic unit tests for similarity threshold, dedup, adjacent merge/source identities, context budget, NO_EVIDENCE zero chat, prompt isolation, label rejection, auth order/rechecks, in-flight revoke and provider failure.
- `AiPersistenceIntegrationTest`: 16 real PostgreSQL 17.11/pgvector tests including five M6 cases: closer Project B trap through full internal RAG, READY/active/staging/failed/deleted filters, candidate top-K and score direction, mapped citation snapshot surviving document deletion, and real membership removal while fake chat is blocked.
- Targeted command `mvn -B -ntp "-Dtest=EvidenceSelectorTest,SourceLabelAndCitationTest,ConversationContextPolicyTest,ProjectRagServiceTest,AiPersistenceIntegrationTest" test` → 33/33 pass, 0 failures/errors/skips. Full gate result is recorded in the M6 execution plan.

### AI v1 M7 conversation/API verification

- `ProjectAssistantM7IntegrationTest` dùng real PostgreSQL 17.11/pgvector, real SecurityFilterChain/MockMvc, M6 RAG và deterministic `FakeAiChatModel`/`FakeAiEmbeddingModel`; không có real Gemini credential/network. Test bao phủ create/NO_EVIDENCE, JWT, private MEMBER/OWNER/ADMIN lookup, user-row quota 4→5 race, per-project/per-user quota, hard-delete quota release, one-active send, distinct conversations concurrent, provider failure giữ USER/FAILED, revoke trong và sau M6 trước finalization, delete khi generation chạy, citation availability sau document delete, index status/retry permission và non-FAILED rejection.
- `OpenApiContractIntegrationTest` assert chính xác 38 paths / 57 operations, 15 tags, M7/M9 methods plus M10 429/503 annotations, error/DTO schemas và sensitive-field absence. `ProjectAssistantTitleTest` kiểm tra Unicode-safe 100-code-point title.
- Targeted command `mvn -B -ntp "-Dtest=ProjectAssistantM7IntegrationTest,ProjectAssistantTitleTest,OpenApiContractIntegrationTest" test`: 36/36 PASS, 0 failures/errors/skips. Full M7 clean verify result nằm trong active/completed M7 execution plan.

### AI v1 M9 verification đã chạy

- `GuideSourcePackagingTest` proves packaged bytes and SHA-256 match exactly the two canonical source files; `GuideMarkdownChunkerTest` proves fenced-code-safe heading paths; `GuideRagServiceTest` proves a null similarity threshold returns deterministic `NO_EVIDENCE` with zero chat calls; `GuideVectorRepositoryIntegrationTest` proves a rogue perfect-vector source is excluded in SQL before ANN candidate limiting.
- M9 focused Guide tests, M7 regression + Guide startup, and the OpenAPI contract have passed with deterministic fakes/real PostgreSQL where required; the final `mvn -B -ntp clean verify` result is 356/356 with zero failures/errors/skips. No real Gemini credential or public network was used.

### AI v1 M10 verification đã chạy

- `RedisAiUsageGuardIntegrationTest`: 5/5 trên Redis 7.4 Testcontainer, bao phủ atomic fixed-window limit, same-user shared budget/isolation, controllable-clock rollover, first-request TTL semantics và Redis-unavailable → `AI_USAGE_GUARD_UNAVAILABLE`.
- `AiObservabilityTest`: 3/3 chứng minh counters/timers/summaries/gauges được ghi, tag vocabulary được bound và prompt/content sentinels không xuất hiện trong metric tags hoặc telemetry path.
- `GuideControllerTest`: 1/1 chứng minh Guide unavailable → stable `AI_PROVIDER_UNAVAILABLE`/503 và guard failure boundary; no real provider call/network.
- Project Assistant focused regression covers capability/auth/creator preflight before charging, 429 leaves no conversation/message state, 503 guard failure is safe, shared guard injection and provider/NO_EVIDENCE outcomes. `ProjectAssistantM7IntegrationTest` focused set passed 20/20.
- `OpenApiContractIntegrationTest,OpenApiDisabledIntegrationTest`: 22/22; runtime remains exactly 38 paths / 57 operations / 15 tags and documents `AI_RATE_LIMIT_EXCEEDED`/429 plus `AI_USAGE_GUARD_UNAVAILABLE`/503 only on interactive AI POST operations.
- `RetrievalThresholdEvaluationTest` records deterministic relevant/weak/unrelated score ranges and the M10 v1 selection `0.70`; no real Gemini calibration is claimed.
- Final `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 371 tests, 0 failures/errors/skips. `docker compose -f docker-compose.yml config --quiet`, `git diff --check` and sensitive-data/scope scans pass. Non-fatal Testcontainers shutdown/placeholder PostgreSQL scheduler warnings do not change the successful Surefire result.

### AI v1 M11 runtime repair (in progress)

- Post-fix `mvn -B -ntp clean verify` is `BUILD SUCCESS`, 373 tests, 0 failures/errors/skips and jar repackage pass. The runtime repair adds configuration tests proving deterministic mode fails without `runtime-test` + acknowledgement and creates no Spring AI/Google client.
- Clean isolated Compose with the approved pgvector image, Redis, MinIO and mail double applies Flyway V1–V4, validates Hibernate and serves `/v3/api-docs` 200.
- Real HTTP verification proves supported Markdown upload reaches `READY`, Project Assistant returns grounded + one source and strict no-evidence + zero sources, and Guide does the same against packaged allowlisted corpus.
- The deterministic unavailable override returns AI 503 while authenticated Core project list remains 200. Remaining failure variants, security/retention/restart and final audit remain M11 work; no real Gemini credential or public network was used.

### AI v1 M11 runtime verification (freeze, 2026-09-26)

- Final gate `mvn -B -ntp clean verify`: `BUILD SUCCESS`, 377 tests, 0 failures/errors/skips. Compose base + mail-test + mọi M11 verification override pass `config --quiet`; `git diff --check` clean.
- M11 runtime matrix đã chạy trên isolated Compose project `kbase-m11fix` qua real HTTP boundary: security matrix 37/37; retention matrix (+P7D schedule, rejoin restore/cancel/quota, runtime purge qua due-time DB fixture chỉ trên job hợp lệ, no resurrection, in-flight revoke→rejoin race 403/FAILED/0 citations); restart/recovery (pending job resume sau backend-only stop, stale-PROCESSING reclaim với lease-token guard 0-row, PostgreSQL/MinIO persistence, Redis ephemerality, abrupt chat JVM death classification + verified creator-DELETE workaround).
- Comprehensive retained-log audit: 0 sensitive hits trên 518 dòng/9 scenario types; error logs category-only.
- Consistency audits: config defaults đồng bộ (AiProperties = application.yml = .env.example = docker-compose.yml = DEPLOYMENT.md); Flyway V1–V4 + 18 tables + `vector(768)` + HNSW runtime-verified; runtime OpenAPI 38/57/15 = contract test = generated snapshot.
- No code/schema/API/generated-snapshot change trong M11 completion; real Gemini không dùng. AI v1 backend FROZEN 2026-09-26.
