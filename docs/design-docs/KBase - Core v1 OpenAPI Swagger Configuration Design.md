# KBase – Core v1
## OpenAPI / Swagger Configuration Design

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**API Style:** REST  
**Documentation:** OpenAPI 3 + Swagger UI through springdoc-openapi  
**Security:** Bearer JWT access token + HttpOnly refresh cookie  
**Architecture:** Feature-first Modular Monolith  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa OpenAPI / Swagger documentation architecture cho KBase Core v1.

Tài liệu kế thừa trực tiếp từ:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design
- REST API Specification
- Spring Boot Application Architecture + Module/Package Structure
- JPA Entity Mapping + Repository Design
- Spring Security + JWT Design
- Service Layer Detailed Design
- MinIO Integration Design
- Exception Handling Design

Mục tiêu:

- Chuẩn hóa OpenAPI metadata.
- Chuẩn hóa Bearer JWT security scheme.
- Chuẩn hóa Swagger UI.
- Chuẩn hóa tags/controller grouping.
- Chuẩn hóa request/response schema documentation.
- Chuẩn hóa multipart upload documentation.
- Chuẩn hóa pagination/filter/sort documentation.
- Chuẩn hóa error response documentation.
- Chuẩn hóa public/protected endpoint documentation.
- Xác định environment exposure rules.
- Đảm bảo tài liệu API phản ánh đúng contract đã chốt.

---

# 2. Documentation Principles

Core v1 OpenAPI tuân theo:

```text
1. REST implementation and OpenAPI contract must stay aligned.

2. Do not document behavior that service layer does not implement.

3. Do not expose JPA Entity as API schema.

4. Request/Response DTOs are the API contract.

5. Security requirements must be visible per endpoint.

6. Multipart endpoints must clearly document file + metadata parts.

7. Error responses use shared ApiErrorResponse.

8. Pagination/filter/sort parameters must be explicit.

9. Swagger UI is a developer tool, not an authorization boundary.

10. Production exposure of Swagger UI is configurable.
```

---

# 3. springdoc Integration Strategy

KBase uses:

```text
springdoc-openapi
```

for:

```text
OpenAPI JSON/YAML generation
Swagger UI
annotation-driven endpoint documentation
schema generation from DTOs
```

Exact springdoc dependency version is not fixed in this design.

Reason:

```text
Spring Boot version has not yet been finalized.
```

Implementation must choose the springdoc major/version compatible with the selected Spring Boot generation.

---

# 4. Dependency Baseline

For current Spring MVC generations, the expected starter family is:

```text
springdoc-openapi-starter-webmvc-ui
```

Exact version must be selected only after Spring Boot version is locked.

Do not hard-code a stale springdoc version into this architecture document.

---

# 5. Generated Documentation Endpoints

Baseline:

```text
OpenAPI JSON:
GET /v3/api-docs

OpenAPI YAML:
GET /v3/api-docs.yaml

Swagger UI:
GET /swagger-ui.html
```

Paths may be configured but should remain conventional unless deployment requires otherwise.

---

# 6. API Base Path

Application REST API remains:

```text
/api/v1
```

OpenAPI should document only Core REST paths under this version.

Examples:

```text
/api/v1/auth/**
/api/v1/users/**
/api/v1/admin/**
/api/v1/projects/**
/api/v1/documents/**
/api/v1/invitations/**
```

---

# 7. OpenApiConfig

Recommended:

```text
config.OpenApiConfig
```

Responsibilities:

```text
OpenAPI metadata
security scheme
global server definitions if needed
global documentation customizers
```

Do not put business logic here.

---

# 8. OpenAPI Metadata

Suggested:

```text
Title:
KBase Core API

Version:
v1

Description:
REST API for KBase Core knowledge-base management,
including authentication, projects, membership,
invitations, folders, categories, tags, and documents.
```

Do not advertise:

```text
AI Chatbot
RAG
Embedding
Vector Search
```

because they are not part of Core v1.

---

# 9. Contact / License Metadata

Optional.

Do not invent:

```text
company contact
public email
license
terms-of-service URL
```

unless trainer/project requirements provide them.

Leave absent until real values exist.

---

# 10. OpenAPI Server Definitions

Development may document:

```text
http://localhost:8080
```

Production URL should not be hard-coded into source if deployment host can vary.

Options:

```text
let OpenAPI derive current server
```

or use configuration-driven `Server`.

Baseline:

```text
avoid environment-specific hard-coded server URLs
```

---

# 11. Security Scheme

Define Bearer access token:

```text
type: HTTP
scheme: bearer
bearerFormat: JWT
```

Recommended name:

```text
bearerAuth
```

Conceptual Java:

```java
new SecurityScheme()
    .name("bearerAuth")
    .type(SecurityScheme.Type.HTTP)
    .scheme("bearer")
    .bearerFormat("JWT");
```

