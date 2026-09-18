# PRODUCT_SENSE.md

Tệp này ghi lại phán xét sản phẩm lâu bền mà agent không thể suy ra đáng tin cậy chỉ từ mã.

## Cốt lõi Sản phẩm

- Người dùng chính: `Các thành viên team/project cần một knowledge base dùng chung; ADMIN quản trị hệ thống, OWNER quản trị project, MEMBER cộng tác trong project.`
- Công việc cần hoàn thành: `Đăng ký và xác minh email an toàn, tạo/tham gia project, tổ chức tài liệu, upload/view/download/search metadata với quyền truy cập đúng theo project.`
- Sự thất vọng chính cần loại bỏ: `Tài liệu project phân tán, quyền sở hữu không rõ ràng, file khó tìm và thao tác có thể làm lộ hoặc mất dữ liệu giữa các project.`
- Tiêu chuẩn chất lượng để chấp nhận: `Core backend phải ổn định, quyền được enforce phía server, không cross-project leakage, dữ liệu PostgreSQL/MinIO bền vững, OTP/invitation/token không lộ, và các flow quan trọng có verification chạy được.`

## Quy tắc Sản phẩm

- Ưu tiên độ tin cậy có thể nhìn thấy của người dùng hơn số lượng tính năng.
- Core v1 backend được hoàn thiện trước; frontend là optional và được hoãn sang phase sau.
- AI/RAG chỉ được bắt đầu sau khi Core v1 đạt freeze gate trong Implementation Plan.
- OTP của Core v1 chỉ dùng để xác minh email đăng ký, không phải OTP login hoặc MFA/2FA.
- Invitation vẫn dùng email invitation link/token riêng, không thay bằng OTP.
- MEMBER rời/bị remove khỏi project không làm mất các document đã upload; quyền truy cập bị thu hồi nhưng provenance vẫn giữ ở `Document.uploadedBy`.
- Ưu tiên hard rule về project isolation và ownership hơn sự tiện lợi của client.
- Coi hành vi mơ hồ là khoảng trống spec, không phải sự cho phép để đoán.
- Nếu việc triển khai thay đổi những gì người dùng nhìn thấy hoặc tin tưởng, hãy cập nhật spec phù hợp.
- Sử dụng product spec cho các luồng cụ thể, và sử dụng tệp này cho các ưu tiên sản phẩm xuyên suốt.

## Mẫu Không được phép

- Các hành động phá hủy ẩn
- Thất bại âm thầm mà không có phản hồi cho người dùng
- Nguồn sự thật không rõ ràng cho trạng thái có thể nhìn thấy
- Cross-project data leakage hoặc quyền chỉ được ẩn ở frontend nhưng không enforce ở backend
- OTP login, MFA, public file sharing hoặc AI/RAG được tự thêm ngoài phạm vi Core v1
- Các tính năng không thể giải thích trong một câu
