# KBase – AI Chatbot v1
## Persistence and Vector Search Design

**Version:** Draft 1  
**Status:** Accepted design baseline  
**Database:** PostgreSQL 17-compatible runtime with pgvector  
**Vector dimensions:** 768  
**Distance:** cosine

---

# 1. Purpose

Tài liệu này định nghĩa persistent model, pgvector ownership, durable job model, deletion/retention semantics và vector query invariants của AI v1.

Đây là **design target**, chưa phải current generated schema. `docs/generated/db-schema.md` chỉ được cập nhật khi Flyway migration thực tế được triển khai và verify.

---

# 2. Core Persistence Rules to Preserve

- Flyway là schema source of truth.
- Hibernate không tự create/update production schema.
- Core migrations V1–V3 không được sửa.
- AI schema dùng migration version mới.
- PostgreSQL vẫn là durable store.
- Project/document/user foreign keys phải phản ánh current Core tables.
- Same-project isolation phải được bảo vệ ở schema/query, không chỉ service code.
- Vector storage không được tách thành opaque framework-owned table thiếu KBase FK.
- Embedding/vector không xuất hiện trong public REST DTO.

---

# 3. pgvector Extension

AI v1 cần pgvector extension trong PostgreSQL runtime/test environment.

Flyway AI migration phải có:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Local/Test PostgreSQL image phải support extension trước khi migration chạy.

M0/M1 khóa exact pgvector-enabled image/version compatible với PostgreSQL 17 baseline. Không đổi production database technology.

---

# 4. Embedding Dimension

Baseline:

```text
embedding model      = gemini-embedding-2
embedding dimensions = 768
column type          = vector(768)
distance             = cosine
```

Dimension là schema-affecting configuration. Runtime config phải bằng schema dimension.

Application startup/verification phải fail clearly nếu configured dimensions không khớp schema expectation.

Không silently store 3072-dimensional vectors vào schema 768.

---

# 5. Table Overview

AI v1 thêm các logical tables:

```text
document_ai_indexes
document_ai_chunks

ai_conversations
ai_messages
ai_message_sources

ai_jobs

ai_guide_sources
ai_guide_chunks
```

Exact migration naming/constraint naming phải theo Core conventions và được khóa ở M2.

---

# 6. document_ai_indexes

Một row cho mỗi Core document có AI lifecycle record.

Conceptual columns:

| Column | Meaning |
|---|---|
| `document_id UUID PK` | FK tới `documents.id`, ON DELETE CASCADE |
| `project_id UUID NOT NULL` | project scope; composite integrity với document |
| `status VARCHAR` | PENDING / PROCESSING / READY / FAILED / UNSUPPORTED |
| `failure_reason VARCHAR NULL` | safe category |
| `source_hash VARCHAR NULL` | hash của binary/content source đã index |
| `active_version BIGINT NULL` | successful chunk version currently queryable |
| `desired_version BIGINT NOT NULL` | version worker đang/ sẽ build |
| `chunking_version VARCHAR` | algorithm/version identifier |
| `embedding_model VARCHAR` | configured model id |
| `embedding_dimensions INT` | must be 768 for v1 schema |
| `attempt_count INT` | current indexing attempt summary |
| `last_error_code VARCHAR NULL` | safe internal category/code |
| `indexed_at TIMESTAMPTZ NULL` | last successful activation |
| timestamps | created/updated |

Critical FK:

```text
(document_id, project_id)
→ documents(id, project_id)
ON DELETE CASCADE
```

Core `documents` already has unique `(id, project_id)`.

---

# 7. Index Status Semantics

## PENDING

Durable indexing intent exists but active processing chưa bắt đầu.

## PROCESSING

Worker đang build desired version.

## READY

`active_version` exists và retrieval được phép.

## FAILED

No usable active version for initial indexing, hoặc latest required initial attempt đã exhaust retry.

For future reindex with existing active version, user-facing state may remain `READY` while internal job retries replacement; AI v1 current Core has no binary replace API nên case này chủ yếu là evolution path.

