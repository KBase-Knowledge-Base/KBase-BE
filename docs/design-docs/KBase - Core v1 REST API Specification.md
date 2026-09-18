# KBase – Core v1
## REST API Specification

**Version:** Draft 2  
**API Style:** REST  
**Backend:** Java Spring Boot  
**Database:** PostgreSQL  
**Object Storage:** MinIO  
**OTP Store:** Redis  
**Email Delivery:** Gmail SMTP  
**API Documentation:** OpenAPI / Swagger UI  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa REST API contract cho KBase Core v1 dựa trên các tài liệu đã chốt trước đó:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design

Mục tiêu của tài liệu:

- Xác định endpoint.
- Xác định HTTP method.
- Xác định request/response DTO.
- Xác định authentication và authorization.
- Xác định business rule liên quan.
- Xác định HTTP status code.
- Xác định business error code.
- Tạo baseline để triển khai:
  - Controller
  - Service
  - Repository
  - Spring Security
  - Swagger/OpenAPI
  - Frontend integration

---

# 2. Core Decisions Preserved

REST API này giữ nguyên các business decision đã chốt:

```text
OWNER = project-level role
ADMIN = system-level role
USER = normal system-level role

Each project has exactly one OWNER.

Registration email verification uses OTP stored short-term in Redis and delivered through Gmail SMTP.

Invitation is email-based and continues to use a secure invitation link/token, not OTP.

Refresh sessions remain in PostgreSQL; Redis does not replace `refresh_sessions`.

MEMBER:
- can read/download all files in project
- can modify/delete only files uploaded by themselves

OWNER:
- manages the project
- manages project membership
- manages all files inside the project

ADMIN:
- system-level administrative override

Folder + Category + Tag are supported.

MEMBER may create tags.
MEMBER may assign/remove tags on own documents.
MEMBER cannot rename/delete shared tags.

Folder structure is managed by OWNER.

Category structure is managed by OWNER.

When MEMBER leaves or is removed:
- existing documents remain
- user loses project access
- uploaded_by_user_id remains for provenance
- OWNER continues managing those documents

Hard delete is used for Project and Document.

Document versioning is not included.

Metadata search only in Core v1.

PostgreSQL stores metadata.
MinIO stores binary files.

File-size limits are configurable.

AI/RAG/Embedding/Vector Search are deferred until Core is stable.
```

---

# 3. Base URL

All Core v1 APIs use:

```text
/api/v1
```

Examples:

```text
/api/v1/auth/login
/api/v1/projects
/api/v1/projects/{projectId}/documents
```

---

# 4. Content Types

Standard JSON API:

```http
Content-Type: application/json
Accept: application/json
```

File upload:

```http
Content-Type: multipart/form-data
```

File preview/download:

```http
Content-Type: <actual MIME type>
```

---

# 5. Authentication

Protected endpoints require:

```http
Authorization: Bearer <access-token>
```

Access token:

```text
JWT
TTL baseline: 15 minutes
```

Refresh token:

```text
TTL baseline: 7 days
Stored client-side in Secure + HttpOnly cookie
Server stores token hash in refresh_sessions
```

Email verification OTP:

```text
6 numeric digits
TTL baseline: 5 minutes
Resend cooldown baseline: 60 seconds
Maximum attempts baseline: 5
OTP verification state stored in Redis
Raw OTP delivered through Gmail SMTP
```

OTP values and token TTL values remain configurable.

A user must have:

```text
status = ACTIVE
emailVerified = true
```

before login succeeds.

---

# 6. Refresh Token Cookie

Recommended cookie baseline:

```text
Name: kbase_refresh_token
HttpOnly: true
Secure: true in production
SameSite: Lax
Path: /api/v1/auth
```

Raw refresh token must not be returned in normal JSON response.

Development environment may relax `Secure` when HTTPS is unavailable locally.

---

# 7. Authorization Layers

Authorization is evaluated in this order:

```text
Authenticated?
    ↓
User ACTIVE?
    ↓
Email verified?
    ↓
Resource exists?
    ↓
Project membership?
    ↓
System role ADMIN?
    ↓
Project role OWNER/MEMBER?
    ↓
Resource ownership rule?
```

Frontend hiding a button is not authorization.

Every protected operation must be enforced by Spring Boot.

---

# 8. Role Definitions

## SystemRole

```text
ADMIN
USER
```

## ProjectRole

```text
OWNER
MEMBER
```

## UserStatus

```text
ACTIVE
DISABLED
```

Email verification is independent from `UserStatus` and is persisted as `users.email_verified_at`. API responses expose a derived `emailVerified` boolean.

## InvitationStatus

```text
PENDING
ACCEPTED
EXPIRED
CANCELLED
```

## FileKind

```text
DOCUMENT
IMAGE
VIDEO
```

---

# 9. Standard Error Response

All API errors should follow a consistent model.

```json
{
  "timestamp": "2026-09-16T03:00:00Z",
  "status": 403,
  "code": "DOCUMENT_MODIFICATION_FORBIDDEN",
  "message": "You do not have permission to modify this document.",
  "path": "/api/v1/documents/123"
}
```

Validation response may include field errors:

```json
{
  "timestamp": "2026-09-16T03:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "path": "/api/v1/auth/register",
  "errors": {
    "email": "Invalid email address",
    "password": "Password must contain between 8 and 64 characters"
  }
}
```

---

# 10. Standard HTTP Status Codes

| Status | Usage |
|---|---|
| 200 | Successful GET/PATCH/action |
| 201 | Resource created |
| 204 | Successful delete/logout with no response body |
| 400 | Request validation / malformed request |
| 401 | Missing/invalid authentication |
| 403 | Authenticated but not authorized |
| 404 | Resource does not exist |
| 409 | Business rule conflict |
| 413 | Uploaded file too large |
| 415 | Unsupported file/media type |
| 429 | OTP resend/attempt throttling |
| 500 | Unexpected internal failure |
| 503 | MinIO/email/other required service unavailable |

---

# 11. Pagination Contract

List endpoints that may grow large use:

```text
page
size
sort
```

Baseline:

```text
page = 0
size = 20
max size = 100
```

Example:

```http
GET /api/v1/projects?page=0&size=20&sort=createdAt,desc
```

