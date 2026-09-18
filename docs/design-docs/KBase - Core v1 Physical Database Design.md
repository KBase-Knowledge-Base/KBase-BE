# KBase – Core v1
## Physical Database Design – PostgreSQL

**Version:** Draft 2  
**Database:** PostgreSQL  
**Based on:** Core v1 Specification + Entity Analysis & ERD  
**AI/RAG:** Out of scope

---

## 1. Design Principles

Physical schema của Core v1 tuân theo các nguyên tắc:

1. PostgreSQL lưu user, project và metadata.
2. MinIO lưu actual binary files.
3. Mỗi bảng chính dùng `UUID`.
4. Thời gian dùng `TIMESTAMPTZ`.
5. Database lưu timestamp theo UTC.
6. Enum phía Java được lưu dưới dạng `VARCHAR`, không dùng PostgreSQL native ENUM.
7. Project là security/data boundary quan trọng nhất.
8. Database phải ngăn cross-project relationship ở những vị trí quan trọng.
9. Hard delete được sử dụng cho Project và Document.
10. Không sử dụng soft-delete column trong Core v1.
11. Document versioning chưa được triển khai.
12. AI-related tables chưa được tạo.
13. Email verification result được lưu bền vững bằng `users.email_verified_at`.
14. OTP verification state không được lưu trong PostgreSQL; Redis container giữ OTP hash/TTL/attempt state.
15. Flyway migration SQL nằm trong backend source/image; actual PostgreSQL data và Flyway history được persist bằng Docker named volume `postgres_data` trong local Docker deployment.

Các bảng:

```text
users
refresh_sessions

projects
project_members
project_invitations

folders
categories
tags

documents
document_tags
```

Tổng cộng:

```text
10 tables
```

---

## 2. UUID Strategy

Các primary key sử dụng:

```sql
UUID
```

Baseline hiện tại đề xuất **Spring Boot generate UUID**, thay vì phụ thuộc vào PostgreSQL extension.

Ví dụ Java:

```java
UUID.randomUUID()
```

hoặc Hibernate UUID generation.

Do đó:

```sql
id UUID NOT NULL
```

không bắt buộc:

```sql
DEFAULT gen_random_uuid()
```

Điều này giúp schema không phụ thuộc PostgreSQL extension/version cụ thể.

---

## 3. Enum Storage Strategy

Java sử dụng enum:

```java
@Enumerated(EnumType.STRING)
```

Database lưu:

```text
VARCHAR
```

kèm `CHECK`.

Ví dụ:

```sql
system_role VARCHAR(20)
CHECK (system_role IN ('ADMIN', 'USER'))
```

Thay vì PostgreSQL ENUM:

```sql
CREATE TYPE ...
```

Lý do:

- Migration dễ hơn.
- Thêm enum value đơn giản hơn.
- Ít coupling với PostgreSQL-specific type.
- Mapping Spring Data JPA đơn giản.

---

## 4. users

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY,

    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,

    system_role     VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    email_verified_at TIMESTAMPTZ NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email
        UNIQUE (email),

    CONSTRAINT ck_users_email_normalized
        CHECK (
            email = LOWER(BTRIM(email))
            AND email <> ''
        ),

    CONSTRAINT ck_users_display_name
        CHECK (
            display_name = BTRIM(display_name)
            AND display_name <> ''
        ),

    CONSTRAINT ck_users_system_role
        CHECK (system_role IN ('ADMIN', 'USER')),

    CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);
```

### Important

Email được normalize:

```text
Example@Mail.com

↓

example@mail.com
```

trước khi insert.

Database cũng kiểm tra email đã lowercase/trim.

Email format validation:

```text
user@example.com
```

vẫn nên nằm ở application layer.

Không nên cố implement RFC email validation phức tạp bằng SQL regex.

`email_verified_at`:

```text
NULL
→ email chưa verify bằng OTP

NOT NULL
→ email đã verify thành công
```

Email verification OTP itself không có PostgreSQL table. Redis giữ state ngắn hạn; PostgreSQL chỉ giữ verification result cuối cùng.

---

## 5. refresh_sessions

```sql
CREATE TABLE refresh_sessions (
    id              UUID PRIMARY KEY,

    user_id         UUID NOT NULL,

    token_hash      VARCHAR(128) NOT NULL,

    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_sessions_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_refresh_sessions_expiration
        CHECK (expires_at > created_at)
);
```

Indexes:

```sql
CREATE INDEX idx_refresh_sessions_user_id
    ON refresh_sessions(user_id);

