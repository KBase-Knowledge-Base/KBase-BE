# KBase – AI Chatbot v1
## REST API Specification

**Version:** Draft 1  
**Scope:** Backend AI v1 public REST contract target  
**Base path:** `/api/v1`  
**Transport:** JSON, non-streaming

---

# 1. Purpose

Tài liệu này định nghĩa REST contract target cho Project Assistant, document AI indexing visibility/retry và KBase Guide.

Đây là design contract. `docs/generated/api-schema.md` vẫn phản ánh Core runtime cho tới khi AI controllers/DTO được triển khai và runtime OpenAPI được verify.

Core API conventions, JWT authentication, `ApiErrorResponse`, pagination và `X-Request-Id` vẫn áp dụng.

---

# 2. Authentication and Authorization

Tất cả AI v1 endpoints yêu cầu Bearer JWT.

KBase Guide không yêu cầu `projectId`, nhưng vẫn authenticated trong v1.

Project Assistant:

```text
JWT authenticated
+ current ProjectAuthorizationService.requireProjectAccess(projectId)
+ conversation.createdByUserId == current user
```

ADMIN override chỉ thay project membership check. ADMIN không được bypass conversation ownership.

Document AI retry additionally uses current `DocumentAuthorizationService.requireModifyPermission`.

---

# 3. Common AI Answer Shape

Conceptual response:

```json
{
  "message": {
    "id": "uuid",
    "role": "ASSISTANT",
    "content": "Grounded answer...",
    "answerType": "GROUNDED",
    "generationStatus": "COMPLETED",
    "createdAt": "..."
  },
  "sources": [
    {
      "order": 1,
      "documentId": "uuid",
      "documentName": "Deployment.pdf",
      "pageNumber": 8,
      "slideNumber": null,
      "sectionTitle": "Rollback",
      "availability": "AVAILABLE"
    }
  ]
}
```

`retrievalScore` có thể được giữ internal hoặc expose only if product later needs it. Initial public DTO không cần score.

No evidence:

```json
{
  "message": {
    "id": "uuid",
    "role": "ASSISTANT",
    "content": "Tôi chưa tìm thấy đủ thông tin trong tài liệu hiện có của project để trả lời câu hỏi này.",
    "answerType": "NO_EVIDENCE",
    "generationStatus": "COMPLETED",
    "createdAt": "..."
  },
  "sources": []
}
```

`NO_EVIDENCE` là successful domain outcome, không phải 5xx/provider error.

---

# 4. Conversation Endpoints

## API-AI-001 – Create conversation with first message

```http
POST /api/v1/projects/{projectId}/ai/conversations
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "message": "Quy trình rollback production là gì?"
}
```

Behavior:

1. validate text length/non-blank;
2. authorize current project access;
3. enforce max 5 conversations/user/project atomically;
4. derive deterministic initial title;
5. persist conversation + USER message + generation marker;
6. execute strict-grounded AI turn;
7. return conversation summary + turn result.

Response baseline: `201 Created`.

Provider failure after persistence returns stable error response; created conversation/user message remain discoverable and failed generation state is visible on subsequent read.

Errors:

- `PROJECT_ACCESS_FORBIDDEN`
- `AI_CONVERSATION_LIMIT_REACHED`
- `AI_RATE_LIMIT_EXCEEDED`
- `AI_PROVIDER_UNAVAILABLE`
- validation errors.

## API-AI-002 – List own conversations

```http
GET /api/v1/projects/{projectId}/ai/conversations?page=0&size=20
```

Only current user's conversations.

Default sort fixed to `updatedAt DESC`; arbitrary client sort not required v1.

Response uses existing `PageResponse` convention.

Current project access required before query.

## API-AI-003 – Get conversation

```http
GET /api/v1/projects/{projectId}/ai/conversations/{conversationId}
```

Returns conversation metadata and paginated/recent messages according to implementation DTO.

Conversation must belong to both route project and current user.

Do not leak existence of another user's private conversation. Error mapping should use `AI_CONVERSATION_NOT_FOUND` or equivalent privacy-safe behavior rather than revealing owner.

## API-AI-004 – Rename conversation

```http
PATCH /api/v1/projects/{projectId}/ai/conversations/{conversationId}
```

Request:

```json
{
  "title": "Production rollback"
}
```

Rules:

- creator only;
- current project access required;
- title trim/non-blank/max 100;
- updates `updatedAt`.

Response: `200 OK`.

## API-AI-005 – Delete conversation

```http
DELETE /api/v1/projects/{projectId}/ai/conversations/{conversationId}
```

Creator only, current project access required.

Hard delete.

Response: `204 No Content`.

---

# 5. Message Endpoints

## API-AI-006 – Send message

```http
POST /api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages
```

Request:

```json
{
  "message": "Nếu deploy lỗi thì rollback database thế nào?"
}
```

Behavior:

- current project access + private ownership;
- enforce usage guard;
- reject if active generation exists;
- persist USER message;
- create assistant PROCESSING marker;
- run retrieval;
- deterministic `NO_EVIDENCE` without Gemini when no usable evidence;
- otherwise Gemini grounded generation;
- re-check project access before final completed persistence/response;
- persist sources for grounded answer.

Response: `200 OK` with common AI answer shape.

Errors:

- `AI_CONVERSATION_NOT_FOUND`
- `AI_REQUEST_IN_PROGRESS` → `409 Conflict`
- `PROJECT_ACCESS_FORBIDDEN`
- `AI_RATE_LIMIT_EXCEEDED` → `429 Too Many Requests`
- `AI_PROVIDER_UNAVAILABLE` → `503 Service Unavailable`.

