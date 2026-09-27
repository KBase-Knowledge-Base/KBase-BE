# Active Plans

- **Active implementation plan: `KBase_RAG_Hardening.md`.** Owner đã duyệt phase post-freeze RAG Hardening ngày 2026-09-27; M0 `Baseline & Lease Edge Reproduction` là bước hiện tại. Core v1 và AI v1 product behavior vẫn FROZEN; plan chỉ được harden reliability/evaluation/CI/performance trong guard đã ghi.
- **Core v1: FROZEN** (M15 + M16).
- **AI v1 backend: FROZEN** (M0–M11 PASS; freeze report `../completed/KBase_AI_Chatbot_v1_M11_Full_Runtime_Verification_AI_v1_Freeze.md`).
- **Post-freeze final codebase audit: PASS** (2026-09-26; report `../completed/KBase_Post_Freeze_Final_Codebase_Audit.md`).
- **Real Gemini: adapter smoke + hardening + full live RAG Golden Journey đều PASS** (2026-09-26/27; reports `../completed/KBase_Real_Gemini_Provider_Smoke.md`, `../completed/KBase_Real_Gemini_Provider_Smoke_Hardening.md`, `../completed/KBase_Real_Gemini_RAG_Golden_Journey.md`).

## Quy tắc

- Chỉ một active slice KBase được ưu tiên tại một thời điểm trừ khi master plan xác nhận task có thể chạy song song an toàn.
- Khi active slice hoàn tất, di chuyển nó sang `../completed/` và tạo slice kế tiếp từ master plan tương ứng.
- Nếu thiếu context hoặc có conflict giữa docs/code, đánh dấu task `BLOCKED`, đọc lại source-of-truth và không suy đoán.
- Core v1 và AI v1 là frozen baseline; mọi thay đổi kế tiếp cần phase/plan mới được duyệt.
- Frontend, streaming, Project Chat, OCR/multimodal, spreadsheet RAG, broker và các scope guard khác không được tự mở.
