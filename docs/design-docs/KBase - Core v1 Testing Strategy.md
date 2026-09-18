# KBase – Core v1
## Testing Strategy

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**Architecture:** Feature-first Modular Monolith  
**Persistence:** PostgreSQL / Spring Data JPA  
**Object Storage:** MinIO  
**Security:** Spring Security + JWT  
**API Docs:** OpenAPI / Swagger  
**Build Baseline:** Maven  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa Testing Strategy cho KBase Core v1.

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
- OpenAPI / Swagger Configuration Design

Mục tiêu:

- Chuẩn hóa test layers.
- Xác định test nào cần mock và test nào cần real infrastructure.
- Bảo vệ business rules quan trọng.
- Bảo vệ authorization matrix.
- Kiểm tra database constraints thực tế.
- Kiểm tra MinIO integration.
- Kiểm tra transaction và compensation.
- Kiểm tra REST contract.
- Kiểm tra exception/error contract.
- Kiểm tra OpenAPI contract.
- Giảm flaky tests.
- Chuẩn bị trực tiếp cho Implementation Plan và coding.

---

# 2. Testing Philosophy

KBase Core v1 ưu tiên:

```text
Test behavior that matters.
```

Không ưu tiên:

```text
Test private implementation details.
```

Ví dụ quan trọng:

```text
MEMBER cannot modify another member's document.
```

Ví dụ không nên test quá chặt:

```text
DocumentService must call repository method X exactly twice.
```

trừ khi số lần gọi là behavior có ý nghĩa.

---

# 3. Main Test Layers

Core v1 sử dụng các lớp test:

```text
1. Unit Tests
2. Repository Tests
3. Service Tests
4. Security Tests
5. Controller / API Tests
6. Integration Tests
7. Database Migration Tests
8. MinIO Integration Tests
9. OpenAPI Contract Tests
```

Không cần E2E browser automation trong backend Core v1 nếu frontend chưa ổn định.

---

# 4. Recommended Test Distribution

Không cần áp dụng cứng một tỷ lệ tuyệt đối, nhưng baseline:

```text
Many unit/service tests

Moderate repository/security/API tests

Smaller number of full integration tests
```

Mục tiêu:

```text
fast local feedback
+
real infrastructure confidence
```

---

# 5. Test Technology Baseline

Recommended:

```text
JUnit 5
Spring Boot Test
Mockito
AssertJ
MockMvc
Testcontainers
PostgreSQL container
MinIO container
Redis container
```

Exact library versions depend on selected Spring Boot version.

Do not hard-code test dependency versions before implementation baseline is fixed.

---

# 6. Why PostgreSQL Testcontainers

Physical design uses PostgreSQL-specific behavior:

```text
partial unique indexes
expression indexes with LOWER(...)
composite foreign keys
TIMESTAMPTZ
real constraint names
```

H2 cannot reliably represent all of these semantics.

Therefore:

```text
PostgreSQL Testcontainers
```

is preferred for repository/integration tests.

---

# 7. Why MinIO Testcontainers

Mocking `MinioClient` is useful for adapter unit tests.

But Core integration also needs real MinIO behavior for:

```text
upload
get
range reads
stat
delete
batch delete
bucket initialization
```

Therefore:

```text
MinIO Testcontainer
```

should be used for important storage integration tests.

---

# 7.1 Why Redis Testcontainers

Core v1 uses Redis only for short-lived registration email-verification OTP state.

Integration tests need real Redis behavior for:

```text
OTP TTL
resend cooldown
attempt counter
key replacement on resend
state removal after successful verification
Redis-unavailable handling
```

Refresh sessions remain PostgreSQL-backed and must not be moved into Redis by tests.

Redis test data is ephemeral. No durable Redis volume is required.

---

# 8. Test Environment Categories

Recommended:

```text
unit
integration
container-backed integration
```

Potential Maven profiles or test naming can separate:

```text
fast test suite
full integration suite
```

Exact Maven profile names are implementation details.

---

# 9. Test Package Structure

Mirror production packages.

Example:

```text
src/test/java/com/kbase
│
├── auth
├── user
├── project
├── invitation
├── folder
├── category
├── tag
├── document
├── security
├── storage
├── shared
└── integration
```

This helps feature ownership remain clear.

---

# 10. Naming Convention

Recommended test class names:

```text
AuthServiceTest
ProjectServiceTest
ProjectMemberServiceTest
InvitationServiceTest
FolderServiceTest
CategoryServiceTest
TagServiceTest
DocumentServiceTest
DocumentAuthorizationServiceTest
```

Integration:

```text
AuthApiIntegrationTest
ProjectApiIntegrationTest
DocumentApiIntegrationTest
```

Repository:

```text
ProjectMemberRepositoryTest
DocumentRepositoryTest
```

---

# 11. Test Method Naming

Prefer readable behavior names.

Example:

```text
shouldRejectMemberWhenDeletingAnotherUsersDocument
```

or:

```text
memberCannotDeleteAnotherMembersDocument
```

