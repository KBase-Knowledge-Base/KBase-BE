# KBase – Knowledge Base
## Core v1 Specification

**Version:** 1.1 Draft  
**Scope:** Core System  
**AI/RAG:** Out of scope for Core v1

---

## 1. Purpose

KBase là hệ thống Knowledge Base theo project, cho phép các team tập trung tài liệu và file liên quan đến project vào một nơi duy nhất.

Hệ thống phải hỗ trợ:

- Quản lý tài khoản.
- Xác thực và phân quyền.
- Tạo và quản lý project.
- Xác minh email đăng ký bằng OTP gửi qua Gmail SMTP.
- Mời thành viên qua email.
- Phân quyền theo từng project.
- Upload và quản lý file.
- Tổ chức file bằng folder, category và tag.
- Tìm kiếm tài liệu.
- Download và preview tài liệu.
- Lưu metadata trong PostgreSQL.
- Lưu binary file trong MinIO.
- Lưu OTP verification state ngắn hạn trong Redis.
- Cung cấp REST API qua Spring Boot.
- Cung cấp Swagger/OpenAPI documentation.
- Chạy được bằng Docker.

AI Chatbot, RAG, Embedding và Vector Search sẽ được tích hợp sau khi Core v1 ổn định.

---

# 2. Core v1 Scope

Core v1 gồm các module:

| Module | Core v1 |
|---|---|
| Authentication | ✅ |
| Email Verification OTP | ✅ |
| Redis OTP Store | ✅ |
| Gmail SMTP | ✅ |
| User Management | ✅ |
| Project Management | ✅ |
| Project Membership | ✅ |
| Email Invitation | ✅ |
| Folder Management | ✅ |
| Category Management | ✅ |
| Tag Management | ✅ |
| File Upload | ✅ |
| File Metadata Management | ✅ |
| Preview / View | ✅ |
| File Download | ✅ |
| File Delete | ✅ |
| Basic Search | ✅ |
| Swagger/OpenAPI | ✅ |
| PostgreSQL | ✅ |
| MinIO | ✅ |
| Docker | ✅ |
| AI Chatbot | ❌ Later |
| RAG | ❌ Later |
| Embedding | ❌ Later |
| Document Versioning | ❌ Later |
| Kubernetes | ❌ Optional later |
| Terraform | ❌ Optional later |

---

# 3. User and Role Model

KBase sử dụng hai lớp role khác nhau.

## 3.1 System Role

System role áp dụng trên toàn bộ KBase.

```text
ADMIN
USER
```

### ADMIN

Admin quản trị toàn hệ thống.

Admin có thể:

- Xem danh sách users.
- Xem thông tin user.
- Disable user.
- Xóa user nếu user không còn dependency ngăn cản việc xóa.
- Xem tất cả projects.
- Quản lý project trong trường hợp cần thiết.
- Có quyền administrative override đối với project.

### USER

Đây là tài khoản thông thường.

Quyền thực tế của USER trong một project được xác định thông qua `ProjectMember`.

---

# 4. Project Role

Project role không được lưu trực tiếp trong `User`.

Một user có thể có role khác nhau trong những project khác nhau.

Ví dụ:

```text
User A

Project Alpha → OWNER
Project Beta  → MEMBER
Project Gamma → MEMBER
```

Project role gồm:

```text
OWNER
MEMBER
```

Mỗi project trong Core v1 có:

```text
Exactly 1 OWNER
0..N MEMBER
```

Người tạo project tự động trở thành OWNER.

---

# 5. Permission Model

## 5.1 Project Permission

| Action | MEMBER | OWNER | ADMIN |
|---|---:|---:|---:|
| View project | ✅ | ✅ | ✅ |
| Edit project | ❌ | ✅ | ✅ |
| Delete project | ❌ | ✅ | ✅ |
| View members | ✅ | ✅ | ✅ |
| Invite member | ❌ | ✅ | ✅ |
| Remove member | ❌ | ✅ | ✅ |
| Leave project | ✅ | ❌ | N/A |
| Manage folders | ❌ | ✅ | ✅ |
| Manage categories | ❌ | ✅ | ✅ |

OWNER không thể tự leave project trong Core v1.

Ownership transfer chưa thuộc phạm vi Core v1.

---

# 6. File Permission Model

Các thành viên trong cùng project được phép **đọc tài liệu của nhau**, vì đây là Knowledge Base dùng chung.

