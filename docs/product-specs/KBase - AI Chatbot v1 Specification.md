# KBase – Knowledge Base
## AI Chatbot v1 Specification

**Version:** Draft 1  
**Scope:** Backend AI v1 on top of frozen Core v1  
**Primary capabilities:** Project Assistant + KBase Guide  
**Frontend:** Out of scope for this phase  
**Streaming:** Out of scope for initial AI v1  
**Human Project Chat:** Future scope, not part of AI v1

---

# 1. Purpose

AI Chatbot v1 mở phase AI/RAG đầu tiên của KBase sau khi Core v1 đã đạt freeze gate.

AI v1 có hai capability độc lập về product behavior:

```text
KBase AI v1
├── Project Assistant
│   └── private AI conversations grounded in one project's documents
└── KBase Guide
    └── product/help assistant grounded in approved KBase documentation
```

AI v1 không biến KBase thành general-purpose chatbot. Mọi câu trả lời factual của hai capability phải được grounding bởi corpus được phê duyệt của capability đó.

Core v1 vẫn là baseline bắt buộc. AI v1 không được phá vỡ authentication, project isolation, document ownership, MinIO lifecycle, PostgreSQL/Flyway source of truth hoặc API/error conventions đã freeze.

---

# 2. AI v1 Scope

| Capability | AI v1 |
|---|---|
| Project-scoped RAG assistant | ✅ |
| Private AI conversations per user | ✅ |
| Max 5 conversations / user / project | ✅ |
| Rename / hard-delete conversation | ✅ |
| Strict no-evidence refusal | ✅ |
| Structured citations | ✅ |
| Visible document AI indexing state | ✅ |
| Automatic retry for transient indexing failure | ✅ |
| Manual retry for failed indexing | ✅ |
| Durable asynchronous indexing | ✅ |
| PostgreSQL + pgvector semantic retrieval | ✅ |
| Gemini as primary AI provider | ✅ |
| Spring AI as primary Gemini integration layer | ✅ |
| KBase Guide | ✅ |
| Guide corpus from curated existing KBase docs | ✅ |
| Text questions | ✅ |
| Configurable AI usage/rate guard | ✅ |
| Streaming/SSE | ❌ Later |
| Human Project Chat | ❌ Later |
| Shared AI conversation | ❌ |
| Edit/delete individual message | ❌ |
| Branch/regenerate answer UX | ❌ |
| General-knowledge fallback | ❌ |
| OCR / scanned-image extraction | ❌ Later |
| Image/video multimodal RAG | ❌ Later |
| XLS/XLSX RAG | ❌ Later |
| Voice input | ❌ Later |
| Hybrid search / reranker | ❌ Later |
| Kafka/RabbitMQ | ❌ Not required for v1 |

---

# 3. Product Identities

## 3.1 Project Assistant

Project Assistant trả lời câu hỏi dựa trên knowledge hiện đang được phép retrieval trong **một project cụ thể**.

Nó không được dùng general knowledge để lấp khoảng trống của project corpus.

```text
current user
   ↓
current project authorization
   ↓
current READY project knowledge
   ↓
grounded answer + citations
```

## 3.2 KBase Guide

KBase Guide giải thích KBase và cách sử dụng các capability đã được tài liệu chính thức xác nhận.

Guide hoạt động ngoài project context, nhưng AI v1 vẫn dùng authentication hiện tại của KBase. "Ngoài project" nghĩa là request không cần một `projectId`, không có nghĩa là public anonymous chatbot. Public Guide có thể được quyết định ở version sau.

Guide không được truy cập project documents, project conversations hoặc private user knowledge.

---

# 4. Project Assistant Conversation Model

## FR-AI-CONV-001 – Private ownership

Mỗi Project Assistant conversation có:

```text
projectId
createdByUserId
```

`projectId` xác định knowledge scope. `createdByUserId` xác định privacy/ownership.

Conversation không phải shared project conversation.

- MEMBER A không xem conversation của MEMBER B.
- OWNER không mặc định xem conversation riêng của MEMBER.
- ADMIN không được list/read conversation của user khác qua Project Assistant API thông thường.
- Existing ADMIN project-access override cho phép ADMIN dùng Project Assistant bằng conversation riêng của chính ADMIN; override không biến ADMIN thành owner của conversation người khác.

## FR-AI-CONV-002 – Fixed project

