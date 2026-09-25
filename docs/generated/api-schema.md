# API Schema

> Trạng thái: **RUNTIME OPENAPI ĐÃ HOẠT ĐỘNG (AI M10)**. springdoc `/v3/api-docs` là contract máy đọc được.
> Nguồn sự thật: `springdoc-openapi 3.1.1` sinh từ `AuthController`, `UserController`, `AdminUserController`,
> `ProjectController`, `AdminProjectController`, `ProjectMemberController`, `InvitationController`,
> `InvitationAcceptController`, `FolderController`, `CategoryController`, `TagController`, `DocumentController`,
> `ProjectAssistantController`, `DocumentAiIndexController`, `GuideController`
> và DTO tương ứng. File này là snapshot Markdown đồng bộ từ runtime; `OpenApiContractIntegrationTest`
> xác minh security scheme, security requirement theo endpoint, multipart/binary/range schemas,
> error codes và sự vắng mặt của field nhạy cảm trong spec runtime.

## Metadata

* Ngày sinh hoặc cập nhật: `2026-09-25` (AI M10 — snapshot đồng bộ thủ công từ runtime OpenAPI đã verify)
* Phiên bản API: `v1` (khớp path `/api/v1`)
* Base URL development: `http://localhost:8080/api/v1` (port theo `KBASE_SERVER_PORT`)
* Base URL production: `Chưa cấu hình`
* Nguồn sinh: `springdoc-openapi-starter-webmvc-ui 3.1.1 trên Spring Boot 4.1.1; spec máy đọc được tại GET /v3/api-docs (JSON) và /v3/api-docs.yaml; Swagger UI tại /swagger-ui.html`
* Commit tương ứng: `N/A`

## OpenAPI Runtime (AI M10)

* Endpoint máy đọc được: `GET /v3/api-docs` (OpenAPI 3 JSON), `GET /v3/api-docs.yaml`; Swagger UI: `GET /swagger-ui.html`
* Security scheme: `bearerAuth` — `type: http`, `scheme: bearer`, `bearerFormat: JWT`; chỉ đại diện cho access token
* Security requirement: per-controller/per-operation, không áp global. Public (không Bearer): `register`, `verify-email`,
  `resend-verification-otp`, `login`, `refresh`, `logout`. Mọi operation khác khai báo `bearerAuth`
* Refresh token: HttpOnly cookie `kbase_refresh_token` (document qua parameter cookie ở `refresh`/`logout` và description);
  không bao giờ là JSON field hay bearer scheme
* OTP: chỉ là email verification OTP (Redis-backed, Gmail SMTP, 6 chữ số, TTL 5m, cooldown 60s, tối đa 5 attempts);
  không document như OTP login/MFA/password reset; invitation dùng invitation token riêng
* Tags (15, theo thứ tự canonical trong `config/OpenApiConfig`): `Authentication`, `Users`, `Admin - Users`, `Projects`,
  `Admin - Projects`, `Project Members`, `Project Invitations`, `Invitations`, `Folders`, `Categories`, `Tags`, `Documents`,
  `AI - Project Assistant`, `AI - Document Indexing`, `AI - KBase Guide`
* Role rules trong description: ADMIN endpoints ghi "Requires SystemRole.ADMIN"; project-scoped endpoints ghi
  MEMBER/OWNER/ADMIN; document mutation ghi rule uploader MEMBER vs OWNER/ADMIN
* Multipart upload: `POST .../documents` với part `file` (string/binary) + part `metadata` JSON tùy chọn
  (`DocumentMetadataRequest`); batch dùng part `files` (array of binary) + `metadata` chung
* Binary response: `download`/`preview` là `type: string, format: binary` (application/octet-stream), không phải DTO;
  `preview` document `Range` header, `206 Partial Content` (header `Content-Range`) và `416` (header `Content-Range: bytes */total`)
* Shared error: mọi error response tham chiếu schema `ApiErrorResponse`; protected operations nhận `401 AUTHENTICATION_REQUIRED`
  qua `OperationCustomizer` dùng chung; OTP/Gmail/Redis/storage error codes document trên đúng endpoint
* DTO là contract: JPA entity không xuất hiện; `passwordHash`, token hash, `storageKey`, credential không có trong schema
* AI M10: 38 paths / 57 operations / 15 tags, gồm API-AI-001..010 và M10 hardening trên các operation tương tác. AI DTO không public score, chunk ID, vector, hash, storage key, job payload hoặc lease. Usage guard chỉ áp dụng cho ba POST interactive operations; các GET, document-index retry và background jobs không bị tính quota.
* Exposure flags: `KBASE_OPENAPI_ENABLED` / `KBASE_SWAGGER_UI_ENABLED` (local/dev mặc định bật; prod mặc định tắt);
  SecurityConfig chỉ permit các docs path khi `kbase.openapi.enabled=true`, không nới `/api/v1/**`

## Quy ước Chung

* Content type: `application/json`; upload dùng `multipart/form-data`; preview/download có binary response
* Định dạng ngày giờ: `ISO 8601`
* Timezone: `UTC`
* Authentication: `Bearer JWT access token cho protected API; refresh token qua HttpOnly cookie`
* Error format: `ApiErrorResponse` gồm `timestamp`, `status`, `code`, `message`, `path`, `requestId`; validation thêm `errors` dạng map field → safe message
* Error correlation: mọi response có header `X-Request-Id`; error body lặp lại cùng giá trị trong `requestId`
* Pagination format: `page=0, size=20, max size=100; sort theo whitelist trong REST spec`

## Authentication

Mô tả cơ chế xác thực chung (đã triển khai M6):

* Header hoặc cookie:
  * Protected API: `Authorization: Bearer <access-token>`
  * Refresh/logout: cookie `kbase_refresh_token` — `HttpOnly`, `Secure` theo profile (prod `true`, local `false`), `SameSite=Lax`, `Path=/api/v1/auth`, `Max-Age` = refresh TTL (mặc định 7d)