Standard page response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

---

# 12. Common Date Format

All API timestamps use ISO 8601 UTC.

Example:

```text
2026-09-16T03:00:00Z
```

---

# 13. Authentication APIs

## 13.1 Register

```http
POST /api/v1/auth/register
```

Authentication:

```text
Public
```

Request:

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123",
  "displayName": "Example User"
}
```

Validation:

```text
email:
- required
- valid format
- normalized lowercase
- unique

password:
- required
- 8..64 characters

displayName:
- required
- non-blank
- max 100 characters
```

Response:

```http
201 Created
```

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Example User",
  "systemRole": "USER",
  "status": "ACTIVE",
  "emailVerified": false,
  "createdAt": "2026-09-16T03:00:00Z"
}
```

Baseline behavior:

```text
Register does not automatically create login session.
Registration creates an unverified account.
Backend generates verification OTP, stores OTP state in Redis with TTL, and sends the raw OTP through Gmail SMTP.
Client verifies email before login.
```

Errors:

```text
400 VALIDATION_ERROR
409 EMAIL_ALREADY_EXISTS
503 OTP_SERVICE_UNAVAILABLE
503 EMAIL_SERVICE_UNAVAILABLE
```

---

## 13.2 Verify Email OTP

```http
POST /api/v1/auth/verify-email
```

Authentication:

```text
Public
```

Request:

```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

Rules:

```text
email normalized lowercase
account must exist
account must not already be verified
OTP must match Redis verification state
OTP must not be expired
attempt limit must not be exceeded
```

Success:

```http
200 OK
```

```json
{
  "email": "user@example.com",
  "emailVerified": true
}
```

Successful verification:

```text
sets users.email_verified_at = current UTC time
deletes/invalidate OTP verification state in Redis
```

Errors:

```text
400 INVALID_OTP
400 OTP_EXPIRED
404 USER_NOT_FOUND
409 EMAIL_ALREADY_VERIFIED
429 OTP_ATTEMPTS_EXCEEDED
503 OTP_SERVICE_UNAVAILABLE
```

---

## 13.3 Resend Verification OTP

```http
POST /api/v1/auth/resend-verification-otp
```

Authentication:

```text
Public
```

Request:

```json
{
  "email": "user@example.com"
}
```

Behavior:

```text
account must exist
email must not already be verified
resend cooldown enforced through Redis
old OTP becomes invalid
new OTP TTL/attempt counter is reset
new OTP is sent through Gmail SMTP
```

Success:

```http
204 No Content
```

Errors:

```text
404 USER_NOT_FOUND
409 EMAIL_ALREADY_VERIFIED
429 OTP_RESEND_COOLDOWN
503 OTP_SERVICE_UNAVAILABLE
503 EMAIL_SERVICE_UNAVAILABLE
```

---

## 13.4 Login

```http
POST /api/v1/auth/login
```

Authentication:

```text
Public
```

Request:

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123"
}
```

Success:

```http
200 OK
Set-Cookie: kbase_refresh_token=...; HttpOnly; ...
```

Response:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "Example User",
    "systemRole": "USER",
    "status": "ACTIVE",
    "emailVerified": true
  }
}
```

Errors:

```text
401 INVALID_CREDENTIALS
403 EMAIL_NOT_VERIFIED
403 ACCOUNT_DISABLED
```

Login failure response should not reveal whether the email exists.

---

## 13.5 Refresh Access Token

```http
POST /api/v1/auth/refresh
```

Authentication:

```text
Refresh token cookie required
Access token not required
```

Success:

```http
200 OK
```

```json
{
  "accessToken": "<new-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Possible implementation:

```text
refresh-token rotation may be added,
but is not required by current business specification.
```

Errors:

```text
401 REFRESH_TOKEN_MISSING
401 INVALID_REFRESH_TOKEN
401 REFRESH_TOKEN_EXPIRED
401 REFRESH_SESSION_REVOKED
403 ACCOUNT_DISABLED
```

---

## 13.6 Logout

```http
POST /api/v1/auth/logout
```

Authentication:

```text
Refresh token cookie/session
```

Behavior:

```text
Revoke current refresh session.
Clear refresh-token cookie.
```

Response:

```http
204 No Content
```

---

# 14. Current User APIs

## 14.1 Get Current User

```http
GET /api/v1/users/me
```

Authentication:

```text
Authenticated ACTIVE user
```

Response:

```http
200 OK
```

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Example User",
  "systemRole": "USER",
  "status": "ACTIVE",
  "emailVerified": true,
  "createdAt": "2026-09-16T03:00:00Z",
  "updatedAt": "2026-09-16T03:00:00Z"
}
```

---

## 14.2 Update Current Profile

```http
PATCH /api/v1/users/me
```

Request:

```json
{
  "displayName": "New Display Name"
}
```

User cannot modify:

```text
email
systemRole
status
```

Response:

```http
200 OK
```

Errors:

```text
400 VALIDATION_ERROR
```

---

## 14.3 Change Password

```http
PUT /api/v1/users/me/password
```

Request:

```json
{
  "currentPassword": "OldPassword123",
  "newPassword": "NewPassword123"
}
```

Response:

```http
204 No Content
```

Errors:

```text
400 VALIDATION_ERROR
401 CURRENT_PASSWORD_INVALID
```

Recommended behavior:

```text
Existing refresh sessions may be revoked after password change.
```

This is a security baseline recommendation rather than a previously fixed business rule.

---

# 15. Admin User Management APIs

All endpoints in this section require:

```text
SystemRole = ADMIN
```

---

## 15.1 List Users

```http
GET /api/v1/admin/users
```

Query:

```text
q
status
systemRole
page
size
sort
```

Response:

```http
200 OK
```

---

## 15.2 Get User

```http
GET /api/v1/admin/users/{userId}
```

Response:

```http
200 OK
```

Errors:

```text
404 USER_NOT_FOUND
```

---

## 15.3 Change User Status

```http
PATCH /api/v1/admin/users/{userId}/status
```

Request:

```json
{
  "status": "DISABLED"
}
```

or:

```json
{
  "status": "ACTIVE"
}
```

Response:

```http
200 OK
```

Errors:

```text
404 USER_NOT_FOUND
400 INVALID_USER_STATUS
```

---

## 15.4 Delete User

```http
DELETE /api/v1/admin/users/{userId}
```

Behavior:

```text
Hard delete only when dependencies allow it.
```

User cannot be deleted if unresolved dependencies exist, for example:

```text
owns project
has uploaded project documents
is referenced by invitations
has memberships that have not been resolved
```

Response:

```http
204 No Content
```

Errors:

```text
404 USER_NOT_FOUND
409 USER_HAS_DEPENDENCIES
409 USER_OWNS_PROJECT
```

---

# 16. Project APIs

---

## 16.1 Create Project

```http
POST /api/v1/projects
```

Authentication:

```text
Authenticated ACTIVE user
```

Request:

```json
{
  "name": "KBase Project",
  "description": "Internal knowledge base project"
}
```

Behavior:

```text
Create Project
+
Create ProjectMember for creator with role OWNER

