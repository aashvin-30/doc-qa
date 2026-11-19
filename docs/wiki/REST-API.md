# REST API

Base URL: `http://localhost:8080`. All bodies are JSON unless noted.

## Documents

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents?conversationId={id}` | Multipart upload with a `file` field. `conversationId` is optional and attaches the file to that chat (404 if the chat doesn't exist). Returns **202** with the document. |
| `GET` | `/api/documents` | All documents, most recently used first |
| `GET` | `/api/documents/{id}` | One document. Poll this until `status` is `READY` or `FAILED`. |
| `DELETE` | `/api/documents/{id}` | Deletes the document, its chunks and its chat links |

A document's `status` is `PROCESSING`, `READY` or `FAILED`. When it's `FAILED`, `errorMessage` says why.

## Conversations

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/conversations` | List chats, newest first, with a message count |
| `POST` | `/api/conversations` | Create an empty chat titled "New chat" |
| `GET` | `/api/conversations/{id}` | A chat with its `messages` (including `sources`) and `documents` |
| `PATCH` | `/api/conversations/{id}` | Rename with `{"title": "…"}` |
| `DELETE` | `/api/conversations/{id}` | Delete one chat |
| `DELETE` | `/api/conversations` | Delete all chats |
| `POST` | `/api/conversations/{id}/documents` | Attach an existing file with `{"documentId": "…"}` |
| `DELETE` | `/api/conversations/{id}/documents/{docId}` | Detach a file (the file isn't deleted) |

## Chat

### `POST /api/chat/stream` (Server-Sent Events)

```json
{ "question": "How many vacation days do I get?", "conversationId": "8b23…" }
```

| Field | Required | Notes |
|---|---|---|
| `question` | yes | Up to 4,000 characters |
| `conversationId` | no | Omit it to start a new saved chat |
| `documentIds` | no | Restricts the search to these files. For a chat with files, the chat's files are used. |

Events, in order:

| Event | Data | When |
|---|---|---|
| `conversation` | `{"id": "…", "title": "…"}` | First, so the client knows which chat this is |
| `sources` | `[Source, …]` | After retrieval |
| `delta` | `{"text": "…"}` | Many times, as the answer streams |
| `done` | `{"stopReason": "…"}` | The answer is complete and saved |
| `error` | `{"message": "…"}` | Something failed. The stream then ends. |

Source object:

```json
{
  "index": 1,
  "documentId": "3d1303ad-…",
  "filename": "handbook.pdf",
  "pageNumber": 4,
  "chunkIndex": 7,
  "snippet": "Full-time employees receive 24 days…",
  "similarity": 0.68
}
```

`pageNumber` is `null` for files without pages, such as DOCX or text.

### `POST /api/chat/ask` (non-streaming)

Takes the same body, plus an optional `history` array of `{role, content}` (up to 20 entries) for stateless calls without a `conversationId`. It returns:

```json
{ "conversationId": "…", "answer": "You get 24 days [1].", "sources": [ … ] }
```

## Errors

Errors use the standard Problem Details format (`application/problem+json`):

| Status | When |
|---|---|
| 400 | Validation failed, such as an empty question or an unsupported file type |
| 404 | Unknown document or chat |
| 413 | File larger than 25 MB |

## Examples

```bash
# Create a chat and upload a file to it
CID=$(curl -s -X POST localhost:8080/api/conversations | jq -r .id)
curl -F file=@handbook.pdf "localhost:8080/api/documents?conversationId=$CID"

# Stream an answer
curl -N -X POST localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d "{\"question\":\"Summarize the leave policy\",\"conversationId\":\"$CID\"}"
```
