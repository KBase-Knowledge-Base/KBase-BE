# KBase – Core v1
## MinIO Integration Design

**Version:** Draft 2  
**Backend:** Java Spring Boot  
**Object Storage:** MinIO  
**Database:** PostgreSQL stores metadata  
**Architecture:** Feature-first Modular Monolith  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này định nghĩa cách KBase Core v1 tích hợp MinIO dựa trên toàn bộ thiết kế đã chốt trước đó.

Mục tiêu:

- Chốt bucket strategy.
- Chốt object-key strategy.
- Chốt upload / preview / download flow.
- Chốt streaming và HTTP Range cho MP4.
- Chốt private-object access.
- Chốt hard-delete và compensation behavior.
- Chốt exception translation.
- Chốt Docker/local và production configuration.
- Chốt MinIO durable named volume trong local Docker.
- Giữ khả năng thay MinIO bằng S3 về sau.

---

# 2. Storage Boundary

PostgreSQL lưu:

```text
document ID
project ID
uploader ID
folder/category/tag relationships
display name
original filename
file kind
extension
MIME type
size
storage key
description
timestamps
```

MinIO lưu:

```text
actual binary bytes
```

Không lưu binary trực tiếp trong PostgreSQL.

PostgreSQL vẫn là source of truth cho business metadata.

---

# 3. High-Level Architecture

```text
Frontend
   │
   ▼
DocumentController
   │
   ▼
DocumentService
   │
   ├── Repository / PostgreSQL
   │
   └── StorageService
            │
            ▼
     MinioStorageService
            │
            ▼
          MinIO
```

Business module không gọi `MinioClient` trực tiếp.

---

# 4. StorageService Abstraction

Recommended conceptual interface:

```java
public interface StorageService {

    StoredObject upload(StorageUploadRequest request);

    StoredResource get(String storageKey);

    StoredResource getRange(
        String storageKey,
        long offset,
        long length
    );

    ObjectMetadata stat(String storageKey);

    void delete(String storageKey);

    void deleteAll(Collection<String> storageKeys);
}
```

MinIO-specific classes must not escape the `storage` infrastructure package.

---

# 5. Why StorageService Exists

`DocumentService` should depend only on:

```text
StorageService
```

This allows future implementations such as:

```text
MinioStorageService
S3StorageService
Test/FakeStorageService
```

without rewriting document business logic.

---

# 6. Bucket Strategy

Core v1 baseline:

```text
one private KBase bucket per deployment/environment
```

Example:

```text
local      → kbase-files-local
staging    → kbase-files-staging
production → kbase-files-prod
```

Exact bucket name is configurable.

Do not create one bucket per project.

---

# 7. Why Not One Bucket Per Project

Project isolation is already enforced by:

```text
Spring authorization
PostgreSQL relations
project UUID in storage key
```

A bucket per project would add unnecessary:

```text
provisioning
policy management
cleanup complexity
cloud migration complexity
```

---

# 8. Bucket Privacy

Bucket must remain:

```text
PRIVATE
```

Do not enable anonymous/public read or write.

Knowing the storage key must never be sufficient to access a document.

Spring Boot authorization occurs before storage access.

---

# 9. Bucket Versioning

Core v1 has already fixed:

```text
Hard Delete
No Document Versioning
```

Therefore:

```text
MinIO bucket versioning = DISABLED
```

Do not enable Object Lock / retention for the Core v1 document bucket.

Reason: on a versioned S3-compatible bucket, a normal DELETE can create a delete marker while older binary versions remain stored. That conflicts with current hard-delete semantics.

If KBase later adds document version history or compliance retention, this rule must be redesigned explicitly.

---

# 10. Object-Key Strategy

Preserve the existing physical design:

```text
projects/{projectId}/documents/{documentId}.{extension}
```

Example:

```text
projects/2c4a.../documents/9e16....pdf
```

Only backend-controlled values are used:

```text
project UUID
document UUID
validated lowercase extension
```

---

# 11. Never Use Original Filename as Storage Key

Do not use:

```text
report.pdf
../../../file
user-controlled path
```

as object key.

UUID-based keys prevent:

```text
filename collision
path traversal
unsafe special characters
rename-induced object moves
```

---

# 12. Rename and Move Behavior

Document rename changes only:

```text
documents.display_name
```

Moving a document between folders changes only:

```text
documents.folder_id
```

MinIO object does not move or rename.

Storage key stays stable for the lifetime of the document.

---

# 13. Category / Tag Behavior

Category and Tag remain PostgreSQL business metadata.

Do not encode them into storage key.