Both operations must be transactional.
```

Response:

```http
201 Created
```

```json
{
  "id": "uuid",
  "name": "KBase Project",
  "description": "Internal knowledge base project",
  "currentUserRole": "OWNER",
  "createdAt": "2026-09-16T03:00:00Z",
  "updatedAt": "2026-09-16T03:00:00Z"
}
```

Errors:

```text
400 VALIDATION_ERROR
```

---

## 16.2 List My Projects

```http
GET /api/v1/projects
```

For normal USER:

```text
Return only projects where current user is ProjectMember.
```

For ADMIN:

```text
Baseline: this endpoint still behaves as "my projects".
Use admin endpoint for all projects.
```

Query:

```text
q
role
page
size
sort
```

Example:

```http
GET /api/v1/projects?role=OWNER&page=0&size=20
```

Response:

```http
200 OK
```

---

## 16.3 Get Project

```http
GET /api/v1/projects/{projectId}
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Response:

```json
{
  "id": "uuid",
  "name": "KBase Project",
  "description": "Internal knowledge base project",
  "currentUserRole": "MEMBER",
  "createdAt": "2026-09-16T03:00:00Z",
  "updatedAt": "2026-09-16T03:00:00Z"
}
```

Errors:

```text
404 PROJECT_NOT_FOUND
403 PROJECT_ACCESS_FORBIDDEN
```

---

## 16.4 Update Project

```http
PATCH /api/v1/projects/{projectId}
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "name": "Updated Project Name",
  "description": "Updated description"
}
```

Response:

```http
200 OK
```

Errors:

```text
404 PROJECT_NOT_FOUND
403 PROJECT_MANAGEMENT_FORBIDDEN
400 VALIDATION_ERROR
```

---

## 16.5 Delete Project

```http
DELETE /api/v1/projects/{projectId}
```

Permission:

```text
OWNER
ADMIN
```

Behavior:

```text
Hard delete project.
Delete MinIO project files.
Delete relational project data.
```

Response:

```http
204 No Content
```

Errors:

```text
404 PROJECT_NOT_FOUND
403 PROJECT_MANAGEMENT_FORBIDDEN
503 STORAGE_SERVICE_UNAVAILABLE
500 PROJECT_DELETE_FAILED
```

Frontend should require explicit confirmation because this is destructive.

---

# 17. Admin Project APIs

## 17.1 List All Projects

```http
GET /api/v1/admin/projects
```

Permission:

```text
ADMIN
```

Query:

```text
q
ownerId
page
size
sort
```

Response:

```http
200 OK
```

Admin may use the standard project detail/update/delete endpoints due to administrative override.

---

# 18. Project Member APIs

---

## 18.1 List Project Members

```http
GET /api/v1/projects/{projectId}/members
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Response:

```json
{
  "content": [
    {
      "membershipId": "uuid",
      "userId": "uuid",
      "email": "member@example.com",
      "displayName": "Project Member",
      "role": "MEMBER",
      "joinedAt": "2026-09-16T03:00:00Z"
    }
  ]
}
```

Pagination may be used for large projects.

Errors:

```text
404 PROJECT_NOT_FOUND
403 PROJECT_ACCESS_FORBIDDEN
```

---

## 18.2 Remove Project Member

```http
DELETE /api/v1/projects/{projectId}/members/{userId}
```

Permission:

```text
OWNER
ADMIN
```

Rules:

```text
OWNER cannot remove themselves.
OWNER cannot remove the current project OWNER membership through this endpoint.

Removing MEMBER does NOT delete documents uploaded by that user.
```

Response:

```http
204 No Content
```

Errors:

```text
404 PROJECT_NOT_FOUND
404 PROJECT_MEMBER_NOT_FOUND
403 PROJECT_MANAGEMENT_FORBIDDEN
409 PROJECT_OWNER_REMOVAL_FORBIDDEN
```

---

## 18.3 Leave Project

```http
DELETE /api/v1/projects/{projectId}/members/me
```

Permission:

```text
MEMBER
```

Rule:

```text
OWNER cannot leave in Core v1.
```

Documents previously uploaded remain in project.

Response:

```http
204 No Content
```

Errors:

```text
404 PROJECT_NOT_FOUND
404 PROJECT_MEMBER_NOT_FOUND
409 OWNER_CANNOT_LEAVE_PROJECT
```

Ownership transfer is outside Core v1.

---

# 19. Project Invitation APIs

---

## 19.1 Create Invitation

```http
POST /api/v1/projects/{projectId}/invitations
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "email": "newmember@example.com"
}
```

Rules:

```text
email is normalized lowercase

if user is already a project member:
→ reject

if same project + email already has PENDING invitation:
→ reject and allow explicit resend endpoint

new invitation expiration baseline:
72 hours
configurable

