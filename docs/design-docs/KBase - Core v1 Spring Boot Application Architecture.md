# KBase – Core v1
## Spring Boot Application Architecture + Module / Package Structure

**Version:** Draft 2  
**Application Style:** Modular Monolith  
**Backend:** Java Spring Boot  
**Database:** PostgreSQL  
**Object Storage:** MinIO  
**OTP Store:** Redis  
**Email Delivery:** Gmail SMTP  
**API Documentation:** OpenAPI / Swagger  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa kiến trúc ứng dụng Spring Boot và cấu trúc module/package cho KBase Core v1.

Tài liệu này kế thừa trực tiếp từ:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design
- REST API Specification

Mục tiêu:

- Chốt cách tổ chức source code.
- Xác định boundary giữa các feature.
- Xác định responsibility của từng layer.
- Xác định dependency direction.
- Chuẩn hóa luồng Controller → Service → Repository.
- Tách business logic khỏi infrastructure.
- Chuẩn bị nền tảng cho JPA Entity Mapping, Repository Design, Spring Security + JWT, MinIO, Email Invitation, Exception Handling, Testing và Implementation Plan.

---

# 2. Architecture Style

Core v1 sử dụng:

```text
Modular Monolith
```

Không sử dụng microservices ở giai đoạn hiện tại.

Lý do:

- Core domain hiện tại vẫn gắn kết chặt.
- Transaction giữa Project / Membership / Invitation / Document metadata dễ quản lý hơn.
- Deployment và debug đơn giản.
- Phù hợp quá trình học và phát triển MVP.
- Không tạo network complexity không cần thiết.
- Vẫn có thể tách AI service riêng về sau.

High-level:

```text
Frontend
   │
   │ REST
   ▼
┌───────────────────────────────────┐
│          Spring Boot App          │
│                                   │
│ Auth                              │
│ User                              │
│ Project                           │
│ Membership                        │
│ Invitation                        │
│ Folder                            │
│ Category                          │
│ Tag                               │
│ Document                          │
│ Search                            │
│                                   │
│ Security                          │
│ Storage                           │
│ Email Verification / OTP          │
│ Email                             │
└───────┬──────────┬──────────┬─────┘
        │          │          │
        ▼          ▼          ▼
 PostgreSQL      MinIO      Redis
        │
        └────────────────► Gmail SMTP
```

---

# 3. Feature-first Package Structure

Không nên tổ chức toàn bộ ứng dụng theo kiểu root-level:

```text
controller/
service/
repository/
entity/
dto/
```

Khi hệ thống lớn lên, code của cùng một feature sẽ bị tách xa nhau và service dễ phụ thuộc lẫn lộn.

KBase nên ưu tiên:

```text
feature-first
```

Ví dụ:

```text
auth/
user/
project/
document/
```

Mỗi feature tự chứa phần lớn thành phần của nó.

---

# 4. Root Package

Baseline proposal:

```text
com.kbase
```

Ví dụ:

```text
src/main/java/com/kbase/
```

Đây là naming baseline, không phải business rule.

---

# 5. Recommended Root Structure

```text
com.kbase
│
├── KBaseApplication.java
│
├── auth
├── user
├── project
├── invitation
├── folder
├── category
├── tag
├── document
│
├── security
├── storage
├── mail
├── redis
│
├── shared
└── config
```

Nhóm business features:

```text
auth
user
project
invitation
folder
category
tag
document
```

Nhóm infrastructure / cross-cutting:

```text
security
storage
mail
redis
shared
config
```

---

# 6. Dependency Principle

Practical flow:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

External systems:

```text
Service
   ↓
StorageService
   ↓
MinIO
```

```text
Service
   ↓
MailService
   ↓
Gmail SMTP
```

```text
EmailVerificationService
   ↓
OtpStore
   ↓
Redis
```

Controller không gọi Repository trực tiếp.

---

# 7. Controller Responsibility

Controller chỉ nên xử lý:

```text
HTTP request mapping
Request DTO
Bean Validation
Authentication principal extraction
Calling service
HTTP response mapping
```

Controller không nên chứa:

```text
business rule
JPA query
MinIO logic
JWT parsing logic
resource ownership logic
transaction orchestration
```

---

# 8. Service Responsibility

Service là nơi orchestration và business logic chính.

Ví dụ:

```text
ProjectService
- createProject
- updateProject
- deleteProject

ProjectMemberService
- removeMember
- leaveProject

InvitationService
- invite
- resend
- cancel
- accept

DocumentService
- upload
- updateMetadata
- delete
```

Service chịu trách nhiệm:

```text
authorization/business permission
transaction
business validation
repository coordination
external service coordination
```

---

# 9. Repository Responsibility

Repository chỉ xử lý persistence/query.

Ví dụ:

```text
findByEmail
existsByProjectIdAndUserId
findProjectMembership
findDocumentsByFilters
```

Repository không nên:

```text
send email
check JWT
call MinIO
build HTTP response
```

---

# 10. DTO Responsibility

Không expose JPA Entity trực tiếp ra REST API.

Dùng:

```text
Request DTO
Response DTO
```

Lợi ích:

- Không leak internal fields.
- Tránh lazy-loading serialization issue.
- Không expose password/token/storage key.
- API contract độc lập hơn với database.
- Dễ version API.

---

# 11. Shared Package

`shared` chỉ chứa code thực sự dùng chung.

```text
shared/
├── exception
├── response
├── pagination
├── validation
├── util
└── constants
```

Không biến `shared` thành nơi chứa mọi code không biết đặt ở đâu.

---

# 12. Proposed Full Package Tree