Avoid cryptic:

```text
testDelete2
```

---

# 12. Arrange – Act – Assert

Use clear structure:

```text
Arrange
Act
Assert
```

For complex integration flows:

```text
Given
When
Then
```

Consistency matters more than exact naming style.

---

# 13. Test Data Principle

Tests should create only data necessary for scenario.

Avoid giant shared fixture that creates:

```text
10 users
5 projects
20 documents
```

for every test.

Small scenario-specific data improves readability and speed.

---

# 14. Test Data Builder

Recommended helpers:

```text
UserTestData
ProjectTestData
DocumentTestData
InvitationTestData
```

or builder/factory methods.

Examples:

```text
activeUser()
adminUser()
projectOwner()
projectMember()
```

These are test utilities only.

---

# 15. Avoid Shared Mutable Test State

Do not rely on:

```text
test execution order
shared static mutable objects
previous test DB rows
```

Every test must be independently repeatable.

---

# 16. Database Cleanup

Container-backed integration tests may use:

```text
transaction rollback
```

for DB-only tests.

For tests involving MinIO:

```text
explicit storage cleanup
```

may also be needed because MinIO is outside Spring transaction.

---

# 17. Unit Test Scope

Unit tests should focus on logic that can be isolated cheaply.

Examples:

```text
JwtService token logic
RefreshSessionService validation
ProjectAuthorizationService
DocumentAuthorizationService
FileValidationService
constraint translator
mapper logic
```

Avoid booting full Spring context for every unit test.

---

# 18. AuthService Unit Tests

Must cover:

```text
register normalizes email

register hashes password

register always creates USER

register creates ACTIVE user

register leaves emailVerifiedAt null

register creates Redis OTP state

register requests Gmail verification OTP delivery

duplicate email rejected

login correct credentials succeeds only after email verification

unknown email returns INVALID_CREDENTIALS

wrong password returns INVALID_CREDENTIALS

unverified user cannot login and returns EMAIL_NOT_VERIFIED

disabled user cannot login

login creates access token

login creates refresh session
```

---

# 18.1 EmailVerificationService / OtpService Unit Tests

Must cover:

```text
OTP generation uses 6 numeric digits baseline

raw OTP is not persisted

protected OTP value is written to Redis

OTP TTL = configured baseline 5 minutes

correct OTP marks emailVerifiedAt

incorrect OTP returns INVALID_OTP

expired OTP returns OTP_EXPIRED

attempt counter increments

5 failed attempts returns OTP_ATTEMPTS_EXCEEDED

verified account returns EMAIL_ALREADY_VERIFIED

resend replaces previous OTP state

resend cooldown baseline 60 seconds

resend during cooldown returns OTP_RESEND_COOLDOWN

Redis unavailable returns OTP_SERVICE_UNAVAILABLE

Gmail send failure returns EMAIL_SERVICE_UNAVAILABLE

successful verification removes pending OTP state
```

---

# 19. RefreshSessionService Unit Tests

Cover:

```text
secure token generated

token stored as hash

raw token not stored

valid session accepted

revoked session rejected

expired session rejected

invalid token rejected

revoke current session

revoke all sessions for user
```

---

# 20. JwtService Unit Tests

Cover:

```text
valid token generation

subject/userId claim

systemRole claim

issued-at

expiration

invalid signature

expired token

malformed token

tampered token
```

Use controllable:

```text
Clock
```

where possible.

---

# 21. ProjectAuthorizationService Unit Tests

Must cover:

```text
ADMIN bypasses project membership

OWNER accepted

MEMBER accepted for read access

non-member rejected

owner-only operation rejects MEMBER

missing project returns PROJECT_NOT_FOUND
```

---

# 22. DocumentAuthorizationService Unit Tests

Critical cases:

```text
ADMIN can modify any document

OWNER can modify any project document

MEMBER can modify own document

MEMBER cannot modify another member's document

MEMBER can read another member's document

former member cannot read document
```

---

# 23. FileValidationService Unit Tests

Supported file types:

```text
PDF DOC DOCX XLS XLSX PPT PPTX MD TXT
JPG JPEG PNG GIF SVG BMP
MP4 MOV AVI
```

Tests:

```text
empty file rejected

allowed extension accepted

unsupported extension rejected

MIME mismatch rejected

document size limit

image size limit

video size limit

batch count limit

FileKind derivation
```

Configuration values must be test-controlled.

---

# 24. FolderService Unit Tests

Cover:

```text
OWNER creates folder

MEMBER cannot create folder

parent from another project rejected

duplicate sibling name rejected

same folder name under different parent accepted

folder cannot parent itself

folder cannot move under descendant

empty folder can delete

folder with child cannot delete

folder with document cannot delete
```

---

# 25. CategoryService Unit Tests

Cover:

```text
OWNER create

MEMBER cannot manage

duplicate name rejected case-insensitively

rename duplicate rejected

unused category delete succeeds

category in use rejected
```

---

# 26. TagService Unit Tests

Cover:

