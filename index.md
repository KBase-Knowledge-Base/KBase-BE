# KBase Backend Agent Harness

Bộ khung này đã được đồng bộ cho KBase Core v1 và được dùng làm bề mặt tài liệu agent-first trước khi triển khai backend.

## Thứ tự Khởi động

1. Đặt toàn bộ harness này tại root của repository KBase thực tế.
2. Agent bắt đầu từ `AGENTS.md`.
3. Đọc `docs/CURRENT_STATE.md` để xác nhận M1 foundation đã bootstrap, active M2 slice đang READY và frontend đang deferred.
4. Đọc master plan `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md` và active slice trong `docs/exec-plans/active/`; chỉ thực thi task có dependency/gate đã pass.
5. M0 Preflight và M1 Bootstrap đã hoàn tất; task tiếp theo là M2/DB-01 ở trạng thái READY nhưng chưa bắt đầu.
6. Chỉ cập nhật command trong `docs/DEVELOPMENT.md` sau khi command thật đã tồn tại và được kiểm chứng.

## Bộ Khung Này Tối ưu hóa Cho

- implementation backend Core v1 theo task có dependency và verification gate
- ngữ cảnh cục bộ repo lâu bền
- nạp có chọn lọc product/design docs thay vì nhồi toàn bộ context mỗi phiên
- PostgreSQL/Flyway, Redis OTP, Gmail SMTP và MinIO integration rõ boundary
- authorization/project isolation có thể review và test cơ học
- vòng đời kế hoạch và checkpoint trạng thái rõ ràng

Frontend vẫn được giữ tài liệu để dùng về sau nhưng không thuộc active implementation scope hiện tại.