---

# 12. What bearerAuth Represents

Swagger Authorization input represents:

```text
Access Token
```

It does not represent:

```text
Refresh Token
Invitation Token
MinIO credential
SMTP credential
```

---

# 13. Global vs Per-Operation Security

Recommended:

```text
Do NOT blindly apply bearerAuth globally to every operation.
```

Reason:

Public endpoints exist:

```text
register
login
refresh
```

Prefer:

```text
explicit or grouped protected security requirements
```

so public/private status is obvious.

---

# 14. Public Authentication Endpoints

Document as public:

```text
POST /api/v1/auth/register
POST /api/v1/auth/verify-email
POST /api/v1/auth/resend-verification-otp
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

No Bearer security requirement.

Refresh relies on:

```text
HttpOnly cookie
```

not bearer token.

---

# 15. Logout Documentation

Logout uses refresh-session/cookie behavior.

Document:

```text
POST /api/v1/auth/logout
```

Cookie behavior must be described.

Do not incorrectly state that JWT blacklist is used.

---

# 16. Protected Endpoints

Protected endpoint docs should show:

```text
SecurityRequirement(name = "bearerAuth")
```

Examples:

```text
GET /api/v1/users/me
POST /api/v1/projects
GET /api/v1/projects/{projectId}
GET /api/v1/documents/{documentId}
```

---

# 17. ADMIN Endpoints

Tag/documentation should state:

```text
SystemRole.ADMIN required
```

Examples:

```text
/api/v1/admin/users
/api/v1/admin/projects
```

Swagger security scheme alone does not express the full role rule.

Endpoint description must document it.

---

# 18. Project-Scoped Authorization Documentation

For project endpoints, document authorization clearly.

Example:

```text
Access:
MEMBER, OWNER, or ADMIN
```

Management endpoint:

```text
Access:
OWNER or ADMIN
```

Do not imply that possession of Bearer token alone grants access.

---

# 19. Document Authorization Documentation

Document mutation endpoint should explicitly say:

```text
MEMBER:
may modify/delete only own uploaded document

OWNER:
may modify/delete any document in project

ADMIN:
administrative override
```

This permission rule is important enough to appear in API docs.

---

# 20. Controller Tags

Recommended tags:

```text
Authentication
Users
Admin - Users
Projects
Admin - Projects
Project Members
Project Invitations
Invitations
Folders
Categories
Tags
Documents
```

This mirrors REST modules.

---

# 21. Tag Annotation

Example:

```java
@Tag(
    name = "Projects",
    description = "Project management APIs"
)
```

Avoid one giant:

```text
KBase API
```

tag for every endpoint.

---

# 22. Controller-Level vs Method-Level Docs

Controller:

```text
@Tag
```

Method:

```text
@Operation
@ApiResponses
@Parameter
@RequestBody
```

Use method-level details only where meaningful.

Do not overload annotations with duplicated obvious text.

---

# 23. Operation Summary Style

Good:

```text
Create project
List project members
Upload document
Accept project invitation
Delete category
```

Avoid:

```text
This endpoint is used to allow a user to...
```

Keep summaries concise.

Use description for business rules.

---

# 24. Operation Description Style

Description should include only relevant rules.

Example `DELETE document`:

```text
Hard-deletes the document and its binary object.
MEMBER may delete only documents they uploaded.
OWNER and ADMIN may delete any document in the project.
```

This is useful.

Avoid copying full internal service flow into Swagger.

---

# 25. DTO Documentation

API schemas come from:

```text
Request DTO
Response DTO
```

Never from:

```text
JPA Entity
```

Examples:

```text
RegisterRequest
LoginRequest
CreateProjectRequest
ProjectResponse
DocumentResponse
ApiErrorResponse
```

---

# 26. Schema Annotation

Use:

```text
@Schema
```

where field meaning is not obvious.

Example:

```java
@Schema(
    description = "Project role of the current user",
    allowableValues = {"OWNER", "MEMBER"}
)
```

Do not manually document things Jakarta Bean Validation already exposes unless additional explanation helps.

---

# 27. Sensitive DTO Fields

Never include examples containing real:

```text
password
JWT
refresh token
invitation token
MinIO credentials
Gmail SMTP App Password
```

Swagger examples must use placeholders.

Example:

```text
<access-token>
<verification-otp>
```

Never use a real OTP or Gmail credential in documentation examples.

---

# 28. RegisterRequest Documentation

Document:

```text
email
password
displayName
```

Rules:

```text
email required, valid, normalized server-side
password 8..64 characters
displayName required, max 100

