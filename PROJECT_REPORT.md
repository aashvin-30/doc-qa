# Doc-QA — Project Report

**Date:** 17 November 2026 · **Project folder:** `D:\doc-reader`

Doc-QA is a working **RAG (retrieval-augmented generation) document Q&A assistant**. You start a chat, add one or more files to it, and ask questions. Answers stream back word by word, with numbered citations that point to the exact passages used. Each chat has its own files, and all chats are saved.

It is built with **Spring Boot 3.5 (Java 21)**, **React 18 + TypeScript**, **PostgreSQL 17 + pgvector**, **Google Gemini** (embeddings and answers) and, optionally, **Anthropic Claude** for answers.

---

## 1. Architecture

```
 ┌──────────────────────────┐        ┌──────────────────────────────────────────┐
 │  Browser (React + Vite)  │  REST  │       Spring Boot backend  :8080          │
 │  localhost:5173          │ ─────► │                                          │
 │                          │  SSE   │  DocumentController   ConversationCtrl   │
 │  Sidebar · Chat · ＋ menu │ ◄───── │  ChatController (JSON + streaming SSE)   │
 └──────────────────────────┘        │        │                │                │
                                     │  IngestionService   ChatService          │
                                     │  (extract→chunk→     (retrieve→prompt→   │
                                     │   embed→store)        generate→save)     │
                                     └────┬──────────────┬──────────────┬───────┘
                                          │ JDBC         │ REST         │ SDK / REST
                                          ▼              ▼              ▼
                              ┌───────────────────┐ ┌──────────┐ ┌──────────────────┐
                              │ PostgreSQL 17     │ │  Gemini  │ │ Answer model     │
                              │ + pgvector (HNSW) │ │ embedding│ │ Gemini (default) │
                              │ Docker container  │ │  API     │ │ or Claude        │
                              └───────────────────┘ └──────────┘ └──────────────────┘
```

In development, the Vite dev server forwards `/api` requests to the backend, so the browser only ever talks to `localhost:5173`.

---

## 2. How it works

**Uploading a file (runs in the background)**
1. The browser uploads the file with a `conversationId`, and the file is attached to that chat.
2. The text is extracted: PDFBox for PDF (keeping page numbers), Apache POI for DOCX, and plain UTF-8 for TXT, MD, CSV, JSON, HTML, XML and LOG.
3. The text is split into chunks of about 1,200 characters with 200 characters of overlap, breaking at paragraph, sentence or word boundaries.
4. Gemini `gemini-embedding-001` turns each chunk into a 768-dimension vector (`RETRIEVAL_DOCUMENT`). The vectors are normalized, sent in batches of 50, and retried automatically when Gemini rate-limits.
5. The chunks and vectors are saved to pgvector, and the file's status changes from `PROCESSING` to `READY` (or `FAILED` with a reason).

**Asking a question (streamed)**
1. The question is embedded (`RETRIEVAL_QUERY`).
2. A cosine-similarity search over **only the files attached to this chat** returns the top 6 chunks, dropping any with similarity below 0.35.
3. A prompt is built from the numbered excerpts plus the last 8 turns of the chat. It tells the model to cite with `[n]` and to treat document text as data, not instructions.
4. The answer model streams its reply. The browser receives Server-Sent Events: `conversation`, `sources`, `delta`, `done` and `error`.
5. The question, the answer and its sources are saved, and the cited files are marked as recently used.

---

## 3. Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18.3, TypeScript 5.6, Vite 5.4, react-markdown + remark-gfm |
| Backend | Spring Boot 3.5.7, Java 21 (runs on JDK 25), Spring MVC, JdbcTemplate, Bean Validation, Actuator |
| Database | PostgreSQL 17 + pgvector 0.8.7 (HNSW cosine index), Flyway migrations V1–V3 |
| Text extraction | Apache PDFBox 3.0.5, Apache POI 5.4.1 |
| Embeddings | Google Gemini `gemini-embedding-001` (REST, 768 dimensions) |
| Answers (default) | Google Gemini `gemini-flash-latest`, falling back to `gemini-flash-lite-latest` (REST streaming) |
| Answers (optional) | Anthropic Claude `claude-opus-5-5` via the official Java SDK 3.3.0 (streaming) |
| Runtime | Docker Desktop (database), Maven Wrapper (`mvnw.cmd`), Node 24 |