Do not use MinIO object tags as the KBase Tag source of truth.

---

# 14. Upload Flow

```text
JWT authentication
    ↓
ProjectAuthorizationService
    ↓
FileValidationService
    ↓
validate folder/category/tags
    ↓
generate document UUID
    ↓
generate storage key
    ↓
StorageService.upload
    ↓
DocumentRepository.save
    ↓
DocumentTagRepository.save
```

Storage access never happens before authorization.

---

# 15. Upload Goes Through Spring Boot

Core v1 baseline:

```text
Frontend → Spring Boot → MinIO
```

Direct client presigned PUT is not used initially.

Reasons:

```text
central permission check
central MIME/extension/size validation
simpler metadata coordination
simpler MVP behavior
```

---

# 16. Upload Streaming

Do not read a complete file into JVM memory.

Avoid:

```text
MultipartFile.getBytes()
readAllBytes()
full ByteArrayOutputStream
```

especially because the initial video limit may be 500 MB.

Use streaming:

```text
InputStream + known content length
```

into MinIO.

---

# 17. StorageUploadRequest

Suggested internal model:

```text
storageKey
InputStream
sizeBytes
mimeType
```

Possible operational metadata may contain IDs, but PostgreSQL remains the authoritative business state.

---

# 18. Content-Type

Upload object with the validated MIME type.

Do not blindly trust only the client-reported MIME value.

File validation remains responsibility of `FileValidationService`.

---

# 19. Configurable File Limits

Current baseline remains:

| File kind | Initial limit |
|---|---:|
| Documents / Office | 50 MB |
| Images | 20 MB |
| Video | 500 MB |

Batch baseline:

```text
max 10 files
```

These are configuration, not constants inside `MinioStorageService`.

---

# 20. Upload Ordering

Preserve current service design:

```text
1. Validate request / permission.
2. Upload object to MinIO.
3. Persist Document metadata and DocumentTag rows.
4. Return success.
```

This avoids committing a DB record for a file that was never stored.

---

# 21. Upload Compensation

Failure:

```text
MinIO upload ✅
DB persistence ❌
```

Then:

```text
StorageService.delete(storageKey)
```

If compensation fails:

```text
log orphan object
log project/document IDs
return FILE_UPLOAD_FAILED
```

Do not expose storage key to normal clients.

---

# 22. Batch Upload

Recommended Core v1 behavior:

```text
all-or-fail at application level where practical
```

Track:

```text
successfullyUploadedStorageKeys
```

If later upload or DB persistence fails:

```text
rollback DB transaction
attempt delete all previously uploaded objects
```

---

# 23. Batch Delete Adapter Rule

`StorageService.deleteAll()` should attempt all requested deletions and report failures.

When MinIO batch delete returns per-object results, the adapter must consume/check all results instead of assuming the call itself means every object was deleted.

---

# 24. Download Flow

Endpoint:

```text
GET /api/v1/documents/{documentId}/download
```

Flow:

```text
JWT
 ↓
Document lookup
 ↓
DocumentAuthorizationService.requireReadPermission
 ↓
StorageService.get
 ↓
stream response
```

Bucket remains private.

---

# 25. Backend Streaming Baseline

Core v1 keeps the already-designed REST contract:

```text
backend-authorized binary streaming
```

Tradeoff:

```text
+ simplest permission model
+ no public storage endpoint required
+ frontend remains simple
- backend carries file bandwidth
```

This is acceptable for Core v1.

---

# 26. Download Memory Safety

Do not transform object into a full `byte[]`.

Use:

```text
InputStream / streaming resource
```

The stream must be closed after response completion.

---

# 27. Download Headers

Response should contain:

```http
Content-Type: <validated MIME>
Content-Length: <size>
Content-Disposition: attachment; ...
```

Filename comes from:

```text
Document.displayName
```

not from the MinIO storage key.

Header filename must be sanitized.

---

# 28. Preview Flow

Endpoint:

```text
GET /api/v1/documents/{documentId}/preview
```

Preview baseline:

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

Response uses:

```text
Content-Disposition: inline
```

Office formats remain download-only in Core v1.

---

# 29. MP4 and HTTP Range

MP4 preview should support byte ranges to enable:

```text
progressive loading
seeking
browser media playback
```

Storage abstraction therefore includes:

```java
getRange(storageKey, offset, length)
```

---

# 30. Range Request Baseline

Support a single range:

```http
Range: bytes=start-end
```

Valid response:

```http
206 Partial Content
Accept-Ranges: bytes
Content-Range: bytes start-end/total
Content-Length: <selected length>
Content-Type: video/mp4
```