```text
MEMBER may create tag

OWNER may create tag

duplicate tag rejected case-insensitively

MEMBER cannot rename tag

MEMBER cannot delete tag

OWNER may rename/delete

delete does not delete documents
```

---

# 27. InvitationService Unit Tests

Cover:

```text
OWNER can invite

MEMBER cannot invite

existing member cannot be invited

duplicate PENDING invitation rejected

resend only PENDING

resend replaces token hash

resend resets expiration

cancel only PENDING

accept PENDING works

accept expired fails

accept cancelled fails

accept accepted fails

email mismatch fails

existing membership fails

successful accept creates MEMBER

successful accept sets ACCEPTED

successful accept sets acceptedAt
```

---

# 28. Invitation Concurrency Test

Important integration case:

```text
two requests attempt to accept same invitation
```

Expected:

```text
only one membership created

only one successful accept

invitation ends ACCEPTED
```

Use real PostgreSQL and transaction/locking behavior.

---

# 29. ProjectService Unit Tests

Cover:

```text
create project creates Project

create project creates OWNER membership

project creation failure rolls back conceptually

listMyProjects uses membership

get project requires access

MEMBER cannot update

OWNER can update

ADMIN can update

delete requires OWNER/ADMIN
```

External-storage delete behavior should be covered separately with integration/interaction tests.

---

# 30. ProjectMemberService Unit Tests

Cover:

```text
member list requires access

OWNER removes MEMBER

MEMBER cannot remove another member

OWNER cannot remove OWNER

MEMBER can leave

OWNER cannot leave

leaving does not delete uploaded documents
```

---

# 31. UserService Unit Tests

Cover:

```text
get profile

update displayName only

email not mutable through profile update

change password verifies current password

new password encoded

password change revokes refresh sessions

ADMIN disable user

disable revokes sessions

re-enable does not reactivate revoked sessions

delete user with no dependency succeeds

owner cannot be hard-deleted

document uploader cannot be hard-deleted

dependent user returns USER_HAS_DEPENDENCIES
```

---

# 32. Repository Test Scope

Repository tests verify:

```text
query correctness
mapping correctness
constraints
indexes/uniqueness behavior where relevant
```

Use real PostgreSQL.

Avoid mocking JPA repository to prove a query that only PostgreSQL can validate.

---

# 33. UserRepository Tests

Cover:

```text
findByEmail

existsByEmail

unique normalized email

case/lowercase assumptions from DB constraints
```

---

# 34. ProjectMemberRepository Tests

Cover:

```text
findByProjectIdAndUserId

duplicate project/user rejected

single OWNER partial unique index

find owner

member pagination/fetch user
```

---

# 35. ProjectInvitationRepository Tests

Cover:

```text
findByTokenHash

findByIdAndProjectId

duplicate PENDING project/email rejected

same email allowed in different project

non-PENDING history can coexist

pessimistic lookup works transactionally
```

---

# 36. FolderRepository Tests

Critical DB cases:

```text
root names unique case-insensitively

child names unique case-insensitively under same parent

same child name allowed under different parents

parent from another project rejected

self-parent check rejected
```

Long cycle prevention is service-level and should not be expected from DB.

---

# 37. CategoryRepository Tests

Cover:

```text
project-level case-insensitive uniqueness

same category name allowed in another project

same-project FK consistency
```

---

# 38. TagRepository Tests

Cover:

```text
project-level case-insensitive uniqueness

same tag name allowed across projects

findAllByProjectIdAndIdIn
```

---

# 39. DocumentRepository Tests

Cover:

```text
document project FK

uploader FK

folder must belong to same project

category must belong to same project

storageKey unique

storage-key projection

existsByFolderId

existsByCategoryId
```

---

# 40. DocumentTagRepository Tests

Critical:

```text
duplicate document/tag pair rejected

tag must belong to same project as document

document-tag project ID integrity

delete document cascades DocumentTag

delete tag cascades DocumentTag

document remains after tag delete
```

---

# 41. Migration Tests

Flyway migrations are source of truth.

Must verify:

```text
clean database
→ migrations apply successfully
```

Also verify Hibernate:

```text
ddl-auto=validate
```

passes against migrated schema.

---

# 42. Migration Test Environment

Use fresh PostgreSQL container.

Flow:

```text
start empty PostgreSQL

run Spring/Flyway migrations

start JPA validation

assert application context starts
```

This catches drift between:

```text
Flyway SQL
and
JPA mapping
```

---

# 43. Constraint Name Tests

Exception Handling depends on explicit constraint names.

Integration tests should provoke:

```text
uq_users_email

uq_project_members_project_user
uq_project_members_single_owner

uq_project_pending_invitation_email

uq_folders_root_name
uq_folders_child_name

uq_categories_project_name
uq_tags_project_name
```

Then ensure translator maps correctly.

---

# 44. Service Integration Tests

Service tests with real repositories should validate:

```text
transaction boundaries

constraint behavior

repository queries

authorization with real membership rows
```