* Token format: access token là JWT HS256, claims `sub` (userId UUID), `systemRole` (ADMIN/USER), `iat`, `exp`, `jti`; không chứa project role OWNER/MEMBER
* Token lifetime: access TTL 15m configurable (`KBASE_ACCESS_TOKEN_TTL`); refresh TTL 7d configurable (`KBASE_REFRESH_TOKEN_TTL`)
* Refresh mechanism: `POST /api/v1/auth/refresh` với refresh cookie; session PostgreSQL-backed (`refresh_sessions` lưu SHA-256 hash, không lưu raw token, không rotation trong Core v1)
* Unauthorized response: `401` với code `AUTHENTICATION_REQUIRED`, `INVALID_ACCESS_TOKEN`, `ACCESS_TOKEN_EXPIRED`, `INVALID_CREDENTIALS`, `REFRESH_TOKEN_MISSING`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_SESSION_REVOKED`
* Forbidden response: `403` với code `ACCESS_DENIED` (thiếu system role), `ACCOUNT_DISABLED` (user DISABLED), `EMAIL_NOT_VERIFIED` (email chưa verify)
* Account state: JWT filter load current User từ DB mỗi request nên disable account có hiệu lực ngay cả với access token cũ chưa hết hạn

Không ghi secret, private key hoặc credential thật.

## Danh sách Endpoint

| Method | Path | Mô tả | Authentication | Request | Response |
| ------ | ---- | ----- | -------------- | ------- | -------- |
| POST | `/api/v1/auth/register` | Đăng ký tài khoản chưa verify và phát OTP | Public | RegisterRequest | 201 RegisterResponse |
| POST | `/api/v1/auth/verify-email` | Xác minh email bằng OTP | Public | VerifyEmailRequest | 200 VerifyEmailResponse |
| POST | `/api/v1/auth/resend-verification-otp` | Gửi lại OTP verify (cooldown 60s) | Public | ResendVerificationOtpRequest | 204 No Content |
| POST | `/api/v1/auth/login` | Đăng nhập, phát access JWT + refresh cookie | Public | LoginRequest | 200 LoginResponse + Set-Cookie |
| POST | `/api/v1/auth/refresh` | Đổi refresh cookie lấy access JWT mới | Refresh cookie | Không body | 200 AccessTokenResponse |
| POST | `/api/v1/auth/logout` | Revoke refresh session hiện tại và clear cookie | Refresh cookie (không bắt buộc) | Không body | 204 No Content + Set-Cookie clear |
| GET | `/api/v1/users/me` | Xem profile hiện tại | Bearer JWT | Không body | 200 UserResponse |
| PATCH | `/api/v1/users/me` | Cập nhật displayName | Bearer JWT | UpdateProfileRequest | 200 UserResponse |
| PUT | `/api/v1/users/me/password` | Đổi mật khẩu + revoke refresh sessions | Bearer JWT | ChangePasswordRequest | 204 No Content |
| GET | `/api/v1/admin/users` | Danh sách user (q/status/systemRole + pagination) | ADMIN | Không body | 200 PageResponse&lt;UserResponse&gt; |
| GET | `/api/v1/admin/users/{userId}` | Xem một user | ADMIN | Không body | 200 UserResponse |
| PATCH | `/api/v1/admin/users/{userId}/status` | Đổi ACTIVE/DISABLED (disable revoke sessions) | ADMIN | ChangeUserStatusRequest | 200 UserResponse |
| DELETE | `/api/v1/admin/users/{userId}` | Hard delete theo dependency rules | ADMIN | Không body | 204 No Content |
| POST | `/api/v1/projects` | Tạo project + OWNER membership (một transaction) | Bearer JWT | CreateProjectRequest | 201 ProjectResponse |
| GET | `/api/v1/projects` | Danh sách project user đang tham gia (q/role + pagination) | Bearer JWT | Không body | 200 PageResponse&lt;ProjectResponse&gt; |
| GET | `/api/v1/projects/{projectId}` | Xem project (MEMBER/OWNER/ADMIN) | Bearer JWT | Không body | 200 ProjectResponse |
| PATCH | `/api/v1/projects/{projectId}` | Cập nhật project (OWNER/ADMIN) | Bearer JWT | UpdateProjectRequest | 200 ProjectResponse |
| DELETE | `/api/v1/projects/{projectId}` | Hard delete storage-first (OWNER/ADMIN) | Bearer JWT | Không body | 204 No Content |
| GET | `/api/v1/admin/projects` | Danh sách toàn bộ project (q/ownerId + pagination) | ADMIN | Không body | 200 PageResponse&lt;ProjectResponse&gt; |
| GET | `/api/v1/projects/{projectId}/members` | Danh sách members (MEMBER/OWNER/ADMIN) | Bearer JWT | Không body | 200 PageResponse&lt;ProjectMemberResponse&gt; |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | Remove MEMBER (OWNER/ADMIN) | Bearer JWT | Không body | 204 No Content |
| DELETE | `/api/v1/projects/{projectId}/members/me` | Rời project (MEMBER; OWNER bị từ chối) | Bearer JWT | Không body | 204 No Content |
| POST | `/api/v1/projects/{projectId}/invitations` | Tạo invitation PENDING + gửi email token | Bearer JWT (OWNER/ADMIN) | CreateInvitationRequest | 201 InvitationResponse |
| GET | `/api/v1/projects/{projectId}/invitations` | Danh sách invitation (status + pagination) | Bearer JWT (OWNER/ADMIN) | Không body | 200 PageResponse&lt;InvitationResponse&gt; |
| POST | `/api/v1/projects/{projectId}/invitations/{invitationId}/resend` | Resend: token mới + reset expiry + gửi lại email | Bearer JWT (OWNER/ADMIN) | Không body | 200 InvitationResponse |
| DELETE | `/api/v1/projects/{projectId}/invitations/{invitationId}` | Cancel: PENDING → CANCELLED | Bearer JWT (OWNER/ADMIN) | Không body | 204 No Content |
| POST | `/api/v1/invitations/accept` | Accept invitation bằng raw token | Bearer JWT | AcceptInvitationRequest | 200 AcceptInvitationResponse |
| GET | `/api/v1/projects/{projectId}/folders` | Liệt kê flat folder structure hoặc children theo `parentId` | Bearer JWT (MEMBER/OWNER/ADMIN) | Query `parentId` tùy chọn | 200 `FolderResponse[]` |
| POST | `/api/v1/projects/{projectId}/folders` | Tạo folder root hoặc nested | Bearer JWT (OWNER/ADMIN) | CreateFolderRequest | 201 FolderResponse |
| PATCH | `/api/v1/projects/{projectId}/folders/{folderId}` | Rename hoặc move folder | Bearer JWT (OWNER/ADMIN) | UpdateFolderRequest | 200 FolderResponse |
| DELETE | `/api/v1/projects/{projectId}/folders/{folderId}` | Xóa folder rỗng | Bearer JWT (OWNER/ADMIN) | Không body | 204 No Content |
| GET | `/api/v1/projects/{projectId}/categories` | Liệt kê category trong project | Bearer JWT (MEMBER/OWNER/ADMIN) | Không body | 200 `CategoryResponse[]` |
| POST | `/api/v1/projects/{projectId}/categories` | Tạo category | Bearer JWT (OWNER/ADMIN) | CreateCategoryRequest | 201 CategoryResponse |
| PATCH | `/api/v1/projects/{projectId}/categories/{categoryId}` | Rename category | Bearer JWT (OWNER/ADMIN) | UpdateCategoryRequest | 200 CategoryResponse |
| DELETE | `/api/v1/projects/{projectId}/categories/{categoryId}` | Xóa category chưa được dùng | Bearer JWT (OWNER/ADMIN) | Không body | 204 No Content |
| GET | `/api/v1/projects/{projectId}/tags` | Liệt kê/tìm tag trong project | Bearer JWT (MEMBER/OWNER/ADMIN) | Query `q` tùy chọn | 200 `TagResponse[]` |
| POST | `/api/v1/projects/{projectId}/tags` | Tạo shared tag | Bearer JWT (MEMBER/OWNER/ADMIN) | CreateTagRequest | 201 TagResponse |
| PATCH | `/api/v1/projects/{projectId}/tags/{tagId}` | Rename shared tag | Bearer JWT (OWNER/ADMIN) | UpdateTagRequest | 200 TagResponse |
| DELETE | `/api/v1/projects/{projectId}/tags/{tagId}` | Xóa tag và các DocumentTag relation | Bearer JWT (OWNER/ADMIN) | Không body | 204 No Content |
| GET | `/api/v1/projects/{projectId}/documents` | Browse/search metadata document trong project | Bearer JWT (MEMBER/OWNER/ADMIN) | Query `q`, `folderId`, `categoryId`, `tagId`, `fileKind`, `uploadedBy`, `createdFrom`, `createdTo`, pagination/sort | 200 `PageResponse<DocumentSummaryResponse>` |
| POST | `/api/v1/projects/{projectId}/documents` | Upload một file với metadata tùy chọn | Bearer JWT (MEMBER/OWNER/ADMIN) | multipart `file`, optional `metadata` JSON | 201 DocumentResponse |
| POST | `/api/v1/projects/{projectId}/documents/batch` | Upload batch atomic-at-application-level khi khả thi | Bearer JWT (MEMBER/OWNER/ADMIN) | multipart `files`, optional common `metadata` JSON | 201 BatchDocumentUploadResponse |
| GET | `/api/v1/documents/{documentId}` | Xem document metadata | Bearer JWT (project member/ADMIN) | Không body | 200 DocumentResponse |
| PATCH | `/api/v1/documents/{documentId}` | Update metadata (MEMBER chỉ file của mình) | Bearer JWT | UpdateDocumentRequest | 200 DocumentResponse |
| GET | `/api/v1/documents/{documentId}/download` | Stream attachment đã authorize | Bearer JWT (project member/ADMIN) | Không body | 200 binary stream |
| GET | `/api/v1/documents/{documentId}/preview` | Stream inline preview; MP4 single Range | Bearer JWT (project member/ADMIN) | Optional `Range` | 200/206 binary stream |
| DELETE | `/api/v1/documents/{documentId}` | Hard delete storage-first (MEMBER chỉ file của mình) | Bearer JWT | Không body | 204 No Content |
| POST | `/api/v1/projects/{projectId}/ai/conversations` | Tạo private conversation và first turn | Bearer JWT + current project access | CreateAiConversationRequest | 201 CreateAiConversationResponse; 429 `AI_RATE_LIMIT_EXCEEDED`; 503 `AI_PROVIDER_UNAVAILABLE` hoặc `AI_USAGE_GUARD_UNAVAILABLE`; provider failure vẫn giữ conversation/USER/FAILED marker |
| GET | `/api/v1/projects/{projectId}/ai/conversations` | List conversation của chính creator | Bearer JWT + current project access | `page=0`, `size=20` | 200 PageResponse&lt;AiConversationResponse&gt; |
| GET | `/api/v1/projects/{projectId}/ai/conversations/{conversationId}` | Metadata conversation của creator | Bearer JWT + creator | Không body | 200 AiConversationResponse |
| PATCH | `/api/v1/projects/{projectId}/ai/conversations/{conversationId}` | Rename conversation | Bearer JWT + creator | RenameAiConversationRequest | 200 AiConversationResponse |
| DELETE | `/api/v1/projects/{projectId}/ai/conversations/{conversationId}` | Hard delete conversation/messages/sources | Bearer JWT + creator | Không body | 204 No Content |
| POST | `/api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages` | Gửi câu hỏi; grounded hoặc NO_EVIDENCE | Bearer JWT + creator | SendAiMessageRequest | 200 AiTurnResponse; 429 `AI_RATE_LIMIT_EXCEEDED`; 503 `AI_PROVIDER_UNAVAILABLE` hoặc `AI_USAGE_GUARD_UNAVAILABLE` |
| GET | `/api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages` | Paged messages/sources | Bearer JWT + creator | `page=0`, `size=50` | 200 PageResponse&lt;AiTurnResponse&gt; |
| GET | `/api/v1/projects/{projectId}/documents/{documentId}/ai-index` | Trạng thái AI index an toàn | Bearer JWT + document read | Không body | 200 DocumentAiIndexResponse |
| POST | `/api/v1/projects/{projectId}/documents/{documentId}/ai-index/retry` | Retry bất đồng bộ từ FAILED | Bearer JWT + document modify | Không body | 202 DocumentAiIndexResponse |
| POST | `/api/v1/ai/guide/query` | Query stateless KBase Guide từ approved product specifications | Bearer JWT | GuideQueryRequest | 200 GuideQueryResponse (`GROUNDED`/`NO_EVIDENCE`); 429 `AI_RATE_LIMIT_EXCEEDED`; 503 `AI_PROVIDER_UNAVAILABLE` hoặc `AI_USAGE_GUARD_UNAVAILABLE` |

Đã triển khai: runtime `/v3/api-docs` khớp đúng 38 paths / 57 operations / 15 tags theo `OpenApiContractIntegrationTest` và `OpenApiDisabledIntegrationTest` (AI M10). Core baseline trước M7 là 32 paths / 47 operations; M7 thêm 5 paths / 9 operations, M9 thêm 1 path / 1 operation; M10 chỉ harden annotations/errors, không thêm path hoặc operation.

## Chi tiết Endpoint

### POST /api/v1/auth/register

#### Mục đích

`Tạo tài khoản USER/ACTIVE với email_verified_at = NULL, hash BCrypt password, phát hành OTP verification qua Redis + Gmail SMTP. Không auto-login.`

#### Authentication

* Yêu cầu xác thực: `Không`
* Quyền yêu cầu: `Public; client không được submit systemRole/emailVerifiedAt`

#### Request Body

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123",
  "displayName": "Example User"
}
```

