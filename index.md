# KBase Backend Agent Harness

Bộ khung này đã được đồng bộ cho KBase Core v1 và được dùng làm bề mặt tài liệu agent-first trước khi triển khai backend.

## Thứ tự Khởi động

1. Đặt toàn bộ harness này tại root của repository KBase thực tế.
2. Agent bắt đầu từ `AGENTS.md`.
3. Đọc `docs/CURRENT_STATE.md` để xác nhận trạng thái hiện tại: M0–M15 đã PASS và **Core v1 đã FROZEN ngày 2026-09-19** (full suite 210/210; Docker runtime re-verified); frontend vẫn deferred.
4. Đọc master plan `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md` và slice trong `docs/exec-plans/active/` (nếu có); mọi thay đổi sau freeze cần phase/plan mới được duyệt.
5. Các milestone M0–M15 đã hoàn tất và lưu tại `docs/exec-plans/completed/`; không mở AI/RAG, frontend, K8s/Terraform nếu chưa có chỉ thị mới.
6. Chỉ cập nhật command trong `docs/DEVELOPMENT.md` sau khi command thật đã tồn tại và được kiểm chứng.

## Bộ Khung Này Tối ưu hóa Cho

- implementation backend Core v1 theo task có dependency và verification gate
- ngữ cảnh cục bộ repo lâu bền
- nạp có chọn lọc product/design docs thay vì nhồi toàn bộ context mỗi phiên
- PostgreSQL/Flyway, Redis OTP, Gmail SMTP và MinIO integration rõ boundary
- authorization/project isolation có thể review và test cơ học
- vòng đời kế hoạch và checkpoint trạng thái rõ ràng

Frontend vẫn được giữ tài liệu để dùng về sau nhưng không thuộc active implementation scope hiện tại.
