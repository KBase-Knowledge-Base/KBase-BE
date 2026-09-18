# KBase Core v1 – Active Slice: M5 Redis OTP + Gmail Mail Infrastructure

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M4_Shared_Error_Request.md` (`M4 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Xây dựng các port và adapter external authentication cần cho M6: Redis-backed email-verification OTP state và Gmail SMTP mail delivery. M5 không triển khai registration, login, security handler, feature API, frontend hoặc bất kỳ task M6 nào.

## Phạm vi M5

Bao gồm:

- `REDIS-01` — Redis client configuration với `StringRedisTemplate`, timeout và boundary lỗi hạ tầng.
- `REDIS-02` — `auth.port.OtpStore` và application model không phụ thuộc Redis.
- `REDIS-03` — `RedisOtpStore` lưu protected OTP state ngắn hạn, attempts và cooldown trong Redis ephemeral.
- `OTP-01` — `auth.service.OtpService` tạo, bảo vệ, kiểm tra và invalidates email-verification OTP.
- `MAIL-01` — `mail.service.MailService` abstraction cho verification OTP và project invitation.
- `MAIL-02` — Gmail SMTP typed configuration từ environment/configuration.
- `MAIL-03` — `SmtpMailService` adapter và exception translation.
- `MAIL-04` — safe mail templates cho verification OTP và project invitation.
- `EXT-AUTH-TEST` — unit tests, Redis Testcontainers và fake SMTP verification.

Không bao gồm:

- registration/login/verify-email/resend controllers hoặc application auth flow;
- password reset, login OTP, MFA/2FA hoặc invitation OTP;
- refresh-session Redis storage, OTP entity/repository hoặc database migration;
- MinIO, Spring Security handlers/JWT, feature services/API hoặc frontend;
- gửi email qua Gmail thật hoặc commit credential thật.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/INTEGRATION.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 Spring Boot Application Architecture.md`
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md`
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md`
- `docs/design-docs/KBase - Core v1 Exception Handling Design.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `.harness/source-doc-registry.json` (SD-01, SD-05, SD-07, SD-08, SD-10, SD-12, SD-13)

## Thiết kế triển khai đã khóa

- OTP chỉ dành cho registration email verification sau này; M5 không gọi registration/login flow.
- OTP gồm 6 chữ số, TTL 5 phút, resend cooldown 60 giây và tối đa 5 attempts; tất cả lấy từ `OtpProperties`.
- OTP được tạo bằng `SecureRandom`; Redis chỉ nhận HMAC-SHA-256 keyed hash với secret/pepper từ `KBASE_OTP_HASH_SECRET`; so sánh dùng constant-time comparison.
- Redis dùng `StringRedisTemplate` với hash field/value dạng string, không Java serialization. Namespace là `kbase:otp:email-verification:{userId}` cho state và `kbase:otp:email-verification:cooldown:{userId}` cho cooldown.
- State hash chỉ có protected OTP value và attempt count; TTL đặt trên state key. Cooldown key có TTL riêng. Resend atomic replace/reset được thực hiện trong một Redis connection callback/transaction boundary phù hợp, không lưu raw OTP.
- Application layer phụ thuộc `OtpStore`/`OtpService`, không import Redis client hoặc Redis adapter types.
- Mail application layer phụ thuộc `MailService`; `SmtpMailService` là adapter duy nhất biết `JavaMailSender`/Gmail configuration. Raw OTP/invitation token chỉ nằm trong message payload/link và không log.
- Gmail SMTP mặc định `smtp.gmail.com:587`, STARTTLS; username/App Password, timeout và flags lấy từ typed configuration/environment. Adapter dịch mail failure thành `InfrastructureException(EMAIL_SERVICE_UNAVAILABLE)`.
- Redis failure được dịch thành `InfrastructureException(OTP_SERVICE_UNAVAILABLE)`; API-safe response do M4 handler sở hữu.
- Không thay đổi schema hoặc generated DB/API contract vì M5 không tạo persistence table hay endpoint.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `REDIS-01` | `DONE` | Redis client trỏ được container config, string serialization và timeout rõ ràng |
| `REDIS-02` | `DONE` | `OtpStore` chỉ expose application models/operations |
| `REDIS-03` | `DONE` | Redis store persist protected state, attempts, TTL/cooldown; không raw OTP |
| `OTP-01` | `DONE` | secure OTP generation, HMAC protection, constant-time verification và stable errors |
| `MAIL-01` | `DONE` | `MailService` có verification/invitation methods, không lộ SMTP |
| `MAIL-02` | `DONE` | Gmail SMTP config lấy từ environment, timeout/STARTTLS rõ ràng |
| `MAIL-03` | `DONE` | SMTP adapter gửi template và maps failure thành `EMAIL_SERVICE_UNAVAILABLE` |
| `MAIL-04` | `DONE` | hai template safe, không leak secret/OTP trong log |
| `EXT-AUTH-TEST` | `DONE` | Redis Testcontainers + fake SMTP + leakage/regression tests pass |

## Verification path