Conversation được tạo trong project nào thì tồn tại cố định trong project đó.

Không hỗ trợ move conversation giữa project.

## FR-AI-CONV-003 – Maximum 5 conversations

Mỗi user có tối đa:

```text
5 existing Project Assistant conversations / project
```

Quota áp dụng riêng theo từng project.

Nếu quota đã đủ 5, backend từ chối tạo conversation mới bằng stable business error. Backend không tự xóa conversation cũ.

Quota phải an toàn trước concurrent create requests.

## FR-AI-CONV-004 – Create on first message

Nhấn "New chat" ở frontend tương lai không bắt buộc tạo persistent row ngay.

Conversation được tạo khi backend nhận câu hỏi đầu tiên hợp lệ.

Request tạo conversation phải:

```text
authorize project
→ enforce quota
→ create conversation
→ persist first USER message
→ create one generation state
→ run one AI turn
```

Nếu generation lỗi sau khi USER message đã persist, conversation vẫn tồn tại và failure phải có trạng thái có thể quan sát được.

## FR-AI-CONV-005 – Title

Title tối đa 100 ký tự.

Title ban đầu được tạo deterministic từ câu hỏi đầu tiên bằng normalize/truncate. Không gọi thêm LLM chỉ để đặt title.

Creator có thể rename conversation. Rename cập nhật `updatedAt`.

## FR-AI-CONV-006 – List order

Conversation list mặc định sắp xếp:

```text
updatedAt DESC
```

## FR-AI-CONV-007 – Delete

Creator có thể hard-delete toàn bộ conversation.

Delete conversation xóa messages và citation records liên quan theo database lifecycle.

Không có trash/restore trong AI v1.

---

# 5. Membership and Conversation Retention

## FR-AI-RET-001 – Immediate access revocation

Khi USER mất membership trong project do leave hoặc bị remove:

```text
membership lost
→ Project Assistant access revoked immediately
```

User không được list/read/send message vào conversation của project đó trong retention period.

## FR-AI-RET-002 – Seven-day grace period

Private conversations của user trong project được giữ trong:

```text
7 days (7 x 24 hours)
```

tính từ thời điểm membership mất hiệu lực.

## FR-AI-RET-003 – Rejoin restore

Nếu cùng `userId` có active membership trở lại trong cùng `projectId` trước `purgeAfter`:

- pending conversation purge bị hủy;
- conversations cũ trở lại khả dụng;
- role mới không cần giống role cũ;
- quota 5 tiếp tục tính các conversation đã giữ.

## FR-AI-RET-004 – Hard purge

Nếu sau 7 ngày user vẫn không có membership:

```text
conversations
→ messages
→ message sources
```

của user trong project đó bị hard-delete.

Cleanup phải kiểm tra membership lại ngay trước purge để tránh race với rejoin.

## FR-AI-RET-005 – Project deletion exception

Project hard-delete không áp dụng 7-day grace period.

Project deletion hard-deletes toàn bộ AI data thuộc project theo database lifecycle.

---

# 6. Message Behavior

## FR-AI-MSG-001 – Text-only input

AI v1 chỉ nhận text question.

Không hỗ trợ attachment trực tiếp vào chat, image prompt, voice hoặc audio.

Muốn hỏi nội dung một file, user upload file vào KBase document flow trước.

## FR-AI-MSG-002 – Input limit

Backend phải có configurable maximum user-message length.

Implementation baseline là 8,000 Unicode characters nếu M0 compatibility/preflight không chứng minh cần giá trị khác. Giá trị cuối được ghi trong typed configuration và API validation.

## FR-AI-MSG-003 – No message editing

AI v1 không hỗ trợ:

- edit từng message;
- delete từng message;
- branch conversation;
- regenerate/version một assistant message.

User có thể gửi một câu hỏi mới hoặc xóa toàn conversation.

## FR-AI-MSG-004 – One active generation

Một conversation chỉ có tối đa một generation active tại một thời điểm.

Concurrent send thứ hai vào cùng conversation trong lúc generation đang chạy phải bị từ chối bằng stable business error.

Các conversation khác nhau có thể xử lý song song.

## FR-AI-MSG-005 – Persist before provider call

USER message phải được persist trước retrieval/provider call.

Assistant generation có explicit state để phân biệt completed và failed generation.

Provider timeout/failure không được làm mất USER message đã nhận.