External MinIO/Email can be real or fake depending scenario.

---

# 45. API / Controller Tests

Use:

```text
MockMvc
```

or equivalent Spring MVC testing.

Focus:

```text
request binding

Bean Validation

HTTP status

JSON shape

security behavior

error response format
```

---

# 46. Authentication API Tests

Endpoints:

```text
POST /auth/register
POST /auth/verify-email
POST /auth/resend-verification-otp
POST /auth/login
POST /auth/refresh
POST /auth/logout
```

Test:

```text
register 201

invalid register 400

duplicate email 409

register unverified account and request OTP delivery

verify correct OTP → success

verify wrong OTP → 400 INVALID_OTP

verify expired OTP → 400 OTP_EXPIRED

verify after max attempts → 429 OTP_ATTEMPTS_EXCEEDED

resend within cooldown → 429 OTP_RESEND_COOLDOWN

resend after cooldown → success

Redis unavailable → 503 OTP_SERVICE_UNAVAILABLE

Gmail SMTP unavailable → 503 EMAIL_SERVICE_UNAVAILABLE

login 200

wrong credential 401

unverified account 403 EMAIL_NOT_VERIFIED

disabled account 403

refresh cookie handling

logout 204
```

---

# 47. Security Filter Integration Tests

Use real SecurityFilterChain.

Cases:

```text
missing Bearer → 401

valid Bearer → request proceeds

expired Bearer → 401

bad signature → 401

public verify-email/resend-verification-otp → no Bearer required

USER on /admin/** → 403

ADMIN on /admin/** → allowed
```

---

# 48. Disabled Account Security Test

Critical scenario:

```text
1. User logs in.
2. Access token issued.
3. ADMIN disables user.
4. Old access token used.
5. Request rejected.
6. Refresh rejected.
7. Login rejected.
```

This confirms DB status check per authenticated request works.

---

# 49. Project API Tests

Test:

```text
create project 201

creator becomes OWNER

list own projects

non-member GET project → 403

MEMBER GET project → 200

MEMBER PATCH project → 403

OWNER PATCH → 200

OWNER DELETE → 204

MEMBER DELETE → 403
```

---

# 50. Member API Tests

Test:

```text
list members as MEMBER

remove MEMBER as OWNER

remove as MEMBER forbidden

remove OWNER conflict

MEMBER leave 204

OWNER leave 409

removed member documents remain
```

---

# 51. Invitation API Tests

Test:

```text
OWNER create 201

MEMBER create 403

existing member 409

duplicate pending 409

list as OWNER

resend PENDING

cancel PENDING

accept with matching authenticated email

accept email mismatch 403

accept expired 409

accept non-pending 409
```

---

# 52. Folder API Tests

Test:

```text
MEMBER list 200

MEMBER create 403

OWNER create 201

duplicate sibling 409

cycle update 409

delete non-empty 409

delete empty 204
```

---

# 53. Category API Tests

Test:

```text
MEMBER list

MEMBER create forbidden

OWNER create

duplicate 409

rename

delete in-use 409

delete unused 204
```

---

# 54. Tag API Tests

Test:

```text
MEMBER list

MEMBER create allowed

MEMBER rename forbidden

MEMBER delete forbidden

OWNER rename allowed

OWNER delete allowed
```

---

# 55. Document API Tests

Test:

```text
upload 201

metadata search

get metadata

update own document as MEMBER

update another's document forbidden

OWNER update another's document

preview supported file

preview unsupported office type

download

delete own

delete other's forbidden

OWNER delete any
```

---

# 56. Multipart Upload Tests

Cases:

```text
valid file only

file + metadata JSON

empty file

unsupported extension

invalid MIME

too large

folder not found

category not found

tag not found

cross-project folder

cross-project category

cross-project tag
```

---

# 57. Batch Upload Tests

Cases:

```text
valid multiple files

over max count

one invalid file

one MinIO failure

one DB persistence failure

compensation deletes prior uploaded objects
```

Current baseline:

```text
all-or-fail at application level where practical
```

Tests should reflect final implementation of that baseline.

---

# 58. Document Search Tests

Filters:

```text
q

folderId

categoryId

tagId

fileKind

uploadedBy

createdFrom

createdTo

combined filters
```

Also:

```text
pagination

sort whitelist

unsupported sort rejected
```

---

# 59. Search Project Isolation

Critical:

```text
Project A search
must never return Project B documents
```

Test even when:

```text
same displayName
same tag name
same category name
```

exists across projects.

---

# 60. MinIO Adapter Unit Tests

Mock MinIO SDK.

Test:

```text
upload request conversion

get conversion

range get conversion

stat conversion

delete

deleteAll per-object failure handling

SDK exception translation
```

No Spring Boot context needed.

---

# 61. MinIO Integration Tests

Real MinIO container.

Test:

```text
bucket creation in local mode

upload stream

read full object

read range

stat

delete

delete already missing behavior

batch delete

private bucket assumptions
```

---

# 62. Large File Streaming Test

Need verify architecture does not rely on full-file buffering.

