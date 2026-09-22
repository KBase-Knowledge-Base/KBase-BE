# Database Schema

> Trạng thái: **ĐÃ ĐỒNG BỘ VỚI SOURCE OF TRUTH ĐÃ XÁC MINH**.
> Schema dưới đây được sinh từ Flyway migration đã chạy thành công trên PostgreSQL 17 + pgvector
> (Testcontainer) và được đối chiếu với live database qua `FlywayMigrationIntegrityTest`
> (information_schema, pg_constraint, pg_indexes) ngày `2026-09-22`.
> Chưa có generator tự động; file này được tái sinh thủ công trong cùng thay đổi với migration.

## Nguồn

- Nguồn sự thật runtime: `src/main/resources/db/migration/V1__create_core_tables.sql`, `V2__create_unique_partial_and_expression_indexes.sql`, `V3__create_query_indexes.sql`, `V4__create_ai_persistence_schema.sql`
- Nguồn thiết kế: `docs/design-docs/KBase - Core v1 Physical Database Design.md`
- Nguồn thiết kế AI: `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`
- Cách cập nhật: tái sinh thủ công trong cùng phiên khi migration thay đổi; generator tự động chưa được thiết lập
- Xác minh lần cuối: `2026-09-22` — Flyway 4/4 migration pass từ database rỗng; Core V1–V3 upgrade path pass; Hibernate `ddl-auto=validate` pass; `FlywayMigrationIntegrityTest` 13/13 pass; AI persistence tests pass trên pgvector PostgreSQL 17.11

## Tổng quan

- Database: PostgreSQL 17 + pgvector (baseline image: `pgvector/pgvector:0.8.6-pg17-bookworm`)
- Migration tool: Flyway 12.4.0, locations `classpath:db/migration`
- Hibernate: `ddl-auto=validate` (schema chỉ thay đổi qua Flyway)
- 18 persistent tables (10 Core + 8 AI); UUID do application sinh (không dùng `gen_random_uuid()`); timestamp dùng `TIMESTAMPTZ`, UTC
- Enum lưu `VARCHAR` + `CHECK` (không dùng PostgreSQL native ENUM)
- pgvector extension được tạo bởi V4; mọi AI embedding dùng `vector(768)` và cosine distance
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

### document_ai_indexes

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| document_id | UUID | PK; composite FK `fk_document_ai_indexes_document_same_project` → documents(id, project_id) ON DELETE CASCADE |
| project_id | UUID | NOT NULL; phải trùng project của document |
| status | VARCHAR(20) | NOT NULL; CHECK `ck_document_ai_indexes_status` IN (`PENDING`, `PROCESSING`, `READY`, `FAILED`, `UNSUPPORTED`) |
| failure_reason | VARCHAR(120) | NULL |
| source_hash | VARCHAR(128) | NULL; non-blank khi có giá trị |
| active_version | BIGINT | NULL; NULL trước lần index thành công, dương khi có |
| desired_version | BIGINT | NOT NULL; > 0 |
| chunking_version | VARCHAR(100) | NOT NULL; non-blank |
| embedding_model | VARCHAR(150) | NOT NULL; non-blank |
| embedding_dimensions | INTEGER | NOT NULL; CHECK bằng `768` |
| attempt_count | INTEGER | NOT NULL DEFAULT `0`; >= 0 |
| last_error_code | VARCHAR(120) | NULL; non-blank khi có giá trị |
| indexed_at | TIMESTAMPTZ | NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

Indexes: `idx_document_ai_indexes_project_status`, `idx_document_ai_indexes_status`.

