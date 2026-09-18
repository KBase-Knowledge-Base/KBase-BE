# Quy trình Triển khai

## Mục đích

Tài liệu này mô tả cách build, phát hành, triển khai, kiểm chứng và rollback hệ thống.


## Baseline KBase Hiện tại

- Phase hiện tại chỉ build backend; frontend optional được hoãn.
- Local runtime mục tiêu dùng Docker Compose với `backend`, `postgres`, `minio`, `redis`; Gmail SMTP là external provider.
- PostgreSQL dùng named volume `postgres_data`; MinIO dùng `minio_data`; Redis OTP không cần durable volume.
- Flyway migration nằm trong backend artifact/source và chạy vào PostgreSQL khi backend startup theo cấu hình đã chốt; Hibernate validate schema sau migration.
- Backend container phải stateless đối với durable business data.
- Production deployment target cụ thể chưa được khóa; agent không được tự chọn AWS/Kubernetes/Terraform chỉ vì chúng xuất hiện trong trainer optional stack.

### Trạng thái M1 đã xác minh

M1 đã tạo `docker-compose.yml` cho ba dependency local: `postgres`, `minio` và `redis`. PostgreSQL và MinIO dùng named volume; Redis OTP dùng `tmpfs` cho `/data` và tắt persistence. Backend Dockerfile/full Compose wiring được để dành cho M14. Compose configuration và readiness/restart smoke đã pass với Docker Desktop server `29.8.0`; PostgreSQL marker giữ được qua restart, MinIO readiness trở lại HTTP 200 và Redis key mất sau restart.

### Trạng thái M2 đã xác minh

M2 đã thêm ba Flyway migrations làm schema source-of-truth và xác minh chúng từ database rỗng trên PostgreSQL 17 Testcontainer. Flyway áp dụng V1–V3 thành công; constraint/index PostgreSQL-specific đã được kiểm tra bằng live catalog và Hibernate khởi động với `ddl-auto=validate`. Đây chưa phải backend Compose runtime: migration chưa được chạy qua backend vào `postgres_data` local, việc đó thuộc M14.

### Trạng thái M10 đã xác minh

M10 đã bổ sung storage adapter nhưng không thay đổi Docker topology. Local profile dùng `KBASE_STORAGE_AUTO_CREATE=true` và `KBASE_STORAGE_INITIALIZE_ON_STARTUP=true` để validate/tạo bucket cấu hình khi cần; base/production mặc định là `false`, yêu cầu bucket private được pre-provision. Runtime không thay bucket policy, versioning, retention hay object lock; Core v1 giữ hard-delete semantics với bucket unversioned. `KBASE_STORAGE_ENDPOINT`, access key, secret key, bucket, region và connect/write/read timeout đều externalized; access key/secret không được hard-code. Binary local vẫn nằm tại MinIO `/data` mount từ named volume `minio_data`, đã được giữ nguyên và static-verified; M10 MinIO Testcontainer xác minh stream/range/stat/delete nhưng full backend Compose runtime vẫn thuộc M14.

## Môi trường

Mô tả các môi trường:

- Development.
- Test.
- Staging.
- Production.

Với mỗi môi trường, ghi:

- Mục đích.
- Cách truy cập.
- Nguồn cấu hình.
- Nguồn secret.
- Database hoặc dependency liên quan.
- Người hoặc vai trò có quyền thay đổi.

## Pipeline tiêu chuẩn

Pipeline nên bao gồm:

1. Cài dependency.
2. Format, lint và type-check.
3. Build.
4. Unit test.
5. Integration hoặc contract test.
6. Security scan.
7. Tạo artifact.
8. Chạy migration theo chính sách.
9. Deploy.
10. Health check.
11. Smoke test.
12. Theo dõi sau triển khai.

Điều chỉnh theo dự án nhưng không bỏ verification mà không ghi lý do.

## Artifact và version

- Artifact phải gắn với commit hoặc version cụ thể.
- Ưu tiên dùng cùng một artifact qua các môi trường.
- Không đóng cứng cấu hình môi trường trong source code.
- Secret phải lấy từ cơ chế quản lý secret được phê duyệt.
- Có thể xác định chính xác phiên bản đang chạy.

## Database migration

- Migration phải tuân theo `docs/DATABASE.md`.
- Thay đổi rủi ro phải có backup hoặc recovery plan.
- Ưu tiên migration tương thích ngược.
- Có thể tách deploy code và cleanup schema thành nhiều giai đoạn.
- Không tự động chạy thao tác phá hủy nếu chưa được phê duyệt.

## Rollback

Mô tả:

- Điều kiện kích hoạt rollback.
- Cách rollback application.
- Cách xử lý migration không thể rollback.
- Cách khôi phục cấu hình.
- Cách xác nhận hệ thống đã hồi phục.
- Ai có quyền thực hiện.

## Post-deploy verification

- Health check.
- Smoke test.
- Golden journey quan trọng.
- Error rate.
- Log.
- Metrics.
- Alert.
- Compatibility với client và integration.

## Giới hạn đối với AI Agent

AI agent không được tự:

- Deploy production.
- Thay secret.
- Chạy destructive migration.
- Xóa dữ liệu.
- Thay quyền truy cập.
- Rollback production.

Các hành động trên chỉ được thực hiện khi có yêu cầu và phê duyệt rõ ràng.
