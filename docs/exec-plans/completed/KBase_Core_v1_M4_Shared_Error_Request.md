# KBase Core v1 – Active Slice: M4 Shared Error / Request Infrastructure

Status: `DONE` — hoàn tất ngày `2026-09-17`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M3_JPA.md` (`M3 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Thiết lập error contract REST ổn định và request correlation baseline trước khi triển khai các feature endpoint. M4 chỉ triển khai shared error/request infrastructure; không mở M5.

## Phạm vi M4

Bao gồm:

- `ERR-01` — centralized `ErrorCode` với status và safe default message.
- `ERR-02` — `KBaseException` hierarchy.
- `ERR-03` — `ApiErrorResponse`.
- `ERR-04` — `RequestIdFilter` và MDC.
- `ERR-05` — `GlobalExceptionHandler` cho application, validation, request parsing, multipart và fallback errors.
- `ERR-06` — PostgreSQL constraint-name translator với fallback không đoán business error.
- `ERR-07` — unit, MockMvc contract và PostgreSQL Testcontainers verification.

Không bao gồm:

- M5 Redis OTP, Gmail SMTP hoặc bất kỳ infrastructure adapter nào.
- Spring Security handlers/JWT, controller/service business feature hoặc OpenAPI runtime generation.
- Frontend.
- Thay đổi Flyway schema/migration, entity/repository hoặc database constraint.
- Error code/feature ngoài Core v1 source documents.

## Gate predecessor

- M0, M1, M2 và M3 Gate đã `PASS`.
- Không có blocker từ M3; schema constraint names được đối chiếu với V1/V2 migrations trước khi viết translator.
- Archive không có `.git` metadata; đây là giới hạn harness đã ghi nhận, không phải blocker M4.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`, `docs/API_CONVENTIONS.md`, `docs/TESTING.md`
- `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/INTEGRATION.md`, `docs/DEPLOYMENT.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 REST API Specification.md`
- `docs/design-docs/KBase - Core v1 Exception Handling Design.md`
- `docs/design-docs/KBase - Core v1 Spring Boot Application Architecture.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `.harness/source-doc-registry.json` (SD-01, SD-04, SD-05, SD-10, SD-12, SD-13)

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `ERR-01` | `DONE` | `ErrorCode` tập trung, có HTTP status và safe default message; gồm đầy đủ error code Core v1 và OTP/email |
| `ERR-02` | `DONE` | `KBaseException`, `BusinessException`, `ResourceNotFoundException`, `ForbiddenOperationException`, `InfrastructureException` |
| `ERR-03` | `DONE` | `ApiErrorResponse` có timestamp/status/code/message/path/requestId và field errors tùy validation |
| `ERR-04` | `DONE` | `RequestIdFilter` tạo/reuse UUID hợp lệ, gắn header/attribute/MDC và clear MDC trong `finally` |
| `ERR-05` | `DONE` | Global handler chuẩn hóa application, validation, parsing, parameter, multipart, persistence và fallback errors |
| `ERR-06` | `DONE` | Constraint names thật của M2 được map; constraint không nhận diện được fallback `INTERNAL_SERVER_ERROR` |
| `ERR-07` | `DONE` | Unit/MockMvc/PostgreSQL Testcontainers và leakage checks pass |

M4 không tạo controller/service feature, security handler, Redis OTP, Gmail SMTP, MinIO adapter, OpenAPI runtime hoặc frontend.

## Thiết kế triển khai đã khóa

- `ErrorCode` giữ HTTP status và safe English default message; exception không chứa `ResponseEntity`.
- `ApiErrorResponse` dùng `Instant`, status, stable code, safe message, request path, request ID và field errors nullable/omitted ngoài validation.
- Request ID dùng UUID server-generated; chỉ reuse incoming `X-Request-Id` nếu là UUID hợp lệ. ID được đặt vào response header, servlet request attribute và MDC, rồi dọn trong `finally`.
- Handler lấy request ID từ filter/MDC và không tạo ID thứ hai trong request bình thường.
- Constraint translator chỉ nhận diện các constraint names đã có mapping business trong M2/Exception Handling Design; unknown constraint trả `INTERNAL_SERVER_ERROR` và không đoán.
- Không trả stack trace, SQL, exception class, credential, password/hash, JWT, refresh/invitation token, OTP hoặc storage detail.

## Constraint mapping được hỗ trợ