A practical integration test can upload/download a reasonably large generated stream.

Do not require actual 500 MB in normal CI.

Goal:

```text
stream path works
```

not:

```text
benchmark maximum configured file size every build
```

---

# 63. Video Range Tests

For MP4/range-capable preview:

```text
Range: bytes=0-99
→ 206

correct Content-Range

correct Content-Length

returned bytes match expected segment

invalid range
→ 416

no Range
→ 200
```

Use binary test fixture small enough for CI.

---

# 64. Preview Tests

Supported:

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

At least one representative per behavior group should be tested.

Office:

```text
DOCX/XLSX/PPTX preview
→ PREVIEW_NOT_SUPPORTED
```

Download still succeeds.

---

# 65. Storage Compensation Test

Critical scenario:

```text
MinIO upload succeeds
DB save fails
```

Expected:

```text
StorageService.delete called
object removed
FILE_UPLOAD_FAILED
```

With real integration where practical:

```text
verify object absent after failure
```

---

# 66. Compensation Failure Test

Scenario:

```text
MinIO upload succeeds

DB save fails

cleanup delete fails
```

Expected:

```text
FILE_UPLOAD_FAILED

error logged

orphan object remains for operational repair
```

API must not expose storage key.

---

# 67. Document Delete Failure Tests

Scenario A:

```text
storage delete fails
```

Expected:

```text
DB row remains

DOCUMENT_DELETE_FAILED
or STORAGE_SERVICE_UNAVAILABLE
```

Scenario B:

```text
storage delete succeeds
DB delete fails
```

Expected:

```text
error surfaced

inconsistency logged
```

This is a known Core v1 risk.

---

# 68. Project Delete Failure Tests

Scenario:

```text
Project has N document objects.

deleteAll fails for one.
```

Expected under current baseline:

```text
Project metadata is not deleted.

Failure returned.

Storage error logged.
```

---

# 69. Gmail SMTP Adapter Unit Tests

Mock Gmail SMTP / MailService abstraction.

Test:

```text
correct recipient

verification OTP email content passed safely

project/inviter data passed

invitation URL passed

invitation expiry passed

Gmail SMTP exception translated
```

Never assert/log raw secret credentials.

---

# 70. Gmail Email Integration Tests

Full real external SMTP is not required for normal CI.

Use:

```text
fake SMTP server
```

or controlled mail test server if implementation adds one.

Goal:

```text
verification OTP message generated correctly
project invitation message generated correctly
delivery integration works
```

not send real Gmail messages.

Exact test mail tool can be selected during implementation.

---

# 70.1 Redis OTP Integration Tests

Use a real Redis Testcontainer.

Test:

```text
OTP state written with TTL

TTL expiration removes validity

attempt count persists for the OTP lifetime

resend cooldown key/state works

resend replaces previous OTP state

successful verification removes OTP state

Redis restart/loss invalidates pending OTP state

user can request a new OTP after Redis returns
```

Do not test or implement refresh sessions in Redis.

---

# 71. Exception Handling Tests

Global handler:

```text
BusinessException → correct status/code

ResourceNotFound → 404

ForbiddenOperation → 403

validation → errors map

malformed JSON → INVALID_REQUEST_BODY

invalid UUID → INVALID_PARAMETER

unknown exception → INTERNAL_SERVER_ERROR
```

---

# 72. Error Response Contract Tests

Every tested error should include:

```text
timestamp

status

code

message

path

requestId
```

Validation errors additionally:

```text
errors
```

No stack trace.

---

# 73. Request ID Tests

Cases:

```text
request without X-Request-Id
→ server generates one

error response includes same requestId

response header includes same requestId

MDC cleared after request
```

If client-supplied IDs are reused:

```text
validate expected behavior
```

---

# 74. Information Leakage Tests

API responses must not expose:

```text
passwordHash

tokenHash

storageKey

MinIO endpoint internals

SQL

constraint stack trace

Java exception class

refresh token

invitation token hash
```

This can be checked in targeted API tests.

---

# 75. OpenAPI Contract Tests

At minimum:

```text
GET /v3/api-docs → 200

contains /api/v1/auth/register

contains /api/v1/auth/verify-email

contains /api/v1/auth/resend-verification-otp

contains /api/v1/projects

contains /api/v1/documents/{documentId}

bearerAuth exists

ApiErrorResponse exists

multipart upload represented

binary download represented
```

---

# 76. OpenAPI Security Tests

Verify:

```text
register/verify-email/resend-verification-otp/login/refresh
do not require bearerAuth

protected endpoints
do require bearerAuth

admin operations are documented as ADMIN in description/tags
```

Swagger annotation metadata is not authorization, but contract must remain accurate.

---

# 77. OpenAPI Sensitive Field Test

Generated schema must not expose:

```text
passwordHash

tokenHash

storageKey

refresh_sessions fields

MinIO credentials
```

---

# 78. Swagger Multipart Test

Inspect generated spec:

```text
multipart/form-data

file:
type string
format binary

metadata:
DocumentMetadataRequest
```

---

# 79. Swagger Binary Test

Preview/download:

```text
200/206 response schema
type string
format binary
```

Do not show:

```text
DocumentResponse
```

as binary response body.

---

# 80. Authorization Matrix Tests

A dedicated matrix is recommended.

Actors:

```text
Non-member USER
MEMBER
OWNER
ADMIN
```

Operations:

```text
view project

update project

delete project

view members

remove member

manage invitation

view folders

manage folders

view categories

manage categories

create tag

rename/delete tag

upload

read document

modify own document

modify other's document

delete own document

delete other's document
```

---

# 81. Authorization Matrix – Expected Core Behavior

| Operation | Non-member | MEMBER | OWNER | ADMIN |
|---|---:|---:|---:|---:|
| View project | ❌ | ✅ | ✅ | ✅ |
| Update project | ❌ | ❌ | ✅ | ✅ |
| Delete project | ❌ | ❌ | ✅ | ✅ |
| View members | ❌ | ✅ | ✅ | ✅ |
| Remove member | ❌ | ❌ | ✅ | ✅ |
| Manage invitation | ❌ | ❌ | ✅ | ✅ |
| View folders | ❌ | ✅ | ✅ | ✅ |
| Manage folders | ❌ | ❌ | ✅ | ✅ |
| View categories | ❌ | ✅ | ✅ | ✅ |
| Manage categories | ❌ | ❌ | ✅ | ✅ |
| View tags | ❌ | ✅ | ✅ | ✅ |
| Create tag | ❌ | ✅ | ✅ | ✅ |
| Rename/delete tag | ❌ | ❌ | ✅ | ✅ |
| Upload document | ❌ | ✅ | ✅ | ✅ |
| Read document | ❌ | ✅ | ✅ | ✅ |
| Modify own document | ❌ | ✅ | ✅ | ✅ |
| Modify other's document | ❌ | ❌ | ✅ | ✅ |
| Delete own document | ❌ | ✅ | ✅ | ✅ |
| Delete other's document | ❌ | ❌ | ✅ | ✅ |

This matrix should be represented by automated tests, not only documentation.

---

# 82. Former Member Tests

Critical scenario:

```text
User uploads Document A.

OWNER removes User from Project.

Document A remains.

Former User attempts:
GET Document A
DOWNLOAD Document A
PATCH Document A
DELETE Document A
```

Expected:

```text
all access denied
```

OWNER still:

```text
can manage Document A
```

---

# 83. ADMIN Override Tests

ADMIN without ProjectMember row:

```text
view project

update/delete project

view documents

modify/delete documents

manage membership

manage folders/categories/tags
```

should work according to accepted Core rules.

Do not insert fake ADMIN membership in test setup.

---

# 84. Hard Delete Tests

Document hard delete:

```text
Document row gone

DocumentTag rows gone

MinIO object gone
```

Project hard delete:

```text
Project gone

members gone

invitations gone

folders gone

categories gone

tags gone

documents gone

document tags gone

MinIO project document objects gone
```

---

# 85. User Hard Delete Tests

Safe user:

```text
delete succeeds
```

User with project owner role:

```text
USER_OWNS_PROJECT
```

User with uploaded document:

```text
USER_HAS_DEPENDENCIES
```

User with unresolved membership/invitation dependency:

```text
USER_HAS_DEPENDENCIES
```

---

# 86. Case-Insensitive Uniqueness Tests

Must explicitly test:

```text
User@example.com
user@example.com
```

according to normalized email rule.

Folder:

```text
Specs
specs
```

same parent:

```text
rejected
```

Category/tag similarly.

---

# 87. Cross-Project Integrity Tests

Database-level:

```text
Folder parent Project B assigned to Folder Project A
→ rejected

Folder Project B assigned to Document Project A
→ rejected

Category Project B assigned to Document Project A
→ rejected

Tag Project B linked to Document Project A
→ rejected
```

Service-level tests should reject before DB where possible.

---

# 88. Transaction Tests

Create Project:

```text
Project save succeeds

OWNER membership save fails

→ no Project persists
```

Invitation Accept:

```text
membership creation fails

→ invitation remains PENDING
```

Document metadata update:

```text
tag sync fails

→ metadata/tag DB changes roll back
```

---

# 89. Transaction vs External Side Effect Tests

Be explicit:

```text
DB rollback does not rollback MinIO

DB rollback does not unsend email
```

Tests should validate compensation behavior where implemented.

Do not write tests assuming distributed ACID.

---

# 90. Time-Based Tests

Use injected:

```text
Clock
```

for:

```text
JWT expiry

refresh expiry

invitation expiry
```

Avoid:

```text
Thread.sleep(...)
```

for expiration tests.

---

# 91. Concurrency Tests

High-value concurrency cases:

```text
duplicate email registration

duplicate project membership

two OWNER creation attempts

duplicate PENDING invitation

two invitation accept requests

duplicate category/tag creation
```

Database constraints must preserve integrity.

