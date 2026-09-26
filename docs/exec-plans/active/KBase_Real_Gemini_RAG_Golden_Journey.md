# KBase – Real Gemini RAG Golden Journey

**Status:** ACTIVE – READY TO EXECUTE (2026-09-27)
**Baseline:** `feat-AI` @ `636ea26469823bc475732d1e0f79f147556d1f63` (`Review adapter v3`)
**Type:** Owner-approved real-provider verification / rollout slice
**Not a milestone:** Không phải M12, không phải AI v2, không mở feature mới

## 1. Mục tiêu

Chứng minh implementation AI v1 đã frozen hoạt động end-to-end với **Gemini thật** qua runtime boundary hiện có, không chỉ ở adapter level:

```text
synthetic document upload
→ durable DOCUMENT_INDEX job
→ real Gemini embedding
→ pgvector active index
→ project-scoped retrieval
→ real Gemini grounded chat
→ structured citations
```

Đồng thời verify:

- strict `NO_EVIDENCE` của Project Assistant;
- database-level cross-project isolation trong một live real-embedding journey;
- source lifecycle sau document delete;
- KBase Guide real embedding/retrieval/chat trên exact approved two-spec corpus;
- Guide no-evidence cho câu hỏi ngoài corpus;
- Core runtime vẫn healthy trong suốt journey;
- không có secret/private-data leakage trong logs hoặc tracked files.

Phase này là verification của behavior đã accepted/frozen. Nếu execution phát hiện bug, agent được phép sửa **chỉ khi** fix khôi phục behavior đã được source-of-truth quy định. Nếu cần product decision, public API change, Flyway/schema change hoặc reinterpret frozen behavior thì task phải `BLOCKED` và xin owner decision.

## 2. Source of truth bắt buộc

Đọc trước khi thực thi:

1. `docs/CURRENT_STATE.md`
2. `AGENTS.md`
3. `docs/product-specs/KBase - AI Chatbot v1 Specification.md`
4. `docs/design-docs/KBase - AI Chatbot RAG Architecture.md`
5. `docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md`
6. `docs/design-docs/KBase - AI Chatbot REST API Specification.md`
7. `docs/design-docs/KBase - AI Chatbot Testing Strategy.md`
8. `docs/SECURITY.md`
9. `docs/RELIABILITY.md`
10. `docs/INTEGRATION.md`
11. `docs/DEVELOPMENT.md`
12. completed provider evidence:
   - `docs/exec-plans/completed/KBase_Real_Gemini_Provider_Smoke.md`
   - `docs/exec-plans/completed/KBase_Real_Gemini_Provider_Smoke_Hardening.md`

Product/design docs trên giữ authority. Plan này chỉ sở hữu execution order và verification evidence.

## 3. Entry baseline

Trước live journey phải xác nhận:

- branch `feat-AI` bắt đầu từ baseline plan hoặc descendant không có scope lạ;
- `mvn -B -ntp clean verify` PASS; baseline đã ghi nhận tại entry là **400/400**;
- `docker compose -f docker-compose.yml config --quiet` PASS;
- `git diff --check` PASS;
- OpenAPI invariant: `38 paths / 57 operations / 15 tags`;
- Flyway `V1–V4`, 18 persistent tables, pgvector `vector(768)`;
- real provider adapter smoke/hardening vẫn là PASS evidence;
- `.env` ignored và Gemini key không nằm trong tracked content.

Nếu baseline regression đỏ trước live provider work: STOP, phân loại regression trước, không dùng public Gemini để che lỗi.

## 4. Runtime/provider configuration

Golden Journey sử dụng runtime override đã được smoke xác minh:

```env
KBASE_AI_ENABLED=true
KBASE_AI_PROVIDER_MODE=gemini
KBASE_AI_GEMINI_CHAT_MODEL=gemini-3.5-flash-lite
KBASE_AI_GEMINI_EMBEDDING_MODEL=gemini-embedding-2
KBASE_AI_GEMINI_EMBEDDING_DIMENSIONS=768
```

Giữ retrieval/indexing defaults hiện tại trừ khi source code yêu cầu giá trị explicit:

```text
candidate limit = 10
final context limit = 6
similarity threshold = 0.70
chunk target = 700
chunk overlap = 12%
```

Không đổi canonical/default model trong `application.yml` hoặc `.env.example` trong phase này.

### Worker rule rất quan trọng

Manual adapter smoke trước đây cố ý disable scheduler. **Golden Journey thì ngược lại: background worker phải hoạt động**, vì document indexing và Guide reindex cần durable job execution.