Tuy nhiên MEMBER chỉ được thay đổi file do chính mình upload.

| Operation | Own File | Other Member's File | OWNER | ADMIN |
|---|---:|---:|---:|---:|
| View metadata | ✅ | ✅ | ✅ | ✅ |
| Preview | ✅ | ✅ | ✅ | ✅ |
| Download | ✅ | ✅ | ✅ | ✅ |
| Upload | ✅ | — | ✅ | ✅ |
| Rename | ✅ | ❌ | ✅ | ✅ |
| Change description | ✅ | ❌ | ✅ | ✅ |
| Change category | ✅ | ❌ | ✅ | ✅ |
| Change tags | ✅ | ❌ | ✅ | ✅ |
| Move folder | ✅ | ❌ | ✅ | ✅ |
| Delete | ✅ | ❌ | ✅ | ✅ |

Ví dụ:

```text
Project A

User A uploads:
backend-api.pdf

User B:
✅ View
✅ Download
❌ Rename
❌ Move
❌ Delete

Project Owner:
✅ View
✅ Rename
✅ Move
✅ Delete
```

---

# 7. Authentication

## FR-AUTH-001 – Register

User có thể đăng ký bằng:

```text
email
password
displayName
```

Email:

- Bắt buộc.
- Phải đúng email format.
- Không phân biệt chữ hoa/chữ thường.
- Được normalize trước khi lưu.
- Phải unique.

Ví dụ:

```text
Example@Email.com
example@email.com
```

phải được coi là cùng một email.

Sau khi tạo account, email chưa được coi là đã verify ngay lập tức.

Backend phải:

```text
Create User
    ↓
Generate email verification OTP
    ↓
Store OTP verification state in Redis with TTL
    ↓
Send OTP through Gmail SMTP
```

User chỉ có thể login sau khi verify email thành công.

Redis chỉ lưu OTP verification state ngắn hạn. Refresh session vẫn được lưu trong PostgreSQL.

---

## FR-AUTH-002 – Email Verification OTP

Core v1 sử dụng OTP để verify email sau registration.

OTP baseline:

```text
6 numeric digits
TTL: 5 minutes
Resend cooldown: 60 seconds
Maximum attempts per OTP: 5
```

Các giá trị TTL/cooldown/attempt limit phải configurable.

Redis key/value phải có TTL và không được dùng làm durable business storage.

Không lưu raw OTP trong PostgreSQL.

Recommended Redis state:

```text
email verification OTP hash
attempt count
expiration via Redis TTL
resend cooldown state
```

Raw OTP chỉ được gửi tới email người dùng qua Gmail SMTP và không được log.

OTP của Core v1 dùng cho **email verification**, không phải OTP login và không phải MFA/2FA.

User table lưu trạng thái verification lâu dài bằng:

```text
email_verified_at
```

`NULL` nghĩa là email chưa verify.

---

## FR-AUTH-003 – Verify Email

User submit:

```text
email
otp
```

Backend:

```text
load User by normalized email
    ↓
read OTP state from Redis
    ↓
validate OTP + TTL + attempts
    ↓
set users.email_verified_at
    ↓
delete OTP state from Redis
```

Nếu OTP hết hạn, user có thể request resend.

---

## FR-AUTH-004 – Resend Verification OTP

User chưa verify email có thể request OTP mới.

Backend phải:

- Kiểm tra account tồn tại và chưa verify.
- Tôn trọng resend cooldown.
- Invalidate/replace OTP cũ.
- Reset OTP TTL và attempt counter.
- Gửi OTP mới qua Gmail SMTP.

---

## FR-AUTH-005 – Password

Đề xuất Core v1:

```text
Minimum: 8 characters
Maximum: 64 characters
```

Password không được lưu plaintext.

Backend sử dụng secure password hashing như BCrypt.

---

## FR-AUTH-006 – Login

User login bằng:

```text
email
password
```

Điều kiện login:

```text
credentials valid
+
status = ACTIVE
+
email_verified_at IS NOT NULL
```

Nếu hợp lệ:

```text
Access Token
Refresh Token
```

được tạo.

Đề xuất ban đầu:

```text
Access Token TTL: 15 minutes
Refresh Token TTL: 7 days
```

Các TTL phải configurable, không hard-code trong business logic.

---

## FR-AUTH-007 – JWT

Access Token dùng để truy cập protected REST APIs.

Request:

```http
Authorization: Bearer <access-token>
```

