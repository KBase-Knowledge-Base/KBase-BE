# KBase Core v1 – Active Slice: M7 User / Project / Membership

Status: `DONE` — hoàn tất ngày `2026-09-18`

Predecessor: `docs/exec-plans/completed/KBase_Core_v1_M6_Spring_Security_Authentication.md` (`M6 Gate: PASS`)

Master plan: `docs/exec-plans/KBase_Core_v1_Implementation_Plan.md`

## Mục tiêu

Triển khai main collaboration/security boundary: current-user APIs, admin user APIs, project create/list/get/update + admin project listing, và membership list/remove/leave với `ProjectAuthorizationService` làm trung tâm authorization domain. M7 không triển khai invitation, folder/category/tag, document/MinIO, search, OpenAPI runtime hoặc frontend.

## Phạm vi M7

Bao gồm:

- `USER-01` — User DTOs/Mapper: `UserResponse` (id, email, displayName, systemRole, status, derived `emailVerified`, createdAt, updatedAt); không expose `passwordHash`; `emailVerifiedAt` chỉ dưới dạng derived boolean.
- `USER-02` — Current user APIs: `GET /api/v1/users/me`, `PATCH /api/v1/users/me` (chỉ displayName), `PUT /api/v1/users/me/password` (verify current → hash mới → revoke refresh sessions theo security baseline).
- `USER-03` — Admin user APIs: `GET /admin/users` (q/status/systemRole/page/size/sort), `GET /admin/users/{id}`, `PATCH /admin/users/{id}/status` (INVALID_USER_STATUS cho giá trị lạ; DISABLED → revokeAllForUser), `DELETE /admin/users/{id}` (dependency rules; không cascade project knowledge).
- `PROJ-01` — Project DTOs/Mapper: `ProjectResponse` (currentUserRole nullable cho ADMIN non-member), `ProjectMemberResponse`, `CreateProjectRequest`, `UpdateProjectRequest`.
- `PROJ-02` — `ProjectAuthorizationService.requireProjectAccess`/`requireOwner` với ADMIN override, không tạo fake ProjectMember; trả `ProjectAccess(project, role nullable, admin)`.
- `PROJ-03` — Project APIs: `POST /projects` (Project + OWNER membership trong một transaction), `GET /projects` (chỉ membership projects, kể cả ADMIN; filter q/role), `GET /projects/{projectId}` (currentUserRole từ membership, null cho ADMIN non-member), `PATCH /projects/{projectId}` (OWNER/ADMIN). **Project hard delete (DELETE /projects/{projectId}) không thuộc M7** — master plan `PROJ-03`/`PROJ-DELETE-01` chuyển nó sang sau MinIO integration (M11).
- `PROJ-ADMIN-01` — `GET /api/v1/admin/projects` (q/ownerId/page/size/sort; ADMIN xem toàn bộ project, currentUserRole=null) — SD-08 §25 giao trách nhiệm này cho ProjectService và đề bài yêu cầu admin endpoint riêng thay cho việc nới lỏng `GET /projects`.
- `MEM-01` — `GET /projects/{projectId}/members` (MEMBER/OWNER/ADMIN; paginated).
- `MEM-02` — `DELETE /projects/{projectId}/members/{userId}` (OWNER/ADMIN; target OWNER → 409; documents remain).
- `MEM-03` — `DELETE /projects/{projectId}/members/me` (MEMBER only; OWNER → 409; documents remain).
- `M7-TEST` — unit tests cho services + PostgreSQL integration/authz matrix test.

Không bao gồm:

- Invitation lifecycle (M8), folder/category/tag (M9), MinIO/document (M10/M11), search (M12), OpenAPI (M13);
- `DELETE /api/v1/projects/{projectId}` (M11 sau MinIO theo master plan);
- Ownership transfer, fake ADMIN ProjectRole, `Project.ownerId`, OTP/auth flow changes;
- Frontend.

