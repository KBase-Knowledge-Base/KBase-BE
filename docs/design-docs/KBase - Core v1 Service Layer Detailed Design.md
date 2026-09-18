# KBase – Core v1
## Service Layer Detailed Design

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**Architecture:** Feature-first Modular Monolith  
**Persistence:** Spring Data JPA / PostgreSQL  
**Object Storage:** MinIO through `StorageService`  
**Email:** Gmail SMTP through `MailService`  
**OTP Store:** Redis through `OtpStore`  
**Security:** Spring Security + JWT  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa chi tiết Service Layer cho KBase Core v1.

Tài liệu kế thừa trực tiếp từ:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design
- REST API Specification
- Spring Boot Application Architecture + Module/Package Structure
- JPA Entity Mapping + Repository Design
- Spring Security + JWT Design

Mục tiêu:

- Xác định responsibility của từng service.
- Xác định public method contract.
- Xác định authorization call.
- Xác định repository orchestration.
- Xác định transaction boundary.
- Xác định external dependency như MinIO, Redis và Gmail SMTP.
- Xác định email verification OTP flow.
- Xác định business exception path.
- Giảm circular dependency.
- Chuẩn bị trực tiếp cho implementation.

---

# 2. Service Layer Principles

Core v1 tuân theo các nguyên tắc:

```text
Controller
    ↓
Service
    ↓
Repository / Infrastructure abstraction
```

Service chịu trách nhiệm:

```text
business validation
authorization
transaction
repository coordination
external-service coordination
business exceptions
```

Service không chịu trách nhiệm:

```text
HTTP serialization
ResponseEntity
raw JWT parsing
direct SMTP configuration
direct MinIO SDK configuration
```

---

# 3. Service Inventory

Core v1 dự kiến có các service chính:

```text
AuthService
EmailVerificationService
OtpService
RefreshSessionService

UserService
CurrentUserService

ProjectService
ProjectMemberService
ProjectAuthorizationService

InvitationService

FolderService
CategoryService
TagService

DocumentService
DocumentSearchService
DocumentAuthorizationService
FileValidationService

StorageService
MinioStorageService

MailService
SmtpMailService

OtpStore
RedisOtpStore
```

Không phải mọi service đều cần interface.

Infrastructure abstraction nên có interface:

```text
StorageService
MailService
OtpStore
```

Application service có thể là concrete `@Service`.

---

# 4. Transaction Rule

Transaction boundary nằm ở Service Layer.

Ví dụ:

```java
@Transactional
public ProjectResponse createProject(...) { ... }
```

Read-only operation có thể:

```java
@Transactional(readOnly = true)
```

Controller không mở transaction.

Repository không định nghĩa business transaction.

---

# 5. External Systems and Transaction Limitation

PostgreSQL transaction không bao trùm:

```text
MinIO
Redis
Gmail SMTP
```

Do đó:

```text
@Transactional
```

không tạo ACID transaction xuyên:

```text
PostgreSQL + MinIO + Redis + Email
```

Các operation này cần:

```text
ordering
compensation
logging
retry strategy where appropriate
```

Core v1 baseline vẫn là synchronous coordination.

Advanced patterns như:

```text
Outbox
Saga
message broker
distributed transaction
```

chưa thuộc Core v1.

---

# 6. AuthService

Package:

```text
auth.service.AuthService
```

Responsibilities:

```text
register
login
refresh access token
logout
```

Dependencies:

```text
UserRepository
PasswordEncoder
JwtService
RefreshSessionService
EmailVerificationService
CurrentUserService where needed
```

---

# 7. AuthService.register

Suggested signature:

```java
UserResponse register(RegisterRequest request)
```

Authorization:

```text
Public
```

Transaction:

```text
@Transactional
```

Flow:

```text
normalize email
    ↓
validate uniqueness
    ↓
hash password
    ↓
create User:
    systemRole = USER
    status = ACTIVE
    emailVerifiedAt = null
    ↓
UserRepository.save
    ↓
EmailVerificationService.issueOtp
    ↓
map response
```

Errors:

```text
VALIDATION_ERROR
EMAIL_ALREADY_EXISTS
OTP_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
```

Race protection:

```text
Service existsByEmail check
+
DB UNIQUE(email)
```

If DB unique violation occurs after concurrent registration:

```text
translate to EMAIL_ALREADY_EXISTS
```

---

## 7.1 EmailVerificationService

Package:

```text
auth.service.EmailVerificationService
```

Responsibilities:

```text
issue verification OTP
resend verification OTP
verify OTP
set users.email_verified_at
coordinate Redis OTP state + Gmail SMTP
```

Dependencies:

```text
UserRepository
OtpService / OtpStore
MailService
Clock
```

### issueOtp

Suggested signature:

```java
void issueOtp(User user)
```

Flow:

```text
generate 6-digit OTP
    ↓
store keyed/hash OTP state in Redis
    TTL baseline = 5 minutes
    attempts = 0
    ↓
create resend cooldown state
    ↓
MailService.sendEmailVerificationOtp
```

Raw OTP must not be persisted in PostgreSQL or logs.

If Gmail SMTP send fails after Redis write:

```text
best-effort delete the newly-created OTP state
throw EMAIL_SERVICE_UNAVAILABLE
```

If Redis is unavailable:

```text
throw OTP_SERVICE_UNAVAILABLE
```

### verify

Suggested signature:

```java
EmailVerificationResponse verify(
    VerifyEmailRequest request
)
```

Flow:

```text
normalize email
    ↓
load User
    ↓
if emailVerifiedAt != null:
EMAIL_ALREADY_VERIFIED
    ↓
OtpService.verify(userId, otp)
    ↓
user.emailVerifiedAt = now
    ↓
persist User
    ↓
best-effort remove Redis OTP state
```

Transaction:

```text
@Transactional
```

Redis cleanup is outside PostgreSQL transaction. If cleanup fails after DB verification commit, repeated verification is still rejected by `emailVerifiedAt != null`.

### resend

Suggested signature:

```java
void resend(ResendVerificationOtpRequest request)
```

Rules:

```text
user exists
email not already verified
Redis resend cooldown not active
replace old OTP state
reset attempts/TTL
send through Gmail SMTP
```

Errors:

```text
USER_NOT_FOUND
EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
OTP_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
```

---