## FR-AI-MSG-006 – Authorization during long request

Project access phải được kiểm tra trước retrieval và kiểm tra lại trước khi completed answer/sources được persist và trả về.

Nếu membership bị thu hồi trong lúc embedding/Gemini đang chạy:

- generated content không được trả cho caller;
- generated content/sources không được persist như completed answer;
- current request kết thúc bằng authorization/failure outcome phù hợp;
- future access tiếp tục bị deny theo current membership.

Non-streaming v1 giúp giữ rule này rõ ràng.

---

# 7. Strict Grounding and No-Evidence Behavior

## FR-AI-RAG-001 – Current evidence is mandatory

Mỗi factual Project Assistant answer phải được support bởi current authorized retrieval.

Conversation history giúp hiểu ngữ cảnh hội thoại nhưng **không phải authoritative knowledge source**.

## FR-AI-RAG-002 – No evidence

Retrieval layer quyết định evidence đủ hay không trước generation.

Nếu không có usable evidence:

```text
answerType = NO_EVIDENCE
sources = []
```

Backend trả deterministic refusal, ví dụ:

> Tôi chưa tìm thấy đủ thông tin trong tài liệu hiện có của project để trả lời câu hỏi này.

Không gọi Gemini chỉ để model tự suy diễn general knowledge cho case này.

## FR-AI-RAG-003 – Deleted knowledge cannot be resurrected

Nếu một document từng được dùng trong conversation nhưng sau đó bị xóa:

- document bị loại khỏi retrieval ngay;
- old assistant message vẫn tồn tại;
- source snapshot có thể vẫn hiển thị nhưng phải đánh dấu source không còn khả dụng;
- câu hỏi mới không được dùng old chat history để phục hồi nội dung document đã xóa;
- nếu current corpus không còn evidence thì trả `NO_EVIDENCE`.

---

# 8. Citations

## FR-AI-CITE-001 – Structured sources

Mỗi grounded assistant answer phải có ít nhất một structured source.

Source do backend map từ retrieved chunk metadata; không tin citation text do LLM tự phát minh.

Baseline source metadata:

```text
documentId when source still exists
documentNameSnapshot
chunkId when chunk still exists
pageNumber / slideNumber / sectionTitle when available
rank
retrievalScore
availability
```

## FR-AI-CITE-002 – Authorization on source open

Citation không tạo public/permanent storage URL.

User mở source vẫn phải đi qua current KBase document authorization.

---

# 9. Project Document AI Indexing

## FR-AI-IDX-001 – Supported AI v1 content types

AI v1 indexing hỗ trợ:

```text
PDF
DOC
DOCX
PPT
PPTX
MD
TXT
```

Core upload có thể hỗ trợ nhiều file type hơn; điều đó không đồng nghĩa AI v1 phải index tất cả.

Deferred:

```text
XLS/XLSX
images
video
OCR
multimodal content
```

## FR-AI-IDX-002 – Visible states

Document AI indexing state:

```text
PENDING
PROCESSING
READY
FAILED
UNSUPPORTED
```

Chỉ chunks thuộc active successful index được retrieval.

## FR-AI-IDX-003 – Upload independence

Core document upload success không phụ thuộc Gemini/indexing network success.

```text
Core upload completes
→ durable AI indexing intent exists
→ provider work happens asynchronously
```

Gemini outage không được biến normal upload/download/preview/metadata search thành outage.

## FR-AI-IDX-004 – Failure reason

`FAILED` có safe failure category, ví dụ:

```text
EXTRACTION_FAILED
AI_SERVICE_ERROR
PROCESSING_ERROR
```

Không expose stack trace, provider payload, API key hoặc raw internal error cho client.

Supported file không có extractable text có thể dùng `FAILED + EXTRACTION_FAILED`; không cần thêm state riêng trong v1.

## FR-AI-IDX-005 – Retry

Transient provider/network failures được automatic retry theo bounded policy.

Sau khi bounded automatic retry kết thúc, document có thể ở `FAILED`.

Manual retry chỉ được phép khi document đang `FAILED`, và permission dùng cùng document-modification rule hiện tại:

- MEMBER uploader của document;
- project OWNER;
- ADMIN override.

`UNSUPPORTED`, `PENDING`, `PROCESSING` và `READY` không nhận manual retry v1.

## FR-AI-IDX-006 – Last successful index

