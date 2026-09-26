# Active Plans

- **AI v1 backend FROZEN (2026-09-26):** M0–M11 đã PASS; M11 freeze report nằm tại `../completed/KBase_AI_Chatbot_v1_M11_Full_Runtime_Verification_AI_v1_Freeze.md`. Không có active AI v1 implementation milestone. Không tạo M12 tự động; bất kỳ phase mới nào cần owner approval.
- Parent roadmap: `../KBase_AI_Chatbot_v1_Implementation_Plan.md`.
- Core v1 M0–M15 + M16 maintenance đã hoàn thành/frozen; không mở lại Core scope ngoài dependency cần thiết nếu một phase mới được duyệt.

## Quy tắc

- Chỉ một active slice KBase được ưu tiên tại một thời điểm trừ khi master plan xác nhận task có thể chạy song song an toàn.
- Khi active slice hoàn tất, di chuyển nó sang `../completed/` và tạo slice kế tiếp từ AI master plan.
- Nếu thiếu context hoặc có conflict giữa docs/code, đánh dấu task `BLOCKED`, đọc lại source-of-truth và không suy đoán.
- Core v1 là frozen baseline; AI v1 là frozen baseline từ 2026-09-26; mọi thay đổi kế tiếp cần phase/plan mới được duyệt.
- Frontend, streaming, Project Chat, OCR/multimodal, spreadsheet RAG, broker và các scope guard khác không được tự mở.
