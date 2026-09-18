# KBase Core v1 – Active Slice: M8 Invitation Lifecycle

Status: `DONE` — hoàn tất ngày `2026-09-18`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M7_User_Project_Membership.md` (`M7 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Triển khai invitation lifecycle bằng Gmail MailService đã xây ở M5: create/list/resend/cancel/accept với secure invitation token riêng (không OTP), locking khi accept. M8 không triển khai folder/category/tag, MinIO/document, search, OpenAPI runtime hoặc frontend.

## Phạm vi M8

Bao gồm:

- `INV-01` — DTOs: `CreateInvitationRequest`, `AcceptInvitationRequest`, `InvitationResponse` (id, projectId, email, status, expiresAt, createdAt — không có token), `AcceptInvitationResponse` (projectId, membershipId, role, joinedAt).
- `INV-02` — Invitation token utility: SecureRandom 32-byte URL-safe token + SHA-256 hex hash; raw token chỉ nằm trong email link và request accept; PostgreSQL chỉ lưu `token_hash`; `InvitationProperties.acceptBaseUrl` mới để dựng invitation link.
- `INV-03` — `POST /api/v1/projects/{projectId}/invitations` (OWNER/ADMIN; member email → 409; PENDING trùng → 409; mail qua `MailService.sendProjectInvitation`; mail fail rollback) + `GET .../invitations` (OWNER/ADMIN, filter status + pagination).
- `INV-04` — Resend `POST .../invitations/{id}/resend` (chỉ PENDING; token mới thay hash cũ; reset expiresAt; gửi lại email) và Cancel `DELETE .../invitations/{id}` (PENDING → CANCELLED, không physical delete).
- `INV-05` — Accept `POST /api/v1/invitations/accept`: authenticated, PESSIMISTIC_WRITE theo tokenHash, PENDING → không expired → email khớp → chưa là member → tạo `ProjectMember(MEMBER)` + `ACCEPTED` + `acceptedAt`; expired có thể set status EXPIRED trong cùng transaction.
- `INV-06` — Unit + integration tests (PostgreSQL, gồm concurrency accept test).

Không bao gồm:

- Folder/category/tag (M9), MinIO/document (M10/M11), search (M12), OpenAPI (M13), frontend;
- Auto-join sau registration/OTP verify, ownership transfer, invitation bằng OTP;
- Đổi auth/OTP/Redis/Gmail behavior (chỉ reuse `MailService.sendProjectInvitation` như thiết kế M5);
- Đổi membership/project APIs của M7.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`, `docs/SECURITY.md`, `docs/TESTING.md`, `docs/API_CONVENTIONS.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 REST API Specification.md` (SD-04, section 19)
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` (SD-08, section 35–40)
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md` (§29 authenticated accept)
- `docs/design-docs/KBase - Core v1 Testing Strategy.md` (#46 khu vực invitation, #51 invitation tests)
- `.harness/source-doc-registry.json`

## Quy tắc business không được tái diễn giải

- OWNER/ADMIN tạo invitation; MEMBER bị từ chối (`requireOwner` của `ProjectAuthorizationService`).
- Invitation dùng secure token riêng; **không dùng OTP**; raw token chỉ trong email link (`{acceptBaseUrl}?token=<raw>`) và request accept.
- PostgreSQL chỉ lưu `tokenHash` (SHA-256 hex); không log raw token/hash.
- Một invitation PENDING cho mỗi `project + email` (`uq_project_pending_invitation_email`).
- Không invite user đã là member (lookup user theo email rồi check membership).
- Resend: chỉ PENDING; token mới thay hash cũ (token cũ vô hiệu); reset expiresAt; gửi lại email; member-already → 409.
- Cancel: `PENDING → CANCELLED`, không physical delete; token accept với CANCELLED → `INVITATION_NOT_PENDING`.
- Accept: authenticated (JWT filter đảm bảo ACTIVE + verified); PESSIMISTIC_WRITE; PENDING → chưa expired → email khớp → chưa là member → tạo `ProjectMember(MEMBER)` + `ACCEPTED` + `acceptedAt` trong cùng transaction.
- Flow user mới: register → verify OTP → login → accept token.
- Expiration baseline 72h configurable (`kbase.invitation.expiration`).

## Thiết kế triển khai đã khóa

- Token: 32 byte `SecureRandom` → Base64 URL-safe (43 ký tự); hash = SHA-256 hex (64 ký tự, khớp cột `token_hash`); utility `invitation.service.InvitationTokens` chung chung, không chứa domain logic.
- `InvitationProperties` thêm `acceptBaseUrl` (env `KBASE_INVITATION_ACCEPT_URL`, default `http://localhost:3000/invitations/accept`); `application.yml` + `.env.example` cập nhật.
- `createInvitation` @Transactional: mail gửi trong transaction; mail fail → exception propagate → rollback (không để lại invitation PENDING "lỡ"). Edge case mail-ok-commit-fail là known risk đã ghi trong SD-08 (outbox deferred).
- Accept lấy user từ `principal.getUserId()` (JWT filter đã load DB, đảm bảo ACTIVE + verified); email so sánh sau normalize.
- Accept concurrency: `findByTokenHashForUpdate` (PESSIMISTIC_WRITE, đã có từ M3) serialize hai accept; transaction thứ hai thấy status != PENDING → `INVITATION_NOT_PENDING`; test hai thread thật trên PostgreSQL.
- Response create/list/resend không bao giờ trả raw token; token hash không xuất hiện trong DTO.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `INV-01` | `TODO` | DTOs đúng contract, không token trong response |
| `INV-02` | `TODO` | Token utility + acceptBaseUrl config; hash-only trong DB |
| `INV-03` | `TODO` | Create/list theo OWNER/ADMIN; 409 member/pending; mail được gọi |
| `INV-04` | `TODO` | Resend token mới + reset expiry; cancel CANCELLED |
| `INV-05` | `TODO` | Accept đúng flow + PESSIMISTIC_WRITE + ACCEPTED/acceptedAt |
| `INV-06` | `TODO` | Unit + integration (gồm concurrency 2 thread) pass |

## Verification path

- Unit: `InvitationServiceTest` — create (OWNER, member conflict, pending conflict, mail fail propagate), list, resend (replace hash/reset expiry/non-pending), cancel, accept (success/mismatch/expired/not-pending/not-found).
- Integration: `InvitationLifecycleIntegrationTest` trên PostgreSQL Testcontainer + MockMvc: create 201 + mail capture + không token trong body; MEMBER 403; ADMIN override 201; member/duplicate 409; resend token cũ vô hiệu + reset expiry; cancel → CANCELLED; accept success → 1 MEMBER + ACCEPTED + acceptedAt; email mismatch 403; expired 409; cancelled/accepted 409; mail fail 503 + rollback; concurrency 2 thread chỉ 1 accept thắng.
- Regression: `mvn -B -ntp test`, `mvn -B -ntp clean verify` (130 baseline + M8).
- Static review: không OTP trong invitation, không raw token/hash trong log/DTO, không auto-join.

Standard commands từ `docs/DEVELOPMENT.md`: `mvn -B -ntp test`, `mvn -B -ntp clean verify`.

## Rủi ro và blocker

- Không có blocker từ M7 (Gate PASS, Docker available).
- Concurrency test dùng 2 thread + CountDownLatch gọi service thật trên PostgreSQL; có thể flaky nếu lock khôngserialize — design PESSIMISTIC_WRITE bảo đảm tuần tự.
- Mail trong test dùng mock `MailService`; không gửi Gmail thật.

## Generated docs / state