successful registration creates an unverified account
and sends a verification OTP through Gmail SMTP
```

Do not expose:

```text
systemRole
status
```

as writable registration fields.

---

# 28.1 VerifyEmailRequest Documentation

Endpoint:

```text
POST /api/v1/auth/verify-email
```

Document request fields:

```text
email
otp
```

Rules:

```text
public endpoint
OTP is used only for registration email verification
OTP baseline = 6 numeric digits
OTP TTL baseline = 5 minutes
maximum 5 attempts per OTP
raw OTP is never returned by the API
```

Success marks the account email as verified.

Important errors:

```text
400 INVALID_OTP
400 OTP_EXPIRED
409 EMAIL_ALREADY_VERIFIED
429 OTP_ATTEMPTS_EXCEEDED
503 OTP_SERVICE_UNAVAILABLE
```

---

# 28.2 ResendVerificationOtpRequest Documentation

Endpoint:

```text
POST /api/v1/auth/resend-verification-otp
```

Document request field:

```text
email
```

Rules:

```text
public endpoint
account must exist and remain unverified
resend cooldown baseline = 60 seconds
new OTP replaces previous pending OTP state
OTP is stored only as protected short-lived Redis state
Gmail SMTP sends the raw OTP
```

Important errors:

```text
409 EMAIL_ALREADY_VERIFIED
429 OTP_RESEND_COOLDOWN
503 OTP_SERVICE_UNAVAILABLE
503 EMAIL_SERVICE_UNAVAILABLE
```

---

# 29. LoginRequest Documentation

Example:

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123"
}
```

Response:

```text
accessToken
tokenType
expiresIn
user
```

Login requires the account email to be verified.

Important error:

```text
403 EMAIL_NOT_VERIFIED
```

Refresh cookie should be documented in description/header behavior.

---

# 30. Refresh Endpoint Documentation

Document:

```text
Requires refresh token cookie.
Does not require Bearer access token.
```

Success returns:

```text
new access token
```

Do not document refresh-token rotation as mandatory because Core v1 has not fixed that behavior.

---

# 31. Cookie Documentation

OpenAPI can describe cookie parameters for request-side refresh behavior.

Conceptually:

```text
cookie:
kbase_refresh_token
```

However because the cookie is HttpOnly and typically browser-managed, Swagger UI behavior may depend on same-origin/environment configuration.

Document this limitation explicitly.

---

# 32. Pagination Schema

Standard list response:

```text
PageResponse<T>
```

Fields:

```text
content
page
size
totalElements
totalPages
first
last
```

Keep naming aligned with REST Specification.

---

# 33. Pagination Parameters

Document standard parameters:

```text
page
size
sort
```

Defaults:

```text
page = 0
size = 20
max size = 100
```

---

# 34. Sort Parameter

Document format:

```text
sort=field,direction
```

Examples:

```text
sort=createdAt,desc
sort=displayName,asc
```

Allowed document fields:

```text
displayName
createdAt
updatedAt
sizeBytes
```

Do not advertise arbitrary entity properties.

---

# 35. Filter Documentation

Document list/search parameters explicitly.

Document search:

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

---

# 36. Date Filter Format

Document:

```text
ISO-8601 timestamp
```

Example:

```text
2026-09-17T00:00:00Z
```

Avoid ambiguous date/time examples.

---

# 37. Search q Documentation

State clearly:

```text
Core v1 metadata search only.
```

Searches:

```text
displayName
originalFilename
description
category name
tag name
```

Does NOT search:

```text
PDF content
Word content
video transcript
semantic embeddings
AI/RAG
```

---

# 38. Project List Documentation

`GET /api/v1/projects`

Document:

```text
normal USER:
returns only joined projects

ADMIN:
this endpoint still behaves as "my projects"

all-project admin list:
GET /api/v1/admin/projects
```

Avoid ambiguity.

---

# 39. ProjectResponse currentUserRole

For normal member:

```text
OWNER
MEMBER
```

For ADMIN without membership:

```text
may be null
```

This must be reflected in schema if implementation follows that baseline.

Do not invent a fake `ADMIN` ProjectRole.

---

# 40. Member Removal Documentation

`DELETE /projects/{projectId}/members/{userId}`

Description must note:

```text
Removing a MEMBER does not delete their documents.
```

Also:

```text
Project OWNER cannot be removed through this endpoint.
```

---

# 41. Leave Project Documentation

`DELETE /projects/{projectId}/members/me`

Rules:

```text
MEMBER may leave
OWNER cannot leave in Core v1
documents remain in project
```

---

# 42. Invitation Create Documentation

Document:

```text
OWNER/ADMIN only

existing member → 409
existing pending invitation → 409

expiration baseline:
72h configurable
```

Do not expose raw invitation token in response schema.

---

# 43. Invitation Accept Documentation

Document:

```text
Authenticated ACTIVE user required.

Authenticated user's normalized email
must equal invitation email.

Invitation must be:
PENDING
not expired
```

