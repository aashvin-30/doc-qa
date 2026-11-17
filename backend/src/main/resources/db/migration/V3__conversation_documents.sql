-- Files attached to a chat. Each chat answers only from its own files.
CREATE TABLE conversation_documents (
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    document_id     UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, document_id)
);

CREATE INDEX idx_conversation_documents_doc ON conversation_documents (document_id);
