export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED'

export interface DocumentInfo {
  id: string
  filename: string
  contentType: string | null
  sizeBytes: number
  status: DocumentStatus
  chunkCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  lastUsedAt: string | null
}

export interface Source {
  index: number
  documentId: string
  filename: string
  pageNumber: number | null
  chunkIndex: number
  snippet: string
  similarity: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
  streaming?: boolean
  error?: string
}

export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messageCount: number
}

export interface StoredMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources: Source[]
  error: string | null
  createdAt: string
}

export interface ConversationDetail {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: StoredMessage[]
  documents: DocumentInfo[]
}

/** A file being uploaded right now (before the server knows about it). */
export interface PendingUpload {
  key: string
  name: string
  size: number
  progress: number // 0..1
  error?: string
}
