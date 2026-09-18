# KBase – Core v1
## Exception Handling Design

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**Architecture:** Feature-first Modular Monolith  
**API Error Format:** Custom JSON error contract  
**Security:** Spring Security + JWT  
**Persistence:** PostgreSQL / Spring Data JPA  
**Storage:** MinIO through `StorageService`  
**Email:** through `MailService`  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa chiến lược Exception Handling cho KBase Core v1.

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

Mục tiêu:

- Chuẩn hóa exception hierarchy.
- Chuẩn hóa `ErrorCode`.
- Chuẩn hóa HTTP status mapping.
- Chuẩn hóa API error response.
- Tách business error khỏi infrastructure error.
- Xử lý validation error nhất quán.
- Xử lý authentication/authorization error nhất quán.
- Translate database constraint violations thành business-safe error.
- Translate MinIO/Email exception thành stable KBase error.
- Chuẩn hóa logging.
- Thêm request/correlation ID để debug.
- Không leak thông tin nhạy cảm ra frontend.

---

# 2. Exception Handling Principles

Core v1 tuân theo các nguyên tắc:

```text
1. Business error != Technical error.

2. API client receives stable ErrorCode.

3. Internal exception detail stays server-side.

4. Controller does not catch business exceptions manually.

5. GlobalExceptionHandler centralizes REST error mapping.

6. Spring Security authentication errors are handled by
   AuthenticationEntryPoint / AccessDeniedHandler.

7. Database constraints remain final data-integrity protection.

8. Service translates known technical failures into
   meaningful business/application exceptions.

9. Unknown errors return generic 500 response.

10. Never expose stack trace, SQL, secrets, token, storage internals.
```

---

# 3. Error Categories

KBase exceptions are divided into five categories:

```text
1. Validation
2. Authentication / Security
3. Domain / Business
4. Infrastructure / Persistence
5. Unexpected Internal Error
```

Examples:

```text
Validation
→ invalid email
→ blank project name
→ unsupported sort field

Authentication
→ invalid access token
→ expired refresh token

Business
→ folder not empty
→ member modifying another member's document

Infrastructure
→ MinIO unavailable
→ Gmail SMTP unavailable
→ Redis OTP store unavailable
→ DB connection failure

Unexpected
→ NullPointerException
→ unhandled bug
```

---

# 4. Standard API Error Response

Baseline response:

```json
{
  "timestamp": "2026-09-17T02:00:00Z",
  "status": 403,
  "code": "DOCUMENT_MODIFICATION_FORBIDDEN",
  "message": "You do not have permission to modify this document.",
  "path": "/api/v1/documents/8c3f...",
  "requestId": "01J..."
}
```

Fields:

| Field | Required | Meaning |
|---|---:|---|
| timestamp | ✅ | UTC ISO-8601 |
| status | ✅ | HTTP status |
| code | ✅ | Stable KBase error code |
| message | ✅ | Safe user-facing message |
| path | ✅ | Request path |
| requestId | ✅ | Correlation/request identifier |
| errors | ❌ | Field-level validation errors |

---

# 5. Validation Error Response

Example:

```json
{
  "timestamp": "2026-09-17T02:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "path": "/api/v1/auth/register",
  "requestId": "01J...",
  "errors": {
    "email": "Invalid email address",
    "password": "Password must contain between 8 and 64 characters"
  }
}
```

`errors` should only contain fields safe to expose.

---

# 6. ApiErrorResponse Model

Suggested model:

```java
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String requestId,
    Map<String, String> errors
) {}
```

For non-validation errors:

```text
errors = null
```

or omitted through serialization configuration.

---

# 7. ErrorCode

All stable API business codes live in one enum:

```text
shared.exception.ErrorCode
```

Conceptual:

```java
public enum ErrorCode {

    VALIDATION_ERROR(...),

    AUTHENTICATION_REQUIRED(...),
    INVALID_ACCESS_TOKEN(...),

    PROJECT_NOT_FOUND(...),
    PROJECT_ACCESS_FORBIDDEN(...),

    DOCUMENT_NOT_FOUND(...),
    DOCUMENT_MODIFICATION_FORBIDDEN(...),

    STORAGE_SERVICE_UNAVAILABLE(...),

    INTERNAL_SERVER_ERROR(...);
}
```

Each code may contain:

```text
code
defaultMessage
httpStatus
```

---

# 8. Why Centralize ErrorCode

Avoid scattered literals:

```java
throw new RuntimeException("PROJECT_NOT_FOUND");
```

Central enum provides:

```text
consistent spelling
consistent status
consistent default message
easy OpenAPI documentation
easy frontend mapping
easy testing
```

---

# 9. Error Message Policy

Error code is stable.

Message is safe and human-readable.

Example:

```text
code:
PROJECT_ACCESS_FORBIDDEN

message:
You do not have access to this project.
```

Do not put internal details into message:

```text
BAD:
FK project_members_user_id violated for user 97...
```

---

# 10. Exception Hierarchy

Recommended hierarchy:

```text
RuntimeException

KBaseException
├── BusinessException
├── ResourceNotFoundException
├── ForbiddenOperationException
└── InfrastructureException
```

Exact subclasses may be simplified during implementation.

Important goal:

```text
known application errors
must carry an ErrorCode
```

---

# 11. Base KBaseException

Conceptual:

```java
public abstract class KBaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected KBaseException(
        ErrorCode errorCode
    ) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    protected KBaseException(
        ErrorCode errorCode,
        String safeMessage
    ) {
        super(safeMessage);
        this.errorCode = errorCode;
    }
}
```

No HTTP object is stored in exception.

---

# 12. BusinessException

Used for business conflict/state errors.

Examples:

```text
EMAIL_ALREADY_EXISTS
PROJECT_MEMBER_ALREADY_EXISTS
INVITATION_ALREADY_PENDING
FOLDER_NOT_EMPTY
CATEGORY_IN_USE
TAG_NAME_ALREADY_EXISTS
OWNER_CANNOT_LEAVE_PROJECT
```

Most map to:

```text
400 or 409
```

depending on contract.

---

# 13. ResourceNotFoundException

Used for:

```text
USER_NOT_FOUND
PROJECT_NOT_FOUND
DOCUMENT_NOT_FOUND
FOLDER_NOT_FOUND
CATEGORY_NOT_FOUND
TAG_NOT_FOUND
INVITATION_NOT_FOUND
```

Maps to:

```text
404
```

---

# 14. ForbiddenOperationException

Used for authenticated-but-not-allowed domain operations:

```text
PROJECT_ACCESS_FORBIDDEN
PROJECT_MANAGEMENT_FORBIDDEN
DOCUMENT_MODIFICATION_FORBIDDEN
TAG_MANAGEMENT_FORBIDDEN
INVITATION_EMAIL_MISMATCH
```

Maps to:

```text
403
```

---

# 15. InfrastructureException

Used after infrastructure adapter translation.

Examples:

```text
STORAGE_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
FILE_UPLOAD_FAILED
DOCUMENT_DELETE_FAILED
PROJECT_DELETE_FAILED
```

Maps to:

```text
500 or 503
```

based on error semantics.

---

# 16. Validation Exceptions

Validation is primarily raised by:

```text
MethodArgumentNotValidException
ConstraintViolationException
BindException
HttpMessageNotReadableException
MissingServletRequestParameterException
MethodArgumentTypeMismatchException
```

These are mapped centrally.

---

# 17. Bean Validation Mapping

Example request:

```java
public record RegisterRequest(
    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    String password
) {}
```

If invalid:

```text
MethodArgumentNotValidException
    ↓
GlobalExceptionHandler
    ↓
VALIDATION_ERROR
```

---

# 18. Field Error Aggregation

If multiple constraints fail on same field, baseline:

```text
return one safe message per field
```

Example:

```json
"errors": {
  "email": "Invalid email address",
  "password": "Password must contain between 8 and 64 characters"
}
```

Do not expose Java class names or validator internals.

---

# 19. Malformed JSON

Invalid JSON:

```text
HttpMessageNotReadableException
```

Response:

```text
400
INVALID_REQUEST_BODY
```

Suggested safe message:

```text
Request body is malformed or contains invalid values.
```

Do not return Jackson parser stack trace.

---

# 20. Type Mismatch

Example:

```text
/project/{projectId}
```

with invalid UUID.

Map to:

```text
400
INVALID_PARAMETER
```

Safe message:

```text
One or more request parameters are invalid.
```

---

# 21. Unsupported Sort Field

Document/list sorting allows only whitelist:

```text
displayName
createdAt
updatedAt
sizeBytes
```

Unsupported value:

```text
400
VALIDATION_ERROR
```

Do not allow raw persistence property access from arbitrary client sort input.

---

# 22. File Validation Errors

`FileValidationService` throws known application errors.

Examples:

```text
FILE_EMPTY
FILE_TOO_LARGE
UNSUPPORTED_FILE_TYPE
MIME_TYPE_MISMATCH
INVALID_FILE_METADATA
```

Status mapping:

```text
FILE_EMPTY
→ 400

FILE_TOO_LARGE
→ 413

UNSUPPORTED_FILE_TYPE
→ 415

MIME_TYPE_MISMATCH
→ 415

INVALID_FILE_METADATA
→ 400
```

---

# 23. Authentication Exceptions

Authentication errors are not normally handled by ControllerAdvice before Spring Security.

Use:

```text
RestAuthenticationEntryPoint
```

for protected endpoint authentication failures.

Examples:

```text
AUTHENTICATION_REQUIRED
INVALID_ACCESS_TOKEN
ACCESS_TOKEN_EXPIRED
```

Response:

```text
401
```

---

# 24. Login Exceptions

Login is an application endpoint.

`AuthService.login()` may throw:

```text
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
ACCOUNT_DISABLED
```

Mappings:

```text
INVALID_CREDENTIALS
→ 401

EMAIL_NOT_VERIFIED
→ 403

ACCOUNT_DISABLED
→ 403
```

Do not reveal:

```text
EMAIL_NOT_FOUND
```

during login.

---

# 25. Refresh Exceptions

Refresh flow may return:

```text
REFRESH_TOKEN_MISSING
INVALID_REFRESH_TOKEN
REFRESH_TOKEN_EXPIRED
REFRESH_SESSION_REVOKED
ACCOUNT_DISABLED
```

Mappings:

```text
REFRESH_TOKEN_MISSING → 401
INVALID_REFRESH_TOKEN → 401
REFRESH_TOKEN_EXPIRED → 401
REFRESH_SESSION_REVOKED → 401
ACCOUNT_DISABLED → 403
```

---


# 25.1 Email Verification OTP Exceptions

Registration email verification may return:

```text
EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN
OTP_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
```

Mappings:

```text
EMAIL_ALREADY_VERIFIED → 409
INVALID_OTP → 400
OTP_EXPIRED → 400
OTP_ATTEMPTS_EXCEEDED → 429
OTP_RESEND_COOLDOWN → 429
OTP_SERVICE_UNAVAILABLE → 503
EMAIL_SERVICE_UNAVAILABLE → 503
```

OTP-related errors apply only to registration email verification in Core v1.

They are not used for:

```text
login OTP
MFA / 2FA
password reset OTP
project invitation acceptance
```

# 26. AccessDeniedHandler

Spring Security system-level denial uses:

```text
RestAccessDeniedHandler
```

Examples:

```text
USER calling /api/v1/admin/**
```

Response:

```text
403
ACCESS_DENIED
```

Domain-specific denial should remain more precise:

```text
PROJECT_MANAGEMENT_FORBIDDEN
```

from service layer.

---

# 27. Domain Authorization Error Flow

Example:

```text
DocumentService
    ↓
DocumentAuthorizationService
    ↓
throw ForbiddenOperationException(
    DOCUMENT_MODIFICATION_FORBIDDEN
)
    ↓
GlobalExceptionHandler
    ↓
403 JSON
```

---

# 28. Not Found vs Forbidden

Existing REST baseline distinguishes:

```text
404 resource missing
403 resource exists but user lacks permission
```

This Exception Handling design preserves that contract.

Do not silently convert all unauthorized resource access into 404.

---

# 29. Repository Constraint Translation

PostgreSQL remains final integrity layer.

Potential exception:

```text
DataIntegrityViolationException
```

should not go directly to API.

Translate known constraints.

---

# 30. Constraint Translation Strategy

Preferred flow:

```text
Service pre-check
    ↓
DB constraint safety net
    ↓
DataIntegrityViolationException
    ↓
identify known constraint
    ↓
map to ErrorCode
```

Unknown constraint:

```text
500 INTERNAL_SERVER_ERROR
```

with full detail logged internally.

---

# 31. Constraint Names Are Important

Physical schema already uses explicit names.

Examples:

```text
uq_users_email

uq_project_members_project_user
uq_project_members_single_owner

uq_project_invitations_token_hash
uq_project_pending_invitation_email

uq_folders_root_name
uq_folders_child_name

uq_categories_project_name
uq_tags_project_name

uq_documents_storage_key
```

Explicit names make error translation deterministic.

---

# 32. User Email Constraint

Constraint:

```text
uq_users_email
```

Maps to:

```text
409 EMAIL_ALREADY_EXISTS
```

---

# 33. Project Membership Constraint

Constraint:

```text
uq_project_members_project_user
```

Maps to:

```text
409 PROJECT_MEMBER_ALREADY_EXISTS
```

---

# 34. Single OWNER Constraint

Constraint:

```text
uq_project_members_single_owner
```

Maps to internal conflict such as:

```text
409 PROJECT_OWNER_ALREADY_EXISTS
```

This code is mainly a defensive concurrency/integrity error.

Normal application flow should prevent it.

---

# 35. Pending Invitation Constraint

Constraint:

```text
uq_project_pending_invitation_email
```

Maps to:

```text
409 INVITATION_ALREADY_PENDING
```

---

# 36. Folder Name Constraints

Constraints:

```text
uq_folders_root_name
uq_folders_child_name
```

Map to:

```text
409 FOLDER_NAME_ALREADY_EXISTS
```

---

# 37. Category Constraint

Constraint:

```text
uq_categories_project_name
```

Maps to:

```text
409 CATEGORY_NAME_ALREADY_EXISTS
```

---

# 38. Tag Constraint

Constraint:

```text
uq_tags_project_name
```

Maps to:

```text
409 TAG_NAME_ALREADY_EXISTS
```

---

# 39. Foreign Key Restrict Errors

Examples:

```text
Folder still has child/document
Category still used
User still referenced
```

Normal business flow should pre-check.

If FK still catches a race:

```text
translate based on operation context
```

Examples:

```text
delete Folder
→ FOLDER_NOT_EMPTY

delete Category
→ CATEGORY_IN_USE

delete User
→ USER_HAS_DEPENDENCIES
```

Operation context is often safer than generic constraint-name-only mapping.

---

# 40. Why Service Pre-check Still Matters

Constraint-only approach would produce poor UX.

Example:

```text
delete category
```

Instead of raw:

```text
foreign key violation
```

service checks:

```text
DocumentRepository.existsByCategoryId
```

and throws:

```text
CATEGORY_IN_USE
```

DB remains race-condition safety net.

---

# 41. Optimistic/Pessimistic Lock Errors

Invitation accept uses:

```text
PESSIMISTIC_WRITE
```

Possible lock-related persistence failures should not leak raw Hibernate exception.

If temporary DB lock/timeout occurs:

```text
500 or 503
```

depending on operational classification.

Core v1 does not introduce client-visible lock-specific codes unless needed.

---

# 42. Storage Exception Translation

`MinioStorageService` translates SDK errors into storage exceptions:

```text
StorageUnavailableException
StorageObjectNotFoundException
StorageUploadException
StorageDeleteException
```

No MinIO SDK exception leaves `storage` package.

---

# 43. Storage Unavailable

Examples:

```text
connection refused
timeout
MinIO unavailable
bucket unavailable
```

Maps to:

```text
503 STORAGE_SERVICE_UNAVAILABLE
```

---

# 44. Upload Failure

Known upload failure:

```text
StorageUploadException
```

Maps to:

```text
500 FILE_UPLOAD_FAILED
```

or `503 STORAGE_SERVICE_UNAVAILABLE` when clearly infrastructure availability related.

The mapping should preserve stable semantics.

---

# 45. Compensation Failure

Scenario:

```text
MinIO upload success
DB save fails
cleanup delete also fails
```

Client still receives:

```text
500 FILE_UPLOAD_FAILED
```

Internal log must include:

```text
COMPENSATION FAILURE
documentId
projectId
storageKey
requestId
root exceptions
```

Do not add compensation internals to client response.

---

# 46. Missing Storage Object

If Document metadata exists but object is missing:

```text
not DOCUMENT_NOT_FOUND
```

This indicates internal inconsistency.

Suggested API:

```text
500 INTERNAL_SERVER_ERROR
```

Log explicit:

```text
STORAGE_OBJECT_MISSING
```

internally.

A future dedicated API code may be added if operational need appears.

---

# 47. Document Delete Failure

Storage delete failure:

```text
DOCUMENT_DELETE_FAILED
```

or storage unavailable where applicable.

Mapping baseline:

```text
500 DOCUMENT_DELETE_FAILED
503 STORAGE_SERVICE_UNAVAILABLE
```

---

# 48. Project Delete Failure

Maps:

```text
500 PROJECT_DELETE_FAILED
503 STORAGE_SERVICE_UNAVAILABLE
```

depending on cause.

---


# 48.1 Redis OTP Exception Translation

`RedisOtpStore` translates Redis client/connectivity failures into an application infrastructure exception.

Application layer maps Redis OTP unavailability to:

```text
503 OTP_SERVICE_UNAVAILABLE
```

Do not expose:

```text
Redis host
Redis port
Redis command payload
OTP hash
OTP key
```

A Redis restart may invalidate pending verification OTP state. The user can request a new OTP after Redis becomes available.

---

# 49. Email Exception Translation

`SmtpMailService` / Gmail SMTP adapter catches raw mail exceptions.

Translate to:

```text
MailDeliveryException
MailServiceUnavailableException
```

Application layer maps to:

```text
503 EMAIL_SERVICE_UNAVAILABLE
```

for registration verification OTP delivery and invitation send/resend failures.

---

# 50. Gmail SMTP Mail Failure

Client should receive:

```text
503 EMAIL_SERVICE_UNAVAILABLE
```

Do not return:

```text
Gmail SMTP host
SMTP response
Gmail account
Gmail SMTP App Password
```

Server log may contain safe provider diagnostics.

---

# 51. Unknown Runtime Exceptions

Catch last:

```java
@ExceptionHandler(Exception.class)
```

Return:

```text
500 INTERNAL_SERVER_ERROR
```

Safe message:

```text
An unexpected error occurred.
```

Never send:

```text
exception.getMessage()
```

blindly.

---

# 52. GlobalExceptionHandler

Recommended:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    ...
}
```

Responsibilities:

```text
KBaseException
validation exceptions
request parsing errors
persistence translation fallback
unknown exceptions
```

It should not contain business queries.

---

# 53. Handler Order

Conceptual order:

```text
1. Specific KBase exceptions
2. Validation/request exceptions
3. DataIntegrityViolationException
4. Infrastructure-specific application exceptions
5. Generic Exception
```

Most infrastructure exceptions should already be translated before reaching handler.

---

# 54. Handling KBaseException

Concept:

```java
@ExceptionHandler(KBaseException.class)
ResponseEntity<ApiErrorResponse> handleKBaseException(
    KBaseException ex,
    HttpServletRequest request
)
```

Read:

```text
ErrorCode
HTTP status
safe message
request path
request ID
```

---

# 55. Request ID / Correlation ID

Every request should have:

```text
requestId
```

Recommended header:

```text
X-Request-Id
```

Behavior:

```text
if client sends a valid request ID:
optionally reuse

otherwise:
generate server-side ID
```

Server includes request ID in:

```text
response header
error response
logs
```

---

# 56. RequestIdFilter

Recommended lightweight filter:

```text
OncePerRequestFilter
```

Responsibilities:

```text
create/read request ID
put into MDC
set response X-Request-Id
clear MDC in finally
```

This filter is independent of JWT filter.

---

# 57. Request ID Format

Use a collision-resistant value:

```text
UUID
```

or:

```text
ULID
```

Exact choice is an implementation detail.

No business meaning should be encoded.

---

# 58. MDC Logging

Add:

```text
requestId
```

to MDC.

Optional safe context:

```text
userId
projectId
documentId
```

when known.

Never put secrets in MDC.

---

# 59. Logging Levels

Recommended:

```text
INFO
expected important business/security events

WARN
known rejected/suspicious conditions

ERROR
unexpected/internal/infrastructure failure
```

Examples:

```text
INVALID_CREDENTIALS
→ WARN or controlled INFO depending security policy

PROJECT_ACCESS_FORBIDDEN
→ WARN/INFO

FOLDER_NOT_EMPTY
→ no ERROR; expected business conflict

STORAGE_SERVICE_UNAVAILABLE
→ ERROR

