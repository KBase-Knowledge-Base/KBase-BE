# M12 – Document Search / Pagination / Sorting

## Objective

Implement only Core v1 project-scoped document metadata discovery from SD-01, SD-04, SD-06, SD-08 and SD-12.

## Entry gate

M11 Gate is PASS on `2026-09-18`: document lifecycle and project hard-delete API evidence passed on PostgreSQL + MinIO Testcontainers, with `mvn -B -ntp test` and `mvn -B -ntp clean verify` both passing 184 tests. No M11 functional blocker remains.

## In scope

- `DocumentSearchCriteria`, authorized `DocumentSearchService`, and `GET /api/v1/projects/{projectId}/documents`.
- Approved metadata filters (`q`, folder/category/tag, file kind, uploader, created range), standard pagination, and the document sort whitelist.
- PostgreSQL repository/specification/API integration evidence and manual API-schema synchronization.

## Out of scope

- M13 OpenAPI/Swagger runtime, frontend, schema/migration changes, lifecycle/storage/hard-delete changes.
- Content extraction, file full-text search, embeddings, vector/semantic search, AI and RAG.

## Fixed decisions

- Every query requires `projectId`, authorization uses `ProjectAuthorizationService.requireProjectAccess`, and ADMIN keeps its existing system-level override.
- `q` searches only display name, original filename, description, category name, and tag name.
- Tag predicates use `EXISTS` over `DocumentTag`, not a collection join, so documents are not duplicated and count queries remain correct.
- Pagination baseline remains `page=0`, `size=20`, maximum `100`; only `displayName`, `createdAt`, `updatedAt`, and `sizeBytes` may reach `Sort`.

## Progress log

- 2026-09-18: M12 opened after re-checking M11 Gate and a clean 184-test baseline.
- 2026-09-18: Implemented criteria/service/endpoint, canonical whitelist-to-persistence sort mapping, and PostgreSQL Testcontainer API matrix.
- 2026-09-18: Focused M12 suite passed 16/16; `mvn -B -ntp test` and `mvn -B -ntp clean verify` both passed 188 tests with 0 failures/errors/skips. API schema, state and quality records were synchronized; no migration was needed.

## Completion gate

- Metadata search contract matches SD-04.
- Project isolation and MEMBER/OWNER/ADMIN authorization pass; non-member is denied.
- All filters, combined filtering, pagination, sorting, invalid-sort error contract, and duplicate-tag behavior are proven on PostgreSQL.
- No content/full-text/vector/AI search is introduced.

## Outcome

- M12 Gate: **PASS** on 2026-09-18.
- All M12 acceptance criteria are proven by `DocumentSearchIntegrationTest` on PostgreSQL Testcontainers and full regression.
- No blocker remains beyond the archive-level absence of Git implementation history recorded in `CURRENT_STATE.md`.
- M13 was not started.