Backend phải xác thực:

```text
Token valid?
        ↓
User exists?
        ↓
User active?
        ↓
Authorization?
```

---

## FR-AUTH-008 – Refresh Token

Đề xuất:

```text
Access Token
→ returned in response

Refresh Token
→ Secure + HttpOnly cookie
```

Refresh token được dùng để lấy access token mới.

---

## FR-AUTH-009 – Logout

Logout phải vô hiệu hóa refresh session hiện tại.

Access token đã được phát có thể tồn tại đến khi hết hạn.

---

# 8. User Management

## FR-USER-001 – Current User Profile

Authenticated user có thể:

- Xem profile.
- Thay đổi display name.
- Đổi password.

User không được tự thay đổi:

```text
systemRole
accountStatus
```

---

## FR-USER-002 – User Status

Đề xuất:

```text
ACTIVE
DISABLED
```

User `DISABLED`:

- Không login được.
- Không refresh token được.
- Không được thao tác với hệ thống.

Email verification là state độc lập với `UserStatus`:

```text
status = ACTIVE
email_verified_at = NULL
```

vẫn chưa được login cho đến khi OTP verification hoàn tất.

---

## FR-USER-003 – Admin User Management

ADMIN có thể:

```text
GET users
GET user detail
DISABLE user
ENABLE user
DELETE user
```

---

## FR-USER-004 – User Hard Delete

User chỉ có thể bị hard delete khi không còn dependency không thể xử lý.

Ví dụ một user đang là OWNER của một project:

```text
DELETE User
→ rejected
```

Backend trả conflict và yêu cầu project đó phải được xử lý trước.

Điều này tránh tạo project không có owner.

---

# 9. Project Management

## FR-PROJECT-001 – Create Project

Authenticated USER có thể tạo project.

Required:

```text
name
```

Optional:

```text
description
```

Creator tự động trở thành:

```text
ProjectMember.role = OWNER
```

---

## FR-PROJECT-002 – Project Name

Project name:

- Không được blank.
- Có giới hạn chiều dài.
- Không bắt buộc unique toàn hệ thống.

Ví dụ hai người khác nhau có thể đều có:

```text
Knowledge Base
```

---

## FR-PROJECT-003 – View Projects

User chỉ nhìn thấy:

```text
projects where user is a ProjectMember
```

ADMIN có thể xem toàn bộ project.

---

## FR-PROJECT-004 – Update Project

Chỉ:

```text
OWNER
ADMIN
```

được update:

```text
name
description
```

---

## FR-PROJECT-005 – Delete Project

Core v1 sử dụng:

```text
HARD DELETE
```

Chỉ OWNER hoặc ADMIN được delete project.

Delete project sẽ xóa toàn bộ dữ liệu thuộc project:

```text
Project
ProjectMember
Invitation
Folders
Categories
Tags
Documents metadata
Files in MinIO
```

Đây phải được xem là destructive operation.

Frontend cần confirmation trước khi thực hiện.

---

# 10. Project Membership

## FR-MEMBER-001 – Membership

Quan hệ:

```text
User N ───── N Project
```

được thể hiện qua:

```text
ProjectMember
```

Conceptual structure:

```text
ProjectMember

user
project
role
joinedAt
```

---

## FR-MEMBER-002 – Unique Membership

Một user chỉ có một membership trong cùng một project.

Không tồn tại:

```text
User A
Project X
MEMBER

User A
Project X
OWNER
```

đồng thời.

---

## FR-MEMBER-003 – Remove Member

OWNER có thể remove MEMBER.

OWNER không thể remove chính mình.

ADMIN có administrative override.

---

## FR-MEMBER-004 – Leave Project

MEMBER có thể tự leave project.

OWNER không được leave project trong Core v1.

---

# 11. Email Invitation

## FR-INV-001 – Create Invitation

OWNER nhập email người muốn invite.

Backend tạo invitation:

```text
Project
Email
InvitedBy
Token
Status
ExpiresAt
CreatedAt
```

---

## FR-INV-002 – Invitation Status

Invitation có:

```text
PENDING
ACCEPTED
EXPIRED
CANCELLED
```

---

## FR-INV-003 – Invitation Expiry

Đề xuất:

```text
72 hours
```

Invitation expiration phải configurable.

---

## FR-INV-004 – Send Email

Email chứa:

```text
Project name
Inviter
Invitation link
Expiration information
```