INTERNAL_SERVER_ERROR
→ ERROR
```

---

# 60. Do Not Log Expected Business Errors as Stack Traces

Example:

```text
CATEGORY_IN_USE
```

is expected business conflict.

Do not flood logs with full stack trace at ERROR.

Log concise contextual message if needed.

---

# 61. Log Technical Failures with Root Cause

Infrastructure/unexpected errors should log:

```text
requestId
operation
safe entity IDs
stack trace
root cause
```

Example:

```text
Failed deleting MinIO object for documentId=...
requestId=...
```

Do not log secret credentials.

---

# 62. Sensitive Data Logging Rules

Never log:

```text
password
password hash
raw access token
raw refresh token
raw invitation token
raw verification OTP
Redis OTP hash / protected OTP value
JWT signing secret
Gmail SMTP App Password
MinIO secret
presigned URL signature
cookie content
```

---

# 63. File Metadata Logging

Safe to log when useful:

```text
documentId
projectId
fileKind
sizeBytes
operation
```

Be careful with:

```text
original filename
description
```

because these can contain user-provided sensitive information.

Do not log by default unless required.

---

# 64. Authentication Error Logging

Invalid token logging should not print token.

Use:

```text
requestId
user agent if appropriate
remote metadata according to privacy policy
failure category
```

Avoid security logs that become a secret store.

---

# 65. ErrorCode Groups

Suggested grouping:

```text
VALIDATION_*

AUTH_*
USER_*
PROJECT_*
MEMBER_*
INVITATION_*
FOLDER_*
CATEGORY_*
TAG_*
DOCUMENT_*
STORAGE_*
EMAIL_*
OTP_*
INTERNAL_*
```

Actual enum constants may keep current names without prefixes where already established.

---

# 66. Authentication Error Codes

```text
AUTHENTICATION_REQUIRED
INVALID_ACCESS_TOKEN
ACCESS_TOKEN_EXPIRED
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
ACCOUNT_DISABLED

EMAIL_ALREADY_VERIFIED
INVALID_OTP
OTP_EXPIRED
OTP_ATTEMPTS_EXCEEDED
OTP_RESEND_COOLDOWN

REFRESH_TOKEN_MISSING
INVALID_REFRESH_TOKEN
REFRESH_TOKEN_EXPIRED
REFRESH_SESSION_REVOKED

