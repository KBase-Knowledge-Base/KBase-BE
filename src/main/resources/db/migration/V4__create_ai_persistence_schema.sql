-- KBase AI v1 - pgvector persistence schema (M2)
-- Source of truth: docs/design-docs/KBase - AI Chatbot Persistence and Vector Search Design.md
-- Core V1-V3 remain immutable. This migration is additive and forward-only.

CREATE EXTENSION IF NOT EXISTS vector;

-- One durable AI lifecycle row per Core document. The composite foreign key
-- preserves the document/project boundary in addition to the document PK.
CREATE TABLE document_ai_indexes (
    document_id          UUID PRIMARY KEY,
    project_id           UUID NOT NULL,

    status               VARCHAR(20) NOT NULL,
    failure_reason       VARCHAR(120) NULL,
    source_hash          VARCHAR(128) NULL,
    active_version       BIGINT NULL,
    desired_version      BIGINT NOT NULL,
    chunking_version     VARCHAR(100) NOT NULL,
    embedding_model      VARCHAR(150) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    attempt_count        INTEGER NOT NULL DEFAULT 0,
    last_error_code      VARCHAR(120) NULL,
    indexed_at           TIMESTAMPTZ NULL,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_ai_indexes_document_same_project
        FOREIGN KEY (document_id, project_id)
        REFERENCES documents(id, project_id)
        ON DELETE CASCADE,

    CONSTRAINT ck_document_ai_indexes_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'UNSUPPORTED')),

    CONSTRAINT ck_document_ai_indexes_source_hash
        CHECK (source_hash IS NULL OR BTRIM(source_hash) <> ''),

    CONSTRAINT ck_document_ai_indexes_active_version
        CHECK (active_version IS NULL OR active_version > 0),

    CONSTRAINT ck_document_ai_indexes_desired_version
        CHECK (desired_version > 0),

    CONSTRAINT ck_document_ai_indexes_chunking_version
        CHECK (BTRIM(chunking_version) <> ''),

    CONSTRAINT ck_document_ai_indexes_embedding_model
        CHECK (BTRIM(embedding_model) <> ''),

    CONSTRAINT ck_document_ai_indexes_embedding_dimensions
        CHECK (embedding_dimensions = 768),

    CONSTRAINT ck_document_ai_indexes_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_document_ai_indexes_last_error_code
        CHECK (last_error_code IS NULL OR BTRIM(last_error_code) <> '')
);

-- Vector-heavy rows are intentionally owned by the KBase JDBC repository. The
-- table remains fully constrained by Flyway, while Hibernate need not map the
-- vendor-specific vector type.
CREATE TABLE document_ai_chunks (
    id                   UUID PRIMARY KEY,
    project_id           UUID NOT NULL,
    document_id          UUID NOT NULL,
    index_version        BIGINT NOT NULL,
    chunk_index          INTEGER NOT NULL,
    content              TEXT NOT NULL,
    page_number          INTEGER NULL,
    slide_number         INTEGER NULL,
    section_title        VARCHAR(255) NULL,
    token_count          INTEGER NULL,
    content_hash         VARCHAR(128) NOT NULL,
    embedding            vector(768) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_document_ai_chunks_document_version_index
        UNIQUE (document_id, index_version, chunk_index),

    CONSTRAINT fk_document_ai_chunks_document_same_project
        FOREIGN KEY (document_id, project_id)
        REFERENCES documents(id, project_id)
        ON DELETE CASCADE,

    CONSTRAINT ck_document_ai_chunks_index_version
        CHECK (index_version > 0),

    CONSTRAINT ck_document_ai_chunks_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT ck_document_ai_chunks_content
        CHECK (BTRIM(content) <> ''),

    CONSTRAINT ck_document_ai_chunks_page_number
        CHECK (page_number IS NULL OR page_number > 0),

    CONSTRAINT ck_document_ai_chunks_slide_number
        CHECK (slide_number IS NULL OR slide_number > 0),

    CONSTRAINT ck_document_ai_chunks_token_count
        CHECK (token_count IS NULL OR token_count >= 0),

    CONSTRAINT ck_document_ai_chunks_content_hash
        CHECK (BTRIM(content_hash) <> '')
);