- Unit test `OtpService`: generation, protection, invalid/expired/attempt-exceeded/cooldown/replacement behavior.
- Redis Testcontainers (`GenericContainer`) kiểm tra abstraction, TTL, attempt count, cooldown, replacement/reset, delete, Redis unavailable mapping và ephemeral restart behavior.
- Unit/fake-SMTP test `SmtpMailService`: recipients/content/template, provider failure mapping, no real Gmail.
- Static/source leakage review: no raw OTP, invitation token, SMTP credential or Redis implementation object in logs/application port.
- Regression: `mvn -B -ntp test`, `mvn -B -ntp clean verify`.

Standard commands lấy từ `docs/DEVELOPMENT.md`:

```text
mvn -B -ntp test
mvn -B -ntp clean verify
```

## Rủi ro và blocker

- Redis/Gmail là external side effects; tests phải dùng Redis Testcontainer và fake SMTP, không dùng public internet.
- Redis restart có thể làm mất pending OTP; đây là hành vi được chấp nhận của ephemeral OTP state.
- Không có `.git` metadata trong archive; giữ nguyên ghi nhận từ các milestone trước, không coi là M5 implementation blocker.

## Generated docs / state

- Không đổi `docs/generated/db-schema.md` vì M5 không đổi schema.
- Không đổi `docs/generated/api-schema.md` vì M5 không tạo endpoint/contract.
- Cập nhật `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md` và `docs/INTEGRATION.md`/`docs/RELIABILITY.md` khi behavior/configuration đã được kiểm chứng.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | M5 preflight | Xác nhận M4 Gate PASS, đọc lại registry/source docs và khóa implementation choices từ M0 |
| 2026-09-17 | `REDIS-01..REDIS-03` | Tạo `RedisConfig`, `StringRedisTemplate`, `OtpStore`, `OtpVerificationState` và `RedisOtpStore`; Lua scripts bảo đảm replace/cooldown và attempt increment atomic; state dùng HMAC value + counter với TTL |
| 2026-09-17 | `OTP-01` | Tạo `OtpService` với `SecureRandom`, HMAC-SHA-256, constant-time comparison, TTL/cooldown/max-attempt config và error mapping; không tạo auth flow |
| 2026-09-17 | `MAIL-01..MAIL-04` | Tạo `MailService`, `MailConfig`, `SmtpMailService` và hai HTML template; SMTP credentials/timeout/STARTTLS lấy từ typed configuration; không log payload/credential |
| 2026-09-17 | M5 targeted verification | `OtpServiceTest` 6/6, `RedisOtpStoreIntegrationTest` 6/6 với Redis 7.4 Testcontainer, `SmtpMailServiceTest` 5/5 với fake SMTP/failure/leakage checks |
| 2026-09-17 | M5 runtime/scope verification | Compose config/live Redis healthy; `/data` là tmpfs, `appendonly=no`, không có explicit Redis volume; JavaMail/Redis types chỉ nằm trong adapter/config; không có M6/frontend/OTP persistence |
| 2026-09-17 | M5 regression verification | `mvn -B -ntp test` và `mvn -B -ntp clean verify` pass toàn bộ 58 tests, 0 failures, 0 errors, 0 skipped; jar repackage thành công |
| 2026-09-17 | M5 targeted final rerun | `mvn -B -ntp "-Dtest=OtpServiceTest,RedisOtpStoreIntegrationTest,SmtpMailServiceTest" test` exit code 0; 17/17 tests pass với Redis Testcontainer và fake SMTP |
| 2026-09-17 | M5 final verification rerun | `mvn -B -ntp test` và `mvn -B -ntp clean verify` đều exit code 0; `docker compose -f docker-compose.yml config --quiet` exit code 0 khi cấp process-only local placeholders; live Redis final check vẫn healthy |
| 2026-09-17 | M5 closeout | Cập nhật state/quality/development/integration/reliability docs; archive plan; không tạo active M6 plan |

## M5 Gate

Status: `PASS` — ngày `2026-09-17`

- [x] RedisOtpStore works against containerized Redis.
- [x] OTP state is ephemeral and raw OTP is never stored/logged.
- [x] TTL, attempts, cooldown, replacement and failure mapping are verified.
- [x] Gmail adapter is isolated behind `MailService`.
- [x] Fake SMTP tests pass without real credentials or Gmail calls.
- [x] Full regression and clean restart verification pass.

## Kết quả cuối cùng

M5 đã hoàn thành toàn bộ `REDIS-01..REDIS-03`, `OTP-01`, `MAIL-01..MAIL-04` và `EXT-AUTH-TEST`. Redis OTP state dùng `StringRedisTemplate` với namespace và TTL riêng cho state/cooldown; protected value là HMAC-SHA-256 và không lưu raw OTP. SMTP adapter chỉ được gọi qua `MailService`, dùng environment-backed Gmail settings và safe templates. Redis Testcontainer 6/6, fake SMTP/unit 5/5, OTP unit 6/6 và full regression 58/58 đều pass. Không tạo registration/login/auth flow, OTP entity/repository, API endpoint, frontend hoặc M6 plan.