CURRENT_PASSWORD_INVALID
```

---

# 67. User Error Codes

```text
USER_NOT_FOUND
USER_HAS_DEPENDENCIES
USER_OWNS_PROJECT
INVALID_USER_STATUS
EMAIL_ALREADY_EXISTS
```

---

# 68. Project Error Codes

```text
PROJECT_NOT_FOUND
PROJECT_ACCESS_FORBIDDEN
PROJECT_MANAGEMENT_FORBIDDEN
PROJECT_OWNER_ALREADY_EXISTS
PROJECT_DELETE_FAILED
```

---

# 69. Member Error Codes

```text
PROJECT_MEMBER_NOT_FOUND
PROJECT_MEMBER_ALREADY_EXISTS
PROJECT_OWNER_REMOVAL_FORBIDDEN
OWNER_CANNOT_LEAVE_PROJECT
```

---

# 70. Invitation Error Codes

```text
INVITATION_NOT_FOUND
INVITATION_ALREADY_PENDING
INVITATION_NOT_PENDING
INVITATION_EXPIRED
INVITATION_EMAIL_MISMATCH
```

---

# 71. Folder Error Codes

```text
FOLDER_NOT_FOUND
PARENT_FOLDER_NOT_FOUND
FOLDER_NAME_ALREADY_EXISTS
FOLDER_CYCLE_DETECTED
FOLDER_NOT_EMPTY
```

---

# 72. Category Error Codes

```text
CATEGORY_NOT_FOUND
CATEGORY_NAME_ALREADY_EXISTS
CATEGORY_IN_USE
```

---

# 73. Tag Error Codes

```text
TAG_NOT_FOUND
TAG_NAME_ALREADY_EXISTS
TAG_MANAGEMENT_FORBIDDEN
```

---

# 74. Document Error Codes

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

---

# 75. Infrastructure Error Codes

```text
STORAGE_SERVICE_UNAVAILABLE
EMAIL_SERVICE_UNAVAILABLE
OTP_SERVICE_UNAVAILABLE
INTERNAL_SERVER_ERROR
```

---

# 76. Request Error Codes

Suggested:

```text
VALIDATION_ERROR
INVALID_REQUEST_BODY
INVALID_PARAMETER
```

These complete gaps not previously named in the initial REST list while preserving existing HTTP semantics.

They are transport-level codes, not new domain rules.

---

# 77. ErrorCode HTTP Mapping – Authentication

| ErrorCode | HTTP |
|---|---:|
| AUTHENTICATION_REQUIRED | 401 |
| INVALID_ACCESS_TOKEN | 401 |
| ACCESS_TOKEN_EXPIRED | 401 |
| INVALID_CREDENTIALS | 401 |
| EMAIL_NOT_VERIFIED | 403 |
| ACCOUNT_DISABLED | 403 |
| EMAIL_ALREADY_VERIFIED | 409 |
| INVALID_OTP | 400 |
| OTP_EXPIRED | 400 |
| OTP_ATTEMPTS_EXCEEDED | 429 |
| OTP_RESEND_COOLDOWN | 429 |
| REFRESH_TOKEN_MISSING | 401 |
| INVALID_REFRESH_TOKEN | 401 |
| REFRESH_TOKEN_EXPIRED | 401 |
| REFRESH_SESSION_REVOKED | 401 |
| CURRENT_PASSWORD_INVALID | 401 |

---

# 78. ErrorCode HTTP Mapping – User/Project

| ErrorCode | HTTP |
|---|---:|
| EMAIL_ALREADY_EXISTS | 409 |
| USER_NOT_FOUND | 404 |
| USER_HAS_DEPENDENCIES | 409 |
| USER_OWNS_PROJECT | 409 |
| INVALID_USER_STATUS | 400 |
| PROJECT_NOT_FOUND | 404 |
| PROJECT_ACCESS_FORBIDDEN | 403 |
| PROJECT_MANAGEMENT_FORBIDDEN | 403 |
| PROJECT_OWNER_ALREADY_EXISTS | 409 |
| PROJECT_DELETE_FAILED | 500 |

---

# 79. ErrorCode HTTP Mapping – Membership/Invitation

| ErrorCode | HTTP |
|---|---:|
| PROJECT_MEMBER_NOT_FOUND | 404 |
| PROJECT_MEMBER_ALREADY_EXISTS | 409 |
| PROJECT_OWNER_REMOVAL_FORBIDDEN | 409 |
| OWNER_CANNOT_LEAVE_PROJECT | 409 |
| INVITATION_NOT_FOUND | 404 |
| INVITATION_ALREADY_PENDING | 409 |
| INVITATION_NOT_PENDING | 409 |
| INVITATION_EXPIRED | 409 |
| INVITATION_EMAIL_MISMATCH | 403 |

---

# 80. ErrorCode HTTP Mapping – Organization

| ErrorCode | HTTP |
|---|---:|
| FOLDER_NOT_FOUND | 404 |
| PARENT_FOLDER_NOT_FOUND | 404 |
| FOLDER_NAME_ALREADY_EXISTS | 409 |
| FOLDER_CYCLE_DETECTED | 409 |
| FOLDER_NOT_EMPTY | 409 |
| CATEGORY_NOT_FOUND | 404 |
| CATEGORY_NAME_ALREADY_EXISTS | 409 |
| CATEGORY_IN_USE | 409 |
| TAG_NOT_FOUND | 404 |
| TAG_NAME_ALREADY_EXISTS | 409 |
| TAG_MANAGEMENT_FORBIDDEN | 403 |

---

# 81. ErrorCode HTTP Mapping – Document/File

| ErrorCode | HTTP |
|---|---:|
| DOCUMENT_NOT_FOUND | 404 |
| DOCUMENT_MODIFICATION_FORBIDDEN | 403 |
| FILE_EMPTY | 400 |
| FILE_TOO_LARGE | 413 |
| UNSUPPORTED_FILE_TYPE | 415 |
| MIME_TYPE_MISMATCH | 415 |
| INVALID_FILE_METADATA | 400 |
| PREVIEW_NOT_SUPPORTED | 415 |
| FILE_UPLOAD_FAILED | 500 |
| DOCUMENT_DELETE_FAILED | 500 |
| STORAGE_SERVICE_UNAVAILABLE | 503 |

---

# 82. ErrorCode HTTP Mapping – Request/Infrastructure

| ErrorCode | HTTP |
|---|---:|
| VALIDATION_ERROR | 400 |
| INVALID_REQUEST_BODY | 400 |
| INVALID_PARAMETER | 400 |
| EMAIL_SERVICE_UNAVAILABLE | 503 |
| OTP_SERVICE_UNAVAILABLE | 503 |
| INTERNAL_SERVER_ERROR | 500 |

---

# 83. 404 Handler for Unknown Route

Unknown endpoint:

```text
404
```

Use stable response:

```text
RESOURCE_NOT_FOUND
```

or framework route-not-found code.

This is transport-level and distinct from:

```text
PROJECT_NOT_FOUND
DOCUMENT_NOT_FOUND
```

Implementation depends on Spring MVC route behavior/configuration.

---

# 84. HTTP Method Not Allowed

Example:

```text
POST to GET-only endpoint
```

Maps:

```text
405
METHOD_NOT_ALLOWED
```

This is transport-level.

---

# 85. Unsupported Content Type

Example:

```text
application/xml
```

on JSON-only endpoint.

Maps:

```text
415
UNSUPPORTED_MEDIA_TYPE
```

Distinct from:

```text
UNSUPPORTED_FILE_TYPE
```

which refers to uploaded document type.

---

# 86. Payload Too Large

There are two possible layers:

```text
Spring multipart/request limit
FileValidationService
```

Both should produce:

```text
413 FILE_TOO_LARGE
```

or an equivalent request-size error.

Configure server multipart max high enough to support application business limits where necessary.

Otherwise container may reject before business validation.

---

# 87. MaxUploadSizeExceededException

Map:

```text
413 FILE_TOO_LARGE
```

This keeps response consistent even when Spring rejects upload before `FileValidationService`.

---

# 88. Database Availability Failure

If PostgreSQL is unavailable:

```text
DataAccessResourceFailureException
CannotCreateTransactionException
```

Do not map to a business conflict.

Return:

```text
503 or 500
```

Baseline recommendation:

```text
503 INTERNAL_DEPENDENCY_UNAVAILABLE
```

could be added later.

For Core v1, `INTERNAL_SERVER_ERROR` is acceptable if no separate code is introduced.

---

# 89. Transaction Rollback

Do not expose:

```text
UnexpectedRollbackException
TransactionSystemException
```

to client.

Map to stable internal error.

Log root cause.

---

# 90. Unique Constraint Helper

Recommended helper/component:

```text
ConstraintViolationTranslator
```

Responsibilities:

```text
inspect DataIntegrityViolationException
extract known constraint name safely
map to ErrorCode
```

Keep database-specific parsing isolated.

---

# 91. Why Isolate Constraint Parsing

Database driver exception structure is technical.

Do not scatter:

```java
if (ex.getMessage().contains("uq_tags_project_name"))
```

across services.

Central translator is easier to test and maintain.

---

# 92. Constraint Translation Fallback

If constraint name cannot be resolved:

```text
log root exception
return INTERNAL_SERVER_ERROR
```

Do not guess a business error.

---

# 93. Error Response and Localization

Core v1 does not require multi-language API messages.

Error code is the stable frontend contract.

Frontend may later localize:

```text
DOCUMENT_NOT_FOUND
```

to Vietnamese/English UI text.

Backend default messages remain English and safe.

---

# 94. Do Not Expose Entity Existence Through Auth Errors

Login:

```text
unknown email
wrong password
```

both:

```text
INVALID_CREDENTIALS
```

This rule remains unchanged.

---

# 95. Invitation Token Errors

Invalid/nonexistent token:

```text
INVITATION_NOT_FOUND
```

Expired:

```text
INVITATION_EXPIRED
```

Already accepted/cancelled:

```text
INVITATION_NOT_PENDING
```

Do not return token hash or raw token in errors.

---

# 96. Refresh Token Error Privacy

Invalid token:

```text
INVALID_REFRESH_TOKEN
```

Do not reveal:

```text
whether hash existed
session ID
user ID
```

---

# 97. Storage Error Privacy

Client sees:

```text
STORAGE_SERVICE_UNAVAILABLE
```

not:

```text
NoSuchBucket
SignatureDoesNotMatch
AccessDenied from MinIO
```

Internal log may preserve root storage exception.

---

# 98. Error Handling Flow

General flow:

```text
Controller
    ↓