Ví dụ:

```text
https://kbase.example.com/invitations/{token}
```

---

## FR-INV-005 – Registered User

Nếu email đã có tài khoản:

```text
Email invitation
      ↓
Login
      ↓
Accept
      ↓
ProjectMember created
```

---

## FR-INV-006 – New User

Nếu email chưa tồn tại:

```text
Invitation
    ↓
Register using invited email
    ↓
Login
    ↓
Accept invitation
    ↓
ProjectMember created
```

Tài khoản đăng ký phải có cùng email với invitation.

---

## FR-INV-007 – Existing Member

Nếu email đã là project member:

```text
Invite
→ reject
→ HTTP 409 Conflict
```

---

## FR-INV-008 – Duplicate Pending Invitation

Không tạo nhiều invitation `PENDING` cho cùng:

```text
project + email
```

Owner có thể chọn:

```text
Resend Invitation
```

Khi resend:

- Token cũ bị invalid.
- Token mới được tạo.
- Expiration được reset.

---

## FR-INV-009 – Cancel Invitation

OWNER có thể cancel invitation đang `PENDING`.

Token sau khi cancel không còn sử dụng được.

---

# 12. Folder Management

Folder dùng để tổ chức tài liệu theo hierarchy.

Ví dụ:

```text
Project
│
├── Requirements
│   ├── Functional
│   └── Non-Functional
│
├── Backend
│
├── Frontend
│
└── Meetings
```

---

## FR-FOLDER-001 – Folder Structure

Folder có thể có:

```text
parentFolder
```

cho phép nested folder.

---

## FR-FOLDER-002 – Manage Folder

Core v1:

```text
OWNER → Create / Rename / Move / Delete Folder
MEMBER → View Folder
```

MEMBER có thể upload hoặc move **file của mình** vào folder đã tồn tại.

MEMBER không được thay đổi cấu trúc folder của project.

---

## FR-FOLDER-003 – Folder Name

Trong cùng một parent folder:

```text
folder name must be unique
```

so sánh theo case-insensitive.

Ví dụ:

```text
Backend
backend
```

không được tồn tại cùng cấp.

---

## FR-FOLDER-004 – Folder Cycles

Backend phải ngăn:

```text
A → B → C → A
```

Folder không được:

- Là parent của chính nó.
- Di chuyển vào descendant của chính nó.

---

## FR-FOLDER-005 – Delete Folder

Core v1 không cho delete folder nếu folder:

- Chứa document.
- Có subfolder.

Backend trả:

```text
409 Conflict
```

OWNER phải move/delete content trước.

Điều này tránh accidental recursive deletion.

---

# 13. Category Management

Category biểu diễn semantic/business classification.

Ví dụ:

```text
Requirement
Technical
Meeting Note
Design
Guide
Other
```

---

## FR-CATEGORY-001

Category thuộc một project.

OWNER có thể:

```text
Create
Rename
Delete
```

MEMBER:

```text
View
Assign category to own document
```

---

## FR-CATEGORY-002

Một document trong Core v1 có:

```text
0..1 Category
```

Không bắt buộc document phải có category.

---

## FR-CATEGORY-003

Category name unique trong project, case-insensitive.

---

## FR-CATEGORY-004

Không được delete category đang được document sử dụng.

Backend trả:

```text
409 Conflict
```

---

# 14. Tag Management

Tag dùng để tạo flexible labels.

Ví dụ:

```text
spring-boot
jwt
database
urgent
sprint-4
```

Document có thể có nhiều tags.

```text
Document N ───── N Tag
```

---

## FR-TAG-001

Tag thuộc project.

Tag name unique trong project, case-insensitive.

---

## FR-TAG-002

MEMBER có thể:

- Gắn tag vào file của mình.
- Remove tag khỏi file của mình.
- Tạo tag mới nếu cần.

MEMBER không được rename/delete global tag.

---

## FR-TAG-003

OWNER có thể:

```text
Create
Rename
Delete
Assign
Remove
```

đối với toàn project.

---

## FR-TAG-004

Delete tag:

```text
Tag deleted
+
DocumentTag relationships deleted
```

Document không bị delete.

---

# 15. Document / File Management

Trong Core v1, `Document` được dùng như khái niệm chung cho uploaded resource.

Nó có thể là:

```text
DOCUMENT
IMAGE
VIDEO
```

---

# 16. Supported File Types

## Documents

