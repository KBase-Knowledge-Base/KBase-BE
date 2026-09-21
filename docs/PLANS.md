# PLANS.md

Tệp này định nghĩa cách các kế hoạch thực thi được tạo, cập nhật, hoàn thành và lưu trữ.

## Khi Cần Một Kế hoạch

Tạo một kế hoạch thực thi khi công việc:

- trải dài hơn một phiên
- thay đổi nhiều hơn một hệ thống con
- có rủi ro xác minh hoặc triển khai không tầm thường
- phụ thuộc vào các quyết định mở nên được ghi lại

## Vị trí Kế hoạch

- `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`: master roadmap lịch sử của Core v1 đã frozen
- `docs/exec-plans/KBase_AI_Chatbot_v1_Implementation_Plan.md`: master roadmap active của AI v1; giữ dependency/milestone AI, không dùng thay active slice của phiên hiện tại
- `docs/exec-plans/active/`: các kế hoạch slice hiện đang thúc đẩy công việc
- `docs/exec-plans/completed/`: các kế hoạch đã hoàn thành được giữ lại để cung cấp ngữ cảnh cho agent trong tương lai
- `docs/exec-plans/tech-debt-tracker.md`: công việc đã hoãn và các follow-up

## Các Phần Kế hoạch Tối thiểu

- mục tiêu
- phạm vi và ngoài phạm vi
- tài liệu và quy tắc áp dụng
- các hệ thống, domain, layer hoặc contract bị ảnh hưởng
- ảnh hưởng đến trạng thái hiện tại của repository
- tài liệu generated cần cập nhật
- đường dẫn verification
- các command verification lấy từ `docs/DEVELOPMENT.md`
- mức kiểm thử yêu cầu theo `docs/TESTING.md`
- rủi ro và blocker
- kế hoạch migration hoặc rollback nếu cần
- nhật ký tiến độ
- quyết định mở
- kết quả cuối cùng

## Tài liệu Cần Đối chiếu

Mỗi kế hoạch phải xác định những tài liệu thực sự áp dụng:

- `docs/FRONTEND.md`
- `docs/BACKEND.md`
- `docs/DATABASE.md`
- `docs/API_CONVENTIONS.md`
- `docs/INTEGRATION.md`
- `docs/TESTING.md`
- `docs/SECURITY.md`
- `docs/RELIABILITY.md`
- `docs/DEPLOYMENT.md`
- `docs/CURRENT_STATE.md` khi kế hoạch thay đổi trạng thái tổng thể, ưu tiên, blocker hoặc bước tiếp theo
- `docs/generated/db-schema.md` khi database schema thay đổi
- `docs/generated/api-schema.md` khi API contract thay đổi

Chỉ liệt kê những tài liệu thực sự liên quan đến kế hoạch.

## Quy tắc Vận hành

- Master roadmap giữ toàn bộ dependency; AI phase phải làm việc từ active slice trong `docs/exec-plans/active/` và quay lại `KBase_AI_Chatbot_v1_Implementation_Plan.md` khi cần dependency/milestone context. Core master plan chỉ còn là baseline lịch sử trừ khi một task AI chạm Core behavior.
- Một kế hoạch active nên có một bước hiện tại được sở hữu rõ ràng.
- Cập nhật kế hoạch khi công việc tiến triển; đừng coi nó như văn xuôi tĩnh.
- Nếu một quyết định thay đổi hướng triển khai, hãy ghi lại nó trong kế hoạch.
- Di chuyển các kế hoạch đã hoàn thành sang `completed/` để agent vẫn có thể khám phá ngữ cảnh trước đó.
- Khi kế hoạch làm thay đổi trạng thái tổng thể của repository, cập nhật `docs/CURRENT_STATE.md`.
- Khi kế hoạch thay đổi database schema, cập nhật migration và sinh lại `docs/generated/db-schema.md`.
- Khi kế hoạch thay đổi API contract, cập nhật hoặc sinh lại `docs/generated/api-schema.md`.
- Không sao chép toàn bộ execution plan vào `docs/CURRENT_STATE.md`; chỉ tóm tắt trạng thái, blocker, phần chưa xác minh và bước tiếp theo.
- Generated documentation chỉ được xem là hiện tại sau khi đã đối chiếu với source of truth.
- Công việc bị hoãn phải được ghi trong `docs/exec-plans/tech-debt-tracker.md`.