CREATE INDEX idx_refresh_sessions_expires_at
    ON refresh_sessions(expires_at);
```

### Delete rule

```text
DELETE User
    ↓
RefreshSession automatically deleted
```

Điều này hợp lý vì session không có giá trị khi account không còn tồn tại.

Raw refresh token không lưu DB.

```text
Client:
raw token

Database:
hash(token)
```

Redis trong Core v1 không lưu refresh session. `refresh_sessions` tiếp tục là authoritative refresh-session store trong PostgreSQL. Redis chỉ dùng cho email verification OTP.

---

## 6. projects

```sql
CREATE TABLE projects (
    id              UUID PRIMARY KEY,

    name            VARCHAR(150) NOT NULL,
    description     TEXT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_projects_name
        CHECK (
            name = BTRIM(name)
            AND name <> ''
        )
);
```

Không có:

```sql
owner_id
```

trong `projects`.

OWNER được xác định duy nhất qua:

```text
project_members.role = OWNER
```

Điều này tránh hai source of truth.

Project name:

```text
NOT globally unique
```

Hai project khác nhau hoàn toàn có thể cùng tên.

---

## 7. project_members

```sql
CREATE TABLE project_members (
    id              UUID PRIMARY KEY,

    project_id      UUID NOT NULL,
    user_id         UUID NOT NULL,

    role            VARCHAR(20) NOT NULL,

    joined_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_project_members_project_user
        UNIQUE (project_id, user_id),

    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_project_members_role
        CHECK (role IN ('OWNER', 'MEMBER'))
);
```

Indexes:

```sql
CREATE INDEX idx_project_members_user_id
    ON project_members(user_id);

CREATE INDEX idx_project_members_project_id
    ON project_members(project_id);
```

---

## 8. Exactly One OWNER

Database có thể dễ dàng enforce:

```text
At most one OWNER
```

bằng partial unique index:

```sql
CREATE UNIQUE INDEX uq_project_members_single_owner
    ON project_members(project_id)
    WHERE role = 'OWNER';
```

Như vậy:

```text
Project A
OWNER User 1 ✅

Project A
OWNER User 2 ❌
```

Tuy nhiên SQL constraint thông thường không dễ đảm bảo:

```text
at least one OWNER
```

nên application phải đảm bảo điều này bằng transaction:

```text
BEGIN

INSERT INTO projects ...

INSERT INTO project_members (
    project_id,
    user_id,
    role = 'OWNER'
)

