# KBase – Post-Freeze Final Codebase Audit & Handoff

**Status:** PASS – COMPLETED (2026-09-26)
**Baseline:** `feat-AI` @ `e1b5fecb3fe711ad6335c5fa7e9fc63c4761a46f`
**Context:** Core v1 FROZEN; AI v1 backend FROZEN (M0–M11 PASS, freeze report `../completed/KBase_AI_Chatbot_v1_M11_Full_Runtime_Verification_AI_v1_Freeze.md`); final M11 gate 377/377.

## Goal

Một lượt audit độc lập cuối cùng toàn bộ codebase trước phase mới: verify reviewer findings, fix bug xác nhận trong accepted behavior, tự tìm thêm issue, chạy full regression, và chuẩn bị living docs thành clean frozen baseline cho session tiếp theo (không bắt session sau đọc lại M0–M11 chronology).

Non-goals: M12, AI v2, new feature milestone, Workspace, frontend, refresh-token redesign, durable chat recovery, bất kỳ product/schema/API change nào.

## Entry baseline (2026-09-26)

- `mvn -B -ntp clean verify`: BUILD SUCCESS (377 tests, 0 failures/errors/skips)
- `docker compose -f docker-compose.yml config --quiet`: PASS
- `git diff --check`: PASS (working tree sạch trước audit)

## Reviewer findings to verify

- **F-01** Raw infrastructure exception logging (MEDIUM): MinIO SDK cause chain → StorageException → InfrastructureException → full-stack log.
- **F-02** Invitation email before constraint flush (MEDIUM): concurrent create cùng project+email có thể gửi mail cho transaction bị rollback.
- **F-03** Default local profile fail-open (MEDIUM): `spring.profiles.default=local` cho phép artifact quên set profile chạy với Secure=false + auto-create.
- **F-04** MinIO 5xx error classification (LOW/MEDIUM): `ErrorResponseException` HTTP 5xx chưa map STORAGE_SERVICE_UNAVAILABLE cho upload/delete.

## Verification record

Điền khi hoàn tất; không mark PASS nếu không có bằng chứng chạy được.

| Item | Required evidence | Status |
|---|---|---|
| F-01 | Sentinel log-capture test qua boundary thật | PASS — CONFIRMED + fixed; `MinioStorageServiceTest.providerFailureLogExcludesSentinels` + 8 test khác 9/9 |
| F-02 | Real-PostgreSQL concurrency regression proving constraint-before-mail | PASS — CONFIRMED (UUID id defers INSERT to flush) + `saveAndFlush` fix; `concurrentCreateOfSamePendingInvitationFlushesConstraintBeforeMail` FAILS without fix / PASSES with fix; lifecycle 6/6 |
| F-03 | Profile fail-safe test + Compose config + isolated Docker smoke | PASS — CONFIRMED + fixed; `ProfileFailSafeTest` 4/4; smoke `kbase-audit` (explicit local, Flyway V1–V4, 18 tables) + `kbase-m11fix` rebuild (runtime-test provider vẫn start) |
| F-04 | Synthetic MinIO error-response classification tests | PASS — CONFIRMED + fixed; 503 upload/delete → STORAGE_SERVICE_UNAVAILABLE, 400 → generic, 404 read giữ not-found; trong MinioStorageServiceTest 9/9 |
| Autonomous audit | Mechanical scans + targeted reviews + packaging check | PASS — A-01 (bucket initializer cause) + A-02 (index-handler stream IO cause) sanitized; TODO/System.out/printStackTrace/Thread.sleep/DTO/log scans clean; CORS wildcard+credentials guarded; jar chỉ chứa deterministic provider profile-guarded |
| Full regression | `mvn -B -ntp clean verify` | PASS — BUILD SUCCESS, **387 tests, 0 failures, 0 errors, 0 skipped** (377 baseline + 5 storage classification/sanitization + 1 invitation concurrency + 4 profile fail-safe); jar repackage pass |
| Compose verification | config --quiet mọi combination + Docker smoke | PASS — 8/8 combos; smoke như trên |
| OpenAPI/DB unchanged | 38/57/15 + V1–V4 + 18 tables | PASS — không đổi API/schema |
| Living-doc handoff | CURRENT_STATE simplified + report + tracker + index | PASS — CURRENT_STATE viết lại gọn theo baseline hiện tại; audit report này là handoff reference; tracker giữ nguyên open debt |

