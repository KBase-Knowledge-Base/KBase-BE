# KBase Core v1 – Completed Slice: M10 MinIO Storage Infrastructure

Status: `COMPLETED` — M10 Gate `PASS` ngày `2026-09-18`; archived after Gate

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M9_Folder_Category_Tag.md` (`M9 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Xây storage infrastructure private, streaming và S3-compatible cho Core v1 sau boundary `StorageService -> MinioStorageService`, sẵn sàng để M11 orchestration document lifecycle sử dụng nhưng không triển khai document API/lifecycle trong slice này.

## Phạm vi

- `STORAGE-01`: storage models và internal storage exception hierarchy.
- `STORAGE-02`: `StorageService` port không chứa authorization/domain logic.
- `STORAGE-03`: singleton `MinioClient` từ typed, environment-backed configuration.
- `STORAGE-04`: bucket initializer theo config; local auto-create, production pre-provisioned; không rewrite policy/versioning/retention.
- `STORAGE-05`: upload/get/stat/delete streaming và SDK exception translation.
- `STORAGE-06`: ranged read offset/length.
- `STORAGE-07`: batch delete consume từng per-object result và report partial failure.
- `STORAGE-08`: unit + real MinIO Testcontainer integration tests, config/Compose persistence verification.
- Cập nhật config, `.env.example`, `docs/INTEGRATION.md`, `docs/DEVELOPMENT.md`, state/quality/plan evidence phù hợp.

## Ngoài phạm vi

- M11 document upload/download/preview endpoints, `DocumentService`, `DocumentAuthorizationService`, upload validation, metadata persistence orchestration, compensation hoặc project hard delete.
- M12 search, M13 runtime OpenAPI và M14 full backend Compose/runtime wiring.
- Frontend, PostgreSQL/Flyway schema, Redis/Gmail/auth/project/membership/invitation/folder/category/tag behavior.
- Public bucket, presigned URL/PUT, object policy/versioning/retention/object-lock mutation.

## Rules và quyết định khóa

- PostgreSQL giữ business metadata; MinIO chỉ giữ binary. Business/application layer chỉ biết `StorageService`, không biết `MinioClient` hay SDK model/exception.
- Một private bucket configurable cho mỗi environment; không có bucket theo project. Local auto-create được phép; production `auto-create=false` và bucket được pre-provision.
- Bucket versioning phải disabled cho Core v1. Adapter chỉ verify/create bucket; không gọi APIs đổi bucket policy, versioning, retention hay object lock.
- `StorageKeyFactory` tạo key backend-controlled `projects/{projectId}/documents/{documentId}.{extension}` từ UUID + validated lowercase extension; không dùng original filename, folder/category/tag.
- Upload/get/getRange phải trả/nhận `InputStream`, không `byte[]`/`readAllBytes`; caller đóng stream. Range nhận `offset >= 0`, `length > 0`.
- `deleteAll` luôn consume toàn bộ iterable kết quả của MinIO; bất cứ failed result nào sau khi đã xử lý các kết quả khác đều làm operation fail với internal `StorageDeleteException`.
- Storage exception chỉ là internal adapter exception; không leak raw MinIO/S3 error, endpoint hay credential. M11 sẽ map operation-specific storage failure sang public KBase `ErrorCode`.

## Tài liệu/source-of-truth

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`, `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`.
- `docs/product-specs/KBase - Core v1 Specification.md`.
- `docs/design-docs/KBase - Core v1 MinIO Integration Design.md` (§2–18, §23, §29–33, §40–65).
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` (§70–71).
- `docs/design-docs/KBase - Core v1 Exception Handling Design.md` (§42–46, §111, §118–119).
- `docs/design-docs/KBase - Core v1 Testing Strategy.md` (§60–62, §100.1, §123–124).
- `docs/BACKEND.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`, `docs/DATABASE.md`.

## Affected layers/contracts

