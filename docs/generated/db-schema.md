# Database Schema

> Trạng thái: **ĐÃ ĐỒNG BỘ VỚI SOURCE OF TRUTH ĐÃ XÁC MINH**.
> Schema dưới đây được sinh từ Flyway migration đã chạy thành công trên PostgreSQL 17 (Testcontainer)
> và được đối chiếu với live database qua `FlywayMigrationIntegrityTest` (information_schema,
> pg_constraint, pg_indexes) ngày `2026-09-17`.
> Chưa có generator tự động; file này được tái sinh thủ công trong cùng thay đổi với migration.

## Nguồn

- Nguồn sự thật runtime: `src/main/resources/db/migration/V1__create_core_tables.sql`, `V2__create_unique_partial_and_expression_indexes.sql`, `V3__create_query_indexes.sql`
- Nguồn thiết kế: `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- Cách cập nhật: tái sinh thủ công trong cùng phiên khi migration thay đổi; generator tự động chưa được thiết lập
- Xác minh lần cuối: `2026-09-17` — Flyway 3/3 migration pass từ database rỗng; Hibernate `ddl-auto=validate` pass; 12/12 migration integrity test pass

## Tổng quan

- Database: PostgreSQL (baseline test: `postgres:17-alpine`)
- Migration tool: Flyway 12.4.0, locations `classpath:db/migration`
- Hibernate: `ddl-auto=validate` (schema chỉ thay đổi qua Flyway)
- 10 persistent tables; UUID do application sinh (không dùng `gen_random_uuid()`); timestamp dùng `TIMESTAMPTZ`, UTC
- Enum lưu `VARCHAR` + `CHECK` (không dùng PostgreSQL native ENUM)
- Không có OTP table: OTP state là Redis short-lived state; kết quả verify bền vững nằm ở `users.email_verified_at`
- Không có soft-delete column; Project/Document là hard delete

## Tables

### users

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| email | VARCHAR(254) | NOT NULL; UNIQUE `uq_users_email` |
| password_hash | VARCHAR(255) | NOT NULL |
| display_name | VARCHAR(100) | NOT NULL |
| system_role | VARCHAR(20) | NOT NULL; CHECK `ck_users_system_role` IN (`ADMIN`, `USER`) |
| status | VARCHAR(20) | NOT NULL; CHECK `ck_users_status` IN (`ACTIVE`, `DISABLED`) |
| email_verified_at | TIMESTAMPTZ | NULL — NULL là chưa verify OTP; NOT NULL là đã verify |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

CHECK: `ck_users_email_normalized` — `email = LOWER(BTRIM(email)) AND email <> ''`; `ck_users_display_name` — `display_name = BTRIM(display_name) AND display_name <> ''`.

### refresh_sessions

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL; FK `fk_refresh_sessions_user` → users(id) ON DELETE CASCADE |
| token_hash | VARCHAR(128) | NOT NULL; UNIQUE `uq_refresh_sessions_token_hash` |
| expires_at | TIMESTAMPTZ | NOT NULL |
| revoked_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

CHECK: `ck_refresh_sessions_expiration` — `expires_at > created_at`.
Indexes: `idx_refresh_sessions_user_id`, `idx_refresh_sessions_expires_at`.

### projects

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| name | VARCHAR(150) | NOT NULL |
| description | TEXT | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

CHECK: `ck_projects_name` — `name = BTRIM(name) AND name <> ''`. Không có `owner_id`; OWNER nằm trong `project_members.role`. Project name không unique toàn cục.

### project_members

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_project_members_project` → projects(id) ON DELETE CASCADE |
| user_id | UUID | NOT NULL; FK `fk_project_members_user` → users(id) ON DELETE RESTRICT |
| role | VARCHAR(20) | NOT NULL; CHECK `ck_project_members_role` IN (`OWNER`, `MEMBER`) |
| joined_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_project_members_project_user` (project_id, user_id).
Partial unique index: `uq_project_members_single_owner` ON (project_id) WHERE role = 'OWNER' — tối đa một OWNER mỗi project (đủ một OWNER do application transaction đảm bảo).
Indexes: `idx_project_members_user_id`, `idx_project_members_project_id`.

### project_invitations

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_project_invitations_project` → projects(id) ON DELETE CASCADE |
| invited_by_user_id | UUID | NOT NULL; FK `fk_project_invitations_inviter` → users(id) ON DELETE RESTRICT |
| email | VARCHAR(254) | NOT NULL |
| token_hash | VARCHAR(128) | NOT NULL; UNIQUE `uq_project_invitations_token_hash` (chỉ lưu hash, không lưu raw token) |
| status | VARCHAR(20) | NOT NULL; CHECK `ck_project_invitations_status` IN (`PENDING`, `ACCEPTED`, `EXPIRED`, `CANCELLED`) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| accepted_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

CHECK: `ck_project_invitations_email` — `email = LOWER(BTRIM(email)) AND email <> ''`; `ck_project_invitations_expiration` — `expires_at > created_at`; `ck_project_invitations_accepted_at` — `(status = 'ACCEPTED' AND accepted_at IS NOT NULL) OR (status <> 'ACCEPTED' AND accepted_at IS NULL)`.
Partial unique index: `uq_project_pending_invitation_email` ON (project_id, email) WHERE status = 'PENDING'.
Indexes: `idx_project_invitations_project_status` (project_id, status), `idx_project_invitations_expires_at`.

