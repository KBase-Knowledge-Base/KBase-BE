# KBase Core v1 – Active Slice: M9 Folder / Category / Tag

Status: `DONE` — hoàn tất ngày `2026-09-18`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M8_Invitation_Lifecycle.md` (`M8 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Triển khai organization metadata project-scoped cho backend Core v1: folder hierarchy, category lifecycle và tag lifecycle, giữ nguyên các project/membership/authentication/invitation behavior đã verify ở M0–M8.

## Phạm vi

- FOLDER-01..04: DTO/mapper, list/create/update-move/delete folder.
- CAT-01: list/create/rename/delete category.
- TAG-01: list/create/rename/delete tag.
- ORG-TEST: authorization matrix, cycle/integrity/uniqueness/non-empty/in-use/tag relation tests.
- Cập nhật API generated docs, repository state/quality và verification evidence.

## Ngoài phạm vi

- M10 MinIO/storage.
- M11 document upload/lifecycle, document metadata mutation hoặc project hard delete.
- M12 search, M13 OpenAPI runtime, M14 deployment/full runtime wiring.
- Frontend.
- Thay đổi authentication, project membership, invitation hoặc Flyway schema.

## Rules và quyết định áp dụng

- Folder/category/tag đều project-scoped; ADMIN là system-level override, không tạo fake ProjectMember.
- Folder structure và category management chỉ OWNER/ADMIN; MEMBER chỉ list/read.
- Folder hỗ trợ nested hierarchy; cùng sibling không phân biệt hoa thường, khác parent hợp lệ.
- Parent folder phải resolve bằng `findByIdAndProjectId`; self/descendant bị từ chối bằng `FOLDER_CYCLE_DETECTED`.
- Cycle prevention dùng ancestor walk theo `parentId` trong transaction; DB `CHECK` vẫn chặn self-parent và composite FK vẫn chặn cross-project parent.
- Folder delete chỉ khi không có child và không có document; category delete chỉ khi không có document sử dụng.
- Tag MEMBER được create; chỉ OWNER/ADMIN rename/delete. Xóa tag dựa vào FK cascade trên `document_tags`, không xóa document.
- Service pre-check + PostgreSQL expression/partial unique indexes bảo vệ case-insensitive uniqueness; known unique constraint errors map qua `ConstraintViolationTranslator`.

## Tài liệu/source-of-truth

