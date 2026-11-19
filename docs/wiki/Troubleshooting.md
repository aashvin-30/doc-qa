# Troubleshooting

## Startup

| Symptom | Fix |
|---|---|
| `JAVA_HOME is not defined` from `mvnw.cmd` | Set it to your JDK folder, for example `setx JAVA_HOME "C:\Program Files\Java\jdk-21"`, then open a new terminal. `start.bat` does this automatically. |
| Docker Desktop won't start, or mentions WSL | Run `wsl --update` in an Administrator terminal, then restart Docker Desktop. |
| WSL update fails with error **1618** | Another installer is running. Wait for it to finish, or restart, then try again. |
| `Port 8080 (or 5173) is used by another program` | Close that program, or run `stop.bat` if it's an old Doc-QA window. |
| The backend can't connect to the database | Run `docker compose up -d` and check `docker ps` for `docqa-postgres` with status `healthy`. |
| `localhost:5173` refuses to connect | The frontend isn't running. Start it with `npm run dev` in `frontend/`. |

## Uploads

| Symptom | Fix |
|---|---|
| The file shows **Failed: No readable text found** | It's probably a scanned PDF (images only). OCR isn't supported yet. |
| The file is stuck on **Indexing…** | Check the backend window for errors. If the backend restarted, the file is marked Failed; upload it again. |
| **429 / rate limit** errors on many uploads | The free Gemini tier limits requests per minute. Doc-QA retries automatically; very large batches may still fail, so upload them again later. |
| **File is too large** | The limit is 25 MB per file. Split the file, or raise `spring.servlet.multipart.max-file-size`. |

## Answers

| Symptom | Fix |
|---|---|
| **API key not valid** | Check `GEMINI_API_KEY`. Create a new key at https://aistudio.google.com/apikey and restart the backend. |
| **Model not found (404)** | A model name was retired. Set `GEMINI_CHAT_MODEL=gemini-flash-latest`. |
| Answers are slow or say the model is busy | Gemini is overloaded. Doc-QA falls back to `gemini-flash-lite-latest` automatically; try again in a minute. |
| The answer says the files don't contain the answer | The answer isn't in the chat's files, or no excerpt matched well enough. Check the file is **Ready** and in **this** chat, or lower `app.retrieval.min-similarity`. |
| It answers from the wrong file | Each chat only uses its own files. Remove the files you don't want with **×**. |

## Resetting everything

```bash
stop-all.bat                # or: docker compose down
docker compose down -v      # ⚠ deletes ALL chats and files
```

## Getting logs

- **Backend:** the "Doc-QA backend" window, or the terminal running `mvnw`.
- **Frontend:** the browser's developer console (F12).
- **Database:** `docker compose logs postgres`.