- Không đổi schema → `docs/generated/db-schema.md` không cần sinh lại.
- API contract thay đổi (5 endpoints mới) → cập nhật `docs/generated/api-schema.md`.
- Cập nhật `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md` sau khi verify.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-18 | M8 preflight | Xác nhận M7 Gate PASS không blocker; đọc SD-04 §19, SD-08 §35–40; kiểm tra entity/repository M3 đã có đủ query (findByTokenHashForUpdate PESSIMISTIC_WRITE); baseline `mvn test` 130/130 pass |
| 2026-09-18 | `INV-01..02` | DTOs (không token trong response), `InvitationTokens` (SecureRandom 32-byte + SHA-256 hex), `InvitationProperties.acceptBaseUrl` + application.yml + `.env.example` |
| 2026-09-18 | `INV-03..05` | `InvitationService` (create/list/resend/cancel/accept) + `InvitationController` + `InvitationAcceptController`; create mail-in-transaction rollback; accept PESSIMISTIC_WRITE |
| 2026-09-18 | Unit tests | `InvitationServiceTest` 8/8: create hash-only + mail link, member/pending conflicts, mail fail propagate, resend replace+reset, cancel không physical delete, accept full error matrix |
| 2026-09-18 | Integration tests | `InvitationLifecycleIntegrationTest` 5/5 trên PostgreSQL 17 Testcontainer: 2 vòng fix — seed expired invitation phải backdate cả `created_at` qua `JdbcTemplate` (check constraint `ck_project_invitations_expiration` + `created_at` updatable=false); concurrency 2 thread chỉ 1 accept thắng với `INVITATION_NOT_PENDING` |
| 2026-09-18 | Final regression | `mvn -B -ntp test` 143/143 pass; `mvn -B -ntp clean verify` 143/143 pass, jar repackage |
| 2026-09-18 | Static review + closeout | Chỉ javadoc ghi "never uses OTP"; không log/expose raw token/hash; không physical delete; không đổi auth/OTP/Redis/Gmail; cập nhật api-schema/state/quality/development; archive plan |

## Verification path và kết quả (chốt)

- Unit 8/8: `InvitationServiceTest`.
- Integration 5/5 (`InvitationLifecycleIntegrationTest`, PostgreSQL 17 + Redis Testcontainers, real filter chain, mail mock): OWNER tạo 201 PENDING + mail URL chứa raw token + body/DB không lộ token/hash; MEMBER 403; ADMIN override 201; member email 409 `PROJECT_MEMBER_ALREADY_EXISTS`; duplicate PENDING 409 `INVITATION_ALREADY_PENDING`; resend 200 → token cũ accept 404, token mới accept 200, expiresAt reset; cancel 204 → CANCELLED còn trong DB, token cancelled accept 409 `INVITATION_NOT_PENDING`; expired accept 409 `INVITATION_EXPIRED`; email mismatch 403 `INVITATION_EMAIL_MISMATCH`; accept success tạo đúng 1 MEMBER + ACCEPTED + acceptedAt, double accept 409; mail fail 503 `EMAIL_SERVICE_UNAVAILABLE` + invitation không được persist (rollback); concurrency 2 thread thật → 1 success + 1 `INVITATION_NOT_PENDING` + đúng 1 membership.
- Regression: `mvn -B -ntp test` 143/143; `mvn -B -ntp clean verify` 143/143 (exit 0).

## M8 Gate

Status: `PASS` — ngày `2026-09-18`

- [x] Invitation lifecycle complete (create/list/resend/cancel/accept).
- [x] Invitation still uses link/token, never OTP.
- [x] Gmail MailService reused correctly (mail captured cho create/resend; fail → rollback).
- [x] Accept concurrency safe (PESSIMISTIC_WRITE; 2 thread chỉ 1 thắng).
- [x] Token hash-only trong PostgreSQL; raw token chỉ trong email link và accept request.

## Kết quả cuối cùng

M8 đã hoàn thành toàn bộ `INV-01..INV-06`. Invitation dùng secure token riêng (32-byte SecureRandom, SHA-256 hash trong `project_invitations.token_hash`, expiry 72h configurable, accept-base-url cấu hình được); resend thay hash và reset expiry làm token cũ vô hiệu; cancel chuyển CANCELLED không xóa; accept authenticated chạy trong transaction với pessimistic lock, tạo `ProjectMember(MEMBER)` + `ACCEPTED` + `acceptedAt`, đúng một membership kể cả khi hai request chạy song song. Full suite 143/143 pass. Không chạm folder/category/tag (M9), MinIO (M10), document (M11), search (M12), OpenAPI (M13) hay frontend.
