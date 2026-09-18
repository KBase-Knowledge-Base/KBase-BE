# KBase – Core v1
## Implementation Plan for Agent / Harness Execution

**Version:** Draft 1  
**Plan Type:** Execution Plan  
**Primary Use:** Coding Agent / Harness Orchestration  
**Scope:** KBase Core v1 Backend + Required Local Runtime Infrastructure  
**Backend:** Java Spring Boot  
**Architecture:** Feature-first Modular Monolith  
**Database:** PostgreSQL  
**Database Migration:** Flyway  
**OTP Store:** Redis  
**Email Delivery:** Gmail SMTP  
**Object Storage:** MinIO  
**Security:** Spring Security + JWT  
**API Documentation:** OpenAPI / Swagger  
**Testing:** JUnit / Spring Boot Test / Testcontainers baseline  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này chuyển toàn bộ bộ thiết kế KBase Core v1 thành một chuỗi task có thể được thực thi bởi coding agent hoặc agent harness.

Implementation Plan **không thay thế** các tài liệu thiết kế trước.

Vai trò của bộ tài liệu:

```text
Design Documents
    = technical / business source of truth

Implementation Plan
    = execution order + dependency + task boundary + gate
```

Execution model:

```text
Source Design Documents
        ↓
Implementation Plan
        ↓
Harness selects runnable task
        ↓
Agent loads only relevant design documents
        ↓
Agent inspects current repository
        ↓
Implement smallest complete task
        ↓
Run required tests
        ↓
Review
        ↓
Pass gate
        ↓
Next task
```

---

# 2. Source Document Registry

Harness nên đăng ký các tài liệu bằng alias ổn định.

| Alias | Canonical Document |
|---|---|
| SD-01 | KBase – Core v1 Specification |
| SD-02 | KBase – Core v1 Entity Analysis & ERD |
| SD-03 | KBase – Core v1 Physical Database Design |
| SD-04 | KBase – Core v1 REST API Specification |
| SD-05 | KBase – Core v1 Spring Boot Application Architecture + Module / Package Structure |
| SD-06 | KBase – Core v1 JPA Entity Mapping + Repository Design |
| SD-07 | KBase – Core v1 Spring Security + JWT Design |
| SD-08 | KBase – Core v1 Service Layer Detailed Design |
| SD-09 | KBase – Core v1 MinIO Integration Design |
| SD-10 | KBase – Core v1 Exception Handling Design |
| SD-11 | KBase – Core v1 OpenAPI / Swagger Configuration Design |
| SD-12 | KBase – Core v1 Testing Strategy |
| SD-13 | KBase – Core v1 Implementation Plan — this document |

The archive reviewed for this plan contains SD-01 through SD-12.

---

# 3. Source-of-Truth Precedence

Agent không được giải quyết conflict bằng suy đoán.

Use this precedence:

```text
1. Explicit accepted business decisions in SD-01
   and later approved decisions already reflected consistently
   across SD-02..SD-12.

2. Domain/data structure:
   SD-02 + SD-03

3. Public REST contract:
   SD-04

4. Application/package architecture:
   SD-05

5. Persistence implementation:
   SD-06

6. Security/authentication:
   SD-07

7. Service orchestration:
   SD-08

8. Storage integration:
   SD-09

9. Error/API failure contract:
   SD-10

10. OpenAPI documentation:
    SD-11

11. Required verification:
    SD-12

12. SD-13 determines implementation order only.
```

If two design documents genuinely conflict:

```text
DO NOT GUESS.
DO NOT silently choose one.
DO NOT invent a compromise.

→ record exact conflict
→ identify documents/sections involved
→ mark task BLOCKED
→ retrieve project history / obtain design resolution
```

---

# 4. No-Guess / Context-Depletion Rule

If the agent notices that one or more project rules are no longer available in context:

```text
treat this as context depletion
```

Required response:

```text
1. Stop modifying the affected behavior.
2. Re-read the relevant design documents.
3. Inspect current implementation and prior completed task state.
4. Re-check project/conversation history if design remains ambiguous.
5. Continue only after the missing rule is recovered.
```

Never fill a missing project requirement from generic best practices alone.

---

# 5. Core Business Rules the Agent Must Never Reinterpret

Unless the design set is explicitly revised:

```text
1. System roles: ADMIN / USER.

2. Project roles: OWNER / MEMBER.

3. OWNER is project-scoped, never a global system role.

4. Each project has exactly one OWNER.

5. A user may join multiple projects.

6. MEMBER may read/view/download all documents in a project they currently belong to.

7. MEMBER may modify/delete only documents they uploaded.

8. OWNER may manage every document in their project.

9. OWNER manages membership and invitations.

10. OWNER cannot leave project in Core v1.

11. Ownership transfer is out of scope.

12. Folder structure is OWNER/ADMIN managed.

13. MEMBER may place/move own document into existing project folders.

14. Category lifecycle is OWNER/ADMIN managed.

15. MEMBER may assign an existing category to own document.

16. MEMBER may create tags and assign/remove tags on own documents.

17. MEMBER may not rename/delete shared tags.

18. Removing/leaving MEMBER does NOT delete uploaded documents.

19. Document.uploadedBy references User, not ProjectMember.

20. File metadata is in PostgreSQL.

21. Binary files are in MinIO.

22. MinIO objects are private.

23. Project/document deletion is hard delete.

24. Document versioning is out of scope.

25. Search is metadata-only.

26. AI/RAG is out of scope until Core v1 is stable.
```

---

# 6. Authentication / OTP / Email Rules the Agent Must Preserve

```text
Registration:
email + password + displayName

Registration creates:
systemRole = USER
status = ACTIVE
emailVerifiedAt = null

Registration does NOT auto-login.

After registration:
generate email verification OTP
store protected OTP state in Redis
send raw OTP through Gmail SMTP

OTP purpose:
registration email verification only

OTP is NOT:
OTP login
MFA / 2FA
password-reset OTP
invitation acceptance OTP

OTP baseline:
6 numeric digits
TTL 5 minutes
resend cooldown 60 seconds
maximum attempts 5

OTP TTL/cooldown/attempts:
configurable

Raw OTP:
never stored in PostgreSQL
never logged

Persistent verification result:
users.email_verified_at

Login requires:
valid email/password
ACTIVE status
email_verified_at != null

Refresh sessions:
remain PostgreSQL-backed

Redis does NOT replace refresh_sessions.

Invitation:
continues to use secure invitation link/token,
not OTP.

Gmail SMTP:
used for both verification OTP
and project invitation email.
```

---

# 7. Runtime Persistence Rules

Local Docker runtime baseline:

```text
backend
frontend (if present in repository)
postgres
minio
redis
```

Persistence:

```text
PostgreSQL
→ Docker named volume: postgres_data

MinIO
→ Docker named volume: minio_data

Redis
→ OTP-only ephemeral state
→ NO durable volume required
```

Flyway:

```text
migration SQL packaged in backend image/source
under src/main/resources/db/migration

backend startup
→ connects PostgreSQL container
→ applies pending Flyway migrations
→ Hibernate validates schema
```

Gmail SMTP:

```text
external service outside Docker
```

No dependency on:

```text
host-installed PostgreSQL
host-installed MinIO
host-installed Redis
```

for the intended local Docker runtime.

---

# 8. Architecture Rules the Agent Must Preserve

```text
Controller → Service → Repository

Controller never calls Repository directly.

Controller does not contain project/document permission logic.

Service owns business orchestration.

Transactions are Service-level.

JWT authentication is separate from project authorization.

Project authorization is centralized.

Document ownership authorization is centralized.

Business modules depend on StorageService,
not directly on MinIO SDK.

Authentication email verification depends on OtpStore,
not directly on Redis client API.

Invitation/email verification depend on MailService,
not directly on Gmail SMTP implementation details.

JPA Entity is never returned directly from REST API.

No broad CascadeType.ALL by default.

Flyway is authoritative schema definition.

Hibernate ddl-auto baseline = validate.

No raw refresh token stored in DB.

No raw invitation token stored in DB.

No raw OTP stored in PostgreSQL.

No public permanent MinIO URL.

No AI code in Core v1.
```

