# Chỉ mục Product Spec

Sử dụng thư mục này cho các spec hành vi dành cho người dùng hiện tại.

## Spec Active

- `KBase - Core v1 Specification.md`: nguồn sự thật sản phẩm đã frozen cho KBase Core v1 backend
- `KBase - AI Chatbot v1 Specification.md`: nguồn sự thật sản phẩm active cho Project Assistant + KBase Guide của AI v1

## Mẫu / Không Active

- `new-user-onboarding.md`: template của starter harness, không phải spec KBase và không được dùng để suy ra hành vi sản phẩm hiện tại

## Quy tắc

- Spec nên mô tả hành vi có thể nhìn thấy của người dùng và tiêu chí chấp nhận.
- Nếu triển khai khác với spec, hãy cập nhật một trong số chúng trong cùng phiên.
- Giữ chỉ mục này hiện tại để agent mới có thể nhanh chóng khám phá phạm vi sản phẩm.
- Phase hiện tại triển khai backend AI v1 trên Core v1 đã frozen; frontend optional/deferred không được suy ra thành công việc active nếu execution plan chưa mở phạm vi đó.
