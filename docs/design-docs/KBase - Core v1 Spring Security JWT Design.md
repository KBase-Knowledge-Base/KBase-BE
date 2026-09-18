# KBase – Core v1
## Spring Security + JWT Design

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**Security:** Spring Security + JWT  
**Persistence:** PostgreSQL  
**Refresh Session Storage:** PostgreSQL  
**Email Verification OTP Store:** Redis  
**Email Delivery:** Gmail SMTP  
**Architecture:** Feature-first Modular Monolith  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa security architecture cho KBase Core v1.

Tài liệu kế thừa trực tiếp từ:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design
- REST API Specification
- Spring Boot Application Architecture + Module/Package Structure
- JPA Entity Mapping + Repository Design

Mục tiêu:

- Chốt authentication flow.
- Chốt registration email verification bằng OTP.
- Chốt Redis OTP storage boundary và Gmail SMTP delivery.
- Chốt JWT access-token design.
- Chốt refresh-token/session design.
- Chốt Spring Security filter chain.
- Chốt authentication principal.
- Chốt password hashing.
- Chốt 401 vs 403 handling.
- Chốt disabled-account behavior.
- Chốt logout/session revocation.
- Chốt ADMIN override.
- Chốt integration với project/document authorization.
- Chuẩn bị nền tảng cho implementation thực tế.

---

# 2. Core Security Principles

KBase Core v1 áp dụng các nguyên tắc:

```text
Authentication != Authorization
```

JWT chịu trách nhiệm:

```text
Who is the user?
Is this access token valid?
What is the user's system role?
```

Project authorization chịu trách nhiệm:

```text
Is this user a member of Project X?
Is the user OWNER or MEMBER?
```

Document authorization chịu trách nhiệm:

```text
Can this user modify/delete this Document?
```

Không nhồi project permission vào JWT.

---

# 3. Security Boundary

Core security model có 3 lớp:

```text
Layer 1
Authentication
JWT + Spring Security

Layer 2
System Authorization
ADMIN / USER

Layer 3
Domain Authorization
Project OWNER / MEMBER
Document uploader ownership
```

Ví dụ:

```text
User authenticated as USER
       ↓
belongs to Project A as MEMBER
       ↓
uploads Document X
       ↓
may modify Document X
       ↓
may not modify Document Y uploaded by another MEMBER
```

---

# 4. System Roles

System role:

```text
ADMIN
USER
```

Stored in:

```text
users.system_role
```

`ADMIN`:

```text
system-level administrative override
```

`USER`:

```text
normal authenticated account
```

OWNER is not a system role.

---

# 5. Project Roles

Project role:

```text
OWNER
MEMBER
```

Stored in:

```text
project_members.role
```

A user may be:

```text
Project A → OWNER
Project B → MEMBER
Project C → MEMBER
```

Therefore project role must not be placed permanently into access-token claims as a global role.

---

# 6. Authentication Components

Recommended components:

```text
SecurityConfig
JwtService
JwtAuthenticationFilter
CustomUserPrincipal
CustomUserDetailsService
PasswordEncoder
RestAuthenticationEntryPoint
RestAccessDeniedHandler

AuthService
EmailVerificationService
OtpService / OtpStore
RefreshSessionService
MailService
```

High-level:

```text
HTTP Request
     ↓
JwtAuthenticationFilter
     ↓
JwtService
     ↓
User lookup / principal
     ↓
SecurityContext
     ↓
Controller
     ↓
Service
     ↓
Domain authorization
```

---

# 7. Password Storage

Passwords must never be stored plaintext.

Recommended baseline:

```text
BCrypt
```

Spring bean:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Exact cost factor may be configured later based on deployment performance.

Do not:

```text
hash password using SHA-256 directly
store reversible encrypted passwords
log passwords
return password hash through DTO
```

---

# 8. Registration Security

Registration input:

```text
email
password
displayName
```

Flow:

```text
normalize email
    ↓
validate email
    ↓
check uniqueness
    ↓
hash password with PasswordEncoder
    ↓
create User
    systemRole = USER
    status = ACTIVE
    emailVerifiedAt = null
    ↓
generate verification OTP
    ↓
store OTP hash/state in Redis with TTL
    ↓
send raw OTP through Gmail SMTP
```

Public registration must never allow client to submit:

```text
systemRole = ADMIN
emailVerifiedAt
```

Registration does not automatically create a login session. User must verify email before login.

---

## 8.1 Email Verification OTP

Core v1 uses email OTP only for registration email verification.

It is **not**:

```text
OTP login
MFA / 2FA
password-reset OTP
invitation acceptance OTP
```

Baseline configurable values:

```text
OTP length: 6 numeric digits
TTL: 5 minutes
Resend cooldown: 60 seconds
Maximum verification attempts: 5
```

Storage boundary:

```text
Redis container
    ↓
OTP hash
attempt count
TTL
resend cooldown
```

Raw OTP must never be stored in PostgreSQL or logs. Because a 6-digit OTP has low entropy, implementation should store a keyed/peppered hash rather than a reversible/raw value.

Persistent verification result:

```text
users.email_verified_at
```

Successful verification sets the timestamp and invalidates Redis OTP state.

Redis does not replace `refresh_sessions`. Refresh token session state remains PostgreSQL-backed.