Service
    ↓
Known issue?
    ↓
throw KBaseException(ErrorCode)
    ↓
GlobalExceptionHandler
    ↓
ApiErrorResponse
```

Technical failure:

```text
Infrastructure adapter
    ↓
technical exception
    ↓
translate to infrastructure exception
    ↓
service / handler
    ↓
stable ErrorCode
```

---

# 99. Security Error Flow

```text
Request
    ↓
JwtAuthenticationFilter
    ↓
token invalid?
    ↓
AuthenticationEntryPoint
    ↓
401 ApiErrorResponse
```

Authenticated but system-role denied:

```text
SecurityFilterChain
    ↓
AccessDeniedHandler
    ↓
403 ApiErrorResponse
```

Domain denied:

```text
Service Authorization
    ↓
ForbiddenOperationException
    ↓
GlobalExceptionHandler
```

---

# 100. Response Header

Every response should include:

```http
X-Request-Id: <id>
```

Especially valuable for:

```text
500
503
security incidents
storage failures
```

User/support can report this ID without exposing internals.

---

# 101. Global Handler Must Preserve Request ID

`GlobalExceptionHandler` reads request ID already set by:

```text
RequestIdFilter / MDC
```

It should not generate a second unrelated ID.

---

# 102. Logging Example – Business Conflict

Example:

```text
INFO/WARN
requestId=...
userId=...
projectId=...
code=FOLDER_NOT_EMPTY
```

No stack trace required.

---

# 103. Logging Example – Storage Failure

Example:

```text
ERROR
requestId=...
userId=...
projectId=...
documentId=...
operation=document-delete
code=DOCUMENT_DELETE_FAILED
```

Include stack trace internally.

Do not log storage secret.

---

# 104. Logging Example – Unexpected Error

```text
ERROR
requestId=...
path=/api/v1/...
Unexpected application error
```

Full internal stack trace.

Client:

```text
500 INTERNAL_SERVER_ERROR
requestId=...
```

---

# 105. Exception Package Structure

Recommended:

```text
shared
└── exception
    ├── ErrorCode.java
    ├── KBaseException.java
    ├── BusinessException.java
    ├── ResourceNotFoundException.java
    ├── ForbiddenOperationException.java
    ├── InfrastructureException.java
    ├── GlobalExceptionHandler.java
    ├── ConstraintViolationTranslator.java
    └── ApiErrorResponse.java
```

Security-specific handlers stay in:

```text
security.handler
```

Storage-specific technical exceptions stay in:

```text
storage.exception
```

Mail technical exceptions may stay in:

```text
mail.exception
```

---

# 106. Avoid Feature-Specific Exception Explosion

Do not create one Java class for every error:

```text
ProjectNotFoundException
FolderNotEmptyException
TagNameAlreadyExistsException
...
```

unless concrete benefit appears.

Prefer:

```text
BusinessException(ErrorCode.FOLDER_NOT_EMPTY)
```

This keeps hierarchy manageable.

---

# 107. When a Dedicated Exception Class Is Useful

Dedicated class may make sense when behavior differs materially.

Examples:

```text
ResourceNotFoundException
ForbiddenOperationException
InfrastructureException
```

These represent categories with common handling.

---

# 108. Controller Responsibilities

Controller should not:

```java
try {
   ...
} catch (Exception e) {
   return ResponseEntity...
}
```

for every endpoint.

Instead:

```text
Controller
→ Service
→ exception bubbles
→ centralized handler
```

---

# 109. Service Responsibilities

Service should throw error as soon as business rule fails.

Example:

```text
FolderService.deleteFolder

