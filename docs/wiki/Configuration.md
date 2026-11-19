# Configuration

All settings live in `backend/src/main/resources/application.yml`. Each can be overridden by an environment variable (shown in the table) or by a Spring property, such as `--app.retrieval.top-k=8`.

## Keys and providers

| Env variable | Default | Description |
|---|---|---|
| `GEMINI_API_KEY` | (none, required) | Google AI Studio key. Used for embeddings, and for answers when the provider is Gemini. |
| `ANSWER_PROVIDER` | `gemini` | `gemini` or `claude` |
| `ANTHROPIC_API_KEY` | (none) | Required only when `ANSWER_PROVIDER=claude` |

## Gemini

| Setting | Default | Description |
|---|---|---|
| `GEMINI_CHAT_MODEL` | `gemini-flash-latest` | The model that writes answers |
| `GEMINI_FALLBACK_MODELS` | `gemini-flash-lite-latest` | Comma-separated models to try when the main one fails |
| `app.gemini.response-timeout-seconds` | `30` | Maximum wait for a model to respond |
| `app.gemini.max-output-tokens` | `8192` | Maximum answer length |
| `app.gemini.dimensions` | `768` | Vector size. If you change it, also change `vector(768)` in the migration. |

## Claude

| Setting | Default | Description |
|---|---|---|
| `app.claude.model` | `claude-opus-5-5` | Claude model |
| `app.claude.effort` | `medium` | `low` is faster and cheaper; `high` is more thorough |
| `app.claude.fallbacks-enabled` | `true` | Server-side fallback when a request is refused |

## Retrieval and chunking

| Setting | Default | Description |
|---|---|---|
| `app.retrieval.top-k` | `6` | Number of excerpts sent to the model |
| `app.retrieval.min-similarity` | `0.35` | Drops matches with a lower cosine similarity |
| `app.chunking.chunk-size` | `1200` | Chunk size, in characters |
| `app.chunking.chunk-overlap` | `200` | Overlap between neighboring chunks |

## Database and web

| Env variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/docqa` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `docqa` / `docqa` | Credentials (match `docker-compose.yml`) |
| `CORS_ORIGINS` | `http://localhost:5173` | Allowed origins when the frontend is hosted separately |
| `VITE_API_BASE` (frontend) | (empty: uses the dev proxy) | Backend URL for a separate frontend deployment |
| Upload limit | 25 MB per file | `spring.servlet.multipart.max-file-size` |

## Setting variables permanently on Windows

```powershell
setx GEMINI_API_KEY "your-key"
setx ANSWER_PROVIDER "claude"
```

Open a new terminal afterwards. `start.bat` also reads these from your user environment.

> 🔒 Never put API keys in `application.yml` or commit them to Git.
