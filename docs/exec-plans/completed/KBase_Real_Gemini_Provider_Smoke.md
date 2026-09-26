# KBase – Real Gemini Provider Smoke

**Status:** PASS – COMPLETED (2026-09-26)
**Baseline:** `feat-AI` @ `18a7cdde9240cdef967b2b382a2f21559d0965ff` ("Review cuối")
**Approval:** Owner phê duyệt slice rất hẹp cho Real Gemini provider smoke — KHÔNG phải M12, KHÔNG phải AI v2, KHÔNG phải feature milestone.

## Goal

1. Sửa nốt stale documentation finding (DOC-01..DOC-05).
2. Khởi động backend với Gemini thật (profile `local` tường minh, provider mode `gemini`).
3. Xác minh riêng biệt:
   - KBase `AiChatModel` → `SpringAiGeminiChatAdapter` → real Gemini hoạt động (chat model `gemini-3.5-flash-lite` runtime override);
   - KBase `AiEmbeddingModel` → `SpringAiGeminiEmbeddingAdapter` → real Gemini trả đúng vector 768 (model `gemini-embedding-2`).
4. Chỉ sau khi cả hai adapter PASS mới kết luận provider connectivity usable.

## Ranh giới cứng

- KHÔNG chạy full Project Assistant / RAG golden journey (thuộc slice tiếp theo "Real Gemini RAG Golden Journey", chưa được duyệt).
- KHÔNG đổi application.yml/.env.example default model (`gemini-2.5-flash` giữ nguyên); chat model override chỉ là runtime evaluation của candidate.
- KHÔNG commit/push; KHÔNG đưa API key vào source/docs/logs; `.env` không vào Git.
- KHÔNG thêm public endpoint; smoke harness chỉ là manual verification (không chạy trong Surefire mặc định, không làm full suite có skipped test).
- KHÔNG dùng deterministic provider để claim Real Gemini PASS; KHÔNG đổi model khác nếu candidate fail.
- KHÔNG đổi DB schema/API contract; generated snapshots không đổi.
- Không thực hiện bulk ingestion; input synthetic; không gửi dữ liệu project thật/private.

## Verification record

| Item | Required evidence | Status |
|---|---|---|
| DOC-01..05 | Stale wording đã verify và sửa trên code hiện tại | PASS — DOC-01/02: CURRENT_STATE cleanup commit = 18a7cdd (9c96a962 = audit commit, phân biệt rõ); DOC-03: 4/4 → 6/6; DOC-04: registry updatedOn 2026-09-26, sourceDocumentCount=19, SD-01..19 nguyên vẹn; DOC-05: scan living docs = 0 stale hit |
| Baseline regression | `mvn -B -ntp clean verify` PASS + compose config + diff-check + JSON parse | PASS — BUILD SUCCESS 389 tests, 0 failures/errors/skipped; compose config PASS; diff-check PASS; registry JSON valid |
| Secret preflight | `.env` git-ignored; `KBASE_AI_GEMINI_API_KEY` non-blank (không print); không key trong tracked content | PASS — .env ignored + untracked; key non-blank (1 line, value không bao giờ được in); config .env: enabled=true, mode=gemini, chat=gemini-3.5-flash-lite, embedding=gemini-embedding-2, dims=768 |
| Startup với Gemini thật | Profile `local`, `KBASE_AI_ENABLED=true`, mode `gemini`, không deterministic/runtime-test; context khởi động không fail | PASS — manual harness (main(), không chạy Surefire) boot profile local + .env import: provider.mode=gemini, chatModel=gemini-3.5-flash-lite, embeddingModel=gemini-embedding-2, dims=768; Gemini clients wire thành công |
| Chat adapter smoke | `SpringAiGeminiChatAdapter` qua `AiChatModel` với real Gemini, non-empty response, không auth/quota/model error | PASS — 1 live request: bean SpringAiGeminiChatAdapter, modelId=gemini-3.5-flash-lite, response="KBASE_GEMINI_OK" (khớp exact prompt) |
| Embedding adapter smoke | `SpringAiGeminiEmbeddingAdapter` trả vector **đúng 768**, mọi element finite | PASS — 1 live request: bean SpringAiGeminiEmbeddingAdapter, modelId=gemini-embedding-2, dimensions=768 (match config), allFinite=true |
| Post-smoke regression | Full verify PASS, không unexpected skipped; OpenAPI 38/57/15; Flyway V1–V4; 18 tables; vector(768) | PASS — BUILD SUCCESS 389 tests, 0 failures/errors/skipped (harness không chạy trong Surefire); OpenAPI/Flyway/tables verified trên live runtime trong slice |
| Secret leakage audit | Diff + logs không chứa credential | PASS — smoke output scan: 0 hits AIza-prefix, 0 header/key pattern; diff scan 0 secret |

## Execution log

### 2026-09-26 — slice tạo

- DOC-01..DOC-05 đều CONFIRMED trên code hiện tại (chi tiết trong execution log bên dưới).
- Real Gemini smoke harness thiết kế: manual main() class trong `src/test/java`, tên không match Surefire includes, không @Test — không thể chạy trong `mvn clean verify`; gọi trực tiếp hai KBase ports qua Spring context profile `local`.

## Final result (2026-09-26)

**PASS.** Real Gemini provider connectivity usable ở tầng adapter: 1 live chat request (SpringAiGeminiChatAdapter, model candidate `gemini-3.5-flash-lite`) trả "KBASE_GEMINI_OK"; 1 live embedding request (SpringAiGeminiEmbeddingAdapter, `gemini-embedding-2`) trả vector đúng 768 allFinite. Tổng 2 live requests, không retry spam. Baseline + post-smoke regression đều 389/389 (harness không chạy trong Surefire). Secret audit 0 hits. **Full live RAG golden journey KHÔNG thuộc slice này và chưa chạy** — đề xuất slice tiếp theo (cần owner approval): "Real Gemini RAG Golden Journey". Việc đổi canonical/default chat model từ `gemini-2.5-flash` sang `gemini-3.5-flash-lite` là quyết định riêng của owner sau khi cân nhắc kết quả smoke này; repo config chưa đổi.