---

# 44. Folder API Documentation

Folder create/update docs should state:

```text
OWNER/ADMIN only

parent must be same project

sibling name unique case-insensitively

folder cannot be moved into itself/descendant
```

Delete:

```text
only empty folder
```

---

# 45. Category API Documentation

Create/rename:

```text
OWNER/ADMIN only
name unique case-insensitively per project
```

Delete:

```text
cannot delete while any document uses category
```

---

# 46. Tag API Documentation

Document:

```text
MEMBER/OWNER/ADMIN may create tag

OWNER/ADMIN may rename/delete tag

deleting tag does not delete documents
```

---

# 47. Single Document Upload

Endpoint:

```text
POST /api/v1/projects/{projectId}/documents
```

Consumes:

```text
multipart/form-data
```

Parts:

```text
file
metadata
```

---

# 48. Multipart File Part

Document:

```text
name: file
required: true
type: string
format: binary
```

This is the standard OpenAPI binary representation.

---

# 49. Multipart Metadata Part

Document metadata JSON:

```json
{
  "displayName": "Backend Authentication Specification.pdf",
  "description": "JWT authentication specification",
  "folderId": "uuid-or-null",
  "categoryId": "uuid-or-null",
  "tagIds": ["uuid"]
}
```

`metadata` is optional.

---

# 50. Multipart Controller Signature Baseline

Conceptual Spring MVC:

```java
@PostMapping(
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
)
public DocumentResponse upload(
    @PathVariable UUID projectId,
    @RequestPart("file") MultipartFile file,
    @RequestPart(
        value = "metadata",
        required = false
    ) DocumentMetadataRequest metadata
)
```

Exact annotation details should match final implementation.

---

# 51. Upload Documentation Rules

Document supported extensions:

```text
PDF DOC DOCX XLS XLSX PPT PPTX MD TXT
JPG JPEG PNG GIF SVG BMP
MP4 MOV AVI
```

Document configurable baseline limits:

```text
Document/Office: 50 MB
Image: 20 MB
Video: 500 MB
```

Make clear values are configuration-driven.

---

# 52. Upload Error Documentation

Important responses:

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
500 FILE_UPLOAD_FAILED
503 STORAGE_SERVICE_UNAVAILABLE
```

---

# 53. Batch Upload Documentation

Endpoint:

```text
POST /api/v1/projects/{projectId}/documents/batch
```

Document:

```text
common optional folder/category/tag metadata
for all files

