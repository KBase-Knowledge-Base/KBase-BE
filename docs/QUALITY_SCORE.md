# QUALITY_SCORE.md

Tài liệu này theo dõi liệu kho lưu trữ có đang trở nên mạnh hơn hay yếu hơn theo thời gian.

## Thang điểm

- `A`: đã xác minh, có thể đọc được, ổn định, ranh giới được thực thi
- `B`: hoạt động với các khoảng trống nhỏ
- `C`: hoạt động một phần, nhầm lẫn hoặc không ổn định đáng kể
- `D`: bị hỏng, không an toàn, hoặc cấu trúc không rõ ràng
- `-`: chưa có implementation/runtime để chấm; không được tự suy diễn thành điểm đạt

## Domain Sản phẩm

| Domain | Điểm | Xác minh | Khả năng đọc của Agent | Độ ổn định Test | Khoảng trống chính | Cập nhật lần cuối |
|--------|-------|-------------|-----------------|---------------|----------|-------------|
| Authentication & Email Verification | A | M6 suites + M14 Docker runtime journey: register → Redis OTP (key + TTL) → mail double → verify → login/refresh/logout qua containerized backend | Cao - spec/security/service docs rõ | Auth unit + integration + runtime smoke pass trong 210/210 | Gmail thật (manual smoke) chưa chạy | 2026-09-18 |
| Project / Membership / Invitation | A | M7/M8 integration + M11 API + M14 runtime invitation flow và membership matrix qua container | Cao - spec/service/security docs rõ | OWNER/ADMIN delete, storage failure stop-DB và OWNER-path hard delete (regression M14) được cover | Invitation email Gmail thật manual smoke chưa chạy | 2026-09-18 |
| Folder / Category / Tag | A | M9 suites vẫn pass; M11 API test chứng minh folder/category/tag cross-project bị reject lúc upload; M13 contract test verify role docs | Cao - product/design/service/API rules rõ | Authorization, hierarchy, uniqueness, delete dependency và cross-project integrity đã được test | Chi tiết field-level trong api-schema.md vẫn sync thủ công | 2026-09-18 |
| Document / MinIO / Search | A | M12 search suites; M14 runtime upload/download checksum khớp, preview 206/416, hard delete storage-first qua MinIO container | Cao | Storage/lifecycle/search authorization boundary, project isolation và persistence qua `minio_data` được verify | Chi tiết field-level trong api-schema.md vẫn sync thủ công | 2026-09-18 |
| AI Chatbot / RAG | B | M0–M6 executable evidence: provider/indexing foundation plus real pgvector project isolation, active READY/current-document retrieval, deterministic selection, strict NO_EVIDENCE, prompt/label validation, citation snapshots and in-flight revoke; M6 targeted 33/33, full 329/329 | Cao ở mức internal RAG boundary và source docs | Conversation/Guide runtime và AI API chưa có; real Gemini smoke intentionally absent; M5 worker lease heartbeat còn là debt | 2026-09-23 |

## Lớp Kiến trúc

| Lớp | Điểm | Thực thi Ranh giới | Khả năng đọc của Agent | Khoảng trống chính | Cập nhật lần cuối |
|-------|-------|---------------------|-----------------|----------|-------------|
| Controller / API | A | M13 OpenAPI runtime + M14: spec serve trong container, toàn bộ golden journeys chạy qua real HTTP boundary trong Docker | Cao - REST/OpenAPI design có sẵn và runtime spec đã verify | Frontend client chưa tồn tại (deferred) | 2026-09-18 |
| Service / Authorization | A | Authorization matrix + storage-first hard delete verify qua container runtime; bug OWNER-path delete (M11) được phát hiện và fix tại M14 với regression test | Cao - Service/Security design rõ | Không còn gap runtime | 2026-09-18 |
| Repository / Persistence | A | 10 JPA entity, 10 feature-local repository, Hibernate validate + Flyway trong Docker runtime; bulk cascade delete cho project (M14 fix) | Cao - DB/JPA design và integration evidence rõ | Chưa có generator schema tự động | 2026-09-18 |
| Infrastructure Adapters | A | Redis OTP, Gmail SMTP, MinIO adapters verify cả Testcontainer lẫn Docker runtime; bucket auto-create local và ephemeral Redis đúng policy | Cao - adapter boundaries rõ | Gmail thật manual smoke chưa chạy | 2026-09-18 |
| UI | - | Không áp dụng phase hiện tại | Frontend optional | Hoãn | 2026-09-17 |