## 7.2 OtpService / OtpStore

`OtpService` owns OTP-specific application logic.

`OtpStore` is the infrastructure boundary used by `EmailVerificationService`.

Conceptual interface:

```java
public interface OtpStore {
    void saveVerificationOtp(...);
    OtpVerificationState getVerificationOtp(UUID userId);
    void incrementAttempts(UUID userId);
    boolean isResendCooldownActive(UUID userId);
    void deleteVerificationOtp(UUID userId);
}
```

Implementation:

```text
RedisOtpStore
```

Redis is used only for short-lived OTP state. RefreshSession remains PostgreSQL-backed.

---

# 8. AuthService.login

Suggested signature:

```java
LoginResult login(LoginRequest request)
```

`LoginResult` is an internal/application result containing:

```text
accessToken
expiresIn
rawRefreshToken
user response
```

Controller is responsible for placing raw refresh token into HttpOnly cookie.

Flow:

```text
normalize email
    ↓
UserRepository.findByEmail
    ↓
passwordEncoder.matches
    ↓
if invalid:
INVALID_CREDENTIALS
    ↓
check ACTIVE
    ↓
check emailVerifiedAt != null
    ↓
JwtService.generateAccessToken
    ↓
RefreshSessionService.createSession
    ↓
return LoginResult
```

Transaction:

```text
@Transactional
```

Reason:

RefreshSession must be persisted consistently.

Errors:

```text
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
ACCOUNT_DISABLED
```

Do not reveal whether email exists.

---

# 9. AuthService.refresh

Suggested signature:

```java
AccessTokenResponse refresh(String rawRefreshToken)
```

Flow:

```text
RefreshSessionService.validate(raw token)
    ↓
load User
    ↓
check ACTIVE
    ↓
check emailVerifiedAt != null
    ↓
JwtService.generateAccessToken
    ↓
return token
```

Transaction:

```text
@Transactional(readOnly = true)
```

for baseline non-rotating refresh token.

If refresh rotation is introduced later, change to write transaction.

Errors:

```text
REFRESH_TOKEN_MISSING
INVALID_REFRESH_TOKEN
REFRESH_TOKEN_EXPIRED
REFRESH_SESSION_REVOKED
EMAIL_NOT_VERIFIED
ACCOUNT_DISABLED
```

---

# 10. AuthService.logout

Suggested signature:

```java
void logout(String rawRefreshToken)
```

Behavior baseline:

```text
effectively idempotent
```

Flow:

```text
if token missing:
return success
    ↓
RefreshSessionService.revokeIfPresent
```

Transaction:

```text
@Transactional
```

Controller clears cookie regardless.

Response contract:

```text
204 No Content
```

---

# 11. RefreshSessionService

Responsibilities:

```text
generate refresh token
hash token
persist session
validate session
revoke session
revoke all user sessions
optional cleanup helpers
```

Dependencies:

```text
RefreshSessionRepository
UserRepository or User entity supplied by caller
SecureRandom/token generator
Clock
```

---

# 12. RefreshSessionService.createSession

Suggested signature:

```java
CreatedRefreshSession createSession(User user)
```

Internal result:

```text
rawToken
sessionId
expiresAt
```

Flow:

```text
generate secure random token
    ↓
hash token
    ↓
create RefreshSession
    ↓
save
    ↓
return raw token once
```

Never:

```text
log raw token
store raw token
```

---

# 13. RefreshSessionService.validate

Suggested signature:

```java
RefreshSession validate(String rawToken)
```

Flow:

```text
hash raw token
    ↓
find by tokenHash
    ↓
exists?
    ↓
revokedAt == null?
    ↓
expiresAt > now?
```

Errors:

```text
INVALID_REFRESH_TOKEN
REFRESH_SESSION_REVOKED
REFRESH_TOKEN_EXPIRED
```

---

# 14. RefreshSessionService.revoke

Suggested methods:

```java
void revokeIfPresent(String rawToken)

void revokeAllForUser(UUID userId)
```

`revokeAllForUser` is useful for:

```text
password change
admin disable
```

These behaviors are security baseline recommendations already established in Security Design.

---

# 15. CurrentUserService

Package:

```text
security.service.CurrentUserService
```

Responsibilities:

```text
return current principal
return current user ID
return current system role
```

It should be a small adapter over Spring Security context.

Suggested methods:

```java
CustomUserPrincipal requirePrincipal()

UUID requireUserId()

boolean isAdmin()
```

It must not contain:

```text
project permission queries
document permission queries
```

---

# 16. UserService

Responsibilities:

```text
get current profile
update profile
change password
admin list users
admin get user
enable/disable user
hard-delete user when safe
```

Dependencies:

```text
UserRepository
PasswordEncoder
RefreshSessionService
ProjectMemberRepository
DocumentRepository
ProjectInvitationRepository
UserMapper
```

---

# 17. UserService.getCurrentUser

Suggested signature:

```java
UserResponse getCurrentUser(UUID currentUserId)
```

Transaction:

```text
@Transactional(readOnly = true)
```

Errors:

```text
USER_NOT_FOUND
```

---

# 18. UserService.updateProfile

Suggested signature:

```java
UserResponse updateProfile(
    UUID currentUserId,
    UpdateProfileRequest request
)
```

Allowed field:

```text
displayName
```

Not allowed:

```text
email
systemRole
status
```

Transaction:

```text
@Transactional
```

Errors:

```text
USER_NOT_FOUND
VALIDATION_ERROR
```

---

# 19. UserService.changePassword

Suggested signature:

```java
void changePassword(
    UUID currentUserId,
    ChangePasswordRequest request
)
```

Flow:

```text
load User
    ↓
verify current password
    ↓
validate new password
    ↓
hash new password
    ↓
save
    ↓
revoke refresh sessions (recommended baseline)
```

Transaction:

```text
@Transactional
```

Errors:

```text
USER_NOT_FOUND
CURRENT_PASSWORD_INVALID
VALIDATION_ERROR
```

---

# 20. UserService.updateStatus

ADMIN only.

Suggested signature:

```java
UserResponse updateStatus(
    UUID targetUserId,
    UserStatus status
)
```

Authorization:

```text
Spring Security ADMIN endpoint
```

Flow if disabling:

```text
load user
    ↓
status = DISABLED
    ↓
save
    ↓
RefreshSessionService.revokeAllForUser
```