Nếu tương lai document content có reindex:

- không xóa active successful index trước khi replacement index thành công;
- atomic switch sang version mới sau success;
- failed replacement không làm mất last-known-good index.

Core v1 hiện chưa có binary replacement; rule này bảo vệ evolution path và không tạo feature replace-file trong AI v1.

---

# 10. Project Retrieval Scope

## FR-AI-SEARCH-001 – Project authorization first

Project Assistant request phải authorize current project access trước retrieval.

## FR-AI-SEARCH-002 – Database-level project isolation

Vector retrieval phải filter `project_id` trong database query.

Không được:

```text
global vector search
→ return candidates
→ filter project in Java
```

Cross-project chunk không được đi vào prompt kể cả khi semantic score cao hơn.

## FR-AI-SEARCH-003 – Corpus

Initial Project Assistant corpus là tất cả supported documents có active successful index trong current project.

AI v1 chưa cho user chọn folder/category/tag/selected documents làm chat scope, nhưng persistence/retrieval design không được tự khóa khả năng filter này trong tương lai.

---

# 11. KBase Guide

## FR-AI-GUIDE-001 – Purpose

Guide chỉ trả lời:

```text
How do I use KBase?
What does this KBase feature do?
What is the documented behavior of KBase?
```

Guide không phải source-code assistant và không phải general-purpose chatbot.

## FR-AI-GUIDE-002 – Approved corpus only

Guide dùng curated allowlist từ existing KBase documentation.

Không scan toàn bộ repository và không ingest mọi file dưới `docs/`.

Initial v1 source set là accepted product specs:

```text
docs/product-specs/KBase - Core v1 Specification.md
docs/product-specs/KBase - AI Chatbot v1 Specification.md
```

Nguồn mới chỉ được thêm bằng reviewed allowlist change. AI v1 không yêu cầu tạo duplicate Guide documentation.

Không ingest:

- execution plans;
- tech-debt tracker;
- internal architecture/security/deployment details;
- generated DB/API snapshots;
- future proposal chưa accepted;
- arbitrary repo files.

## FR-AI-GUIDE-003 – No project data

Guide không được query:

- project documents;
- project vector chunks;
- private Project Assistant conversations;
- project membership-private data.

Nếu user hỏi nội dung Project A, Guide hướng user sang Project Assistant của Project A thay vì tự truy cập.

## FR-AI-GUIDE-004 – Strict grounding

Guide cũng không đoán undocumented KBase behavior.

Nếu approved Guide corpus không có evidence, trả deterministic no-evidence response.

## FR-AI-GUIDE-005 – Lightweight context

Guide v1 không có persistent conversation library, rename hoặc history screen.

Guide request có thể mang bounded prior USER/ASSISTANT turns để hiểu follow-up. Prior turns chỉ là conversational context, không phải authority.

No server-side durable Guide chat archive được tạo trong v1.

---

# 12. Provider Behavior

## FR-AI-PROVIDER-001 – Gemini primary

Gemini API là primary LLM/embedding provider của AI v1.

## FR-AI-PROVIDER-002 – KBase-owned ports

Business/application code không phụ thuộc trực tiếp vào Spring AI model class hoặc Google SDK type.

KBase phải sở hữu provider-neutral ports cho:

```text
chat generation
embedding
```

Spring AI là integration path chính cho Gemini Chat và Embedding trong v1.

Direct Gemini SDK chỉ được dùng cho một capability cụ thể nếu M0 chứng minh Spring AI không đáp ứng hoặc không tương thích; thay đổi đó phải nằm sau KBase adapter và được ghi lại trong active plan.

## FR-AI-PROVIDER-003 – Configurable models

Không hard-code chat model trong application logic.

Embedding baseline:

```text
model: gemini-embedding-2
dimensions: 768
```

Chat model, embedding model, dimensions, timeout và retrieval tuning đi qua typed configuration.

Nếu M0 phát hiện provider/integration không thể đáp ứng baseline này, task phải BLOCKED hoặc ghi design resolution; không silently đổi model/dimensions.

---

# 13. Prompt Safety

Retrieved document/Guide content là untrusted data, không phải system instruction.

Prompt construction phải tách rõ:

```text
system policy
conversation context
retrieved evidence
current user question
```

Instruction nằm trong retrieved content như "ignore previous instructions" không được thay đổi authorization, corpus scope hoặc system policy.

