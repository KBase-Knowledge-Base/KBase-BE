# KBase Core v1 – Active Slice: M6 Spring Security + Authentication

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M5_Redis_OTP_Gmail_Mail.md` (`M5 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Triển khai registration → verify OTP → login → refresh → logout với Spring Security + JWT: security filter chain, JWT access token, PostgreSQL refresh session, email verification orchestration và AuthController endpoints. M6 không triển khai user/project/membership/invitation API, MinIO, frontend hoặc bất kỳ task M7 nào.

## Phạm vi M6

Bao gồm:

- `SEC-01` — `PasswordEncoder` (BCrypt) bean; `RefreshCookieProperties` bổ sung `path`; JWT/cookie/CORS/OTP properties bind từ config/environment.
- `SEC-02` — `security.jwt.JwtService`: tạo/parse/verify access JWT HS256 (sub=userId, systemRole, iat, exp, jti), TTL 15m configurable; không chứa project role.
- `SEC-03` — `CustomUserPrincipal` (UserDetails, userId/email/systemRole/status/emailVerified), `CustomUserDetailsService` (load theo userId từ DB), `CurrentUserService` (adapter SecurityContext).
- `SEC-04` — `JwtAuthenticationFilter`: Bearer → validate → load current User từ DB → reject DISABLED/unverified → SecurityContext; token failure ghi request attribute cho entry point; không check project membership.
- `SEC-05` — `RestAuthenticationEntryPoint` + `RestAccessDeniedHandler` + `RestSecurityErrorWriter` trả `ApiErrorResponse` chuẩn với `X-Request-Id`.
- `SEC-06` — `SecurityConfig` (servlet-only) + `SecurityBeansConfiguration` (mọi context): stateless, 6 public auth endpoints, `/api/v1/admin/**` hasRole ADMIN, còn lại authenticated; Swagger/OpenAPI paths theo `OpenApiProperties`; CORS từ `CorsProperties` (chặn wildcard + credentials).
- `AUTH-01` — `RefreshSessionService`: opaque 32-byte SecureRandom URL-safe token, SHA-256 hex hash trong PostgreSQL, TTL 7d configurable, validate (tồn tại → chưa revoke → chưa hết hạn), revokeIfPresent, revokeAllForUser; raw token trả đúng một lần.
- `AUTH-02` — `EmailVerificationService`: issueOtp/verify/resend qua `OtpService`/`OtpStore` + `MailService`, set `users.email_verified_at`, best-effort cleanup khi mail fail sau Redis write.
- `AUTH-03` — `POST /api/v1/auth/register`: normalize email, password 8..64, BCrypt, USER/ACTIVE/emailVerifiedAt=null, saveAndFlush + race translate `EMAIL_ALREADY_EXISTS`, issue OTP, trả `emailVerified=false`, không auto-login, @Transactional.
- `AUTH-04` — `POST /api/v1/auth/verify-email`: USER_NOT_FOUND/EMAIL_ALREADY_VERIFIED/OTP errors; set timestamp, persist, best-effort delete Redis state.
- `AUTH-05` — `POST /api/v1/auth/resend-verification-otp`: cooldown atomically qua `OtpService.resendVerificationOtp`, replace OTP cũ, reset attempts/TTL, gửi Gmail.
- `AUTH-06` — `POST /api/v1/auth/login`: password + ACTIVE + verified; access JWT + refresh session + Set-Cookie HttpOnly; `INVALID_CREDENTIALS` generic; 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_DISABLED`.
- `AUTH-07` — `POST /api/v1/auth/refresh`: cookie-based (`@CookieValue` tên theo property), PostgreSQL session, không rotation, `REFRESH_TOKEN_MISSING/INVALID_REFRESH_TOKEN/REFRESH_TOKEN_EXPIRED/REFRESH_SESSION_REVOKED` + account-state 403.
- `AUTH-08` — `POST /api/v1/auth/logout`: revokeIfPresent, clear cookie (Max-Age=0), effectively idempotent, 204.
- `AUTH-SEC-TEST` — 32 unit tests + 14 MockMvc/real-filter-chain integration tests trên PostgreSQL/Redis Testcontainers.

Không bao gồm (đúng scope):

- `GET/PATCH /users/me`, admin user APIs, project/membership/invitation/folder/category/tag/document APIs (M7+);
- password reset, OTP login, MFA/2FA, refresh-token rotation, access-token blacklist;
- ProjectAuthorizationService/DocumentAuthorizationService (M7), project role trong JWT hoặc SecurityConfig;
- Redis refresh session, OTP entity/repository, database migration;
- MinIO, frontend, gửi Gmail thật trong automated test.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`, `docs/SECURITY.md`, `docs/TESTING.md`, `docs/API_CONVENTIONS.md`, `docs/INTEGRATION.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md` (SD-07)
- `docs/design-docs/KBase - Core v1 REST API Specification.md` (SD-04, section 5, 6, 13)
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` (SD-08, section 6–15)
- `docs/design-docs/KBase - Core v1 Exception Handling Design.md` (SD-10)
- `docs/design-docs/KBase - Core v1 Testing Strategy.md` (SD-12, #46–#48)
- `.harness/source-doc-registry.json` (SD-01, SD-04, SD-07, SD-08, SD-10, SD-12, SD-13)

## Thiết kế triển khai đã khóa

- Access JWT: HS256 với secret từ `KBASE_JWT_SECRET` (JJWT 0.13.0, yêu cầu ≥256 bit); claims `sub` = userId UUID, `systemRole`, `iat`, `exp`, `jti`; TTL 15m từ `JwtProperties`; JwtParser dùng chung `Clock` injectable để test deterministic.
- `JwtAuthenticationFilter` chỉ authenticate khi có `Authorization: Bearer`; luôn load User từ DB theo `sub`; reject DISABLED (403 `ACCOUNT_DISABLED`) và unverified (403 `EMAIL_NOT_VERIFIED`) bằng JSON trực tiếp; token expired/invalid/tampered → anonymous + request attribute, entry point trả `ACCESS_TOKEN_EXPIRED`/`INVALID_ACCESS_TOKEN` trên protected endpoint; DB là source of truth cho systemRole.
- Refresh token: opaque 32+ byte `SecureRandom` URL-safe; DB lưu SHA-256 hex hash; raw token chỉ trong cookie `kbase_refresh_token` (HttpOnly, Secure theo profile, SameSite=Lax, Path=/api/v1/auth, Max-Age = refresh TTL); không rotation trong Core v1.
- Security endpoints: permitAll cho 6 auth POST endpoints; `/api/v1/admin/**` hasRole ADMIN; còn lại authenticated; Swagger/OpenAPI paths permitAll khi `kbase.openapi.enabled=true`; `/error` permitAll; session STATELESS; CSRF off (Bearer auth + SameSite=Lax path-scoped cookie).
- PasswordEncoder + JwtService nằm trong `SecurityBeansConfiguration` không conditional để mọi context (kể cả web none của test) có bean; SecurityConfig chỉ giữ filter chain + CORS và có `@ConditionalOnWebApplication(SERVLET)`.
- Error contract: security handlers/filter dùng `ApiErrorResponse` + `X-Request-Id`; login dùng generic 401 cho unknown email/wrong password.
- Không log password, hash, JWT, raw refresh token, OTP, cookie value; `LoginResult`/`CreatedRefreshSession`/`GeneratedOtp` toString redacted.
- Không đổi Flyway migration/schema.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `SEC-01` | `DONE` | BCrypt bean dùng mọi context; cookie path property bind; binding test pass |
| `SEC-02` | `DONE` | JwtService claims đúng; expired/tampered/wrong-secret bị reject; ≥256-bit secret enforced |
| `SEC-03` | `DONE` | Principal từ DB mỗi request; không giữ password; authorities = ROLE_ADMIN/ROLE_USER |
| `SEC-04` | `DONE` | Bearer flow; DISABLED/unverified bị chặn ngay; failure codes distinguable |
| `SEC-05` | `DONE` | 401/403 JSON chuẩn + requestId |
| `SEC-06` | `DONE` | Public/admin/authenticated đúng; stateless; CORS typed; Swagger theo flag |
| `AUTH-01` | `DONE` | Create/validate/revoke; hash-only trong DB; raw token không log |
| `AUTH-02` | `DONE` | issue/verify/resend; set `email_verified_at`; cleanup khi mail fail |
| `AUTH-03` | `DONE` | 201 unverified; OTP issued; không auto-login; 409 duplicate + race |
| `AUTH-04` | `DONE` | Verify success/error đúng contract; timestamp persisted |
| `AUTH-05` | `DONE` | Cooldown 429; replace OTP; attempts reset; OTP cũ invalid |
| `AUTH-06` | `DONE` | 200 + cookie + JWT; generic 401; 403 unverified/disabled |
| `AUTH-07` | `DONE` | Refresh qua PostgreSQL session; full error contract |
| `AUTH-08` | `DONE` | Revoke + clear cookie + idempotent 204 |
| `AUTH-SEC-TEST` | `DONE` | 14 integration + 32 unit pass; Testcontainers + real filter chain |

## Verification path và kết quả

- Unit: `JwtServiceTest` 7/7, `RefreshSessionServiceTest` 6/6, `EmailVerificationServiceTest` 9/9, `AuthServiceTest` 10/10.
- Integration/security: `AuthenticationSecurityIntegrationTest` 14/14 trên PostgreSQL 17 + Redis 7.4 Testcontainers, real SecurityFilterChain, mail mock: register→verify→login→refresh→logout journey, error contracts, JWT valid/expired/tampered/garbage, disabled account (access cũ + refresh + login đều bị chặn), ADMIN route (USER 403 / ADMIN 200 / anonymous 401), refresh hash-only trong DB, cookie attributes, response body không chứa refresh token, JWT claims chỉ 5 keys.
- Regression: `mvn -B -ntp test` 104/104 pass (exit 0); `mvn -B -ntp clean verify` 104/104 pass (exit 0, Spring Boot jar repackage).
- Static leakage/boundary review: không log nhạy cảm trong security/auth code; không OWNER/MEMBER/ProjectMember trong security package; Redis/JavaMail types chỉ trong adapter/config; principal không giữ password.
- `OTP_SERVICE_UNAVAILABLE` mapping: đã verify ở adapter level (`RedisOtpStoreIntegrationTest` 6/6, M5) và unit level (`EmailVerificationServiceTest` propagate `OtpServiceUnavailableException`); không tạo context thứ hai với Redis chết trong M6 integration (chi phí/chậm, mapping không đổi).

Standard commands lấy từ `docs/DEVELOPMENT.md`:

```text
mvn -B -ntp test
mvn -B -ntp clean verify
```

## Dependency mới

- `spring-boot-starter-webmvc-test` (test scope): Boot 4 tách `@AutoConfigureMockMvc`/MockMvc auto-config khỏi `spring-boot-test-autoconfigure` thành module riêng; cần cho API/security contract tests theo `docs/TESTING.md`.
- `spring-boot-starter-security-test` (test scope): auto-config wire security filter chain vào MockMvc (kèm `spring-security-test`); cần cho security filter chain integration tests.
- Cả hai là test-only starter chính thức của Boot 4.1.1, không thêm runtime dependency; được chứng minh trong kế hoạch này theo `docs/BACKEND.md`.

## Rủi ro và blocker

- Không còn blocker. Docker daemon đã có sẵn cho Testcontainers.
- JWT secret placeholder trong các test cũ (M2/M3/M4 + context smoke) đã nâng từ 23 lên ≥32 byte vì JwtService bean enforce RFC 7518 HS256 ≥256 bit; đây là test-only placeholder, production secret vẫn từ environment.
- Không có Git metadata trong archive; bằng chứng giữ trong plan theo các milestone trước.

## Generated docs / state

- `docs/generated/db-schema.md` không đổi vì M6 không đổi schema.
- `docs/generated/api-schema.md` đã đồng bộ thủ công với 6 auth endpoints + security error codes + UserResponse/SystemRole/UserStatus schemas từ source code đã verify; vẫn khai báo trạng thái chưa-sinh-từ-runtime (M13 OpenAPI).
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`, `docs/INTEGRATION.md` đã cập nhật.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | M6 preflight | Xác nhận M5 Gate PASS; đọc SD-04/07/08/10/12 + harness docs; baseline `mvn test` 58/58 pass |
| 2026-09-17 | `SEC-01..SEC-03` | `SecurityBeansConfiguration` (BCrypt + JwtService), `JwtService` (HS256, clock injectable, parser clock), principal/details/current-user service; `RefreshCookieProperties.path` + config binding |
| 2026-09-17 | `SEC-04..SEC-06` | `JwtAuthenticationFilter` (DB user lookup, failure attribute), `RestSecurityErrorWriter`/EntryPoint/AccessDeniedHandler (Jackson 3), `SecurityConfig` stateless + public/admin/authenticated + CORS |
| 2026-09-17 | `AUTH-01..AUTH-02` | `RefreshSessionService` (SecureRandom + SHA-256 + validate/revoke), `EmailVerificationService` (issue/verify/resend + cleanup) |
| 2026-09-17 | `AUTH-03..AUTH-08` | Auth DTOs, `AuthService` (register/login/refresh/logout), `AuthController` (cookie set/clear, @CookieValue) |
| 2026-09-17 | Unit tests | `JwtServiceTest` 7/7, `RefreshSessionServiceTest` 6/6, `EmailVerificationServiceTest` 9/9, `AuthServiceTest` 10/10 |
| 2026-09-17 | Integration tests | Thêm `spring-boot-starter-webmvc-test` + `spring-boot-starter-security-test` (test scope, Boot 4 modularization); `AuthenticationSecurityIntegrationTest` 14/14 sau 2 vòng fix (helper login riêng cho admin, captor atLeastOnce) |
| 2026-09-17 | Regression fixes | Tách PasswordEncoder/JwtService khỏi SecurityConfig conditional (fix M2/M3/M4 context tests); smoke test chuyển sang datasource never-connected; JWT placeholder secret ≥256 bit trong tests cũ |
| 2026-09-17 | Final regression | `mvn -B -ntp test` 104/104 pass; `mvn -B -ntp clean verify` 104/104 pass, jar repackage; static scope/leakage/boundary review pass |
| 2026-09-17 | M6 closeout | Cập nhật api-schema/CURRENT_STATE/QUALITY_SCORE/DEVELOPMENT/INTEGRATION; archive plan M6 |

## M6 Gate

Status: `PASS` — ngày `2026-09-17`

- [x] Registration requires OTP verification (register issue OTP; login/refresh/access đều yêu cầu verified).
- [x] Login blocked until verified (403 EMAIL_NOT_VERIFIED, test pass).
- [x] Redis contains only short-lived OTP state (OtpStore TTL; không refresh data trong Redis).
- [x] Refresh sessions remain PostgreSQL (hash-only, revoke/expiry validated; test assert).
- [x] Gmail SMTP is used through MailService (mock verify; không Gmail thật trong CI).
- [x] JWT security tests pass (valid/expired/tampered/garbage + disabled-account enforcement, 104/104).

## Kết quả cuối cùng

M6 đã hoàn thành toàn bộ `SEC-01..SEC-06`, `AUTH-01..AUTH-08` và `AUTH-SEC-TEST`. Registration tạo account USER/ACTIVE chưa verify và phát OTP qua `OtpService`/`MailService`; verify set `email_verified_at`; login yêu cầu password + ACTIVE + verified và trả access JWT + refresh cookie HttpOnly; refresh/logout vận hành trên PostgreSQL `refresh_sessions` với SHA-256 hash; `JwtAuthenticationFilter` load user từ DB mỗi request nên DISABLED có hiệu lực tức thì; `/api/v1/admin/**` chỉ cho ADMIN; 401/403 theo security design qua `ApiErrorResponse`. Full suite 104 tests pass. Không tạo feature API M7, MinIO, frontend, refresh rotation hay OTP login/MFA/password reset.