## UNSUPPORTED

Core document tồn tại nhưng extension/content type không thuộc AI v1 indexing types.

---

# 8. document_ai_chunks

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | chunk id |
| `project_id UUID NOT NULL` | security/query scope |
| `document_id UUID NOT NULL` | source document |
| `index_version BIGINT NOT NULL` | staging/active version |
| `chunk_index INT NOT NULL` | stable ordinal inside version |
| `content TEXT NOT NULL` | extracted chunk text |
| `page_number INT NULL` | PDF source location |
| `slide_number INT NULL` | PPT source location |
| `section_title VARCHAR NULL` | heading/section |
| `token_count INT NULL` | diagnostic/tuning metadata |
| `content_hash VARCHAR NOT NULL` | chunk hash |
| `embedding vector(768) NOT NULL` | semantic vector |
| `created_at TIMESTAMPTZ` | creation time |

Required invariants:

```text
UNIQUE(document_id, index_version, chunk_index)

(document_id, project_id)
→ documents(id, project_id)
ON DELETE CASCADE
```

Index version not equal to `document_ai_indexes.active_version` must never be returned by normal retrieval.

---

# 9. Vector Indexes

Baseline ANN index:

```sql
CREATE INDEX ... ON document_ai_chunks
USING hnsw (embedding vector_cosine_ops);
```

Also create relational indexes for:

- `project_id`;
- `document_id`;
- `(document_id, index_version)`;
- job/status queries.

Exact HNSW tuning params should remain default/configured only after benchmark; do not invent performance knobs without evidence.

---

# 10. Project-Scoped Retrieval Query

Security invariant:

```text
project_id filtering occurs inside SQL
```

Conceptual query:

```sql
SELECT
  c.id,
  c.document_id,
  c.content,
  c.page_number,
  c.slide_number,
  c.section_title,
  1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS score
FROM document_ai_chunks c
JOIN document_ai_indexes i
  ON i.document_id = c.document_id
 AND i.project_id = c.project_id
WHERE c.project_id = :projectId
  AND i.status = 'READY'
  AND c.index_version = i.active_version
ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
LIMIT :candidateLimit;
```

Không chạy global ANN query rồi filter `project_id` trong Java.

Application vẫn phải authorize `projectId` trước query; SQL filter là defense-in-depth và leakage boundary.

---

# 11. Vector Repository Boundary

Vector persistence/query có thể dùng dedicated repository với JDBC/native SQL thay vì ép JPA map vector type.

Allowed:

```text
AiChunkRepository
  └── JdbcTemplate/native PostgreSQL vector SQL
```

Not allowed:

```text
Controller → JdbcTemplate
Service → raw SQL string scattered across methods
Spring AI auto schema as source of truth
```

Exact pgvector JDBC binding library/type được khóa tại M0/M2.

---

# 12. Index Version Activation

Worker không delete active chunks trước khi replacement complete.

Conceptual:

```text
active_version = 3
desired_version = 4

build all v4 chunks
→ validate count/content
→ one DB transaction:
     set active_version = 4
     status = READY
     indexed_at = now
→ cleanup inactive v3 chunks after activation
```

Nếu v4 fail trước activation, v3 remains active.

For initial indexing, `active_version` is null until success.

---

# 13. Source Hash

Worker computes a deterministic SHA-256 source hash from the actual binary/content stream read through `StorageService`.

Purpose:

- identify unchanged source;
- avoid unnecessary re-embedding;
- protect late/stale job activation;
- support future replacement/versioning.

Do not use filename/displayName as content identity.

Metadata-only change does not force embedding rebuild.

Current metadata should be joined/read from Core document tables when displaying source name rather than re-embedding metadata.

---

# 14. ai_conversations

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | conversation |
| `project_id UUID NOT NULL` | knowledge scope |
| `created_by_user_id UUID NOT NULL` | private owner |
| `title VARCHAR(100) NOT NULL` | deterministic initial or user rename |
| `created_at` | created |
| `updated_at` | list ordering |

