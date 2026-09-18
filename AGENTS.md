# AGENTS.md

Kho lưu trữ này được tối ưu hóa cho công việc coding-agent chạy lâu. Giữ tệp này ngắn. Sử dụng nó như lớp định tuyến vào các tài liệu hệ thống ghi chép, không phải như một đống hướng dẫn khổng lồ.

## Quy trình Khởi động

Trước khi thay đổi mã:

1. Xác nhận thư mục gốc repo bằng `pwd`.
2. Đọc `ARCHITECTURE.md` để biết bản đồ hệ thống hiện tại và các quy tắc phụ thuộc cứng.
3. Đọc `docs/QUALITY_SCORE.md` để xem domain, lớp hoặc năng lực kỹ thuật nào đang yếu.
4. Đọc `docs/CURRENT_STATE.md` để biết trạng thái hiện tại của repository, công việc ưu tiên, blocker, phần chưa được xác minh và bước tiếp theo.
5. Đọc `docs/PLANS.md`, sau đó mở kế hoạch active liên quan trong `docs/exec-plans/active/`.
6. Đọc product spec liên quan trong `docs/product-specs/`.
7. Đọc design document liên quan trong `docs/design-docs/` nếu công việc ảnh hưởng thiết kế hoặc kiến trúc.
8. Đọc `docs/DEVELOPMENT.md` để biết cách thiết lập, khởi động và xác minh repository.
9. Đọc các tài liệu chuyên biệt phù hợp với phạm vi thay đổi:
   - Frontend: `docs/FRONTEND.md`
   - Backend: `docs/BACKEND.md`
   - Database hoặc migration: `docs/DATABASE.md`
   - API contract: `docs/API_CONVENTIONS.md` và `docs/generated/api-schema.md`
   - Tích hợp nhiều lớp hoặc dịch vụ bên ngoài: `docs/INTEGRATION.md`
   - Kiểm thử: `docs/TESTING.md`
   - Bảo mật: `docs/SECURITY.md`
   - Độ tin cậy: `docs/RELIABILITY.md`
   - Build hoặc triển khai: `docs/DEPLOYMENT.md`
10. Chạy bootstrap và baseline verification được khai báo trong `docs/DEVELOPMENT.md`. Nếu `docs/DEVELOPMENT.md` xác nhận backend chưa bootstrap và command chưa khả dụng, M0 Preflight được phép chỉ inspect repository; không tự phát minh command để thay placeholder.
11. Nếu baseline đang thất bại, ghi nhận lỗi có sẵn trong execution plan và `docs/CURRENT_STATE.md` trước khi mở rộng phạm vi.

## Bản đồ Định tuyến

- `ARCHITECTURE.md`: bản đồ domain, mô hình lớp và quy tắc phụ thuộc
- `docs/CURRENT_STATE.md`: trạng thái hiện tại của repository, công việc ưu tiên, blocker, verification gần nhất và bước tiếp theo
- `docs/design-docs/index.md`: các quyết định thiết kế và nguyên tắc cốt lõi
- `docs/product-specs/index.md`: các hành vi sản phẩm hiện tại và acceptance criteria
- `docs/PLANS.md`: vòng đời và quy tắc quản lý execution plan
- `docs/QUALITY_SCORE.md`: sức khỏe domain, lớp kiến trúc và năng lực kỹ thuật
- `docs/RELIABILITY.md`: tín hiệu runtime, journey quan trọng và khả năng khởi động lại
- `docs/SECURITY.md`: secret, dữ liệu, quyền hạn và hành động nguy hiểm
- `docs/FRONTEND.md`: quy tắc UI, component, trạng thái giao diện và accessibility
- `docs/BACKEND.md`: ranh giới layer, business logic, transaction và quy tắc backend
- `docs/DATABASE.md`: schema, migration, transaction và tính toàn vẹn dữ liệu
- `docs/generated/db-schema.md`: snapshot của database schema hiện tại
- `docs/API_CONVENTIONS.md`: quy tắc thiết kế endpoint, request, response, lỗi và compatibility
- `docs/generated/api-schema.md`: snapshot Markdown của API contract hiện tại
- `docs/INTEGRATION.md`: kết nối frontend, backend, database và dịch vụ bên ngoài
- `docs/TESTING.md`: chiến lược kiểm thử và mức verification tối thiểu
- `docs/DEVELOPMENT.md`: thiết lập local, command khởi động, reset và baseline verification
- `docs/DEPLOYMENT.md`: build, artifact, migration, triển khai và rollback