Re-enable:

```text
status = ACTIVE
```

Old revoked sessions remain revoked.

---

# 21. UserService.deleteUser

ADMIN only.

Suggested signature:

```java
void deleteUser(UUID userId)
```

Flow:

```text
load user
    ↓
check OWNER dependency
    ↓
check uploaded documents
    ↓
check project memberships
    ↓
check invitation references
    ↓
if unresolved dependency:
reject
    ↓
delete User
```

Transaction:

```text
@Transactional
```

Errors:

```text
USER_NOT_FOUND
USER_OWNS_PROJECT
USER_HAS_DEPENDENCIES
```

Do not cascade-delete project knowledge.

---

# 22. ProjectAuthorizationService

Responsibilities:

```text
requireProjectMember
requireProjectOwner
getMembership
ADMIN override
```

Dependencies:

```text
ProjectRepository
ProjectMemberRepository
```

This service is reused by:

```text
ProjectService
ProjectMemberService
InvitationService
FolderService
CategoryService
TagService
DocumentService
DocumentSearchService
```

---

# 23. ProjectAuthorizationService.requireProjectAccess

Suggested signature:

```java
ProjectAccess requireProjectAccess(
    UUID projectId,
    CustomUserPrincipal principal
)
```

Flow:

```text
ensure Project exists
    ↓
if ADMIN:
return admin access
    ↓
find ProjectMember
    ↓
if missing:
PROJECT_ACCESS_FORBIDDEN
```

Internal `ProjectAccess` may contain:

```text
Project
ProjectRole or null for ADMIN
boolean admin
```

This reduces repeated DB lookups inside one service operation.

---

# 24. ProjectAuthorizationService.requireOwner

Suggested signature:

```java
ProjectAccess requireOwner(
    UUID projectId,
    CustomUserPrincipal principal
)
```

Flow:

```text
project exists
    ↓
ADMIN?
allow
    ↓
membership exists?
    ↓
role == OWNER?
allow
else PROJECT_MANAGEMENT_FORBIDDEN
```

---

# 25. ProjectService

Responsibilities:

```text
create project
list current user's projects
get project
update project
hard delete project
admin list all projects
```

Dependencies:

```text
ProjectRepository
ProjectMemberRepository
ProjectAuthorizationService
DocumentRepository
StorageService
ProjectMapper
```

---

# 26. ProjectService.createProject

Suggested signature:

```java
ProjectResponse createProject(
    UUID currentUserId,
    CreateProjectRequest request
)
```

Transaction:

```text
@Transactional
```

Flow:

```text
load creator User
    ↓
create Project
    ↓
save Project
    ↓
create ProjectMember(role OWNER)
    ↓
save membership
    ↓
return response
```

If OWNER membership save fails:

```text
whole DB transaction rolls back
```

DB partial unique index enforces max one OWNER.

---

# 27. ProjectService.listMyProjects

Suggested signature:

```java
PageResponse<ProjectResponse> listMyProjects(
    UUID currentUserId,
    ProjectRole role,
    Pageable pageable
)
```

Transaction:

```text
@Transactional(readOnly = true)
```

Normal USER:

```text
only membership projects
```

ADMIN on this endpoint:

```text
still "my projects"
```

All-project admin listing is separate.

---

# 28. ProjectService.getProject

Suggested signature:

```java
ProjectResponse getProject(
    UUID projectId,
    CustomUserPrincipal principal
)
```

Flow:

```text
ProjectAuthorizationService.requireProjectAccess
    ↓
map response with currentUserRole
```

ADMIN may have:

```text
currentUserRole = null
```

or an explicit API representation selected consistently by mapper.

No fake membership should be created.

---

# 29. ProjectService.updateProject

Suggested signature:

```java
ProjectResponse updateProject(
    UUID projectId,
    UpdateProjectRequest request,
    CustomUserPrincipal principal
)
```

Flow:

```text
requireOwner
    ↓
update name/description
    ↓
save via persistence context
```

Transaction:

```text
@Transactional
```

Errors:

```text
PROJECT_NOT_FOUND
PROJECT_MANAGEMENT_FORBIDDEN
VALIDATION_ERROR
```

---

# 30. ProjectService.deleteProject

Suggested signature:

```java
void deleteProject(
    UUID projectId,
    CustomUserPrincipal principal
)
```

Authorization:

```text
OWNER or ADMIN
```

Existing Core baseline:

```text
Hard delete Project
+
delete MinIO objects
+
DB relational cascade
```

Flow baseline:

```text
requireOwner
    ↓
DocumentRepository.findStorageKeysByProjectId
    ↓
StorageService.delete project objects
    ↓
ProjectRepository.delete(project)
```

Important:

```text
MinIO is outside PostgreSQL transaction.
```

If storage operation fails:

```text
abort DB deletion
throw STORAGE_SERVICE_UNAVAILABLE / PROJECT_DELETE_FAILED
```

If storage deletion succeeds but DB deletion fails:

```text
metadata may remain while binaries are missing
```

This is a known consistency risk of the existing synchronous hard-delete baseline.

Core v1 must at minimum:

```text
log failure with project/document IDs
surface PROJECT_DELETE_FAILED
provide retry/repair path operationally
```

A stronger outbox/tombstone design may be introduced later, but is not silently added here.

---

# 31. ProjectMemberService

Responsibilities:

```text
list members
remove member
leave project
```

Dependencies:

```text
ProjectMemberRepository
ProjectAuthorizationService
ProjectRepository
```

---

# 32. ProjectMemberService.listMembers

Suggested signature:

```java
PageResponse<ProjectMemberResponse> listMembers(
    UUID projectId,
    CustomUserPrincipal principal,
    Pageable pageable
)
```

Authorization:

```text
MEMBER
OWNER
ADMIN
```

Flow:

```text
requireProjectAccess
    ↓
query members with User
    ↓
map
```

Transaction:

```text
@Transactional(readOnly = true)
```

---

# 33. ProjectMemberService.removeMember

Suggested signature:

```java
void removeMember(
    UUID projectId,
    UUID targetUserId,
    CustomUserPrincipal principal
)
```

Authorization:

```text
OWNER or ADMIN
```

Rules:

```text
OWNER cannot remove current project OWNER through this endpoint
OWNER cannot remove self if self is project OWNER
```

