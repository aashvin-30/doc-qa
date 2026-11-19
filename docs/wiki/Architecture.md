# Architecture

```mermaid
flowchart LR
    UI["React + Vite<br/>localhost:5173"] -- "REST / SSE<br/>(/api proxied)" --> API["Spring Boot<br/>localhost:8080"]
    API -- JDBC --> DB[("PostgreSQL 17<br/>+ pgvector")]
    API -- REST --> GE["Gemini<br/>embeddings"]
    API -- "REST / SDK" --> LLM["Answer model<br/>Gemini or Claude"]
```

In development, the Vite dev server proxies `/api` to the backend, so the browser only talks to port 5173.

## Ingestion pipeline (asynchronous)

```mermaid
sequenceDiagram
    participant B as Browser
    participant C as DocumentController
    participant I as IngestionService (@Async)
    participant G as Gemini
    participant P as pgvector
    B->>C: POST /api/documents (file, conversationId)
    C->>P: insert document (PROCESSING), attach to chat
    C-->>B: 202 Accepted
    C->>I: ingest(document)
    I->>I: extract text (PDFBox / POI / UTF-8)
    I->>I: chunk (1200 chars, 200 overlap)
    I->>G: batchEmbedContents (50 per batch, RETRIEVAL_DOCUMENT)
    I->>P: insert chunks + vectors
    I->>P: status = READY (or FAILED + reason)
    B->>C: poll GET /api/documents/{id}
```

- **Extraction:** `TextExtractor` uses PDFBox for PDFs (page by page, so each chunk keeps its page number), Apache POI for DOCX, and UTF-8 for plain-text types.
- **Chunking:** `Chunker` makes chunks of about 1,200 characters with 200 characters of overlap. It breaks at a paragraph, sentence or word boundary where it can.
- **Embeddings:** `GeminiEmbeddingClient` calls `gemini-embedding-001` at 768 dimensions and normalizes the vectors. It retries 429 and 5xx errors with backoff (2, 4, 8 and 16 seconds).
- **Thread pool:** ingestion runs on `ingestExecutor` (3 threads, queue of 500).
- **Recovery:** on startup, `StartupRecovery` marks any document still `PROCESSING` as `FAILED` with "please upload again", because in-memory work doesn't survive a restart.

## Question answering

```mermaid
sequenceDiagram
    participant B as Browser
    participant S as ChatService
    participant G as Gemini
    participant P as pgvector
    participant L as Answer model
    B->>S: POST /api/chat/stream {question, conversationId}
    S->>P: load chat, its files, recent history
    S-->>B: event: conversation
    S->>G: embed question (RETRIEVAL_QUERY)
    S->>P: cosine top-6 over this chat's files (similarity >= 0.35)
    S-->>B: event: sources
    S->>L: prompt = rules + numbered excerpts + history + question
    loop streaming
        L-->>S: text
        S-->>B: event: delta
    end
    S->>P: save question, answer, sources; update last_used_at
    S-->>B: event: done
```

### The prompt

The system prompt tells the model to:
- answer **only** from the numbered excerpts,
- cite them inline as `[n]`,
- say so when the excerpts don't contain the answer, and
- treat document text as **data, not instructions**, which guards against prompt injection inside uploaded files.

Up to 8 recent messages of the chat are included, so follow-up questions work.

### Answer providers

`AnswerGenerator` is an interface with two implementations, selected by `app.answer-provider`:

| Provider | Class | Details |
|---|---|---|
| `gemini` (default) | `GeminiAnswerGenerator` | REST `streamGenerateContent?alt=sse` using `gemini-flash-latest`. On 429/5xx or a 30-second timeout, it falls back to `gemini-flash-lite-latest` and skips the failing model for 5 minutes. |
| `claude` | `ClaudeAnswerGenerator` | Official Anthropic Java SDK with `claude-opus-5-5` and streaming. The effort level is configurable, and server-side fallbacks are enabled. |

To add another provider, implement `AnswerGenerator` and register it with `@ConditionalOnProperty(name = "app.answer-provider", havingValue = "yourname")`.

### Streaming to the browser

`ChatController` uses Spring's `SseEmitter` on a separate `chatExecutor`. The frontend reads the stream with `fetch` and a stream parser (not `EventSource`, which can't send a POST). If the browser disconnects partway through, the partial answer is still saved.

## Frontend

| File | Role |
|---|---|
| `App.tsx` | App state: active chat, chat files, pending uploads, theme and accent |
| `api.ts` | REST calls, XHR uploads with progress, SSE parser |
| `components/Sidebar.tsx` | Chats and Files tabs, search, rename and delete |
| `components/ChatPanel.tsx` | Messages, ＋ menu, file chips, drag and drop, message box |
| `components/MessageBubble.tsx` | Markdown rendering, citation chips, sources, Copy |
| `components/Appearance.tsx` | Theme and accent picker |
| `files.ts` | Accepted types, validation, concurrency limiter (3 at a time) |
| `styles.css` | "Aurora" design tokens, light and dark themes, 5 accents |