Không truyền:

```text
--kbase.ai.worker.scheduling-enabled=false
```

vào Golden Journey runtime.

`AiWorkerSchedulingConfiguration` production/default behavior (`matchIfMissing=true`) phải được giữ nguyên.

## 5. Isolation và dữ liệu test

### 5.1 Fresh isolated runtime là bắt buộc

Không reuse database/volumes từng chứa deterministic fake embeddings.

Lý do: deterministic vectors và Gemini vectors không cùng vector space; trộn chúng làm live retrieval evidence không hợp lệ.

Chạy bằng một isolated Compose project/volume set riêng, ví dụ:

```text
kbase-real-gemini-rag
```

hoặc cơ chế isolation tương đương đã có trong repo.

Fresh runtime phải chứng minh Flyway V1–V4 apply sạch, Hibernate validate và pgvector hoạt động trước journey.

### 5.2 Chỉ dùng synthetic data

Không upload tài liệu cá nhân, production document, secret, credential, nội dung khách hàng hoặc dữ liệu project thật.

Tạo fixture nhỏ để mỗi document chỉ cần rất ít chunk/provider work.

Fixture Project A gợi ý:

```markdown
# Aurora Deployment Runbook

The rollback code for the Aurora deployment is AURORA-ROLLBACK-741.
If Aurora health checks fail after release, restore the previous application artifact.
```

Fixture Project B cross-project trap gợi ý:

```markdown
# Borealis Private Runbook

The rollback code for the Aurora deployment is BOREALIS-LEAK-999.
This document belongs only to Project Borealis and must never be used by Project Aurora.
```

Project B fixture cố ý có semantic/query overlap cao hơn để cross-project SQL filter được kiểm tra trong live retrieval.

## 6. Provider/quota discipline

- Chạy journey serial, không parallelize live Gemini calls.
- Không loop/retry thủ công vô hạn.
- Để bounded retry hiện có xử lý transient provider failure.
- Không thay threshold/model/retry policy chỉ để làm test xanh.
- Dùng fixture nhỏ, tránh unnecessary reindex.
- Nếu provider trả 429/quota exhaustion sau bounded policy: ghi `BLOCKED_BY_PROVIDER_QUOTA`, chờ/reset quota theo operator workflow; không spam retry.
- Nếu model unavailable/auth invalid/network failure: phân loại riêng (`BLOCKED_BY_MODEL_ACCESS_OR_COMPATIBILITY`, `BLOCKED_BY_CREDENTIAL`, `BLOCKED_BY_ENVIRONMENT`).

## 7. Golden Journey A — Real document indexing

Qua public Core HTTP/API flow hiện có:

1. tạo/chuẩn bị authenticated synthetic user;
2. tạo Project Aurora;
3. upload fixture Aurora bằng supported format nhỏ (`MD` hoặc `TXT`);
4. xác nhận Core upload thành công độc lập;
5. đọc `API-AI-008` để quan sát AI index state;
6. state phải đi theo durable lifecycle hợp lệ và cuối cùng `READY`;
7. verify active index/chunks dùng real Gemini embedding model và dimension 768 bằng safe DB/config evidence; không dump vector;
8. không có raw provider error/credential trong API/log.

Poll status phải bounded (có timeout rõ ràng), không busy-loop vô hạn.

Nếu indexing fail vì provider/quota, không chỉnh business behavior. Phân loại failure và xử lý theo §6.

## 8. Golden Journey B — Cross-project isolation trap

1. tạo Project Borealis bằng cùng user hoặc fixture user phù hợp;
2. upload Borealis trap fixture và đợi `READY`;
3. trong **Project Aurora**, tạo Project Assistant conversation (`API-AI-001`) với câu hỏi:

```text
What is the rollback code for the Aurora deployment?
```

Expected:

- `answerType = GROUNDED`;
- có ít nhất 1 structured source;
- source thuộc Project Aurora document;
- answer/source không chứa `BOREALIS-LEAK-999`;
- không source nào thuộc Project Borealis;
- backend persistence/message-source mapping cũng không chứa Borealis source cho Aurora turn.

Không chấp nhận global vector search rồi filter sau. Nếu cross-project source xuất hiện ở bất kỳ candidate/context/source evidence nào có thể quan sát được thì **BLOCKER**.

## 9. Golden Journey C — Citation integrity

Trên grounded Aurora answer:

- source order deterministic/valid;
- `documentId` (khi available) map đúng Aurora fixture;
- `documentNameSnapshot`/public DTO name đúng fixture;
- `availability = AVAILABLE`;
- page/slide/section metadata chỉ xuất hiện khi thật sự có;
- citation không chứa MinIO public URL, storage key, embedding score/vector hoặc provider internals;
- opening/reading live document vẫn đi qua normal document authorization.

Nếu model output tự invent source label/id, backend mapping phải reject/normalize theo frozen design; không được persist invented citation như trusted source.

## 10. Golden Journey D — Strict NO_EVIDENCE

Trong Aurora conversation gửi câu hỏi không có trong corpus, ví dụ:

```text
What is the cafeteria Wi-Fi password?
```

Expected:

- HTTP/domain outcome thành công theo current contract;
- `answerType = NO_EVIDENCE`;
- `sources = []`;
- deterministic refusal, không general-knowledge hallucination;
- conversation history từ grounded turn trước không được biến thành evidence cho câu hỏi mới.

Live run không bắt buộc chứng minh network invocation count của chat model; deterministic automated tests hiện có vẫn là authority cho rule `no evidence → chat invocation count = 0`. Golden Journey verify public behavior + persisted outcome.

## 11. Golden Journey E — Deleted source lifecycle

Sau khi đã có grounded answer/citation:

1. hard-delete Aurora source document qua normal Core API;
2. verify document không còn retrieval-eligible;
3. đọc historical conversation/message:
   - snapshot vẫn có thể tồn tại theo frozen design;
   - source availability phải phản ánh source không còn available;
4. hỏi lại rollback-code question;
5. vì current Aurora corpus không còn evidence, expected `NO_EVIDENCE` + empty sources;
6. old assistant/history không được resurrect deleted document content làm current evidence.

Không trực tiếp delete AI rows để tạo outcome; dùng normal document lifecycle.

## 12. Golden Journey F — Real KBase Guide

Fresh runtime sẽ reconcile exact two-spec Guide corpus:

```text
docs/product-specs/KBase - Core v1 Specification.md
docs/product-specs/KBase - AI Chatbot v1 Specification.md
```

Wait bounded cho durable Guide reindex hoàn tất. Vì AI v1 không có public Guide-index status endpoint, safe DB/status inspection được phép cho verification.

### Grounded Guide query

Gọi `API-AI-010` với câu hỏi documented rõ ràng, ví dụ:

```text
Project Assistant conversations có được chia sẻ cho các thành viên khác trong project không?
```

Expected:

- `answerType = GROUNDED`;
- sources chỉ thuộc exact approved Guide allowlist;
- không project document/conversation data đi vào Guide response;
- không arbitrary repo docs/execution plan source.

### Guide no-evidence

Gọi Guide với off-topic question, ví dụ:

```text
Thời tiết hôm nay như thế nào?
```

Expected:

- strict no-evidence/refusal;
- không general-purpose answer;
- sources empty;
- không project repository/vector access để cố trả lời.

## 13. Core health check trong live provider phase

Trong hoặc ngay sau AI journey, verify ít nhất một Core read endpoint (ví dụ project list/document metadata) vẫn trả bình thường.

Nếu AI provider transiently fail, Core health không được phụ thuộc provider. Không cố tình phá key/quota trong slice này nếu không cần; M11 deterministic failure-isolation evidence vẫn là baseline authority.

## 14. Security/privacy/log audit

Trong toàn bộ live run:

Không log/persist ngoài designed data:

- Gemini API key;
- Authorization bearer token;
- OTP/refresh token;
- full raw provider response/error body;
- embedding vector;
- storage credential/key;
- synthetic document content trong ordinary operational logs;
- full user prompt/assistant answer trong logs nếu current policy cấm.

Allowed operational evidence chỉ nên gồm safe IDs/status/model/category/count/latency theo existing conventions.

Sau run, scan captured logs/diff cho credential patterns và fixture leak ngoài expected response artifacts.

## 15. Không thuộc scope

Phase này không:

- tạo M12;
- đổi product spec/design behavior;
- đổi canonical/default chat model;
- đổi embedding model/dimension;
- tune similarity threshold vì một fixture khó;
- thêm/rewrite Flyway migration;
- đổi public REST contract;
- thêm streaming/SSE;
- thêm frontend;
- load/stress test;
- quota benchmarking;
- OCR/multimodal/XLSX;
- fix chat PROCESSING recovery debt, lease heartbeat, CI hoặc unrelated Core debt;
- chạy production deployment.

## 16. Bug policy trong phase

Nếu golden journey fail:

1. reproduce + classify;
2. đối chiếu source-of-truth;
3. nếu là implementation bug rõ ràng trong frozen behavior, agent có thể fix tối thiểu và thêm regression test;
4. rerun focused test + affected live journey;
5. rerun full gate;
6. nếu fix cần API/schema/product/model-default decision → STOP/BLOCK, không tự mở scope.

Không sửa bằng cách làm test yếu đi, hạ security/isolation, bỏ NO_EVIDENCE hoặc hard-code fixture answer.

## 17. Verification bắt buộc

### Entry

```bash
mvn -B -ntp clean verify
docker compose -f docker-compose.yml config --quiet
git diff --check
```

### During live runtime

Record tối thiểu:

- isolated Compose/runtime identifier;
- profile/provider/model IDs (không secret);
- Flyway V1–V4 / 18 tables / vector(768);
- document AI state transitions;
- Guide source status;
- HTTP status + safe response assertions cho từng journey;
- safe DB postconditions cho project/source isolation;
- provider failure category nếu có.

### Final

```bash
mvn -B -ntp clean verify
docker compose -f docker-compose.yml config --quiet
git diff --check
```

Nếu code/test không đổi, expected current test baseline là 400. Nếu bug fix thêm tests, record exact count mới; không cố giữ 400.

Re-verify OpenAPI/DB invariants:

```text
OpenAPI = 38 paths / 57 operations / 15 tags
Flyway = V1–V4
persistent tables = 18
vector dimension = 768
```

## 18. Acceptance gate

Phase chỉ PASS khi toàn bộ điều sau đúng:

- real supported document reaches `READY` through real Gemini embedding;
- real Project Assistant grounded turn succeeds;
- structured citations map only to current authorized Project A evidence;
- cross-project Borealis trap never appears in Aurora retrieval/result/persisted source evidence;
- Project Assistant no-evidence returns `NO_EVIDENCE` + empty sources;
- deleted-source follow-up cannot resurrect deleted knowledge;
- Guide real reindex completes on exact two allowlisted specs;
- Guide documented query grounded with allowlisted Guide source;
- Guide off-topic query returns no-evidence/refusal;
- Core endpoint remains healthy;
- secret/log audit clean;
- final Core+AI regression PASS;
- API/schema invariants unchanged unless task was explicitly BLOCKED for owner decision;
- no unresolved HIGH/BLOCKER introduced by this phase.

## 19. Completion/handoff

Nếu PASS:

1. cập nhật plan này với execution log + exact evidence;
2. move plan từ `active/` sang `completed/`;
3. `docs/exec-plans/active/index.md` → `Active implementation plan: NONE`;
4. cập nhật `docs/CURRENT_STATE.md`: Real Gemini RAG Golden Journey PASS + exact models/evidence;
5. cập nhật `docs/QUALITY_SCORE.md`: thay `full live RAG golden journey pending` bằng verified evidence;
6. cập nhật `docs/DEVELOPMENT.md`/`docs/DEPLOYMENT.md` nếu có reproducible manual runbook/provider rollout fact mới;
7. `.harness/source-doc-registry.json` trở lại frozen/no-active-slice wording;
8. `AGENTS.md`/`docs/PLANS.md` trở lại no-active-slice wording;
9. giữ technical debt tracker nguyên vẹn trừ khi phase phát hiện debt mới.

Sau PASS vẫn **không tự** đổi default chat model. Promote `gemini-3.5-flash-lite` thành canonical/default là owner decision riêng.

Nếu BLOCKED:

- giữ plan active;
- ghi exact blocking category/evidence;
- không claim partial PASS thành full RAG PASS.

## 20. Final report format cho coding agent

```text
## Baseline
- HEAD
- entry full gate + exact count
- OpenAPI/DB invariants

## Runtime
- isolated runtime id
- profile/provider/chat/embedding model
- worker enabled
- secret present YES/NO (never print value)

## Document Indexing
- upload result
- state transition
- READY evidence
- real embedding/dimension evidence

## Project Assistant
- grounded result
- sources
- cross-project trap result
- NO_EVIDENCE result
- deleted-source result

## Guide
- source/reindex readiness
- grounded result
- allowlist evidence
- no-evidence result

## Security / Logs
- secret scan
- cross-project persistence check
- Core health check

## Regression
- focused tests/fixes (if any)
- final mvn clean verify + exact count
- compose/diff check
- OpenAPI/Flyway/table/vector invariants

## Remaining scope
- model-default promotion decision
- existing technical debts unchanged/new debt if any

## Final state
READY / BLOCKED with exact reason
```

## 21. Progress log

- `2026-09-27`: Plan created and activated by owner approval. Execution not started yet.