---

# 9. Persistent Data Model Guard

Core v1 still has **10 persistent PostgreSQL entities/tables**:

```text
User
RefreshSession
Project
ProjectMember
ProjectInvitation
Folder
Category
Tag
Document
DocumentTag
```

Email verification adds:

```text
users.email_verified_at
```

but does NOT add:

```text
Otp entity
Otp JPA repository
Otp PostgreSQL table
```

OTP state lives behind:

```text
OtpStore → RedisOtpStore
```

---

# 10. Harness Execution Contract

Recommended task states:

```text
TODO
READY
IN_PROGRESS
BLOCKED
IMPLEMENTED
REVIEW_FAILED
TEST_FAILED
DONE
```

A task becomes `READY` only when all dependencies are `DONE`.

Recommended state separation:

```text
docs/KBase_Core_v1_Implementation_Plan.md
.harness/state.json
```

Avoid rewriting this source plan on every run if harness can persist execution state separately.

---

# 11. Per-Task Agent Protocol

For every task:

```text
1. Read task definition.

2. Load every listed source document.

3. Inspect current repository before editing.

4. Confirm dependency tasks actually exist in code.

5. Re-read docs if a rule is missing from active context.

6. Implement only current task scope.

7. Do not opportunistically add future features.

8. Run required task tests.

9. Run relevant regression tests.

10. Self-review against acceptance criteria.

11. Reviewer verifies:
    - business rule compliance
    - architecture boundary
    - data/security isolation
    - sensitive-data handling
    - tests
    - scope discipline

12. Mark DONE only after the task gate passes.
```

---

# 12. Agent Completion Report Format

Each coding task should return:

```text
Task:
Status:

Files created:
Files modified:

Behavior implemented:

Tests added:
Tests executed:
Test result:

Design documents used:

Known limitations:
New unresolved questions:

Business rules changed:
YES / NO
```

If:

```text
Business rules changed = YES
```

the task must not be accepted as a normal implementation task.

---

# 13. Technical Decision Gate

Several choices remain implementation-level rather than domain-level.

Before dependent tasks, lock:

```text
exact Java version
exact Spring Boot version
exact Hibernate version
exact springdoc version
exact Testcontainers version
exact MinIO Java SDK version
JWT library
GenerationType.UUID vs Hibernate @UuidGenerator
Lombok usage
MapStruct usage
OSIV final setting
MIME-detection library
Redis Java integration approach supported by selected Spring Boot version
Gmail mail dependency/configuration supported by selected Spring Boot version
```

Rules:

```text
- Prefer explicit repository/trainer constraints first.
- Use currently supported compatible versions.
- Record selected versions in technical setup documentation.
- Do not turn an implementation choice into a new product requirement.
```

---

# 14. Milestone Overview

```text
M0  Preflight & Execution Baseline

M1  Project Bootstrap + Local Runtime Skeleton

M2  PostgreSQL / Flyway Schema

M3  JPA Entities & Repositories

M4  Shared Error / Request Infrastructure

M5  Redis OTP + Gmail Mail Infrastructure

M6  Spring Security + Authentication

M7  User / Project / Membership

M8  Invitation Lifecycle

M9  Folder / Category / Tag

M10 MinIO Storage Infrastructure

M11 Document Lifecycle + Project Hard Delete

M12 Document Search / Pagination / Sorting

M13 OpenAPI / Swagger

M14 Full Docker Runtime Verification

M15 Full Verification / Core v1 Freeze
```

---

# 15. High-Level Dependency Graph

```text
M0
 ↓
M1
 ↓
M2
 ↓
M3
 ↓
M4
 ↓
M5
 ↓
M6
 ↓
M7
 ↓
M8
 ↓
M9
 ↓
M10
 ↓
M11
 ↓
M12
 ↓
M13
 ↓
M14
 ↓
M15
```

Default harness behavior should follow this sequence.

Parallelization is allowed only when dependencies are explicit and no shared architecture decision remains unresolved.

---

# 16. Milestone M0 – Preflight & Execution Baseline

Goal:

```text
Make repository state, design documents, and technical version baseline explicit before code changes.
```

---

## M0-01 – Inspect Current Repository

**Depends on:** none

**Load:**

```text
SD-01
SD-05
SD-13
current repository
trainer/project instructions if present
```

**Inspect:**

```text
Java version
Spring Boot version if project exists
build tool
root package
current dependencies
current source tree
Docker files
environment files
current tests
existing database config
existing Redis config
existing mail config
existing MinIO config
```

**Do not:**

```text
overwrite valid existing implementation blindly
```

**Acceptance:**

```text
repository baseline recorded
existing conflicts with SD-01..SD-12 identified
```

---

## M0-02 – Lock Compatible Technical Versions

**Depends on:** M0-01

**Load:**

```text
SD-05
SD-06
SD-07
SD-09
SD-11
SD-12
```

Lock:

```text
Java
Spring Boot
Hibernate
Spring Data Redis integration
springdoc
Testcontainers
MinIO SDK
JWT library
```

**Acceptance:**

```text
dependency graph resolves
selected versions are mutually compatible
```

---

## M0-03 – Lock Optional Implementation Choices

**Depends on:** M0-02

Resolve:

```text
Lombok yes/no
MapStruct yes/no
OSIV
UUID annotation strategy
MIME detection library
Redis serialization model
OTP keyed/hash implementation primitive
```

Constraints:

```text
No @Data on JPA entities.
Raw 6-digit OTP must never be stored.
OtpStore abstraction must remain.
```

---

## M0-04 – Register Source Documents in Harness

**Depends on:** M0-01

Actions:

```text
register SD-01..SD-13
store precedence rules
store context-depletion/no-guess rule
store Core v1 scope guard
```

**Acceptance:**

```text
agent can load source docs by alias
```

---

### M0 Gate

```text
✓ repository inspected
✓ technical versions locked
✓ optional technical choices recorded
✓ docs registered
✓ no unresolved design/repository conflict
```

---

# 17. Milestone M1 – Project Bootstrap + Local Runtime Skeleton

Goal:

```text
Create the application foundation and local container topology before feature implementation.
```

---

## BOOT-01 – Create / Normalize Spring Boot Project

**Depends on:** M0 Gate

**Load:**

```text
SD-05
```

Baseline dependencies:

```text
Spring Web
Spring Data JPA
Spring Security
Validation
PostgreSQL Driver
Flyway
Spring Data Redis
Mail support
springdoc
MinIO Java SDK
testing dependencies
```

**Acceptance:**

```text
project compiles
test phase runs
```

---

## BOOT-02 – Create Feature-First Package Skeleton

**Depends on:** BOOT-01

**Load:**

```text
SD-05
```

Create/normalize:

```text
auth
user
project
invitation
folder
category
tag
document
security
storage
mail
redis
shared
config
```

Auth package must accommodate:

```text
EmailVerificationService
OtpService
OtpStore
```

Redis package must accommodate:

```text
RedisConfig
RedisOtpStore
```

---

## BOOT-03 – Create Typed Configuration Properties

**Depends on:** BOOT-01

**Load:**

```text
SD-05
SD-07
SD-08
SD-09
```

Create property models for:

```text
JWT
refresh cookie
OTP TTL
OTP resend cooldown
OTP max attempts
OTP hash secret/pepper
Redis connection
Gmail SMTP
invitation expiry
upload limits
MinIO
CORS
OpenAPI enable flags
```

---

## BOOT-04 – Configure Profiles

**Depends on:** BOOT-03

Create/normalize:

```text
application.yml
application-local.yml
application-test.yml
production configuration strategy
```

No production secret in source.

---

## BOOT-05 – Create Local Infrastructure Compose Skeleton

**Depends on:** BOOT-03

**Load:**

```text
SD-01
SD-03
SD-05
SD-09
SD-12
```

Initial services:

```text
postgres
minio
redis
```

Named volumes:

```text
postgres_data
minio_data
```

Redis:

```text
no durable volume
```

The full backend/frontend Compose wiring may be completed later in M14.

---