No Range header:

```text
normal 200 stream
```

Multiple ranges are outside the initial Core v1 requirement.

---

# 31. Invalid Range

Out-of-bounds range should return:

```http
416 Range Not Satisfiable
Content-Range: bytes */<total>
```

This is transport behavior, independent from project authorization.

---

# 32. Object Stat

Suggested operation:

```java
ObjectMetadata stat(String storageKey)
```

Possible internal fields:

```text
size
contentType
etag
lastModified
```

MinIO SDK types must not leak outside storage infrastructure.

---

# 33. Missing Binary Object

Possible inconsistency:

```text
Document exists in PostgreSQL
MinIO object is missing
```

This is not `DOCUMENT_NOT_FOUND`.

Treat it as infrastructure/data-integrity failure.

Server log should contain safe operational identifiers.

Public response should use a stable KBase error rather than raw MinIO details.

---

# 34. Document Hard Delete

Preserve the accepted flow:

```text
load document
 ↓
requireModifyPermission
 ↓
StorageService.delete(storageKey)
 ↓
DocumentRepository.delete
 ↓
DocumentTag cascade
```

---

# 35. Known Delete Risk

Failure:

```text
MinIO delete ✅
DB delete ❌
```

Result:

```text
metadata remains
binary missing
```

Core v1 must:

```text
log the failure
return DOCUMENT_DELETE_FAILED
allow operational retry/repair
```

Do not pretend PostgreSQL rollback restores a MinIO object.

---

# 36. Why Delete Order Is Not Changed Here

Previous accepted design uses:

```text
storage-first delete
```

This document preserves that design instead of silently changing it.

A future reliability phase may explicitly compare:

```text
DB-first + orphan cleanup
outbox / deferred delete
tombstone state
```

---

# 37. Project Hard Delete

Existing flow:

```text
require OWNER / ADMIN
 ↓
DocumentRepository.findStorageKeysByProjectId
 ↓
StorageService.deleteAll(keys)
 ↓
ProjectRepository.delete
 ↓
DB cascades related rows
```

PostgreSQL remains source of truth for which KBase objects belong to the project.

---

# 38. Do Not Use Prefix Scan as Primary Delete Source

Object prefix:

```text
projects/{projectId}/...
```

is useful for diagnostics, but project hard-delete should primarily use DB storage-key projection.

This reduces accidental deletion of unexpected objects.

---

# 39. Project Delete Failure

If one required storage deletion fails:

```text
do not proceed with normal project DB deletion
```

Return:

```text
STORAGE_SERVICE_UNAVAILABLE
or PROJECT_DELETE_FAILED
```

Exact API mapping is finalized in Exception Handling Design.

---

# 40. Storage Exception Hierarchy

Recommended internal hierarchy:

```text
StorageException
├── StorageUnavailableException
├── StorageObjectNotFoundException
├── StorageUploadException
└── StorageDeleteException
```

These are infrastructure exceptions, not HTTP response objects.

---

# 41. Exception Translation

Examples:

```text
StorageUploadException
→ FILE_UPLOAD_FAILED

StorageUnavailableException
→ STORAGE_SERVICE_UNAVAILABLE

StorageDeleteException during document delete
→ DOCUMENT_DELETE_FAILED

StorageDeleteException during project delete
→ PROJECT_DELETE_FAILED / STORAGE_SERVICE_UNAVAILABLE
```

Do not expose raw S3/MinIO XML errors or SDK stack traces.

---

# 42. Presigned URLs

MinIO supports presigned object access.

Core v1 baseline:

```text
not used by default
```

because current API is backend-streamed.

Keep this as a future optimization.

---

# 43. Future Presigned GET

Potential future flow:

```text
client requests KBase download
 ↓
backend authenticates and authorizes
 ↓
backend creates short-lived presigned GET
 ↓
client downloads directly from storage
```

Bucket stays private.

Presigned URL must be short-lived and configurable if enabled later.

---

# 44. Direct Presigned PUT

Direct browser upload is outside Core v1.

It would require additional design for:

```text
upload finalization
abandoned uploads
validation
metadata commit
permission lifecycle
```

---

# 45. Credential Security

Frontend never receives:

```text
MinIO access key
MinIO secret key
```

Production credentials come from:

```text
environment / secret management
```

not source code.

---

# 46. Least-Privilege Storage Identity

Runtime credentials should only have permissions required by KBase, conceptually:

```text
read object
write object
delete object
stat object
```

Bucket administration should be separated where possible in production.

---

# 47. Auto-Create Bucket

Local development:

```text
auto-create-bucket = true
```

is acceptable.

Production baseline:

```text
auto-create-bucket = false
```

Bucket should be provisioned by deployment/infrastructure.

---

# 48. Startup Validation

Application should verify:

```text
storage endpoint reachable
configured bucket exists
```

If local auto-create is enabled and bucket is missing:

```text
create it
```

Otherwise fail fast instead of waiting until first upload.

---

# 49. Do Not Rewrite Production Bucket Policy at Startup

Application should not automatically:

```text
make bucket public
change bucket policy
change versioning
change retention
```

These are deployment/infrastructure concerns.

---

# 50. Docker Local Setup

Conceptual Docker Compose:

```text
frontend
backend
postgres
minio
redis
```

Persistence baseline:

```text
postgres → postgres_data named volume
minio    → minio_data named volume mounted at /data
redis    → OTP-only ephemeral state; no durable volume required
```

Backend internal endpoint may be:

```text
http://minio:9000
```

Frontend does not need MinIO credentials under the backend-streaming Core v1 design.

Redis is listed only as part of the shared Docker runtime topology. MinIO integration code does not depend on Redis.

Flyway remains part of the Spring Boot backend, not MinIO. In local Docker, backend startup applies pending migrations to the PostgreSQL container, whose durable data and Flyway schema history live in `postgres_data`.

---

# 51. Local Environment Variables

Conceptual:

```text
KBASE_STORAGE_ENDPOINT=http://minio:9000
KBASE_STORAGE_ACCESS_KEY=...
KBASE_STORAGE_SECRET_KEY=...
KBASE_STORAGE_BUCKET=kbase-files-local
KBASE_STORAGE_AUTO_CREATE=true
```

Local `.env` containing credentials must be ignored by Git.

MinIO object data must be written to `/data` backed by Docker named volume `minio_data`, not to backend-container local disk.

---

# 52. Production Configuration

Production should externalize:

```text
endpoint
access key
secret key
bucket name
TLS configuration
timeouts
```

No production default secret in `application.yml`.

---

# 53. Timeouts

Storage calls must have bounded:

```text
connect timeout
read timeout
write timeout
```

Exact values depend on deployment and large-file requirements.

They are operational configuration, not business rules.

---

# 54. Retry Policy

Do not blindly retry every operation.

Core v1 does not yet fix exact retry counts.

Retries should be deliberate and must not hide persistent failures.

---

# 55. Local Filesystem

Do not rely on backend-container permanent local disk.

Durable local object storage belongs to:

```text
minio_data Docker named volume
```

PostgreSQL durability is handled separately through `postgres_data`. Redis OTP state remains ephemeral.

Container backend should remain stateless with respect to uploaded binaries.

Framework temporary multipart storage is an implementation detail, not durable storage.

---

# 56. Duplicate Uploads

Core v1 does not perform content deduplication.

Two identical files can produce:

```text
two Document IDs
two object keys
two stored objects
```

---

# 57. Same Filename Uploads

Same display/original filename can coexist unless a future rule changes this.

UUID storage keys prevent collisions.

---

# 58. Storage Prefix Is Not Authorization

Although the object key contains:

```text
projects/{projectId}
```

this is organizational only.

Authorization remains:

```text
Spring Security
ProjectAuthorizationService
DocumentAuthorizationService
```

---

# 59. Lifecycle Policies

Do not configure lifecycle expiration that can remove active KBase documents automatically.

That could create:

```text
valid DB metadata
missing binary
```

Any lifecycle cleanup must be carefully scoped to incomplete/orphan technical data only.

---

# 60. Object Lock / Retention

Do not enable object retention or legal hold on Core v1 bucket.

They may prevent the required hard delete.

If compliance retention becomes a requirement, domain deletion rules must be changed first.

---

# 61. S3 Compatibility / Future Migration

Storage abstraction keeps business code independent of MinIO-specific SDK types.

Future architecture can support:

```text
StorageService
├── MinioStorageService
└── S3StorageService
```

No S3 adapter is required yet unless deployment requires it.

---

# 62. Package Structure

Recommended:

```text
storage
├── config
│   ├── MinioConfig.java
│   └── StorageProperties.java
│
├── service
│   ├── StorageService.java
│   └── MinioStorageService.java
│
├── model
│   ├── StorageUploadRequest.java
│   ├── StoredObject.java
│   ├── StoredResource.java
│   └── ObjectMetadata.java
│
└── exception
    ├── StorageException.java
    ├── StorageUnavailableException.java
    ├── StorageObjectNotFoundException.java
    ├── StorageUploadException.java
    └── StorageDeleteException.java
```

