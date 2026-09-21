# M16 – Post-Audit Fixes (Non-Blocking Issues)

## Mục tiêu

Khắc phục các finding đã được xác nhận trong Full Codebase Audit ngày 2026-09-19 (0 BLOCKER / 0 HIGH / 1 MEDIUM / 8 LOW / 12 INFO) mà có thể sửa an toàn mà không thay đổi API contract hay business rule; phản hồi lại (không sửa) các finding cần quyết định product hoặc phase mới.

Phê duyệt: yêu cầu trực tiếp của owner trong phiên 2026-09-19 sau khi audit report được review. Đây là maintenance slice sau freeze, không mở AI/RAG/frontend.

## Phạm vi

In scope (fix trong slice này):

| ID | Fix | Files chính |
|---|---|---|
| M-01 | Persist `EXPIRED` khi accept invitation hết hạn: dedicated exception + `@Transactional(noRollbackFor=...)` giữ đúng design §40 ("set status inside the same transaction before returning error"); regression test assert DB status + khả năng tạo invitation mới cho email đó | `InvitationService`, `InvitationExpiredException` (mới), `InvitationLifecycleIntegrationTest` |
| L-01 | Batch upload: mỗi storage key chỉ được compensation-delete đúng 1 lần; unit test verify call count | `DocumentService`, `DocumentServiceTest` |
| L-03 | Runtime OpenAPI description của search 400 khớp code thực tế (`VALIDATION_ERROR` cho unknown sort) | `DocumentController` (api-schema.md đã đúng, không cần tái sinh) |
| L-04 | Xóa `JwtProperties.algorithm` (unused knob) + javadoc ghi rõ trạng thái production-caller của `findAllByStatusAndExpiresAtBefore` | `JwtProperties`, `application.yml`, `ConfigurationPropertiesBindingTest`, `ProjectInvitationRepository` |
| L-05 | Escape LIKE wildcard (`%`, `_`, `\`) cho `q` ở document search, my-projects, tags list, admin users — search trở thành literal matching | `DocumentSpecification`, `ProjectService`, `ProjectMemberRepository`, `TagService`, `TagRepository`, `UserSpecification`, `DocumentSearchIntegrationTest` |
| L-06 | Folder move: pessimistic lock cả moved folder + target parent theo UUID order (chống deadlock) trước cycle walk; concurrency test 2 thread ngược chiều → đúng 1 thắng | `FolderRepository`, `FolderService`, `OrganizationIntegrationTest` |
| L-07 | SMTP failure log sanitized: chỉ exception class name, không message/cause/payload | `SmtpMailService`, `SmtpMailServiceTest` |
| L-08 | Repo hygiene: xóa file rỗng `Trạng` (tracked), cập nhật `index.md` root (stale M2), xóa `KBASE_MAIL_TEST_ENABLED` khỏi `.env.example` | root files |

Out of scope (không sửa, phản hồi lại owner):

- **L-02** — PATCH document không clear được folderId/categoryId (null = keep): cần product decision về JSON null semantics trước khi đổi contract; không tự ý đổi API.
- **I-01..I-12** — known limitations/accepted trade-offs (JWT non-revocation, storage-first failure windows, Redis ephemeral, email enumeration qua documented codes, SMTP/Redis trong TX, CSRF/SameSite, CI, Gmail smoke, generated-docs generators, health/metrics, Range MP4-only, filter write trên public endpoint cho disabled principal).
- **Dead repository methods** (`findAllByStatusAndExpiresAtBefore`, `existsSiblingWithNameExcluding`, `existsByParentId`, `findProjectsForUser`, `deleteByProjectIdAndUserId`): đang là một phần của M3 repository regression surface (`JpaMappingRepositoryIntegrationTest`); xóa làm giảm coverage đã verify — giữ nguyên, chỉ thêm javadoc cho method gắn với future expiry job.

## Tài liệu áp dụng

`docs/BACKEND.md`, `docs/DATABASE.md` (không đổi schema), `docs/API_CONVENTIONS.md`, `docs/SECURITY.md`, `docs/TESTING.md`, `docs/INTEGRATION.md`, `docs/RELIABILITY.md`, `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` (§40), `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md`, `docs/design-docs/KBase - Core v1 MinIO Integration Design.md`.

## Hệ thống bị ảnh hưởng

- Backend `invitation` (state machine accept-expired), `document` (batch cleanup + search `q`), `project` (my-projects `q`), `tag` (list `q`), `user` (admin list `q`), `folder` (move locking), `mail` (failure log), `config` (JwtProperties).
- Không đổi Flyway schema, không đổi endpoint/path/method/status, không đổi DTO schema → `docs/generated/db-schema.md` không đổi; `docs/generated/api-schema.md` chỉ khác 1 annotation description đã được snapshot đúng từ trước (VALIDATION_ERROR) → không cần tái sinh.

## Ảnh hưởng trạng thái repository

- Sau slice: Core v1 vẫn FROZEN về scope; các fix là maintenance theo audit. `CURRENT_STATE.md`, `QUALITY_SCORE.md`, tech-debt tracker được cập nhật trong cùng phiên.

## Đường dẫn verification

1. Targeted: `mvn -B -ntp "-Dtest=InvitationServiceTest,InvitationLifecycleIntegrationTest,DocumentServiceTest,DocumentSearchIntegrationTest,OrganizationIntegrationTest,SmtpMailServiceTest,ConfigurationPropertiesBindingTest,JpaMappingRepositoryIntegrationTest" test`
2. Full gate: `mvn -B -ntp clean verify` — kỳ vọng 214+ tests, 0 failures/errors/skips.
3. `docker compose -f docker-compose.yml config --quiet` (config không đổi nhưng re-check).

## Rủi ro và biện pháp

- M-01 đổi hành vi DB state (EXPIRED được persist) — lifecycle theo đúng spec FR-INV-002; resend trên invitation EXPIRED giờ trả 409 INVITATION_NOT_PENDING (đúng contract vì resend yêu cầu PENDING); recovery = tạo invitation mới (partial unique index được giải phóng). Ghi rõ trong CURRENT_STATE.
- L-05 đổi search semantics: `q` chứa `%`/`_` giờ là literal — ghi vào plan + CURRENT_STATE; test regression thêm.
- L-06 thêm SELECT ... FOR UPDATE — không deadlock nhờ UUID ordering; concurrency test chứng minh.
- Không touching storage/auth/security flow ngoài các điểm trên.

## Migration / rollback

Không có migration. Rollback = git revert commit slice; không có dữ liệu cần chuyển đổi (EXPIRED status đã nằm trong CHECK constraint từ V1).
