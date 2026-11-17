CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id            UUID PRIMARY KEY,
    filename      TEXT        NOT NULL,
    content_type  TEXT,
    size_bytes    BIGINT      NOT NULL,
    status        TEXT        NOT NULL,          -- PROCESSING | READY | FAILED
    chunk_count   INT         NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INT         NOT NULL,
    page_number INT,
    content     TEXT        NOT NULL,
    embedding   vector(768) NOT NULL
);

CREATE INDEX idx_chunks_document ON chunks (document_id);
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
