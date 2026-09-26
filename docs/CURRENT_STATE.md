# Trạng thái Hiện tại

> File này cung cấp ảnh chụp ngắn gọn về trạng thái hiện tại của repository.
> Chỉ ghi thông tin đã được xác nhận. Không dùng file này để thay thế execution plan, product spec hoặc Git history.

## Cập nhật Lần cuối

* Ngày cập nhật: `2026-09-27`
* Người hoặc agent cập nhật: `Real Gemini RAG Golden Journey Planning`
* Nhánh hiện tại: `feat-AI` (được tạo trực tiếp từ `dev`)

## Trạng thái Tổng quan

* **Core v1 backend: FROZEN** (M15 pass 2026-09-19; M16 post-audit maintenance pass).
* **AI v1 backend: FROZEN** (M0–M11 PASS; M11 freeze report 2026-09-26).
* **Post-freeze final codebase audit: PASS** (2026-09-26 — `docs/exec-plans/completed/KBase_Post_Freeze_Final_Codebase_Audit.md`): 4 reviewer findings xác nhận và sửa (storage exception log sanitization, invitation flush-before-mail, profile fail-safe, MinIO 5xx classification), 2 autonomous findings sửa (A-01/A-02 sanitization), full regression PASS.
* **Final handoff cleanup PASS** (2026-09-26): OpenAPI/Swagger chuyển fail-closed ở base config (no-profile artifact không expose docs — Docker smoke 401; local/test/runtime-test enable tường minh — `ProfileFailSafeTest` 6/6 + smoke runtime-test provider vẫn hoạt động), `AGENTS.md` + harness registry phản ánh frozen baseline, docs baseline-first đồng bộ. Đã commit/push tại 18a7cdde (Review cuối).
* **Real Gemini provider adapter smoke: PASS** (2026-09-26, owner-approved narrow slice `docs/exec-plans/completed/KBase_Real_Gemini_Provider_Smoke.md`): backend khởi động profile `local` với `KBASE_AI_ENABLED=true`, mode `gemini`, key từ .env (không commit); direct chat adapter PASS — `SpringAiGeminiChatAdapter` (chat model runtime candidate `gemini-3.5-flash-lite`) trả "KBASE_GEMINI_OK"; direct embedding adapter PASS — `SpringAiGeminiEmbeddingAdapter` (`gemini-embedding-2`) trả vector **768** allFinite. **Full live RAG golden journey CHƯA chạy** — belongs to a future owner-approved slice. Default chat model trong config vẫn là `gemini-2.5-flash`; việc đổi canonical model là quyết định riêng sau live verification.
* **Real Gemini smoke hardening: PASS** (2026-09-26 — `docs/exec-plans/completed/KBase_Real_Gemini_Provider_Smoke_Hardening.md`): manual smoke giờ mechanically isolate background AI scheduling (`kbase.ai.worker.scheduling-enabled=false`, production default giữ nguyên) + preflight fail-before-provider-call theo candidate được duyệt; full gate **400 tests, 0 failures/errors/skipped**.
* **Active owner-approved verification slice: Real Gemini RAG Golden Journey.** Plan: `docs/exec-plans/active/KBase_Real_Gemini_RAG_Golden_Journey.md`. Đây là verification/rollout slice trên frozen AI v1 behavior, không phải M12/AI v2 và không tự mở feature mới.
* Frontend và streaming: deferred, không thuộc phase backend hiện tại.

Bảng tổng quan:

| Khu vực          | Trạng thái    | Ghi chú |
| ---------------- | ------------- | ------- |
| Build            | FROZEN baseline | Full gate hiện tại: `mvn -B -ntp clean verify` BUILD SUCCESS, **400 tests / 0 failures / 0 errors / 0 skipped**; Compose base + mọi verification override validate. |
| Backend          | Core + AI FROZEN | Project Assistant (private creator conversations), Guide (2 packaged specs), document indexing/semantic retrieval, durable job engine, usage guard — toàn bộ frozen behavior. |
| Database         | Ổn định | Flyway V1–V4, 18 persistent tables, pgvector `vector(768)`, HNSW cosine indexes, Hibernate validate. |
| API contract     | Ổn định | Runtime OpenAPI 38 paths / 57 operations / 15 tags; API-AI-001..010; generated snapshots đồng bộ. |
| Security         | Verified runtime | Security matrix 37/37 (M11): cross-project isolation, creator privacy, prompt injection, source authz, Guide isolation; profile fail-safe thêm sau audit. |
| Reliability      | Verified runtime | Retention (+P7D, purge, race), restart/recovery (stale reclaim, persistence), failure isolation, 0-hit log audit (M11). |
| Deployment       | Ổn định | Explicit profile contract: compose = `local` tường minh, production = `prod`, artifact không profile fail-fast. |