```text
com.kbase
│
├── KBaseApplication.java
│
├── auth
│   ├── controller
│   │   └── AuthController.java
│   ├── dto
│   │   ├── request
│   │   │   ├── RegisterRequest.java
│   │   │   ├── VerifyEmailRequest.java
│   │   │   ├── ResendVerificationOtpRequest.java
│   │   │   └── LoginRequest.java
│   │   └── response
│   │       ├── EmailVerificationResponse.java
│   │       ├── LoginResponse.java
│   │       └── AccessTokenResponse.java
│   ├── service
│   │   ├── AuthService.java
│   │   ├── EmailVerificationService.java
│   │   ├── OtpService.java
│   │   └── RefreshSessionService.java
│   ├── port
│   │   └── OtpStore.java
│   ├── entity
│   │   └── RefreshSession.java
│   └── repository
│       └── RefreshSessionRepository.java
│
├── user
│   ├── controller
│   │   ├── UserController.java
│   │   └── AdminUserController.java
│   ├── dto
│   │   ├── request
│   │   │   ├── UpdateProfileRequest.java
│   │   │   ├── ChangePasswordRequest.java
│   │   │   └── UpdateUserStatusRequest.java
│   │   └── response
│   │       └── UserResponse.java
│   ├── entity
│   │   └── User.java
│   ├── enums
│   │   ├── SystemRole.java
│   │   └── UserStatus.java
│   ├── repository
│   │   └── UserRepository.java
│   ├── service
│   │   └── UserService.java
│   └── mapper
│       └── UserMapper.java
│
├── project
│   ├── controller
│   │   ├── ProjectController.java
│   │   ├── AdminProjectController.java
│   │   └── ProjectMemberController.java
│   ├── dto
│   │   ├── request
│   │   │   ├── CreateProjectRequest.java
│   │   │   └── UpdateProjectRequest.java
│   │   └── response
│   │       ├── ProjectResponse.java
│   │       └── ProjectMemberResponse.java
│   ├── entity
│   │   ├── Project.java
│   │   └── ProjectMember.java
│   ├── enums
│   │   └── ProjectRole.java
│   ├── repository
│   │   ├── ProjectRepository.java
│   │   └── ProjectMemberRepository.java
│   ├── service
│   │   ├── ProjectService.java
│   │   ├── ProjectMemberService.java
│   │   └── ProjectAuthorizationService.java
│   └── mapper
│       └── ProjectMapper.java
│
├── invitation
│   ├── controller
│   │   ├── ProjectInvitationController.java
│   │   └── InvitationController.java
│   ├── dto
│   │   ├── request
│   │   │   ├── CreateInvitationRequest.java
│   │   │   └── AcceptInvitationRequest.java
│   │   └── response
│   │       ├── InvitationResponse.java
│   │       └── AcceptInvitationResponse.java
│   ├── entity
│   │   └── ProjectInvitation.java
│   ├── enums
│   │   └── InvitationStatus.java
│   ├── repository
│   │   └── ProjectInvitationRepository.java
│   ├── service
│   │   └── InvitationService.java
│   └── mapper
│       └── InvitationMapper.java
│
├── folder
│   ├── controller
│   │   └── FolderController.java
│   ├── dto
│   ├── entity
│   │   └── Folder.java
│   ├── repository
│   │   └── FolderRepository.java
│   ├── service
│   │   └── FolderService.java
│   └── mapper
│
├── category
│   ├── controller
│   │   └── CategoryController.java
│   ├── dto
│   ├── entity
│   │   └── Category.java
│   ├── repository
│   │   └── CategoryRepository.java
│   ├── service
│   │   └── CategoryService.java
│   └── mapper
│
├── tag
│   ├── controller
│   │   └── TagController.java
│   ├── dto
│   ├── entity
│   │   └── Tag.java
│   ├── repository
│   │   └── TagRepository.java
│   ├── service
│   │   └── TagService.java
│   └── mapper
│
├── document
│   ├── controller
│   │   └── DocumentController.java
│   ├── dto
│   │   ├── request
│   │   │   ├── DocumentMetadataRequest.java
│   │   │   └── UpdateDocumentRequest.java
│   │   └── response
│   │       ├── DocumentResponse.java
│   │       └── DocumentSummaryResponse.java
│   ├── entity
│   │   ├── Document.java
│   │   └── DocumentTag.java
│   ├── enums
│   │   └── FileKind.java
│   ├── repository
│   │   ├── DocumentRepository.java
│   │   └── DocumentTagRepository.java
│   ├── service
│   │   ├── DocumentService.java
│   │   ├── DocumentSearchService.java
│   │   ├── DocumentAuthorizationService.java
│   │   └── FileValidationService.java
│   └── mapper
│       └── DocumentMapper.java
│
├── security
│   ├── config
│   │   └── SecurityConfig.java
│   ├── jwt
│   │   ├── JwtService.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtProperties.java
│   ├── principal
│   │   ├── CustomUserPrincipal.java
│   │   └── CustomUserDetailsService.java
│   └── handler
│       ├── RestAuthenticationEntryPoint.java
│       └── RestAccessDeniedHandler.java
│
├── storage
│   ├── config
│   │   ├── MinioConfig.java
│   │   └── StorageProperties.java
│   ├── service
│   │   ├── StorageService.java
│   │   └── MinioStorageService.java
│   └── model
│       └── StoredObject.java
│
├── mail
│   ├── config
│   │   └── MailProperties.java
│   ├── service
│   │   ├── MailService.java
│   │   └── SmtpMailService.java
│   └── template
│       ├── email-verification-otp.html
│       └── project-invitation.html
│
├── redis
│   ├── config
│   │   └── RedisConfig.java
│   └── otp
│       └── RedisOtpStore.java
│
├── shared
│   ├── exception
│   │   ├── GlobalExceptionHandler.java
│   │   ├── BusinessException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── ForbiddenOperationException.java
│   │   └── ErrorCode.java
│   ├── response
│   │   └── ApiErrorResponse.java
│   ├── pagination
│   │   └── PageResponse.java
│   ├── validation
│   └── util
│
└── config
    ├── JpaConfig.java
    ├── OpenApiConfig.java
    └── ApplicationProperties.java
```

---

# 13. auth Module

Responsibility:

```text
register
email verification OTP
resend verification OTP
login
refresh token
logout
refresh session lifecycle
```

`auth` không sở hữu User entity. User entity thuộc `user`.

Main flow:

```text
AuthController
      ↓
AuthService / EmailVerificationService
      ├── UserRepository
      ├── PasswordEncoder
      ├── JwtService
      ├── RefreshSessionService
      ├── OtpStore → Redis
      └── MailService → Gmail SMTP
```

---

# 14. user Module

Responsibility:

```text
current profile
update profile
change password
admin user listing
enable/disable user
hard-delete validation
```

Entity:

```text
User
```

Enums:

```text
SystemRole
UserStatus
```

---

# 15. project Module

Responsibility:

```text
project CRUD
membership
project role
project authorization
```

Entities:

```text
Project
ProjectMember
```

Enum:

```text
ProjectRole
```

Main services:

```text
ProjectService
ProjectMemberService
ProjectAuthorizationService
```

---

# 16. ProjectAuthorizationService

Permission được sử dụng bởi nhiều feature:

```text
Folder
Category
Tag
Document
Invitation
```