-- Conversations deliberately reference users/projects rather than project_members:
-- membership loss revokes access but retained rows must survive the seven-day
-- retention window defined by the product design.
CREATE TABLE ai_conversations (
    id                   UUID PRIMARY KEY,
    project_id           UUID NOT NULL,
    created_by_user_id   UUID NOT NULL,
    title                VARCHAR(100) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_conversations_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_conversations_creator
        FOREIGN KEY (created_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_ai_conversations_title
        CHECK (title = BTRIM(title) AND title <> '')
);

CREATE TABLE ai_messages (
    id                   UUID PRIMARY KEY,
    conversation_id      UUID NOT NULL,
    role                 VARCHAR(20) NOT NULL,
    content              TEXT NULL,
    generation_status    VARCHAR(20) NOT NULL,
    answer_type          VARCHAR(20) NULL,
    model                VARCHAR(150) NULL,
    failure_code         VARCHAR(120) NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMPTZ NULL,

    CONSTRAINT fk_ai_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_ai_messages_role
        CHECK (role IN ('USER', 'ASSISTANT')),

    CONSTRAINT ck_ai_messages_generation_status
        CHECK (generation_status IN ('PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT ck_ai_messages_answer_type
        CHECK (answer_type IS NULL OR answer_type IN ('GROUNDED', 'NO_EVIDENCE')),

    CONSTRAINT ck_ai_messages_user_lifecycle
        CHECK (role <> 'USER' OR (generation_status = 'COMPLETED' AND content IS NOT NULL)),

    CONSTRAINT ck_ai_messages_answer_type_scope
        CHECK (answer_type IS NULL OR (role = 'ASSISTANT' AND generation_status = 'COMPLETED')),

    CONSTRAINT ck_ai_messages_processing_lifecycle
        CHECK (generation_status <> 'PROCESSING'
            OR (role = 'ASSISTANT' AND completed_at IS NULL)),

    CONSTRAINT ck_ai_messages_failure_code
        CHECK (failure_code IS NULL OR BTRIM(failure_code) <> '')
);

-- A citation keeps historical snapshot data even when its live source is
-- deleted. The live references are nullable and intentionally SET NULL.
CREATE TABLE ai_message_sources (
    assistant_message_id       UUID NOT NULL,
    source_order               INTEGER NOT NULL,
    document_id                UUID NULL,
    chunk_id                   UUID NULL,
    document_id_snapshot       UUID NOT NULL,
    document_name_snapshot     VARCHAR(255) NOT NULL,
    page_number_snapshot       INTEGER NULL,
    slide_number_snapshot      INTEGER NULL,
    section_title_snapshot     VARCHAR(255) NULL,
    retrieval_score            DOUBLE PRECISION NULL,

    CONSTRAINT pk_ai_message_sources
        PRIMARY KEY (assistant_message_id, source_order),

    CONSTRAINT fk_ai_message_sources_assistant_message
        FOREIGN KEY (assistant_message_id)
        REFERENCES ai_messages(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_message_sources_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_ai_message_sources_chunk
        FOREIGN KEY (chunk_id)
        REFERENCES document_ai_chunks(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_ai_message_sources_source_order
        CHECK (source_order >= 0),

    CONSTRAINT ck_ai_message_sources_document_snapshot
        CHECK (document_name_snapshot = BTRIM(document_name_snapshot)
            AND document_name_snapshot <> ''),

    CONSTRAINT ck_ai_message_sources_page_snapshot
        CHECK (page_number_snapshot IS NULL OR page_number_snapshot > 0),

    CONSTRAINT ck_ai_message_sources_slide_snapshot
        CHECK (slide_number_snapshot IS NULL OR slide_number_snapshot > 0)
);

-- Durable job state only. Claiming, leasing and execution belong to M3.
CREATE TABLE ai_jobs (
    id                   UUID PRIMARY KEY,
    job_type             VARCHAR(30) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    project_id           UUID NULL,
    document_id          UUID NULL,
    user_id              UUID NULL,
    dedup_key            VARCHAR(512) NOT NULL,
    payload              JSONB NULL,
    run_at               TIMESTAMPTZ NOT NULL,
    attempt_count        INTEGER NOT NULL DEFAULT 0,
    max_attempts         INTEGER NOT NULL DEFAULT 3,
    lease_until          TIMESTAMPTZ NULL,
    locked_by            VARCHAR(255) NULL,
    last_error_code      VARCHAR(120) NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMPTZ NULL,

    CONSTRAINT fk_ai_jobs_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_jobs_document_same_project
        FOREIGN KEY (document_id, project_id)
        REFERENCES documents(id, project_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_jobs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_ai_jobs_type
        CHECK (job_type IN (
            'DOCUMENT_INDEX',
            'DOCUMENT_REINDEX',
            'CONVERSATION_PURGE',
            'GUIDE_REINDEX'
        )),

    CONSTRAINT ck_ai_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'DONE', 'FAILED', 'CANCELLED')),

    CONSTRAINT ck_ai_jobs_document_project_scope
        CHECK (document_id IS NULL OR project_id IS NOT NULL),

    CONSTRAINT ck_ai_jobs_dedup_key
        CHECK (BTRIM(dedup_key) <> ''),

    CONSTRAINT ck_ai_jobs_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_ai_jobs_max_attempts
        CHECK (max_attempts > 0),

    CONSTRAINT ck_ai_jobs_last_error_code
        CHECK (last_error_code IS NULL OR BTRIM(last_error_code) <> '')
);

CREATE TABLE ai_guide_sources (
    id                   UUID PRIMARY KEY,
    source_key           VARCHAR(1024) NOT NULL,
    content_hash         VARCHAR(128) NOT NULL,
    active_version       BIGINT NULL,
    desired_version      BIGINT NOT NULL,
    status               VARCHAR(20) NOT NULL,
    indexed_at           TIMESTAMPTZ NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ai_guide_sources_source_key
        UNIQUE (source_key),

    CONSTRAINT ck_ai_guide_sources_source_key
        CHECK (BTRIM(source_key) <> ''),

    CONSTRAINT ck_ai_guide_sources_content_hash
        CHECK (BTRIM(content_hash) <> ''),

    CONSTRAINT ck_ai_guide_sources_active_version
        CHECK (active_version IS NULL OR active_version > 0),

    CONSTRAINT ck_ai_guide_sources_desired_version
        CHECK (desired_version > 0),

    CONSTRAINT ck_ai_guide_sources_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE TABLE ai_guide_chunks (
    id                   UUID PRIMARY KEY,
    guide_source_id      UUID NOT NULL,
    index_version        BIGINT NOT NULL,
    chunk_index          INTEGER NOT NULL,
    content              TEXT NOT NULL,
    heading_path         VARCHAR(500) NULL,
    token_count          INTEGER NULL,
    content_hash         VARCHAR(128) NOT NULL,
    embedding            vector(768) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ai_guide_chunks_source_version_index
        UNIQUE (guide_source_id, index_version, chunk_index),

    CONSTRAINT fk_ai_guide_chunks_source
        FOREIGN KEY (guide_source_id)
        REFERENCES ai_guide_sources(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_ai_guide_chunks_index_version
        CHECK (index_version > 0),

    CONSTRAINT ck_ai_guide_chunks_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT ck_ai_guide_chunks_content
        CHECK (BTRIM(content) <> ''),

    CONSTRAINT ck_ai_guide_chunks_token_count
        CHECK (token_count IS NULL OR token_count >= 0),

    CONSTRAINT ck_ai_guide_chunks_content_hash
        CHECK (BTRIM(content_hash) <> '')
);

-- Relational indexes follow the planned query paths; primary/unique indexes
-- above are not repeated here.
CREATE INDEX idx_document_ai_indexes_project_status
    ON document_ai_indexes(project_id, status);

CREATE INDEX idx_document_ai_indexes_status
    ON document_ai_indexes(status);

CREATE INDEX idx_document_ai_chunks_project_id
    ON document_ai_chunks(project_id);

CREATE INDEX idx_document_ai_chunks_document_id
    ON document_ai_chunks(document_id);

CREATE INDEX idx_document_ai_chunks_document_version
    ON document_ai_chunks(document_id, index_version);

CREATE INDEX idx_ai_conversations_project_user_updated_at
    ON ai_conversations(project_id, created_by_user_id, updated_at DESC);

CREATE INDEX idx_ai_messages_conversation_created_at
    ON ai_messages(conversation_id, created_at, id);

CREATE INDEX idx_ai_message_sources_document_id
    ON ai_message_sources(document_id);

CREATE INDEX idx_ai_message_sources_chunk_id
    ON ai_message_sources(chunk_id);

CREATE INDEX idx_ai_jobs_status_run_at
    ON ai_jobs(status, run_at);

CREATE INDEX idx_ai_jobs_project_id
    ON ai_jobs(project_id);

CREATE INDEX idx_ai_jobs_document_id
    ON ai_jobs(document_id);

CREATE INDEX idx_ai_jobs_user_id
    ON ai_jobs(user_id);

CREATE INDEX idx_ai_jobs_dedup_key
    ON ai_jobs(dedup_key);

CREATE INDEX idx_ai_guide_sources_status
    ON ai_guide_sources(status);

CREATE INDEX idx_ai_guide_chunks_source_version
    ON ai_guide_chunks(guide_source_id, index_version);

-- Database backstops for concurrent generation and vector ANN lookup.
CREATE UNIQUE INDEX uq_ai_messages_active_generation
    ON ai_messages(conversation_id)
    WHERE role = 'ASSISTANT'
      AND generation_status = 'PROCESSING';

CREATE INDEX idx_document_ai_chunks_embedding_hnsw
    ON document_ai_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_ai_guide_chunks_embedding_hnsw
    ON ai_guide_chunks
    USING hnsw (embedding vector_cosine_ops);