Flow:

```text
requireOwner
    ↓
find target membership
    ↓
if target role OWNER:
PROJECT_OWNER_REMOVAL_FORBIDDEN
    ↓
delete ProjectMember
```

Documents remain.

Transaction:

```text
@Transactional
```

---

# 34. ProjectMemberService.leaveProject

Suggested signature:

```java
void leaveProject(
    UUID projectId,
    UUID currentUserId
)
```

Flow:

```text
find membership
    ↓
if OWNER:
OWNER_CANNOT_LEAVE_PROJECT
    ↓
delete membership
```

Documents remain.

Transaction:

```text
@Transactional
```

---

# 35. InvitationService

Responsibilities:

```text
create invitation
list invitations
resend invitation
cancel invitation
accept invitation
```

Dependencies:

```text
ProjectAuthorizationService
ProjectInvitationRepository
ProjectMemberRepository
UserRepository
MailService
Clock
token generator/hash utility
```

---

# 36. InvitationService.createInvitation

Suggested signature:

```java
InvitationResponse createInvitation(
    UUID projectId,
    CreateInvitationRequest request,
    CustomUserPrincipal principal
)
```

Authorization:

```text
OWNER or ADMIN
```

Flow:

```text
requireOwner
    ↓
normalize email
    ↓
if email belongs to existing member:
PROJECT_MEMBER_ALREADY_EXISTS
    ↓
if PENDING invitation exists:
INVITATION_ALREADY_PENDING
    ↓
generate secure token
    ↓
hash token
    ↓
create PENDING invitation
    ↓
save
    ↓
MailService.sendProjectInvitation(raw token)
```

Email is outside DB transaction.

Baseline consistency approach:

```text
if email send fails during request:
treat request as failed
```

Implementation must avoid leaving a misleading successfully-sent state.

A practical Core v1 approach:

```text
save invitation
attempt mail
if mail fails:
rollback DB transaction if exception propagates before commit
```

Because external email cannot roll back, there is still a theoretical edge case where mail succeeds but DB commit fails.

Advanced outbox delivery is deferred.

Errors:

```text
PROJECT_MEMBER_ALREADY_EXISTS
INVITATION_ALREADY_PENDING
EMAIL_SERVICE_UNAVAILABLE
```

---

# 37. InvitationService.listInvitations

Suggested signature:

```java
PageResponse<InvitationResponse> listInvitations(
    UUID projectId,
    InvitationStatus status,
    CustomUserPrincipal principal,
    Pageable pageable
)
```

Authorization:

```text
OWNER or ADMIN
```

Transaction:

```text
@Transactional(readOnly = true)
```

---

# 38. InvitationService.resend

Suggested signature:

```java
InvitationResponse resend(
    UUID projectId,
    UUID invitationId,
    CustomUserPrincipal principal
)
```

Flow:

```text
requireOwner
    ↓
load invitation scoped to project
    ↓
status == PENDING?
    ↓
ensure email not already project member
    ↓
generate new token
    ↓
replace token hash
    ↓
reset expiresAt
    ↓
send email
```

Errors:

```text
INVITATION_NOT_FOUND
INVITATION_NOT_PENDING
PROJECT_MEMBER_ALREADY_EXISTS
EMAIL_SERVICE_UNAVAILABLE
```

---

# 39. InvitationService.cancel

Suggested signature:

```java
void cancel(
    UUID projectId,
    UUID invitationId,
    CustomUserPrincipal principal
)
```

Flow:

```text
requireOwner
    ↓
load invitation
    ↓
status == PENDING?
    ↓
status = CANCELLED
```

Transaction:

```text
@Transactional
```

No hard delete of invitation record.

---

# 40. InvitationService.accept

Suggested signature:

```java
AcceptInvitationResponse accept(
    String rawToken,
    CustomUserPrincipal principal
)
```

Transaction:

```text
@Transactional
```

Concurrency:

```text
PESSIMISTIC_WRITE on invitation
```

Flow:

```text
hash token
    ↓
find invitation FOR UPDATE
    ↓
status == PENDING?
    ↓
expiresAt > now?
    ↓
load current User
    ↓
normalized current user email == invitation email?
    ↓
membership absent?
    ↓
create ProjectMember(role MEMBER)
    ↓
status = ACCEPTED
acceptedAt = now
```

Errors:

```text
INVITATION_NOT_FOUND
INVITATION_NOT_PENDING
INVITATION_EXPIRED
INVITATION_EMAIL_MISMATCH
PROJECT_MEMBER_ALREADY_EXISTS
```

If expired during accept:

Core v1 may additionally set:

```text
status = EXPIRED
```

inside the same transaction before returning error.

That is a lifecycle implementation detail consistent with existing status model.

---

# 41. FolderService

Responsibilities:

```text
list folders
create folder
rename/move folder
delete empty folder
detect cycles
```

Dependencies:

```text
FolderRepository
DocumentRepository
ProjectAuthorizationService
FolderMapper
```

---

# 42. FolderService.listFolders

Suggested signature:

```java
List<FolderResponse> listFolders(
    UUID projectId,
    UUID parentId,
    CustomUserPrincipal principal
)
```

Authorization:

```text
MEMBER / OWNER / ADMIN
```

Transaction:

```text
@Transactional(readOnly = true)
```

If `parentId` supplied:

```text
verify parent belongs to project
```

---

# 43. FolderService.createFolder

Suggested signature:

```java
FolderResponse createFolder(
    UUID projectId,
    CreateFolderRequest request,
    CustomUserPrincipal principal
)
```

Authorization:

```text
OWNER / ADMIN
```

Flow:

```text
requireOwner
    ↓
if parentId:
load parent scoped to project
    ↓
check sibling name case-insensitive
    ↓
create Folder
```

Transaction:

```text
@Transactional
```

Errors:

```text
PARENT_FOLDER_NOT_FOUND
FOLDER_NAME_ALREADY_EXISTS
```

---

# 44. FolderService.updateFolder

Suggested signature:

```java
FolderResponse updateFolder(
    UUID projectId,
    UUID folderId,
    UpdateFolderRequest request,
    CustomUserPrincipal principal
)
```

May update:

```text
name
parent
```

Flow:

```text
requireOwner
    ↓
load folder scoped to project
    ↓
resolve new parent
    ↓
check not self
    ↓
walk ancestors to prevent cycle
    ↓
check sibling name excluding self
    ↓
update
```

Errors:

```text
FOLDER_NOT_FOUND
PARENT_FOLDER_NOT_FOUND
FOLDER_CYCLE_DETECTED
FOLDER_NAME_ALREADY_EXISTS
```

---

# 45. FolderService.deleteFolder

Suggested signature:

```java
void deleteFolder(
    UUID projectId,
    UUID folderId,
    CustomUserPrincipal principal
)
```

Flow:

```text
requireOwner
    ↓
load folder
    ↓
exists child folder?
    ↓
exists document in folder?
    ↓
if yes:
FOLDER_NOT_EMPTY
    ↓
delete folder
```

Transaction:

```text
@Transactional
```

---

# 46. CategoryService

Responsibilities:

```text
list categories
create category
rename category
delete unused category
```

Dependencies:

```text
CategoryRepository
DocumentRepository
ProjectAuthorizationService
```

---

# 47. CategoryService.create

Authorization:

```text
OWNER / ADMIN
```

Flow:

```text
requireOwner
    ↓
normalize/trim name
    ↓
check same project case-insensitive duplicate
    ↓
save
```

Errors:

```text
CATEGORY_NAME_ALREADY_EXISTS
```

---

# 48. CategoryService.delete

Flow:

```text
requireOwner
    ↓
load category scoped to project
    ↓
DocumentRepository.existsByCategoryId
    ↓
if in use:
CATEGORY_IN_USE
    ↓
delete
```

Transaction:

```text
@Transactional
```

---

# 49. TagService

Responsibilities:

```text
list/search tags
create tag
rename tag
delete tag
```

Dependencies:

```text
TagRepository
ProjectAuthorizationService
```

---

# 50. TagService.create

Authorization:

```text
MEMBER / OWNER / ADMIN
```

Flow:

```text
requireProjectAccess
    ↓
check project-level case-insensitive duplicate
    ↓
save
```

Error:

```text
TAG_NAME_ALREADY_EXISTS
```

---

# 51. TagService.rename

Authorization:

```text
OWNER / ADMIN
```

Flow:

```text
requireOwner
    ↓
load tag scoped to project
    ↓
check duplicate excluding self
    ↓
rename
```

---

# 52. TagService.delete

Authorization:

```text
OWNER / ADMIN
```

Flow:

```text
requireOwner
    ↓
load tag
    ↓
delete tag
```

DB cascade removes:

```text
DocumentTag
```

Documents remain.

---

# 53. DocumentAuthorizationService

Responsibilities:

```text
requireReadPermission
requireModifyPermission
```

Dependencies:

```text
ProjectAuthorizationService
```

Suggested methods:

```java
void requireReadPermission(
    Document document,
    CustomUserPrincipal principal
)

void requireModifyPermission(
    Document document,
    CustomUserPrincipal principal
)
```

---

# 54. DocumentAuthorizationService.requireReadPermission

Logic:

```text
if ADMIN:
allow

require ProjectMember(document.projectId)
```

All current project members may read.

---

# 55. DocumentAuthorizationService.requireModifyPermission

Logic:

```text
if ADMIN:
allow

membership = requireProjectAccess

if OWNER:
allow

if MEMBER
and document.uploadedBy.id == current user:
allow

else:
DOCUMENT_MODIFICATION_FORBIDDEN
```

This rule applies to:

```text
metadata update
move folder
change category/tags
rename
delete
```

---

# 56. FileValidationService

Responsibilities:

```text
validate not empty
validate extension
validate MIME
derive FileKind
validate size against config
validate batch count
```

Dependencies:

```text
UploadProperties
```

Suggested result:

```java
ValidatedFile validate(MultipartFile file)
```

`ValidatedFile` may contain:

```text
originalFilename
extension
mimeType
fileKind
sizeBytes
```

---

# 57. File Type Validation

Allowed types remain:

Documents:

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

Images:

```text
JPG
JPEG
PNG
GIF
SVG
BMP
```

Videos:

```text
MP4
MOV
AVI
```

Validation should not trust extension alone.

Check:

```text
extension
+
detected/reported MIME compatibility
```

Exact content-sniffing library is an implementation choice.

---

# 58. DocumentService

Responsibilities:

```text
upload
batch upload
get metadata/detail
update metadata
preview
download
hard delete
```

Dependencies:

```text
DocumentRepository
DocumentTagRepository
FolderRepository
CategoryRepository
TagRepository
ProjectAuthorizationService
DocumentAuthorizationService
FileValidationService
StorageService
DocumentMapper
```

Search is delegated to:

```text
DocumentSearchService
```

---

# 59. DocumentService.upload

Suggested signature:

```java
DocumentResponse upload(
    UUID projectId,
    MultipartFile file,
    DocumentMetadataRequest metadata,
    CustomUserPrincipal principal
)
```

Flow:

```text
require project access
    ↓
FileValidationService.validate
    ↓
resolve folder scoped to project
    ↓
resolve category scoped to project
    ↓
resolve all tags scoped to project
    ↓
generate Document UUID/storage key
    ↓
StorageService.upload
    ↓
save Document metadata
    ↓
save DocumentTag rows
    ↓
return response
```

Transaction:

DB metadata work:

```text
@Transactional
```

but MinIO remains external.

Compensation:

```text
if Storage upload succeeded
and DB persistence fails:
attempt StorageService.delete(storageKey)
```

If compensation also fails:

```text
log orphan object
throw FILE_UPLOAD_FAILED
```

Operational cleanup may be required.

---

# 60. Document Upload Ordering

Existing baseline favors:

```text
1. Validate
2. Upload object
3. Persist DB metadata
4. Compensate storage if DB fails
```

Reason:

Database should not commit a Document pointing to an object that never uploaded.

Residual failure mode:

```text
DB failure
+
compensation delete failure
→ orphan MinIO object
```

This is safer than exposing a DB record with no binary to users.

---

# 61. DocumentService.batchUpload

Suggested signature:

```java
BatchDocumentUploadResponse batchUpload(...)
```

Core REST baseline:

```text
common folder/category/tag metadata
for all files
```

Flow:

```text
validate project access
    ↓
validate batch count
    ↓
resolve common metadata
    ↓
for each file:
 validate
 upload
 save metadata
```

Atomicity across the entire batch has not been fixed as a business rule.

Recommended Core v1 baseline:

```text
treat request as all-or-fail at application level where practical
```

If a later item fails:

```text
rollback DB transaction
attempt delete already-uploaded objects
```

This keeps batch behavior predictable.

Because MinIO is external, compensation may still fail and must be logged.

---

# 62. DocumentService.getDocument

Suggested signature:

```java
DocumentResponse getDocument(
    UUID documentId,
    CustomUserPrincipal principal
)
```

Flow:

```text
load document detail
    ↓
requireReadPermission
    ↓
load tags
    ↓
map response
```

Transaction:

```text
@Transactional(readOnly = true)
```

---

# 63. DocumentService.updateMetadata

Suggested signature:

```java
DocumentResponse updateMetadata(
    UUID documentId,
    UpdateDocumentRequest request,
    CustomUserPrincipal principal
)
```

Flow:

```text
load document
    ↓
requireModifyPermission
    ↓
resolve folder/category/tags in same project
    ↓
update displayName/description/folder/category
    ↓
replace DocumentTag relationships
    ↓
return response
```

Transaction:

```text
@Transactional
```

Rename affects:

```text
displayName
```

not:

```text
storageKey
```

---

# 64. DocumentService.preview

Suggested internal result:

```text
FileDelivery
```

containing:

```text
InputStream/resource
mimeType
displayFilename
contentLength
disposition INLINE
```

Flow:

```text
load document
    ↓
requireReadPermission
    ↓
check preview-supported extension/type
    ↓
StorageService.get
```

Errors:

```text
DOCUMENT_NOT_FOUND
PROJECT_ACCESS_FORBIDDEN
PREVIEW_NOT_SUPPORTED
STORAGE_SERVICE_UNAVAILABLE
```

Controller turns `FileDelivery` into HTTP response.

Service should not return `ResponseEntity`.

---

# 65. DocumentService.download

Same security flow:

```text
load
    ↓
requireReadPermission
    ↓
StorageService.get
```

Result disposition:

```text
ATTACHMENT
```

MinIO remains private.

---

# 66. DocumentService.delete

Suggested signature:

```java
void delete(
    UUID documentId,
    CustomUserPrincipal principal
)
```

Existing baseline:

```text
Hard delete
```

Flow:

```text
load document
    ↓
requireModifyPermission
    ↓
StorageService.delete(storageKey)
    ↓
DocumentRepository.delete
    ↓
DocumentTag DB cascade
```

Transaction:

```text
DB delete transaction
```

Known consistency risk:

```text
storage delete succeeds
DB delete fails
→ metadata remains but binary is gone
```

Core v1 must:

```text
log failure clearly
return DOCUMENT_DELETE_FAILED
support retry/repair operationally
```

A stronger deferred-delete/outbox design is a future enhancement and is not silently introduced here.

---

# 67. DocumentSearchService

Responsibilities:

```text
metadata search
filter
pagination
sorting
```

Dependencies:

```text
DocumentRepository
ProjectAuthorizationService
DocumentMapper
DocumentTagRepository where needed
```

Suggested signature:

```java
PageResponse<DocumentSummaryResponse> search(
    UUID projectId,
    DocumentSearchCriteria criteria,
    CustomUserPrincipal principal,
    Pageable pageable
)
```

---

# 68. DocumentSearchCriteria

Internal/application model:

```text
q
folderId
categoryId
tagId
fileKind
uploadedBy
createdFrom
createdTo
```

Project ID is always supplied separately and always included in DB predicate.

---

# 69. DocumentSearchService.search

Flow:

```text
require project access
    ↓
validate filter IDs if necessary
    ↓
build Document Specification
    ↓
apply sort whitelist
    ↓
DocumentRepository.findAll
    ↓
map summaries
```

Transaction:

```text
@Transactional(readOnly = true)
```

No content extraction.

No vector search.

No AI.

---

# 70. StorageService

Infrastructure abstraction.

Suggested interface concept:

```java
public interface StorageService {

    StoredObject upload(
        String storageKey,
        InputStream data,
        long size,
        String mimeType
    );

    StoredResource get(String storageKey);

    void delete(String storageKey);

    void deleteAll(Collection<String> storageKeys);
}
```

StorageService knows:

```text
object storage
```

It does not know:

```text
project role
document ownership
JWT
```

---

# 71. MinioStorageService

Implementation of:

```text
StorageService
```

Responsibilities:

```text
MinIO SDK
bucket
upload
download
delete
presigned URL if selected later
translate SDK exceptions
```

Infrastructure exceptions should be converted to an internal storage exception that application service maps to:

```text
STORAGE_SERVICE_UNAVAILABLE
FILE_UPLOAD_FAILED
DOCUMENT_DELETE_FAILED
```

as appropriate.

---

# 72. MailService

Suggested interface:

```java
public interface MailService {

    void sendEmailVerificationOtp(
        String recipientEmail,
        String otp,
        Instant expiresAt
    );

    void sendProjectInvitation(
        String recipientEmail,
        String inviterDisplayName,
        String projectName,
        String invitationUrl,
        Instant expiresAt
    );
}
```

MailService should not know:

```text
ProjectInvitationRepository
membership rules
```

EmailVerificationService and InvitationService build business context.

Mail implementation delivers it.

---

# 73. SmtpMailService

Responsibilities:

```text
Gmail SMTP integration
smtp.gmail.com:587
STARTTLS
Gmail/Google Workspace App Password from environment
template rendering
delivery
translate mail exceptions
```

Errors:

```text
EMAIL_SERVICE_UNAVAILABLE
```

Raw invitation token should only appear as part of generated invite link. Raw verification OTP should only appear in the verification email payload. Neither may be logged. Gmail SMTP credentials/App Password must never be logged.

---

# 74. Mapper Responsibility

Service may use mapper:

```text
UserMapper
ProjectMapper
InvitationMapper
FolderMapper
DocumentMapper
```

Mapper should:

```text
Entity → Response DTO
```

and possibly:

```text
Request → new Entity
```

for simple field mapping.

Mapper must not:

```text
perform authorization
call repository
call StorageService
```

---

# 75. Cross-Service Dependency Rules

Recommended dependencies:

```text
AuthService
→ UserRepository
→ RefreshSessionService
→ JwtService

ProjectService
→ ProjectAuthorizationService
→ repositories
→ StorageService for delete orchestration

InvitationService
→ ProjectAuthorizationService
→ MailService

FolderService
→ ProjectAuthorizationService

CategoryService
→ ProjectAuthorizationService

TagService
→ ProjectAuthorizationService

DocumentService
→ ProjectAuthorizationService
→ DocumentAuthorizationService
→ FileValidationService
→ StorageService

DocumentSearchService
→ ProjectAuthorizationService
```

---

# 76. Avoiding Circular Dependencies

Avoid:

```text
ProjectService
→ DocumentService
→ ProjectService
```

Prefer narrower components:

```text
ProjectAuthorizationService
DocumentRepository projection
StorageService
```

Likewise:

```text
UserService
```

should use dependency repositories for delete checks rather than importing full business services unless necessary.

Do not use:

```text
@Lazy
```

as the primary architecture fix.

---

# 77. Business Exception Policy

Service throws:

```text
BusinessException(ErrorCode)
ResourceNotFoundException
ForbiddenOperationException
```

or equivalent typed hierarchy.

Examples:

```text
PROJECT_NOT_FOUND
PROJECT_ACCESS_FORBIDDEN
FOLDER_NOT_EMPTY
CATEGORY_IN_USE
DOCUMENT_MODIFICATION_FORBIDDEN
```

Controller does not catch each business exception.

`GlobalExceptionHandler` maps them.

---

# 78. Repository Integrity Race Translation

Service performs pre-checks for friendly UX.

Database remains final integrity protection.

Example:

```text
TagService checks "jwt" not exists
two requests race
DB unique index rejects one
```

Catch:

```text
DataIntegrityViolationException
```

and translate to:

```text
TAG_NAME_ALREADY_EXISTS
```

where constraint context is known.

---

# 79. Read-Only Transactions

Use:

```java
@Transactional(readOnly = true)
```

for:

```text
getCurrentUser
list projects
get project
list members
list invitations
list folders
list categories
list tags
get document
search documents
```

This communicates intent and may help persistence optimization.

---

# 80. Write Transactions

Use:

```java
@Transactional
```

for:

```text
register
login session persistence
logout revoke
change password
user status change
project create/update/delete DB phase
remove/leave member
invitation lifecycle
folder create/update/delete
category create/update/delete
tag create/update/delete
document metadata write/delete DB phase
```

---

# 81. Service Method Input Rule

Services should not receive raw HTTP objects such as:

```text
HttpServletRequest
HttpServletResponse
ResponseEntity
```

Exceptions:

```text
MultipartFile
```

may be accepted by DocumentService as a Spring upload abstraction, though a custom upload input model can later decouple it further.

---

# 82. Service Method Output Rule

Prefer:

```text
Response DTO
PageResponse
application result object
```

Do not return:

```text
ResponseEntity
```

Example:

```text
AuthService.login
→ LoginResult

Controller
→ builds JSON + refresh cookie
```

---

# 83. Service-Level Validation

Three validation levels:

```text
1. Request validation
Bean Validation

2. Business validation
Service

3. Integrity validation
PostgreSQL constraints
```

Example:

```text
@NotBlank folder name
→ request level

parent belongs to same project
→ service level

composite FK
→ DB level
```

All three remain useful.

---

# 84. Clock Usage

Inject:

```java
Clock
```

where time affects business logic.

Services:

```text
RefreshSessionService
InvitationService
JwtService
```

Benefits:

```text
deterministic tests
consistent expiration decisions
```

---

# 85. Logging Rules

Service logs meaningful events:

```text
project deletion failure
upload compensation failure
storage delete failure
mail send failure
invitation accept conflict
user disable
```

Do not log:

```text
password
password hash
access token
raw refresh token
raw invitation token
SMTP password
MinIO secret
```

---

# 86. Idempotency Considerations

Explicit Core v1 behaviors:

Logout:

```text
effectively idempotent
```

Delete document/project:

```text
not guaranteed idempotent beyond normal REST semantics
```

Invitation resend:

```text
changes token + expiry each time
```

Invitation accept:

```text
only first valid acceptance succeeds
```

No generic idempotency-key infrastructure is added.

---

# 87. Service Tests – Auth/User

Unit tests should cover:

```text
register normalizes email
register rejects duplicate
register always creates USER

login generic invalid credentials
login blocks disabled account

refresh rejects revoked/expired
logout revokes session

password change checks current password
password change hashes new password
password change revokes sessions

admin disable revokes sessions
```

---

# 88. Service Tests – Project/Membership

```text
create project creates exactly one OWNER
project creation rolls back if membership creation fails

member can read project
member cannot update project
owner can update

owner remove member
owner cannot remove OWNER
member leave succeeds
owner leave fails

member removal preserves documents
```

---

# 89. Service Tests – Invitation

```text
owner can invite
member cannot invite

cannot invite existing member
cannot duplicate pending invite

resend changes token/expiry
cancel pending works

accept:
PENDING only
not expired
email matches
not already member
creates MEMBER
marks ACCEPTED

concurrent acceptance only succeeds once
```

---

# 90. Service Tests – Folder/Category/Tag

Folder:

```text
owner create
member cannot manage
duplicate sibling rejected
cross-project parent rejected
cycle rejected
non-empty delete rejected
```

Category:

```text
duplicate rejected
in-use delete rejected
```

Tag:

```text
member create allowed
member rename/delete forbidden
owner rename/delete allowed
delete preserves documents
```

---

# 91. Service Tests – Document

```text
member upload allowed
cross-project folder rejected
cross-project category rejected
cross-project tag rejected

member read all project documents
member modifies own
member cannot modify another's
owner modifies all

upload DB failure triggers storage compensation
unsupported file rejected
too-large file rejected

preview unsupported type rejected

member delete own allowed
member delete other's rejected
owner delete any allowed
```

---

# 92. Integration Tests

Important service integration tests should use:

```text
PostgreSQL Testcontainers
MinIO Testcontainer where practical
Redis Testcontainer for OTP verification
```

Especially for:

```text
project create transaction
invitation accept transaction
cross-project constraints
document upload compensation
project/document hard delete
```

---

# 93. Services Deliberately Not Added