---

# 92. No Optimistic Locking Assumption

Core v1 has no mandatory:

```text
@Version
```

Do not write tests expecting optimistic-lock exceptions unless implementation later explicitly adds it.

---

# 93. Performance-Smoke Tests

Core v1 does not require full performance benchmark suite.

Useful smoke checks may include:

```text
document list pagination

metadata search on representative dataset

large stream upload/download
```

These are not strict SLA tests.

---

# 94. N+1 Query Tests

Potentially inspect query count for high-risk endpoints:

```text
list project members

list/search documents

document detail + tags
```

Goal:

```text
avoid obvious N+1 regressions
```

Do not make tests brittle around an exact query count unless needed.

---

# 95. Pagination Tests

Test:

```text
default page = 0

default size = 20

max size = 100

page boundaries

empty result

last page

sort ASC/DESC
```

---

# 96. Unknown Resource Tests

Examples:

```text
unknown project

unknown document

unknown folder
```

Expected:

```text
404
```

When resource exists but user lacks access:

```text
403
```

Preserve existing REST contract.

---

# 97. 401 vs 403 Tests

Explicitly test:

```text
no token
→ 401

expired/invalid token
→ 401

valid token but no project access
→ 403

valid token MEMBER but owner-only operation
→ 403

disabled authenticated account
→ 403
```

---

# 98. HTTP Contract Tests

Also test:

```text
malformed JSON → 400

invalid UUID → 400

unsupported content type → 415

wrong HTTP method → 405

multipart too large → 413
```

---

# 99. Request Validation Tests

Examples:

```text
blank project name

invalid email

short password

blank displayName

invalid enum

negative/invalid pagination values
```

Ensure:

```text
VALIDATION_ERROR
```

format remains stable.

---

# 100. CI Test Stages

Recommended future CI order:

```text
1. compile

2. unit tests

3. repository/integration tests with containers

4. OpenAPI contract tests

5. package/build
```

Optional later:

```text
static analysis
dependency scanning
container image test
```

---

# 100.1 Docker Persistence Smoke Tests

Local Docker Compose verification should include:

```text
create PostgreSQL data
store a MinIO object
recreate PostgreSQL and MinIO containers without deleting named volumes
verify PostgreSQL data remains
verify MinIO object remains
```

Required local named volumes:

```text
postgres_data
minio_data
```

Redis OTP state is intentionally ephemeral:

```text
Redis container recreation may invalidate pending OTPs
no durable Redis volume is required
user can resend verification OTP
```

Also verify backend startup:

```text
Flyway migrations execute against the PostgreSQL container
Flyway history persists in PostgreSQL named volume
Hibernate schema validation succeeds
```

---

# 101. Fast Local Development Loop

Developer should be able to run:

```text
unit tests
```

without Docker.

Full confidence suite can run with:

```text
Docker/Testcontainers
```

This avoids making every tiny code change depend on infrastructure startup.

---

# 102. Testcontainers Reuse

Local reuse may reduce startup time.

CI should remain isolated.

Exact Testcontainers reuse settings are implementation/environment details.

---

# 103. Flaky Test Policy

Do not accept tests that depend on:

```text
sleep timing

external real Gmail SMTP

external internet

test order

random shared ports without container management

uncontrolled system clock
```

Tests must be deterministic.

---

# 104. Random Data

Random UUIDs are fine.

Random business values should be controlled enough to debug failures.

Use deterministic fixture values where possible.

---

# 105. External Internet

Core backend tests should not require public internet.

PostgreSQL/MinIO/Redis test infrastructure should be local containers.

This keeps CI stable.

---

# 106. Email Testing Boundary

Do not send actual Gmail email to real users in automated tests.

Use:

```text
mock MailService

or fake local SMTP compatible with the Gmail SMTP adapter contract
```

depending on test layer.

---

# 107. Security Test Data

Use fake credentials only.

Never use:

```text
real production JWT secret

real MinIO credentials

real Gmail SMTP App Password
```

in tests.

---

# 108. Test Secrets

Test-only secrets may be static and clearly marked for test profile.

Do not use real Gmail App Passwords or production Redis/PostgreSQL/MinIO credentials in automated tests.

They must never be shared with production configuration.

---

# 109. Test Profile

Recommended:

```text
application-test.yml
```

containing non-secret test defaults.

Dynamic PostgreSQL/MinIO/Redis Testcontainers connection details should override at runtime.

---

# 110. Flyway in Tests

Do not bypass migrations by letting Hibernate create a different schema.

Container integration flow:

```text
Flyway migrate

Hibernate validate
```

This ensures tests run against real schema design.

---

# 111. ddl-auto in Tests

Recommended integration setting:

```text
validate
```

not:

```text
create-drop
```

for schema-validation integration tests.

Some isolated JPA experiments may differ, but production-like suite must use migrations.

---

# 112. Test Fixture Files

Keep small representative fixtures under:

```text
src/test/resources/files
```

Examples:

```text
sample.pdf
sample.txt
sample.png
small.mp4
unsupported.xyz
```

Do not store huge binary fixtures in Git.

---

# 113. File Generation in Tests

For size-limit tests, generate:

```text
byte stream of controlled size
```

rather than committing huge files.

---

# 114. SVG Security Note

Core v1 supports SVG upload.

Testing should at least validate MIME/type handling.

Content sanitization of SVG has not been specified as a Core business requirement.

Do not silently add a sanitizer behavior to tests unless security implementation explicitly adds it.

---

# 115. Virus Scanning

Virus scanning is not part of Core v1.

No antivirus integration tests are added.

If later introduced, it requires a new security/storage flow design.

---

# 116. API Documentation vs Behavior Tests

Swagger tests verify documentation contract.

API integration tests verify runtime behavior.

Both are needed because:

```text
correct implementation + stale docs
is still a defect
```

and:

```text
correct docs + broken runtime
is also a defect
```

---

# 117. Coverage Guidance

Do not optimize purely for percentage.

Priority:

```text
critical authorization

data integrity

transaction behavior

storage compensation

security token behavior

error contract
```

Coverage percentage is secondary.

---

# 118. Critical Path Coverage

The following flows need strong coverage:

```text
register → verify email OTP → login → refresh → logout

create project → owner membership

invite → accept → member access

folder/category/tag organization

upload → browse → preview/download → update → delete

member removed → documents remain → access revoked

project hard delete
```

---

# 119. Happy Path + Negative Path

Every major service/API should have:

```text
happy-path test

permission-denied test

not-found test

business-conflict test

infrastructure-failure test where relevant
```

---

# 120. Regression Test Rule

Every confirmed bug should ideally gain a regression test before/with fix.

This reduces reintroduction.

---

# 121. Deliberately Not Added

Core v1 Testing Strategy does not require:

```text
browser E2E automation

mobile testing

load-testing platform

chaos engineering

penetration testing automation

contract testing against AI service

Kubernetes integration tests

Terraform tests

AWS S3 tests

real external Gmail SMTP tests

MFA / OTP-login tests

password-reset OTP tests

virus scanner tests
```

These can be added when corresponding functionality exists.

---

# 122. Recommended Minimum Test Suite Before Merge

At minimum:

```text
unit tests pass

repository/PostgreSQL tests pass

security authorization tests pass

API validation/error tests pass

Flyway migration test passes

OpenAPI contract test passes
```

For changes touching storage:

```text
MinIO integration tests also pass
```

---

# 123. Recommended Minimum Test Suite Before Release

Before release/deployment:

```text
all unit tests

all PostgreSQL integration tests

all security tests

all API integration tests

all MinIO integration tests

migration validation

OpenAPI contract validation

critical workflow tests

hard-delete tests

compensation/failure tests
```

---

# 124. Testing Definition of Done

Testing Strategy is implemented when:

```text
unit tests cover core business rules

real PostgreSQL verifies schema constraints

Flyway migrations are tested

real MinIO verifies storage behavior

authorization matrix is automated

JWT/refresh security is tested

registration email verification OTP is tested

Redis OTP TTL/attempt/cooldown behavior is tested

Gmail SMTP delivery adapter is tested without real external Gmail in CI

disabled-user behavior is tested

member removal preserves documents

cross-project isolation is tested

upload compensation is tested

hard delete is tested

error contract is tested

request ID is tested

OpenAPI contract is tested

test suite is deterministic and CI-friendly
```

---

# 125. Recommended Baseline Test Matrix

```text
Auth
✓ Unit
✓ API
✓ Security integration
✓ Email verification OTP
✓ Redis integration
✓ Gmail mail-adapter integration

User
✓ Unit
✓ Repository
✓ API

Project
✓ Unit
✓ Repository
✓ API
✓ Authorization

Membership
✓ Unit
✓ Repository
✓ API
✓ Authorization

Invitation
✓ Unit
✓ Repository
✓ API
✓ Concurrency

Folder
✓ Unit
✓ Repository
✓ API
✓ DB integrity

Category
✓ Unit
✓ Repository
✓ API
✓ DB integrity

Tag
✓ Unit
✓ Repository
✓ API
✓ DB integrity

Document
✓ Unit
✓ Repository
✓ API
✓ Authorization
✓ MinIO integration
✓ Compensation

Exception Handling
✓ Unit/API integration

OpenAPI
✓ Contract integration
```

---

# 126. Next Phase

Recommended next phase:

```text
Testing Strategy
        ↓
Implementation Plan
```

The immediate next artifact should be:

```text
KBase Core v1 – Implementation Plan
```

It should convert all approved design documents into an executable development sequence:

```text
project bootstrap

dependencies

configuration

Flyway schema

entities

repositories

exception framework

security/auth

user/project/membership

invitation/email

folder/category/tag

MinIO/storage

document lifecycle

search

Swagger

tests

Docker

final stabilization
```

The plan should include phase order, dependency between tasks, acceptance criteria, and which tests must pass before moving to the next phase.
