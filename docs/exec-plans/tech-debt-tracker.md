# Tracker Nợ Kỹ thuật

Sử dụng tệp này cho nợ thực, đã được thừa nhận và có chủ ý bị hoãn.

| Ngày | Khu vực | Nợ | Lý do Hoãn | Rủi ro | Kích hoạt Tiếp theo |
|------|------|------|--------------|------|--------------| 
| 2026-09-17 | Database docs | `docs/generated/db-schema.md` được tái sinh thủ công trong cùng thay đổi migration thay vì bằng generator tự động | M2 chỉ tạo schema, chưa có JPA entity/runtime để chạy generator schema tự động | Docs có thể lệch nếu migration đổi mà quên tái sinh | Milestone JPA (M3) khi thiết lập generator hoặc quy trình sinh schema |
| 2026-09-17 | Testing | Hibernate `ddl-auto=validate` trong M2 chạy với persistence surface chưa có entity — **RESOLVED 2026-09-17**: M3/JPA-11 đã chạy validate với đầy đủ entity trên PostgreSQL Testcontainer | Thứ tự milestone theo master plan: schema trước, mapping sau | Drift Flyway/JPA hiện đã được kiểm tra bằng mapping integration suite | M3/JPA-11 đã hoàn tất; theo dõi regression ở các thay đổi persistence sau |
| 2026-09-17 | Testing | `OTP_SERVICE_UNAVAILABLE` (503) cho auth API mới verify ở adapter level (`RedisOtpStoreIntegrationTest`) và unit level (`EmailVerificationServiceTest` propagate); chưa có full-context auth test với Redis chết | Tránh khởi động thêm một `@SpringBootTest` context (PostgreSQL+Redis containers) cho cùng error mapping đã chứng minh; tăng thời gian suite đáng kể | Nếu mapping giữa `OtpStore`/`OtpService` và auth flow thay đổi, 503 contract có thể lệch mà test không bắt được | Khi M7+ chạm lại auth error mapping hoặc khi có cơ chế shared/context reuse cho testcontainers |
| 2026-09-17 | Docs | `docs/generated/api-schema.md` phần auth endpoints (M6) được đồng bộ thủ công từ source code đã verify; generator springdoc runtime vẫn chưa thiết lập | OpenAPI runtime generation thuộc M13 theo master plan | Endpoint schema có thể lệch nếu code đổi mà quên sync thủ công | M13 (API-DOC-01..07) khi `/v3/api-docs` contract tests được thiết lập |
| YYYY-MM-DD | `[khu vực]` | `[nợ]` | `[lý do]` | `[rủi ro]` | `[khi nào xem xét lại]` |
