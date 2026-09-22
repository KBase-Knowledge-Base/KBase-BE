# Active Plans

- **AI v1 active:** `KBase_AI_Chatbot_v1_M1_Runtime_Foundation_and_Provider_Ports.md` — M0 đã PASS và được lưu trong `../completed/`; M1 hiện chỉ xây dựng compile-safe foundation, typed config, KBase provider ports, deterministic fakes và pgvector-capable local/test wiring.
- Parent roadmap: `../KBase_AI_Chatbot_v1_Implementation_Plan.md`.
- Core v1 M0–M15 + M16 maintenance đã hoàn thành/frozen; không mở lại Core scope ngoài dependency cần thiết cho AI v1 nếu source docs/active plan không yêu cầu.

## Quy tắc

- Chỉ một active slice KBase được ưu tiên tại một thời điểm trừ khi master plan xác nhận task có thể chạy song song an toàn.
- Khi active slice hoàn tất, di chuyển nó sang `../completed/` và tạo slice kế tiếp từ AI master plan.
- Nếu thiếu context hoặc có conflict giữa docs/code, đánh dấu task `BLOCKED`, đọc lại source-of-truth và không suy đoán.
- Core v1 là frozen baseline; AI v1 chỉ được thay Core boundary khi SD-14..SD-18 và active plan yêu cầu rõ.
- Frontend, streaming, Project Chat, OCR/multimodal, spreadsheet RAG, broker và các scope guard khác không được tự mở.