```text
PDF
DOC
DOCX
XLS
XLSX
PPT
PPTX
MD
TXT
```

## Images

```text
JPG
JPEG
PNG
GIF
SVG
BMP
```

## Videos

```text
MP4
MOV
AVI
```

---

# 17. File Size Limits

Giới hạn hiện tại là proposal và phải configurable.

| Type | Proposed Limit |
|---|---:|
| Document / Office | 50 MB |
| Image | 20 MB |
| Video | 500 MB |

Một batch upload ban đầu:

```text
Maximum 10 files
```

Không hard-code các con số này trong service code.

---

# 18. Upload

## FR-DOC-001 – Upload File

MEMBER, OWNER và ADMIN có thể upload file vào project mà họ có quyền truy cập.

Backend phải validate:

```text
Authentication
Project membership
File exists
File not empty
Allowed extension
Allowed MIME type
File size
Folder belongs to project
Category belongs to project
Tags belong to project
```

---

## FR-DOC-002 – Storage

Binary content không lưu trực tiếp vào PostgreSQL.

Architecture:

```text
Client
   ↓
Spring Boot
   ├── PostgreSQL → metadata
   │
   └── MinIO → actual binary
```

---

## FR-DOC-003 – Storage Key

Không sử dụng trực tiếp original filename làm MinIO object identifier.

Ví dụ:

```text
project/{projectId}/documents/{UUID}.pdf
```

Database lưu:

```text
originalFilename
storageKey
```

---

# 19. Document Metadata

Conceptually mỗi document cần các thông tin:

```text
id
project
uploadedBy
displayName
originalFilename
fileKind
extension
mimeType
size
storageKey
folder
category
description
createdAt
updatedAt
```

Tag quản lý bằng relationship riêng.

---

# 20. Rename Document

Rename chỉ thay đổi:

```text
displayName
```

Không nhất thiết rename object trong MinIO.

Ví dụ:

```text
storageKey:
abc-123-f81f.pdf

displayName:
Backend Authentication Specification.pdf
```

Điều này giúp object storage ổn định hơn.

---

# 21. Move Document

File có thể move giữa folder bằng cách thay:

```text
folderId
```

Không cần move binary object trong MinIO.

Permission:

```text
MEMBER → own document only
OWNER → every document
ADMIN → every document
```

---

# 22. Preview / View

Tất cả project members được xem file.

Core v1 ưu tiên preview trực tiếp cho:

```text
PDF
Images
TXT
Markdown
MP4
```

Office formats như:

```text
DOCX
XLSX
PPTX
```

không bắt buộc phải render hoàn chỉnh trực tiếp trên browser trong Core v1.

User luôn có thể download nếu có quyền.

---

# 23. Download

Binary objects trong MinIO phải private.

Không cung cấp public permanent URL.

Download flow:

```text
Client
   ↓
Spring Boot authorization
   ↓
Project membership check
   ↓
MinIO
   ↓
Download
```

Có thể sử dụng short-lived presigned URL nếu implementation lựa chọn cách đó.

---

# 24. Document Delete

Core v1 sử dụng:

```text
HARD DELETE
```

MEMBER:

```text
delete own document only
```

OWNER:

```text
delete any document in project
```

ADMIN:

```text
administrative override
```

Delete document phải xóa:

```text
MinIO object
Document metadata
DocumentTag relationships
```

---

# 25. Storage Consistency

Một failure có thể xảy ra:

```text
MinIO upload success
Database insert fail
```

hoặc:

```text
Database operation success
MinIO operation fail
```

Core implementation phải có compensation/error handling để hạn chế orphan data.

Ví dụ upload:

```text
Upload MinIO
      ↓
Insert PostgreSQL
      ↓
DB failed?
      ↓ YES
Delete uploaded MinIO object
```

Ngoài ra lỗi storage phải được log đầy đủ.

---

# 26. Search

Core v1 chỉ implement:

```text
Metadata Search
```

Không search nội dung bên trong PDF/DOCX.

Content search sẽ thuộc AI/RAG hoặc advanced search phase sau.

---

## FR-SEARCH-001 – Search Scope

Search luôn nằm trong project.

MEMBER không được search project mà mình không tham gia.

---

## FR-SEARCH-002 – Searchable Fields

Query có thể match:

```text
displayName
originalFilename
description
category
tag
```

---

## FR-SEARCH-003 – Filters

Core v1 nên hỗ trợ filter:

