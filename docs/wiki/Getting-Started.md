# Getting Started

## Requirements

| Tool | Version | Where to get it |
|---|---|---|
| Java (JDK) | 21 or newer | https://adoptium.net |
| Node.js | 18 or newer | https://nodejs.org |
| Docker Desktop | any recent version | https://www.docker.com/products/docker-desktop |
| Gemini API key | free | https://aistudio.google.com/apikey |

Maven isn't needed, because the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`).

## Get the code

```bash
git clone https://github.com/aashvin-30/doc-qa.git
cd doc-qa
```

## Option A: one click (Windows)

Double-click **`start.bat`**, or run it from a terminal. It runs these steps:

1. Finds Java 21+, and sets `JAVA_HOME` if it's missing.
2. Checks for Node.js.
3. Looks for `GEMINI_API_KEY`. If it isn't set, it asks for the key (the input is hidden) and saves it for next time.
4. Starts Docker Desktop if it isn't running.
5. Starts the PostgreSQL + pgvector container and waits until it's healthy.
6. Frees ports 8080 and 5173 if an old Doc-QA process is using them.
7. Runs `npm install` on the first run.
8. Starts the backend in its own window and waits for `/actuator/health`.
9. Starts the frontend in its own window and opens **http://localhost:5173**.

| Script | What it does |
|---|---|
| `stop.bat` | Stops the backend and frontend. The database keeps running. |
| `stop-all.bat` | Also stops the database container. Your data is kept in a Docker volume. |

## Option B: manual (any OS)

```bash
# 1. Database (PostgreSQL 17 + pgvector on port 5432)
docker compose up -d

# 2. Backend on http://localhost:8080
cd backend
export GEMINI_API_KEY=your-key       # PowerShell: $env:GEMINI_API_KEY="your-key"
./mvnw spring-boot:run               # Windows: mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:5173 (in a second terminal)
cd frontend
npm install
npm run dev
```

On the first run, Flyway creates every table automatically.

## Use Claude for answers (optional)

```bash
export ANSWER_PROVIDER=claude
export ANTHROPIC_API_KEY=sk-ant-...
```

Embeddings always use Gemini, so `GEMINI_API_KEY` is still required.

## Check that it works

- http://localhost:8080/actuator/health should return `{"status":"UP"}`.
- Open http://localhost:5173, click **New chat**, add a file, and ask a question.

Next, read the [User Guide](User-Guide.md). If something goes wrong, see [Troubleshooting](Troubleshooting.md).