Gmail SMTP sends the OTP to the registration email.

---

# 9. Login Flow

Endpoint:

```http
POST /api/v1/auth/login
```

Flow:

```text
email + password
      ↓
normalize email
      ↓
load User
      ↓
verify password
      ↓
check User.status == ACTIVE
      ↓
check User.emailVerifiedAt != null
      ↓
create Access Token
      ↓
create Refresh Token
      ↓
store RefreshSession(token hash)
      ↓
send refresh token in HttpOnly cookie
      ↓
return access token in JSON
```

---

# 10. Login Failure Behavior

Login must not reveal whether account exists.

Use generic error:

```text
401 INVALID_CREDENTIALS
```

for:

```text
unknown email
wrong password
```

After credentials are valid, an account that has not completed email verification returns:

```text
403 EMAIL_NOT_VERIFIED
```

Disabled account may return:

```text
403 ACCOUNT_DISABLED
```

Do not expose password validation details or OTP state details.

---

# 11. Access Token

Access token baseline:

```text
JWT
```

Initial TTL proposal already established:

```text
15 minutes
```

TTL remains configurable.

Client sends:

```http
Authorization: Bearer <access-token>
```

Access token is not persisted in PostgreSQL.

---

# 12. Why Access Token Is Short-Lived

Short TTL limits exposure if token is stolen.

Core v1 behavior:

```text
access token remains valid until expiration
```

unless a stronger revocation mechanism is later introduced.

Logout therefore primarily revokes refresh ability.

---

# 13. Access Token Claims

Recommended minimal claims:

```text
sub
userId
systemRole
iat
exp
jti
```

Possible interpretation:

```text
sub = normalized email or user identifier
userId = UUID
systemRole = ADMIN / USER
iat = issued at
exp = expiry
jti = unique token identifier
```

Avoid putting:

```text
project membership list
project roles
document permissions
folder permissions
```

inside JWT.

Those values can change while token remains valid.

---

# 14. Preferred JWT Subject

Recommended baseline:

```text
sub = userId
```

rather than email.

Reason:

```text
user ID is stable identity
email is business identity and may evolve later
```

Even though email change is not currently included in Core v1, UUID remains the cleaner subject.

Optional claims:

```text
email
```

may be included for convenience but must not become the authorization source of truth.

---

# 15. JWT Signing

Core v1 should use a cryptographically strong signing key.

Two broad options:

```text
HMAC secret
```

or:

```text
asymmetric RSA/EC key pair
```

For a single Spring Boot backend in Core v1:

```text
HMAC with sufficiently strong secret
```

is a reasonable implementation baseline.

If multiple services later need independent verification:

```text
asymmetric signing
```

may become preferable.

Exact algorithm is an implementation decision to finalize with the security library/version.

---

# 16. JWT Secret Configuration

JWT signing material must come from configuration/environment.

Example concept:

```text
KBASE_JWT_SECRET
```

Never commit:

```text
JWT secret
private key
production signing key
```

into Git.

---

# 17. JwtService Responsibilities

`JwtService` should handle:

```text
generate access token
parse token
verify signature
verify expiration
extract userId
extract systemRole
extract token ID if used
```

It should not:

```text
check ProjectMember
check document ownership
query MinIO
send email
```

---

# 18. JwtAuthenticationFilter

Recommended:

```text
OncePerRequestFilter
```

Responsibilities:

```text
read Authorization header
verify Bearer format
validate token
extract user identity
load/construct principal
set Authentication in SecurityContext
continue filter chain
```

Should not:

```text
return project business errors
check folder ownership
check document uploader
```

---

# 19. Access Token Validation

Validation should include:

```text
correct signature
not expired
expected token structure
required subject/userId
valid systemRole
```

Additionally, Core v1 should verify current account state before allowing request.

This matters because:

```text
user may be DISABLED after token was issued
```

JWT should only be issued after email verification. Runtime principal validation should still treat verified email as part of a usable account state.

---

# 20. Disabled Account Handling

Business rule already established:

```text
DISABLED user
- cannot login
- cannot refresh token
- cannot use the system
```

Therefore access-token authentication should not rely solely on claims.

Recommended baseline:

```text
JWT valid
   ↓
load User by userId
   ↓
User.status == ACTIVE?
   ↓
User.emailVerifiedAt != null?
   ↓
yes → authenticate
no  → reject
```

This adds a DB lookup for authenticated requests but preserves immediate account disabling semantics.

---

# 21. Principal Model

Recommended:

```java
public class CustomUserPrincipal {
    UUID userId;
    String email;
    SystemRole systemRole;
    UserStatus status;
}
```

It may implement:

```text
UserDetails
```

or be wrapped inside a Spring `Authentication`.

Important values:

```text
userId
systemRole
status
```

---

# 22. Granted Authorities

System authorities:

```text
ROLE_ADMIN
ROLE_USER
```

Derived from:

```text
User.systemRole
```

Do not add:

```text
ROLE_OWNER
ROLE_MEMBER
```

globally.

OWNER/MEMBER are project-scoped roles and belong to database/domain authorization.

---

# 23. UserDetailsService

`CustomUserDetailsService` may provide:

```text
load user by userId
```

for JWT authentication.