## Kiến trúc & Capability hiện tại (tóm tắt)

* Core v1: auth (JWT + Redis OTP email verification + refresh session), user/project/membership, invitation lifecycle, folder/category/tag, document lifecycle + MinIO storage, metadata search, OpenAPI, Docker runtime.
* AI v1: document AI indexing (7 format allowlist, deterministic chunking `kbase-lex-v1`/`chunk-v1`, durable `DOCUMENT_INDEX` jobs), project-scoped semantic retrieval với strict `NO_EVIDENCE`, private Project Assistant conversations (max-5 quota, one-active generation), stateless Guide trên 2 packaged specs, Redis per-user usage guard (shared budget, 429/503 contract), Micrometer observability bounded.
* Provider: Gemini là production default mode (disabled-by-default, không dùng credential trong automated gate); deterministic runtime-test provider chỉ cho verification (profile + acknowledgement guarded).

## Verification hiện tại

| Kiểm tra | Kết quả | Thời điểm |
| --- | --- | --- |
| `mvn -B -ntp clean verify` (full gate hiện tại, sau smoke hardening) | BUILD SUCCESS — **400 tests, 0 failures/errors/skipped** | 2026-09-27 |
| M11 runtime matrix (security 37/37, retention, restart/recovery, log audit 0 hits) | PASS | 2026-09-26 |
| Profile fail-safe (`ProfileFailSafeTest` 6/6 + Docker smoke local/runtime-test) | PASS | 2026-09-26 |
| Runtime OpenAPI = 38/57/15; Flyway V1–V4; 18 tables | PASS | 2026-09-26 |

Chi tiết lệnh và bằng chứng từng milestone: xem các freeze report và `docs/DEVELOPMENT.md` (verification records).

## Tài liệu tham chiếu chính (thay cho chronology M0–M11)

* Core v1 freeze report: `docs/exec-plans/completed/KBase_Core_v1_M15_Full_Verification_Freeze.md`
* AI v1 freeze report: `docs/exec-plans/completed/KBase_AI_Chatbot_v1_M11_Full_Runtime_Verification_AI_v1_Freeze.md`
* Post-freeze final audit report: `docs/exec-plans/completed/KBase_Post_Freeze_Final_Codebase_Audit.md`
* Lịch sử M0–M11 (historical evidence only): `docs/exec-plans/completed/`
* Product specs: `docs/product-specs/`; design docs: `docs/design-docs/`; generated snapshots: `docs/generated/`

## Blocker Hiện tại

| Blocker | Ảnh hưởng | Hướng xử lý | Trạng thái |
| ------- | --------- | ----------- | ---------- |
| (không có blocker) | — | Thực thi active Real Gemini RAG Golden Journey plan; nếu cần đổi product/API/schema thì BLOCK và xin owner decision | — |

## Rủi ro và Technical Debt Liên quan

* Technical debt tracker (source of truth cho debt đang mở): `docs/exec-plans/tech-debt-tracker.md`
* Debt AI đang mở (accepted, non-blocking): abrupt ASSISTANT PROCESSING recovery (MEDIUM, workaround verified — creator DELETE), worker lease/no heartbeat (MEDIUM), Gemini independent connect-timeout (LOW), transient revoke→rejoin failure-code precision (LOW).
* Debt Core đang mở: PATCH document null semantics (L-02 — cần product decision), CI pipeline, Gmail production smoke, Redis OTP observability.

## Bước Tiếp theo

1. `Active plan: docs/exec-plans/active/KBase_Real_Gemini_RAG_Golden_Journey.md — owner-approved, READY TO EXECUTE trên baseline 636ea264.`
2. `Mục tiêu: verify end-to-end real-provider RAG qua HTTP boundary bằng synthetic data: real document embedding/indexing → pgvector retrieval → grounded Project Assistant answer/citations + strict NO_EVIDENCE + Guide grounded/no-evidence; không đổi frozen product/API/schema nếu không có bug xác nhận.`
3. `Không tạo M12. Sau PASS, archive plan và trả active plan về NONE; quyết định promote gemini-3.5-flash-lite thành canonical default (nếu có) là owner decision riêng.`

## Quy tắc Cập nhật

* Cập nhật khi trạng thái repository thay đổi đáng kể.
* Cập nhật trước khi kết thúc một phiên làm việc dài.
* Chỉ ghi trạng thái đã được xác nhận.
* Không sao chép toàn bộ execution plan vào file này.
* Không ghi quyết định thiết kế dài; liên kết đến `docs/design-docs/`.
* Không ghi chi tiết thay đổi code; dùng Git history.
* Khi có nhiều execution plan, phải chỉ rõ plan đang được ưu tiên.