Không nên duplicate logic membership/OWNER/ADMIN ở từng Service.

`ProjectAuthorizationService` có thể cung cấp:

```text
requireProjectMember(projectId, userId)
requireProjectOwner(projectId, userId)
isProjectOwner(...)
getMembership(...)
```

ADMIN override được xử lý nhất quán tại đây.

---

# 17. invitation Module

Responsibility:

```text
create invitation
send invitation
resend
cancel
accept
expiration validation
email match validation
```

Entity:

```text
ProjectInvitation
```

Dependencies:

```text
project
user
mail
```

Flow:

```text
ProjectInvitationController
        ↓
InvitationService
   ├── ProjectAuthorizationService
   ├── ProjectInvitationRepository
   ├── ProjectMemberRepository
   ├── UserRepository
   └── MailService
```

---

# 18. folder Module

Responsibility:

```text
folder create
rename
move
delete
folder cycle validation
folder hierarchy
```

FolderService xử lý:

```text
same-project parent
unique sibling name
self-parent prevention
descendant-cycle prevention
folder-empty check before delete
```

FolderService không thao tác MinIO.

---

# 19. category Module

Responsibility:

```text
create
rename
delete
list
```

Delete phải đảm bảo:

```text
category not in use
```

---

# 20. tag Module

Responsibility:

```text
create
rename
delete
list/search tags
```

Rules:

```text
MEMBER can create
OWNER/ADMIN can rename/delete
```

Delete Tag:

```text
DocumentTag rows cascade
Documents remain
```

---

# 21. document Module

Đây là module phức tạp nhất Core v1.

Responsibility:

```text
upload
batch upload
metadata update
browse
metadata search
preview
download
delete
document ownership authorization
file validation
DocumentTag synchronization
```

Entities:

```text
Document
DocumentTag
```

Enum:

```text
FileKind
```

Services:

```text
DocumentService
DocumentSearchService
DocumentAuthorizationService
FileValidationService
```

---

# 22. DocumentService

Upload flow:

```text
Controller
   ↓
DocumentService
   ↓
ProjectAuthorizationService
   ↓
FileValidationService
   ↓
validate folder/category/tags
   ↓
StorageService.upload
   ↓
DocumentRepository.save
   ↓
DocumentTagRepository
```

Failure compensation:

```text
Storage upload success
DB save fails
      ↓
try StorageService.delete uploaded object
```

---

# 23. DocumentAuthorizationService

Centralizes rules:

```text
READ:
ADMIN
or any project member

MODIFY / DELETE:
ADMIN
or OWNER
or MEMBER when uploaded_by_user_id == current user
```

---

# 24. DocumentSearchService

Core v1:

```text
metadata search
filters
pagination
sorting
```

Tách riêng để sau này có thể mở rộng PostgreSQL FTS hoặc AI/RAG mà không làm DocumentService phình to.

---

# 25. FileValidationService

Responsibility:

```text
non-empty file
extension
MIME
file kind
configured size limit
batch size
supported type
```

Ví dụ config:

```yaml
kbase:
  upload:
    document-max-size: 50MB
    image-max-size: 20MB
    video-max-size: 500MB
    max-batch-files: 10
```

Giá trị là configurable baseline.

---

# 26. storage Module

Business modules phụ thuộc abstraction:

```java
public interface StorageService {
    StoredObject upload(...);
    InputStream get(...);
    void delete(...);
}
```

Implementation:

```text
MinioStorageService
```

Architecture:

```text
DocumentService
      ↓
StorageService
      ↓
MinioStorageService
      ↓
MinIO SDK
```

Sau này có thể thay bằng `S3StorageService` mà không rewrite DocumentService.

---

# 27. mail Module

Business module dùng:

```text
MailService
```

Implementation:

```text
SmtpMailService
```

Flow:

```text
InvitationService
      ↓
MailService
      ↓
SmtpMailService
```

InvitationService không chứa SMTP configuration details.

---

# 28. security Module

Responsibilities:

```text
JWT validation
SecurityFilterChain
authentication principal
401 handler
403 handler
password encoder
```

Project-specific authorization nằm ở:

```text
ProjectAuthorizationService
DocumentAuthorizationService
```

Security filter không chứa project database business logic.

---

# 29. Security Request Flow

