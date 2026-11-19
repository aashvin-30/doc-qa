# FAQ

**Is it free to run?**
Yes, with the free Gemini tier. You only pay if you choose Claude for answers, or if you exceed Gemini's free limits.

**Where are my files stored?**
On your own computer. The text and vectors are in the local PostgreSQL database (Docker volume `docqa-pgdata`). The original file isn't kept after indexing. Excerpts are sent to Gemini (and to Claude, if enabled) to create embeddings and answers.

**Do chats see each other's files?**
No. Each chat searches only the files attached to it. You can attach the same file to several chats.

**Why does it say it can't find something that's in my file?**
The search uses meaning, not exact words, and keeps only the 6 best matches above a 0.35 similarity. Try rephrasing the question more specifically. If that doesn't help, see [Troubleshooting](Troubleshooting.md).

**Can it read scanned PDFs or images?**
Not yet. It needs a text layer. OCR is on the roadmap.

**Gemini or Claude?**
Gemini is the default and free. Claude (`claude-opus-5-5`) can give stronger answers on complex documents. Set `ANSWER_PROVIDER=claude` and `ANTHROPIC_API_KEY` to switch. See [Configuration](Configuration.md).

**Can several people use it?**
It has no logins yet, so everyone who can open the page sees every chat. Run it locally, or behind your own authentication.

**How do I back up my data?**
```bash
docker exec docqa-postgres pg_dump -U docqa docqa > backup.sql
```