invitation email is delivered through Gmail SMTP
```

Response:

```http
201 Created
```

```json
{
  "id": "uuid",
  "projectId": "uuid",
  "email": "newmember@example.com",
  "status": "PENDING",
  "expiresAt": "2026-09-19T03:00:00Z",
  "createdAt": "2026-09-16T03:00:00Z"
}
```

Do not return raw invitation token in standard API response.

Errors:

```text
404 PROJECT_NOT_FOUND
403 PROJECT_MANAGEMENT_FORBIDDEN
409 PROJECT_MEMBER_ALREADY_EXISTS
409 INVITATION_ALREADY_PENDING
503 EMAIL_SERVICE_UNAVAILABLE
```

---

## 19.2 List Project Invitations

```http
GET /api/v1/projects/{projectId}/invitations
```

Permission:

```text
OWNER
ADMIN
```

Query:

```text
status
page
size
sort
```

Response:

```http
200 OK
```

---

## 19.3 Resend Invitation

```http
POST /api/v1/projects/{projectId}/invitations/{invitationId}/resend
```

Permission:

```text
OWNER
ADMIN
```

Allowed:

```text
PENDING invitation
```

Behavior:

```text
Generate new token.
Invalidate old token.
Reset expiration.
Send new invitation email through Gmail SMTP.
```

Response:

```http
200 OK
```

Errors:

```text
404 INVITATION_NOT_FOUND
409 INVITATION_NOT_PENDING
409 PROJECT_MEMBER_ALREADY_EXISTS
503 EMAIL_SERVICE_UNAVAILABLE
```

---

## 19.4 Cancel Invitation

```http
DELETE /api/v1/projects/{projectId}/invitations/{invitationId}
```

Permission:

```text
OWNER
ADMIN
```

Allowed:

```text
PENDING invitation
```

Behavior:

```text
status → CANCELLED
token becomes unusable
```

Although Project/Document use hard delete, invitation cancellation is represented by its existing status because invitation history already has lifecycle states.

Response:

```http
204 No Content
```

Errors:

```text
404 INVITATION_NOT_FOUND
409 INVITATION_NOT_PENDING
```

---

## 19.5 Accept Invitation

The email link should lead to the frontend, for example:

```text
https://kbase.example.com/invitations/accept?token=<raw-token>
```

Frontend sends token to backend:

```http
POST /api/v1/invitations/accept
```

Authentication:

```text
Authenticated ACTIVE user with verified email
```

Request:

```json
{
  "token": "<raw-invitation-token>"
}
```

Rules:

```text
Invitation must exist.
Status must be PENDING.
Invitation must not be expired.
Current user's normalized email must equal invitation email.
Current user must already have completed email verification.
Current user must not already be a project member.
```

If invited recipient does not yet have an account:

```text
Register
    ↓
Verify registration email with OTP from Gmail
    ↓
Login
    ↓
Accept invitation token
```

Invitation acceptance itself continues to use the invitation link/token and does not use OTP.

Transactional behavior:

```text
Create ProjectMember(role = MEMBER)
+
Invitation status = ACCEPTED
+
accepted_at = now
```

Response:

```http
200 OK
```

```json
{
  "projectId": "uuid",
  "membershipId": "uuid",
  "role": "MEMBER",
  "joinedAt": "2026-09-16T03:00:00Z"
}
```

Errors:

```text
404 INVITATION_NOT_FOUND
409 INVITATION_EXPIRED
409 INVITATION_NOT_PENDING
403 INVITATION_EMAIL_MISMATCH
409 PROJECT_MEMBER_ALREADY_EXISTS
```

---

# 20. Folder APIs

Folder hierarchy is project-scoped.

---

## 20.1 List Folders

```http
GET /api/v1/projects/{projectId}/folders
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Optional query:

```text
parentId
```

If omitted:

```text
return all folders as a flat list
```

Frontend may construct tree using `parentId`.

Response:

```json
[
  {
    "id": "uuid",
    "projectId": "uuid",
    "parentId": null,
    "name": "Backend",
    "createdAt": "2026-09-16T03:00:00Z",
    "updatedAt": "2026-09-16T03:00:00Z"
  }
]
```

---

## 20.2 Create Folder

```http
POST /api/v1/projects/{projectId}/folders
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "name": "Backend",
  "parentId": null
}
```

Rules:

```text
parent must belong to same project
folder name unique case-insensitively within same parent
```

Response:

```http
201 Created
```

Errors:

```text
404 PROJECT_NOT_FOUND
404 PARENT_FOLDER_NOT_FOUND
403 PROJECT_MANAGEMENT_FORBIDDEN
409 FOLDER_NAME_ALREADY_EXISTS
```

---

## 20.3 Update / Move Folder

```http
PATCH /api/v1/projects/{projectId}/folders/{folderId}
```

Permission:

```text
OWNER
ADMIN
```

Request may contain:

```json
{
  "name": "Security",
  "parentId": "uuid-or-null"
}
```

Rules:

```text
cannot parent itself
cannot move folder into descendant
new parent must belong to same project
name must remain unique within new parent
```

Response:

```http
200 OK
```

Errors:

```text
404 FOLDER_NOT_FOUND
404 PARENT_FOLDER_NOT_FOUND
409 FOLDER_NAME_ALREADY_EXISTS
409 FOLDER_CYCLE_DETECTED
```

---

## 20.4 Delete Folder

```http
DELETE /api/v1/projects/{projectId}/folders/{folderId}
```

Permission:

```text
OWNER
ADMIN
```

Rule:

```text
Folder must contain no subfolder and no document.
```

Response:

```http
204 No Content
```

Errors:

```text
404 FOLDER_NOT_FOUND
409 FOLDER_NOT_EMPTY
```

---

# 21. Category APIs

---

## 21.1 List Categories

```http
GET /api/v1/projects/{projectId}/categories
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Response:

```http
200 OK
```

---

## 21.2 Create Category

```http
POST /api/v1/projects/{projectId}/categories
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "name": "Technical"
}
```

Response:

```http
201 Created
```

Errors:

```text
409 CATEGORY_NAME_ALREADY_EXISTS
```

---

## 21.3 Rename Category

```http
PATCH /api/v1/projects/{projectId}/categories/{categoryId}
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "name": "Architecture"
}
```

Response:

```http
200 OK
```

Errors:

```text
404 CATEGORY_NOT_FOUND
409 CATEGORY_NAME_ALREADY_EXISTS
```

---

## 21.4 Delete Category

```http
DELETE /api/v1/projects/{projectId}/categories/{categoryId}
```

Permission:

```text
OWNER
ADMIN
```

Rule:

```text
Category cannot be deleted while any document uses it.
```

Response:

```http
204 No Content
```

Errors:

```text
404 CATEGORY_NOT_FOUND
409 CATEGORY_IN_USE
```

---

# 22. Tag APIs

---

## 22.1 List Tags

```http
GET /api/v1/projects/{projectId}/tags
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Optional query:

