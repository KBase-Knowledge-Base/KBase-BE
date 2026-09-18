# FRONTEND.md

Tệp này định nghĩa các kỳ vọng frontend ổn định để agent không phát minh ra các mẫu UI một cách không thể đoán trước.

## Nguyên tắc UI

- Tối ưu hóa cho sự rõ ràng trước sự mới lạ.
- Giữ các luồng tương tác có thể khám phá và khởi động lại được.
- Ưu tiên một số ít component tái sử dụng thay vì các biến thể một lần.
- Kiểm tra accessibility là một phần của xác minh thông thường, không phải công việc đánh bóng.

## Guardrail

- Ghi lại design system hoặc thư viện component trong `docs/references/`.
- Ghi lại các trạng thái quan trọng dành cho người dùng: trống, đang tải, thành công, lỗi, thử lại.
- Giữ copy, hành vi bàn phím và hệ thống phân cấp trực quan nhất quán qua các luồng.
- Khi một lỗi UI được sửa, hãy thêm hoặc cập nhật bước xác minh phù hợp.
- Mọi request và response phải tuân theo `docs/API_CONVENTIONS.md`.
- Khi tích hợp API, frontend phải đối chiếu endpoint, request, response, enum và error schema hiện tại trong `docs/generated/api-schema.md`.
- Các boundary giữa frontend và backend phải tuân theo `docs/INTEGRATION.md`.
- Không tự suy đoán field, enum hoặc response shape nếu contract đã được tài liệu hóa.
- Lệnh chạy, lint, type-check và build frontend phải được khai báo trong `docs/DEVELOPMENT.md`.

## Kỳ vọng Xác minh

- Ghi lại bằng chứng cho các user journey quan trọng.
- Ghi lại các bước xác minh browser hoặc runtime trong kế hoạch liên quan.
- Nếu các hồi quy trực quan phổ biến, hãy chuẩn hóa kiểm tra screenshot hoặc DOM.
- Mức kiểm thử frontend tối thiểu phải tuân theo `docs/TESTING.md`.
- Nếu API contract thay đổi, phải kiểm chứng cả client và server với `docs/generated/api-schema.md` đã được cập nhật.
- Contract mismatch giữa frontend và backend là lỗi verification, không được bỏ qua.
- Nếu thay đổi ảnh hưởng build hoặc artifact frontend, phải đối chiếu `docs/DEPLOYMENT.md`.
