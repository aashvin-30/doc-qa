import { useEffect, useRef, useState } from 'react'
import { getConversation, streamAnswer } from '../api'
import type { ChatMessage, DocumentInfo, PendingUpload } from '../types'
import { ACCEPT, fileIcon, formatSize } from '../files'
import MessageBubble from './MessageBubble'

interface Props {
  conversationId: string | null
  /** Files attached to this chat (the only files it answers from). */
  chatFiles: DocumentInfo[]
  /** Files being uploaded right now (or that failed to upload). */
  pending: PendingUpload[]
  onDismissPending: (key: string) => void
  /** Every uploaded file, for "add from your files". */
  library: DocumentInfo[]
  onAttachUpload: (files: File[]) => void
  onAttachExisting: (documentId: string) => void
  onDetach: (documentId: string) => void
  onLoaded: (documentIds: string[]) => void
  onConversationStarted: (id: string) => void
  onAnswerFinished: () => void
  onMissingConversation: () => void
}

const SUGGESTIONS = [
  'Summarize the key points of this file',
  'What are the main risks or open questions mentioned?',
  'List every date and deadline mentioned',
]

const uid = () => Math.random().toString(36).slice(2)

export default function ChatPanel(p: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [dragging, setDragging] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const ownId = useRef<string | null>(null) // chat whose messages are already on screen

  // Load a saved chat (messages + its files) when it is opened.
  useEffect(() => {
    const id = p.conversationId
    if (!id) {
      setMessages([])
      return
    }
    if (id === ownId.current) return
    abortRef.current?.abort()
    let cancelled = false
    setLoading(true)
    getConversation(id)
      .then((c) => {
        if (cancelled) return
        ownId.current = id
        p.onLoaded(c.documents.map((d) => d.id))
        setMessages(
          c.messages.map((m) => ({
            id: String(m.id),
            role: m.role,
            content: m.content,
            sources: m.sources,
            error: m.error && m.error !== 'Stopped' ? m.error : undefined,
          })),
        )
      })
      .catch(() => !cancelled && p.onMissingConversation())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p.conversationId])

  useEffect(() => {
    // Scroll only the message list (scrollIntoView would also scroll the page itself).
    const el = listRef.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }, [messages])

  // Close the + menu on outside click / Escape.
  useEffect(() => {
    if (!menuOpen) return
    const onDown = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenuOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  const ready = p.chatFiles.filter((f) => f.status === 'READY')
  const indexing = p.chatFiles.some((f) => f.status === 'PROCESSING')
  const uploading = p.pending.filter((u) => !u.error)
  const noFiles = p.chatFiles.length === 0 && p.pending.length === 0
  const canAsk = ready.length > 0 && !indexing && uploading.length === 0 && !loading
  const attachedIds = new Set(p.chatFiles.map((f) => f.id))
  const otherFiles = p.library.filter((d) => !attachedIds.has(d.id) && d.status !== 'FAILED')

  const placeholder = noFiles
    ? 'Add files with ＋ to start this chat'
    : uploading.length > 0
      ? `Uploading ${uploading.length} file${uploading.length > 1 ? 's' : ''}…`
      : indexing
      ? 'Wait for your files to finish indexing…'
      : ready.length === 0
        ? 'Your files could not be read. Add another file with ＋'
        : 'Ask about your files… (Shift+Enter for a new line)'

  const patch = (id: string, fn: (m: ChatMessage) => ChatMessage) =>
    setMessages((prev) => prev.map((m) => (m.id === id ? fn(m) : m)))

  const openPicker = () => {
    setMenuOpen(false)
    fileInput.current?.click()
  }

  const send = async (text: string) => {
    const question = text.trim()
    if (!question || busy || !canAsk) return

    const botId = uid()
    setMessages((prev) => [
      ...prev,
      { id: uid(), role: 'user', content: question },
      { id: botId, role: 'assistant', content: '', streaming: true },
    ])
    setInput('')
    setBusy(true)
    if (p.conversationId) ownId.current = p.conversationId

    const ctrl = new AbortController()
    abortRef.current = ctrl
    try {
      await streamAnswer(
        {
          question,
          conversationId: p.conversationId ?? undefined,
          documentIds: ready.map((f) => f.id),
        },
        {
          onConversation: (c) => {
            if (c.id !== p.conversationId) {
              ownId.current = c.id
              p.onConversationStarted(c.id)
            }
          },
          onSources: (sources) => patch(botId, (m) => ({ ...m, sources })),
          onDelta: (t) => patch(botId, (m) => ({ ...m, content: m.content + t })),
          onDone: (stop) =>
            patch(botId, (m) => ({
              ...m,
              streaming: false,
              content:
                stop === 'max_tokens' ? m.content + '\n\n_(Answer truncated: reached the length limit.)_' : m.content,
            })),
          onError: (message) => patch(botId, (m) => ({ ...m, streaming: false, error: message })),
        },
        ctrl.signal,
      )
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        patch(botId, (m) => ({ ...m, error: (e as Error).message || 'Network error' }))
      }
    } finally {
      patch(botId, (m) => ({ ...m, streaming: false }))
      setBusy(false)
      abortRef.current = null
      p.onAnswerFinished()
    }
  }

  return (
    <section
      className={`chat ${dragging ? 'dragging' : ''}`}
      onDragOver={(e) => {
        if (!e.dataTransfer.types.includes('Files')) return
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={(e) => {
        if (e.currentTarget === e.target) setDragging(false)
      }}
      onDrop={(e) => {
        e.preventDefault()
        setDragging(false)
        if (e.dataTransfer.files.length) p.onAttachUpload(Array.from(e.dataTransfer.files))
      }}
    >
      {dragging && (
        <div className="drop-overlay" aria-hidden>
          <div>
            <span className="drop-plus">＋</span>
            <strong>Drop your files to add them all to this chat</strong>
          </div>
        </div>
      )}

      <div className="messages" ref={listRef}>
        {loading ? (
          <div className="empty-state muted">Loading chat…</div>
        ) : messages.length === 0 ? (
          <div className="empty-state">
            {noFiles ? (
              <>
                <h2>Start a chat with your files</h2>
                <p className="muted">Each chat has its own files. Add one or many files at once, then ask anything about them.</p>
                <button className="btn big-add" onClick={openPicker}>
                  ＋ Add files from computer
                </button>
                <p className="muted small">
                  Tip: hold <kbd>Ctrl</kbd> to select several files, or drag a whole selection here
                </p>
              </>
            ) : (
              <>
                <h2>
                  Ask about{' '}
                  {p.chatFiles.length + uploading.length === 1
                    ? 'this file'
                    : `these ${p.chatFiles.length + uploading.length} files`}
                </h2>
                <p className="muted">
                  {uploading.length > 0
                    ? `Uploading ${uploading.length} file${uploading.length > 1 ? 's' : ''}…`
                    : indexing
                      ? 'Indexing your files… you can ask as soon as they are ready.'
                      : 'Answers cite the exact passages they use, across all files in this chat.'}
                </p>
                {canAsk && (
                  <div className="suggestions">
                    {SUGGESTIONS.map((s) => (
                      <button key={s} className="chip" onClick={() => send(s)}>
                        {s}
                      </button>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        ) : (
          messages.map((m) => <MessageBubble key={m.id} message={m} />)
        )}
      </div>

      <form
        className="composer"
        onSubmit={(e) => {
          e.preventDefault()
          send(input)
        }}
      >
        <div className="composer-box">
          {(p.chatFiles.length > 0 || p.pending.length > 0) && (
            <div className="attachments">
              {p.pending.map((u) => (
                <span key={u.key} className={`attachment ${u.error ? 'failed' : 'uploading'}`} title={u.error ?? u.name}>
                  <span aria-hidden>{fileIcon(u.name)}</span>
                  <span className="att-name">{u.name}</span>
                  <span className="att-meta">
                    {u.error ? u.error : `${Math.round(u.progress * 100)}%`}
                  </span>
                  {!u.error && (
                    <span className="att-progress" aria-hidden>
                      <span style={{ width: `${Math.max(4, u.progress * 100)}%` }} />
                    </span>
                  )}
                  {u.error && (
                    <button
                      type="button"
                      className="att-remove"
                      aria-label={`Dismiss ${u.name}`}
                      title="Dismiss"
                      onClick={() => p.onDismissPending(u.key)}
                    >
                      ×
                    </button>
                  )}
                </span>
              ))}
              {p.chatFiles.map((f) => (
                <span key={f.id} className={`attachment ${f.status.toLowerCase()}`} title={f.errorMessage ?? f.filename}>
                  <span aria-hidden>{fileIcon(f.filename)}</span>
                  <span className="att-name">{f.filename}</span>
                  <span className="att-meta">
                    {f.status === 'PROCESSING' ? (
                      <>
                        <span className="spinner" /> Indexing
                      </>
                    ) : f.status === 'FAILED' ? (
                      'Failed'
                    ) : (
                      formatSize(f.sizeBytes)
                    )}
                  </span>
                  <button
                    type="button"
                    className="att-remove"
                    aria-label={`Remove ${f.filename} from this chat`}
                    title="Remove from this chat"
                    onClick={() => p.onDetach(f.id)}
                    disabled={busy}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}

          <div className="composer-row">
            <div className="plus-wrap" ref={menuRef}>
              <button
                type="button"
                className={`plus-btn ${menuOpen ? 'on' : ''}`}
                onClick={() => setMenuOpen((o) => !o)}
                aria-label="Add files to this chat"
                aria-expanded={menuOpen}
                title="Add files"
                disabled={busy}
              >
                ＋
              </button>
              {menuOpen && (
                <div className="plus-menu" role="menu">
                  <button type="button" role="menuitem" className="menu-item primary" onClick={openPicker}>
                    <span aria-hidden>💻</span>
                    <span>
                      <strong>Upload from computer</strong>
                      <span className="muted small">Select one or many (Ctrl+click) · PDF, Word, TXT, CSV… · 25 MB each</span>
                    </span>
                  </button>
                  {otherFiles.length > 0 && (
                    <>
                      <div className="menu-label">Add from your files</div>
                      <div className="menu-scroll">
                        {otherFiles.map((d) => (
                          <button
                            key={d.id}
                            type="button"
                            role="menuitem"
                            className="menu-item"
                            onClick={() => {
                              p.onAttachExisting(d.id)
                              setMenuOpen(false)
                            }}
                          >
                            <span aria-hidden>{fileIcon(d.filename)}</span>
                            <span className="menu-file">
                              <span className="row-title">{d.filename}</span>
                              <span className="muted small">{formatSize(d.sizeBytes)}</span>
                            </span>
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              )}
              <input
                ref={fileInput}
                type="file"
                accept={ACCEPT}
                multiple
                hidden
                onChange={(e) => {
                  if (e.target.files?.length) p.onAttachUpload(Array.from(e.target.files))
                  e.target.value = ''
                }}
              />
            </div>

            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  send(input)
                }
              }}
              placeholder={placeholder}
              rows={1}
              maxLength={4000}
            />
            {busy ? (
              <button type="button" className="btn secondary" onClick={() => abortRef.current?.abort()}>
                Stop
              </button>
            ) : (
              <button type="submit" className="btn" disabled={!input.trim() || !canAsk}>
                Ask
              </button>
            )}
          </div>
        </div>
      </form>
    </section>
  )
}