baseline maximum:
10 files, configurable
```

Do not claim per-file metadata support because current REST baseline does not include it.

---

# 54. Preview Documentation

Endpoint:

```text
GET /api/v1/documents/{documentId}/preview
```

Possible responses:

```text
200 full inline resource
206 partial content for supported byte-range delivery
415 PREVIEW_NOT_SUPPORTED
```

Preview priority:

```text
PDF
images
TXT
MD
MP4
```

Office browser rendering is not required.

---

# 55. Binary Response Schema

For preview/download:

```text
schema:
type: string
format: binary
```

Do not document `DocumentResponse` as the body for binary endpoints.

---

# 56. Download Documentation

Endpoint:

```text
GET /api/v1/documents/{documentId}/download
```

Document:

```text
binary attachment
authorization required
bucket remains private
```

Do not expose `storageKey`.

---

# 57. Range Request Documentation

For video preview, document optional header:

```text
Range
```

Example:

```text
bytes=0-1048575
```

Possible:

```text
206 Partial Content
416 Range Not Satisfiable
```

Single-range support is sufficient for Core v1 baseline.

---

# 58. Content-Disposition

Preview:

```text
inline
```

Download:

```text
attachment
```

Filename is based on:

```text
Document.displayName
```

not object storage key.

---

# 59. ApiErrorResponse Schema

Shared schema:

```json
{
  "timestamp": "2026-09-17T02:00:00Z",
  "status": 403,
  "code": "PROJECT_ACCESS_FORBIDDEN",
  "message": "You do not have access to this project.",
  "path": "/api/v1/projects/...",
  "requestId": "01J...",
  "errors": null
}
```

---

# 60. Validation Error Schema

Same base schema plus:

```json
"errors": {
  "email": "Invalid email address"
}
```

Do not create a totally separate unrelated error response type.

---

# 61. Shared Response Documentation

Avoid rewriting full error schema in every controller.

Use shared:

```text
ApiErrorResponse
```

and reusable annotation/constants/customizers where practical.

---

# 62. @ApiResponses Strategy

Document important operation-specific statuses.

Example:

```text
200 successful
403 project forbidden
404 document not found
```

Do not copy every generic possible server error onto every method if it causes annotation noise.

---

# 63. Global Error Responses

Common errors such as:

```text
401 AUTHENTICATION_REQUIRED
500 INTERNAL_SERVER_ERROR
```

can be documented through common reusable components/customizers if maintainable.

---

# 64. Example Response Values

Use fake deterministic examples.

Good:

```text
user@example.com
11111111-1111-1111-1111-111111111111
```

Avoid real user/project data.

---

# 65. UUID Schema

UUID IDs should document:

```text
type: string
format: uuid
```

Springdoc normally infers this from `UUID`.

No need to manually annotate every UUID unless description helps.

---

# 66. Enum Schemas

Enums:

```text
SystemRole
UserStatus
ProjectRole
InvitationStatus
FileKind
```

Swagger should expose allowable values automatically from Java enum.

Do not represent enum as arbitrary free-form string.

---

# 67. Password Fields

Use schema hint:

```text
format: password
writeOnly: true
```

where appropriate.

Password must never appear in response DTO.

---

# 68. Access Token Field

Login response may include:

```text
accessToken
```

Document as:

```text
opaque/JWT credential for Authorization Bearer header
```

Do not put a real token into static example.

---

# 69. Refresh Token

Raw refresh token does not belong in JSON schema.

It is carried in:

```text
HttpOnly cookie
```

Do not create:

```text
refreshToken
```

field in LoginResponse.

---

# 70. Invitation Token

Invitation raw token appears in:

```text
AcceptInvitationRequest
```

because frontend receives it from invite link and POSTs it.

Use placeholder example.

Never expose token hash.

---

# 71. StorageKey

Do not include:

```text
storageKey
```

in `DocumentResponse`.

It remains backend internal.

Swagger should therefore not expose it.

---

# 72. Internal DTOs

Do not document internal classes such as:

```text
LoginResult
StoredObject
StoredResource
ProjectAccess
ValidatedFile
DocumentSearchCriteria
```

unless they are actual REST request/response types.

---

# 73. OpenAPI Naming

Avoid schema names like:

```text
UserDto1
ProjectDto2
```

Use clear names:

```text
UserResponse
CreateProjectRequest
ProjectMemberResponse
DocumentResponse
ApiErrorResponse
```

---

# 74. Generic PageResponse Naming

Generic schema generation can produce unclear names.

If necessary define explicit API page responses:

```text
ProjectPageResponse
UserPageResponse
DocumentPageResponse
ProjectMemberPageResponse
InvitationPageResponse
```

or use OpenAPI customization to preserve readable generic naming.

Implementation can choose whichever remains maintainable.

---

# 75. Endpoint Ordering

Swagger UI grouping order can follow:

```text
Authentication
Users
Admin
Projects
Membership
Invitations
Organization
Documents
```

Exact UI tag ordering is cosmetic, not API behavior.

---

# 76. Swagger UI Configuration

Baseline properties:

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
```

Exact property names/options must match selected springdoc version.

---

# 77. Try-It-Out

Swagger UI `Try it out` is useful in:

```text
local
development
test/staging where appropriate
```

Production exposure should be deliberate.

Swagger UI is not a substitute for authorization.

---

# 78. Swagger UI and JWT

Developer flow:

```text
POST login
    ↓
copy accessToken
    ↓
Swagger Authorize
    ↓
Bearer access token
    ↓
test protected endpoints
```

---

# 79. Swagger UI and Refresh Cookie

Because refresh token is HttpOnly:

```text
Swagger JS cannot manually read it
```

If UI/API are same-origin and browser accepts cookie:

```text
refresh/logout may work naturally
```

Otherwise cookie behavior may require environment-specific setup.

Document this as tooling behavior, not API contract change.

---

# 80. CORS Consideration

Swagger UI origin must obey existing CORS configuration.

Do not open wildcard CORS just to make Swagger easier.

Security rules remain authoritative.

---

# 81. Environment Exposure

Recommended:

Local:

```text
Swagger UI enabled
OpenAPI docs enabled
```

Development/Staging:

```text
enabled as needed
```

Production:

```text
OpenAPI exposure configurable
Swagger UI disabled by default or access-controlled
```

This is an operational baseline recommendation.

---

# 82. Why Production UI May Be Disabled

Swagger UI exposes:

```text
endpoint inventory
request schemas
business operations
```

It is not inherently a security vulnerability if APIs are secured, but there is usually no need to expose interactive developer UI publicly in production.

The API authorization itself must remain secure regardless.

---

# 83. Production API Docs

Possible choices:

```text
disable Swagger UI only
keep JSON spec internal
```

or:

```text
disable both UI and api-docs publicly
```

Exact production policy is deployment-specific.

---

# 84. springdoc Enable/Disable Configuration

Use environment properties.

Conceptual:

```yaml
springdoc:
  api-docs:
    enabled: ${KBASE_OPENAPI_ENABLED:true}
  swagger-ui:
    enabled: ${KBASE_SWAGGER_UI_ENABLED:true}
```