---

## 4. Features

**Chats**
- **New chat:** each chat starts empty and has its own files.
- **Saved history:** chats are grouped by Today, Yesterday, Previous 7 days and Older. You can search, rename and delete them.
- **Follow-ups:** follow-up questions use the earlier conversation as context.
- **Titles:** a chat's title comes from its first question.

**Files**
- **＋ button in the message box:** upload from your computer, or add a file you've already uploaded.
- **Multi-file upload:** pick several files with Ctrl, or drag and drop. Up to 3 upload at once, each with a progress bar.
- **Validation:** files that are unsupported or over 25 MB are flagged immediately, without uploading.
- **Status chips:** each file shows Uploading %, Indexing, Ready or Failed, and × removes it from the chat.
- **Files tab:** recent files, sorted by when they were last used.

**Answers**
- **Streaming:** text appears as it is written, with a Stop button.
- **Citations:** clickable `[n]` chips open the source excerpt, showing file, page and match score.
- **Formatting and copy:** answers render as Markdown, including tables and lists, and have a Copy button.

**Interface**
- **"Aurora" design:** a dark sidebar and gradient accents.
- **Themes and colors:** Light, Dark or Auto themes, and 5 accent colors (Violet, Ocean, Emerald, Sunset, Rose), all remembered.
- **Mobile:** works on narrow screens, where the sidebar becomes a slide-out menu.

**Reliability**
- **Gemini timeouts and fallback:** a 30-second timeout, automatic fallback to the lighter model, and a busy model is skipped for 5 minutes.
- **Recovery after a crash:** files interrupted by a server restart are marked "please upload again" instead of hanging.
- **One-click launchers:** `start.bat`, `stop.bat` and `stop-all.bat`.

---

## 5. REST API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/documents?conversationId=` | Upload a file (multipart field `file`), optionally attaching it to a chat. Returns 202. |
| GET | `/api/documents` | List files, most recently used first |
| GET | `/api/documents/{id}` | Get one file and its status |
| DELETE | `/api/documents/{id}` | Delete a file and its chunks |
| POST | `/api/conversations` | Create an empty chat |
| GET | `/api/conversations` | List chats |
| GET | `/api/conversations/{id}` | Get a chat with its messages, sources and files |
| PATCH | `/api/conversations/{id}` | Rename a chat with `{title}` |
| DELETE | `/api/conversations/{id}` | Delete a chat (`DELETE /api/conversations` deletes all of them) |
| POST | `/api/conversations/{id}/documents` | Attach an existing file with `{documentId}` |
| DELETE | `/api/conversations/{id}/documents/{docId}` | Remove a file from a chat |
| POST | `/api/chat/stream` | Ask a question with `{question, conversationId?, documentIds?}`. Returns an SSE stream. |
| POST | `/api/chat/ask` | The same request, non-streaming. Returns `{conversationId, answer, sources}`. |
| GET | `/actuator/health` | Health check |

---

## 6. Data model

| Table | Key columns | Purpose |
|---|---|---|
| `documents` | id, filename, size_bytes, status, chunk_count, error_message, last_used_at | Uploaded files and their indexing status |
| `chunks` | document_id, chunk_index, page_number, content, embedding `vector(768)` | Text pieces and their vectors, with an HNSW cosine index |
| `conversations` | id, title, created_at, updated_at | Saved chats |
| `messages` | conversation_id, role, content, sources (JSONB), error | Questions and answers, with their citations |
| `conversation_documents` | conversation_id, document_id, added_at | Which files belong to which chat |

Deleting a chat or a file cascades to the rows that depend on it.

---

## 7. Configuration and running

**Requirements:** Java 21+, Node 18+, Docker Desktop and a free Gemini API key (from aistudio.google.com/apikey).