### document_ai_chunks

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; composite FK cùng document |
| document_id | UUID | NOT NULL; `fk_document_ai_chunks_document_same_project` → documents(id, project_id) ON DELETE CASCADE |
| index_version | BIGINT | NOT NULL; > 0 |
| chunk_index | INTEGER | NOT NULL; >= 0 |
| content | TEXT | NOT NULL; non-blank |
| page_number / slide_number | INTEGER | NULL; dương khi có |
| section_title | VARCHAR(255) | NULL |
| token_count | INTEGER | NULL; >= 0 khi có |
| content_hash | VARCHAR(128) | NOT NULL; non-blank |
| embedding | vector(768) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_document_ai_chunks_document_version_index` (document_id, index_version, chunk_index).
Indexes: `idx_document_ai_chunks_project_id`, `idx_document_ai_chunks_document_id`, `idx_document_ai_chunks_document_version`, `idx_document_ai_chunks_embedding_hnsw` (HNSW, `vector_cosine_ops`).

### ai_conversations

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL; FK `fk_ai_conversations_project` → projects(id) ON DELETE CASCADE |
| created_by_user_id | UUID | NOT NULL; FK `fk_ai_conversations_creator` → users(id) ON DELETE RESTRICT |
| title | VARCHAR(100) | NOT NULL; CHECK `ck_ai_conversations_title` trim/non-blank |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

Index: `idx_ai_conversations_project_user_updated_at` (project_id, created_by_user_id, updated_at DESC). Membership không là FK; retention access được kiểm tra ở application layer.

### ai_messages

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| conversation_id | UUID | NOT NULL; FK `fk_ai_messages_conversation` → ai_conversations(id) ON DELETE CASCADE |
| role | VARCHAR(20) | NOT NULL; CHECK `ck_ai_messages_role` IN (`USER`, `ASSISTANT`) |
| content | TEXT | NULL cho assistant placeholder thất bại |
| generation_status | VARCHAR(20) | NOT NULL; CHECK `ck_ai_messages_generation_status` IN (`PROCESSING`, `COMPLETED`, `FAILED`) |
| answer_type | VARCHAR(20) | NULL; chỉ `GROUNDED`/`NO_EVIDENCE` trên assistant COMPLETED |
| model | VARCHAR(150) | NULL |
| failure_code | VARCHAR(120) | NULL; non-blank khi có giá trị |
| created_at / completed_at | TIMESTAMPTZ | created_at NOT NULL; completed_at NULL khi đang PROCESSING |

Index: `idx_ai_messages_conversation_created_at` (conversation_id, created_at, id).
Partial unique index: `uq_ai_messages_active_generation` trên conversation_id cho assistant có status PROCESSING.

### ai_message_sources

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| assistant_message_id | UUID | PK một phần; FK `fk_ai_message_sources_assistant_message` → ai_messages(id) ON DELETE CASCADE |
| source_order | INTEGER | PK một phần; >= 0 |
| document_id | UUID | NULL; FK `fk_ai_message_sources_document` → documents(id) ON DELETE SET NULL |
| chunk_id | UUID | NULL; FK `fk_ai_message_sources_chunk` → document_ai_chunks(id) ON DELETE SET NULL |
| document_id_snapshot | UUID | NOT NULL |
| document_name_snapshot | VARCHAR(255) | NOT NULL; trim/non-blank |
| page_number_snapshot / slide_number_snapshot | INTEGER | NULL; dương khi có |
| section_title_snapshot | VARCHAR(255) | NULL |
| retrieval_score | DOUBLE PRECISION | NULL |

PK: `pk_ai_message_sources` (assistant_message_id, source_order). Indexes: `idx_ai_message_sources_document_id`, `idx_ai_message_sources_chunk_id`.

### ai_jobs

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| job_type | VARCHAR(30) | NOT NULL; CHECK `ck_ai_jobs_type` IN (`DOCUMENT_INDEX`, `DOCUMENT_REINDEX`, `CONVERSATION_PURGE`, `GUIDE_REINDEX`) |
| status | VARCHAR(20) | NOT NULL; CHECK `ck_ai_jobs_status` IN (`PENDING`, `PROCESSING`, `RETRY`, `DONE`, `FAILED`, `CANCELLED`) |
| project_id | UUID | NULL; FK `fk_ai_jobs_project` → projects(id) ON DELETE CASCADE |
| document_id | UUID | NULL; composite FK `fk_ai_jobs_document_same_project` → documents(id, project_id) ON DELETE CASCADE |
| user_id | UUID | NULL; FK `fk_ai_jobs_user` → users(id) ON DELETE SET NULL |
| dedup_key | VARCHAR(512) | NOT NULL; non-blank |
| payload | JSONB | NULL; chỉ parameters nhỏ, không raw content/prompt/secret |
| run_at | TIMESTAMPTZ | NOT NULL |
| attempt_count / max_attempts | INTEGER | attempt_count >= 0; max_attempts > 0 |
| lease_until / locked_by | TIMESTAMPTZ / VARCHAR(255) | NULL |
| last_error_code | VARCHAR(120) | NULL; non-blank khi có giá trị |
| created_at / updated_at / completed_at | TIMESTAMPTZ | created_at/updated_at NOT NULL; completed_at NULL cho job chưa hoàn tất |

Indexes: `idx_ai_jobs_status_run_at`, `idx_ai_jobs_project_id`, `idx_ai_jobs_document_id`, `idx_ai_jobs_user_id`, `idx_ai_jobs_dedup_key`.

### ai_guide_sources

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| source_key | VARCHAR(1024) | NOT NULL; UNIQUE `uq_ai_guide_sources_source_key`; non-blank |
| content_hash | VARCHAR(128) | NOT NULL; non-blank |
| active_version | BIGINT | NULL; dương khi có |
| desired_version | BIGINT | NOT NULL; > 0 |
| status | VARCHAR(20) | NOT NULL; CHECK `ck_ai_guide_sources_status` IN (`PENDING`, `PROCESSING`, `READY`, `FAILED`) |
| indexed_at / created_at / updated_at | TIMESTAMPTZ | indexed_at NULL; created_at/updated_at NOT NULL |

Index: `idx_ai_guide_sources_status`.

### ai_guide_chunks

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | UUID | PK |
| guide_source_id | UUID | NOT NULL; FK `fk_ai_guide_chunks_source` → ai_guide_sources(id) ON DELETE CASCADE |
| index_version | BIGINT | NOT NULL; > 0 |
| chunk_index | INTEGER | NOT NULL; >= 0 |
| content | TEXT | NOT NULL; non-blank |
| heading_path | VARCHAR(500) | NULL |
| token_count | INTEGER | NULL; >= 0 khi có |
| content_hash | VARCHAR(128) | NOT NULL; non-blank |
| embedding | vector(768) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

UNIQUE: `uq_ai_guide_chunks_source_version_index` (guide_source_id, index_version, chunk_index).
Indexes: `idx_ai_guide_chunks_source_version`, `idx_ai_guide_chunks_embedding_hnsw` (HNSW, `vector_cosine_ops`).

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
| Document → Document AI index/chunks | CASCADE |
| Document → citation live document/chunk references | SET NULL (snapshot giữ nguyên) |
| Project → AI conversations/jobs | CASCADE |
| Conversation → AI messages/citations | CASCADE |
| User → AI conversation creator | RESTRICT |
| User → AI job owner | SET NULL |
| Guide source → Guide chunks | CASCADE |

## Same-Project Integrity (database-level)

- `folders.parent_id` phải cùng project với folder con.
- `documents.folder_id` / `documents.category_id` phải thuộc cùng project với document.
- `document_tags.project_id` buộc document và tag trùng project.
- `document_ai_indexes.project_id` và `document_ai_chunks.project_id` phải trùng project của Core document.
- Vector retrieval phải lọc `project_id` trong SQL và chỉ lấy chunk có `status = 'READY'` cùng `index_version = active_version`.

## Ghi chú

- Không chỉnh tay nội dung schema phía trên khi generator chưa tồn tại; chỉ tái sinh cùng thay đổi với migration và đối chiếu lại bằng migration integrity test.
- Toàn bộ constraint/index trong file này đã được xác minh tồn tại với đúng tên trên PostgreSQL 17 + pgvector qua `FlywayMigrationIntegrityTest`; hành vi vector, FK/delete, status vocabulary, quota lock và active-generation guard được xác minh bởi `AiPersistenceIntegrationTest`.