### M1 Gate

```text
✓ app compiles
✓ feature-first structure exists
✓ config properties bind
✓ local infra compose declares postgres/minio/redis
✓ postgres_data and minio_data declared
✓ Redis has no durable OTP volume
✓ no secret committed
```

---

# 18. Milestone M2 – PostgreSQL / Flyway Schema

Goal:

```text
Create the approved persistent schema before JPA feature implementation.
```

---

## DB-01 – Configure PostgreSQL + Flyway

**Depends on:** M1 Gate

**Load:**

```text
SD-03
SD-05
SD-06
SD-12
```

Configure:

```text
PostgreSQL datasource
Flyway
Hibernate ddl-auto=validate
```

---

## DB-02 – Implement Core Tables Migration

**Depends on:** DB-01

**Load:**

```text
SD-03
```

Implement all 10 persistent tables:

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

Critical User column:

```text
email_verified_at TIMESTAMPTZ NULL
```

Do NOT create:

```text
otp table
redis session table
```

---

## DB-03 – Implement Constraints / Partial / Expression Indexes

**Depends on:** DB-02

Include:

```text
normalized unique users.email

unique project member(project,user)

single OWNER partial unique index

single PENDING invitation per project/email

case-insensitive root folder uniqueness

case-insensitive child folder uniqueness

case-insensitive category uniqueness

case-insensitive tag uniqueness

storage key uniqueness

same-project composite FK constraints
```

---

## DB-04 – Implement Query Indexes

**Depends on:** DB-02

Include indexes needed by:

```text
membership
invitation
documents
folder/category
uploader
document_tags
refresh sessions
```

---

## DB-05 – Migration Integrity Test

**Depends on:** DB-02, DB-03, DB-04

Use fresh PostgreSQL Testcontainer.

Verify:

```text
Flyway from empty DB
all migrations succeed
expected constraint names exist
Hibernate validate succeeds
```

---

### M2 Gate

```text
✓ all 10 persistent tables exist
✓ users.email_verified_at exists
✓ no OTP table exists
✓ migrations pass on clean PostgreSQL
✓ Hibernate validate passes
```

---

# 19. Milestone M3 – JPA Entities & Repositories

Goal:

```text
Map the approved persistent model without leaking Redis OTP state into JPA.
```

---

## JPA-01 – Implement Shared Enums

**Depends on:** M2 Gate

Implement:

```text
SystemRole
UserStatus
ProjectRole
InvitationStatus
FileKind
```

Use:

```text
EnumType.STRING
```

---

## JPA-02 – Implement User + RefreshSession

**Depends on:** JPA-01

**Load:**

```text
SD-03
SD-06
```

User includes:

```text
emailVerifiedAt
```

RefreshSession remains PostgreSQL-backed.

No OTP JPA entity.

---

## JPA-03 – Implement Project + ProjectMember

**Depends on:** JPA-01

Critical:

```text
Project has NO owner_id.
OWNER is ProjectMember.role.
```

---

## JPA-04 – Implement ProjectInvitation

**Depends on:** JPA-02, JPA-03

Persistent invitation token:

```text
tokenHash only
```

---

## JPA-05 – Implement Folder / Category / Tag

**Depends on:** JPA-03

Preserve:

```text
same-project integrity
LAZY relationships
minimal bidirectional graphs
```

---

## JPA-06 – Implement Document

**Depends on:** JPA-02, JPA-03, JPA-05

Critical:

```text
uploadedBy → User
folder/category same-project composite joins
storageKey internal
```

---

## JPA-07 – Implement DocumentTag + DocumentTagId

**Depends on:** JPA-05, JPA-06

Use explicit junction entity.

Do not use direct `@ManyToMany`.

---

## JPA-08 – Implement Core Repositories

**Depends on:** JPA-02..JPA-07

Create:

```text
UserRepository
RefreshSessionRepository
ProjectRepository
ProjectMemberRepository
ProjectInvitationRepository
FolderRepository
CategoryRepository
TagRepository
DocumentRepository
DocumentTagRepository
```

Do NOT create:

```text
OtpRepository
```

---

## JPA-09 – Implement Repository Queries / Projections

**Depends on:** JPA-08

Required:

```text
my projects query
member + user fetch
invitation FOR UPDATE
folder sibling uniqueness excluding current folder
same-project tag bulk lookup
document storage-key projection
document detail fetch
document tags + tag fetch
hard-delete dependency queries
```

---

## JPA-10 – Implement Document Specification Baseline

**Depends on:** JPA-08

Prepare metadata filters:

```text
projectId mandatory
q
folder
category
tag
fileKind
uploadedBy
createdFrom
createdTo
```

---

## JPA-11 – Repository / Mapping Integration Tests

**Depends on:** JPA-08, JPA-09, JPA-10

Use PostgreSQL Testcontainer.

Test:

```text
email_verified_at mapping
single OWNER
duplicate membership
pending invitation uniqueness
case-insensitive organization uniqueness
cross-project integrity
DocumentTag cascade behavior
```

---

### M3 Gate

```text
✓ Flyway/JPA mappings agree
✓ repository tests pass
✓ no OTP persistence entity/repository introduced
✓ persistent relationships follow design
```

---

# 20. Milestone M4 – Shared Error / Request Infrastructure

Goal:

```text
Establish stable API error behavior before feature endpoints proliferate.
```

---

## ERR-01 – Implement ErrorCode

**Depends on:** M3 Gate

**Load:**

```text
SD-04
SD-10
```

Must include OTP/email codes:

```text
EMAIL_NOT_VERIFIED
EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
OTP_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
```

plus all existing Core error groups.

---

## ERR-02 – Implement Exception Hierarchy

**Depends on:** ERR-01

Implement:

```text
KBaseException
BusinessException
ResourceNotFoundException
ForbiddenOperationException
InfrastructureException
```

---

## ERR-03 – Implement ApiErrorResponse

**Depends on:** ERR-01

Fields:

```text
timestamp
status
code
message
path
requestId
errors
```

---

## ERR-04 – Implement RequestIdFilter + MDC

**Depends on:** ERR-03

Requirements:

```text
X-Request-Id
MDC
clear in finally
```

---

## ERR-05 – Implement GlobalExceptionHandler

**Depends on:** ERR-01..ERR-04

Handle:

```text
KBaseException
Bean Validation
malformed JSON
invalid parameter/type
multipart size
DataIntegrityViolation fallback
unknown exception
```

---

## ERR-06 – Implement ConstraintViolationTranslator

**Depends on:** ERR-05, M2 Gate

Unknown constraint:

```text
DO NOT GUESS
→ INTERNAL_SERVER_ERROR
```

---

## ERR-07 – Error Contract Tests

**Depends on:** ERR-05, ERR-06

Verify:

```text
stable response
requestId
validation errors
constraint translation
no internal exception leakage
```

---

### M4 Gate

```text
✓ error contract stable
✓ OTP/email error codes available
✓ request IDs work
✓ DB constraint mapping works
```

---

# 21. Milestone M5 – Redis OTP + Gmail Mail Infrastructure

Goal:

```text
Build external authentication dependencies before registration/login implementation.
```

---

## REDIS-01 – Configure Redis Client

**Depends on:** M4 Gate, BOOT-03

**Load:**

```text
SD-05
SD-07
SD-08
SD-10
SD-12
```

Configure:

```text
Redis host
Redis port
serialization model
connectivity exception translation
```

Local runtime target:

```text
redis Docker service
```

Do not depend on host-local Redis.

---

## REDIS-02 – Implement OtpStore Port

**Depends on:** REDIS-01

Package boundary:

```text
auth.port.OtpStore
```

Conceptual operations:

```text
save verification OTP state
load OTP state
increment attempts
check resend cooldown
replace/reset OTP state
delete OTP state
```

OtpStore must expose application models, not Redis client objects.

---

## REDIS-03 – Implement RedisOtpStore

**Depends on:** REDIS-02

Recommended key namespace must remain consistent with design, e.g.:

```text
kbase:otp:email-verification:{userId}
kbase:otp:email-verification:cooldown:{userId}
```

