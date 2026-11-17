import type { Conversation, ConversationDetail, DocumentInfo, Source } from './types'

const BASE = import.meta.env.VITE_API_BASE ?? ''

async function errorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json()
    return body.detail ?? body.message ?? `Request failed (${res.status})`
  } catch {
    return `Request failed (${res.status})`
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, init)
  if (!res.ok) throw new Error(await errorMessage(res))
  return (res.status === 204 ? undefined : await res.json()) as T
}

// ---------- documents ----------
export const listDocuments = () => request<DocumentInfo[]>('/api/documents')

/**
 * Uploads a file; with conversationId it is attached to that chat.
 * Uses XMLHttpRequest so we can report upload progress (0..1).
 */
export function uploadDocument(
  file: File,
  conversationId?: string,
  onProgress?: (fraction: number) => void,
): Promise<DocumentInfo> {
  const form = new FormData()
  form.append('file', file)
  const qs = conversationId ? `?conversationId=${conversationId}` : ''
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE}/api/documents${qs}`)
    xhr.responseType = 'json'
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress?.(e.loaded / e.total)
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.response as DocumentInfo)
      else {
        const body = xhr.response as { detail?: string; message?: string } | null
        reject(new Error(body?.detail ?? body?.message ?? `Upload failed (${xhr.status})`))
      }
    }
    xhr.onerror = () => reject(new Error('Network error - is the backend running?'))
    xhr.send(form)
  })
}

export const deleteDocument = (id: string) => request<void>(`/api/documents/${id}`, { method: 'DELETE' })

// ---------- saved chats ----------
export const listConversations = () => request<Conversation[]>('/api/conversations')

export const createConversation = () => request<Conversation>('/api/conversations', { method: 'POST' })

export const attachDocument = (conversationId: string, documentId: string) =>
  request<DocumentInfo[]>(`/api/conversations/${conversationId}/documents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ documentId }),
  })

export const detachDocument = (conversationId: string, documentId: string) =>
  request<DocumentInfo[]>(`/api/conversations/${conversationId}/documents/${documentId}`, { method: 'DELETE' })

export const getConversation = (id: string) => request<ConversationDetail>(`/api/conversations/${id}`)

export const renameConversation = (id: string, title: string) =>
  request<Conversation>(`/api/conversations/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })

export const deleteConversation = (id: string) => request<void>(`/api/conversations/${id}`, { method: 'DELETE' })

export const deleteAllConversations = () => request<void>('/api/conversations', { method: 'DELETE' })

// ---------- streaming answers ----------
export interface StreamHandlers {
  onConversation: (c: { id: string; title: string }) => void
  onSources: (sources: Source[]) => void
  onDelta: (text: string) => void
  onDone: (stopReason: string) => void
  onError: (message: string) => void
}

export interface AskPayload {
  question: string
  conversationId?: string
  documentIds?: string[]
}

/** POSTs the question and reads the Server-Sent Events stream (EventSource only supports GET). */
export async function streamAnswer(payload: AskPayload, h: StreamHandlers, signal?: AbortSignal) {
  const res = await fetch(`${BASE}/api/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(payload),
    signal,
  })
  if (!res.ok || !res.body) {
    h.onError(await errorMessage(res))
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const dispatch = (raw: string) => {
    let event = 'message'
    const data: string[] = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
    }
    if (!data.length) return
    const json = JSON.parse(data.join('\n'))
    if (event === 'conversation') h.onConversation(json)
    else if (event === 'sources') h.onSources(json)
    else if (event === 'delta') h.onDelta(json.text)
    else if (event === 'done') h.onDone(json.stopReason)
    else if (event === 'error') h.onError(json.message)
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep: RegExpExecArray | null
    while ((sep = /\r?\n\r?\n/.exec(buffer))) {
      dispatch(buffer.slice(0, sep.index))
      buffer = buffer.slice(sep.index + sep[0].length)
    }
  }
  if (buffer.trim()) dispatch(buffer)
}