#### Request Schema

| Field       | Kiểu   | Bắt buộc | Nullable | Validation                  | Mô tả |
| ----------- | ------ | -------- | -------- | --------------------------- | ----- |
| email       | String | Có       | Không    | email format, max 254, unique, normalize lowercase | Email đăng nhập |
| password    | String | Có       | Không    | 8..64 ký tự                 | Được hash BCrypt, không bao giờ trả về |
| displayName | String | Có       | Không    | non-blank, max 100          | Tên hiển thị |

#### Success Response

* HTTP status: `201 Created`

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Example User",
  "systemRole": "USER",
  "status": "ACTIVE",
  "emailVerified": false,
  "createdAt": "2026-09-17T03:00:00Z"
}
```

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| 400 | VALIDATION_ERROR | Request payload sai validation |
| 409 | EMAIL_ALREADY_EXISTS | Email đã tồn tại (pre-check + DB unique constraint) |
| 503 | OTP_SERVICE_UNAVAILABLE | Redis OTP store không khả dụng |
| 503 | EMAIL_SERVICE_UNAVAILABLE | Gmail SMTP send thất bại (Redis state được cleanup best-effort) |

#### Idempotency

* Yêu cầu idempotency: `Không`
* Cơ chế: `Không áp dụng`

#### Ghi chú Compatibility

* Không có

### POST /api/v1/auth/verify-email

#### Mục đích

`Verify email bằng OTP 6 chữ số; set users.email_verified_at = now và invalidates Redis OTP state.`

#### Authentication

* Yêu cầu xác thực: `Không`
* Quyền yêu cầu: `Public`

#### Request Body

```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

#### Request Schema

| Field | Kiểu   | Bắt buộc | Nullable | Validation       | Mô tả |
| ----- | ------ | -------- | -------- | ---------------- | ----- |
| email | String | Có       | Không    | email format, max 254 | Email đã đăng ký |
| otp   | String | Có       | Không    | 6 chữ số         | OTP nhận qua email |

#### Success Response

* HTTP status: `200 OK`