Production environment can override.

---

# 85. API Grouping

Core v1 is small enough for one OpenAPI group:

```text
KBase Core v1
```

No need for multiple `GroupedOpenApi` beans initially.

Potential future groups:

```text
Core
Admin
AI
```

only if API size warrants it.

---

# 86. Why Not Split Groups Yet

Multiple OpenAPI groups add:

```text
more configuration
multiple spec URLs
documentation navigation complexity
```

Current Core v1 remains manageable as one contract.

---

# 87. API Version Documentation

OpenAPI metadata:

```text
v1
```

matches path:

```text
/api/v1
```

If later `/api/v2` is introduced:

```text
maintain separate compatibility/version strategy
```

Do not silently change v1 semantics.

---

# 88. Deprecation

Core v1 currently has no deprecated endpoints.

If later replacing an endpoint:

```text
mark old operation deprecated
document replacement
preserve compatibility for defined period
```

---

# 89. Required Request Body

Explicitly document required request bodies.

Example:

```text
CreateProjectRequest required
```

Optional multipart metadata remains optional.

---

# 90. Nullable Fields

Reflect actual API semantics.

Examples:

```text
Project.description nullable

Document.description nullable

Document.folderId nullable

Document.category nullable

Folder.parentId nullable

ProjectResponse.currentUserRole may be nullable for non-member ADMIN

UserResponse.emailVerified is a read-only derived boolean
```

Do not mark fields required merely because Java property exists.

---

# 91. ReadOnly / WriteOnly

Use where helpful.

Examples:

```text
createdAt → readOnly
updatedAt → readOnly

password → writeOnly
accessToken → response only
```

---

# 92. File Size Documentation

OpenAPI cannot dynamically enforce every configurable file-size business rule in schema alone.

Document limits in endpoint description and config docs.

Actual enforcement stays in:

```text
FileValidationService
Spring multipart settings
```

---

# 93. Multipart Server Limits

Swagger docs should not claim upload works up to 500 MB if server multipart config is lower.

Implementation phase must align:

```text
Spring multipart max request/file size
```

with KBase configurable business limits.

---

# 94. ErrorCode Documentation

OpenAPI should expose:

```text
code: string
```

with examples.

Do not put every possible ErrorCode into one enormous enum schema if that makes clients brittle.

Endpoint docs should list relevant codes.

---

# 95. Request ID Documentation

Error responses include:

```text
requestId
```

All responses should include header:

```text
X-Request-Id
```

OpenAPI may document the header globally or for relevant responses.

---

# 96. X-Request-Id Request Header

Client may optionally provide:

```text
X-Request-Id
```

subject to validation.

Server may generate one when absent.

Document as optional technical header, not business input.

---

# 97. Standard Success Response Policy

Do not wrap all success responses in artificial envelope unless already designed.

Current API uses:

```text
direct DTO
PageResponse
204 no body
binary response
```

OpenAPI must reflect this existing contract.

---

# 98. No Generic "data" Envelope

Do not introduce:

```json
{
  "success": true,
  "data": {}
}
```

only for documentation consistency.

That would change the REST contract and is not part of Core v1.

---

# 99. 204 Documentation

Endpoints returning no content:

```text
logout
delete user
delete project
remove member
leave project
cancel invitation
delete folder/category/tag/document
change password
```

Document:

```text
204
no response body
```

Do not attach a JSON schema to 204.

---

# 100. 201 Documentation

Creation endpoints:

```text
register
create project
create invitation
create folder
create category
create tag
upload document
batch upload
```

should document:

```text
201 Created
```

where already defined in REST Specification.

---

# 101. Location Header

Core v1 REST Specification did not require `Location` response header for `201`.

Do not silently add it as a required behavior.

It may be introduced later as an HTTP refinement.

---

# 102. Admin Documentation

Admin operations should be visually clear in Swagger.

Use:

```text
Admin - Users
Admin - Projects
```

tags.

Descriptions should say:

```text
Requires SystemRole.ADMIN.
```

---

# 103. Security Error Responses

Protected endpoints should document likely:

```text
401 AUTHENTICATION_REQUIRED
403 ACCESS_DENIED or domain-specific forbidden code
```

Avoid misleading users into thinking every 403 uses only generic `ACCESS_DENIED`.

---

# 104. Business Error Responses

Examples:

Folder delete:

```text
404 FOLDER_NOT_FOUND
409 FOLDER_NOT_EMPTY
```

Category delete:

```text
404 CATEGORY_NOT_FOUND
409 CATEGORY_IN_USE
```

Document update:

```text
404 DOCUMENT_NOT_FOUND
403 DOCUMENT_MODIFICATION_FORBIDDEN
404 FOLDER_NOT_FOUND
404 CATEGORY_NOT_FOUND
404 TAG_NOT_FOUND
```

