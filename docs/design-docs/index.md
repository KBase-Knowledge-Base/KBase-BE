# Chỉ mục Tài liệu Thiết kế

Sử dụng chỉ mục này như bản đồ có thể khám phá của lịch sử thiết kế.

## Đã Chấp nhận

- `core-beliefs.md`: niềm tin vận hành agent-first và chuẩn mực dự án lâu bền
- `KBase - Core v1 Entity Analysis & ERD.md`: entity, relationship và domain data model
- `KBase - Core v1 Exception Handling Design.md`: exception hierarchy, ErrorCode, API error contract và logging
- `KBase - Core v1 JPA Entity Mapping Repository Design.md`: JPA mapping, repository query và persistence boundary
- `KBase - Core v1 MinIO Integration Design.md`: private object storage, streaming, range và compensation
- `KBase - Core v1 OpenAPI Swagger Configuration Design.md`: OpenAPI/Swagger contract documentation
- `KBase - Core v1 Physical Database Design.md`: PostgreSQL tables, constraints, indexes và delete strategy
- `KBase - Core v1 REST API Specification.md`: REST endpoints, DTO contract, HTTP/error behavior
- `KBase - Core v1 Service Layer Detailed Design.md`: service responsibility, transaction và external orchestration
- `KBase - Core v1 Spring Boot Application Architecture.md`: feature-first modular monolith và package boundaries
- `KBase - Core v1 Spring Security JWT Design.md`: JWT, refresh session, OTP email verification và authorization model
- `KBase - Core v1 Testing Strategy.md`: unit/integration/security/Testcontainers verification strategy
- `KBase - AI Chatbot RAG Architecture.md`: AI domain boundary, provider ports, indexing/retrieval/Guide architecture
- `KBase - AI Chatbot Persistence and Vector Search Design.md`: pgvector schema, chunks, conversations, durable jobs và retention persistence
- `KBase - AI Chatbot REST API Specification.md`: Project Assistant / indexing / Guide REST contract target
- `KBase - AI Chatbot Testing Strategy.md`: AI-specific security, pgvector, worker, grounding và release verification

## Đề xuất

- `Chưa có tài liệu đề xuất chưa được duyệt; bộ AI v1 phía trên đã được chấp nhận làm baseline implementation.`

## Không còn Dùng

- `Chưa có tài liệu KBase đã bị thay thế.`

## Quy tắc Bảo trì

- Mọi tài liệu thiết kế nên có người sở hữu hoặc kích hoạt cập nhật.
- Xóa các tài liệu lỗi thời hoặc đánh dấu chúng không còn dùng thay vì để chúng trôi dạt.
- Liên kết các kế hoạch thực thi active với các tài liệu thiết kế mà chúng phụ thuộc vào.
- Không sửa business rule chỉ trong harness router; nếu rule thay đổi, cập nhật source document KBase liên quan và Implementation Plan trong cùng thay đổi.