```json
{
  "email": "user@example.com",
  "emailVerified": true
}
```

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| 400 | VALIDATION_ERROR | Payload sai validation |
| 400 | INVALID_OTP | OTP không khớp (attempt count tăng) |
| 400 | OTP_EXPIRED | OTP hết hạn (TTL 5m) hoặc state không tồn tại |
| 404 | USER_NOT_FOUND | Không có account với email này |
| 409 | EMAIL_ALREADY_VERIFIED | Email đã verify trước đó |
| 429 | OTP_ATTEMPTS_EXCEEDED | Vượt 5 lần thử sai |
| 503 | OTP_SERVICE_UNAVAILABLE | Redis không khả dụng |

#### Idempotency

* Yêu cầu idempotency: `Không` (verify lặp sau khi verified → 409)
* Cơ chế: `Không áp dụng`

#### Ghi chú Compatibility

* Không có

### POST /api/v1/auth/resend-verification-otp

#### Mục đích

`Gửi lại OTP mới cho account chưa verify; obey resend cooldown 60s, replace OTP cũ, reset attempts/TTL.`

#### Authentication

* Yêu cầu xác thực: `Không`
* Quyền yêu cầu: `Public`

#### Request Body

```json
{
  "email": "user@example.com"
}
```

#### Success Response

* HTTP status: `204 No Content` (không body)

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| 400 | VALIDATION_ERROR | Payload sai validation |
| 404 | USER_NOT_FOUND | Không có account với email này |
| 409 | EMAIL_ALREADY_VERIFIED | Email đã verify |
| 429 | OTP_RESEND_COOLDOWN | Gửi lại trong cooldown 60s |
| 503 | OTP_SERVICE_UNAVAILABLE | Redis không khả dụng |
| 503 | EMAIL_SERVICE_UNAVAILABLE | Gmail SMTP send thất bại |

#### Idempotency

* Yêu cầu idempotency: `Không` (throttle bằng cooldown)
* Cơ chế: `Redis cooldown key`

#### Ghi chú Compatibility

* Không có

### POST /api/v1/auth/login

#### Mục đích

`Đăng nhập: password đúng + user ACTIVE + email đã verify. Trả access JWT trong body, refresh token trong HttpOnly cookie.`

#### Authentication

* Yêu cầu xác thực: `Không`
* Quyền yêu cầu: `Public; unknown email và wrong password đều trả generic 401 INVALID_CREDENTIALS`