## Execution log

### 2026-09-26 — audit + fixes

- Baseline PASS (377/377, compose, diff-check).
- F-01 CONFIRMED: `MinioStorageService` giữ raw MinIO SDK exception làm cause; `DocumentService`/`ProjectService` re-wrap thành `InfrastructureException` giữ nguyên chain; `GlobalExceptionHandler.logKnownException` log full exception+cause. MinIO `ErrorResponse.toString()` chứa bucketName/objectName/resource → storage key vào log thường. Fix theo precedent SMTP L-07 (audit M16): log chỉ provider exception TYPE tại adapter boundary, throw StorageException không giữ cause; batch-delete aggregate cũng sanitized. Cập nhật `MinioStorageServiceTest` (assert no-cause thay vì cause-retention) + sentinel log-capture test.
- F-02 CONFIRMED: `ProjectInvitation` dùng UUID application-assigned id → Hibernate defer INSERT tới flush; `save()` trước mail gửi bên ngoài cho phép race: 2 concurrent create đều qua exists-check, cả hai gửi mail, loser fail unique index lúc commit → mail token không hợp lệ. Fix: `saveAndFlush` trước mail (constraint reject trước side effect; SMTP failure vẫn rollback row). Regression: concurrency test với blocking-mail latch — verified FAILS without fix, PASSES with fix.
- F-03 CONFIRMED: 21 test class ĐÃ có `@ActiveProfiles` tường minh (11 local + 10 test) — không test nào phụ thuộc default; loại `spring.profiles.default: local`; compose set `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}` tường minh; host run được document `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`; production set `prod`.
- F-04 CONFIRMED: `isUnavailable` không classify `ErrorResponseException` 5xx (MinIO `SlowDown`/`InternalError`/503) → upload/delete map generic failure thay vì 503. Fix: classify qua `response().code() >= 500` (factual HTTP status); 4xx giữ generic; 404 read giữ not-found; batch per-object errors chỉ expose S3 code (không HTTP status) → document limitation, không invent behavior.
- Autonomous A-01: `MinioBucketInitializer` giữ raw MinIO cause trong startup-failure exception → sanitized (type-only trong message, no cause).
- Autonomous A-02: `DocumentIndexJobHandler` giữ IOException cause (okhttp stream error có thể chứa object URL) → sanitized.
- Autonomous audit scopes không phát hiện thêm defect cần fix: auth/JWT/refresh (M6/M14/M15 audit + M11 runtime), project/membership/invitation contract (M7/M8/M16), document/storage lifecycle (M10/M11-Core/M16), AI provider disabled-by-default + deterministic isolation (M1/M4/M11), job claim/lease/dedup (M3/M5/M11 runtime), RAG isolation + Guide allowlist (M6/M9/M11 runtime), usage guard atomicity (M10), OpenAPI exposure (M13/M10), packaging (không test-only class trong artifact).

## Final result (2026-09-26)

**PASS.** Toàn bộ gate điều kiện đạt: baseline độc lập PASS; F-01..F-04 đều CONFIRMED + fixed + regression test; 2 autonomous findings fixed; không HIGH/BLOCKER mở; focused + full regression 387/387 PASS; compose 8/8 + Docker smoke PASS; OpenAPI 38/57/15, Flyway V1–V4, 18 tables không đổi; security/log scan PASS; living docs chuyển sang baseline-first handoff. Changes left uncommitted for operator review.

## Handoff notes cho session tiếp theo

1. Đọc `docs/CURRENT_STATE.md` (đã viết lại gọn) — không cần replay M0–M11.
2. Baseline hiện tại: Core v1 + AI v1 FROZEN, post-freeze audit PASS, full gate 387/387, OpenAPI 38/57/15, Flyway V1–V4, 18 tables.
3. Contract runtime đáng nhớ: profile tường minh (compose `local`, prod `prod`, không default); storage/mail/provider exceptions không giữ raw cause (log chỉ safe type); invitation create flush unique-index trước mail; MinIO 5xx → `STORAGE_SERVICE_UNAVAILABLE` 503.
4. Open debt: xem `docs/exec-plans/tech-debt-tracker.md` — không debt nào bị hidden hay downgrade.
5. Phase mới cần owner approval; không tự tạo M12.
