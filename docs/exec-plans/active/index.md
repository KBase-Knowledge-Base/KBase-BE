# Active Plans

- Không có active slice. M15 đã PASS ngày 2026-09-19 và **Core v1 đã FROZEN** (M0–M15 tất cả PASS; full suite 210/210; Docker runtime re-verified). Không mở phase AI/RAG hay frontend nếu chưa có chỉ thị mới.

## Quy tắc

- Chỉ một active slice KBase được ưu tiên tại một thời điểm trừ khi master plan xác nhận task có thể chạy song song an toàn.
- Khi active slice hoàn tất, di chuyển nó sang `../completed/` và tạo slice kế tiếp từ master plan.
- Nếu thiếu context hoặc có conflict giữa docs/code, đánh dấu task `BLOCKED`, đọc lại source-of-truth và không suy đoán.
- Core v1 đã frozen: mọi thay đổi tiếp theo cần phase/plan mới được duyệt; không tự mở AI/RAG, frontend, K8s/Terraform hay scope guard items.