LLM không bao giờ là security boundary.

---

# 14. Usage Guard and Cost Control

AI endpoints phải có configurable per-user usage/rate guard và bounded provider/resource limits.

Tuning gồm:

- requests per time window;
- provider timeout;
- max retrieved context;
- max output size/tokens;
- max user message length.

Exact numeric defaults được khóa tại technical M0/M10 và không phải immutable product contract.

Rate-limit infrastructure failure chỉ được ảnh hưởng AI endpoint; không được làm Core endpoint outage.

---

# 15. API-Level Outcomes

Project Assistant successful turn phải phân biệt ít nhất:

```text
GROUNDED
NO_EVIDENCE
```

Infrastructure/provider failure dùng stable error contract của KBase, không giả làm `NO_EVIDENCE`.

Conversation/indexing business errors phải có stable codes, tối thiểu các trường hợp:

```text
AI_CONVERSATION_LIMIT_REACHED
AI_CONVERSATION_NOT_FOUND
AI_CONVERSATION_ACCESS_FORBIDDEN
AI_REQUEST_IN_PROGRESS
AI_INDEX_NOT_READY
AI_INDEX_RETRY_NOT_ALLOWED
AI_PROVIDER_UNAVAILABLE
AI_RATE_LIMIT_EXCEEDED
```

Tên cuối cùng phải được đồng bộ với `ErrorCode`, OpenAPI và generated API schema khi implementation diễn ra.

---

# 16. Non-Streaming v1

Initial AI v1 response là normal JSON response sau khi generation hoàn tất.

SSE/token streaming không thuộc scope implementation ban đầu.

Không thiết kế persistence quanh partial token stream.

Streaming có thể được bổ sung sau khi frontend tồn tại và RAG/security/persistence đã ổn định.

---

# 17. Privacy and Logging

Không log mặc định:

- full user prompt;
- full assistant answer;
- raw document/chunk content;
- embedding vector;
- Gemini API key;
- provider raw sensitive payload.

Có thể log safe operational metadata:

```text
requestId
userId/projectId theo logging convention hiện tại
model identifier
latency
retrieved chunk count
indexing status/attempt
provider error category
```

Việc gửi retrieved project text tới Gemini là một external data boundary và phải được mô tả trong integration/security docs.

---

# 18. AI v1 Acceptance Criteria

AI v1 chỉ được coi là đạt khi có bằng chứng chạy được cho tối thiểu:

1. Supported document upload vẫn hoàn tất dù AI provider unavailable.
2. Supported document chuyển tới `READY` qua durable async indexing.
3. Project Assistant chỉ retrieve chunks của current authorized project.
4. Cross-project semantically-closer chunk vẫn không được đưa vào prompt/result.
5. Grounded answer có structured citations.
6. No-evidence question trả deterministic `NO_EVIDENCE`, không general-knowledge fallback.
7. Deleted document lập tức không còn retrieval; old citation hiển thị unavailable.
8. Max 5 private conversations/user/project được enforce kể cả concurrent create.
9. Conversation chỉ creator đọc được; OWNER/other MEMBER/ADMIN không đọc chat của người khác qua normal AI API.
10. Membership loss revoke ngay; rejoin trong 7 ngày restore; quá 7 ngày hard purge.
11. Membership bị revoke giữa request không được nhận/persist completed answer.
12. Project delete hard-deletes project AI data ngay.
13. Provider timeout/failure không làm Core KBase outage.
14. Prompt injection trong document không phá project isolation/system policy.
15. KBase Guide chỉ retrieve approved Guide corpus và không chạm project corpus.
16. Automated suite không cần real Gemini credential.
17. pgvector behavior được verify trên PostgreSQL/pgvector Testcontainer thực.
18. Full Core regression vẫn pass.

---

# 19. Explicit Out of Scope / Future Direction

AI v1 không triển khai:

- human-to-human Project Chat;
- shared AI conversation;
- AI mention trong Project Chat;
- streaming UI;
- frontend;
- OCR/multimodal;
- spreadsheet RAG;
- reranking/hybrid search;
- multi-provider routing;
- per-user configurable retention;
- AI billing dashboard;
- persistent Guide conversation history.

Human Project Chat là product direction có thể được xem xét trong Core/AI version sau, nhưng không được dùng để mở rộng AI v1 scope.