---

# 105. Infrastructure Responses

Document only where meaningful.

File operations:

```text
503 STORAGE_SERVICE_UNAVAILABLE
```

Invitation send:

```text
503 EMAIL_SERVICE_UNAVAILABLE
```

Do not add storage error to unrelated profile endpoint.

---

# 106. Internal Server Error

Generic:

```text
500 INTERNAL_SERVER_ERROR
```

can be considered a cross-cutting fallback.

No stack trace in schema/example.

---

# 107. OpenAPI Customizer

If annotation repetition becomes large, use:

```text
OpenApiCustomizer
OperationCustomizer
```

carefully for shared behavior.

Examples:

```text
common request ID header
common security response
```

Do not use customizer to hide endpoint-specific business rules.

---

# 108. Annotation Reuse

Java annotations cannot always be composed cleanly for every case.

Prefer maintainability over clever meta-annotation frameworks.

Core v1 can tolerate some explicit `@ApiResponse` declarations.

---

# 109. Documentation and Source Code Drift

Primary prevention:

```text
generate OpenAPI from implementation DTO/controllers
```

and write contract tests.

Avoid maintaining an entirely separate handwritten OpenAPI YAML as another source of truth unless project later chooses contract-first development.

---

# 110. Contract Test Baseline

Integration test should verify:

```text
/v3/api-docs returns 200

spec contains /api/v1 paths

spec contains /api/v1/auth/verify-email

spec contains /api/v1/auth/resend-verification-otp

bearerAuth exists

public register/verify-email/resend-verification-otp/login/refresh are not marked Bearer-required

protected project/document operations are documented

ApiErrorResponse schema exists

multipart upload schema exists
```

---

# 111. Snapshot / Breaking Change Test

Future-friendly option:

```text
export OpenAPI JSON in CI
compare intentional contract changes
```

Not mandatory for initial Core v1 but useful before release.

---

# 112. DTO Schema Tests

Check that API spec does NOT expose:

```text
passwordHash
tokenHash
raw OTP
Redis OTP hash/protected value
storageKey
MinIO credential
refresh session internals
JPA relationship graphs
```

---

# 113. Security Documentation Tests

Check:

```text
/admin/** operations require bearer auth
```

and descriptions state:

```text
ADMIN
```

Project-specific role rules remain operation descriptions, not JWT scopes.

Also verify public authentication documentation:

```text
register
verify-email
resend-verification-otp
login
refresh
```

does not require `bearerAuth`.

---

# 114. Multipart Documentation Test

Verify Swagger UI displays:

```text
file chooser
metadata JSON part
```

for upload.

If generated UI is confusing, use explicit OpenAPI annotation schema for multipart request rather than changing runtime API.

---

# 115. Binary Documentation Test

Verify preview/download are shown as:

```text
binary
```

not `DocumentResponse`.

---

# 116. Range Header Test

Preview operation docs should show:

```text
Range
```

as optional header and:

```text
206
416
```

where applicable.

---

# 117. Documentation Package Placement

Recommended:

```text
config/OpenApiConfig.java
```

DTO annotations remain with DTO classes.

Controller operation annotations remain with controllers.

Avoid creating a giant separate class that mirrors all controllers only for Swagger unless necessary.

---

# 118. OpenAPIConfig Responsibilities

Should contain:

```text
Info
SecurityScheme
optional Servers
optional global customizers
```

Should not contain:

```text
project permissions
repository calls
database queries
```

---

# 119. Swagger UI Properties

Potential baseline:

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
    display-request-duration: true
```

These are UX settings, not API rules.

Exact available properties depend on selected springdoc version.

---

# 120. API Docs Path Configuration

Default:

```text
/v3/api-docs
```

Keeping default is recommended unless reverse proxy/security infrastructure requires custom path.

Changing path provides no meaningful API security by itself.

---

# 121. Reverse Proxy Considerations

If deployed behind:

```text
Nginx
Ingress
Load Balancer
```

OpenAPI server/base URL rendering may require forwarded headers configuration.

Do not hard-code internal Docker hostnames such as:

```text
backend:8080
```

into public docs.

---

# 122. HTTPS

Production Swagger/OpenAPI links should resolve through HTTPS when production API uses HTTPS.

No mixed-content setup.

---

# 123. Docker Local Access

Example local:

```text
Backend:
http://localhost:8080

Swagger:
http://localhost:8080/swagger-ui.html