FK:

```text
project_id → projects(id) ON DELETE CASCADE
created_by_user_id → users(id)
```

Conversation intentionally does **not** FK to `project_members` because conversation must survive 7-day membership absence.

Access always checks current membership separately.

---

# 15. Conversation Quota Concurrency

Product invariant:

```text
max 5 conversations / user / project
```

Count-then-insert without serialization is invalid.

Baseline implementation:

```text
transaction
→ lock current users row FOR UPDATE for createdByUserId
→ count conversations for (projectId, userId)
→ if >= 5 reject
→ insert conversation
```

Serializing conversation creation for one user across projects is acceptable for v1.

Alternative advisory-lock implementation requires design update/verification; agent must not rely on an unlocked count.

---

# 16. ai_messages

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | message |
| `conversation_id UUID NOT NULL` | parent |
| `role VARCHAR` | USER / ASSISTANT |
| `content TEXT NULL` | content; may be null for failed assistant placeholder |
| `generation_status VARCHAR` | PROCESSING / COMPLETED / FAILED |
| `answer_type VARCHAR NULL` | GROUNDED / NO_EVIDENCE for completed assistant |
| `model VARCHAR NULL` | provider model snapshot |
| `failure_code VARCHAR NULL` | safe AI error category |
| `created_at` | order |
| `completed_at NULL` | lifecycle |

USER messages are stored as `COMPLETED`.

Assistant placeholder starts `PROCESSING`, then becomes `COMPLETED` or `FAILED`.

A partial unique index must enforce at most one active assistant generation per conversation:

```text
UNIQUE(conversation_id)
WHERE role = 'ASSISTANT'
  AND generation_status = 'PROCESSING'
```

This is a database backstop in addition to service logic.

---

# 17. ai_message_sources

Historical citation snapshot.

Conceptual columns:

| Column | Meaning |
|---|---|
| `assistant_message_id UUID` | parent assistant message |
| `source_order INT` | citation order |
| `document_id UUID NULL` | live document FK, ON DELETE SET NULL |
| `chunk_id UUID NULL` | live chunk FK, ON DELETE SET NULL |
| `document_id_snapshot UUID NOT NULL` | historical original id |
| `document_name_snapshot VARCHAR NOT NULL` | historical display name |
| `page_number_snapshot INT NULL` | location |
| `slide_number_snapshot INT NULL` | location |
| `section_title_snapshot VARCHAR NULL` | location |
| `retrieval_score DOUBLE PRECISION NULL` | diagnostic score |

Primary/unique key can be `(assistant_message_id, source_order)`.

Source availability is derived:

```text
document_id != null
→ potentially available, still requires current DocumentAuthorizationService

document_id == null
→ SOURCE_UNAVAILABLE
```

Source snapshot is not authorization.

---

# 18. ai_jobs

One durable job table is preferred for v1.

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | job |
| `job_type VARCHAR` | DOCUMENT_INDEX / DOCUMENT_REINDEX / CONVERSATION_PURGE / GUIDE_REINDEX |
| `status VARCHAR` | PENDING / PROCESSING / RETRY / DONE / FAILED / CANCELLED |
| `project_id UUID NULL` | scope |
| `document_id UUID NULL` | document job |
| `user_id UUID NULL` | retention scope |
| `dedup_key VARCHAR NOT NULL` | semantic idempotency key |
| `payload JSONB NULL` | small versioned parameters only |
| `run_at TIMESTAMPTZ NOT NULL` | due time |
| `attempt_count INT NOT NULL` | attempts |
| `max_attempts INT NOT NULL` | bounded |
| `lease_until TIMESTAMPTZ NULL` | crash recovery |
| `locked_by VARCHAR NULL` | worker diagnostic |
| `last_error_code VARCHAR NULL` | safe category |
| timestamps | created/updated/completed |

Do not place document content, prompt or secret in `payload`.

