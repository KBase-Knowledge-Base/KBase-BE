# M14 – Full Docker Runtime Verification

## Objective

Run Core v1 in the intended local Docker topology and prove persistence boundaries, Flyway startup, authentication, storage and core API flows against the containerized backend — without adding features or changing M0–M13 behavior (SD-01 §37, SD-03, SD-05, SD-07, SD-09, SD-12).

## Entry gate

M13 Gate is PASS on `2026-09-18`: OpenAPI runtime verified by 21 contract tests, full suite 209/209 via `mvn -B -ntp test` and `mvn -B -ntp clean verify`. No M13 functional blocker remains. Docker daemon 29.8.0 with Compose v5.5.1 is available.

## In scope

- `Dockerfile` (DOCKER-01): reproducible multi-stage build, env-driven runtime, stateless backend container.
- `docker-compose.yml` (DOCKER-02/03): `backend` joins existing `postgres`/`minio`/`redis`; `postgres_data` → `/var/lib/postgresql/data`, `minio_data` → `/data`, Redis stays tmpfs-ephemeral; MinIO healthcheck so `depends_on: service_healthy` works without sleeps.
- Runtime environment wiring (DOCKER-04): service-name hosts (`postgres`, `redis`, `minio`), internal ports (5432/6379/9000), all credentials/secrets from environment, local `.env` git-ignored, `.env.example` refreshed.
- Flyway startup verification (DOCKER-05): migrations V1–V3 applied from empty `postgres_data`, Hibernate `ddl-auto=validate` pass, Flyway history persists across backend restart.
- Persistence smoke (DOCKER-06): data survives postgres/minio container recreation via named volumes; Redis OTP state may vanish on Redis recreation and resend still works; backend restart keeps data.
- Auth runtime smoke (DOCKER-07): register → OTP state in Redis container → email captured by a local mail double (Gmail stays external; automated run never touches real Gmail) → verify-email → login → refresh → logout.
- Core runtime smoke (DOCKER-08): project create, invitation flow, folder/category/tag, upload, download, preview range, search, delete through the containerized backend.

## Out of scope

- M15 freeze/verification, frontend container, AI/RAG, Kubernetes/Terraform, new endpoints, schema changes, business behavior changes.
- Real Gmail delivery (manual production smoke only, per SD-12/DOCKER-07).

## Fixed decisions

- Gmail SMTP is not Dockerized in the designed topology; the automated smoke uses an explicitly optional compose override (`docker-compose.mail-test.yml`, axllent/mailpit) as a local mail double, removed from the run afterwards.
- Backend connects to `postgres:5432`, `redis:6379`, `http://minio:9000` — never localhost — and keeps profile `local` so the documented local bucket auto-create/initialize behavior applies.
- No arbitrary sleeps: startup dependencies use Compose healthchecks (`pg_isready`, `redis-cli ping`, MinIO health endpoint).
- Secrets stay in git-ignored `.env`; compose uses `:?required` interpolation where credentials are mandatory.
- Local refresh cookie `Secure=false` must match the documented contract (prod `true`, local `false`).

## Progress log

- 2026-09-18: M14 opened after re-checking M13 Gate and a clean 209-test baseline.
- 2026-09-18: Implemented `Dockerfile`, compose `backend` service with healthcheck gating, mail-test override, `.env` wiring, local refresh-cookie Secure=false default per documented contract.
- 2026-09-18: Clean startup verified (build, healthchecks, Flyway V1–V3, Hibernate validate, OpenAPI 200, bucket auto-created); auth journey and core flows passed through the containerized backend.
- 2026-09-18: Found a real M11 bug at runtime: OWNER-path project hard delete failed with `TransientPropertyValueException` (Hibernate 7.4) because the caller's managed membership was in the persistence context; fixed minimally with `ProjectRepository.deleteProjectCascade` bulk delete, updated unit test, added OWNER-path regression test to `DocumentApiIntegrationTest`.
- 2026-09-18: Persistence verified (backend restart, postgres/minio force-recreate, Redis recreation + resend); log leak scan clean after excluding `UserDetailsServiceAutoConfiguration`; full suite 210/210 via `mvn clean verify`.

## Verification plan

- `docker compose -f docker-compose.yml config --quiet` (plus mail-test override combination). — passed
- Clean build + startup: `docker compose up -d --build`, health statuses, backend logs (Flyway/Hibernate). — passed
- HTTP smokes against `http://localhost:8080` (OpenAPI, auth journey, core flows, binary/range, search). — passed
- Persistence: `docker compose restart` + `--force-recreate` for postgres/minio (data stays), Redis recreation (OTP lost, resend works), backend restart (data + no pending migrations). — passed
- Log leakage grep over `docker compose logs backend`. — passed (0 hits after excluding the generated-password log)
- `mvn -B -ntp clean verify` regression after config change. — passed 210/210

## Completion gate (M14 Gate)

- Full Docker topology works end-to-end. — PASS
- `postgres_data` and `minio_data` persist across container recreation. — PASS
- Redis is containerized and ephemeral; resend after loss works. — PASS
- Flyway runs against containerized PostgreSQL with Hibernate validate. — PASS
- Backend does not depend on host-local PostgreSQL/Redis/MinIO. — PASS (service-name wiring verified)
- No secret leakage in container logs; no committed credentials. — PASS

## Outcome

- M14 Gate: **PASS** on 2026-09-18.
- Full topology verified in real Docker runtime; persistence boundaries and Redis ephemerality proven; logs clean.
- One M11 runtime bug found and fixed with regression test (OWNER-path project hard delete); full regression 210/210.
- Gmail real-delivery manual smoke remains optional (automated path used the mail double).
- M15 was not started.