**Easiest way:** double-click `start.bat`. It checks Java, Node, the API key and Docker, starts Docker and the database, frees ports 8080 and 5173, installs packages, starts both servers and opens the browser. Use `stop.bat` to stop it, or `stop-all.bat` to stop the database too.

**Manual way:**
```powershell
cd D:\doc-reader; docker compose up -d
cd backend;  .\mvnw.cmd spring-boot:run     # window 1
cd frontend; npm run dev                    # window 2  → http://localhost:5173
```

**Main settings** (`backend/src/main/resources/application.yml`, each overridable by an environment variable):

| Setting | Default |
|---|---|
| `ANSWER_PROVIDER` | `gemini` (or `claude`) |
| `GEMINI_API_KEY` / `ANTHROPIC_API_KEY` | from the environment |
| `GEMINI_CHAT_MODEL` / `GEMINI_FALLBACK_MODELS` | `gemini-flash-latest` / `gemini-flash-lite-latest` |
| Retrieval top-k / minimum similarity | 6 / 0.35 |
| Chunk size / overlap | 1,200 / 200 characters |
| Maximum upload size | 25 MB per file |

---

## 8. Testing and verification

| Check | Result |
|---|---|
| Backend unit tests (chunker, chat history, titles) | Pass |
| Frontend type check and production build | Pass |
| Upload → index → streamed answer with a citation | Pass ("24 days of paid vacation [1]") |
| Follow-up question uses chat history | Pass ("those" correctly read as vacation days, 5 days) |
| Each chat answers only from its own files | Pass. A recipe-only chat said it had no information on vacation. |
| 5 files uploaded at the same time | All READY within 4 s. A cross-file question returned a correct table and a total of 265,000 USD. |
| Gemini overloaded (503) | Automatic fallback. Answers dropped from about 32 s to 2–3 s after adding the cool-down. |
| Upload to an unknown chat | Correctly rejected with 404 |
| `start.ps1` one-click start | All 9 steps passed |

Not yet verified: streaming through the frontend proxy in a scripted test (the browser is the main path here), and a visual check of the new UI in a browser.

---

## 9. Problems found and fixed

| Problem | Cause | Fix |
|---|---|---|
| Docker would not start | WSL 2.0.14 was too old | Updated WSL to 3.0.5 |
| WSL update failed (error 1618) | Another Windows installer was running | Retried after it finished |
| `mvnw.cmd` failed | `JAVA_HOME` was not set | Set `JAVA_HOME`, and `start.ps1` now detects it automatically |
| Every upload failed with 404 | The model name was URL-encoded (`models%2F…`) | Put `models/` in the URL path itself |
| Answers failed with "model not found" | `gemini-2.5-flash` is retired for new users | Switched to the `gemini-flash-latest` alias |
| App hung forever on a question | Gemini Flash was overloaded and there was no HTTP timeout | 30 s timeout, fallback model and a 5-minute cool-down |
| Rate limits when uploading many files | Gemini free-tier 429 errors | Retries with backoff (2, 4, 8, 16 s) |
| Files stuck on "Indexing…" after a restart | Indexing runs in memory | Files are marked FAILED on startup with a re-upload message |
| `start.ps1` stopped at the database step | PowerShell 5.1 treats Docker's normal messages as errors | Native commands now run through `cmd` |

---

## 10. Limitations and next steps

**Limitations**
- Scanned PDFs (images only) can't be read, because there's no OCR.
- There are no user accounts. Anyone who can reach the app sees all chats and files.
- The free Gemini tier can be slow or rate-limited at busy times.
- The app runs locally only. There's no production deployment yet, such as Dockerfiles for the backend and frontend.

**Suggested next steps**
1. Hybrid search (pgvector plus PostgreSQL full-text) with re-ranking, for better retrieval.
2. OCR for scanned PDFs, for example with Tesseract.
3. Login and per-user data.
4. Dockerfiles and a single `docker compose up` for the whole app.
5. Automated end-to-end tests that run in CI.

---

*Security note:* API keys are read from environment variables and never stored in the code. The Gemini key was pasted into a chat during setup, so it should be rotated at aistudio.google.com/apikey.