```text
HTTP Request
    ↓
JwtAuthenticationFilter
    ↓
Validate JWT
    ↓
Load active user
    ↓
SecurityContext
    ↓
Controller
    ↓
Service
    ↓
Project/Document authorization
```

JWT layer trả lời:

```text
Who is the user?
Is token valid?
```

Business authorization trả lời:

```text
Can this user modify this project/document?
```

---

# 30. Current User Principal

Recommended principal:

```text
CustomUserPrincipal
-------------------
userId
email
systemRole
status
```

Controller có thể nhận qua `@AuthenticationPrincipal` hoặc abstraction tương đương.

---

# 31. Exception Strategy

Business code throw typed/domain-oriented exceptions.

Ví dụ:

```text
ResourceNotFoundException
ForbiddenOperationException
BusinessException(ErrorCode)
```

Central handler:

```text
GlobalExceptionHandler
```

map sang:

```text
HTTP status
business error code
ApiErrorResponse
```

Service không trả `ResponseEntity`.

---

# 32. ErrorCode Enum

Centralize API business error codes:

```text
ErrorCode
```

Groups:

```text
AUTH_...
USER_...
PROJECT_...
INVITATION_...
FOLDER_...
CATEGORY_...
TAG_...
DOCUMENT_...
STORAGE_...
MAIL_...
```

Tránh scattered string literals.

---

# 33. Mapper Strategy

DTO conversion không nên nằm rải rác trong Controller.

Dùng:

```text
UserMapper
ProjectMapper
DocumentMapper
...
```

MapStruct là optional, không bắt buộc. Manual mapper đủ cho Core v1 nếu mapping đơn giản.

---

# 34. JPA Relationships

Ví dụ:

```text
ProjectMember
@ManyToOne User
@ManyToOne Project
```

```text
Document
@ManyToOne Project
@ManyToOne User uploadedBy
@ManyToOne Folder nullable
@ManyToOne Category nullable
```

Không dùng `CascadeType.ALL` mặc định. JPA cascade phải bám đúng lifecycle đã thiết kế ở Physical Database Design.

---

# 35. Fetch Strategy

Baseline:

```text
@ManyToOne → LAZY where practical
@OneToMany → LAZY
```

Không serialize Entity trực tiếp.

Dùng DTO projection, mapping hoặc explicit fetch để tránh:

```text
N+1 queries
recursive JSON
large accidental object graphs
```

---

# 36. Repository Pattern

Repositories giữ local theo feature:

```text
UserRepository
ProjectRepository
ProjectMemberRepository
ProjectInvitationRepository
FolderRepository
CategoryRepository
TagRepository
DocumentRepository
DocumentTagRepository
RefreshSessionRepository
```

Không cần tạo generic repository abstraction nằm trên Spring Data JPA nếu chưa có nhu cầu thực tế.

---

# 37. Query Design

Simple query:

```text
derived query methods
```

Complex metadata search:

```text
JPQL
Specification
Criteria
custom repository
```

Với filter document hiện tại:

```text
q
folder
category
tag
fileKind
uploadedBy
createdFrom
createdTo
```

baseline recommendation là Spring Data JPA Specification.

Đây là architectural baseline, không phải business rule.

---

# 38. Transaction Boundaries

Đặt `@Transactional` ở Service layer.

Ví dụ:

```text
ProjectService.createProject
```

atomic:

```text
create Project
+
create OWNER membership
```

```text
InvitationService.accept
```

atomic:

```text
create MEMBER
+
mark invitation ACCEPTED
```

```text
DocumentService.updateMetadata
```

atomic:

```text
update Document
+
replace DocumentTag relationships
```

Không đặt transaction orchestration ở Controller.

---

# 39. External Storage and Transactions

MinIO không tham gia PostgreSQL transaction.

`@Transactional` không biến DB + MinIO thành một ACID transaction.

Upload cần compensation:

```text
upload MinIO
    ↓
save DB
    ↓
DB error
    ↓
delete MinIO object
```

Core v1 có thể bắt đầu bằng:

```text
synchronous compensation
+
logging
```

Outbox/retry advanced có thể thêm sau nếu cần.

---

# 40. Avoiding Circular Dependencies

Bad:

```text
ProjectService
   ↓
DocumentService
   ↓
ProjectService
```

Better:

```text
ProjectService
DocumentService
      │
      └──── both use ProjectAuthorizationService
```

