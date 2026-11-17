CREATE TABLE conversations (
    id         UUID PRIMARY KEY,
    title      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            TEXT        NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT        NOT NULL,
    sources         JSONB,
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, id);
CREATE INDEX idx_conversations_updated ON conversations (updated_at DESC);

-- "Recent files": when a document was last used as a source for an answer.
ALTER TABLE documents ADD COLUMN last_used_at TIMESTAMPTZ;