Persist short-lived protected state only:

```text
OTP protected/hash value
attempt count
TTL
cooldown state
```

Do not store raw OTP.

---

## OTP-01 – Implement OtpService

**Depends on:** REDIS-03

Requirements:

```text
6 numeric digits
configured TTL
configured resend cooldown
configured max attempts
protected/keyed hash comparison
secure randomness
Clock where applicable
```

Errors:

```text
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
OTP_SERVICE_UNAVAILABLE
```

---

## MAIL-01 – Implement MailService Port

**Depends on:** M4 Gate

Methods include:

```text
sendEmailVerificationOtp(...)
sendProjectInvitation(...)
```

No Gmail classes in application services.

---

## MAIL-02 – Configure Gmail SMTP Adapter

**Depends on:** MAIL-01, BOOT-03

**Load:**

```text
SD-01
SD-05
SD-08
SD-10
```

Core baseline:

```text
smtp.gmail.com
port 587 / STARTTLS according to selected mail configuration
username from environment
Google/Gmail App Password from environment
```

Do not commit Gmail credentials.

---

## MAIL-03 – Implement SmtpMailService

**Depends on:** MAIL-02

Implement:

```text
verification OTP email
project invitation email
template rendering
mail exception translation
```

Never log:

```text
raw OTP
raw invitation token
Gmail App Password
```

---

## MAIL-04 – Add Mail Templates

**Depends on:** MAIL-03

Templates:

```text
email-verification-otp.html
project-invitation.html
```

OTP template should include only safe user-facing content.

Invitation template contains intended invitation link/token.

---

## EXT-AUTH-TEST – Redis / Gmail Adapter Tests

**Depends on:** REDIS-03, OTP-01, MAIL-03

Required:

```text
OtpService unit tests
Redis OTP integration with Redis Testcontainer
TTL behavior
attempt counter
cooldown
replace/resend behavior
Redis failure translation
Gmail adapter unit tests
fake/local SMTP integration where practical
```

Do not send real email in CI.

---

### M5 Gate

```text
✓ RedisOtpStore works against containerized Redis
✓ OTP state is ephemeral
✓ raw OTP is not stored
✓ Gmail adapter is isolated behind MailService
✓ no real credentials required in automated tests
```

---

# 22. Milestone M6 – Spring Security + Authentication

Goal:

```text
Implement registration → verify OTP → login → refresh → logout.
```

---

## SEC-01 – PasswordEncoder + Security Properties

**Depends on:** M5 Gate

Implement:

```text
BCrypt
JWT properties
cookie properties
CORS properties
OTP properties already bound
```

---

## SEC-02 – Implement JwtService

**Depends on:** SEC-01, JPA-02

Baseline:

```text
access TTL 15m configurable
sub = userId
systemRole claim
iat
exp
jti if selected
```

No project roles in JWT.

---

## SEC-03 – Implement CustomUserPrincipal / Current User Loading

**Depends on:** JPA-02

Principal:

```text
userId
email
systemRole
status
```

---

## SEC-04 – Implement JwtAuthenticationFilter

**Depends on:** SEC-02, SEC-03

Flow:

```text
Bearer
validate
userId
load current User
reject DISABLED
SecurityContext
```

Do not check project membership here.

---

## SEC-05 – Implement AuthenticationEntryPoint / AccessDeniedHandler

**Depends on:** ERR-03, ERR-04

Use standard `ApiErrorResponse`.

---

## SEC-06 – Implement SecurityConfig

**Depends on:** SEC-04, SEC-05

Public:

```text
POST /api/v1/auth/register
POST /api/v1/auth/verify-email
POST /api/v1/auth/resend-verification-otp
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

Admin:

```text
/api/v1/admin/** → ADMIN
```

Other Core APIs:

```text
authenticated
```

Swagger routes depend on environment flags.

---

## AUTH-01 – Implement RefreshSessionService

**Depends on:** JPA-02, SEC-01

Requirements:

```text
opaque high-entropy refresh token
token hash persisted in PostgreSQL
7-day configurable baseline
revoke current
revoke all sessions
```

Redis is not used for refresh sessions.

---

## AUTH-02 – Implement EmailVerificationService

**Depends on:** OTP-01, MAIL-03, JPA-02

Responsibilities:

```text
issueOtp
verify
resend
update users.email_verified_at
coordinate Redis + Gmail
```

Registration issue flow:

```text
create protected Redis state
send Gmail OTP
```

If Gmail send fails after Redis write:

```text
best-effort cleanup new OTP state
EMAIL_SERVICE_UNAVAILABLE
```

Successful verify:

```text
validate OTP
set email_verified_at
persist User
best-effort remove Redis state
```

---

## AUTH-03 – Implement Registration

**Depends on:** AUTH-02, ERR-06

Endpoint:

```text
POST /api/v1/auth/register
```

Rules:

```text
normalize email
password 8..64
hash password
systemRole USER
status ACTIVE
emailVerifiedAt null
persist User
issue OTP
return emailVerified false
no auto-login
```

---

## AUTH-04 – Implement Verify Email

**Depends on:** AUTH-02

Endpoint:

```text
POST /api/v1/auth/verify-email
```

Request:

```text
email
otp
```

Success:

```text
emailVerified true
users.email_verified_at set
OTP state invalidated
```

---

## AUTH-05 – Implement Resend Verification OTP

**Depends on:** AUTH-02

Endpoint:

```text
POST /api/v1/auth/resend-verification-otp
```

Rules:

```text
user exists
not verified
cooldown respected
old OTP replaced
attempt count reset
TTL reset
new OTP sent by Gmail
```

---

## AUTH-06 – Implement Login

**Depends on:** AUTH-01, SEC-02

Endpoint:

```text
POST /api/v1/auth/login
```

Flow:

```text
normalize email
verify password
ACTIVE
emailVerifiedAt != null
access JWT
refresh session
HttpOnly refresh cookie
```

Unverified:

```text
EMAIL_NOT_VERIFIED
```

---

## AUTH-07 – Implement Refresh

**Depends on:** AUTH-01, SEC-02

Endpoint:

```text
POST /api/v1/auth/refresh
```

Requirements:

```text
cookie-based
session PostgreSQL-backed
not revoked
not expired
User ACTIVE
email verified
new access token
```

No mandatory refresh-token rotation.

---

## AUTH-08 – Implement Logout

**Depends on:** AUTH-01

Endpoint:

```text
POST /api/v1/auth/logout
```

Behavior:

```text
revoke current refresh session
clear cookie
effectively idempotent
204
```

---

## AUTH-SEC-TEST – Authentication / Security Tests

**Depends on:** SEC-06, AUTH-03..AUTH-08

Must cover:

```text
register creates unverified account
register writes OTP Redis state
register requests Gmail delivery

verify correct OTP
invalid OTP
expired OTP
attempt exhaustion
resend cooldown
resend replaces OTP

login before verify → EMAIL_NOT_VERIFIED
login after verify succeeds

invalid credentials privacy
disabled account
JWT valid/expired/tampered
refresh
logout
ADMIN route protection
```

Use Redis Testcontainer for OTP integration.

---

### M6 Gate

```text
✓ registration requires OTP verification
✓ login blocked until verified
✓ Redis contains only short-lived OTP state
✓ refresh sessions remain PostgreSQL
✓ Gmail SMTP is used through MailService
✓ JWT security tests pass
```

---

# 23. Milestone M7 – User / Project / Membership

Goal:

```text
Implement main collaboration/security boundary.
```

---

## USER-01 – Implement User DTOs / Mapper

**Depends on:** M6 Gate

User response includes derived:

```text
emailVerified
```

Never expose:

```text
passwordHash
emailVerifiedAt unless API explicitly requires timestamp
```

---

## USER-02 – Implement Current User APIs

**Depends on:** USER-01

Endpoints:

```text
GET /users/me
PATCH /users/me
PUT /users/me/password
```

Password change:

```text
verify current password
revoke refresh sessions baseline
```

---

## USER-03 – Implement Admin User APIs

**Depends on:** USER-01, SEC-06

Endpoints:

```text
GET /admin/users
GET /admin/users/{id}
PATCH /admin/users/{id}/status
DELETE /admin/users/{id}
```

Disable user:

```text
revoke refresh sessions
```

Hard-delete dependency checks preserved.

---

## PROJ-01 – Implement Project DTOs / Mapper

**Depends on:** M6 Gate

For ADMIN without membership:

```text
currentUserRole may be null
```

Never invent ADMIN ProjectRole.

---

## PROJ-02 – Implement ProjectAuthorizationService

**Depends on:** PROJ-01, JPA-08

Implement:

```text
requireProjectAccess
requireOwner
ADMIN override
```

---

## PROJ-03 – Implement Project Create/List/Get/Update

**Depends on:** PROJ-02

Endpoints:

```text
POST /projects
GET /projects
GET /projects/{projectId}
PATCH /projects/{projectId}
```

Create transaction:

```text
Project + OWNER membership
```

Project hard delete is completed later after MinIO integration.

---

## MEM-01 – Implement Member Listing

**Depends on:** PROJ-02

Endpoint:

```text
GET /projects/{projectId}/members
```

---

## MEM-02 – Implement Remove Member

**Depends on:** MEM-01

Rules:

```text
OWNER/ADMIN
cannot remove project OWNER
documents remain
```

---

## MEM-03 – Implement Leave Project

**Depends on:** MEM-01

Rules:

```text
MEMBER can leave
OWNER cannot leave
documents remain
```

---

## M7-TEST – User / Project / Membership Tests

**Depends on:** USER-02, USER-03, PROJ-03, MEM-02, MEM-03

Must cover:

```text
creator becomes OWNER
single OWNER
non-member forbidden
MEMBER cannot update project
OWNER can
ADMIN override without membership
member removal preserves documents
OWNER cannot leave
emailVerified field mapping
```

---

### M7 Gate

```text
✓ user management works
✓ project access model works
✓ membership rules work
✓ ADMIN override works
```

---

# 24. Milestone M8 – Invitation Lifecycle

Goal:

```text
Implement invitation using the already-built Gmail MailService.
```

---

## INV-01 – Implement Invitation DTOs / Mapper

**Depends on:** M7 Gate

Create:

```text
CreateInvitationRequest
AcceptInvitationRequest
InvitationResponse
AcceptInvitationResponse
```

---

## INV-02 – Implement Invitation Token Utility

**Depends on:** INV-01

Requirements:

```text
high-entropy token
hash persisted
raw token only in email link
```

OTP is not involved.

---

## INV-03 – Implement Create/List Invitation

**Depends on:** INV-02, MAIL-03, PROJ-02

Endpoints:

```text
POST /projects/{id}/invitations
GET /projects/{id}/invitations
```

Rules:

```text
OWNER/ADMIN
existing member rejected
one PENDING invitation per project/email
72h configurable expiration
Gmail invitation email
```

---

## INV-04 – Implement Resend / Cancel

**Depends on:** INV-03

Resend:

```text
new invitation token
old token invalid
expiry reset
Gmail email
```

Cancel:

```text
PENDING → CANCELLED
```

---

## INV-05 – Implement Accept with Pessimistic Lock

**Depends on:** INV-03

Endpoint:

```text
POST /api/v1/invitations/accept
```

Requirements:

```text
authenticated
ACTIVE
email already verified because login requires verification
PENDING
not expired
current email matches invitation email
not already member
create MEMBER
mark ACCEPTED
acceptedAt
```

New user invite flow:

```text
register
→ verify OTP
→ login
→ accept invitation token
```

---

## INV-06 – Invitation Tests

**Depends on:** INV-04, INV-05

Must cover:

```text
OWNER vs MEMBER
existing member
duplicate pending
Gmail adapter invoked
resend invalidates old token
cancel
expired
email mismatch
concurrent accept only once
new-user flow assumes verified/login account
```

---

### M8 Gate

```text
✓ invitation lifecycle complete
✓ invitation still uses link/token, not OTP
✓ Gmail MailService reused correctly
✓ accept concurrency safe
```

---

# 25. Milestone M9 – Folder / Category / Tag

Goal:

```text
Implement project organization metadata before document lifecycle.
```

Status: `DONE` — M9 Gate `PASS` ngày `2026-09-18`.

Evidence: `FolderController`, `CategoryController`, `TagController` với 12 project-scoped endpoints; unit 12/12, `OrganizationIntegrationTest` 6/6, migration/JPA integrity regression 23/23 và full suite 161/161 qua `mvn -B -ntp clean verify`. M10/M11, document upload/lifecycle và frontend không thuộc slice này.

---

## FOLDER-01 – Folder DTO / Mapper

**Depends on:** M7 Gate

---

## FOLDER-02 – Implement List/Create Folder

**Depends on:** FOLDER-01, PROJ-02

Rules:

```text
list MEMBER/OWNER/ADMIN
create OWNER/ADMIN
same-project parent
case-insensitive sibling uniqueness
```

---

## FOLDER-03 – Implement Rename / Move Folder

**Depends on:** FOLDER-02

Rules:

```text
OWNER/ADMIN
no self-parent
no descendant cycle
same-project parent
duplicate sibling check excluding self
```

---

## FOLDER-04 – Implement Delete Folder

**Depends on:** FOLDER-03

Allow only when:

```text
no child folders
no documents
```

---

## CAT-01 – Implement Category CRUD

**Depends on:** M7 Gate

Rules:

```text
view project members
manage OWNER/ADMIN
unique per project case-insensitive
cannot delete while in use
```

---

## TAG-01 – Implement Tag CRUD

**Depends on:** M7 Gate

Rules:

```text
MEMBER may create
OWNER/ADMIN may rename/delete
delete removes DocumentTag only
```

---

## ORG-TEST – Organization Tests

**Depends on:** FOLDER-04, CAT-01, TAG-01

Test:

```text
folder cycle
cross-project parent
folder non-empty
category in-use
member tag create
member tag mutation forbidden
case-insensitive uniqueness
```

---

### M9 Gate

```text
✓ organization rules implemented
✓ project isolation preserved
```

Result: `PASS` — OWNER/ADMIN folder và category management, MEMBER read-only folder/category access, MEMBER tag creation, OWNER/ADMIN tag rename/delete, case-insensitive uniqueness, folder ancestor-walk cycle prevention, non-empty/in-use deletion rules và DocumentTag-only tag deletion đã được verify.

---

# 26. Milestone M10 – MinIO Storage Infrastructure

Goal:

```text
Build private, streaming object storage behind StorageService.
```

---

## STORAGE-01 – Storage Models / Exceptions

**Depends on:** M4 Gate

Implement:

```text
StorageUploadRequest
StoredObject
StoredResource
ObjectMetadata

StorageException
StorageUnavailableException
StorageObjectNotFoundException
StorageUploadException
StorageDeleteException
```

---

## STORAGE-02 – StorageService Interface

**Depends on:** STORAGE-01

Methods:

```text
upload
get
getRange
stat
delete
deleteAll
```

---

## STORAGE-03 – Configure MinioClient

**Depends on:** STORAGE-02, BOOT-03

Rules:

```text
singleton
environment credentials
private bucket
one bucket per environment
```

---

## STORAGE-04 – Bucket Initialization / Assumptions

**Depends on:** STORAGE-03

Local:

```text
auto-create allowed
```

Production:

```text
pre-provisioned
```

Core v1:

```text
versioning disabled
no object lock
no retention
```

---

## STORAGE-05 – Implement Upload / Get / Stat / Delete

**Depends on:** STORAGE-04

Critical:

```text
stream
no full-file byte[] buffering
exception translation
```

Object key:

```text
projects/{projectId}/documents/{documentId}.{extension}
```

---

## STORAGE-06 – Implement Range Read

**Depends on:** STORAGE-05

Support:

```text
offset
length
```

for MP4.

---

## STORAGE-07 – Implement Batch Delete

**Depends on:** STORAGE-05

Process per-object result and attempt all requested cleanup operations.

---

## STORAGE-08 – MinIO Integration Tests

**Depends on:** STORAGE-05..STORAGE-07

Use MinIO Testcontainer:

```text
bucket
upload
full get
range get
stat
delete
batch delete
missing object
```

---

### M10 Gate

```text
✓ MinIO streaming works
✓ range read works
✓ private storage boundary preserved
✓ MinIO SDK does not leak outside adapter
```

---

# 27. Milestone M11 – Document Lifecycle + Project Hard Delete

Goal:

```text
Implement KBase file knowledge lifecycle and finish project deletion.
```

---

## DOC-01 – Document DTOs / Mapper

**Depends on:** M9 Gate, M10 Gate

Create:

```text
DocumentMetadataRequest
UpdateDocumentRequest
DocumentResponse
DocumentSummaryResponse
```

Never expose:

```text
storageKey
```

---

## DOC-02 – FileValidationService

**Depends on:** DOC-01

Supported:

```text
PDF DOC DOCX XLS XLSX PPT PPTX MD TXT
JPG JPEG PNG GIF SVG BMP
MP4 MOV AVI
```

Configurable baseline limits:

```text
Document/Office 50 MB
Image 20 MB
Video 500 MB
Batch 10
```

Validate:

```text
empty
extension
MIME
size
FileKind
batch count
```

---

## DOC-03 – DocumentAuthorizationService

**Depends on:** PROJ-02

Read:

```text
ADMIN or current ProjectMember
```

Modify/delete:

```text
ADMIN
or OWNER
or MEMBER when uploadedBy == currentUser
```

---

## DOC-04 – Implement Same-Project Metadata Resolver

**Depends on:** DOC-02, M9 Gate

Validate:

```text
folder
category
tags
```

all belong to target project.

---

## DOC-05 – Implement Single Upload

**Depends on:** DOC-02, DOC-03, DOC-04, STORAGE-05

Flow:

```text
authorize
validate
resolve metadata
generate documentId
generate storageKey
upload MinIO
persist Document
persist DocumentTag
```

Compensation:

```text
DB failure after upload
→ best-effort delete object
```

---

## DOC-06 – Implement Batch Upload

**Depends on:** DOC-05, STORAGE-07

Baseline:

```text
common metadata
application-level all-or-fail where practical
rollback DB
cleanup uploaded keys on failure
```

Do not add partial-success contract.

---

## DOC-07 – Implement Get / Update Metadata

**Depends on:** DOC-03, DOC-04

Endpoints:

```text
GET /documents/{id}
PATCH /documents/{id}
```

Rename changes `displayName`, not storage key.

DocumentTag sync is transactional.

---

## DOC-08 – Implement Download

**Depends on:** DOC-03, STORAGE-05

Rules:

```text
authorize before storage
stream attachment
filename from displayName
```

---

## DOC-09 – Implement Preview + HTTP Range

**Depends on:** DOC-08, STORAGE-06

Preview:

```text
PDF
images
TXT
MD
MP4
```

Office:

```text
PREVIEW_NOT_SUPPORTED
```

MP4:

```text
single Range
206
416 invalid
```

---

## DOC-10 – Implement Document Hard Delete

**Depends on:** DOC-03, STORAGE-05

Accepted order:

```text
authorize
storage delete
DB delete
DocumentTag cascade
```

Do not silently reverse ordering.

---

## PROJ-DELETE-01 – Implement Project Hard Delete

**Depends on:** DOC-10, STORAGE-07, PROJ-02

Flow:

```text
OWNER/ADMIN
load document storage-key projection
deleteAll MinIO objects
delete Project
DB cascades relational rows
```

If storage delete fails:

```text
do not proceed with normal DB project deletion
```

---

## DOC-11 – Document Lifecycle Tests

**Depends on:** DOC-05..DOC-10, PROJ-DELETE-01

Must include:

```text
single upload
batch
same-project metadata rules
member/owner/admin authorization
former-member denial
preview/download
range
document hard delete
project hard delete
upload compensation
cleanup failure
storage delete failure
```

Use PostgreSQL + MinIO Testcontainers for critical flows.

---

### M11 Gate

```text
✓ document lifecycle complete
✓ authorization matrix passes
✓ MinIO/DB compensation behavior tested
✓ project hard delete complete
```

---

# 28. Milestone M12 – Document Search / Pagination / Sorting

Goal:

```text
Complete Core v1 metadata discovery.
```

Status: `DONE` — M12 Gate `PASS` ngày `2026-09-18`.

Evidence: `DocumentSearchCriteria`, `DocumentSearchService`, `DocumentController` project-scoped endpoint và canonical `PaginationParser` sort mapping; `DocumentSearchIntegrationTest` 2/2 với PostgreSQL 17 Testcontainer + real security filter chain, focused search/repository suite 16/16, full suite 188/188 qua `mvn -B -ntp test` và `mvn -B -ntp clean verify`. Search remains metadata-only and tag predicates use `EXISTS` to prevent duplicate document rows.

---

## SEARCH-01 – Implement DocumentSearchCriteria

**Depends on:** JPA-10, DOC-01

Fields:

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

---

## SEARCH-02 – Implement DocumentSearchService

**Depends on:** SEARCH-01, PROJ-02

Mandatory:

```text
projectId predicate
```

`q` searches:

```text
displayName
originalFilename
description
category
tag
```

No content extraction / vector search.

---

## SEARCH-03 – Implement Search Endpoint

**Depends on:** SEARCH-02

Endpoint:

```text
GET /projects/{projectId}/documents
```

Pagination:

```text
page 0
size 20
max 100
```

Sort whitelist:

```text
displayName
createdAt
updatedAt
sizeBytes
```

---

## SEARCH-04 – Search Tests

**Depends on:** SEARCH-03

Test:

```text
filters
combined filters
tag search
pagination
ASC/DESC
invalid sort
Project A never leaks Project B docs
```

---

### M12 Gate

```text
✓ metadata search contract matches SD-04
✓ project isolation passes
✓ no AI/content search introduced
```

---

# 29. Milestone M13 – OpenAPI / Swagger

Goal:

```text
Make implemented REST contract accurately discoverable.
```

Status: `DONE` — M13 Gate `PASS` ngày `2026-09-18`.

Evidence: `config/OpenApiConfig` (metadata "KBase Core API v1", `bearerAuth` HTTP Bearer JWT, 12 canonical tags, shared 401 customizer dùng `ApiErrorResponse`); 12 controllers / 48 operations annotated với security requirements đúng (public auth endpoints không Bearer-required, protected endpoints require `bearerAuth`, ADMIN ghi `SystemRole.ADMIN`), project/document permission descriptions, multipart `file`/`files` binary + `metadata` JSON parts, binary download/preview schemas, MP4 `Range`/`206`/`416` documentation và OTP/Gmail/Redis/storage error codes đúng endpoint. `OpenApiContractIntegrationTest` 19/19 + `OpenApiDisabledIntegrationTest` 2/2 trên real SecurityFilterChain; full suite 209/209 qua `mvn -B -ntp test` và `mvn -B -ntp clean verify`. Swagger UI bật local/dev, prod mặc định tắt qua `kbase.openapi.*` không nới `/api/v1/**`; không document AI/RAG hay JPA entity.

---

## API-DOC-01 – OpenApiConfig + bearerAuth

**Depends on:** M12 Gate

Implement:

```text
KBase Core API metadata
bearerAuth HTTP Bearer JWT
```

---

## API-DOC-02 – Document Authentication Endpoints

**Depends on:** API-DOC-01

Public docs:

```text
register
verify-email
resend-verification-otp
login
refresh
```

Document:

```text
OTP is Redis-backed
OTP is email-verification only
Gmail SMTP sends OTP
login requires verified email
```

---

## API-DOC-03 – Controller Tags / Role Rules

**Depends on:** API-DOC-01

Tags:

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

Document actual permissions.

---

## API-DOC-04 – Multipart / Binary / Range Documentation

**Depends on:** API-DOC-03

Represent:

```text
multipart file + metadata
binary download
binary preview
Range
206
416
```

---

## API-DOC-05 – Shared Error Documentation

**Depends on:** ERR-03

Document:

```text
ApiErrorResponse
OTP errors
Redis unavailable
Gmail unavailable
storage errors
```

---

## API-DOC-06 – Environment Exposure Flags

**Depends on:** API-DOC-01

Local/dev:

```text
enabled
```

Production:

```text
configurable
Swagger UI disabled/restricted baseline
```

---

## API-DOC-07 – OpenAPI Contract Tests

**Depends on:** API-DOC-02..API-DOC-06

Verify:

```text
/v3/api-docs
bearerAuth
public OTP endpoints
protected endpoints
multipart schema
binary schema
ApiErrorResponse
no sensitive fields
```

---

### M13 Gate

```text
✓ Swagger matches real API
✓ OTP/Gmail/Redis behavior documented correctly
✓ internal fields absent
```

---

# 30. Milestone M14 – Full Docker Runtime Verification

Goal:

```text
Run Core v1 using the intended local Docker topology and verify persistence boundaries.
```

---

## DOCKER-01 – Backend Dockerfile

**Depends on:** M13 Gate

Requirements:

```text
reproducible build
environment-driven runtime
no durable backend-local business data
```

---

## DOCKER-02 – Complete docker-compose

**Depends on:** DOCKER-01, BOOT-05

Services:

```text
backend
postgres
minio
redis
frontend if present
```

Gmail SMTP stays outside Docker.

---

## DOCKER-03 – Wire Named Volumes

**Depends on:** DOCKER-02

Required:

```text
postgres_data → PostgreSQL data directory
minio_data → MinIO /data
```

Redis:

```text
no named volume required
```

---

## DOCKER-04 – Wire Runtime Environment

**Depends on:** DOCKER-02

Configure via env:

```text
DB URL/credentials
Redis host/port
OTP settings/hash secret
Gmail username/App Password
JWT secret/TTLs
MinIO endpoint/credentials/bucket
invitation expiry
upload limits
frontend URL/CORS
Swagger flags
```

No secret in Git.

---

## DOCKER-05 – Flyway Startup Verification

**Depends on:** DOCKER-02, DOCKER-03

Verify:

```text
backend startup
→ PostgreSQL container
→ pending Flyway migration
→ Hibernate validate
```

Flyway history persists in:

```text
postgres_data
```

---

## DOCKER-06 – Persistence Smoke Test

**Depends on:** DOCKER-03

Procedure:

```text
create DB data
upload MinIO object

recreate postgres/minio containers
without deleting named volumes

verify:
DB data remains
MinIO object remains
```

Redis behavior:

```text
create pending OTP
recreate Redis container
pending OTP may disappear
user can resend OTP
```

This loss is acceptable by design.

---

## DOCKER-07 – Authentication Runtime Smoke Test

**Depends on:** DOCKER-04

Verify against Docker runtime:

```text
register
OTP state in Redis container
verification email through configured Gmail SMTP in intended environment/manual test
verify email
login
refresh
logout
```

Automated CI should not require real Gmail delivery; production/local manual smoke may.

---

## DOCKER-08 – Core Runtime Smoke Test

**Depends on:** DOCKER-07

Verify:

```text
project create
invitation flow
folder/category/tag
upload
download
search
delete
```

---

### M14 Gate

```text
✓ full Docker topology works
✓ postgres_data persists
✓ minio_data persists
✓ Redis is containerized and ephemeral
✓ Flyway runs against containerized PostgreSQL
✓ backend does not depend on host-local DB/Redis/MinIO
```

---

# 31. Milestone M15 – Full Verification / Core v1 Freeze

Goal:

```text
Prove Core v1 is stable before any AI/RAG work.
```

---

## VERIFY-01 – Full Unit Suite

Must pass:

```text
auth
OTP
JWT
authorization
service logic
file validation
mappers
storage adapter
mail adapter
error translation
```

---

## VERIFY-02 – PostgreSQL Integration Suite

Must pass:

```text
Flyway
Hibernate validate
constraints
repositories
transactions
concurrency
cross-project integrity
```

---

## VERIFY-03 – Redis OTP Integration Suite

Must pass:

```text
OTP state
TTL
attempt counter
cooldown
replacement/resend
delete after verify
Redis unavailable mapping
```

---

## VERIFY-04 – MinIO Integration Suite

Must pass:

```text
upload
download
range
stat
delete
batch delete
compensation
hard delete
```

---

## VERIFY-05 – Security / Authorization Matrix

Actors:

```text
non-member
MEMBER
OWNER
ADMIN
```

Test all SD-04 / SD-12 operations.

Also:

```text
unverified account cannot login
disabled account cannot use old JWT
```

---

## VERIFY-06 – Critical Workflow Suite

Must pass:

```text
register
verify OTP
login
refresh
logout

create project
creator OWNER

invite
new-user:
register
verify OTP
login
accept invitation

existing-user invite accept

folder/category/tag

upload
browse
search
preview/download
metadata update
delete

member removal
documents remain
former member loses access

project hard delete
```

---

## VERIFY-07 – Information Leakage Review

Ensure no API/log exposure of:

```text
password
password hash
JWT
raw refresh token
refresh hash
raw OTP
OTP protected/hash value
raw invitation token in logs
invitation token hash
Gmail App Password
MinIO credential
storageKey
SQL
stack trace
```

---

## VERIFY-08 – Docker Persistence Review

Verify:

```text
PostgreSQL durable through postgres_data
MinIO durable through minio_data
Redis intentionally ephemeral
Flyway history durable with PostgreSQL
```

---

## VERIFY-09 – OpenAPI Review

All Core endpoints present.

No endpoints for:

```text
OTP login
MFA
password reset
AI
RAG
embedding
document versioning
public sharing
ownership transfer
comments
```

---

## VERIFY-10 – Architecture Review

Search codebase for violations:

```text
Controller → Repository

Controller → MinIO

Controller → Redis

Controller → SMTP

project authorization in JwtFilter

Redis used for refresh session

OTP JPA entity/table

raw OTP persistence

Gmail implementation imported directly by AuthService/InvitationService

MinIO SDK outside storage adapter

Project.ownerId

Document.uploadedBy → ProjectMember

@ManyToMany replacing DocumentTag

CascadeType.ALL misuse

ddl-auto=update in production config

host-local Redis/PostgreSQL/MinIO assumptions

AI code
```

---

## VERIFY-11 – Release Test Gate

Per SD-12:

```text
all unit tests
all PostgreSQL integration tests
all Redis OTP integration tests
all security tests
all API integration tests
all MinIO integration tests
migration validation
OpenAPI contract validation
critical workflow tests
hard-delete tests
compensation/failure tests
Docker persistence smoke tests
```

---

## VERIFY-12 – Core v1 Freeze

Core v1 is frozen only when the full Specification Definition of Done is satisfied.

After freeze:

```text
Core v1 stable
```

Only then start separate AI/RAG design.

---

# 32. Recommended Harness Task Selection Logic

Pseudo-code:

```text
for each task:
    if every dependency == DONE:
        task = READY

select smallest READY task

inject:
    task definition
    listed source docs
    current repository files relevant to task
    current execution state
    required test commands

agent implements

run tests

if missing context:
    retrieve docs/history
    do not guess

if tests fail:
    TEST_FAILED
    fix same task

review

if architecture/business issue:
    REVIEW_FAILED
    fix same task

else:
    DONE
```

---

# 33. Context Loading Strategy

Do not inject all design documents for every task.

Load:

```text
current task
+
task-relevant SD files
+
relevant current source files
+
latest task state
```

Examples:

OTP task:

```text
SD-01
SD-04
SD-05
SD-07
SD-08
SD-10
SD-12
```

Database task:

```text
SD-02
SD-03
SD-06
SD-12
```

Invitation task:

```text
SD-01
SD-04
SD-07
SD-08
SD-10
SD-12
```

Document upload task:

```text
SD-01
SD-03
SD-04
SD-06
SD-07
SD-08
SD-09
SD-10
SD-12
```

Docker task:

```text
SD-01
SD-03
SD-05
SD-07
SD-09
SD-12
```

---

# 34. Task-Specific Source Documents

Recommended defaults:

| Task Area | Required Docs |
|---|---|
| Bootstrap | SD-05 |
| Docker runtime | SD-01, SD-03, SD-05, SD-07, SD-09, SD-12 |
| Flyway / DB | SD-02, SD-03, SD-06, SD-12 |
| JPA / Repository | SD-02, SD-03, SD-06, SD-12 |
| Exception | SD-04, SD-10, SD-12 |
| Redis OTP | SD-01, SD-04, SD-05, SD-07, SD-08, SD-10, SD-12 |
| Gmail SMTP | SD-01, SD-05, SD-08, SD-10, SD-12 |
| JWT/Auth | SD-01, SD-04, SD-07, SD-08, SD-10, SD-12 |
| User | SD-01, SD-04, SD-06, SD-08, SD-10, SD-12 |
| Project/Membership | SD-01, SD-02, SD-04, SD-06, SD-08, SD-12 |
| Invitation | SD-01, SD-03, SD-04, SD-07, SD-08, SD-10, SD-12 |
| Folder/Category/Tag | SD-01, SD-02, SD-03, SD-04, SD-06, SD-08, SD-12 |
| MinIO | SD-04, SD-08, SD-09, SD-10, SD-12 |
| Documents | SD-01, SD-03, SD-04, SD-06, SD-07, SD-08, SD-09, SD-10, SD-12 |
| Search | SD-04, SD-06, SD-08, SD-12 |
| OpenAPI | SD-04, SD-07, SD-10, SD-11, SD-12 |

---

# 35. Scope Guard

Explicitly forbidden without a new approved design phase:

```text
OTP login
MFA / 2FA
password-reset OTP
forgot-password workflow

AI chatbot
RAG
embedding
vector search
content indexing
video STT/transcription

document version history
comments
favorites
public share links
ownership transfer
audit dashboard
approval workflow
storage quota

OAuth
Google login

Redis refresh-session migration
Redis caching layer unrelated to OTP

Kubernetes
Terraform
Terragrunt

S3 adapter implementation
```

---

# 36. Agent Review Checklist

Reviewer checks every task:

```text
[ ] Correct source docs loaded

[ ] Missing context was reloaded rather than guessed

[ ] No business rule changed

[ ] No unrelated scope added

[ ] Controller boundary respected

[ ] Service boundary respected

[ ] Repository boundary respected

[ ] Authorization backend-enforced

[ ] Project isolation preserved

[ ] DTO used instead of Entity response

[ ] Error codes consistent

[ ] Sensitive data not logged/exposed

[ ] OTP state only in Redis

[ ] Refresh session only in PostgreSQL

[ ] Gmail used only through MailService

[ ] MinIO used only through StorageService

[ ] Required tests added

[ ] Relevant integration tests pass
```

---

# 37. Test Gate by Change Type

## Pure unit/business logic

Required:

```text
target unit tests
relevant regressions
```

## Flyway / JPA / repository

Required:

```text
PostgreSQL Testcontainer
Flyway migration
Hibernate validate
repository tests
```

## Redis / OTP

Required:

```text
OtpService unit tests
Redis Testcontainer integration
TTL/attempt/cooldown tests
```

## Gmail mail adapter

Required:

```text
adapter unit tests
fake/local SMTP integration where practical
no real Gmail in CI
```

## Security

Required:

```text
security unit tests
filter-chain integration
auth API tests
```

## Storage / document

Required:

```text
unit
PostgreSQL integration
MinIO integration
compensation tests
```

## Docker runtime

Required:

```text
Compose smoke
named-volume persistence
Redis ephemeral behavior
Flyway startup
```

## OpenAPI

Required:

```text
/v3/api-docs contract tests
```

---

# 38. Commit / Checkpoint Strategy

Recommended:

```text
one logical task or tightly coupled task group per commit
```

Examples:

```text
feat(db): add core Flyway schema

feat(otp): add Redis-backed email verification

feat(mail): add Gmail SMTP mail adapter

feat(auth): require verified email before login

feat(project): create project with owner membership

feat(storage): add MinIO adapter

feat(document): add single document upload

test(document): verify upload compensation

chore(docker): persist postgres and minio volumes
```

Avoid milestone-sized commits.

---

# 39. Recommended First Execution Sequence

For a new/empty backend repository:

```text
M0-01
M0-02
M0-03
M0-04

BOOT-01
BOOT-02
BOOT-03
BOOT-04
BOOT-05

DB-01
DB-02
DB-03
DB-04
DB-05

JPA-01
JPA-02
JPA-03
JPA-04
JPA-05
JPA-06
JPA-07
JPA-08
JPA-09
JPA-10
JPA-11

ERR-01
ERR-02
ERR-03
ERR-04
ERR-05
ERR-06
ERR-07

REDIS-01
REDIS-02
REDIS-03
OTP-01

MAIL-01
MAIL-02
MAIL-03
MAIL-04
EXT-AUTH-TEST

SEC-01
SEC-02
SEC-03
SEC-04
SEC-05
SEC-06

AUTH-01
AUTH-02
AUTH-03
AUTH-04
AUTH-05
AUTH-06
AUTH-07
AUTH-08
AUTH-SEC-TEST

USER-01
USER-02
USER-03

PROJ-01
PROJ-02
PROJ-03

MEM-01
MEM-02
MEM-03
M7-TEST

INV-01
INV-02
INV-03
INV-04
INV-05
INV-06

FOLDER-01
FOLDER-02
FOLDER-03
FOLDER-04
CAT-01
TAG-01
ORG-TEST

STORAGE-01
STORAGE-02
STORAGE-03
STORAGE-04
STORAGE-05
STORAGE-06
STORAGE-07
STORAGE-08

DOC-01
DOC-02
DOC-03
DOC-04
DOC-05
DOC-06
DOC-07
DOC-08
DOC-09
DOC-10
PROJ-DELETE-01
DOC-11

SEARCH-01
SEARCH-02
SEARCH-03
SEARCH-04

API-DOC-01
API-DOC-02
API-DOC-03
API-DOC-04
API-DOC-05
API-DOC-06
API-DOC-07

DOCKER-01
DOCKER-02
DOCKER-03
DOCKER-04
DOCKER-05
DOCKER-06
DOCKER-07
DOCKER-08

VERIFY-01
VERIFY-02
VERIFY-03
VERIFY-04
VERIFY-05
VERIFY-06
VERIFY-07
VERIFY-08
VERIFY-09
VERIFY-10
VERIFY-11
VERIFY-12
```

---

# 40. Recommended Harness Handling of Technical Choices

Before implementation of a task that depends on a still-open technical choice:

```text
1. Inspect existing repository choice.
2. Check selected technical baseline from M0.
3. Check official compatibility if needed.
4. Record the selected technical option.
5. Continue without changing business semantics.
```

Examples:

```text
JWT library choice
springdoc version
Spring Data Redis serializer
MIME detection library
UUID generator annotation
```

These are implementation choices.

They must not modify:

```text
OTP flow
JWT semantics
permission model
API contract
data ownership
```

---

# 41. Implementation Definition of Done

Implementation Plan has been successfully executed only when:

```text
all milestone gates M0..M15 pass

source documents remain available

harness respects task dependencies

missing context is reloaded instead of guessed

PostgreSQL uses Flyway + postgres_data

MinIO uses minio_data

Redis OTP runs in container and remains ephemeral

Gmail SMTP is isolated behind MailService

email verification OTP is required before login

refresh sessions remain PostgreSQL-backed

invitation remains token-link based

project/document permission matrix passes

cross-project data leakage tests pass

hard-delete and compensation tests pass

OpenAPI matches runtime behavior

Core v1 contains no AI/RAG implementation
```

---

# 42. Final Rule

The execution principle is:

```text
Implementation may refine code.

Implementation may select compatible libraries.

Implementation may optimize internals after tests.

Implementation may NOT silently redefine the product.
```

If a required rule is missing:

```text
re-read the relevant design documents
and project/conversation history first.
```

If the rule still does not exist:

```text
treat it as an unresolved requirement,
not an invitation to guess.
```