## Năng lực Kỹ thuật

| Năng lực | Điểm | Bằng chứng | Khoảng trống chính | Cập nhật lần cuối |
|---|---|---|---|---|
| Backend | A | M0–M16 Core implementation/maintenance + M1–M6 AI foundation, indexing and internal RAG verified; Core v1 frozen; full M6 regression 329/329 | Core ổn định; conversation/API là phase tiếp theo và chưa thay đổi Core API | 2026-09-23 |
| AI / RAG | B | M0–M5 foundation plus M6 real pgvector trap/active filtering, bounded selection, NO_EVIDENCE, prompt isolation, label validation, citation lifecycle and revoke-before/after-chat; targeted 33/33, full 329/329 | Chưa có conversation/Guide runtime hoặc AI API; real Gemini chưa gọi | 2026-09-23 |
| Frontend | - | `docs/FRONTEND.md` | Optional và hoãn khỏi phase hiện tại | 2026-09-17 |
| Database và migration | A | Flyway V1–V4 fresh apply + Core V1–V3 upgrade trên pgvector PostgreSQL 17.11; integrity 13/13, AI persistence 11/11, mapping 11/11, upgrade 1/1; M3 real-PostgreSQL job/delete/retention races; Hibernate validate; `docs/generated/db-schema.md` đối chiếu 18 tables/vector(768)/HNSW/FK/index catalog | Chưa có generator schema tự động; full Compose runtime với AI V4 chưa re-run trong slice này | 2026-09-22 |
| API contract | A | Runtime OpenAPI `/v3/api-docs` serve trong container runtime; 21 contract tests; `docs/generated/api-schema.md` đồng bộ theo runtime | Markdown snapshot sync thủ công; chưa có snapshot/breaking-change CI diff (optional theo SD-11) | 2026-09-18 |
| Tích hợp hệ thống | A | Core Docker runtime smoke + M1–M6 Testcontainers/fakes: PostgreSQL 17.11 pgvector, V1–V4, job/index lifecycle, real project vector trap/citation deletion/membership revoke, explicit provider boundary and full regression 329/329; mail/provider real network không dùng | Gmail thật manual smoke và real Gemini connectivity/Compose indexing chưa chạy | 2026-09-23 |
| Kiểm thử | A | M1 pgvector compatibility 1/1, M2 targeted 36/36, M3 targeted 27/27, M4 provider targeted 22/22, M5 targeted 17/17, M6 targeted 33/33; full `mvn -B -ntp clean verify` 329/329; Core golden journeys vẫn giữ pass | Docker smoke chưa script hóa (đã ghi command trong DEVELOPMENT.md) | 2026-09-23 |
| Thiết lập development | A | `docker compose` config + Testcontainers pgvector 0.8.6/PostgreSQL 17.11 verified; V4 fresh/upgrade, M3 lifecycle, M4 provider, M5 extraction/indexing commands, reset/start commands và generated DB schema tài liệu hóa; AI disabled mặc định | Full real-provider/AI indexing Compose runtime chưa verify; Gmail thật chưa verify | 2026-09-23 |
| Reliability | A | Core golden journeys trong Docker runtime (M14/M15), M3 durable job/document/retention races, M4 provider privacy and M5 bounded source/index lifecycle have passed; lease-token/stale recovery, last-good activation, delete no-resurrection and safe categories are documented | No lease heartbeat during long M5 extraction/embedding; health/metrics endpoint chuyên dụng chưa có; provider/runtime chat monitoring là scope sau | 2026-09-23 |
| Deployment và rollback | A | M14/M15 Dockerfile + Compose/runtime verification; M1 healthcheck topology, M2 additive V4 policy, M4 disabled-by-default provider configuration/request-timeout, M5 no-topology/no-migration change and Compose config pass | Real Gemini deployment/connectivity chưa verify; connect-timeout SDK limitation and M5 lease limitation tracked; chưa có production deployment target | 2026-09-23 |
| Quản lý trạng thái repository | A | Git history theo milestone trên nhánh `dev` (M0–M15); `docs/CURRENT_STATE.md`, completed/active execution plans, source registry được duy trì đồng bộ trong M15 | Commits cỡ milestone thay vì cỡ task (master plan §38 khuyến nghị task-level) | 2026-09-19 |