## API-AI-007 – List conversation messages

```http
GET /api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages?page=0&size=50
```

Uses existing pagination envelope.

Order must be deterministic by `createdAt ASC` + stable tie-breaker/id.

Response message records include:

- role;
- content when available;
- generationStatus;
- answerType for completed assistant;
- safe failure code for failed generation if product-visible;
- sources for assistant messages.

Failed provider generation must not contain raw provider error.

---

# 6. Document AI Index Endpoints

## API-AI-008 – Get document AI index status

```http
GET /api/v1/projects/{projectId}/documents/{documentId}/ai-index
```

Read permission follows existing document read access.

Response conceptual:

```json
{
  "documentId": "uuid",
  "status": "READY",
  "failureReason": null,
  "indexedAt": "...",
  "retryAllowed": false
}
```

User-facing status enum:

```text
PENDING
PROCESSING
READY
FAILED
UNSUPPORTED
```

Do not expose:

- source hash;
- embedding model internals unless intentionally documented later;
- raw job error;
- vector;
- storage key.

## API-AI-009 – Retry failed document index

```http
POST /api/v1/projects/{projectId}/documents/{documentId}/ai-index/retry
```

Permission: existing document modify permission.

Allowed only when status `FAILED`.

Creates/re-arms durable job idempotently and returns current index status.

Recommended response: `202 Accepted`.

Errors:

- document/project authorization errors;
- `AI_INDEX_RETRY_NOT_ALLOWED` for other states.

Retry endpoint does not synchronously call Gemini.

---

# 7. KBase Guide Endpoint

## API-AI-010 – Query Guide

```http
POST /api/v1/ai/guide/query
Authorization: Bearer <token>
```

Request conceptual:

```json
{
  "message": "MEMBER có thể xóa file của người khác không?",
  "context": [
    {
      "role": "USER",
      "content": "Project role của KBase là gì?"
    },
    {
      "role": "ASSISTANT",
      "content": "KBase có OWNER và MEMBER ở cấp project."
    }
  ]
}
```

Rules:

- `context` optional and bounded;
- accepted context roles only `USER` / `ASSISTANT`;
- client cannot send SYSTEM role;
- context is non-authoritative;
- Guide retrieval only from approved Guide source tables;
- no project/document access;
- no persistent Guide conversation row;
- strict no-evidence if product docs do not support answer.

Response conceptual:

```json
{
  "answer": "...",
  "answerType": "GROUNDED",
  "sources": [
    {
      "sourceKey": "docs/product-specs/KBase - Core v1 Specification.md",
      "title": "KBase – Core v1 Specification",
      "section": "File Permission Model"
    }
  ]
}
```

Guide source response exposes only approved documentation identifiers/headings, not filesystem secret/internal paths outside allowlist.

---

# 8. Error Contract Additions

AI errors extend existing `ErrorCode` + `ApiErrorResponse`.

Target catalog:

| Code | HTTP | Meaning |
|---|---:|---|
| `AI_CONVERSATION_LIMIT_REACHED` | 409 | user already has 5 conversations in project |
| `AI_CONVERSATION_NOT_FOUND` | 404 | missing/not-owned/private conversation |
| `AI_REQUEST_IN_PROGRESS` | 409 | active generation exists in same conversation |
| `AI_INDEX_NOT_READY` | 409 | operation requires READY index where applicable |
| `AI_INDEX_RETRY_NOT_ALLOWED` | 409 | retry requested outside FAILED |
| `AI_PROVIDER_UNAVAILABLE` | 503 | provider timeout/unavailable/usable provider failure |
| `AI_RATE_LIMIT_EXCEEDED` | 429 | configured AI usage guard exceeded |

Do not encode `NO_EVIDENCE` as an error code.

M0/M4 may split provider configuration/auth errors for diagnostics internally, but client contract must remain safe.

---

# 9. Resource Privacy Rules

- Never expose another user's conversation id/content/source through normal Project Assistant APIs.
- Knowing a conversation UUID does not grant access.
- Route `projectId` must equal conversation project.
- Source document id does not bypass `DocumentAuthorizationService`.
- Guide source ids do not grant access to arbitrary repository files.
- No endpoint returns embedding vectors.
- No endpoint returns AI job payload, lease/worker id or provider raw response.

---

# 10. Non-Streaming Rule

No SSE/WebSocket/token-stream route in initial AI v1.

All endpoints are request/response JSON.

If future streaming is added, it requires separate design for:

- disconnect/partial answer;
- authorization recheck;
- persistence finalization;
- citation finalization;
- rate/accounting behavior.

Do not pre-create streaming endpoint placeholders in v1.

---

# 11. OpenAPI Requirements

When endpoints are implemented:

- annotate canonical AI tags;
- document bearer requirement;
- document private conversation rule;
- document `GROUNDED` / `NO_EVIDENCE`;
- document index status enum;
- document AI error codes;
- document non-streaming response;
- ensure no persistence entities/provider model appear in schemas.

`OpenApiContractIntegrationTest` expected path/operation set must be intentionally updated.

Then synchronize `docs/generated/api-schema.md` from verified runtime contract.

---

# 12. Contract Verification

API milestone must include:

- authentication negative tests;
- cross-project tests;
- conversation ownership tests OWNER/MEMBER/ADMIN;
- quota concurrent create;
- concurrent send;
- no-evidence response;
- provider 503 mapping;
- rate 429 mapping;
- index status/retry permission;
- membership revoke during/after conversation;
- Guide cannot access project corpus;
- generated OpenAPI sensitive-field scan.