OpenAPI:
http://localhost:8080/v3/api-docs
```

Exact external port remains Docker Compose configuration.

---

# 124. AI Documentation Boundary

Do not create:

```text
/chat
/ask
/embedding
/search/semantic
/rag
```

operations in Core v1 Swagger.

Future AI API should be documented only when AI phase is specified.

---

# 125. Deliberately Not Added

Core v1 OpenAPI design does not add:

```text
OAuth2 Swagger login
Google OAuth
OTP-based login
MFA / 2FA
password-reset OTP
API-key security scheme
refresh-token bearer scheme
multiple API groups
external public developer portal
code-generation pipeline
contract-first YAML source
AI endpoints
public share-link endpoints
GraphQL docs
```

These require separate requirements.

---

# 126. Recommended Annotation Example – Project

Conceptual:

```java
@Operation(
    summary = "Update project",
    description = "Updates project name or description. Requires project OWNER or system ADMIN."
)
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Project updated"),
    @ApiResponse(responseCode = "403", description = "Project management forbidden"),
    @ApiResponse(responseCode = "404", description = "Project not found")
})
```

Actual implementation can reference shared response schemas.

---

# 127. Recommended Annotation Example – Upload

Conceptual:

```java
@Operation(
    summary = "Upload document",
    description = "Uploads a supported file into a project. Requires current project membership."
)
@SecurityRequirement(name = "bearerAuth")
```

Runtime signature remains multipart:

```text
file
metadata
```

Do not fake upload as Base64 JSON.

---

# 128. Recommended Annotation Example – Binary

Conceptual:

```text
GET /documents/{documentId}/download

200:
Content-Type application/octet-stream or actual MIME
schema string/binary
```

Do not expose MinIO response models.

---

# 129. Documentation Quality Rules

Every endpoint should make these obvious:

```text
What does it do?
Who can call it?
What inputs are required?
What success response is returned?
What important business conflicts can occur?
```

Avoid documenting internal implementation details that do not affect API consumers.

---

# 130. Definition of Done

OpenAPI / Swagger Configuration is complete when:

```text
OpenAPI metadata exists

bearerAuth JWT scheme exists

public endpoints are correctly public

registration email-verification OTP endpoints are documented

OTP verification/resend errors are documented

protected endpoints show Bearer requirement

ADMIN requirements are documented

project/document role rules are documented

all request/response DTOs render correctly

JPA entities are not exposed

multipart file upload is correctly represented

binary preview/download is correctly represented

pagination/filter/sort parameters are documented

ApiErrorResponse is shared

field validation errors are documented

request ID is documented

Swagger can authenticate with access token

production exposure can be disabled/configured

no AI endpoints appear in Core v1 documentation
```

---

# 131. Implementation Checklist

```text
Add compatible springdoc starter.

Create OpenApiConfig.

Define bearerAuth scheme.

Annotate controller tags.

Annotate important operations.

Annotate multipart upload.

Annotate binary responses.

Document pagination/filter/sort.

Document role requirements.

Document shared ApiErrorResponse.

Document verify-email and resend-verification-otp endpoints.

Document OTP-specific 400/409/429/503 responses.

Add environment configuration.

Allow swagger/api-docs paths in SecurityConfig only when enabled.

Verify Swagger UI manually.

Add /v3/api-docs integration test.

Verify sensitive internal fields are absent.
```

---

# 132. SecurityConfig Integration

When documentation is enabled, SecurityConfig may permit:

```text
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

This only exposes documentation assets.

It does NOT weaken:

```text
/api/v1/**
```

endpoint authorization.

If docs are disabled in production, corresponding routes should not be exposed.

---

# 133. Recommended Baseline Decisions

Core v1 OpenAPI baseline:

```text
OpenAPI 3

springdoc-openapi

one KBase Core v1 API group

/api/v1 documented

bearerAuth for JWT access token

refresh token documented as HttpOnly cookie

registration email verification documented as Redis-backed OTP + Gmail SMTP flow

DTO-based schemas

feature-based controller tags

shared ApiErrorResponse

explicit multipart upload docs

binary preview/download schemas

pagination/filter/sort docs

Swagger UI enabled in local/dev

production exposure configurable

no JPA entity exposure

no AI API documentation
```

---

# 134. Technical Compatibility Note

The project has intentionally not fixed:

```text
Spring Boot version
springdoc version
```

Therefore dependency version must be selected during implementation setup.

The implementation must use the springdoc release line compatible with the selected Spring Boot generation rather than copying a version number from this design document.

---

# 135. Next Phase

Recommended next phase:

```text
OpenAPI / Swagger Configuration Design
        ↓
Testing Strategy
        ↓
Implementation Plan
```

The immediate next artifact should be:

```text
KBase Core v1 – Testing Strategy
```

It should consolidate:

```text
unit tests
repository tests
service tests
security tests
API/controller tests
PostgreSQL Testcontainers
MinIO Testcontainers
migration tests
authorization matrix tests
failure/compensation tests
OpenAPI contract tests
```

before moving to the final implementation plan.
