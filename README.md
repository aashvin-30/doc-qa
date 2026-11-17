# Doc-QA — RAG Document Q&A Assistant

Upload documents, then ask questions about them. Answers stream back with numbered citations that point to the exact excerpts used. Each chat has its own files: start a new chat, add files with the ＋ button in the message box (or drag them in), and that chat answers only from those files. Chats are saved, so you can reopen, rename or delete them later.

| Layer | Tech |
|---|---|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3.5 (Java 21), REST + Server-Sent Events |
| Database | PostgreSQL 17 + pgvector (HNSW cosine index), Flyway migrations |
| Embeddings | Google Gemini `gemini-embedding-001` (768 dims) via REST |
| Answers | Choose with `ANSWER_PROVIDER`: **Gemini** `gemini-flash-latest` via REST (the default, free tier) or **Claude** `claude-opus-5-5` via the official Java SDK. Both stream. |

## How it works

```
Upload ─► extract text (PDFBox / POI) ─► chunk (1200 chars, 200 overlap)
       ─► Gemini embeddings (RETRIEVAL_DOCUMENT) ─► pgvector `chunks` table

Question ─► Gemini embedding (RETRIEVAL_QUERY) ─► top-k cosine search in pgvector
         ─► Claude, given the numbered excerpts ─► streamed answer with [n] citations
```

Ingestion runs in the background. A document shows as `PROCESSING` until its status becomes `READY` or `FAILED`.

## Prerequisites

- Java 21+ (Maven isn't required because the project includes the `mvnw` wrapper)
- Node 18+
- PostgreSQL with pgvector. Docker is the easiest way to get it.
- `JAVA_HOME` pointing at your JDK. `mvnw.cmd` needs it.
- `GEMINI_API_KEY`, free from https://aistudio.google.com/apikey. That's all you need by default.
- Optional: `ANTHROPIC_API_KEY` from https://console.anthropic.com, to have Claude write the answers (`ANSWER_PROVIDER=claude`).

## Run it

```bash
# 1. Database
docker compose up -d

# 2. Backend  (http://localhost:8080)
cd backend
# PowerShell:  $env:GEMINI_API_KEY="AIza..."
export GEMINI_API_KEY=AIza...
# optional, to use Claude for answers:
# export ANSWER_PROVIDER=claude ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

In development, Vite proxies `/api` to `localhost:8080`. For a separate deployment, set `VITE_API_BASE` on the frontend and `CORS_ORIGINS` on the backend.

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents?conversationId=` | Multipart upload with a `file` field. With `conversationId`, the file is attached to that chat. Returns `202` and the document (status `PROCESSING`). |
| `GET` | `/api/documents` | Lists documents, most recently used or uploaded first (`lastUsedAt`). |
| `GET` | `/api/documents/{id}` | Gets one document and its status. |
| `DELETE` | `/api/documents/{id}` | Deletes a document and its chunks. |
| `POST` | `/api/chat/stream` | Takes `{question, conversationId?, documentIds?}`. Omit `conversationId` to start a new saved chat. Returns an SSE stream with the events `conversation`, `sources`, `delta`, `done` and `error`. |
| `POST` | `/api/chat/ask` | Same body, non-streaming. Returns `{conversationId, answer, sources}`. |
| `GET` | `/api/conversations` | Lists saved chats, newest first. |
| `POST` | `/api/conversations` | Creates an empty chat. |
| `GET` | `/api/conversations/{id}` | Gets one chat with its messages, sources and attached `documents`. |
| `POST` | `/api/conversations/{id}/documents` | Attaches an already-uploaded file with `{documentId}`. |
| `DELETE` | `/api/conversations/{id}/documents/{docId}` | Removes a file from the chat. The file stays in your library. |
| `PATCH` | `/api/conversations/{id}` | Renames a chat with `{title}`. |
| `DELETE` | `/api/conversations/{id}` | Deletes one chat. `DELETE /api/conversations` deletes all of them. |
| `GET` | `/actuator/health` | Health check. |

```bash
curl -F file=@handbook.pdf http://localhost:8080/api/documents
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the vacation policy?"}'
```

## Configuration

These live in `backend/src/main/resources/application.yml`, and each one can be overridden with an environment variable:

| Key | Default | Notes |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `localhost:5432/docqa`, `docqa`/`docqa` | |
| `ANSWER_PROVIDER` | `gemini` | `gemini` or `claude`. Embeddings always use Gemini. |
| `GEMINI_CHAT_MODEL` | `gemini-flash-latest` | The Gemini model that writes answers. |
| `GEMINI_FALLBACK_MODELS` | `gemini-flash-lite-latest` | Tried when the main model is busy (429/5xx) or slow. A failing model is skipped for 5 minutes. |
| `app.claude.model` | `claude-opus-5-5` | |
| `app.claude.effort` | `medium` | `low` gives faster and cheaper answers. `high` gives more thorough ones. |
| `app.claude.fallbacks-enabled` | `true` | Turns on the server-side refusal fallback (beta `server-side-fallback-2026-07-01`). |
| `app.retrieval.top-k` | `6` | Number of excerpts sent to Claude. |
| `app.retrieval.min-similarity` | `0.35` | Drops weak matches. |
| `app.gemini.dimensions` | `768` | If you change this, also change `vector(768)` in the Flyway migration. |
| `app.chunking.chunk-size` / `chunk-overlap` | `1200` / `200` | Measured in characters. |

## Project layout

```
backend/src/main/java/com/docqa
  config/      AppProperties, CORS + thread pools, Claude client
  document/    upload/list/delete REST + repository
  ingest/      TextExtractor (PDF/DOCX/text), Chunker, async IngestionService
  embedding/   GeminiEmbeddingClient (REST)
  retrieval/   ChunkRepository (pgvector insert + similarity search)
  chat/        ChatService (RAG prompt + Claude streaming), ChatController (JSON + SSE)
frontend/src
  api.ts       REST + SSE client
  components/  DocumentSidebar, ChatPanel, MessageBubble
```

## Ideas for next steps

- Hybrid search (pgvector plus Postgres full-text) with re-ranking
- OCR for scanned PDFs
- Saved conversations and authentication
- A Dockerfile for the backend and frontend