---

# 19. Job Claiming

Conceptual claim flow:

```text
transaction
→ select due PENDING/RETRY jobs
   WHERE run_at <= now
   FOR UPDATE SKIP LOCKED
   LIMIT batch
→ mark PROCESSING + lease_until + locked_by
→ commit
→ execute outside claim transaction
```

Completion/failure update runs in separate short transaction.

Stale recovery:

```text
PROCESSING with lease_until < now
→ eligible for RETRY/reclaim according to attempt policy
```

No exactly-once assumption. Handler must be idempotent.

---

# 20. Conversation Retention Job

On membership loss:

```text
dedup_key = conversation-purge:{projectId}:{userId}
run_at = lostAt + 7 days
```

Purge handler:

1. load current membership;
2. if membership exists, mark job CANCELLED/DONE without deleting;
3. if no membership and grace expired, delete `ai_conversations` for that user/project;
4. message/source cascade follows;
5. mark job DONE.

Rejoin before due date cancels or neutralizes matching purge job transactionally.

Project deletion removes project conversations immediately and may cascade/delete pending project jobs.

---

# 21. ai_guide_sources

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | source |
| `source_key VARCHAR UNIQUE` | canonical packaged path/key |
| `content_hash VARCHAR NOT NULL` | source hash |
| `active_version BIGINT NULL` | active chunks |
| `desired_version BIGINT NOT NULL` | current desired |
| `status VARCHAR` | PENDING / PROCESSING / READY / FAILED |
| `indexed_at NULL` | success |
| timestamps | lifecycle |

No client API can add arbitrary source path in v1.

Allowlist exists in application/build configuration reviewed in source control.

---

# 22. ai_guide_chunks

Conceptual columns:

| Column | Meaning |
|---|---|
| `id UUID PK` | chunk |
| `guide_source_id UUID NOT NULL` | source |
| `index_version BIGINT NOT NULL` | version |
| `chunk_index INT NOT NULL` | ordinal |
| `content TEXT NOT NULL` | product documentation text |
| `heading_path VARCHAR NULL` | markdown heading context |
| `token_count INT NULL` | tuning |
| `content_hash VARCHAR NOT NULL` | hash |
| `embedding vector(768) NOT NULL` | vector |
| `created_at` | timestamp |

HNSW cosine index may be shared across Guide chunks because Guide corpus has no project security partition.

Guide retrieval never queries `document_ai_chunks`.

---

# 23. Deletion and FK Summary

```text
projects delete
→ document Core rows cascade
→ document_ai_indexes/chunks cascade
→ ai_conversations/messages/sources cascade
→ project-scoped jobs removed/cancelled

documents delete
→ document_ai_indexes/chunks cascade
→ ai_message_sources live FK SET NULL
→ source snapshot remains in chat history

conversation delete
→ messages cascade
→ sources cascade

user membership delete
→ conversation rows remain for 7 days
→ access denied by current membership
→ purge job decides eventual hard delete
```

---

# 24. Migration Verification

AI schema milestone must verify on fresh pgvector-enabled PostgreSQL:

- Core V1–V3 apply unchanged.
- New AI migration applies.
- `vector` extension exists.
- expected AI tables/constraints/indexes exist.
- vector dimension is 768.
- composite document/project FK works.
- HNSW index exists.
- Hibernate/JPA validation succeeds for mapped entities.
- vector JDBC repository can insert/query.
- cross-project query returns zero foreign project chunks.
- project/document deletes produce designed cascade/SET NULL behavior.

`FlywayMigrationIntegrityTest` currently asserts three migrations/ten business tables; AI milestone must intentionally update that verification rather than work around it.

---

# 25. Generated Documentation Rule

After migration implementation:

- update/regenerate `docs/generated/db-schema.md`;
- update `docs/DATABASE.md` from planned to implemented state;
- record migration verification in active plan/current state.

Before migration implementation, this design document is authoritative for planned AI schema and generated schema remains Core-only.