Core v1 does not contain:

```text
AIService
EmbeddingService
RagService
TranscriptionService
DocumentVersionService
AuditService
QuotaService
ShareLinkService
CommentService
ApprovalService
PasswordResetService
OAuthService
MfaService
OtpLoginService
```

These require future specification decisions.

---

# 94. Service Package Summary

```text
auth/service
├── AuthService
├── EmailVerificationService
├── OtpService
└── RefreshSessionService

auth/port
└── OtpStore

user/service
└── UserService

project/service
├── ProjectService
├── ProjectMemberService
└── ProjectAuthorizationService

invitation/service
└── InvitationService

folder/service
└── FolderService

category/service
└── CategoryService

tag/service
└── TagService

document/service
├── DocumentService
├── DocumentSearchService
├── DocumentAuthorizationService
└── FileValidationService

security/service
└── CurrentUserService

storage/service
├── StorageService
└── MinioStorageService

mail/service
├── MailService
└── SmtpMailService

redis/otp
└── RedisOtpStore
```

---

# 95. Main Method Contract Summary

## Auth

```text
register
verifyEmail
resendVerificationOtp
login
refresh
logout
```

## User

```text
getCurrentUser
updateProfile
changePassword
listUsers
getUser
updateStatus
deleteUser
```

## Project

```text
createProject
listMyProjects
getProject
updateProject
deleteProject
listAllProjects
```

## Membership

```text
listMembers
removeMember
leaveProject
```

## Invitation

```text
createInvitation
listInvitations
resend
cancel
accept
```

## Folder

```text
listFolders
createFolder
updateFolder
deleteFolder
```

## Category

```text
listCategories
createCategory
renameCategory
deleteCategory
```

## Tag

```text
listTags
createTag
renameTag
deleteTag
```

## Document

```text
upload
batchUpload
getDocument
updateMetadata
preview
download
delete
search
```

---

# 96. Core Orchestration Examples

## Email Verification

```text
AuthService.register
    ↓
UserRepository
    ↓
EmailVerificationService
    ├── OtpStore → Redis
    └── MailService → Gmail SMTP
```

Verification:

```text
EmailVerificationService
    ↓
UserRepository
    ↓
OtpStore.verify
    ↓
users.email_verified_at = now
```

## Upload

```text
DocumentService
    ↓
ProjectAuthorizationService
    ↓
FileValidationService
    ↓
Folder/Category/Tag repositories
    ↓
StorageService
    ↓
DocumentRepository
    ↓
DocumentTagRepository
```

## Invite

```text
InvitationService
    ↓
ProjectAuthorizationService
    ↓
ProjectMemberRepository
    ↓
ProjectInvitationRepository
    ↓
MailService
```

## Project Delete

```text
ProjectService
    ↓
ProjectAuthorizationService
    ↓
DocumentRepository storage-key projection
    ↓
StorageService
    ↓
ProjectRepository
```

---

# 97. Known Core v1 Consistency Risks

Because hard-delete and upload span PostgreSQL + MinIO:

```text
upload:
MinIO success
DB fail
→ compensation required

delete:
MinIO success
DB fail
→ metadata may point to missing binary
```

Because registration verification spans PostgreSQL + Redis + Gmail SMTP:

```text
User DB state
+ Redis OTP state
+ Gmail delivery
```

can fail independently. Core v1 uses synchronous coordination, best-effort Redis cleanup, retry/resend capability, and persistent `email_verified_at` as the final verification result.

Because invitation spans PostgreSQL + Email:

```text
email success
DB commit fail
or
DB state prepared
email fail
```

Core v1 uses synchronous coordination plus:

```text
transactions
compensation where possible
logging
clear failure response
```

If reliability requirements rise, the natural next step is:

```text
outbox / async worker / retryable job model
```

but that is not required yet.

---

# 98. Deliberately Open Implementation Details

Not silently fixed in this document:

```text
exact class constructor style
Lombok usage
MapStruct usage
exact MIME-detection library
exact storage retry count
exact email retry count
exact Redis client tuning
async email sending
outbox implementation
batch upload partial-success policy beyond recommended all-or-fail baseline
exact logging framework configuration
```

Where recommendations are given, they remain implementation baselines rather than new business rules.

---

# 99. Definition of Done

Service Layer design is complete when implementation satisfies:

```text
Controller does not call Repository directly.

Service owns business logic.

Email verification is handled through EmailVerificationService.

OtpStore isolates Redis and raw OTP is not persisted in PostgreSQL.

Gmail SMTP is accessed only through MailService.

Project authorization is centralized.

Document ownership authorization is centralized.

Transactions are Service-level.

Upload validates project/folder/category/tag boundaries.

Member leaving/removal never deletes existing documents.

Invitation acceptance is concurrency-safe.

Folder cycle detection exists.

Category cannot be deleted while in use.

Member may create tags but not rename/delete shared tags.

MinIO is accessed only through StorageService.

Email is accessed only through MailService.

Redis OTP state is accessed only through OtpStore/RedisOtpStore.

RefreshSession remains PostgreSQL-backed.

DB + external system inconsistency is handled explicitly.

Business errors are raised from service layer consistently.

No AI functionality is mixed into Core v1.
```

---

# Docker Runtime Dependencies

Local service dependencies:

```text
PostgreSQL container → postgres_data named volume
MinIO container      → minio_data named volume
Redis container      → OTP ephemeral state, no durable volume required
Gmail SMTP           → external service
```

Backend uses Docker network hostnames (`postgres`, `minio`, `redis`) rather than requiring host-installed PostgreSQL/MinIO/Redis.

Flyway migrations execute against the PostgreSQL container on backend startup.

---

# 100. Next Phase

Recommended next phase:

```text
Service Layer Detailed Design
        ↓
MinIO Integration Design
        ↓
Exception Handling Design
        ↓
OpenAPI Configuration
        ↓
Testing Strategy
        ↓
Implementation Plan
```

The immediate next artifact should be:

```text
KBase Core v1 – MinIO Integration Design
```

It should define:

```text
bucket strategy
object-key strategy
upload flow
download/preview flow
private object access
presigned URL decision
file streaming
delete flow
compensation
MinIO exception mapping
Docker/local configuration
production S3 compatibility considerations
```

without changing the existing document permission and hard-delete rules.