```text
folder
category
tag
fileKind
uploadedBy
createdDate
```

---

## FR-SEARCH-004 – Sorting

Đề xuất:

```text
name
createdAt
updatedAt
size
```

Ascending hoặc descending.

---

## FR-SEARCH-005 – Pagination

List/search API phải pagination.

Default proposal:

```text
page size = 20
```

Maximum:

```text
100
```

---

# 27. Authorization Rules

Backend phải enforce authorization.

Frontend disable hoặc hide button chỉ có giá trị UX.

Không được dựa vào frontend để bảo vệ resource.

Ví dụ:

```text
DELETE /documents/123
```

Backend phải kiểm tra:

```text
Authenticated?
      ↓
Document exists?
      ↓
User belongs to project?
      ↓
ADMIN?
      ↓
OWNER?
      ↓
uploadedBy == currentUser?
```

Nếu không:

```text
403 Forbidden
```

---

# 28. Resource Isolation

Project là security boundary chính.

Nếu:

```text
User A ∈ Project 1
User A ∉ Project 2
```

User A không được:

```text
View Project 2
View metadata
View folder
View member list
Search files
Download files
Guess UUID and access resource
```

Ngay cả khi user biết chính xác `documentId`.

---

# 29. HTTP Error Convention

Core API sử dụng HTTP status code nhất quán.

| Status | Meaning |
|---|---|
| 400 | Invalid request / validation |
| 401 | Not authenticated |
| 403 | Authenticated but no permission |
| 404 | Resource not found |
| 409 | Business rule conflict |
| 413 | File too large |
| 415 | Unsupported media/file type |
| 500 | Unexpected server error |
| 503 | External service unavailable |

Ví dụ:

```text
409
PROJECT_MEMBER_ALREADY_EXISTS
```

hoặc:

```text
403
DOCUMENT_MODIFICATION_FORBIDDEN
```

---

# 30. Standard Error Response

Đề xuất:

```json
{
  "timestamp": "2026-09-16T02:30:00Z",
  "status": 403,
  "code": "DOCUMENT_MODIFICATION_FORBIDDEN",
  "message": "You do not have permission to modify this document.",
  "path": "/api/documents/..."
}
```

Field validation có thể thêm:

```json
{
  "errors": {
    "email": "Invalid email address"
  }
}
```

---

# 31. API Naming

API prefix:

```text
/api/v1
```

Conceptual resources:

```text
/api/v1/auth
/api/v1/users
/api/v1/projects
/api/v1/projects/{projectId}/members
/api/v1/projects/{projectId}/invitations
/api/v1/projects/{projectId}/folders
/api/v1/projects/{projectId}/categories
/api/v1/projects/{projectId}/tags
/api/v1/projects/{projectId}/documents
/api/v1/documents/{documentId}
```

Exact REST endpoints sẽ được xác định trong API Design phase.

---

# 32. Database General Rules

Đề xuất:

```text
Primary IDs → UUID
```

Timestamps:

```text
createdAt
updatedAt
```

Backend lưu timestamp theo:

```text
UTC
```

Frontend chịu trách nhiệm convert sang local timezone để hiển thị.

Email được normalize lowercase.

Case-insensitive uniqueness cần áp dụng với:

```text
email
folder name within parent
category name within project
tag name within project
```

---

# 33. MinIO Rules

MinIO bucket không public.

Client không được tùy ý truy cập object dựa trên object key.

Spring Boot chịu trách nhiệm authorization trước khi cung cấp file.

Object key không chứa path do client hoàn toàn kiểm soát.

Ví dụ:

```text
project/
    {projectUuid}/
        documents/
            {documentUuid}.{extension}
```

---

# 34. Email Service

Email được sử dụng trong Core v1 cho:

```text
Email Verification OTP
Project Invitation
```

Core v1 chốt provider ban đầu là:

```text
Gmail SMTP
Host: smtp.gmail.com
Port: 587
Security: STARTTLS
Authentication: Gmail / Google Workspace account
Credential baseline: App Password stored through environment variables
```

`MailService` vẫn là abstraction để application layer không phụ thuộc trực tiếp Gmail API/SMTP details.

Không commit Gmail account credential hoặc App Password vào source control.

Failure khi gửi email phải được xử lý.

Invitation/verification email chỉ được coi là đã gửi thành công khi Gmail SMTP không báo lỗi.

---

# 35. Swagger/OpenAPI

Backend phải expose API documentation.

