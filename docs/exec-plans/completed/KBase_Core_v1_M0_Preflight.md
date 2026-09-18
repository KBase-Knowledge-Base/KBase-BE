# KBase Core v1 – Active Slice: M0 Preflight & Execution Baseline

## Mục tiêu

Xác minh repository thực tế và khóa technical baseline trước khi bất kỳ agent nào bootstrap hoặc triển khai backend KBase.

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Phạm vi

Bao gồm:

- `M0-01 – Inspect Current Repository`
- `M0-02 – Lock Compatible Technical Versions`
- `M0-03 – Lock Optional Implementation Choices`
- `M0-04 – Register Source Documents in Harness`

Không bao gồm:

- Tạo feature code.
- Tạo database migration.
- Implement auth/OTP/Redis/Gmail/MinIO.
- Implement frontend.

## Tài liệu và Quy tắc Áp dụng

Đọc trước:

- `AGENTS.md`
- `ARCHITECTURE.md`
- `docs/CURRENT_STATE.md`
- `docs/DEVELOPMENT.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 Spring Boot Application Architecture.md`
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md`
- `docs/design-docs/KBase - Core v1 OpenAPI Swagger Configuration Design.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

Nếu repository thực tế hoặc một source document mâu thuẫn với assumption trong active slice, không tự chọn hướng giải quyết. Ghi conflict, cập nhật blocker và đối chiếu source-of-truth trước.

## Tiến độ và kết quả

### M0-01 – Inspect Current Repository

Status: `DONE`

Đã xác minh:

- Repository root là `C:\Learning\Fsoft\KBase`.
- Archive hiện chỉ có tài liệu; không có `pom.xml`, `mvnw`, Gradle files, Java source, test source, Dockerfile, Docker Compose hoặc runtime configuration.
- Không có Git metadata (`git rev-parse --show-toplevel` trả về `not a git repository`).
- Java runtime quan sát được là `21.0.11`; Maven là `3.9.15`; Docker CLI là `29.8.0` nhưng Docker daemon không chạy trong môi trường này.
- Không có PostgreSQL, Redis, MinIO hoặc Gmail configuration implementation để xung đột với design set.
- Đã đối chiếu product spec, architecture, database/JPA, security, service, storage, exception, OpenAPI và testing documents; không tìm thấy unresolved repository/design conflict. Các điểm để mở trong design được chốt riêng tại M0-03, không được suy đoán thành business rule.

### M0-02 – Lock Compatible Technical Versions

Status: `DONE`

Technical baseline đã chốt:

| Thành phần | Phiên bản / lựa chọn | Bằng chứng |
|---|---|---|
| Java | `21` (runtime đã quan sát: `21.0.11`) | `java -version` |
| Spring Boot | `4.1.1` | Maven dependency probe |
| Hibernate ORM | `7.4.5.Final` | Boot-managed dependency tree |
| Spring Data Redis | `4.1.1` | Boot-managed dependency tree |
| Lettuce | `7.5.2.RELEASE` | Boot-managed dependency tree |
| springdoc | `3.1.1` | `springdoc-openapi-starter-webmvc-ui` |
| Testcontainers | `2.0.5` | JUnit Jupiter + PostgreSQL modules |
| MinIO Java SDK | `9.0.3` | `io.minio:minio` |
| JWT | JJWT `0.13.0` (`api`, `impl`, `jackson`) | Maven dependency probe |
| PostgreSQL JDBC | `42.7.13` | Maven dependency probe |
| Flyway | `12.4.0` | PostgreSQL database module |
| MIME detection | Apache Tika `3.3.2` | `org.apache.tika:tika-core` |
| Gmail mail support | `spring-boot-starter-mail` `4.1.1`; Jakarta Mail/Angus managed by Boot | Maven dependency tree |

`mvn -f .m0-dependency-probe/pom.xml validate` và `mvn -f .m0-dependency-probe/pom.xml dependency:tree "-DoutputFile=dependency-tree.txt" "-DoutputType=text"` đều đã chạy với exit code `0`. Probe chỉ là artifact xác minh tạm thời và đã được gỡ khỏi repository sau khi chốt baseline.

### M0-03 – Lock Optional Implementation Choices

Status: `DONE`

| Quyết định | Baseline đã chốt |
|---|---|
| Lombok | Không dùng; giữ Java rõ ràng, đặc biệt không dùng `@Data` trên JPA entity |
| MapStruct | Không dùng; dùng manual mapper qua mapper thuộc feature |
| OSIV | Disabled |
| UUID | `GenerationType.UUID`, phù hợp với application-side UUID generation đã nêu trong persistence design |
| Redis serialization | `StringRedisTemplate` với string/hash state rõ ràng; không Java serialization |
| OTP protection | HMAC-SHA-256 với secret/pepper từ environment và constant-time comparison; raw 6-digit OTP không được lưu |
| OTP boundary | Giữ `OtpStore` abstraction; Redis là ephemeral OTP state, không thêm OTP entity/table |
| JWT | JJWT với HMAC/HS256 và secret từ environment; HS256 phù hợp single-backend baseline; access JWT không chứa project role |
| Refresh-token rotation | Chưa bật trong Core v1; refresh-session hash lưu PostgreSQL theo design |
| Redis integration test | Testcontainers `GenericContainer` cho Redis; không thêm community Redis module |

### M0-04 – Register Source Documents in Harness

Status: `DONE`

Đã tạo `.harness/source-doc-registry.json` với alias ổn định `SD-01` đến `SD-13`, canonical path, authority, precedence, context-depletion/no-guess policy, unresolved-conflict blocking policy và Core v1 backend-only scope guard. Validation đã xác nhận 13 alias duy nhất, precedence rank `1..12` và toàn bộ path tồn tại.

## M0 Gate

Status: `PASS` — ngày `2026-09-17`

- [x] Repository đã được inspect.
- [x] Exact technical versions đã được khóa và dependency graph probe resolve thành công.
- [x] Optional implementation choices đã được ghi.
- [x] Source docs có thể truy xuất ổn định qua `.harness/source-doc-registry.json`.
- [x] Không còn unresolved repository/design conflict.
- [x] `docs/CURRENT_STATE.md` và `docs/DEVELOPMENT.md` phản ánh repository thực tế.

Build/test sản phẩm, Docker runtime, migration và API schema generation chưa chạy vì archive chưa có backend project; đây là giới hạn đã được ghi rõ, không phải acceptance failure của M0. Không có feature code, migration, frontend hoặc M1 implementation nào được tạo.

## Verification record

| Lệnh / kiểm tra | Kết quả |
|---|---|
| `pwd` / `Get-Location` | `C:\Learning\Fsoft\KBase` |
| `java -version` | Pass — `21.0.11` |
| `mvn -version` | Pass — Maven `3.9.15` |
| `docker --version` | Pass — CLI `29.8.0` |
| `docker info` | Chưa pass — daemon unavailable; follow-up cho runtime milestone |
| `mvn -f .m0-dependency-probe/pom.xml validate` | Pass — exit code `0` |
| `mvn -f .m0-dependency-probe/pom.xml dependency:tree "-DoutputFile=dependency-tree.txt" "-DoutputType=text"` | Pass — exit code `0` |
| JSON registry/path validation | Pass — 13/13 documents present |
| Product build/test | Chưa khả dụng — không có Maven project/backend source |