```text
q
```

Response:

```http
200 OK
```

---

## 22.2 Create Tag

```http
POST /api/v1/projects/{projectId}/tags
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Request:

```json
{
  "name": "spring-boot"
}
```

Response:

```http
201 Created
```

Errors:

```text
409 TAG_NAME_ALREADY_EXISTS
```

A MEMBER may create a shared tag because this behavior was explicitly accepted for Core v1.

---

## 22.3 Rename Tag

```http
PATCH /api/v1/projects/{projectId}/tags/{tagId}
```

Permission:

```text
OWNER
ADMIN
```

Request:

```json
{
  "name": "spring"
}
```

Response:

```http
200 OK
```

Errors:

```text
404 TAG_NOT_FOUND
409 TAG_NAME_ALREADY_EXISTS
403 TAG_MANAGEMENT_FORBIDDEN
```

---

## 22.4 Delete Tag

```http
DELETE /api/v1/projects/{projectId}/tags/{tagId}
```

Permission:

```text
OWNER
ADMIN
```

Behavior:

```text
Delete Tag
Delete related DocumentTag rows
Keep Documents
```

Response:

```http
204 No Content
```

---

# 23. Document APIs

`Document` represents all uploaded resources:

```text
documents
images
videos
```

Supported Core v1 file extensions:

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

JPG
JPEG
PNG
GIF
SVG
BMP

MP4
MOV
AVI
```

---

# 24. Upload Limits

Baseline proposal remains configurable:

| Type | Initial Limit |
|---|---:|
| Document / Office | 50 MB |
| Image | 20 MB |
| Video | 500 MB |

Batch baseline:

```text
Maximum 10 files
```

Actual values are configuration, not hard-coded business constants.

---

# 25. Upload Single Document

```http
POST /api/v1/projects/{projectId}/documents
Content-Type: multipart/form-data
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Parts:

```text
file          required binary part
metadata      optional application/json part
```

Example metadata:

```json
{
  "displayName": "Backend Authentication Specification.pdf",
  "description": "JWT authentication specification",
  "folderId": "uuid-or-null",
  "categoryId": "uuid-or-null",
  "tagIds": [
    "uuid",
    "uuid"
  ]
}
```

If `displayName` is omitted:

```text
use original filename as initial display name
```

Validation:

```text
project membership
file exists
file not empty
allowed extension
allowed MIME type
configured file size
folder belongs to project
category belongs to project
all tags belong to project
```

Success:

```http
201 Created
```

```json
{
  "id": "uuid",
  "projectId": "uuid",
  "uploadedBy": {
    "id": "uuid",
    "displayName": "Example User"
  },
  "folderId": "uuid",
  "category": {
    "id": "uuid",
    "name": "Technical"
  },
  "tags": [
    {
      "id": "uuid",
      "name": "jwt"
    }
  ],
  "displayName": "Backend Authentication Specification.pdf",
  "originalFilename": "auth-spec-final.pdf",
  "fileKind": "DOCUMENT",
  "extension": "pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 123456,
  "description": "JWT authentication specification",
  "createdAt": "2026-09-16T03:00:00Z",
  "updatedAt": "2026-09-16T03:00:00Z"
}
```

The response must not expose MinIO credentials.

`storageKey` should normally remain internal and does not need to be exposed to frontend.

Errors:

```text
400 FILE_EMPTY
400 INVALID_FILE_METADATA
403 PROJECT_ACCESS_FORBIDDEN
404 FOLDER_NOT_FOUND
404 CATEGORY_NOT_FOUND
404 TAG_NOT_FOUND
413 FILE_TOO_LARGE
415 UNSUPPORTED_FILE_TYPE
415 MIME_TYPE_MISMATCH
503 STORAGE_SERVICE_UNAVAILABLE
500 FILE_UPLOAD_FAILED
```

---

# 26. Batch Upload

```http
POST /api/v1/projects/{projectId}/documents/batch
Content-Type: multipart/form-data
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

Baseline:

```text
1..configured max files
initial proposal max = 10
```

To keep Core v1 API simple, batch upload applies common optional metadata to all files:

```text
folderId
categoryId
tagIds
```

Per-file metadata can be adjusted after upload through document update API.

This is an API baseline choice, not a new domain rule.

Response:

```http
201 Created
```

```json
{
  "documents": [
    {
      "id": "uuid",
      "displayName": "file1.pdf"
    },
    {
      "id": "uuid",
      "displayName": "file2.docx"
    }
  ]
}
```

Recommended transactional policy across PostgreSQL + MinIO:

```text
Do not pretend MinIO + PostgreSQL are a single ACID transaction.
Use compensation cleanup when metadata persistence fails after object upload.
```

---

# 27. List / Search Project Documents

```http
GET /api/v1/projects/{projectId}/documents
```

Permission:

```text
MEMBER
OWNER
ADMIN
```

This endpoint provides both browsing and Core v1 metadata search.

Query parameters:

```text
q
folderId
categoryId
tagId
fileKind
uploadedBy
createdFrom
createdTo
page
size
sort
```

Example:

```http
GET /api/v1/projects/{projectId}/documents?q=authentication&categoryId=...&fileKind=DOCUMENT&page=0&size=20&sort=createdAt,desc
```

`q` searches metadata only:

```text
display_name
original_filename
description
category name
tag name
```

It does not search inside PDF/DOCX/etc.

Response:

```json
{
  "content": [
    {
      "id": "uuid",
      "displayName": "Authentication Specification.pdf",
      "originalFilename": "auth-spec.pdf",
      "fileKind": "DOCUMENT",
      "extension": "pdf",
      "mimeType": "application/pdf",
      "sizeBytes": 123456,
      "folderId": "uuid",
      "category": {
        "id": "uuid",
        "name": "Technical"
      },
      "tags": [
        {
          "id": "uuid",
          "name": "jwt"
        }
      ],
      "uploadedBy": {
        "id": "uuid",
        "displayName": "Example User"
      },
      "createdAt": "2026-09-16T03:00:00Z",
      "updatedAt": "2026-09-16T03:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

# 28. Get Document Metadata

```http
GET /api/v1/documents/{documentId}
```

Permission:

```text
MEMBER of document project
OWNER
ADMIN
```

Response:

```http
200 OK
```

Errors:

```text
404 DOCUMENT_NOT_FOUND
403 PROJECT_ACCESS_FORBIDDEN
```

---

# 29. Update Document Metadata

```http
PATCH /api/v1/documents/{documentId}
```

Permission:

```text
MEMBER:
own document only