## Hợp đồng Làm việc

- Phase hiện tại chỉ triển khai backend Core v1. Frontend là optional và được hoãn; chỉ đọc hoặc thay đổi `docs/FRONTEND.md` khi một kế hoạch sau này mở lại phạm vi frontend.
- Làm việc từ một kế hoạch có ranh giới hoặc slice tính năng tại một thời điểm.
- Nếu thiếu hoặc không nhớ chắc một rule KBase, coi đó là thiếu context: đọc lại product spec, design document và execution plan liên quan trước khi tiếp tục; không suy đoán để lấp khoảng trống.
- Không đánh dấu công việc xong chỉ từ kiểm tra mã; cần bằng chứng có thể chạy được.
- Nếu bạn thay đổi hành vi, hãy cập nhật tài liệu sản phẩm, kế hoạch hoặc độ tin cậy phù hợp trong cùng phiên.
- Nếu bạn thấy phản hồi review lặp đi lặp lại, hãy thúc đẩy nó thành quy tắc cơ học, kiểm tra hoặc linter thay vì giải thích lại trong chat.
- Giữ tài liệu được tạo ra trong `docs/generated/` và tài liệu tham khảo nguồn trong `docs/references/`.
- Ưu tiên thêm tài liệu nhỏ, hiện tại hơn là phát triển tệp này.
- Không dùng `docs/CURRENT_STATE.md` làm bằng chứng duy nhất; luôn đối chiếu với code, Git history, execution plan và kết quả verification thực tế.
- Khi `docs/CURRENT_STATE.md` không còn phản ánh đúng trạng thái repository, phải cập nhật file trong cùng phiên.
- Không chỉnh trực tiếp tài liệu trong `docs/generated/` nếu tài liệu đó được sinh tự động.
- Khi database schema thay đổi, phải cập nhật hoặc sinh lại `docs/generated/db-schema.md`.
- Khi API contract thay đổi, phải cập nhật hoặc sinh lại `docs/generated/api-schema.md`.
- Nếu tài liệu generated mâu thuẫn với source code, migration hoặc contract đã được xác minh, tài liệu generated được xem là lỗi thời.

## Định nghĩa Hoàn thành

Một thay đổi chỉ xong khi tất cả những điều sau đây là đúng:

- hành vi mục tiêu đã được triển khai
- xác minh cần thiết đã thực sự chạy
- bằng chứng được liên kết từ kế hoạch hoặc tài liệu chất lượng liên quan
- các tài liệu bị ảnh hưởng vẫn là hiện tại
- kho lưu trữ có thể khởi động lại sạch sẽ từ đường dẫn khởi động chuẩn
- mức kiểm thử tối thiểu trong `docs/TESTING.md` đã được đáp ứng
- migration và `docs/generated/db-schema.md` đã được cập nhật nếu database schema thay đổi
- `docs/generated/api-schema.md` đã được cập nhật hoặc sinh lại nếu endpoint, method, request, response, enum, pagination hoặc error schema thay đổi
- các thay đổi tích hợp đã được kiểm tra theo `docs/INTEGRATION.md`
- các thay đổi ảnh hưởng build hoặc triển khai đã được đối chiếu với `docs/DEPLOYMENT.md`
- `docs/CURRENT_STATE.md` đã được cập nhật nếu trạng thái tổng thể, công việc ưu tiên, blocker hoặc bước tiếp theo thay đổi đáng kể

## Cuối Phiên

Trước khi kết thúc phiên:

1. Cập nhật execution plan active với tiến độ, verification, blocker và quyết định mới.
2. Cập nhật `docs/CURRENT_STATE.md` với trạng thái đã được xác minh, phần chưa xác minh, blocker và bước tiếp theo.
3. Cập nhật `docs/QUALITY_SCORE.md` nếu domain, lớp hoặc năng lực kỹ thuật thay đổi có ý nghĩa.
4. Cập nhật hoặc sinh lại tài liệu trong `docs/generated/` nếu source of truth liên quan đã thay đổi.
5. Ghi technical debt mới trong `docs/exec-plans/tech-debt-tracker.md` nếu có công việc bị hoãn.
6. Di chuyển kế hoạch hoàn thành sang `docs/exec-plans/completed/` khi thích hợp.
7. Để repository ở trạng thái có thể khởi động lại với hành động tiếp theo rõ ràng.
