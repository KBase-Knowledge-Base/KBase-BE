# M11 – Document Lifecycle + Project Hard Delete

## Objective

Implement only the Core v1 document lifecycle and the storage-aware project hard delete defined by SD-01, SD-04, SD-08, SD-09, SD-10 and SD-12.

## Entry gate

M10 Gate is verified from the completed M10 plan, `docs/CURRENT_STATE.md`, source inspection, and the recorded 169-test clean-verify result. No functional M10 blocker is open. The repository is clean at M11 start. A fresh `mvn -B -ntp test` baseline was started; the harness command time limit interrupted its output before completion, so the pre-M11 recorded M10 full-suite evidence remains the baseline evidence and M11 will run its own full verification before completion.

## In scope

- Document DTOs, mapper, validation, same-project metadata resolver, document authorization, single and batch upload, metadata get/update, download, preview/range, document hard delete.
- OWNER/ADMIN storage-aware project hard delete using the DB storage-key projection.
- Error mapping, tests, API schema and state/quality/reliability documentation required by these APIs.

## Out of scope

- M12 search, filters, pagination, sorting, OpenAPI runtime, frontend, schema changes, and changes to existing auth/OTP/invitation/organization behavior.

## Fixed decisions

- `Document.uploadedBy` remains a `User`; former members lose all access but their documents remain.
- Storage is reached only through `StorageService`; metadata remains PostgreSQL and bytes remain MinIO.
- Key format is `projects/{projectId}/documents/{documentId}.{extension}` and never changes on metadata rename/move.
- Upload is authorize → validate → resolve metadata → generate ID/key → upload → persist Document → persist DocumentTag, with best-effort storage compensation after DB failure.
- Delete is storage first, then DB. Project deletion uses DB key projection, storage delete-all, then project DB delete; a storage failure stops DB deletion.

## Verification plan

- Unit tests for validation, authorization, compensation, deletion, range parsing and metadata update.
- PostgreSQL + MinIO Testcontainers API/integration tests for critical document and project deletion journeys.
- Regressions: relevant organization/auth/storage suites, `mvn -B -ntp test`, then `mvn -B -ntp clean verify`.

## Progress log

- 2026-09-18: M11 opened after M10 Gate evidence review. Implementation not yet started.
- 2026-09-18: Added document DTOs/controller/service, centralized document authorization, streaming validation/delivery, storage compensation, document hard delete, and project storage-first hard delete. Focused unit suite (12/12) and PostgreSQL+MinIO Testcontainer lifecycle journey (1/1) pass.
- 2026-09-18: M11 remains IN_PROGRESS. API-level authorization/range/partial-failure matrix and full regression/clean-verify have not yet been completed; do not move this plan or mark the M11 Gate passed.
- 2026-09-18: `mvn -B -ntp test` and `mvn -B -ntp clean verify` both passed with 177 tests, 0 failures/errors/skips. Gate remains open solely for missing required M11 API-matrix evidence.
- 2026-09-18: Completed the missing API matrix with `DocumentApiIntegrationTest` 3/3 against PostgreSQL + MinIO Testcontainers and the real security filter chain. It covers single/batch validation (including application-level rejected-batch no-partial persistence), same-project metadata rejection, MEMBER/OWNER/ADMIN/former-member access, download stream, Office rejection, MP4 206/416, document cascade and project hard delete. The matrix exposed and fixed three M11 defects: MVC could not write `StreamingResponseBody` as binary, tag relations were not materialized for response mapping, and the managed Document returned from preassigned-ID persistence was not retained. Focused document unit/controller tests 10/10 plus the project-delete unit flow and lifecycle integration 1/1 pass. Final `mvn -B -ntp test` and `mvn -B -ntp clean verify` pass 184 tests with 0 failures/errors/skips; package/repackage succeeds.

## Completion gate

- All DOC-01 through DOC-11 and PROJ-DELETE-01 acceptance criteria pass, including authorization matrix, PostgreSQL/MinIO compensation, document delete, and project hard delete.

## Outcome

- M11 Gate: **PASS** on 2026-09-18.
- No schema migration was needed; PostgreSQL retains metadata and MinIO retains binary data through the existing M10 boundary.
- No M12 search/filter/pagination or frontend work was started.
- No open M11 blocker remains. The archive's absent Git metadata remains the harness-level limitation recorded in `CURRENT_STATE.md`.