OWNER:
any document in project

ADMIN:
any document
```

Request may contain:

```json
{
  "displayName": "New Display Name.pdf",
  "description": "Updated description",
  "folderId": "uuid-or-null",
  "categoryId": "uuid-or-null",
  "tagIds": [
    "uuid",
    "uuid"
  ]
}
```

Rules:

```text
folder/category/tags must belong to document project

MEMBER can modify only own document

OWNER can modify any document in project

rename changes displayName only
MinIO object key does not need to change
```

Response:

```http
200 OK
```

Errors:

```text
404 DOCUMENT_NOT_FOUND
403 DOCUMENT_MODIFICATION_FORBIDDEN
404 FOLDER_NOT_FOUND
404 CATEGORY_NOT_FOUND
404 TAG_NOT_FOUND
400 VALIDATION_ERROR
```

---

# 30. Preview Document

```http
GET /api/v1/documents/{documentId}/preview
```

Permission:

```text
Any project member
OWNER
ADMIN
```

Core v1 preview priority:

```text
PDF
JPG/JPEG
PNG
GIF
SVG
BMP
TXT
MD
MP4
```

Office formats such as:

```text
DOC/DOCX
XLS/XLSX
PPT/PPTX
```

are not required to have full browser rendering in Core v1.

Response:

```http
200 OK
Content-Type: <mime-type>
Content-Disposition: inline; filename="..."
```

If preview is unsupported:

```text
415 PREVIEW_NOT_SUPPORTED
```

Download remains available.

---

# 31. Download Document

```http
GET /api/v1/documents/{documentId}/download
```

Permission:

```text
Any project member
OWNER
ADMIN
```

Response:

```http
200 OK
Content-Type: <mime-type>
Content-Disposition: attachment; filename="..."
```

Binary object remains private in MinIO.

Backend may internally:

```text
stream object
```

or use:

```text
short-lived presigned URL
```

provided authorization occurs first and the public API contract remains secure.

Errors:

```text
404 DOCUMENT_NOT_FOUND
403 PROJECT_ACCESS_FORBIDDEN
503 STORAGE_SERVICE_UNAVAILABLE
```

---

# 32. Delete Document

```http
DELETE /api/v1/documents/{documentId}
```

Permission:

```text
MEMBER:
own document only

OWNER:
any document in project

ADMIN:
any document
```

Behavior:

```text
Hard delete

Delete MinIO object
Delete Document
Delete related DocumentTag rows
```

Response:

```http
204 No Content
```

Errors:

```text
404 DOCUMENT_NOT_FOUND
403 DOCUMENT_MODIFICATION_FORBIDDEN
503 STORAGE_SERVICE_UNAVAILABLE
500 DOCUMENT_DELETE_FAILED
```

---

# 33. Document Permission Evaluation

For mutation:

```text
if currentUser.systemRole == ADMIN
    ALLOW

else load project membership

if membership.role == OWNER
    ALLOW

else if membership.role == MEMBER
    ALLOW only when
    document.uploadedByUserId == currentUser.id

else
    DENY