Swagger phải thể hiện:

```text
Endpoint
HTTP method
Request model
Response model
Validation
Authentication requirement
Possible HTTP statuses
```

Protected APIs phải hỗ trợ:

```text
Bearer JWT
```

trong Swagger UI.

---

# 36. Logging

Backend phải log tối thiểu:

```text
Authentication failures
Invitation failures
Upload failures
MinIO errors
Project deletion
Document deletion
Unexpected exceptions
```

Không log:

```text
password
raw JWT
refresh token
sensitive credentials
```

---

# 37. Docker

Core v1 phải chạy được bằng Docker.

Development deployment dự kiến:

```text
docker-compose
│
├── backend
├── frontend
├── postgres
├── minio
└── redis
```

Persistence:

```text
PostgreSQL → Docker named volume: postgres_data
MinIO      → Docker named volume: minio_data
Redis OTP  → container-local ephemeral state, no durable volume required
```

Flyway migration files nằm trong backend source/image tại `db/migration` và được chạy khi backend kết nối tới PostgreSQL container.

Schema history và actual PostgreSQL data được lưu trong `postgres_data`, không phụ thuộc local PostgreSQL trên máy host.

MinIO object data được lưu trong `minio_data`, không phụ thuộc local filesystem của backend container.

Redis phải chạy bằng Docker service trong Core v1 local environment thay vì phụ thuộc Redis cài trực tiếp trên máy developer. Nếu Redis container restart, OTP chưa dùng có thể bị invalidated và user request resend OTP.

Gmail SMTP nằm ngoài Docker.

---

# 38. Configuration

Các giá trị sau không được hard-code:

```text
Database URL
Database credentials
JWT secret/key
Access token TTL
Refresh token TTL
MinIO endpoint
MinIO credentials
MinIO bucket
Gmail SMTP username
Gmail SMTP App Password
Redis host/port
OTP TTL
OTP resend cooldown
OTP maximum attempts
Invitation expiration
Upload limits
Allowed file types
Frontend base URL
```

Sử dụng:

```text
environment variables
```

hoặc configuration files kết hợp environment variables.

---

# 39. Core v1 Main Business Rules

1. Mỗi project có đúng một OWNER.

2. OWNER là project role, không phải system role.

3. ADMIN là system-level role.

4. Một user có thể thuộc nhiều project.

5. Một user có thể là OWNER project này và MEMBER project khác.

6. User chỉ được truy cập project mà họ tham gia, ngoại trừ ADMIN.

7. MEMBER được đọc/download toàn bộ tài liệu trong project.

8. MEMBER chỉ được modify/delete file do mình upload.

9. OWNER được quản lý tất cả file trong project.

10. OWNER quản lý project membership.

11. Registration phải verify email bằng OTP trước khi login.

12. OTP verification state được lưu ngắn hạn trong Redis container với TTL; Redis không thay thế PostgreSQL refresh sessions.

13. Gmail SMTP được dùng để gửi verification OTP và project invitation.

14. Invitation được thực hiện bằng email link/token, không dùng OTP invitation.

15. Invitation không trực tiếp tạo membership.

16. Membership chỉ được tạo sau khi invitation được accept.

17. Folder structure do OWNER quản lý.

18. MEMBER có thể đặt file của mình vào folder có sẵn.

19. Category do OWNER quản lý.

20. MEMBER có thể assign category cho file của mình.

21. Tag là flexible metadata.

22. MEMBER có thể tạo/assign tag nhưng không rename/delete global tag.

23. File binary lưu trong MinIO.

24. File metadata lưu trong PostgreSQL.

25. Project resource luôn private.

26. Project deletion là hard delete.

27. Document deletion là hard delete.

28. Document versioning không thuộc Core v1.

29. Basic search chỉ search metadata.

30. AI/RAG không thuộc Core v1.

---

# 40. Core v1 Out of Scope

Các chức năng sau cố ý chưa triển khai:

```text
AI Chatbot
RAG
Embeddings
Vector Search
Document content indexing
Speech-to-text
Video transcription
Document version history
Document comments
Real-time collaborative editing
Public sharing link
Ownership transfer
OTP login / MFA / 2FA
Forgot-password / password-reset OTP
Advanced audit dashboard
Kubernetes
Terraform
Terragrunt
```

Các phần này có thể được thêm mà không thay đổi mục tiêu của Core v1.

---

# 41. Expected Core Entities