Không dùng `@Lazy` như giải pháp mặc định cho architectural cycle.

---

# 41. Service Interfaces

Không bắt buộc pattern:

```text
ProjectService
ProjectServiceImpl
```

nếu chỉ có một implementation.

Có thể dùng trực tiếp:

```java
@Service
public class ProjectService { ... }
```

Nên dùng interface ở nơi implementation thực sự có thể thay đổi:

```text
StorageService
MailService
```

Ví dụ:

```text
MinIO → S3
Gmail SMTP → another mail provider
```

---

# 42. Resource Naming

Giữ vocabulary nhất quán:

```text
Project
Document
Member
Owner
Invitation
Folder
Category
Tag
```

Không đổi qua lại giữa:

```text
Workspace / Project
File / Document
Member / Participant
```

nếu không có lý do rõ ràng.

---

# 43. Validation

Request validation dùng Jakarta Bean Validation:

```text
@NotBlank
@Email
@Size
@NotNull
```

Controller dùng `@Valid`.

Nhưng business validation vẫn ở Service.

Ví dụ `@Email` không thay thế unique email; `@NotNull` không xác nhận folder có thuộc cùng project hay không.

---

# 44. Configuration Properties

Nên map environment variables vào typed properties:

```text
JwtProperties
StorageProperties
MailProperties
RedisProperties
OtpProperties
UploadProperties
InvitationProperties
```

Ví dụ:

```yaml
kbase:
  jwt:
    access-token-ttl: 15m
    refresh-token-ttl: 7d

  otp:
    length: 6
    ttl: 5m
    resend-cooldown: 60s
    max-attempts: 5

  redis:
    host: redis
    port: 6379

  invitation:
    expiration: 72h

  upload:
    document-max-size: 50MB
    image-max-size: 20MB
    video-max-size: 500MB
    max-batch-files: 10
```

Secrets không commit vào source control.

---

# 45. Resource Files

Recommended:

```text
src/main/resources
│
├── application.yml
├── application-local.yml
├── application-prod.yml
│
├── db
│   └── migration
│
└── templates
    └── mail
        ├── email-verification-otp.html
        └── project-invitation.html
```

Không commit production credentials.

---

# 46. Database Migration

Core v1 nên dùng migration tooling.

Recommended baseline:

```text
Flyway
```

Vì physical schema đã được thiết kế khá rõ bằng SQL.

Ví dụ:

```text
db/migration/
├── V1__create_users.sql
├── V2__create_projects.sql
├── V3__create_documents.sql
└── ...
```

Không dựa vào `spring.jpa.hibernate.ddl-auto=update` để quản lý production schema.

---

# 47. JPA Schema Management

Recommended:

```text
development:
Flyway migration
Hibernate validate

production:
Flyway migration
Hibernate validate
```

Ví dụ:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

---

# 48. API Documentation

OpenAPI config:

```text
config/OpenApiConfig
```

Swagger phải hỗ trợ:

```text
Bearer JWT authentication
multipart upload
request/response schemas
error responses
```

---

# 49. Testing Architecture

Recommended layers:

```text
Unit tests
Integration tests
Repository tests
Security tests
API/controller tests
```

Ví dụ:

```text
ProjectServiceTest
InvitationServiceTest
FolderServiceTest
DocumentAuthorizationServiceTest
DocumentServiceTest
```

Integration:

```text
ProjectApiIntegrationTest
InvitationApiIntegrationTest
DocumentApiIntegrationTest
```

---

# 50. Testcontainers

Recommended:

```text
PostgreSQL Testcontainer
MinIO Testcontainer where practical
Redis Testcontainer for OTP verification flows
```

Lợi ích:

```text
real PostgreSQL constraints
partial indexes
composite FKs
real migration scripts
less H2 incompatibility
```

Physical Design của KBase có PostgreSQL-specific behaviors như partial indexes, nên không nên dựa vào H2 làm integration database chính.

---

# 51. Security Tests

Phải test rõ:

```text
MEMBER cannot modify another member's file
OWNER can modify all project files
non-member cannot access project
MEMBER can leave
OWNER cannot leave
MEMBER cannot manage folders
MEMBER can create tag
MEMBER cannot rename/delete tag
ADMIN override works
DISABLED user cannot authenticate/use refresh
unverified user cannot login
verified user can login after correct OTP
```

---

# 52. Database Integrity Tests