Spring's traditional:

```text
loadUserByUsername(email)
```

may still be used during username/password authentication, but JWT runtime identity should prefer `userId`.

---

# 24. SecurityContext

After JWT authentication:

```text
SecurityContext
```

contains authenticated principal.

Controller/service retrieves current user identity through:

```text
@AuthenticationPrincipal
```

or a shared current-user helper.

Avoid repeatedly parsing JWT manually inside controllers.

---

# 25. Current User Abstraction

Recommended helper:

```text
CurrentUserService
```

or equivalent lightweight abstraction:

```text
getCurrentUserId()
getCurrentPrincipal()
```

This avoids repeated Spring Security boilerplate.

However it must not become a large domain service.

---

# 26. Spring Security Filter Chain

Conceptual configuration:

```text
CSRF strategy
CORS
session management
public endpoints
admin endpoints
authenticated endpoints
JWT filter
exception handlers
```

Baseline:

```text
SessionCreationPolicy.STATELESS
```

for access-token authentication.

---

# 27. Stateless Access Authentication

Do not use server HTTP session for normal API authentication.

Use:

```text
JWT access token
```

Each request authenticates independently.

Refresh session stored in PostgreSQL is not the same as an HTTP server session.

---

# 28. Public Endpoints

Core v1 public endpoints:

```text
POST /api/v1/auth/register
POST /api/v1/auth/verify-email
POST /api/v1/auth/resend-verification-otp
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

Logout may depend on refresh cookie/session rather than access JWT.

Swagger/OpenAPI endpoints may also be public in development.

Production Swagger exposure can be configured separately.

---

# 29. Invitation Acceptance Endpoint

Endpoint:

```text
POST /api/v1/invitations/accept
```

requires:

```text
authenticated ACTIVE user
```

Reason:

Invitation business rule requires:

```text
current user's normalized email
==
invitation email
```

Unauthenticated acceptance would not satisfy this rule safely.

---

# 30. Admin Endpoint Rules

Endpoints:

```text
/api/v1/admin/**
```

require:

```text
SystemRole.ADMIN
```

Spring Security can enforce:

```text
hasRole("ADMIN")
```

or equivalent.

Administrative override for normal project/document endpoints is still handled by domain authorization service where relevant.

---

# 31. General Authenticated Endpoints

Most Core APIs require authentication:

```text
/api/v1/users/**
/api/v1/projects/**
/api/v1/documents/**
```

Spring Security only checks:

```text
authenticated + active account
```

Then domain service checks resource permissions.

---

# 32. Why Not Encode All Authorization in SecurityConfig

SecurityConfig should not contain dynamic rules such as:

```text
user owns this specific project
user uploaded this document
```

Those depend on database resource state.

Use:

```text
ProjectAuthorizationService
DocumentAuthorizationService
```

inside service layer.

---

# 33. ProjectAuthorizationService

Responsibilities:

```text
requireProjectMember
requireProjectOwner
getMembership
hasProjectAccess
```

ADMIN override:

```text
if systemRole == ADMIN
    allow
```

Example logic:

```text
requireProjectMember(projectId, principal)

if principal.systemRole == ADMIN:
    return administrative access

membership =
    projectMemberRepository.findByProjectIdAndUserId(...)

if missing:
    throw PROJECT_ACCESS_FORBIDDEN
```

---

# 34. Project OWNER Check

Conceptual:

```text
if ADMIN:
    allow

membership = current user's project membership

if membership.role != OWNER:
    reject PROJECT_MANAGEMENT_FORBIDDEN
```

Do not derive ownership from:

```text
Project.ownerId
```

because that field intentionally does not exist.

---

# 35. DocumentAuthorizationService

Read:

```text
ADMIN
or any current project member
```

Modify/delete:

```text
ADMIN
or OWNER
or MEMBER when uploadedByUserId == currentUserId
```

Pseudo-code:

```text
if ADMIN:
    ALLOW

membership = requireProjectMember(document.projectId)

if membership.role == OWNER:
    ALLOW

if membership.role == MEMBER
and document.uploadedBy.id == currentUser.id:
    ALLOW

DENY
```

---

# 36. Former Member Behavior

Previously agreed rule:

```text
member leaves / gets removed
documents remain
```

After membership deletion:

```text
former member still exists as User
document.uploadedBy still points to User
```

But read/write permission now fails because:

```text
no ProjectMember
```

Therefore former member cannot access the old files.

OWNER retains management rights.

---

# 37. ADMIN Override

ADMIN may:

```text
view project
manage project
manage documents
manage membership
```

without ProjectMember row.

Domain authorization services must consistently check:

```text
systemRole == ADMIN
```

before normal project membership logic.

Do not create fake `ProjectMember` rows for ADMIN just to grant access.

---

# 38. Refresh Token

Refresh token is a high-entropy opaque token.

Recommended:

```text
random cryptographically secure value
```

It does not need to be JWT.

Opaque token is simpler because server already stores refresh-session state.

---

# 39. Why Opaque Refresh Token

Access token:

```text
self-contained JWT
```

Refresh token:

```text
server-tracked session credential
```

Using an opaque random token:

```text
avoids putting unnecessary claims into refresh token
makes revocation straightforward
```

---

# 40. RefreshSession Persistence

Database table:

```text
refresh_sessions
```

Fields:

```text
id
user_id
token_hash
expires_at
revoked_at
created_at
```

Store:

```text
hash(raw refresh token)
```

Never store raw refresh token.

---

# 41. Refresh Token Generation

Concept:

```text
SecureRandom
    ↓
32+ bytes random entropy
    ↓
URL-safe encoding
```

Exact implementation can use Java cryptographic APIs.

Do not generate using:

```text
UUID.randomUUID() alone
Math.random()
timestamp
predictable strings
```

for security tokens.

---

# 42. Refresh Token Hashing

A deterministic cryptographic hash such as:

```text
SHA-256
```

is appropriate for high-entropy random refresh tokens.

Reason:

Unlike passwords, refresh tokens are already random and high-entropy.

Flow:

```text
raw token
   ↓
SHA-256
   ↓
hex/base64 hash
   ↓
refresh_sessions.token_hash
```

Password hashing and token hashing have different requirements.

---

# 43. Refresh Cookie

Baseline already established:

```text
Name: kbase_refresh_token
HttpOnly: true
Secure: true in production
SameSite: Lax
Path: /api/v1/auth
```

Recommended also:

```text
Max-Age aligned with refresh TTL
```

Do not make refresh cookie available to frontend JavaScript.

---

# 44. Why Access Token Is Not in HttpOnly Cookie

Core v1 baseline established earlier:

```text
Access Token
→ JSON response / frontend memory

Refresh Token
→ Secure HttpOnly Cookie
```

Frontend sends access token using:

```http
Authorization: Bearer ...
```

This separates:

```text
short-lived API credential
long-lived refresh credential
```

---

# 45. CSRF Consideration

Normal protected APIs use:

```text
Authorization Bearer token
```

rather than automatically attached authentication cookies.

Therefore normal API requests are less exposed to classic cookie-auth CSRF.

However:

```text
/auth/refresh
/auth/logout
```

use the automatically attached refresh cookie.

`SameSite=Lax` provides a baseline protection.

If deployment introduces cross-site frontend/backend requirements or weaker SameSite settings, explicit CSRF protection for refresh/logout must be revisited.

---

# 46. CORS

CORS must be explicitly configured.

Allow only configured frontend origins.

Example configuration concept:

```text
KBASE_ALLOWED_ORIGINS
```

Development:

```text
http://localhost:3000
```

or frontend dev port.

Production:

```text
https://kbase.example.com
```

Do not use:

```text
allowCredentials=true
+
allowedOrigins="*"
```

because refresh cookies require credentials and unrestricted origins are unsafe.

---

# 47. Refresh Flow

Endpoint:

```http
POST /api/v1/auth/refresh
```

Flow:

```text
browser sends HttpOnly refresh cookie
      ↓
extract raw token
      ↓
hash token
      ↓
find RefreshSession
      ↓
session exists?
      ↓
not revoked?
      ↓
not expired?
      ↓
User exists and ACTIVE?
      ↓
email verified?
      ↓
issue new access token
```

Response:

```text
new access token
```

---

# 48. Refresh Token Rotation

Refresh-token rotation has not been fixed as a mandatory Core v1 rule.

Baseline Core v1:

```text
same active refresh session may issue new access tokens
until revoked or expired
```

Optional security enhancement later:

```text
rotation on every refresh
old token revoked
new refresh token + session state issued
reuse detection
```

Do not treat rotation as already agreed.

---

# 49. Logout Flow

Endpoint:

```http
POST /api/v1/auth/logout
```

Flow:

```text
read refresh cookie
      ↓
hash token
      ↓
find session
      ↓
set revoked_at = now
      ↓
clear refresh cookie
```

Response:

```text
204 No Content
```

Access token already issued may remain valid until its short expiration.

---

# 50. Logout Idempotency

Recommended:

```text
logout should be effectively idempotent
```

If refresh cookie is already missing/invalid:

```text
clear cookie
return 204
```

rather than exposing session existence unnecessarily.

This is an API/security baseline recommendation.

---

# 51. Multi-Device Sessions

Current schema supports:

```text
User 1 → N RefreshSession
```

Therefore user may login from:

```text
browser A
browser B
device C
```

Logout current session should revoke only the current refresh session.

A future:

```text
logout all devices
```

can revoke all user refresh sessions, but is not currently required.

---

# 52. Password Change

Current API includes:

```text
PUT /api/v1/users/me/password
```

Security flow:

```text
verify current password
      ↓
validate new password
      ↓
hash new password
      ↓
save User
```

Recommended security behavior:

```text
revoke existing refresh sessions after successful password change
```

This remains a security baseline recommendation rather than a previously explicit domain requirement.

A new login may then be required.

---

# 53. Admin Disable User

When ADMIN sets:

```text
status = DISABLED
```

recommended behavior:

```text
revoke all refresh sessions for that user
```

Even if session rows are not immediately revoked, refresh endpoint must reject because User.status != ACTIVE.

Existing access tokens also stop working because authentication filter checks current account status.

---

# 54. Re-enable User

When status returns:

```text
ACTIVE
```

previously revoked sessions should remain revoked.

User should login again.

Do not silently reactivate old refresh sessions.

---

# 55. Token Expiration Errors

Expired access token:

```text
401 ACCESS_TOKEN_EXPIRED
```

Invalid/malformed token:

```text
401 INVALID_ACCESS_TOKEN
```

Missing access token on protected endpoint:

```text
401 AUTHENTICATION_REQUIRED
```

These may be represented through a smaller stable error-code set if desired, but behavior should remain distinguishable enough for frontend refresh logic.

---

# 56. 401 vs 403

Use:

```text
401 Unauthorized
```

when:

```text
no authentication
invalid access token
expired access token
invalid refresh token
expired refresh token
revoked refresh session
```

Use:

```text
403 Forbidden
```

when:

```text
authenticated user lacks permission
account disabled
not project member
not project owner
member modifying another member's document
```

---

# 57. AuthenticationEntryPoint

`RestAuthenticationEntryPoint` handles unauthenticated access.

Response:

```json
{
  "timestamp": "...",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "message": "Authentication is required.",
  "path": "/api/v1/..."
}
```

It must return JSON consistent with KBase standard error format.

---

# 58. AccessDeniedHandler

`RestAccessDeniedHandler` handles Spring Security-level authorization denial.

Response:

```text
403
```

with standard API error structure.

Domain-level forbidden cases are usually raised from application services through business exceptions.

---

# 59. Domain Authorization Errors

Examples:

```text
PROJECT_ACCESS_FORBIDDEN
PROJECT_MANAGEMENT_FORBIDDEN
DOCUMENT_MODIFICATION_FORBIDDEN
TAG_MANAGEMENT_FORBIDDEN
```

These are not generic Spring Security errors.

They should come from:

```text
ProjectAuthorizationService
DocumentAuthorizationService
business service
```

and be mapped by:

```text
GlobalExceptionHandler
```

---

# 60. SecurityConfig Endpoint Rules

Conceptual baseline:

```text
permitAll:
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh

authenticated:
everything else

ADMIN:
 /api/v1/admin/**
```

Logout can be allowed based on refresh cookie handling.

Swagger paths can be configured per environment.

---

# 61. Method Security

Spring method security such as:

```text
@PreAuthorize
```

may be used for simple system-role rules:

```java
@PreAuthorize("hasRole('ADMIN')")
```

For project/document business authorization, prefer explicit domain services rather than large SpEL expressions.

Avoid:

```text
@PreAuthorize with complex repository calls embedded everywhere
```

because it becomes difficult to debug and test.

---

# 62. Recommended SecurityConfig Shape

Conceptual:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) {

    http
        .csrf(...)
        .cors(...)
        .sessionManagement(...)
        .authorizeHttpRequests(...)
        .exceptionHandling(...)
        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
}
```

Exact Spring Security DSL depends on chosen Spring Boot version.

---

# 63. JWT Filter Skip Rules

The filter may simply attempt authentication only when a Bearer header exists.

Public endpoints do not require special business behavior inside filter.

Malformed Bearer token should not be silently treated as anonymous on protected endpoints.

---

# 64. User Lookup Per Request

Recommended Core v1 behavior:

```text
JWT
↓
extract userId
↓
UserRepository.findById
↓
verify ACTIVE
↓
build principal
```

Benefit:

```text
immediate user disable enforcement
latest system role
```

Cost:

```text
one user lookup per authenticated request
```

For Core v1 this tradeoff is acceptable.

Later performance optimization could use caching if measured necessary.

---

# 65. Role Freshness

Because current User is loaded per request:

```text
system role changes
```

take effect immediately rather than waiting for token expiration.

JWT `systemRole` claim can still be checked for token integrity/context, but database user record should remain authoritative.

---

# 66. Project Permission Freshness

Project role is always queried from:

```text
project_members
```

Therefore:

```text
MEMBER removed
```

loses access immediately.

```text
MEMBER promoted/demoted
```

would also reflect immediately if such role modification is introduced later.

No token reissue required.

---

# 67. Resource Lookup and Information Leakage

Core REST specification currently distinguishes:

```text
404 resource not found
403 resource exists but access forbidden
```

Security implementation should remain consistent with that existing API baseline.

A future hardened mode could intentionally return 404 for inaccessible resources to reduce enumeration, but that would change the API contract and should be decided explicitly.

---

# 68. JWT Library

Use a maintained JWT library compatible with the selected Spring Boot version.

Possible approaches include:

```text
JJWT
Nimbus JOSE + JWT
Spring Security OAuth2 JOSE support
```

No exact library has yet been fixed.

The architecture should hide JWT implementation details behind:

```text
JwtService
```

---

# 69. Security Package Structure

Recommended:

```text
security
├── config
│   └── SecurityConfig.java
│
├── jwt
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   └── JwtProperties.java
│
├── principal
│   ├── CustomUserPrincipal.java
│   └── CustomUserDetailsService.java
│
├── handler
│   ├── RestAuthenticationEntryPoint.java
│   └── RestAccessDeniedHandler.java
│
└── service
    └── CurrentUserService.java
```

PasswordEncoder bean may live in:

```text
SecurityConfig
```

---

# 70. Auth Package Interaction

```text
auth
├── AuthController
├── AuthService
├── RefreshSessionService
├── RefreshSessionRepository
└── RefreshSession
```

`AuthService` depends on:

```text
UserRepository
PasswordEncoder
JwtService
RefreshSessionService
```

Security package must not depend on AuthController.

---

# 71. RefreshSessionService

Responsibilities:

```text
create refresh session
find/validate refresh session
revoke current session
revoke all user sessions when required
cleanup expired sessions if later scheduled
```

It should encapsulate:

```text
token hashing
session persistence
```

---

# 72. Refresh Session Creation

Conceptual:

```text
generateRawRefreshToken()
      ↓
hashRefreshToken()
      ↓
RefreshSession {
  user,
  tokenHash,
  expiresAt,
  createdAt
}
      ↓
save
      ↓
return raw token to AuthService only
```

Raw token must not be logged.

---

# 73. Token Cleanup

Expired refresh sessions may accumulate.

Possible future cleanup:

```text
scheduled job
delete where expires_at < now
```

Current schema already supports:

```text
deleteByExpiresAtBefore
```

However a scheduled cleanup process is not a mandatory Core v1 requirement unless explicitly added later.

---

# 74. Security Logging

Log:

```text
login success at safe metadata level
login failure
invalid token attempt
disabled account access
refresh failure
admin disable/enable
logout
security exceptions
```

Do not log:

```text
password
password hash
raw access token
raw refresh token
JWT signing secret
raw invitation token
raw verification OTP
Redis OTP hash/state
Gmail SMTP App Password
cookie value
```

---

# 75. Brute-Force Protection

General login rate limiting / account lockout has not yet been included in Core v1 requirements.

Email verification OTP is a separate flow and does include configurable OTP attempt limit + resend cooldown stored in Redis.

Do not silently invent a general login rule such as:

```text
5 failed password attempts → lock user
```

Possible later security hardening:

```text
rate limiting
IP throttling
progressive delays
temporary login lockout
```

This should be introduced explicitly if required.

---

# 76. Email Verification

Core v1 requires registration email verification.

Flow:

```text
Register
    ↓
User persisted with email_verified_at = NULL
    ↓
Generate OTP
    ↓
Store OTP hash/attempt state in Redis with TTL
    ↓
Send OTP through Gmail SMTP
    ↓
POST /auth/verify-email
    ↓
users.email_verified_at = now
    ↓
Redis OTP state invalidated
```

Resend:

```text
POST /auth/resend-verification-otp
    ↓
Redis cooldown check
    ↓
replace previous OTP state
    ↓
Gmail SMTP sends new OTP
```

Project invitation remains a separate mechanism. Invitation email contains a secure invitation token link. OTP does not replace the invitation token.

If an invited email does not yet have an account:

```text
register
→ verify email OTP
→ login
→ accept invitation token
```

---

# 77. Forgot Password

Password reset / forgot-password flow is not currently part of Core v1.

Do not add endpoints, token tables, or email flow silently.

If added later, design should use:

```text
single-use high-entropy reset token
hashed server-side
short expiration
```

but this is outside current scope. Existing registration OTP must not be reused as a password-reset credential.

---

# 78. Security for File Download

Download flow:

```text
access token
    ↓
JWT authentication
    ↓
Document lookup
    ↓
DocumentAuthorizationService.requireReadPermission
    ↓
StorageService
    ↓
binary stream / short-lived presigned URL
```

Never accept:

```text
storageKey from user
```

as proof of authorization.

---

# 79. Presigned URL Security

If short-lived MinIO presigned URLs are used:

```text
authorize first
generate short-lived URL second
```

Do not expose permanent public URLs.

URL expiration should be short and configurable.

Exact presigned-url TTL has not been fixed.

---

# 80. Upload Security

Authentication/authorization occurs before file persistence.

Flow:

```text
authenticate
   ↓
require project membership
   ↓
validate file metadata
   ↓
upload
```

Member can upload to any accessible project, subject to existing folder/category/tag rules.

---

# 81. Project Delete Security

Only:

```text
OWNER
ADMIN
```

may delete project.

Security check must occur before loading/deleting MinIO resources.

Project delete is a business service operation, not a SecurityFilterChain responsibility.

---

# 82. Member Removal Security

Only:

```text
OWNER
ADMIN
```

may remove member.

OWNER cannot remove self through member-removal endpoint.

Core v1 has no ownership transfer.

These rules remain inside:

```text
ProjectMemberService
```

with authorization assistance from `ProjectAuthorizationService`.

---

# 83. Tag Security

MEMBER:

```text
may create tag
may use tag on own document
cannot rename/delete shared tag
```

OWNER/ADMIN:

```text
may rename/delete shared tag
```

System security only authenticates user.

TagService enforces project role.

---

# 84. Folder / Category Security

MEMBER:

```text
view only
```

OWNER/ADMIN:

```text
create
rename
move/delete where applicable
```

Use:

```text
ProjectAuthorizationService.requireProjectOwner
```

---

# 85. API Error Examples

Missing access token:

```json
{
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "message": "Authentication is required."
}
```

Expired token:

```json
{
  "status": 401,
  "code": "ACCESS_TOKEN_EXPIRED",
  "message": "Access token has expired."
}
```

Disabled account:

```json
{
  "status": 403,
  "code": "ACCOUNT_DISABLED",
  "message": "This account is disabled."
}
```

Project denial:

```json
{
  "status": 403,
  "code": "PROJECT_ACCESS_FORBIDDEN",
  "message": "You do not have access to this project."
}
```

Document mutation denial:

```json
{
  "status": 403,
  "code": "DOCUMENT_MODIFICATION_FORBIDDEN",
  "message": "You do not have permission to modify this document."
}
```

---

# 86. Security Error Code Baseline

Authentication:

```text
AUTHENTICATION_REQUIRED
INVALID_ACCESS_TOKEN
ACCESS_TOKEN_EXPIRED
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
OTP_SERVICE_UNAVAILABLE
ACCOUNT_DISABLED
```

Refresh:

```text
REFRESH_TOKEN_MISSING
INVALID_REFRESH_TOKEN
REFRESH_TOKEN_EXPIRED
REFRESH_SESSION_REVOKED
```

Authorization:

```text
PROJECT_ACCESS_FORBIDDEN
PROJECT_MANAGEMENT_FORBIDDEN
DOCUMENT_MODIFICATION_FORBIDDEN
TAG_MANAGEMENT_FORBIDDEN
```

Password:

```text
CURRENT_PASSWORD_INVALID
```

---

# 87. Test Strategy – Authentication

Must test:

```text
register hashes password
register cannot create ADMIN
register creates emailVerifiedAt = null
register writes OTP state to Redis
register sends OTP through MailService/Gmail SMTP

login before email verification fails with EMAIL_NOT_VERIFIED
correct OTP verifies email
wrong/expired OTP fails
resend replaces OTP and respects cooldown
correct login succeeds after verification
wrong password fails
unknown email fails without revealing existence
disabled account cannot login

valid access token authenticates
expired access token returns 401
malformed access token returns 401
tampered signature returns 401

refresh succeeds with active session
expired refresh fails
revoked refresh fails
disabled user cannot refresh

logout revokes refresh session
logout clears cookie
```

---

# 88. Test Strategy – Authorization

Must test:

```text
non-member cannot access project

MEMBER can read project
MEMBER cannot update/delete project

OWNER can update/delete project

MEMBER can read all project documents
MEMBER can modify own document
MEMBER cannot modify another member's document

OWNER can modify all project documents

former MEMBER loses access immediately after membership deletion

ADMIN can access project without ProjectMember row

MEMBER cannot manage folder/category
MEMBER can create tag
MEMBER cannot rename/delete tag

OWNER cannot leave project
```

---

# 89. Test Strategy – Disabled Account

Scenario:

```text
1. User logs in.
2. User receives access + refresh tokens.
3. ADMIN disables user.
4. Old access token is used.
5. Request must fail.
6. Refresh must fail.
7. User cannot login.
```

This test confirms current-user DB lookup is enforcing account status.

---

# 90. Integration Testing

Recommended:

```text
Spring Boot Test
PostgreSQL Testcontainers
Redis Testcontainer for email verification OTP
MockMvc or equivalent HTTP tests
```

Security integration tests should exercise:

```text
real filter chain
real JWT validation
real membership queries
```

not only unit-test isolated methods.

---

# 91. Unit Testing

Useful unit targets:

```text
JwtService
RefreshSessionService
OtpService / RedisOtpStore
EmailVerificationService
ProjectAuthorizationService
DocumentAuthorizationService
AuthService
```

Mock cryptographic clock/time where expiration behavior needs deterministic testing.

---

# 92. Clock Abstraction

Security code depends heavily on time:

```text
access expiration
refresh expiration
invitation expiration
verification OTP expiration/cooldown
```

Recommended technical baseline:

```java
Clock
```

injected where practical.

This improves deterministic tests.

It is an implementation recommendation, not a business requirement.

---

# 93. Sensitive Configuration

Use environment variables or secret management.

Examples:

```text
KBASE_JWT_SECRET
KBASE_JWT_ACCESS_TTL
KBASE_JWT_REFRESH_TTL
KBASE_ALLOWED_ORIGINS
KBASE_REDIS_HOST
KBASE_REDIS_PORT
KBASE_OTP_TTL
KBASE_OTP_RESEND_COOLDOWN
KBASE_OTP_MAX_ATTEMPTS
KBASE_OTP_HASH_SECRET
KBASE_GMAIL_SMTP_USERNAME
KBASE_GMAIL_SMTP_APP_PASSWORD
```

Production secrets must not be committed.

---

# 94. Suggested Security Properties

Conceptual:

```yaml
kbase:
  security:
    jwt:
      access-token-ttl: 15m
      refresh-token-ttl: 7d
    cookie:
      refresh-name: kbase_refresh_token
      secure: true
      same-site: Lax
      path: /api/v1/auth
    otp:
      length: 6
      ttl: 5m
      resend-cooldown: 60s
      max-attempts: 5

    redis:
      host: redis
      port: 6379

    cors:
      allowed-origins:
        - https://kbase.example.com

spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${KBASE_GMAIL_SMTP_USERNAME}
    password: ${KBASE_GMAIL_SMTP_APP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

Environment overrides configuration.

---

# 95. Frontend Authentication Flow

Registration:

```text
Frontend
   ↓
POST /auth/register
   ↓
verification OTP arrives by Gmail
   ↓
POST /auth/verify-email
   ↓
emailVerified = true
```

Login:

```text
Frontend
   ↓
POST /auth/login
   ↓
receives accessToken JSON
+
browser receives HttpOnly refresh cookie
   ↓
store accessToken in frontend memory
```

API call:

```text
Authorization: Bearer accessToken
```

When access token expires:

```text
POST /auth/refresh
with credentials/cookie
   ↓
receive new access token
```

Logout:

```text
POST /auth/logout
   ↓
server revokes refresh session
   ↓
browser cookie cleared
   ↓
frontend deletes in-memory access token
```

---

# 96. Frontend Must Not Read Refresh Token

Because cookie is:

```text
HttpOnly
```

JavaScript cannot access it.

Frontend should not:

```text
copy refresh token to localStorage
store refresh token in Redux
read cookie manually
```

---

# 97. Access Token Storage

Current Core v1 baseline:

```text
frontend memory
```

Avoid long-lived browser persistence such as:

```text
localStorage
```

for access token unless a deliberate security tradeoff is later made.

Page reload can trigger refresh flow to obtain a fresh access token.

---

# 98. Security and Swagger

Swagger UI should support:

```text
Bearer JWT
```

for protected endpoints.

Do not place refresh token into Swagger global bearer authorization.

Refresh flow relies on cookie.

Development Swagger can test:

```text
register
verify-email / resend-verification-otp
login
copy accessToken
Authorize
call protected APIs
```

Cookie behavior may depend on browser/Swagger origin configuration.

---

# 99. Development vs Production

Development may use:

```text
HTTP
refresh cookie Secure=false
localhost CORS origin
```

Production:

```text
HTTPS
refresh cookie Secure=true
strict allowed origins
secure secret injection
```

Do not accidentally ship development cookie settings to production.

---

# 100. Security Design Summary

Core v1 security architecture:

```text
Password
→ BCrypt

Registration email verification
→ 6-digit OTP
→ Redis TTL state
→ Gmail SMTP delivery
→ users.email_verified_at persistent result

Access authentication
→ short-lived JWT

Refresh authentication
→ opaque random refresh token

Refresh state
→ PostgreSQL RefreshSession

Redis
→ OTP verification state only
→ does not store refresh sessions

Client storage
→ access token in memory
→ refresh token in HttpOnly cookie

System authorization
→ ADMIN / USER

Project authorization
→ ProjectMember OWNER / MEMBER

Document mutation authorization
→ ADMIN
  or OWNER
  or uploader MEMBER

Disabled account
→ blocked immediately through DB status check

Logout
→ revoke refresh session

MinIO
→ private
→ authorization before object access
```

---

# 101. Deliberately Not Added

Current Security Design does not add:

```text
OAuth / Google Login
forgot-password/reset-password
OTP-based login
MFA / 2FA
API keys
refresh-token rotation as mandatory
access-token blacklist
Redis refresh-session store
general login rate limiting
account lockout
CAPTCHA
ownership transfer
public sharing authentication
AI-specific authorization
```

These require separate specification decisions.

---

# 102. Recommended Baseline Decisions

For Core v1:

```text
Spring Security
BCrypt
JWT access token
15-minute configurable access TTL
opaque refresh token
7-day configurable refresh TTL
hashed refresh token in PostgreSQL
email verification OTP stored through Redis with TTL
Gmail SMTP sends registration OTP and invitation email
users.email_verified_at stores persistent verification result
HttpOnly refresh cookie
access token in frontend memory
stateless Spring Security
DB user lookup per authenticated request
ADMIN represented as system authority
OWNER/MEMBER checked from ProjectMember
project/document authorization handled in services
```

---

# 103. Security Definition of Done

Security design can be considered implemented when:

```text
passwords are securely hashed

public registration cannot escalate roles

registration generates Redis-backed verification OTP and sends it through Gmail SMTP

unverified user cannot login

correct OTP sets persistent email verification state

login returns short-lived access token

refresh token is HttpOnly and server-tracked

raw refresh token is never stored in DB

JWT filter authenticates valid tokens

disabled user is rejected even with old access token

401 and 403 are consistently separated

ADMIN endpoints require ADMIN

project access uses current ProjectMember data

document mutation enforces uploader/OWNER rules

former project member immediately loses access

logout revokes current refresh session

MinIO resources remain private

security errors use standard API response format

integration tests cover role and ownership boundaries
```

---

# Docker / Redis Security Boundary

Local Core v1 uses a Redis Docker service:

```text
backend → redis:6379
```

The backend must not depend on a Redis installation on the developer host.

Redis OTP data is intentionally ephemeral and does not require a durable Docker volume. A Redis restart may invalidate pending OTPs; resend generates a new OTP.

PostgreSQL and MinIO persistence are separate:

```text
postgres → postgres_data named volume
minio    → minio_data named volume
```

Gmail SMTP is external to Docker.

---

# 104. Next Phase

Recommended next phase:

```text
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

The immediate next artifact should be:

```text
KBase Core v1 – Service Layer Detailed Design
```

It should define:

```text
service responsibilities
method contracts
transaction boundaries
authorization calls
repository orchestration
cross-module dependencies
MinIO/email coordination
compensation behavior
business exception paths
```

without changing the domain and permission rules already fixed.