### folders

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_folders_project` → projects(id) ON DELETE CASCADE |
| parent_id | UUID | NULL; FK composite `fk_folders_parent_same_project` (parent_id, project_id) → folders(id, project_id) ON DELETE NO ACTION |
| name | VARCHAR(150) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_folders_id_project` (id, project_id) — phục vụ composite FK same-project.
CHECK: `ck_folders_name` — `name = BTRIM(name) AND name <> ''`; `ck_folders_not_self_parent` — `parent_id IS NULL OR parent_id <> id` (cycle dài hơn là trách nhiệm service layer).
Partial expression unique indexes: `uq_folders_root_name` (project_id, LOWER(name)) WHERE parent_id IS NULL; `uq_folders_child_name` (project_id, parent_id, LOWER(name)) WHERE parent_id IS NOT NULL.

### categories

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_categories_project` → projects(id) ON DELETE CASCADE |
| name | VARCHAR(100) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_categories_id_project` (id, project_id).
CHECK: `ck_categories_name` — `name = BTRIM(name) AND name <> ''`.
Expression unique index: `uq_categories_project_name` (project_id, LOWER(name)).

### tags

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_tags_project` → projects(id) ON DELETE CASCADE |
| name | VARCHAR(50) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_tags_id_project` (id, project_id).
CHECK: `ck_tags_name` — `name = BTRIM(name) AND name <> ''`.
Expression unique index: `uq_tags_project_name` (project_id, LOWER(name)).

### documents

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_documents_project` → projects(id) ON DELETE CASCADE |
| uploaded_by_user_id | UUID | NOT NULL; FK `fk_documents_uploader` → users(id) ON DELETE RESTRICT (tham chiếu users, không tham chiếu project_members) |
| folder_id | UUID | NULL; FK composite `fk_documents_folder_same_project` (folder_id, project_id) → folders(id, project_id) ON DELETE NO ACTION |
| category_id | UUID | NULL; FK composite `fk_documents_category_same_project` (category_id, project_id) → categories(id, project_id) ON DELETE NO ACTION |
| display_name | VARCHAR(255) | NOT NULL |
| original_filename | VARCHAR(255) | NOT NULL |
| file_kind | VARCHAR(20) | NOT NULL; CHECK `ck_documents_file_kind` IN (`DOCUMENT`, `IMAGE`, `VIDEO`) |
| extension | VARCHAR(20) | NOT NULL |
| mime_type | VARCHAR(150) | NOT NULL |
| size_bytes | BIGINT | NOT NULL |
| storage_key | VARCHAR(1024) | NOT NULL; UNIQUE `uq_documents_storage_key` |
| description | TEXT | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_documents_id_project` (id, project_id).
CHECK: `ck_documents_display_name`, `ck_documents_original_filename` (`<> ''`), `ck_documents_extension` (`extension = LOWER(BTRIM(extension)) AND extension <> ''`), `ck_documents_size` (`size_bytes > 0`), `ck_documents_storage_key` (`<> ''`).
Indexes: `idx_documents_project_created_at` (project_id, created_at DESC), `idx_documents_project_folder`, `idx_documents_project_category`, `idx_documents_project_file_kind`, `idx_documents_uploaded_by`.

### document_tags

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| document_id | UUID | PK một phần; FK composite `fk_document_tags_document_same_project` (document_id, project_id) → documents(id, project_id) ON DELETE CASCADE |
| tag_id | UUID | PK một phần; FK composite `fk_document_tags_tag_same_project` (tag_id, project_id) → tags(id, project_id) ON DELETE CASCADE |
| project_id | UUID | NOT NULL — đảm bảo document và tag luôn cùng project |

PK: `pk_document_tags` (document_id, tag_id).
Indexes: `idx_document_tags_tag_id`, `idx_document_tags_project_id`.

## FK Delete Strategy

| Relationship | ON DELETE |
|---|---|
| User → RefreshSession | CASCADE |
| User → ProjectMember | RESTRICT |
| User → Invitation inviter | RESTRICT |
| User → Document uploader | RESTRICT |
| Project → ProjectMember / Invitation / Folder / Category / Tag / Document | CASCADE |
| Folder parent → child | NO ACTION |
| Folder → Document | NO ACTION |
| Category → Document | NO ACTION |
| Document → DocumentTag | CASCADE |
| Tag → DocumentTag | CASCADE |

## Same-Project Integrity (database-level)

- `folders.parent_id` phải cùng project với folder con.
- `documents.folder_id` / `documents.category_id` phải thuộc cùng project với document.
- `document_tags.project_id` buộc document và tag trùng project.

## Ghi chú

- Không chỉnh tay nội dung schema phía trên khi generator chưa tồn tại; chỉ tái sinh cùng thay đổi với migration và đối chiếu lại bằng migration integrity test.
- Toàn bộ constraint/index trong file này đã được xác minh tồn tại với đúng tên trên PostgreSQL 17 qua `FlywayMigrationIntegrityTest`.