Phải verify:

```text
only one OWNER per project
duplicate project membership rejected
duplicate pending invitation rejected
root folder names unique case-insensitively
child folder names unique per parent
cross-project parent folder rejected
cross-project document-folder relation rejected
cross-project category relation rejected
cross-project document-tag relation rejected
category in use cannot be deleted
folder not empty cannot be deleted
```

---

# 53. Document Storage Tests

Test scenarios:

```text
valid upload
unsupported type
MIME mismatch
too large
empty file
storage failure
DB failure after MinIO upload
delete storage failure
member delete permission
owner delete permission
```

---

# 54. AI Future Boundary

AI không được đưa vào Core hiện tại.

Future architecture có thể thêm:

```text
separate Python AI service
```

Flow tương lai:

```text
Spring Boot
    ↓
AI Client / Integration
    ↓
Python AI Service
    ↓
RAG / embedding / transcription
```

Không thêm AI package/table ở Core v1.

---

# 55. Main Flow – Create Project

```text
POST /api/v1/projects
        ↓
ProjectController
        ↓
ProjectService.createProject
        ↓
@Transactional
        ↓
ProjectRepository.save
        ↓
ProjectMemberRepository.save OWNER
        ↓
ProjectMapper
        ↓
ProjectResponse
```

---

# 56. Main Flow – Accept Invitation

```text
POST /api/v1/invitations/accept
        ↓
InvitationController
        ↓
InvitationService.accept
        ↓
Hash raw token
        ↓
ProjectInvitationRepository
        ↓
Validate:
- PENDING
- not expired
- email match
- not already member
        ↓
@Transactional
        ↓
ProjectMemberRepository.save MEMBER
        ↓
Invitation.status = ACCEPTED
        ↓
Response
```

---

# 57. Main Flow – Upload Document

```text
POST multipart
        ↓
DocumentController
        ↓
DocumentService.upload
        ↓
ProjectAuthorizationService
        ↓
FileValidationService
        ↓
Folder/Category/Tag validation
        ↓
StorageService.upload
        ↓
DocumentRepository.save
        ↓
DocumentTagRepository
        ↓
DocumentMapper
        ↓
DocumentResponse
```

Failure:

```text
MinIO success
DB failure
    ↓
StorageService.delete
    ↓
throw FILE_UPLOAD_FAILED
```

---

# 58. Main Flow – Modify Document

```text
PATCH /documents/{id}
        ↓
DocumentController
        ↓
DocumentService.updateMetadata
        ↓
DocumentRepository.find
        ↓
DocumentAuthorizationService.requireModifyPermission
        ↓
validate folder/category/tags same project
        ↓
update metadata
        ↓
sync DocumentTag
        ↓
response
```

---

# 59. Main Flow – Download

```text
GET /documents/{id}/download
        ↓
DocumentController
        ↓
DocumentService
        ↓
DocumentAuthorizationService.requireReadPermission
        ↓
StorageService
        ↓
stream / short-lived delivery
```

Không có direct unauthenticated MinIO access.

---

# 60. Dependency Summary

```text
auth
 ├── user
 ├── security
 ├── redis
 └── mail

user
 └── shared

project
 ├── user
 └── shared

invitation
 ├── project
 ├── user
 ├── mail
 └── shared

folder
 ├── project
 └── shared

category
 ├── project
 └── shared

tag
 ├── project
 └── shared

document
 ├── project
 ├── folder
 ├── category
 ├── tag
 ├── storage
 └── shared

security
 ├── user
 └── shared

storage
 └── shared

mail
 └── shared

redis
 └── shared
```

Actual dependency graph phải tránh cycle.

---

# 61. Important Architectural Rules