### Quy tắc Đánh giá Năng lực

- Việc một file tài liệu tồn tại không tự động làm tăng điểm.
- `docs/CURRENT_STATE.md` chỉ là bằng chứng tốt khi phản ánh đúng trạng thái đã được xác minh.
- `docs/generated/api-schema.md` chỉ là bằng chứng tốt khi đồng bộ với source of truth.
- Không chấm `A` nếu verification không thể chạy lặp.
- Khi bằng chứng lỗi thời hoặc không thể tái tạo, phải giảm điểm hoặc ghi rõ khoảng trống.
- Không chấm điểm implementation cho một domain chỉ vì design document đã hoàn chỉnh.

## Snapshot Benchmark

| Ngày | Biến thể Harness | Tỷ lệ Hoàn thành | Thử lại | Lỗi trước Review | Ghi chú |
|------|-----------------|----------------|--------|-----------------------|---------|
| 2026-09-17 | `KBase backend harness - pre-implementation` | 0% implementation | N/A | N/A | Source docs + master plan + active M0 slice đã sẵn sàng |
| 2026-09-17 | `KBase backend harness - M0 Gate` | 0% implementation | N/A | N/A | M0 Gate pass; Java 21/Spring Boot 4.1.1 và dependency baseline đã khóa; M1 plan READY; runtime chưa verify |
| 2026-09-17 | `KBase backend harness - M1 Gate` | M1 foundation | N/A | N/A | M1 Gate pass; Maven project, feature-first package skeleton, typed properties, profiles, Compose dependency skeleton và live dependency restart smoke đã verified; M2 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M2 Gate` | M2 schema | N/A | N/A | M2 Gate pass; 3 Flyway migrations tạo 10 persistent tables với đầy đủ PostgreSQL-specific constraint/index; migration integrity 12/12 pass trên PostgreSQL 17 Testcontainer; Hibernate validate pass; M3 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M3 Gate` | M3 persistence baseline | N/A | N/A | M3 Gate pass; 10 entity, 5 enum, 10 repository, projection/query/specification và PostgreSQL mapping/repository integration 11/11 pass; full suite 25/25; M4 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M4 Gate` | M4 shared error/request baseline | N/A | N/A | M4 Gate pass; centralized error contract, request ID/MDC, constraint translation và MockMvc/PostgreSQL verification; full suite 41/41; M5 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M5 Gate` | M5 Redis OTP + Gmail mail infrastructure | N/A | M5 Gate pass; RedisOtpStore/OtpService, MailService/SmtpMailService, protected OTP state, Redis 7.4 Testcontainer, fake SMTP và full suite 58/58; M6 chưa bắt đầu |
| 2026-09-17 | `KBase backend harness - M6 Gate` | M6 Spring Security + authentication | N/A | M6 Gate pass; SecurityConfig/JwtService/JwtAuthenticationFilter, refresh session PostgreSQL hash-only, EmailVerificationService + AuthService, 6 auth endpoints; auth integration 14/14 với PostgreSQL/Redis Testcontainers; full suite 104/104; M7 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M7 Gate` | M7 User / Project / Membership | N/A | M7 Gate pass; ProjectAuthorizationService, user/project/membership APIs, single-OWNER + ADMIN override + documents remain; authorization matrix integration 6/6 trên PostgreSQL; full suite 130/130; M8 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M8 Gate` | M8 Invitation Lifecycle | N/A | M8 Gate pass; InvitationService với secure token (hash-only, không OTP), MailService reuse + rollback, cancel/resend lifecycle, accept PESSIMISTIC_WRITE với concurrency test 2 thread; integration 5/5 trên PostgreSQL; full suite 143/143; M9 chưa bắt đầu |
| 2026-09-18 | `KBase backend harness - M9 Gate` | M9 Folder / Category / Tag | N/A | M9 Gate pass; 12 project-scoped endpoints, authorization matrix, nested folder cycle prevention, case-insensitive uniqueness, non-empty/in-use deletes, DocumentTag-only tag delete; unit 12/12, integration 6/6, integrity regression 23/23, full suite 161/161 |
| 2026-09-18 | `KBase backend harness - M10 Gate` | M10 MinIO Storage Infrastructure | N/A | M10 Gate pass; private unversioned bucket boundary, singleton MinIO adapter behind streaming port, range read and safe batch-delete result handling; unit/config 7/7, MinIO Testcontainer 2/2, full suite 169/169 |
| 2026-09-18 | `KBase backend harness - M12 Gate` | M12 Document Search / Pagination / Sorting | N/A | M12 Gate pass; metadata-only project-scoped search, all filters/combined filters, pagination/sort whitelist, tag duplicate guard and authorization/isolation API matrix pass on PostgreSQL; full suite 188/188 |
| 2026-09-18 | `KBase backend harness - M13 Gate` | M13 OpenAPI / Swagger | N/A | M13 Gate pass; springdoc runtime spec (32 paths/47 operations — đếm lại trên runtime trong M15; bản ghi gốc ghi 48 là miscount), bearerAuth scheme, public/protected/ADMIN security requirements verified, multipart/binary/Range/206/416 docs, ApiErrorResponse + OTP/Gmail/Redis error codes, sensitive-field absence, exposure flags per environment; contract 21/21, full suite 209/209 |
| 2026-09-18 | `KBase backend harness - M14 Gate` | M14 Full Docker Runtime Verification | N/A | M14 Gate pass; clean Docker startup với Flyway V1–V3 + Hibernate validate trong container; golden journeys qua containerized backend; postgres_data/minio_data persistence + Redis ephemeral verified; fix 1 bug M11 (OWNER-path project delete) với regression test; full suite 210/210 |
| 2026-09-19 | `KBase backend harness - M15 Gate (Core v1 Freeze)` | M15 Full Verification / Core v1 Freeze | N/A | M15 Gate pass; **Core v1 FROZEN**. Full release gate 210/210; Docker runtime re-verification từ volume rỗng (Flyway/Hibernate trong container, 10 tables, không OTP table; toàn bộ golden journeys; persistence restart/recreate; Redis ephemeral + resend); static architecture/leakage scans clean; runtime spec 32 paths/47 operations khớp contract test + SD-04 (sửa miscount "48" từ bản ghi M13); consistency audit docs↔code pass; không có code change trong M15 |
| 2026-09-19 | `KBase backend harness - M16 Gate (Post-Audit Fixes)` | M16 Post-Audit Fixes (maintenance sau freeze, owner-approved) | N/A | M16 Gate pass. Full Codebase Audit cùng ngày: 0 BLOCKER / 0 HIGH / 1 MEDIUM / 8 LOW / 12 INFO. Fix 1 MEDIUM + 7 LOW (invitation EXPIRED persistence, batch cleanup idempotent, swagger annotation, JwtProperties.algorithm, LIKE wildcard literal `q`, folder-move pessimistic lock + concurrency test, SMTP sanitized failure log, repo hygiene); không fix: L-02 (cần product decision) + INFO items (phase mới). Full gate 213/210+3 regression pass; không đổi API contract/Flyway schema |
| 2026-09-21 | `KBase AI v1 documentation baseline` | 0% AI implementation | N/A | Product/design/master plan/harness source set được mở trên `feat-AI`; AI capability giữ điểm `-` cho tới khi M0+ tạo executable evidence. Core freeze vẫn đứng. |
| 2026-09-22 | `KBase backend harness - AI v1 M0 Gate` | 0% AI implementation | N/A | M0 Gate PASS; Core `mvn -B -ntp clean verify` 213/213 và Compose config PASS; Spring AI 2.0.1/Gemini options, pgvector PostgreSQL 17/HNSW, Tika 3.3.2 dependency và harness probes PASS; M1 active/READY; AI domain vẫn `-` vì chưa có runtime implementation. |
| 2026-09-22 | `KBase backend harness - AI v1 M1 Gate` | AI runtime foundation | N/A | M1 Gate PASS; dependency tree + Compose config, typed disabled-by-default config, KBase provider ports, deterministic fakes, pgvector compatibility 1/1 và full `mvn -B -ntp clean verify` 228/228; M2 schema active, no AI migration/API/RAG/provider call. |
| 2026-09-22 | `KBase backend harness - AI v1 M2 Gate` | AI persistence schema | N/A | M2 Gate PASS; Flyway V4 + pgvector extension/`vector(768)`, 8 AI tables, HNSW/relational catalog, fresh + V1–V3 upgrade path, Hibernate validate, vector project/active-version SQL isolation, FK/delete/status/quota/generation guards; targeted 36/36 and final full gate 241/241; M3 durable job engine READY. |
| 2026-09-22 | `KBase backend harness - AI v1 M3 Gate` | Durable Job Engine & Core Lifecycle Hooks | N/A | M3 Gate PASS; PostgreSQL `SKIP LOCKED`/lease/retry/stale recovery/advisory-lock dedup, bounded handler registry/scheduler, supported/unsupported document intent, delete-race no-resurrection, membership `+P7D` retention/rejoin cancellation; targeted 27/27 and full `mvn -B -ntp clean verify` 268/268; no migration/API/provider/RAG/purge implementation; M4 READY. |
| 2026-09-22 | `KBase backend harness - AI v1 M4 Gate` | Gemini Provider Adapters | N/A | M4 Gate PASS; explicit disabled-by-default Gemini/Spring AI configuration, deterministic chat/embedding mapping, strict 768 validation, safe provider categories, request-timeout wiring and privacy/logging tests; targeted 22/22 and full `mvn -B -ntp clean verify` 290/290; no real Gemini credential/network, no M5+ implementation; active M5 READY. |
| 2026-09-23 | `KBase backend harness - AI v1 M5 Gate` | Content Extraction / Chunking / Document Indexing | N/A | M5 Gate PASS; exactly seven supported formats, Tika/source-location evidence, deterministic `kbase-lex-v1`/`chunk-v1`, StorageService-only bounded/hash-checked source reads, durable staging/atomic activation, bounded safe retry/failure, internal status/manual retry and PostgreSQL delete/stale-lease races; targeted 17/17 and full `mvn -B -ntp clean verify` 307/307; M6 retrieval/grounding/citations READY planning-only. |
| 2026-09-23 | `KBase backend harness - AI v1 M6 Gate` | Semantic Retrieval / Grounding / Citations | N/A | M6 Gate PASS; real pgvector cross-project/active READY/current-document retrieval, deterministic bounded selection, NO_EVIDENCE zero chat, prompt injection isolation, exact labels, citation snapshot deletion and in-flight membership revoke; targeted 33/33 and full `mvn -B -ntp clean verify` 329/329; M7 conversation/API READY only. |

## Nhật ký Đơn giản hóa

| Ngày | Thành phần Đã xóa | Kết quả | Quyết định |
|------|-------------------|---------|------------|
| 2026-09-17 | `Frontend khỏi active implementation scope` | `Giảm context và giữ đúng optional scope của đề` | `Giữ frontend deferred cho phase sau` |