```

For read/preview/download:

```text
ADMIN
or
any active ProjectMember
```

---

# 34. Search Rules

Core v1 search is strictly metadata search.

Supported:

```text
displayName
originalFilename
description
category name
tag name
```

Not supported:

```text
PDF content
Word content
PowerPoint content
Excel cell content
video transcript
semantic similarity
embedding
RAG
```

Those belong to later phases.

---

# 35. Resource Isolation

Project is a security boundary.

A user not belonging to Project B cannot:

```text
GET Project B
GET Project B members
GET Project B folders
GET Project B categories
GET Project B tags
GET Project B documents
search Project B
preview Project B documents
download Project B documents
modify Project B documents
```

Knowing a UUID does not grant access.

Baseline response for an existing but unauthorized resource:

```text
403 PROJECT_ACCESS_FORBIDDEN
```

---

# 36. Swagger / OpenAPI Security Scheme

Recommended OpenAPI configuration:

```yaml
type: http
scheme: bearer
bearerFormat: JWT
```

Swagger UI must allow:

```text
Authorize
Bearer <access-token>
```

Every protected endpoint must declare authentication requirement.

---

# 37. DTO Baseline

## UserResponse

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Example User",
  "systemRole": "USER",
  "status": "ACTIVE",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## ProjectResponse

```json
{
  "id": "uuid",
  "name": "Project",
  "description": "...",
  "currentUserRole": "OWNER",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## ProjectMemberResponse

```json
{
  "membershipId": "uuid",
  "userId": "uuid",
  "email": "user@example.com",
  "displayName": "Example User",
  "role": "MEMBER",
  "joinedAt": "..."
}
```

## FolderResponse

```json
{
  "id": "uuid",
  "projectId": "uuid",
  "parentId": null,
  "name": "Backend",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## CategoryResponse

```json
{
  "id": "uuid",
  "name": "Technical",
  "createdAt": "...",
  "updatedAt": "..."
}
```

## TagResponse

```json
{
  "id": "uuid",
  "name": "jwt",
  "createdAt": "..."
}
```

## DocumentResponse

```json
{
  "id": "uuid",
  "projectId": "uuid",
  "uploadedBy": {
    "id": "uuid",
    "displayName": "Example User"
  },
  "folderId": "uuid",
  "category": {
    "id": "uuid",
    "name": "Technical"
  },
  "tags": [],
  "displayName": "Specification.pdf",
  "originalFilename": "spec.pdf",
  "fileKind": "DOCUMENT",
  "extension": "pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 123456,
  "description": "...",
  "createdAt": "...",
  "updatedAt": "..."
}
```

Do not expose:

```text
password_hash
refresh token hash
invitation token hash
MinIO credentials
JWT signing secrets
internal storage credentials
```

`storageKey` should remain internal unless a future backend use case specifically requires exposing it.

---

# 38. Business Error Codes

## Authentication

```text
VALIDATION_ERROR
EMAIL_ALREADY_EXISTS
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
ACCOUNT_DISABLED
REFRESH_TOKEN_MISSING
INVALID_REFRESH_TOKEN
REFRESH_TOKEN_EXPIRED
REFRESH_SESSION_REVOKED
CURRENT_PASSWORD_INVALID
```

## User

```text
USER_NOT_FOUND
USER_HAS_DEPENDENCIES
USER_OWNS_PROJECT
INVALID_USER_STATUS
```

## Project

```text
PROJECT_NOT_FOUND
PROJECT_ACCESS_FORBIDDEN
PROJECT_MANAGEMENT_FORBIDDEN
```

## Member

```text
PROJECT_MEMBER_NOT_FOUND
PROJECT_MEMBER_ALREADY_EXISTS
PROJECT_OWNER_REMOVAL_FORBIDDEN
OWNER_CANNOT_LEAVE_PROJECT
```

## Invitation

```text
INVITATION_NOT_FOUND
INVITATION_ALREADY_PENDING
INVITATION_NOT_PENDING
INVITATION_EXPIRED
INVITATION_EMAIL_MISMATCH
```

## Folder

```text
FOLDER_NOT_FOUND
PARENT_FOLDER_NOT_FOUND
FOLDER_NAME_ALREADY_EXISTS
FOLDER_CYCLE_DETECTED
FOLDER_NOT_EMPTY
```

## Category

```text
CATEGORY_NOT_FOUND
CATEGORY_NAME_ALREADY_EXISTS
CATEGORY_IN_USE
```

## Tag

```text
TAG_NOT_FOUND
TAG_NAME_ALREADY_EXISTS
TAG_MANAGEMENT_FORBIDDEN
```

## Document

```text
DOCUMENT_NOT_FOUND
DOCUMENT_MODIFICATION_FORBIDDEN
FILE_EMPTY
FILE_TOO_LARGE
UNSUPPORTED_FILE_TYPE
MIME_TYPE_MISMATCH
INVALID_FILE_METADATA
PREVIEW_NOT_SUPPORTED
FILE_UPLOAD_FAILED
DOCUMENT_DELETE_FAILED
```

## Infrastructure

```text
STORAGE_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
OTP_SERVICE_UNAVAILABLE
INTERNAL_SERVER_ERROR
```

---

# 39. Endpoint Permission Matrix

| Endpoint Area | MEMBER | OWNER | ADMIN |
|---|---:|---:|---:|
| View project | ✅ | ✅ | ✅ |
| Update project | ❌ | ✅ | ✅ |
| Delete project | ❌ | ✅ | ✅ |
| View members | ✅ | ✅ | ✅ |
| Remove member | ❌ | ✅ | ✅ |
| Leave project | ✅ | ❌ | N/A |
| List invitations | ❌ | ✅ | ✅ |
| Send invitation | ❌ | ✅ | ✅ |
| Resend invitation | ❌ | ✅ | ✅ |
| Cancel invitation | ❌ | ✅ | ✅ |
| View folders | ✅ | ✅ | ✅ |
| Manage folders | ❌ | ✅ | ✅ |
| View categories | ✅ | ✅ | ✅ |
| Manage categories | ❌ | ✅ | ✅ |
| View tags | ✅ | ✅ | ✅ |
| Create tag | ✅ | ✅ | ✅ |
| Rename/delete tag | ❌ | ✅ | ✅ |
| Upload document | ✅ | ✅ | ✅ |
| View document | ✅ | ✅ | ✅ |
| Preview/download document | ✅ | ✅ | ✅ |
| Modify own document | ✅ | ✅ | ✅ |
| Modify another member's document | ❌ | ✅ | ✅ |
| Delete own document | ✅ | ✅ | ✅ |
| Delete another member's document | ❌ | ✅ | ✅ |

---

# 40. Main Endpoint Summary

## Authentication

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/verify-email
POST   /api/v1/auth/resend-verification-otp
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
```

## Current User

```text
GET    /api/v1/users/me
PATCH  /api/v1/users/me
PUT    /api/v1/users/me/password
```

## Admin Users

```text
GET    /api/v1/admin/users
GET    /api/v1/admin/users/{userId}
PATCH  /api/v1/admin/users/{userId}/status
DELETE /api/v1/admin/users/{userId}
```

## Projects

```text
POST   /api/v1/projects
GET    /api/v1/projects
GET    /api/v1/projects/{projectId}
PATCH  /api/v1/projects/{projectId}
DELETE /api/v1/projects/{projectId}
```

## Admin Projects

```text
GET    /api/v1/admin/projects
```

## Members

```text
GET    /api/v1/projects/{projectId}/members
DELETE /api/v1/projects/{projectId}/members/{userId}
DELETE /api/v1/projects/{projectId}/members/me
```

## Invitations

```text
POST   /api/v1/projects/{projectId}/invitations
GET    /api/v1/projects/{projectId}/invitations
POST   /api/v1/projects/{projectId}/invitations/{invitationId}/resend
DELETE /api/v1/projects/{projectId}/invitations/{invitationId}

POST   /api/v1/invitations/accept
```

## Folders

```text
GET    /api/v1/projects/{projectId}/folders
POST   /api/v1/projects/{projectId}/folders
PATCH  /api/v1/projects/{projectId}/folders/{folderId}
DELETE /api/v1/projects/{projectId}/folders/{folderId}
```

## Categories

```text
GET    /api/v1/projects/{projectId}/categories
POST   /api/v1/projects/{projectId}/categories
PATCH  /api/v1/projects/{projectId}/categories/{categoryId}
DELETE /api/v1/projects/{projectId}/categories/{categoryId}
```

## Tags

```text
GET    /api/v1/projects/{projectId}/tags
POST   /api/v1/projects/{projectId}/tags
PATCH  /api/v1/projects/{projectId}/tags/{tagId}
DELETE /api/v1/projects/{projectId}/tags/{tagId}
```

## Documents

```text
POST   /api/v1/projects/{projectId}/documents
POST   /api/v1/projects/{projectId}/documents/batch

GET    /api/v1/projects/{projectId}/documents

GET    /api/v1/documents/{documentId}
PATCH  /api/v1/documents/{documentId}
DELETE /api/v1/documents/{documentId}

GET    /api/v1/documents/{documentId}/preview
GET    /api/v1/documents/{documentId}/download
```

---

# 41. Transaction Boundaries

Operations that should use Spring transaction boundaries where applicable:

```text
Verify Email OTP
    users.email_verified_at update
    + Redis OTP invalidation coordinated outside PostgreSQL transaction

Create Project
    Project
    + OWNER ProjectMember

Accept Invitation
    ProjectMember
    + invitation ACCEPTED

Remove Member
    membership deletion

Update Document metadata
    Document
    + DocumentTag relationships
```

PostgreSQL transaction cannot include MinIO atomically.

For upload/delete:

```text
Spring service
    ↓
coordinate DB transaction
+
MinIO operation
+
compensation/retry
```

---

# 42. MinIO Integration Boundary

REST API must not expose raw permanent public MinIO URLs.

Allowed approaches:

```text
Backend streams object
```

or:

```text
Backend authorizes user
↓
creates short-lived presigned URL
↓
returns/redirects securely
```

MinIO bucket remains private.

---

# 43. Email / OTP Integration Boundary

Core v1 email delivery provider:

```text
Gmail SMTP
smtp.gmail.com:587
STARTTLS
App Password from environment configuration
```

Registration verification flow:

```text
Create unverified User
Generate numeric OTP
Store OTP hash/attempt state in Redis with TTL
Send raw OTP through Gmail SMTP
```

Invitation flow:

```text
Create invitation data
Generate secure invitation token
Store token hash in PostgreSQL
Send invitation link through Gmail SMTP
```

Raw OTP appears only in verification email.
Raw invitation token appears only in invitation link sent to intended recipient.

Redis stores OTP verification state only. PostgreSQL stores invitation token hash and refresh-session token hash.

Sensitive OTP/invitation token must not be logged.

---

# 44. Logging Rules

Log:

```text
authentication failures
account-disabled access
project deletion
member removal
invitation send/resend failures
upload failures
document deletion failures
MinIO failures
unexpected exceptions
```

Never log:

```text
password
password hash
raw JWT
raw refresh token
raw invitation token
raw verification OTP
Redis OTP hash/state
Gmail SMTP credentials / App Password
MinIO secret keys
```

---

# 45. OpenAPI Documentation Requirements

Each endpoint should document:

```text
summary
description
authentication requirement
required role
path parameters
query parameters
request schema
response schema
validation
success status
possible business errors
```

Multipart endpoints must document:

```text
binary file part
JSON metadata part
size/type constraints
```

---

# 46. APIs Deliberately Not Included

Core v1 does not expose endpoints for:

```text
AI chatbot
RAG
embedding
vector search
document content indexing
video transcription
document version history
ownership transfer
public share links
document comments
favorites/bookmarks
audit history dashboard
approval workflow
storage quota management
OTP-based login
MFA / 2FA
forgot-password / password-reset OTP
```

These must first be introduced at specification level before API endpoints are added.

---

# 47. Baseline API Choices That Are Not New Domain Rules

The following are REST implementation choices introduced in this specification:

```text
/api/v1 version prefix

page starts from 0

default page size = 20
max page size = 100

register does not auto-login
registration requires email verification OTP before login
OTP state is Redis-backed with TTL
Gmail SMTP delivers verification/invitation email

batch upload uses common folder/category/tags metadata

PATCH is used for partial resource update

invitation email opens frontend page,
frontend POSTs token to backend accept endpoint

storageKey remains internal to backend
```

These choices may be adjusted later without changing the core business domain, provided API clients are updated consistently.

---

# 48. Recommended Spring Controller Mapping

Potential controller boundaries:

```text
AuthController

UserController
AdminUserController

ProjectController
AdminProjectController

ProjectMemberController
ProjectInvitationController
InvitationController

FolderController
CategoryController
TagController

DocumentController
```

These are module boundaries, not a requirement that each class must match exactly one table.

---

# 49. Recommended Service Boundaries

```text
AuthService
EmailVerificationService
OtpService / OtpStore
RefreshSessionService

UserService

ProjectService
ProjectAuthorizationService

ProjectMemberService
InvitationService
MailService (Gmail SMTP implementation)

FolderService
CategoryService
TagService

DocumentService
DocumentAuthorizationService
FileValidationService
StorageService

DocumentSearchService
```

Avoid placing all authorization directly in controllers.

---

# 50. Core v1 REST Definition of Done

REST API phase can be considered complete when:

```text
Authentication contract is fixed.

Email verification OTP contract is fixed.

Redis OTP storage/TTL contract is fixed.

Gmail SMTP email delivery boundary is fixed.

Role/permission checks are defined for every protected endpoint.

User APIs are defined.

Admin user APIs are defined.

Project CRUD is defined.

Project membership lifecycle is defined.

Email invitation lifecycle is defined.

Folder APIs are defined.

Category APIs are defined.

Tag APIs are defined.

Document upload is defined.

Batch upload is defined.

Document browse/search is defined.

Metadata update is defined.

Preview/download are defined.

Hard delete is defined.

Pagination/filter/sort contract is defined.

Error model and business codes are defined.

OpenAPI security scheme is defined.

MinIO exposure rules are defined.

No AI-related endpoint is mixed into Core v1.
```

---

# 51. Next Phase

After REST API Specification is accepted, recommended next phase:

```text
REST API Specification
        ↓
Spring Boot Application Architecture
        ↓
Package / Module Structure
        ↓
JPA Entity Mapping
        ↓
Repository Design
        ↓
Spring Security + JWT Design
        ↓
Service Layer Design
        ↓
MinIO Integration Design
        ↓
Exception Handling
        ↓
OpenAPI Configuration
        ↓
Implementation Plan
```

The next design artifact should therefore focus on the **Spring Boot Application Architecture and Module/Package Structure**, using this API contract and the existing physical database schema as the two main inputs.