COMMIT
```

Nếu tạo membership OWNER thất bại:

```text
Project creation rollback
```

Không được để tồn tại project không có OWNER.

---

## 9. project_invitations

```sql
CREATE TABLE project_invitations (
    id                  UUID PRIMARY KEY,

    project_id          UUID NOT NULL,
    invited_by_user_id  UUID NOT NULL,

    email               VARCHAR(254) NOT NULL,
    token_hash          VARCHAR(128) NOT NULL,

    status              VARCHAR(20) NOT NULL,

    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_project_invitations_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_project_invitations_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_invitations_inviter
        FOREIGN KEY (invited_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_project_invitations_email
        CHECK (
            email = LOWER(BTRIM(email))
            AND email <> ''
        ),

    CONSTRAINT ck_project_invitations_status
        CHECK (
            status IN (
                'PENDING',
                'ACCEPTED',
                'EXPIRED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_project_invitations_expiration
        CHECK (expires_at > created_at),

    CONSTRAINT ck_project_invitations_accepted_at
        CHECK (
            (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
            OR
            (status <> 'ACCEPTED' AND accepted_at IS NULL)
        )
);
```

---

## 10. Pending Invitation Uniqueness

Một project không được tồn tại nhiều invitation `PENDING` cho cùng email.

```sql
CREATE UNIQUE INDEX uq_project_pending_invitation_email
    ON project_invitations(project_id, email)
    WHERE status = 'PENDING';
```

Resend phải:

```text
invalidate/change old token
reset expires_at
send email again
```

thay vì tạo duplicate pending invitation.

Indexes:

```sql
CREATE INDEX idx_project_invitations_project_status
    ON project_invitations(project_id, status);

CREATE INDEX idx_project_invitations_expires_at
    ON project_invitations(expires_at);
```

---

## 11. folders

```sql
CREATE TABLE folders (
    id              UUID PRIMARY KEY,

    project_id      UUID NOT NULL,
    parent_id       UUID NULL,

    name            VARCHAR(150) NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_folders_id_project
        UNIQUE (id, project_id),

    CONSTRAINT fk_folders_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_folders_parent_same_project
        FOREIGN KEY (parent_id, project_id)
        REFERENCES folders(id, project_id)
        ON DELETE NO ACTION,

    CONSTRAINT ck_folders_name
        CHECK (
            name = BTRIM(name)
            AND name <> ''
        ),

    CONSTRAINT ck_folders_not_self_parent
        CHECK (
            parent_id IS NULL
            OR parent_id <> id
        )
);
```

---

## 12. Why Composite Parent FK?

Không chỉ dùng:

```sql
parent_id REFERENCES folders(id)
```

mà sử dụng:

```sql
(parent_id, project_id)
    REFERENCES folders(id, project_id)
```

để database trực tiếp ngăn:

```text
Project A / Folder A
        ↓
parent =
Project B / Folder B
```

Điều đó tạo thêm một lớp bảo vệ cho project isolation.

---

## 13. Folder Name Uniqueness

Dùng hai partial unique indexes.

### Root folders

```sql
CREATE UNIQUE INDEX uq_folders_root_name
    ON folders(project_id, LOWER(name))
    WHERE parent_id IS NULL;
```

### Child folders

```sql
CREATE UNIQUE INDEX uq_folders_child_name
    ON folders(project_id, parent_id, LOWER(name))
    WHERE parent_id IS NOT NULL;
```

Như vậy:

```text
Project A

Backend
backend
```

ở cùng root:

```text
❌
```

Nhưng:

```text
Backend/Specs
Frontend/Specs
```

được phép:

```text
✅
```

---

## 14. Folder Cycles

Database ngăn trực tiếp:

```text
Folder A
parent = Folder A
```

nhờ:

```sql
CHECK(parent_id <> id)
```

Nhưng cycle dài hơn:

```text
A → B → C → A
```

phải được validate ở Spring Boot service.

---

## 15. categories

```sql
CREATE TABLE categories (
    id              UUID PRIMARY KEY,

    project_id      UUID NOT NULL,

    name            VARCHAR(100) NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_categories_id_project
        UNIQUE (id, project_id),

    CONSTRAINT fk_categories_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_categories_name
        CHECK (
            name = BTRIM(name)
            AND name <> ''
        )
);
```

Unique case-insensitive:

```sql
CREATE UNIQUE INDEX uq_categories_project_name
    ON categories(project_id, LOWER(name));
```

---

## 16. tags

```sql
CREATE TABLE tags (
    id              UUID PRIMARY KEY,

    project_id      UUID NOT NULL,

    name            VARCHAR(50) NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_tags_id_project
        UNIQUE (id, project_id),

    CONSTRAINT fk_tags_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_tags_name
        CHECK (
            name = BTRIM(name)
            AND name <> ''
        )
);
```

Unique:

```sql
CREATE UNIQUE INDEX uq_tags_project_name
    ON tags(project_id, LOWER(name));
```

---

## 17. documents

```sql
CREATE TABLE documents (
    id                      UUID PRIMARY KEY,

    project_id              UUID NOT NULL,
    uploaded_by_user_id     UUID NOT NULL,

    folder_id               UUID NULL,
    category_id             UUID NULL,

    display_name            VARCHAR(255) NOT NULL,
    original_filename       VARCHAR(255) NOT NULL,

    file_kind               VARCHAR(20) NOT NULL,

    extension               VARCHAR(20) NOT NULL,
    mime_type               VARCHAR(150) NOT NULL,
    size_bytes              BIGINT NOT NULL,

    storage_key             VARCHAR(1024) NOT NULL,

    description             TEXT NULL,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_documents_storage_key
        UNIQUE (storage_key),

    CONSTRAINT uq_documents_id_project
        UNIQUE (id, project_id),

    CONSTRAINT fk_documents_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_documents_uploader
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_documents_folder_same_project
        FOREIGN KEY (folder_id, project_id)
        REFERENCES folders(id, project_id)
        ON DELETE NO ACTION,

    CONSTRAINT fk_documents_category_same_project
        FOREIGN KEY (category_id, project_id)
        REFERENCES categories(id, project_id)
        ON DELETE NO ACTION,

    CONSTRAINT ck_documents_display_name
        CHECK (
            display_name = BTRIM(display_name)
            AND display_name <> ''
        ),

    CONSTRAINT ck_documents_original_filename
        CHECK (original_filename <> ''),

    CONSTRAINT ck_documents_file_kind
        CHECK (
            file_kind IN (
                'DOCUMENT',
                'IMAGE',
                'VIDEO'
            )
        ),

    CONSTRAINT ck_documents_extension
        CHECK (
            extension = LOWER(BTRIM(extension))
            AND extension <> ''
        ),

    CONSTRAINT ck_documents_size
        CHECK (size_bytes > 0),

    CONSTRAINT ck_documents_storage_key
        CHECK (storage_key <> '')
);
```

---

## 18. Why `uploaded_by_user_id → users`

Không reference `project_members.id` vì MEMBER có thể rời project nhưng file vẫn được giữ lại.

```text
User B uploads API.pdf
↓
User B leaves project
↓
API.pdf remains
```

Do đó:

```text
documents.uploaded_by_user_id
→ users.id
```

OWNER tiếp tục quản lý file.

---

## 19. Prevent Cross-Project Folder Assignment

Composite FK:

```sql
(folder_id, project_id)
REFERENCES folders(id, project_id)
```

ngăn document thuộc Project A nhưng gán vào folder của Project B.

Tương tự với Category.

---

## 20. Document Name Uniqueness

Core v1 chưa quy định `display_name` phải unique, nên database không tự thêm constraint này.

Storage vẫn không collision vì `storage_key` dựa trên UUID.

---

## 21. Document Indexes

```sql
CREATE INDEX idx_documents_project_created_at
    ON documents(project_id, created_at DESC);

CREATE INDEX idx_documents_project_folder
    ON documents(project_id, folder_id);

CREATE INDEX idx_documents_project_category
    ON documents(project_id, category_id);

CREATE INDEX idx_documents_project_file_kind
    ON documents(project_id, file_kind);

CREATE INDEX idx_documents_uploaded_by
    ON documents(uploaded_by_user_id);
```

---

## 22. File Limits

Database chỉ enforce:

```sql
size_bytes > 0
```

Các giới hạn như:

```text
Document ≤ 50 MB
Image ≤ 20 MB
Video ≤ 500 MB
```

sẽ configurable ở application layer.

---

## 23. document_tags

```sql
CREATE TABLE document_tags (
    document_id     UUID NOT NULL,
    tag_id          UUID NOT NULL,
    project_id      UUID NOT NULL,

    CONSTRAINT pk_document_tags
        PRIMARY KEY (document_id, tag_id),

    CONSTRAINT fk_document_tags_document_same_project
        FOREIGN KEY (document_id, project_id)
        REFERENCES documents(id, project_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_document_tags_tag_same_project
        FOREIGN KEY (tag_id, project_id)
        REFERENCES tags(id, project_id)
        ON DELETE CASCADE
);
```

Indexes:

```sql
CREATE INDEX idx_document_tags_tag_id
    ON document_tags(tag_id);

CREATE INDEX idx_document_tags_project_id
    ON document_tags(project_id);
```

---

## 24. Why `project_id` Exists in `document_tags`

Việc thêm `project_id` giúp database đảm bảo:

```text
Document.project_id
==
Tag.project_id
```

và ngăn gắn tag từ project khác.

---

## 25. Main FK Delete Strategy

| Relationship | ON DELETE |
|---|---|
| User → RefreshSession | CASCADE |
| User → ProjectMember | RESTRICT |
| User → Invitation inviter | RESTRICT |
| User → Document uploader | RESTRICT |
| Project → ProjectMember | CASCADE |
| Project → Invitation | CASCADE |
| Project → Folder | CASCADE |
| Project → Category | CASCADE |
| Project → Tag | CASCADE |
| Project → Document | CASCADE |
| Folder parent → child | NO ACTION |
| Folder → Document | NO ACTION |
| Category → Document | NO ACTION |
| Document → DocumentTag | CASCADE |
| Tag → DocumentTag | CASCADE |

---

## 26. Why Folder → Document Is Not CASCADE

Nếu folder còn document, Core v1 yêu cầu:

```text
409 Conflict
```

không tự động xóa document.

---

## 27. Category Delete

Category đang được document sử dụng không được xóa.

Application trả:

```text
409 CATEGORY_IN_USE
```

---

## 28. Tag Delete

Xóa tag:

```text
Tag removed
DocumentTag removed
Document remains
```

---

## 29. Member Removal

Khi OWNER remove MEMBER:

```text
project_members row deleted
```

nhưng các document đã upload vẫn còn.

Former member mất quyền truy cập project.

---

## 30. User Hard Delete

Không cascade documents hoặc project memberships một cách âm thầm.

Nếu user còn dependency:

```text
DELETE User
→ rejected
```

Trong thực tế nên ưu tiên:

```text
DISABLED
```

---

## 31. Project Hard Delete

Flow:

```text
OWNER requests delete
↓
Authorization
↓
Load document storage keys
↓
Delete MinIO objects
↓
DELETE projects
↓
Database cascade relational data
```

---

## 32. Document Hard Delete

Flow:

```text
Authorization
↓
OWNER or uploader
↓
Delete MinIO object
↓
Delete Document DB row
↓
DocumentTag CASCADE
```

Không có soft-delete fields trong Core v1.

---

## 33. Storage Key

Ví dụ:

```text
projects/{projectId}/documents/{documentId}.pdf
```

Không dùng filename do user cung cấp làm object key.

---

## 34. Timestamp Strategy

Toàn bộ timestamps:

```sql
TIMESTAMPTZ
```

Backend lưu UTC.

`updated_at` nên được quản lý bằng Spring Data JPA Auditing.

---

## 35. Database-Level Project Isolation

Schema enforce project consistency tại:

- Folder → Parent Folder
- Document → Folder
- Document → Category
- DocumentTag → Document/Tag

Điều này giúp giảm lỗi cross-project data linkage.

---

## 36. Application-Level Rules Still Required

Spring Boot vẫn phải enforce:

```text
Authentication
Authorization
Project membership
OWNER / MEMBER permissions
Document uploader permission
Invitation token verification
Invitation expiration
Existing project member check
Folder cycle detection
File MIME validation
File extension validation
Configurable upload size
Batch size
Allowed file types
Owner cannot leave
Owner cannot remove himself
At least one owner
MinIO consistency
```

---

## 37. Search Baseline

Core v1 search metadata:

```text
display_name
original_filename
description
category
tag
```

Có thể implement ban đầu bằng `ILIKE` + join.

Không thêm vector search hoặc full-text infrastructure ở phase này.

---

## 38. Pagination

List API dùng:

```text
page
size
sort
```

Default:

```text
20 items/page
```

Maximum:

```text
100
```

---

## 39. Complete Relationship Model

```text
USERS
│
├──< REFRESH_SESSIONS
│
├──< PROJECT_MEMBERS >── PROJECTS
│                          │
├──< PROJECT_INVITATIONS >─┤
│                          │
└──< DOCUMENTS             ├──< FOLDERS
                           │      │
                           │      └──< FOLDERS
                           │
                           ├──< CATEGORIES
                           │
                           ├──< TAGS
                           │
                           └──< DOCUMENTS
                                  │
                                  └──< DOCUMENT_TAGS >── TAGS
```

---

## 40. Physical Schema Summary

### users
```text
PK id
UK email
email_verified_at nullable verification timestamp
```

### refresh_sessions
```text
PK id
FK user_id
UK token_hash
```

### projects
```text
PK id
```

### project_members
```text
PK id
FK project_id
FK user_id
UK project_id + user_id
Partial UK project_id WHERE OWNER
```

### project_invitations
```text
PK id
FK project_id
FK invited_by_user_id
UK token_hash
Partial UK project + email WHERE PENDING
```

### folders
```text
PK id
FK project_id
Self FK parent_id + project_id
Partial UK root folder name
Partial UK child folder name
```

### categories
```text
PK id
FK project_id
UK project + lower(name)
```

### tags
```text
PK id
FK project_id
UK project + lower(name)
```

### documents
```text
PK id
FK project_id
FK uploaded_by_user_id
Composite FK folder + project
Composite FK category + project
UK storage_key
```

### document_tags
```text
Composite PK document_id + tag_id
Composite FK document + project
Composite FK tag + project
```

---

## 41. Final Table Structure

```text
users
├── id UUID PK
├── email VARCHAR(254) UK
├── password_hash VARCHAR(255)
├── display_name VARCHAR(100)
├── system_role VARCHAR(20)
├── status VARCHAR(20)
├── email_verified_at TIMESTAMPTZ NULL
├── created_at TIMESTAMPTZ
└── updated_at TIMESTAMPTZ

refresh_sessions
├── id UUID PK
├── user_id UUID FK
├── token_hash VARCHAR(128) UK
├── expires_at TIMESTAMPTZ
├── revoked_at TIMESTAMPTZ
└── created_at TIMESTAMPTZ

projects
├── id UUID PK
├── name VARCHAR(150)
├── description TEXT
├── created_at TIMESTAMPTZ
└── updated_at TIMESTAMPTZ

project_members
├── id UUID PK
├── project_id UUID FK
├── user_id UUID FK
├── role VARCHAR(20)
└── joined_at TIMESTAMPTZ

project_invitations
├── id UUID PK
├── project_id UUID FK
├── invited_by_user_id UUID FK
├── email VARCHAR(254)
├── token_hash VARCHAR(128) UK
├── status VARCHAR(20)
├── expires_at TIMESTAMPTZ
├── accepted_at TIMESTAMPTZ
└── created_at TIMESTAMPTZ

folders
├── id UUID PK
├── project_id UUID FK
├── parent_id UUID FK
├── name VARCHAR(150)
├── created_at TIMESTAMPTZ
└── updated_at TIMESTAMPTZ

categories
├── id UUID PK
├── project_id UUID FK
├── name VARCHAR(100)
├── created_at TIMESTAMPTZ
└── updated_at TIMESTAMPTZ

tags
├── id UUID PK
├── project_id UUID FK
├── name VARCHAR(50)
└── created_at TIMESTAMPTZ

documents
├── id UUID PK
├── project_id UUID FK
├── uploaded_by_user_id UUID FK
├── folder_id UUID FK NULL
├── category_id UUID FK NULL
├── display_name VARCHAR(255)
├── original_filename VARCHAR(255)
├── file_kind VARCHAR(20)
├── extension VARCHAR(20)
├── mime_type VARCHAR(150)
├── size_bytes BIGINT
├── storage_key VARCHAR(1024) UK
├── description TEXT NULL
├── created_at TIMESTAMPTZ
└── updated_at TIMESTAMPTZ

document_tags
├── document_id UUID PK/FK
├── tag_id UUID PK/FK
└── project_id UUID
```

---

## 42. Important Decisions Preserved from Previous Phases

```text
OWNER = project role
ADMIN = system role
Invitation = email-based secure link/token
Registration email verification = OTP via Gmail SMTP + Redis TTL state
Refresh session = PostgreSQL, not Redis
Member can read/download all project files
Member can modify/delete only own files
Owner manages every project file
Member leaving does NOT delete documents
Folder + Category + Tag supported
No versioning
Hard delete
PostgreSQL metadata
MinIO binary storage
Configurable file limits
AI/RAG deferred until Core is stable
```

---

## 43. Items Deliberately NOT Invented

Chưa tự thêm requirement cho:

```text
Document name uniqueness
Storage quota per project
Storage quota per user
Folder ownership
Document version history
Audit history
Favorite/bookmark document
Document comments
Public document sharing
Ownership transfer
Document approval workflow
```

Nếu cần, các chức năng này sẽ được bổ sung vào Specification trước khi thay đổi schema.

---

## Docker / Flyway Persistence Boundary

Local Docker baseline:

```text
postgres container
    ↓
postgres_data named volume

minio container
    ↓
minio_data named volume

redis container
    ↓
OTP-only ephemeral state
```

Flyway migration files are packaged with the Spring Boot backend under `src/main/resources/db/migration`. On backend startup, Flyway applies pending migrations to the PostgreSQL container.

`postgres_data` preserves actual database files and Flyway schema history across normal container recreation.

Redis does not require a durable volume for Core v1 because OTP state is intentionally short-lived; after Redis restart, affected users request a new OTP.

---

## 44. Physical Design Result

Schema Core v1 hiện tại gồm:

```text
10 tables
+
foreign keys
+
check constraints
+
unique constraints
+
partial unique indexes
+
query indexes
```

và đủ để chuyển sang REST API / Spring Boot design mà chưa cần thêm AI-related tables.
