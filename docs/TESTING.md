# Chiến lược Kiểm thử

## Mục tiêu

Kiểm thử phải cung cấp bằng chứng rằng hành vi quan trọng đúng, thay đổi không phá vỡ hệ thống và lỗi có thể được phát hiện sớm.


## Phạm vi KBase Hiện tại

- Phase hiện tại là backend-only; frontend component/E2E test được hoãn cho đến khi frontend được mở lại.
- Unit/API/security test dùng Java/Spring test stack được khóa tại M0.
- PostgreSQL integration phải dùng PostgreSQL Testcontainers cho Flyway, constraint, repository và transaction behavior.
- Redis OTP integration phải dùng Redis Testcontainers cho TTL, attempts, cooldown, replacement và failure mapping.
- MinIO integration phải dùng MinIO Testcontainers cho streaming upload/read/range/delete và compensation behavior.
- Automated mail tests không gửi Gmail thật; dùng mock `MailService` hoặc fake/local SMTP tùy test layer.
- Authorization matrix `non-member / MEMBER / OWNER / ADMIN`, former-member access, cross-project isolation và unverified-user login là critical regression coverage.
- Chi tiết đầy đủ nằm trong `docs/design-docs/KBase - Core v1 Testing Strategy.md`.

## Các cấp kiểm thử

### Unit test

Dùng cho:

- Logic nhỏ và deterministic.
- Business rule cô lập.
- Mapping hoặc validation phức tạp.
- Utility có hành vi quan trọng.

### Integration test

Dùng cho:

- Database và repository.
- Framework configuration.
- External adapter.
- Queue, cache hoặc file storage.
- Transaction behavior.

### API hoặc Contract test

Dùng cho:

- Endpoint.
- Request và response schema.
- Validation.
- Error format.
- Authentication và authorization.
- Compatibility.

### Frontend test

Dùng cho:

- Component behavior.
- Form.
- State transition.
- Loading và error state.
- Interaction quan trọng.

### End-to-end test

Dùng cho:

- Golden journey.
- Luồng xuyên qua nhiều layer.
- Tích hợp frontend, backend và database.
- Luồng có rủi ro nghiệp vụ cao.

### Non-functional test

Khi cần:

- Security.
- Performance.
- Accessibility.
- Reliability.
- Migration.
- Compatibility.

## Nguyên tắc

- Test hành vi thay vì implementation detail khi có thể.
- Test phải độc lập và có thể chạy lặp.
- Không phụ thuộc vào thứ tự chạy.
- Dữ liệu test phải được kiểm soát.
- Mock chỉ dùng tại boundary hợp lý.
- Bug fix nên có regression test khi khả thi.
- Không bỏ qua test lỗi mà không ghi lý do.
- Flaky test phải được sửa hoặc theo dõi như technical debt.
- Không dùng retry vô hạn để che flaky test.

## Ma trận verification tối thiểu

| Loại thay đổi | Verification tối thiểu |
|---|---|
| Logic đơn lẻ | Unit test |
| Repository hoặc database | Integration test và migration check |
| API contract | API hoặc contract test |
| UI behavior | Frontend test hoặc E2E |
| Cross-stack flow | Integration test và E2E |
| Security-sensitive | Negative test và authorization test |
| Performance-sensitive | Benchmark hoặc load test phù hợp |

## Bằng chứng

Execution plan hoặc kết quả task phải ghi:

- Test đã thêm hoặc sửa.
- Lệnh đã chạy.
- Kết quả.
- Phần chưa kiểm chứng.
- Lý do nếu không thể chạy.

Các command chuẩn phải được khai báo trong `docs/DEVELOPMENT.md`.