- `AGENTS.md`, `ARCHITECTURE.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 Entity Analysis & ERD.md`
- `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- `docs/design-docs/KBase - Core v1 REST API Specification.md`
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md`
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md`
- `docs/design-docs/KBase - Core v1 Exception Handling Design.md`
- `docs/design-docs/KBase - Core v1 Testing Strategy.md`
- `docs/BACKEND.md`, `docs/DATABASE.md`, `docs/API_CONVENTIONS.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/SECURITY.md`, `docs/RELIABILITY.md`, `docs/DEPLOYMENT.md`

## Affected layers/contracts

- `folder`, `category`, `tag` DTO/mapper/service/controller/repository.
- Shared organization error mapping only; không đổi security filter hay existing authorization contract.
- API contract adds the twelve M9 endpoints and organization DTO/error documentation.
- Database schema/migrations không thay đổi; same-project composite FK và uniqueness indexes phải giữ nguyên.

## Verification path

1. `mvn -B -ntp "-Dtest=FolderServiceTest,CategoryServiceTest,TagServiceTest" test`
2. `mvn -B -ntp "-Dtest=OrganizationIntegrationTest" test`
3. `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test`
4. `mvn -B -ntp test`
5. `mvn -B -ntp clean verify`
6. Static scope review: no frontend, no M10/M11 implementation, no controller→repository direct access, no weakening of PostgreSQL/JPA same-project constraints.

## M9 acceptance checklist

- [x] MEMBER/OWNER/ADMIN list folder/category/tag đúng quyền.
- [x] MEMBER bị từ chối create/update/delete folder và category.
- [x] OWNER/ADMIN tạo và quản lý folder/category được.
- [x] Folder duplicate root/sibling case-insensitive conflict; same name khác parent hợp lệ.
- [x] Cross-project parent, self-parent và descendant move bị reject.
- [x] Folder có child/document trả `FOLDER_NOT_EMPTY`; folder rỗng delete thành công.
- [x] Category duplicate case-insensitive conflict; category đang dùng trả `CATEGORY_IN_USE`.
- [x] MEMBER tạo tag được; MEMBER rename/delete tag bị từ chối; OWNER/ADMIN rename/delete tag được.
- [x] Delete tag chỉ gỡ `DocumentTag`, document vẫn tồn tại.
- [x] Cross-project integrity regression và M0–M8 regression pass.

## Task sequence và kết quả

| Task | Trạng thái | Evidence |
|---|---|---|
| `FOLDER-01` | `DONE` | `CreateFolderRequest`, `UpdateFolderRequest`, `FolderResponse`, `FolderMapper` |
| `FOLDER-02` | `DONE` | `FolderService`/`FolderController`: list flat hoặc theo parent, create OWNER/ADMIN, same-project parent, sibling uniqueness |
| `FOLDER-03` | `DONE` | Rename/move, explicit move-to-root, ancestor-walk cycle prevention, duplicate sibling exclusion |
| `FOLDER-04` | `DONE` | Child/document pre-check, `FOLDER_NOT_EMPTY`, empty-folder delete |
| `CAT-01` | `DONE` | Category CRUD, OWNER/ADMIN management, project uniqueness, `CATEGORY_IN_USE` |
| `TAG-01` | `DONE` | Tag list/create for project members, OWNER/ADMIN rename/delete, DocumentTag-only cascade |
| `ORG-TEST` | `DONE` | `OrganizationIntegrationTest` 6/6 + service unit tests 12/12 |

## Risks/blockers

- Không có blocker sau M8 Gate. Archive không có Git metadata nên evidence dựa trên code, plan và test results.
- Concurrent duplicate writes vẫn do PostgreSQL unique indexes quyết định cuối cùng; service pre-check chỉ cải thiện lỗi thông thường.

## Migration/rollback

Không có migration. Rollback feature code/docs theo slice nếu verification không đạt; không đụng các migration V1–V3.

## Progress log

- `2026-09-18`: M8 Gate verified from current state (`mvn -B -ntp test` baseline 143/143); M9 slice opened.
- `2026-09-18`: source/design/router preflight completed; implementation constrained to FOLDER-01..04, CAT-01, TAG-01 and ORG-TEST; no frontend, M10 or M11 work started.
- `2026-09-18`: implemented folder/category/tag DTOs, mappers, repositories, services and controllers. Folder hierarchy is project-scoped; category and tag are project-scoped; existing project/membership/authentication/invitation behavior is unchanged.
- `2026-09-18`: enforced OWNER/ADMIN folder/category management, MEMBER read-only folder/category access, MEMBER tag creation, OWNER/ADMIN tag rename/delete, case-insensitive uniqueness and all delete dependency rules.
- `2026-09-18`: added cycle prevention by walking proposed-parent ancestors with a visited set; PostgreSQL composite same-project FKs, self-parent CHECK and existing expression/partial unique indexes remain authoritative database constraints.
- `2026-09-18`: added unit/integration coverage for authorization, hierarchy, uniqueness, cross-project isolation, non-empty/in-use deletes and DocumentTag cascade behavior.
- `2026-09-18`: M9 targeted unit 12/12, organization integration 6/6, cross-project/migration regression 23/23, full suite 161/161 and clean verify pass.

## Open decisions

- Không có. PATCH folder dùng request object có presence flag để phân biệt parent bị bỏ qua với explicit `parentId: null` khi move về root.

## Final result

M9 đã hoàn tất toàn bộ `FOLDER-01..04`, `CAT-01`, `TAG-01` và `ORG-TEST`. Backend cung cấp 12 endpoint project-scoped cho folder/category/tag với đúng ma trận MEMBER/OWNER/ADMIN; folder cycle/parent/uniqueness/delete rules, category in-use protection và tag relation cascade đã được xác minh. Không có migration/schema change, không thay đổi auth/project/membership/invitation, không implement document upload/lifecycle, MinIO, M10/M11 hoặc frontend.

## Verification path và kết quả (chốt)

- `mvn -B -ntp "-Dtest=FolderServiceTest,CategoryServiceTest,TagServiceTest" test`: **12/12 pass**.
- `mvn -B -ntp "-Dtest=OrganizationIntegrationTest" test`: **6/6 pass** trên PostgreSQL 17 + Redis Testcontainers với real SecurityFilterChain; cover list permissions, OWNER/ADMIN management, MEMBER restrictions, duplicate root/sibling names, same-name different parent, cross-project parent, self/descendant cycle, child/document non-empty deletion, category in-use, MEMBER tag create, tag mutation authorization and DocumentTag-only delete.
- `mvn -B -ntp "-Dtest=FlywayMigrationIntegrityTest,JpaMappingRepositoryIntegrationTest" test`: **23/23 pass**; same-project composite FK, self-parent CHECK, indexes and JPA mappings remain valid.
- `mvn -B -ntp test`: **161/161 pass**, 0 failures, 0 errors, 0 skipped.
- `mvn -B -ntp clean verify`: **BUILD SUCCESS**, 161/161 pass; compile, package and Spring Boot repackage pass.
- Static scope review: no frontend/M10/M11 implementation, no migration change, no controller-to-repository access, no weakening of PostgreSQL/JPA same-project constraints, and no changes to auth/project/membership/invitation behavior.

## M9 Gate

Status: **`PASS` — 2026-09-18**

- [x] Organization rules implemented according to the product/design source-of-truth.
- [x] MEMBER/OWNER/ADMIN list and mutation permissions verified.
- [x] Folder hierarchy, same-project parent validation, cycle prevention and non-empty deletion verified.
- [x] Case-insensitive folder sibling, category project and tag project uniqueness verified.
- [x] Category in-use protection and tag DocumentTag-only deletion verified.
- [x] Cross-project integrity and M0–M8 regression suites pass.
- [x] API schema, repository state, quality score and development verification records updated.

## Closeout

- Active slice is moved to `docs/exec-plans/completed/KBase_Core_v1_M9_Folder_Category_Tag.md`.
- `docs/exec-plans/active/index.md` no longer opens M10; the next milestone remains deferred until a later authorized session.
- Archive limitation remains: this harness has no Git metadata, so evidence is based on source code, test output and execution-plan records.