#### Request Body

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123"
}
```

#### Success Response

* HTTP status: `200 OK`
* Header: `Set-Cookie: kbase_refresh_token=<opaque raw token>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800`

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

Raw refresh token không bao giờ xuất hiện trong JSON body.

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| 400 | VALIDATION_ERROR | Payload sai validation |
| 401 | INVALID_CREDENTIALS | Unknown email hoặc wrong password (generic) |
| 403 | EMAIL_NOT_VERIFIED | Credentials đúng nhưng email chưa verify |
| 403 | ACCOUNT_DISABLED | Credentials đúng nhưng user DISABLED |

#### Idempotency

* Yêu cầu idempotency: `Không` (mỗi login tạo refresh session mới)
* Cơ chế: `Không áp dụng`

#### Ghi chú Compatibility

* Không có

### POST /api/v1/auth/refresh

#### Mục đích

`Đổi refresh cookie (PostgreSQL session valid: tồn tại, chưa revoke, chưa hết hạn) lấy access JWT mới. Không rotation trong Core v1.`

#### Authentication

* Yêu cầu xác thực: `Không cần access token; cần refresh cookie`
* Quyền yêu cầu: `Session phải thuộc user ACTIVE, email đã verify`

#### Success Response

* HTTP status: `200 OK`

```json
{
  "accessToken": "<new-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| 401 | REFRESH_TOKEN_MISSING | Cookie không có hoặc rỗng |
| 401 | INVALID_REFRESH_TOKEN | Hash không khớp session nào trong refresh_sessions |
| 401 | REFRESH_TOKEN_EXPIRED | Session hết hạn (TTL 7d) |
| 401 | REFRESH_SESSION_REVOKED | Session đã revoke (logout) |
| 403 | ACCOUNT_DISABLED | User DISABLED |
| 403 | EMAIL_NOT_VERIFIED | Email chưa verify |

#### Idempotency

* Yêu cầu idempotency: `Có trong giới hạn session active` (cùng session phát access token mới nhiều lần)
* Cơ chế: `Không áp dụng`

#### Ghi chú Compatibility

* Không có

### POST /api/v1/auth/logout

#### Mục đích

`Revoke refresh session hiện tại (set revoked_at) và clear refresh cookie. Effectively idempotent.`

#### Authentication

* Yêu cầu xác thực: `Không cần access token; cookie tùy chọn`
* Quyền yêu cầu: `Không áp dụng`

#### Success Response

* HTTP status: `204 No Content`
* Header: `Set-Cookie: kbase_refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=0`

Logout không có/khớp cookie vẫn trả 204 và clear cookie.

#### Error Responses

| HTTP Status | Error Code | Điều kiện |
| ----------- | ---------- | --------- |
| — | — | Effectively idempotent; không có error case |

#### Idempotency

* Yêu cầu idempotency: `Có`
* Cơ chế: `revoke if present; luôn clear cookie`

#### Ghi chú Compatibility

* Không có

## User / Project / Membership Endpoints (M7)

Tất cả endpoints dưới đây yêu cầu `Authorization: Bearer <access-token>` (đã verify ACTIVE + email verified bởi JWT filter); các endpoint `/api/v1/admin/**` yêu cầu thêm system role ADMIN qua SecurityConfig.

### GET /api/v1/users/me

* Mục đích: profile của caller hiện tại.
* Success: `200` — `UserResponse` (id, email, displayName, systemRole, status, `emailVerified` derived, createdAt, updatedAt; không có password hash).
* Errors: `401 AUTHENTICATION_REQUIRED` khi chưa xác thực.

### PATCH /api/v1/users/me

* Mục đích: cập nhật `displayName` (email/systemRole/status không thể tự đổi).
* Request: `{"displayName": "New Display Name"}` — required, non-blank, max 100.
* Success: `200` — `UserResponse` mới. Errors: `400 VALIDATION_ERROR`.

### PUT /api/v1/users/me/password

* Mục đích: đổi mật khẩu: verify current password, hash BCrypt mật khẩu mới, revoke toàn bộ refresh sessions của user (security baseline).
* Request: `{"currentPassword": "...", "newPassword": "..."}` — newPassword 8..64 ký tự.
* Success: `204`. Errors: `400 VALIDATION_ERROR`, `401 CURRENT_PASSWORD_INVALID`.

### GET /api/v1/admin/users

* Mục đích: admin liệt kê users với filter `q` (email/displayName), `status`, `systemRole` và pagination `page/size/sort` (whitelist: email, displayName, createdAt, updatedAt; default `createdAt,desc`).
* Success: `200` — `PageResponse<UserResponse>`. Errors: `403 ACCESS_DENIED` với non-ADMIN, `400 VALIDATION_ERROR` nếu sort ngoài whitelist.

### GET /api/v1/admin/users/{userId}

* Success: `200` — `UserResponse`. Errors: `404 USER_NOT_FOUND`.

### PATCH /api/v1/admin/users/{userId}/status

* Mục đích: đổi `status` ACTIVE/DISABLED; DISABLED revoke toàn bộ refresh sessions của user (session revoked → refresh trả `401 REFRESH_SESSION_REVOKED`; access token cũ bị chặn bởi JWT filter với `403 ACCOUNT_DISABLED`); re-enable không reactivate session cũ.
* Request: `{"status": "ACTIVE" | "DISABLED"}` (string; giá trị lạ → `400 INVALID_USER_STATUS`).
* Success: `200` — `UserResponse`. Errors: `404 USER_NOT_FOUND`, `400 INVALID_USER_STATUS`.

### DELETE /api/v1/admin/users/{userId}

* Mục đích: hard delete user chỉ khi không còn dependency: owns project → `409 USER_OWNS_PROJECT`; uploaded documents / memberships / invitation references → `409 USER_HAS_DEPENDENCIES`. Không cascade project knowledge; chỉ `refresh_sessions` cascade theo schema.
* Success: `204`. Errors: `404 USER_NOT_FOUND`, `409 USER_OWNS_PROJECT`, `409 USER_HAS_DEPENDENCIES`.

### POST /api/v1/projects

* Mục đích: tạo project + membership OWNER cho creator trong **một transaction**; partial unique index bảo đảm đúng một OWNER.
* Request: `{"name": "KBase Project"}` (required, max 150), `{"description": "..."}` (optional, max 2000).
* Success: `201` — `ProjectResponse` với `currentUserRole: "OWNER"`. Errors: `400 VALIDATION_ERROR`.

### GET /api/v1/projects

* Mục đích: chỉ trả các project caller đang tham gia (kể cả ADMIN); filter `q` (name/description), `role` (OWNER/MEMBER), pagination (whitelist sort: name, createdAt, updatedAt; default `createdAt,desc`).
* Success: `200` — `PageResponse<ProjectResponse>` với `currentUserRole` per-row.

### GET /api/v1/projects/{projectId}

* Permission: MEMBER/OWNER/ADMIN. ADMIN non-member nhận `currentUserRole: null` (không fake role).
* Success: `200` — `ProjectResponse`. Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`.

### PATCH /api/v1/projects/{projectId}

* Permission: OWNER/ADMIN (`requireOwner`). MEMBER → `403 PROJECT_MANAGEMENT_FORBIDDEN`.
* Request: partial `{name?, description?}`; name present-but-blank → `400 VALIDATION_ERROR`.
* Success: `200` — `ProjectResponse`. Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `400 VALIDATION_ERROR`.

### GET /api/v1/admin/projects

* Mục đích: admin liệt kê toàn bộ project (filter `q`, `ownerId` = project do user đó OWN), `currentUserRole` luôn null trong listing này.
* Success: `200` — `PageResponse<ProjectResponse>`. Errors: `403 ACCESS_DENIED` với non-ADMIN.

### GET /api/v1/projects/{projectId}/members

* Permission: MEMBER/OWNER/ADMIN. Pagination (whitelist sort: joinedAt, email, displayName; default `joinedAt,asc`).
* Success: `200` — `PageResponse<ProjectMemberResponse>`: `{membershipId, userId, email, displayName, role, joinedAt}`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`.

### DELETE /api/v1/projects/{projectId}/members/{userId}

* Permission: OWNER/ADMIN. Target có role OWNER → `409 PROJECT_OWNER_REMOVAL_FORBIDDEN` (kể cả bởi ADMIN — bất biến single-OWNER). Documents upload bởi member bị remove vẫn tồn tại.
* Success: `204`. Errors: `404 PROJECT_NOT_FOUND`, `404 PROJECT_MEMBER_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `409 PROJECT_OWNER_REMOVAL_FORBIDDEN`.

### DELETE /api/v1/projects/{projectId}/members/me

* Permission: MEMBER. OWNER → `409 OWNER_CANNOT_LEAVE_PROJECT`. ADMIN không thuộc project → `404 PROJECT_MEMBER_NOT_FOUND`. Documents upload trước đó vẫn tồn tại.
* Success: `204`. Errors: `404 PROJECT_NOT_FOUND`, `404 PROJECT_MEMBER_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`, `409 OWNER_CANNOT_LEAVE_PROJECT`.

### PageResponse (pagination envelope)

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

Baseline: `page=0`, `size=20`, max `size=100` (giá trị lớn hơn bị clamp), `sort=field,direction` với whitelist theo resource; sort ngoài whitelist → `400 VALIDATION_ERROR`.

### ProjectResponse

| Field          | Kiểu        | Nullable | Mô tả |
| -------------- | ----------- | -------- | ----- |
| id             | UUID        | Không    | Project ID |
| name           | String      | Không    | Tên project |
| description    | String      | Có       | Mô tả |
| currentUserRole| ProjectRole | Có       | OWNER/MEMBER của caller; null khi ADMIN truy cập không qua membership |
| createdAt      | ISO-8601    | Không    | Thời điểm tạo |
| updatedAt      | ISO-8601    | Không    | Thời điểm cập nhật gần nhất |

## Invitation Endpoints (M8)

Tất cả endpoints yêu cầu `Authorization: Bearer <access-token>`; quyền OWNER/ADMIN được enforce qua `ProjectAuthorizationService.requireOwner`, ADMIN override không cần membership. Invitation **không dùng OTP**; raw token chỉ xuất hiện trong email link và request accept; response không bao giờ trả raw token hay token hash.

### POST /api/v1/projects/{projectId}/invitations

* Mục đích: tạo invitation PENDING với expiration 72h configurable, gửi invitation email qua `MailService.sendProjectInvitation` (link `{KBASE_INVITATION_ACCEPT_URL}?token=<raw>`). Mail fail → transaction rollback, không để lại row PENDING.
* Request: `{"email": "newmember@example.com"}` (required, email format, normalize lowercase).
* Success: `201` — `InvitationResponse` `{id, projectId, email, status: "PENDING", expiresAt, createdAt}`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `409 PROJECT_MEMBER_ALREADY_EXISTS` (email đã là member), `409 INVITATION_ALREADY_PENDING` (đã có PENDING cho project+email), `503 EMAIL_SERVICE_UNAVAILABLE`.

### GET /api/v1/projects/{projectId}/invitations

* Permission: OWNER/ADMIN. Query: `status`, `page`, `size`, `sort` (whitelist: createdAt, email, status, expiresAt; default `createdAt,desc`).
* Success: `200` — `PageResponse<InvitationResponse>`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`.

### POST /api/v1/projects/{projectId}/invitations/{invitationId}/resend

* Mục đích: chỉ cho invitation PENDING; generate token mới (hash cũ bị thay → token cũ vô hiệu), reset `expiresAt` = now + expiration, gửi lại email.
* Success: `200` — `InvitationResponse`.
* Errors: `404 INVITATION_NOT_FOUND`, `409 INVITATION_NOT_PENDING`, `409 PROJECT_MEMBER_ALREADY_EXISTS` (email đã trở thành member), `503 EMAIL_SERVICE_UNAVAILABLE`.

### DELETE /api/v1/projects/{projectId}/invitations/{invitationId}

* Mục đích: cancel invitation PENDING → status `CANCELLED` (không physical delete; token không thể accept vì accept yêu cầu PENDING).
* Success: `204`. Errors: `404 INVITATION_NOT_FOUND`, `409 INVITATION_NOT_PENDING`.

### POST /api/v1/invitations/accept

* Mục đích: authenticated user accept invitation bằng raw token; chạy trong transaction với **PESSIMISTIC_WRITE** trên invitation row (hai accept song song chỉ một thắng). Flow: hash token → tìm invitation FOR UPDATE → PENDING? → chưa expired? (expired có thể set status EXPIRED trong cùng transaction) → email caller khớp invitation email? → chưa là member? → tạo `ProjectMember(role=MEMBER)` + `ACCEPTED` + `acceptedAt=now`.
* Request: `{"token": "<raw-invitation-token>"}`.
* Success: `200` — `AcceptInvitationResponse` `{projectId, membershipId, role: "MEMBER", joinedAt}`.
* Errors: `404 INVITATION_NOT_FOUND`, `409 INVITATION_EXPIRED`, `409 INVITATION_NOT_PENDING`, `403 INVITATION_EMAIL_MISMATCH`, `409 PROJECT_MEMBER_ALREADY_EXISTS`.
* Flow user mới được invite: register → verify OTP → login → accept token (không auto-join).

### InvitationResponse

| Field     | Kiểu             | Nullable | Mô tả |
| --------- | ---------------- | -------- | ----- |
| id        | UUID             | Không    | Invitation ID |
| projectId | UUID             | Không    | Project được invite |
| email     | String           | Không    | Email đã normalize |
| status    | InvitationStatus | Không    | PENDING/ACCEPTED/EXPIRED/CANCELLED |
| expiresAt | ISO-8601         | Không    | Hạn chấp nhận |
| createdAt | ISO-8601         | Không    | Thời điểm tạo |

Raw invitation token và token hash không thuộc bất kỳ response DTO nào.

## Folder / Category / Tag Endpoints (M9)

Tất cả endpoint M9 yêu cầu `Authorization: Bearer <access-token>`. Resource và parent lookup đều bị giới hạn trong `projectId`; ADMIN là system-level override và không tạo project membership giả.

### Folder APIs

#### GET /api/v1/projects/{projectId}/folders

* Permission: MEMBER/OWNER/ADMIN.
* Query `parentId` tùy chọn. Bỏ qua `parentId` trả toàn bộ folder dạng flat list; truyền `parentId` trả các child trực tiếp và yêu cầu parent thuộc project.
* Success: `200` — `FolderResponse[]`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`, `404 PARENT_FOLDER_NOT_FOUND`.

#### POST /api/v1/projects/{projectId}/folders

* Permission: OWNER/ADMIN.
* Request: `{"name":"Backend","parentId":null}` — `name` required, trimmed, tối đa 150 ký tự; `parentId` nullable.
* Success: `201` — `FolderResponse`.
* Rules: parent phải cùng project; tên unique case-insensitive giữa các sibling; nested hierarchy được hỗ trợ.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `404 PARENT_FOLDER_NOT_FOUND`, `409 FOLDER_NAME_ALREADY_EXISTS`.

#### PATCH /api/v1/projects/{projectId}/folders/{folderId}

* Permission: OWNER/ADMIN.
* Request partial: `{"name":"Backend Docs","parentId":"uuid-or-null"}`. Field bị bỏ qua giữ nguyên; `parentId: null` move về root.
* Success: `200` — `FolderResponse`.
* Rules: không self-parent, không move vào descendant; parent mới phải cùng project; tên phải unique case-insensitive trong parent mới. Service walk ancestor chain của parent với `visited` set để chặn cycle trước khi ghi.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `404 FOLDER_NOT_FOUND`, `404 PARENT_FOLDER_NOT_FOUND`, `409 FOLDER_NAME_ALREADY_EXISTS`, `409 FOLDER_CYCLE_DETECTED`.

#### DELETE /api/v1/projects/{projectId}/folders/{folderId}

* Permission: OWNER/ADMIN.
* Success: `204 No Content`.
* Rule: folder phải không có child folder và không có document; không có recursive delete.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `404 FOLDER_NOT_FOUND`, `409 FOLDER_NOT_EMPTY`.

### Category APIs

#### GET /api/v1/projects/{projectId}/categories

* Permission: MEMBER/OWNER/ADMIN.
* Success: `200` — `CategoryResponse[]`, sort theo name tăng dần.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`.

#### POST /api/v1/projects/{projectId}/categories

* Permission: OWNER/ADMIN.
* Request: `{"name":"Technical"}` — required, trimmed, tối đa 100 ký tự.
* Success: `201` — `CategoryResponse`.
* Rule: name unique case-insensitive trong project.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `409 CATEGORY_NAME_ALREADY_EXISTS`.

#### PATCH /api/v1/projects/{projectId}/categories/{categoryId}

* Permission: OWNER/ADMIN.
* Request: `{"name":"Architecture"}` — required, trimmed, tối đa 100 ký tự.
* Success: `200` — `CategoryResponse`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `404 CATEGORY_NOT_FOUND`, `409 CATEGORY_NAME_ALREADY_EXISTS`.

#### DELETE /api/v1/projects/{projectId}/categories/{categoryId}

* Permission: OWNER/ADMIN.
* Success: `204 No Content`.
* Rule: category đang được document sử dụng không được xóa.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_MANAGEMENT_FORBIDDEN`, `404 CATEGORY_NOT_FOUND`, `409 CATEGORY_IN_USE`.

### Tag APIs

#### GET /api/v1/projects/{projectId}/tags

* Permission: MEMBER/OWNER/ADMIN.
* Query `q` tùy chọn để tìm name chứa chuỗi, case-insensitive; kết quả sort theo name tăng dần.
* Success: `200` — `TagResponse[]`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`.

#### POST /api/v1/projects/{projectId}/tags

* Permission: MEMBER/OWNER/ADMIN.
* Request: `{"name":"urgent"}` — required, trimmed, tối đa 50 ký tự.
* Success: `201` — `TagResponse`.
* Rule: name unique case-insensitive trong project.
* Errors: `404 PROJECT_NOT_FOUND`, `403 PROJECT_ACCESS_FORBIDDEN`, `409 TAG_NAME_ALREADY_EXISTS`.

#### PATCH /api/v1/projects/{projectId}/tags/{tagId}

* Permission: OWNER/ADMIN; MEMBER không được rename shared tag.
* Request: `{"name":"priority"}` — required, trimmed, tối đa 50 ký tự.
* Success: `200` — `TagResponse`.
* Errors: `404 PROJECT_NOT_FOUND`, `403 TAG_MANAGEMENT_FORBIDDEN`, `404 TAG_NOT_FOUND`, `409 TAG_NAME_ALREADY_EXISTS`.

#### DELETE /api/v1/projects/{projectId}/tags/{tagId}

* Permission: OWNER/ADMIN; MEMBER không được delete shared tag.
* Success: `204 No Content`.
* Behavior: xóa tag và các `DocumentTag` relation nhờ FK cascade; document vẫn tồn tại.
* Errors: `404 PROJECT_NOT_FOUND`, `403 TAG_MANAGEMENT_FORBIDDEN`, `404 TAG_NOT_FOUND`.

### M9 response DTOs

| DTO | Fields |
|---|---|
| `FolderResponse` | `id: UUID`, `projectId: UUID`, `parentId: UUID?`, `name: String`, `createdAt: ISO-8601`, `updatedAt: ISO-8601` |
| `CategoryResponse` | `id: UUID`, `name: String`, `createdAt: ISO-8601`, `updatedAt: ISO-8601` |
| `TagResponse` | `id: UUID`, `name: String`, `createdAt: ISO-8601` |

Case-insensitive uniqueness is enforced by service pre-checks and the existing PostgreSQL expression/partial unique indexes; concurrent duplicate writes still resolve at the database constraint boundary and are translated by the shared error handler.

## Pagination

Đã triển khai từ M7 qua `PageResponse` (xem envelope ở trên) cho `/api/v1/admin/users`, `/api/v1/projects`, `/api/v1/admin/projects` và `/api/v1/projects/{projectId}/members`. Chi tiết đầy đủ nằm trong `docs/design-docs/KBase - Core v1 REST API Specification.md` (section 11).

## Error Schema

```json
{
  "timestamp": "2026-09-17T02:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "path": "/api/v1/example",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "errors": {
    "field": "Safe validation message"
  }
}
```

| Field     | Kiểu         | Bắt buộc | Mô tả               |
| --------- | ------------ | -------- | ------------------- |
| timestamp | String (ISO-8601 UTC) | Có | Thời điểm tạo response |
| status    | Integer      | Có       | HTTP status tương ứng |
| code      | String       | Có       | Mã lỗi ổn định      |
| message   | String       | Có       | Thông điệp an toàn cho client |
| path      | String       | Có       | Request path |
| requestId | String (UUID) | Có       | ID phục vụ truy vết, cũng có trong `X-Request-Id` |
| errors    | Object&lt;String,String&gt; | Không | Field-level validation errors; bỏ qua với lỗi không phải validation |

Schema thực tế phải tuân theo `docs/API_CONVENTIONS.md` và `docs/design-docs/KBase - Core v1 Exception Handling Design.md`.
Security-layer responses (401 entry point, 403 access denied, JWT/account-state rejections trong filter) dùng cùng schema qua `RestSecurityErrorWriter`.

## Document Endpoints (M11–M12 — implemented and verified)

All endpoints below require bearer authentication. `storageKey` is never a response field. `MEMBER` may read/download/preview every document in a current project membership, but may update/delete only a document uploaded by that user; `OWNER` and `ADMIN` may manage all project documents.

| Method | Path | Request | Success | Principal errors |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/documents` | Query optional `q`, `folderId`, `categoryId`, `tagId`, `fileKind`, `uploadedBy`, `createdFrom`, `createdTo`, `page`, `size`, `sort` | `200 PageResponse<DocumentSummaryResponse>` | `403 PROJECT_ACCESS_FORBIDDEN`; unsupported sort → `400 VALIDATION_ERROR` |
| POST | `/api/v1/projects/{projectId}/documents` | multipart `file`, optional JSON `metadata` (`displayName`, `description`, `folderId`, `categoryId`, `tagIds`) | `201 DocumentResponse` | `FILE_EMPTY` 400, `FILE_TOO_LARGE` 413, `UNSUPPORTED_FILE_TYPE`/`MIME_TYPE_MISMATCH` 415, same-project metadata errors, storage 500/503 |
| POST | `/api/v1/projects/{projectId}/documents/batch` | multipart repeated `files`, common optional JSON metadata | `201 {documents: DocumentResponse[]}` | same as upload; no partial-success contract |
| GET | `/api/v1/documents/{documentId}` | — | `200 DocumentResponse` | `DOCUMENT_NOT_FOUND` 404, `PROJECT_ACCESS_FORBIDDEN` 403 |
| PATCH | `/api/v1/documents/{documentId}` | JSON partial metadata | `200 DocumentResponse` | `DOCUMENT_MODIFICATION_FORBIDDEN` 403; same-project metadata errors |
| GET | `/api/v1/documents/{documentId}/download` | — | streamed attachment, validated MIME, filename from `displayName` | 403/404, storage 500/503 |
| GET | `/api/v1/documents/{documentId}/preview` | optional single `Range` for MP4 | inline stream; MP4 range is `206` plus `Content-Range`; invalid range is `416` plus `Content-Range: bytes */total` | `PREVIEW_NOT_SUPPORTED` 415 for Office; 403/404, storage 500/503 |
| DELETE | `/api/v1/documents/{documentId}` | — | `204` | `DOCUMENT_MODIFICATION_FORBIDDEN` 403; `DOCUMENT_DELETE_FAILED` 500; storage 503 |
| DELETE | `/api/v1/projects/{projectId}` | — | `204` | OWNER/ADMIN only; `PROJECT_DELETE_FAILED` 500; storage 503 |

`DocumentResponse` contains public metadata only: ID, project ID, uploader `{id, displayName}`, folder/category/tag projections, display/original names, kind, extension, MIME, byte size, description and timestamps. It does not contain object-storage credentials or keys. Project delete resolves storage keys through PostgreSQL, deletes all objects, then deletes the project for relational DB cascade.

`DocumentSummaryResponse` is the fixed paginated-browser projection: `id`, `displayName`, `originalFilename`, `fileKind`, `extension`, `mimeType`, `sizeBytes`, `folderId`, `createdAt`, `updatedAt`. It never exposes `storageKey` or a persistence entity. `q` is metadata-only over display/original names, description, category name and tag name; it does not inspect file content. The query always includes the path `projectId`; tag matching uses an `EXISTS` subquery so tag rows cannot duplicate documents. Pagination defaults to `page=0`, `size=20`, clamps size to `100`; only `displayName`, `createdAt`, `updatedAt`, and `sizeBytes` are accepted as sort fields (ASC/DESC), with unsupported fields returning `VALIDATION_ERROR`.

## AI M7/M10 – Project Assistant và Document Index API

Tất cả chín operation yêu cầu Bearer JWT. Conversation routes kiểm tra current project access trước creator-scoped lookup; wrong owner, wrong project và missing conversation ID cùng nhận `404 AI_CONVERSATION_NOT_FOUND` sau khi project access hợp lệ. ADMIN override chỉ áp dụng project access. GET conversation trả metadata, còn messages đọc qua route phân trang riêng. List conversation cố định `updatedAt DESC, id DESC`; list messages cố định `createdAt ASC, id ASC`; size tối đa 100 theo Core.

| DTO | Public fields |
| --- | --- |
| `AiConversationResponse` | `id`, `projectId`, `title`, `createdAt`, `updatedAt` |
| `AiMessageResponse` | `id`, `role`, nullable `content`, `generationStatus`, nullable `answerType`, nullable safe `failureCode`, `createdAt`, nullable `completedAt` |
| `AiSourceResponse` | `order` (public one-based), nullable live `documentId`, snapshot `documentName`, nullable `pageNumber`, `slideNumber`, `sectionTitle`, `availability` (`AVAILABLE`/`UNAVAILABLE`) |
| `AiTurnResponse` | `message`, `sources[]` |
| `CreateAiConversationResponse` | `conversation`, `message`, `sources[]` |
| `DocumentAiIndexResponse` | `documentId`, `status` (`PENDING`/`PROCESSING`/`READY`/`FAILED`/`UNSUPPORTED`), nullable safe `failureReason`, nullable `indexedAt`, `retryAllowed` |

Create/send nhận `message` bắt buộc, trim-aware nonblank và tối đa `kbase.ai.max-message-chars` Unicode code points (mặc định 8,000). Title ban đầu lấy từ câu hỏi đầu tiên, trim Unicode whitespace ở biên và lấy tối đa 100 code points; rename nhận `title` nonblank tối đa 100. Nội dung câu hỏi có internal whitespace được giữ nguyên. `NO_EVIDENCE` là assistant `COMPLETED` với sources rỗng và HTTP 201/200 tương ứng, không phải lỗi. Provider failure trả `503 AI_PROVIDER_UNAVAILABLE`, giữ conversation/USER và assistant `FAILED` với safe `failureCode`; không trả provider text.

`AiSourceResponse.documentId` chỉ có khi document và chunk còn live. Snapshot metadata vẫn hiển thị sau source deletion nhưng `availability=UNAVAILABLE` và `documentId=null`; Core document route tiếp tục enforce current document authorization khi mở nguồn. Public DTO không có `retrievalScore`, `chunkId`, source hash, vector, storage key hoặc URL lâu dài.

M7 runtime error additions: `AI_CONVERSATION_LIMIT_REACHED` 409, `AI_CONVERSATION_NOT_FOUND` 404, `AI_REQUEST_IN_PROGRESS` 409, `AI_INDEX_RETRY_NOT_ALLOWED` 409, `AI_PROVIDER_UNAVAILABLE` 503. M10 adds `AI_RATE_LIMIT_EXCEEDED` 429 and `AI_USAGE_GUARD_UNAVAILABLE` 503 only to the two interactive Project Assistant POST operations. Conversation reads/rename/delete and document-index status/retry remain outside the usage budget. `AI_INDEX_NOT_READY` chưa cần cho operation hiện tại.

## AI M9 – KBase Guide API

`POST /api/v1/ai/guide/query` yêu cầu Bearer JWT và là endpoint stateless: không có `projectId`, không đọc project document/vector/conversation, và không tạo Guide conversation/message row. Request là `{ "message": "...", "context": [{"role":"USER|ASSISTANT","content":"..."}] }`; `context` optional, tối đa 8 turns và chỉ có `USER`/`ASSISTANT` (không chấp nhận SYSTEM). Context chỉ hỗ trợ follow-up, không phải evidence.

Response `200 GuideQueryResponse` gồm `answer`, `answerType` (`GROUNDED` hoặc `NO_EVIDENCE`) và `sources[]`. Mỗi source chỉ công khai `sourceKey`, safe `title`, `section`; không trả content hash, vector, job payload, filesystem path hoặc internal documents. `GROUNDED` dùng duy nhất hai product specs đã review; câu hỏi off-topic/không có evidence trả refusal `NO_EVIDENCE` deterministic với sources rỗng, không gọi chat provider. M10 chọn similarity threshold production mặc định `0.70` từ fixture evaluation; cấu hình nullable rỗng vẫn fail-closed cho Guide. Guide request dùng cùng per-user budget với Project Assistant; provider failure dùng `503 AI_PROVIDER_UNAVAILABLE`, Redis guard failure dùng `503 AI_USAGE_GUARD_UNAVAILABLE`, và vượt limit dùng `429 AI_RATE_LIMIT_EXCEEDED`.

## AI M10 – Usage Guard / Observability / Hardening

Usage guard là KBase application boundary, không phải global HTTP/security limiter. Ba operation bị tính quota là:

- `POST /api/v1/projects/{projectId}/ai/conversations`
- `POST /api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages`
- `POST /api/v1/ai/guide/query`

Budget dùng chung theo authenticated `userId` giữa Project Assistant và Guide: mặc định 20 request trong fixed window 1 phút. Redis key có dạng `kbase:ai:rate:{userId}:{floor(epochMillis/windowMillis)}`; Lua thực hiện atomic `INCR` và chỉ set TTL trên increment đầu tiên. Authorization/capability checks chạy trước khi charge; accepted `NO_EVIDENCE` và provider failure vẫn consume một unit. Redis failure là `503 AI_USAGE_GUARD_UNAVAILABLE` chỉ trên guarded AI operations; Core, AI reads, async document retry và background jobs không phụ thuộc guard.

The public error schema remains `ApiErrorResponse`. `AI_RATE_LIMIT_EXCEEDED` is the only 429 code for M10; it never exposes Redis keys, counters or backend details. `AI_USAGE_GUARD_UNAVAILABLE` is the safe 503 for rate-state failure and never exposes the raw Redis exception.

M10 operational signals use a KBase-owned Micrometer facade with finite tags only: interactive request/outcome and latency, rate outcomes, provider outcome/latency, retrieval candidate count, `NO_EVIDENCE`, job execution/outcome/latency, job depth by state, and failed/stale gauges. No Actuator or public metrics endpoint was added. Logs and schemas do not expose prompts, chunks, answers, vectors, hashes, storage keys, job payloads/leases, Redis keys/counts, provider request/response material or credentials.

## Enum và Kiểu Dùng Chung

### SystemRole

| Giá trị | Mô tả | Deprecated |
| ------- | ----- | ---------- |
| ADMIN   | System-level administrative override | Không |
| USER    | Normal authenticated account | Không |

### UserStatus

| Giá trị  | Mô tả | Deprecated |
| -------- | ----- | ---------- |
| ACTIVE   | Tài khoản dùng được | Không |
| DISABLED | Bị vô hiệu hóa; chặn login/refresh/access ngay lập tức | Không |

### ProjectRole

| Giá trị | Mô tả | Deprecated |
| ------- | ----- | ---------- |
| OWNER   | Project role, lưu trong `project_members.role`; mỗi project đúng một OWNER | Không |
| MEMBER  | Project role của thành viên thường | Không |

### UserResponse

| Field         | Kiểu       | Bắt buộc | Mô tả |
| ------------- | ---------- | -------- | ----- |
| id            | UUID       | Có       | User ID |
| email         | String     | Có       | Email đã normalize |
| displayName   | String     | Có       | Tên hiển thị |
| systemRole    | SystemRole | Có       | ADMIN/USER |
| status        | UserStatus | Có       | ACTIVE/DISABLED |
| emailVerified | Boolean    | Có       | Derived từ email_verified_at; password hash không bao giờ trả về |
| createdAt     | ISO-8601   | Có       | Thời điểm tạo tài khoản |
| updatedAt     | ISO-8601   | Có       | Thời điểm cập nhật gần nhất |

Lưu ý: object `user` lồng trong `LoginResponse` (M6) là projection rút gọn không có `createdAt`/`updatedAt`.

## Endpoint Deprecated

| Method  | Path | Thay thế bởi | Ngày dự kiến loại bỏ |
| ------- | ---- | ------------ | -------------------- |
| Chưa có |      |              |                      |

## Breaking Changes

| Phiên bản hoặc ngày | Thay đổi | Ảnh hưởng | Migration |
| ------------------- | -------- | --------- | --------- |
| Chưa có             |          |           |           |

## Verification

Trước khi cập nhật hoặc phát hành API contract:

* Backend build thành công.
* API hoặc contract test thành công.
* Schema phản ánh đúng endpoint thực tế.
* Authentication và authorization được kiểm tra.
* Error response tuân theo `docs/API_CONVENTIONS.md`.
* Client liên quan được kiểm tra nếu contract thay đổi.
* Breaking change được ghi nhận.
* Không chứa secret hoặc dữ liệu production.

## Quy tắc Cập nhật

* Ưu tiên sinh file từ nguồn contract chính thức.
* Không chỉnh tay nếu file được sinh tự động.
* Nếu đang cập nhật thủ công, phải đồng bộ cùng thay đổi API.
* Không dùng ví dụ chứa dữ liệu thật hoặc nhạy cảm.
* Mọi thay đổi breaking phải được ghi trong execution plan.