Specification hiện tại dự kiến sẽ dẫn tới các entity chính:

```text
User (includes persistent `email_verified_at`)

AuthSession / RefreshToken

Project

ProjectMember

ProjectInvitation

Folder

Category

Tag

Document

DocumentTag
```

Đây mới là entity candidate list.

Attributes, foreign keys, cardinality và cascade rules sẽ được chốt ở ERD phase.

---

# 42. Core v1 Main Flow

```text
User Registration
       ↓
Verification OTP stored in Redis
       ↓
OTP sent through Gmail SMTP
       ↓
Verify Email
       ↓
Login
       ↓
Create Project
       ↓
Become OWNER
       ↓
Create Folder / Category
       ↓
Invite Team Members
       ↓
Email Invitation
       ↓
Member Accepts
       ↓
ProjectMember created
       ↓
Upload Documents
       ↓
PostgreSQL Metadata
       +
MinIO Binary Storage
       ↓
Browse / Search
       ↓
Preview / Download
       ↓
Manage Files According to Permissions
```

---

# 43. Definition of Done – Core v1

Core KBase v1 được xem là hoàn thành khi:

- User có thể register.
- Verification OTP được tạo/lưu trong Redis với TTL và gửi qua Gmail SMTP.
- User có thể verify email và resend OTP theo cooldown.
- User chưa verify email không thể login.
- User có thể login/logout sau khi email verified.
- JWT authentication hoạt động.
- Authorization được enforce ở backend.
- User có thể tạo project.
- Project creator trở thành OWNER.
- OWNER có thể invite bằng email.
- Invitation có thể accept.
- User có thể tham gia nhiều project.
- OWNER có thể quản lý member.
- Folder hierarchy hoạt động.
- Category và tag hoạt động.
- File validation hoạt động.
- File upload vào MinIO thành công.
- Metadata lưu PostgreSQL chính xác.
- Project members có thể browse file.
- Preview hỗ trợ các file cơ bản.
- Download được authorization.
- MEMBER không modify được file người khác.
- OWNER quản lý được toàn bộ file trong project.
- Hard delete hoạt động đúng.
- Search metadata hoạt động.
- Pagination/filter/sort hoạt động.
- Swagger mô tả đầy đủ Core APIs.
- Application chạy được bằng Docker.
- PostgreSQL dùng named volume `postgres_data`.
- MinIO dùng named volume `minio_data`.
- Redis OTP store chạy bằng Docker service, không phụ thuộc Redis host-local.
- Flyway migration chạy vào PostgreSQL container khi backend startup.
- Không có resource leakage giữa các project.
- Không có public MinIO object ngoài authorization flow.

---

# 44. Core v1 Architecture Boundary

```text
                    ┌──────────────────┐
                    │ React / Next.js  │
                    └────────┬─────────┘
                             │
                         REST API
                             │
                    ┌────────▼─────────┐
                    │   Spring Boot    │
                    │                  │
                    │ Authentication   │
                    │ Users            │
                    │ Projects         │
                    │ Membership       │
                    │ Invitations      │
                    │ Folders          │
                    │ Categories       │
                    │ Tags             │
                    │ Documents        │
                    │ Search           │
                    └──────┬─────┬─────┘
                           │     │
                   ┌───────┘     ├───────────┐
                   ▼             ▼           ▼
           ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
           │ PostgreSQL   │ │    MinIO     │ │    Redis     │
           │              │ │              │ │              │
           │ Users        │ │ Binary Files │ │ OTP Hash     │
           │ Projects     │ │ Images       │ │ TTL/Attempts │
           │ Metadata     │ │ Videos       │ │ Cooldown     │
           └──────────────┘ └──────────────┘ └──────────────┘
                    │
                    └──────────────► Gmail SMTP
                                     OTP + Invitation Email


                 AI SERVICE
                    ↓
               NOT IN V1
```

---

# 45. Next Design Phase

Sau khi Core v1 Specification được chấp nhận, thứ tự thiết kế tiếp theo là:

```text
Core v1 Specification
        ↓
Entity Analysis
        ↓
ERD
        ↓
Database Schema
        ↓
REST API Specification
        ↓
Authentication / Authorization Design
        ↓
Spring Boot Module Structure
        ↓
MinIO Integration Design
        ↓
Docker Architecture
        ↓
Implementation Plan
        ↓
Implementation
```

Đây là baseline requirement cho KBase Core v1.