## Tài liệu và quy tắc áp dụng

- `AGENTS.md`, `ARCHITECTURE.md`, `docs/PLANS.md`
- `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md`
- `docs/BACKEND.md`, `docs/SECURITY.md`, `docs/TESTING.md`, `docs/API_CONVENTIONS.md`
- `docs/product-specs/KBase - Core v1 Specification.md`
- `docs/design-docs/KBase - Core v1 REST API Specification.md` (SD-04, section 11 pagination, 14–18)
- `docs/design-docs/KBase - Core v1 Service Layer Detailed Design.md` (SD-08, section 16–34)
- `docs/design-docs/KBase - Core v1 JPA Entity Mapping Repository Design.md` (pagination/sort whitelist, projection patterns)
- `docs/design-docs/KBase - Core v1 Spring Security JWT Design.md` (ADMIN override, authorization layering)
- `docs/design-docs/KBase - Core v1 Testing Strategy.md` (#49–#50 authorization matrix)
- `.harness/source-doc-registry.json`

## Quy tắc business không được tái diễn giải

- ADMIN là system role; OWNER/MEMBER là project role trong `project_members.role`.
- `Project` không có `ownerId`; mỗi project đúng một OWNER (partial unique index `uq_project_members_single_owner`).
- `GET /projects` chỉ trả project user đang tham gia, kể cả khi caller là ADMIN; ADMIN xem toàn bộ qua `/api/v1/admin/projects`.
- `ProjectResponse.currentUserRole` nullable khi ADMIN truy cập mà không phải member; không tạo fake role.
- MEMBER không update/delete project; OWNER/ADMIN được quản lý project.
- Remove/leave MEMBER không xóa documents; `Document.uploadedBy` tham chiếu `User`.
- OWNER không leave; không ownership transfer; OWNER không thể bị remove qua member endpoint (kể cả bởi ADMIN — giữ bất biến single-OWNER).
- User hard delete: chặn theo thứ tự OWNER dependency (USER_OWNS_PROJECT) → uploaded documents → memberships → invitation references (USER_HAS_DEPENDENCIES); refresh_sessions cascade theo schema, không cascade project knowledge.
- Password change: verify current password, revoke refresh sessions (security baseline SD-07 §52).
- Admin disable user: revoke refresh sessions; re-enable không reactivate session cũ.

## Thiết kế triển khai đã khóa

- Shared pagination: `shared/pagination.PageResponse` (content/page/size/totalElements/totalPages/first/last) + `shared/pagination.PaginationParser` (page=0, size=20, max 100, sort "field,direction" với whitelist per resource; sort field ngoài whitelist → VALIDATION_ERROR; clamp size>100 và page âm).
- List my projects: projection query mới `ProjectMemberRepository.findMembershipPageForUser(userId, role, q, pageable)` trả `Page<ProjectMembershipProjection>` (project + role) để map `currentUserRole` đúng per-row; admin list dùng `ProjectSpecification` (q ILIKE name/description + ownerId subquery role=OWNER).
- Admin user list dùng `UserSpecification` (q trên email/displayName + status + systemRole) qua `JpaSpecificationExecutor` đã có từ M3.
- Authorization service trả `ProjectAccess` để tránh query lặp trong một operation; service domain gọi `requireProjectAccess`/`requireOwner`, không query membership trực tiếp (trừ service sở hữu membership lifecycle).
- `ChangeUserStatusRequest` nhận status dạng string và parse trong service để trả đúng `400 INVALID_USER_STATUS` thay vì lỗi JSON binding chung.
- Password change dùng `PasswordEncoder.matches` + revokeAllForUser trong cùng transaction.
- Sort whitelist: users (email, displayName, createdAt, updatedAt), projects (name, createdAt, updatedAt), members (joinedAt, email, displayName); default users/members và projects = createdAt/joinedAt hợp lý, ghi rõ trong plan.
- Không đổi authentication/OTP/Redis/Gmail flow, security filter chain, error infrastructure; chỉ thêm DTO/service/controller/repository query mới.

## Task sequence và acceptance

| Task | Trạng thái | Acceptance chính |
|---|---|---|
| `USER-01` | `TODO` | UserResponse/UserMapper không expose hash; emailVerified derived |
| `USER-02` | `TODO` | GET/PATCH me + password change đúng contract, revoke sessions |
| `USER-03` | `TODO` | Admin list/get/status/delete theo dependency rules |
| `PROJ-01` | `TODO` | DTOs + mapper, currentUserRole nullable |
| `PROJ-02` | `TODO` | requireProjectAccess/requireOwner + ADMIN override, ProjectAccess |
| `PROJ-03` | `TODO` | Create tx Project+OWNER; list membership-only; get/update theo role |
| `PROJ-ADMIN-01` | `TODO` | Admin project listing với q/ownerId |
| `MEM-01..03` | `TODO` | List/remove/leave theo matrix; documents remain |
| `M7-TEST` | `TODO` | Unit + PostgreSQL authorization matrix pass |

## Verification path

- Unit: `UserServiceTest`, `ProjectAuthorizationServiceTest`, `ProjectServiceTest`, `ProjectMemberServiceTest` (mock repositories; bẫy dependency rules và role matrix).
- Integration: `UserProjectMembershipIntegrationTest` (@SpringBootTest + MockMvc + PostgreSQL Testcontainer + real filter chain): creator OWNER trong cùng tx, single-OWNER, non-member 403, MEMBER read-only, OWNER/ADMIN manage, remove/leave rules, documents remain sau remove/leave (Document tạo trực tiếp qua repository), user APIs + admin user APIs + password change revoke sessions.
- Regression: `mvn -B -ntp test`, `mvn -B -ntp clean verify` (104 baseline + M7 suite).
- Static review: không `Project.ownerId`, không fake role, không cascade bừa, không auth/OTP change.

Standard commands từ `docs/DEVELOPMENT.md`: `mvn -B -ntp test`, `mvn -B -ntp clean verify`.

## Rủi ro và blocker

- Không có blocker từ M6 (Gate PASS, Docker available).
- Document-upload flow chưa tồn tại (M10/M11) nên "documents remain" được verify bằng cách seed Document trực tiếp qua repository trong integration test.
- Project hard delete deferred M11 — phải ghi rõ trong state docs để agent sau không tưởng M7 đã xong toàn bộ project API.

## Generated docs / state

- Không đổi schema → `docs/generated/db-schema.md` không cần sinh lại.
- API contract thay đổi (9 endpoints mới) → cập nhật `docs/generated/api-schema.md` từ source code đã verify.
- Cập nhật `docs/CURRENT_STATE.md`, `docs/QUALITY_SCORE.md`, `docs/DEVELOPMENT.md` sau khi verify.

## Nhật ký tiến độ

| Ngày | Sự kiện | Kết quả |
|---|---|---|
| 2026-09-18 | M7 preflight | Xác nhận M6 Gate PASS không blocker; đọc SD-04 §14–18, SD-08 §16–34, JPA pagination rules; kiểm tra repositories M3 đã có query cần thiết; baseline `mvn test` 104/104 pass |
| 2026-09-18 | Shared pagination | `PageResponse` + `PaginationParser` (page=0, size=20, max 100, sort whitelist, VALIDATION_ERROR cho sort lạ) |
| 2026-09-18 | `USER-01..03` | `UserResponse`/`UserMapper`, `UserService` (me/profile/password + admin list/get/status/delete theo dependency rules), `UserController`, `AdminUserController`, `UserSpecification` |
| 2026-09-18 | `PROJ-01..03` + `PROJ-ADMIN-01` | Project DTOs, `ProjectAuthorizationService` (ProjectAccess, ADMIN override không fake role), `ProjectService` (create tx Project+OWNER, list membership-only, get, update, admin list), `ProjectSpecification`, `ProjectMembershipProjection`, controllers |
| 2026-09-18 | `MEM-01..03` | `ProjectMemberService` (list/remove/leave) + `ProjectMemberController`;OWNER membership bất khả xâm, documents remain |
| 2026-09-18 | Unit tests | `UserServiceTest` 6/6, `ProjectAuthorizationServiceTest` 4/4, `ProjectServiceTest` 5/5, `ProjectMemberServiceTest` 5/5 (sau khi sửa nested Mockito stubbing) |
| 2026-09-18 | Integration tests | `UserProjectMembershipIntegrationTest` 6/6 trên PostgreSQL 17 Testcontainer: 2 vòng fix — remap sort `project.*` cho query root ProjectMember, đổi `:q is null` thành sentinel `''` (PostgreSQL không infer kiểu null string), sửa expectation refresh sau disable thành 401 REFRESH_SESSION_REVOKED |
| 2026-09-18 | Final regression | `mvn -B -ntp test` 130/130 pass; `mvn -B -ntp clean verify` 130/130 pass, jar repackage |
| 2026-09-18 | Static review + closeout | Không `Project.ownerId`, không fake ADMIN role, không controller→repository, không cascade project knowledge, không đổi auth/OTP; cập nhật api-schema/state/quality/development; archive plan |

## Verification path và kết quả (chốt)

- Unit 20/20: `UserServiceTest` (profile/password revoke/status parse/delete dependency order), `ProjectAuthorizationServiceTest` (404/403 contracts, ADMIN override role null), `ProjectServiceTest` (create OWNER atomically, get/update mapping, admin listing không fake role), `ProjectMemberServiceTest` (list/remove/leave matrix).
- Integration 6/6 (`UserProjectMembershipIntegrationTest`, PostgreSQL 17 + Redis 7.4 Testcontainers, real filter chain): creator OWNER trong cùng transaction (đúng 1 membership OWNER), `GET /projects` membership-only cả với filter role, non-member 403 `PROJECT_ACCESS_FORBIDDEN`, MEMBER đọc được, MEMBER không update, OWNER update, ADMIN override không membership + `currentUserRole` null, MEMBER không remove, OWNER remove MEMBER 204, remove/ADMIN remove OWNER 409, missing membership 404, MEMBER leave 204, OWNER leave 409, document upload bởi member tồn tại sau remove/leave và `uploadedBy` vẫn tham chiếu User, user APIs (me/profile/password change revoke refresh session), admin users (paginated + q, INVALID_USER_STATUS, disable → refresh 401 REVOKED, delete dependency order), admin projects listing không fake role.
- Regression: `mvn -B -ntp test` 130/130; `mvn -B -ntp clean verify` 130/130 (exit 0).

## M7 Gate

Status: `PASS` — ngày `2026-09-18`

- [x] User management works (profile, password change + session revoke, admin status/delete theo dependency rules).
- [x] Project access model works (OWNER/MEMBER/ADMIN matrix, single-OWNER, membership-only listing).
- [x] Membership rules work (remove/leave invariants, documents remain).
- [x] ADMIN override works without fake ProjectMember or fake project role.

## Kết quả cuối cùng

M7 đã hoàn thành toàn bộ `USER-01..03`, `PROJ-01..03`, `PROJ-ADMIN-01`, `MEM-01..03` và `M7-TEST`. `ProjectAuthorizationService` là điểm vào authorization duy nhất cho project access/management; `ProjectResponse.currentUserRole` null cho ADMIN non-member; tạo project + OWNER membership trong một transaction; remove/leave không đụng documents. `DELETE /api/v1/projects/{projectId}` cố ý deferred sang M11 (PROJ-DELETE-01 sau MinIO). Full suite 130/130 pass. Không chạm invitation (M8), organization (M9), MinIO/document (M10/M11), search (M12), OpenAPI (M13) hay frontend.