---

# 63. MinioClient Bean

`MinioClient` should be a configured Spring singleton bean.

Do not create a new client for every request.

Configuration belongs in:

```text
MinioConfig
```

---

# 64. StorageProperties

Suggested fields:

```text
endpoint
accessKey
secretKey
bucket
autoCreateBucket
```

Potential future fields:

```text
timeouts
presignedUrlTtl
```

Secret values must not appear in logs or generated `toString()` output.

---

# 65. Observability

Safe storage log metadata may include:

```text
operation
documentId
projectId
duration
success/failure
request/correlation ID
```

Never log:

```text
access key
secret key
presigned signed URL
```

---

# 66. Integration Testing

Recommended:

```text
MinIO Testcontainer
PostgreSQL Testcontainer
```

MinIO adapter tests:

```text
bucket initialization
stream upload
full get
range get
stat
single delete
batch delete
missing object
exception translation
```

---

# 67. Document Integration Tests

Must test:

```text
valid upload stores object + metadata

MinIO upload success + DB failure
→ storage compensation

unauthorized project upload rejected before storage

project member can download
former member cannot download
owner can access all project documents

MP4 range preview returns expected range

delete removes binary + metadata

storage delete failure prevents normal DB delete under current baseline
```

---

# 68. Project Delete Tests

Test:

```text
project with multiple documents
→ load storage keys from DB
→ remove all MinIO objects
→ delete project
→ DB cascade
```

Failure case:

```text
one required storage deletion fails
→ project metadata remains
→ operation returns failure
```

---

# 69. Deliberately Not Added

Core v1 does not add:

```text
public bucket
public share links
direct frontend PUT
browser multipart-upload protocol
document version storage
object lock
legal hold
retention workflow
content deduplication
virus scanning
CDN
storage quota
S3 adapter implementation
async storage worker
outbox
storage reconciliation scheduler
```

---

# 70. Recommended Core v1 Baseline

```text
one private bucket per environment

bucket versioning disabled

object key:
projects/{projectId}/documents/{documentId}.{extension}

upload through Spring Boot

stream upload/download

no whole-file byte[] buffering

backend-authorized preview/download

MP4 byte-range support

PostgreSQL = business metadata source of truth

storage key remains internal

StorageService isolates MinIO

upload compensation on DB failure

preserve storage-first hard-delete flow already accepted

local may auto-create bucket

local MinIO binary data persists in `minio_data` named volume

production bucket pre-provisioned
```

---

# 71. Known Tradeoffs

Backend streaming:

```text
+ simple and secure authorization
+ current API remains unchanged
- backend carries binary traffic
```

Storage-first delete:

```text
+ storage failure prevents normal metadata deletion
- DB failure afterward can leave metadata without binary
```

Unversioned bucket:

```text
+ matches hard-delete semantics
+ simple Core v1
- no storage-level recovery after accidental deletion
```

These tradeoffs match the current Core v1 requirements.

---

# 72. Definition of Done

MinIO integration is complete when:

```text
bucket is private

Core v1 bucket is unversioned

StorageService abstraction exists

MinioClient is isolated in infrastructure

object key is generated by backend

uploads/downloads are streamed

MP4 byte ranges work

binary files never live in PostgreSQL

authorization runs before storage access

storage credentials never reach frontend

upload DB failure triggers object cleanup

batch cleanup handles all uploaded keys

hard-delete removes storage object

storage exceptions map to stable KBase errors

local Docker works

local MinIO uses `minio_data` named volume

local PostgreSQL uses separate `postgres_data` named volume

Redis runs as Docker service for OTP but is not part of MinIO storage

production uses external secrets

no MinIO SDK type leaks into business modules
```

---

# 73. Next Phase

Recommended next artifact:

```text
KBase Core v1 – Exception Handling Design
```

It should define:

```text
exception hierarchy
ErrorCode model
HTTP mapping
validation errors
security errors
repository constraint translation
storage/email errors
standard ApiErrorResponse
logging policy
correlation/request ID strategy
```

without changing existing business rules.

---

# 74. External Technical Verification Notes

The design was checked against the current MinIO Java SDK capabilities relevant to KBase, including:

```text
stream upload
stream download
range reads using offset/length
object stat
single-object delete
batch delete
presigned object URLs
```

Current MinIO object-versioning behavior also confirms that ordinary deletion in a versioned bucket uses delete-marker semantics rather than automatically guaranteeing physical removal of all prior data. For that reason, the Core v1 KBase document bucket is intentionally unversioned while hard delete remains a requirement.