| Constraint thật | ErrorCode | HTTP |
|---|---|---:|
| `uq_users_email` | `EMAIL_ALREADY_EXISTS` | 409 |
| `uq_project_members_project_user` | `PROJECT_MEMBER_ALREADY_EXISTS` | 409 |
| `uq_project_members_single_owner` | `PROJECT_OWNER_ALREADY_EXISTS` | 409 |
| `uq_project_pending_invitation_email` | `INVITATION_ALREADY_PENDING` | 409 |
| `uq_folders_root_name`, `uq_folders_child_name` | `FOLDER_NAME_ALREADY_EXISTS` | 409 |
| `uq_categories_project_name` | `CATEGORY_NAME_ALREADY_EXISTS` | 409 |
| `uq_tags_project_name` | `TAG_NAME_ALREADY_EXISTS` | 409 |

Các constraint khác, bao gồm `uq_project_invitations_token_hash`, check constraint và foreign key nếu không có operation context, fallback về `INTERNAL_SERVER_ERROR` theo thiết kế.

## Verification path

Required before closing M4:

- Unit test mọi exception category chính và ErrorCode status mapping.
- MockMvc contract tests cho KBase exception, validation, malformed JSON, invalid UUID/parameter, request ID và unknown exception.
- Kiểm tra response header/body request ID nhất quán và MDC được clear sau request.
- PostgreSQL 17 Testcontainer chạy Flyway V1–V3 từ database rỗng; trigger các unique constraints thật và translate đúng mapping.
- Unknown constraint fallback và response leakage checks.
- Regression: `mvn -B -ntp test`, `mvn -B -ntp clean verify`.

Standard commands từ `docs/DEVELOPMENT.md`:

```text
mvn -B -ntp test
mvn -B -ntp clean verify
```

## Generated docs / state

- `docs/generated/api-schema.md` phải đồng bộ error schema placeholder với `ApiErrorResponse`; chưa có OpenAPI runtime generator nên được cập nhật thủ công và ghi rõ trạng thái.
- `docs/generated/db-schema.md` không thay đổi vì M4 không thay schema.
- Cập nhật `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md` và kế hoạch này trước khi đóng gate.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-17 | M4 preflight | Xác nhận M3 Gate PASS; đọc lại registry, source docs, current state và constraint migrations |
| 2026-09-17 | `ERR-01..ERR-06` | Tạo shared error contract, exception hierarchy, request ID/MDC filter, centralized handler và constraint translator; không thêm M5 adapter |
| 2026-09-17 | `ERR-07` targeted verification | `mvn -B -ntp "-Dtest=ConstraintViolationTranslationIntegrationTest,GlobalExceptionHandlerTest,ConstraintViolationTranslatorTest" test` pass 16/16 |
| 2026-09-17 | M4 regression verification | `mvn -B -ntp test` pass 41/41; `mvn -B -ntp clean verify` pass 41/41 và Spring Boot jar repackage |
| 2026-09-17 | M4 scope/leakage review | Pass; không có M5 adapter, frontend, OTP persistence, secret literal hoặc API response leakage; `docs/generated/db-schema.md` không đổi |
| 2026-09-17 | M4 closeout | Cập nhật API/state/quality/development docs và archive plan; không tạo active M5 plan |

## M4 Gate

Status: `PASS` — ngày `2026-09-17`

- [x] Error contract stable.
- [x] OTP/email error codes available; Redis OTP/Gmail SMTP chưa triển khai trong M4.
- [x] Request IDs work through header/body/MDC lifecycle.
- [x] Known PostgreSQL constraint mapping works; unknown constraint falls back to internal error.
- [x] Required leakage and regression tests pass.

## Kết quả cuối cùng

M4 đã hoàn thành toàn bộ `ERR-01..ERR-07`. Backend hiện có error contract tập trung với `ApiErrorResponse`, exception hierarchy, request correlation qua `X-Request-Id` và MDC, xử lý validation/request/persistence/fallback errors, cùng constraint translation dựa trên tên thật của PostgreSQL constraints từ M2. Unit và MockMvc tests (16/16), PostgreSQL Testcontainers constraint tests (2/2) và full regression (`mvn test`, `mvn clean verify`, 41/41) đều pass. Không triển khai Redis OTP, Gmail SMTP, security handler, feature API hoặc frontend; M5 chưa bắt đầu.