if child exists:
    throw BusinessException(FOLDER_NOT_EMPTY)
```

Do not return:

```text
boolean false
null
special magic values
```

for errors.

---

# 110. Repository Responsibilities

Repository should not translate errors into API `ErrorCode`.

Repository remains persistence layer.

Translation occurs in:

```text
Service
or centralized persistence translator
```

---

# 111. Infrastructure Adapter Responsibilities

Storage/Mail/Redis adapter:

```text
catch vendor exception
translate to KBase technical exception
```

Example:

```text
MinioException
→ StorageUnavailableException
```

Vendor-specific exceptions stay isolated.

---

# 112. Error Contract Stability

Frontend should depend on:

```text
HTTP status
code
safe message
field errors
requestId
```

not on:

```text
Java exception class
PostgreSQL constraint name
MinIO error code
Hibernate message
```

---

# 113. OpenAPI Error Documentation

Each endpoint should document relevant possible errors.

Example upload:

```text
400 FILE_EMPTY
400 INVALID_FILE_METADATA
403 PROJECT_ACCESS_FORBIDDEN
404 FOLDER_NOT_FOUND
413 FILE_TOO_LARGE
415 UNSUPPORTED_FILE_TYPE
415 MIME_TYPE_MISMATCH
503 STORAGE_SERVICE_UNAVAILABLE
500 FILE_UPLOAD_FAILED
```

Use shared `ApiErrorResponse` schema.

Email verification endpoints should additionally document:

```text
400 INVALID_OTP
400 OTP_EXPIRED
409 EMAIL_ALREADY_VERIFIED
429 OTP_ATTEMPTS_EXCEEDED
429 OTP_RESEND_COOLDOWN
503 OTP_SERVICE_UNAVAILABLE
503 EMAIL_SERVICE_UNAVAILABLE
```

---

# 114. Error Examples in Swagger

Swagger should include examples for important errors:

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
PROJECT_ACCESS_FORBIDDEN
DOCUMENT_MODIFICATION_FORBIDDEN
FILE_TOO_LARGE
```

No need to duplicate every possible code in every controller annotation if documentation becomes unmaintainable.

---

# 115. Testing – Global Handler

Test:

```text
KBaseException → expected status/code
validation → field errors
malformed JSON → INVALID_REQUEST_BODY
invalid UUID → INVALID_PARAMETER
unknown exception → INTERNAL_SERVER_ERROR
requestId preserved
```

---

# 116. Testing – Security Handlers

Test:

```text
missing access token → 401 AUTHENTICATION_REQUIRED
expired token → 401 ACCESS_TOKEN_EXPIRED
bad signature → 401 INVALID_ACCESS_TOKEN
USER on admin endpoint → 403 ACCESS_DENIED
```

---

# 117. Testing – Constraint Translation

Use PostgreSQL Testcontainers.

Verify actual constraint names map correctly:

```text
duplicate email
duplicate membership
duplicate pending invitation
duplicate root folder
duplicate child folder
duplicate category
duplicate tag
single OWNER constraint
```

Do not rely only on mocked persistence exceptions.

---

# 118. Testing – Infrastructure Translation

Mock/test adapter failures:

```text
MinIO connection failure
MinIO delete failure
Gmail SMTP failure
Redis OTP store failure
```

Verify:

```text
vendor exception does not reach API
stable KBase ErrorCode returned
requestId included
```

---

# 119. Testing – Information Leakage

Ensure error responses never contain:

```text
SQL
constraint stack trace
Java class names
MinIO endpoint
secret key
JWT
refresh token
invitation token
SMTP credentials
filesystem paths
```

---

# 120. Error Handling Definition of Done

Exception Handling is complete when:

```text
all APIs use one error response format

ErrorCode is centralized

validation errors are structured

401 and 403 are consistent

domain errors come from service layer

database constraint violations are translated

MinIO/Gmail SMTP/Redis client exceptions do not leak

unknown exceptions return generic 500

request ID exists in errors and logs

sensitive data is never logged or returned

Swagger uses shared error schema

integration tests verify real PostgreSQL constraint translation
```

---

# 121. Deliberately Not Added

Core v1 does not add:

```text
localized backend error messages
distributed tracing platform
Sentry-specific integration
OpenTelemetry requirement
automatic user-facing retry hints
generic retry-after mechanism
circuit breaker framework
dead-letter queue
outbox-specific exception hierarchy
AI error codes
```

These can be introduced later if required.

---

# 122. Recommended Baseline

Core v1 baseline:

```text
@RestControllerAdvice

central ErrorCode

ApiErrorResponse:
timestamp
status
code
message
path
requestId
errors

RequestIdFilter + MDC

AuthenticationEntryPoint for 401

AccessDeniedHandler for Spring Security 403

domain forbidden errors from Service

constraint-name translation for PostgreSQL

vendor exception translation at infrastructure boundary

Redis OTP failures map to OTP_SERVICE_UNAVAILABLE

Gmail SMTP failures map to EMAIL_SERVICE_UNAVAILABLE

generic 500 fallback

no stack trace or secret in client response
```

---

# 123. Next Phase

Recommended next phase:

```text
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
KBase Core v1 – OpenAPI / Swagger Configuration Design
```

It should define:

```text
OpenAPI metadata
Bearer JWT security scheme
controller annotations
shared DTO/error schemas
multipart file documentation
pagination/filter parameter documentation
error response documentation
environment exposure rules
Swagger UI configuration
```

without changing any business rules already fixed.