1. Controller không gọi Repository trực tiếp.
2. Controller không chứa business permission logic.
3. Không trả JPA Entity trực tiếp từ REST API.
4. Service sở hữu business orchestration.
5. Transaction boundary ở Service layer.
6. JWT authentication và project authorization là hai concern khác nhau.
7. Project permission logic được centralize.
8. Document ownership permission được centralize.
9. Business module phụ thuộc `StorageService`, không phụ thuộc trực tiếp MinIO SDK.
10. Invitation và email verification phụ thuộc `MailService`; Core v1 implementation sử dụng Gmail SMTP nhưng business services không phụ thuộc trực tiếp Gmail/SMTP API.
11. Email verification phụ thuộc `OtpStore`; Redis implementation được isolate sau abstraction này.
12. RefreshSession vẫn dùng PostgreSQL, không chuyển sang Redis.
13. Không dùng `CascadeType.ALL` mặc định.
14. Không dùng `ddl-auto=update` để quản lý production schema.
15. Không lưu raw refresh/invitation token trong DB; không lưu raw OTP trong PostgreSQL/log.
16. Không dùng permanent public MinIO URL.
17. Không trộn AI code vào Core v1.
18. Tránh circular service dependency.
19. Không tạo Service interface + Impl một cách máy móc.
20. Dùng interface khi infrastructure implementation thực sự có thể thay đổi.
21. Giữ domain vocabulary nhất quán.
22. Feature-first package layout là baseline.

---

# 62. Deliberately Unspecified

Các mục sau chưa được tự ý chốt:

```text
exact Spring Boot version
exact Java version
Maven vs Gradle
MapStruct usage
Lombok usage
Flyway vs Liquibase final choice
CI/CD provider
cloud provider
exact MinIO bucket naming
exact Gmail account / sender address
refresh-token rotation policy
event-driven/outbox implementation
```

Recommendation trong tài liệu là architecture baseline, không phải business rule đã chốt trước đó.

---

# 63. Recommended Baseline Choices

```text
Build:
Maven

Persistence:
Spring Data JPA

Migration:
Flyway

Validation:
Jakarta Bean Validation

Security:
Spring Security + JWT

API Docs:
springdoc-openapi / OpenAPI

Database:
PostgreSQL

Storage abstraction:
StorageService

Storage implementation:
MinIO

Email abstraction:
MailService

Email implementation:
Gmail SMTP through MailService abstraction

OTP store:
Redis through OtpStore abstraction

Integration testing:
Testcontainers

Architecture:
feature-first modular monolith
```

Các lựa chọn này có thể thay đổi trước khi code nếu trainer/environment có yêu cầu khác.

---

# 64. Core Source Structure Summary

```text
src
├── main
│   ├── java
│   │   └── com
│   │       └── kbase
│   │           ├── auth
│   │           ├── user
│   │           ├── project
│   │           ├── invitation
│   │           ├── folder
│   │           ├── category
│   │           ├── tag
│   │           ├── document
│   │           ├── security
│   │           ├── storage
│   │           ├── mail
│   │           ├── redis
│   │           ├── shared
│   │           └── config
│   │
│   └── resources
│       ├── application.yml
│       ├── db
│       │   └── migration
│       └── templates
│           └── mail
│
└── test
    └── java
        └── com
            └── kbase
                ├── auth
                ├── project
                ├── invitation
                ├── folder
                ├── document
                └── ...
```

---

# 65. Architecture Definition of Done

Phase này hoàn thành khi implementation tuân theo:

```text
feature-first modular package structure

Controller → Service → Repository

DTO isolates REST contract from entities

security handles authentication

project/document authorization services handle business permissions

storage abstraction isolates MinIO

mail abstraction isolates Gmail SMTP

OtpStore abstraction isolates Redis

refresh sessions remain PostgreSQL-backed

transactions are Service-level

PostgreSQL migration is explicit

Core v1 contains no AI implementation

package dependencies avoid circular coupling
```

---

# Docker Runtime Persistence Boundary

Local Docker Compose baseline includes:

```text
backend
frontend
postgres
minio
redis
```

Named volumes:

```text
postgres_data → PostgreSQL durable database files + Flyway history
minio_data    → MinIO durable object data
```

Redis is used only for short-lived OTP verification state and does not require a durable volume in Core v1. Redis restart invalidates pending OTP state; user can request a new OTP.

Flyway migrations remain packaged in the backend image and run against the PostgreSQL container on application startup.

Gmail SMTP remains an external service outside Docker.

---

# 66. Next Phase

Bước thiết kế tiếp theo được đề xuất:

```text
Spring Boot Application Architecture
        ↓
JPA Entity Mapping
        ↓
Repository Design
        ↓
Spring Security + JWT Design
        ↓
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

Artifact tiếp theo nên là:

```text
KBase Core v1 – JPA Entity Mapping + Repository Design
```

Phase này sẽ map Physical Database Design hiện có sang JPA entities và repository contracts mà không thay đổi domain rules.