- `storage` package: config, port, MinIO adapter, model, exception and key factory.
- Typed `StorageProperties`, profile/config templates and Compose documentation only.
- No REST endpoint, database contract or generated API/DB schema change expected in M10.
- `docker-compose.yml` already owns local `minio_data:/data`; M10 verifies rather than changes this persistence model unless a config defect is discovered.

## Verification path

1. Baseline before code: `mvn -B -ntp test` — **PASS 161/161**.
2. Targeted unit/config tests for model/key/exception translation/batch partial failure.
3. `mvn -B -ntp "-Dtest=StorageKeyFactoryTest,MinioStorageServiceTest,ConfigurationPropertiesBindingTest" test`.
4. `mvn -B -ntp "-Dtest=MinioStorageIntegrationTest" test` with real MinIO Testcontainer.
5. `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test` regression.
6. `mvn -B -ntp test` and `mvn -B -ntp clean verify`.
7. Static review: no MinIO SDK outside `storage` adapter/config/test, no document API/M11, no direct controller or business dependency, no policy/versioning/retention mutation, Compose `minio_data` preserved.

## M10 acceptance checklist

- [x] `StorageService` exists and contains only storage operations.
- [x] `MinioStorageService` implements port; `MinioClient` is singleton-configured.
- [x] Local configured bucket initialization works; production config does not auto-create/mutate bucket settings.
- [x] Private bucket / disabled-versioning boundary preserved.
- [x] Stream upload, full read, range read, stat and single delete work through real MinIO.
- [x] `deleteAll` consumes per-object results, attempts all entries and rejects partial failures.
- [x] Missing object and unavailable storage translate to typed internal storage exceptions without SDK leak.
- [x] Object-key strategy is deterministic and excludes user filename/organization metadata.
- [x] MinIO Testcontainer integration and M0–M9 regression pass; `minio_data` persistence config remains present.

## Risks/blockers

- No functional blocker from M9 Gate. Archive has no Git metadata, so evidence is code/tests/plans rather than commits.
- MinIO Testcontainer image may require a first pull; tests use fake test credentials and local Docker only.
- Startup bucket validation must stay disabled in ordinary `test` profile so existing non-storage Spring contexts never require MinIO.

## Migration/rollback

No Flyway migration or data rewrite. Roll back feature/config/docs together if verification fails. Do not delete `minio_data`, alter bucket policy or alter production bucket settings.

## Progress log

- `2026-09-18`: confirmed M9 Gate PASS (M9 plan + current state, full suite 161/161) and no functional blocker; opened M10 under explicit authorization.
- `2026-09-18`: reread storage architecture/design/testing/exception/integration/deployment sources and ran baseline `mvn -B -ntp test` 161/161 pass.
- `2026-09-18`: implemented `StorageService`, streaming MinIO adapter, typed internal exceptions, `StorageKeyFactory`, bounded client timeouts and conditional local bucket initializer. Added local/test config boundary without changing database, APIs, authentication or organization behavior.
- `2026-09-18`: verified targeted storage/config tests 7/7, real `MinioStorageIntegrationTest` 2/2, full `mvn -B -ntp test` 169/169 and final `mvn -B -ntp clean verify` 169/169, all with 0 failures/errors/skips.

## Open decisions

- Storage exceptions remain internal adapter exceptions rather than choosing document/project HTTP error codes prematurely; M11 application orchestration owns operation-specific public mapping.

## Final result

`M10 Gate: PASS`. `StorageService` is a vendor-neutral, authorization-free streaming port. `MinioStorageService` isolates all SDK use and translates provider failures to internal storage exceptions. Bucket initialization is config-driven: local can create/validate, test disables startup validation, and base/production defaults do not auto-create. The adapter never changes policy, versioning, retention or object-lock; the real MinIO test confirms the initialized bucket is not versioning-enabled. Object keys are backend-created as `projects/{projectId}/documents/{documentId}.{extension}` with a lower-case alphanumeric extension. `get`/`getRange` return closeable streams, and batch delete consumes every MinIO result before surfacing partial failure.

No database migration, REST endpoint, generated DB schema or generated API schema changed. No blocker remains. M11 has not been started in this session.
