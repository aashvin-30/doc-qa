# Development

## Project structure

```
doc-qa/
├── backend/                         Spring Boot 3.5, Java 21
│   ├── pom.xml
│   └── src/main/java/com/docqa/
│       ├── DocQaApplication.java
│       ├── config/                  AppProperties, CORS, thread pools, HTTP clients
│       ├── common/                  error handling (Problem Details)
│       ├── document/                Document model, repository, service, controller
│       ├── ingest/                  TextExtractor, Chunker, IngestionService, StartupRecovery
│       ├── embedding/               GeminiEmbeddingClient
│       ├── retrieval/               ChunkRepository (pgvector insert and search)
│       ├── conversation/            chats, messages, chat-to-file links
│       └── chat/                    ChatService, ChatController, AnswerGenerator implementations
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/            Flyway V1–V3
├── frontend/                        React 18 + TypeScript + Vite
│   └── src/
│       ├── App.tsx, api.ts, types.ts, files.ts, time.ts
│       ├── components/              Sidebar, ChatPanel, MessageBubble, Appearance
│       └── styles.css
├── docker-compose.yml               PostgreSQL 17 + pgvector
├── start.ps1 / start.bat            one-click start (Windows)
├── stop.ps1 / stop.bat / stop-all.bat
└── PROJECT_REPORT.md
```

## Running the tests

```bash
cd backend
./mvnw test                 # unit tests (Chunker, ChatService)

cd ../frontend
npx tsc --noEmit            # type check
npm run build               # production build into dist/
```

## Common changes

### Add a file type
1. Add the extension to `TextExtractor` in the backend.
2. Add it to `ACCEPT` in `frontend/src/files.ts`.

### Add an answer provider
1. Implement `com.docqa.chat.AnswerGenerator` and stream text through the callback.
2. Annotate the class with `@Service` and `@ConditionalOnProperty(name = "app.answer-provider", havingValue = "myprovider")`.
3. Add its settings to `AppProperties` and `application.yml`.

### Change the embedding size
Change `app.gemini.dimensions`, and add a **new** Flyway migration that changes `chunks.embedding` to `vector(N)` and rebuilds the index. Existing files must be uploaded again.

### Change the database schema
Never edit an applied migration. Add `V4__description.sql` instead.

## Coding notes

- The backend uses plain `JdbcTemplate` (no JPA), so the pgvector SQL stays explicit.
- Long work runs on dedicated executors (`ingestExecutor`, `chatExecutor`), never on request threads.
- API keys come only from the environment.
- The frontend has no global state library. The state lives in `App.tsx` and is passed down as props.
- Colors are CSS variables (`--accent`, `--bg`, and so on) set by `data-theme` and `data-accent` on `<html>`.

## Git workflow

```bash
git checkout -b feature/my-change
# ...edit, test...
git commit -am "Describe the change"
git push -u origin feature/my-change
```

Then open a pull request on GitHub.
