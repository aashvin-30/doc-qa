import { useMemo, useRef, useState, type ReactNode } from 'react'
import type { Conversation, DocumentInfo } from '../types'
import { groupOf, timeAgo, type Group } from '../time'
import { ACCEPT, fileIcon, formatSize } from '../files'

export type Tab = 'chats' | 'files'

interface Props {
  tab: Tab
  onTab: (t: Tab) => void
  onNewChat: () => void
  onClose: () => void

  conversations: Conversation[]
  activeId: string | null
  onOpenChat: (id: string) => void
  onRenameChat: (id: string, title: string) => void
  onDeleteChat: (id: string) => void

  documents: DocumentInfo[]
  chatFileIds: Set<string>
  onAddToChat: (id: string) => void
  onUpload: (files: File[]) => void
  onDeleteDoc: (id: string) => void
  footer?: ReactNode
}

const GROUPS: Group[] = ['Today', 'Yesterday', 'Previous 7 days', 'Older']

export default function Sidebar(p: Props) {
  const [query, setQuery] = useState('')
  const q = query.trim().toLowerCase()

  return (
    <>
      <div className="scrim" onClick={p.onClose} />
      <aside className="sidebar">
        <div className="sidebar-head">
          <button className="btn new-chat-btn" onClick={p.onNewChat}>
            <span aria-hidden>＋</span> New chat
          </button>
          <button className="icon-btn close-btn" onClick={p.onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={p.tab === 'chats'} className={p.tab === 'chats' ? 'on' : ''} onClick={() => p.onTab('chats')}>
            Chats <span className="count">{p.conversations.length}</span>
          </button>
          <button role="tab" aria-selected={p.tab === 'files'} className={p.tab === 'files' ? 'on' : ''} onClick={() => p.onTab('files')}>
            Files <span className="count">{p.documents.length}</span>
          </button>
        </div>

        <input
          className="search"
          type="search"
          placeholder={p.tab === 'chats' ? 'Search chats…' : 'Search files…'}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        {p.tab === 'chats' ? <ChatList {...p} q={q} /> : <FileList {...p} q={q} />}
        {p.footer}
      </aside>
    </>
  )
}

// ---------------------------------------------------------------- chats

function ChatList(p: Props & { q: string }) {
  const filtered = useMemo(
    () => p.conversations.filter((c) => !p.q || c.title.toLowerCase().includes(p.q)),
    [p.conversations, p.q],
  )

  if (p.conversations.length === 0) {
    return <p className="muted small empty">No saved chats yet. Ask a question and it will appear here.</p>
  }
  if (filtered.length === 0) return <p className="muted small empty">No chats match “{p.q}”.</p>

  return (
    <div className="list">
      {GROUPS.map((g) => {
        const items = filtered.filter((c) => groupOf(c.updatedAt) === g)
        if (!items.length) return null
        return (
          <section key={g}>
            <h3 className="group-label">{g}</h3>
            <ul>
              {items.map((c) => (
                <ChatRow key={c.id} c={c} active={c.id === p.activeId} {...p} />
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

function ChatRow({ c, active, onOpenChat, onRenameChat, onDeleteChat }: Props & { c: Conversation; active: boolean }) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(c.title)

  const save = () => {
    setEditing(false)
    const t = title.trim()
    if (t && t !== c.title) onRenameChat(c.id, t)
    else setTitle(c.title)
  }

  return (
    <li className={`row chat-row ${active ? 'active' : ''}`}>
      {editing ? (
        <input
          className="rename"
          autoFocus
          value={title}
          maxLength={120}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={save}
          onKeyDown={(e) => {
            if (e.key === 'Enter') save()
            if (e.key === 'Escape') {
              setTitle(c.title)
              setEditing(false)
            }
          }}
        />
      ) : (
        <button className="row-main" onClick={() => onOpenChat(c.id)} onDoubleClick={() => setEditing(true)} title={c.title}>
          <span className="row-title">{c.title}</span>
          <span className="muted small">{timeAgo(c.updatedAt)}</span>
        </button>
      )}
      {!editing && (
        <span className="row-actions">
          <button className="icon-btn" title="Rename" aria-label={`Rename ${c.title}`} onClick={() => setEditing(true)}>
            ✎
          </button>
          <button
            className="icon-btn"
            title="Delete"
            aria-label={`Delete ${c.title}`}
            onClick={() => confirm(`Delete chat "${c.title}"?`) && onDeleteChat(c.id)}
          >
            🗑
          </button>
        </span>
      )}
    </li>
  )
}

// ---------------------------------------------------------------- files

function FileList(p: Props & { q: string }) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const pick = (list: FileList | null) => list && list.length && p.onUpload(Array.from(list))

  const filtered = p.documents.filter((d) => !p.q || d.filename.toLowerCase().includes(p.q))

  return (
    <>
      <div
        className={`dropzone ${dragging ? 'dragging' : ''}`}
        onClick={() => input.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          pick(e.dataTransfer.files)
        }}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
      >
        <strong>Drag files here</strong>
        <span className="btn dz-btn">＋ Add files to this chat</span>
        <span className="muted small">PDF, DOCX, TXT, MD, CSV, JSON, HTML · max 25 MB</span>
        <input
          ref={input}
          type="file"
          accept={ACCEPT}
          multiple
          hidden
          onChange={(e) => {
            pick(e.target.files)
            e.target.value = ''
          }}
        />
      </div>

      <div className="list">
        <h3 className="group-label">Recent files · ＋ adds to this chat</h3>
        {p.documents.length === 0 && <p className="muted small empty">No files yet.</p>}
        {p.documents.length > 0 && filtered.length === 0 && <p className="muted small empty">No files match “{p.q}”.</p>}
        <ul>
          {filtered.map((d) => (
            <li key={d.id} className={`row doc ${p.chatFileIds.has(d.id) ? 'selected' : ''}`}>
              <div className="doc-main">
                <span className="file-icon" aria-hidden>
                  {fileIcon(d.filename)}
                </span>
                <div className="doc-text">
                  <span className="row-title" title={d.filename}>
                    {d.filename}
                  </span>
                  <span className="muted small">
                    {formatSize(d.sizeBytes)} ·{' '}
                    {d.lastUsedAt ? `used ${timeAgo(d.lastUsedAt)}` : `added ${timeAgo(d.createdAt)}`}
                  </span>
                  {d.status === 'FAILED' && d.errorMessage && <span className="doc-error small">{d.errorMessage}</span>}
                </div>
              </div>
              {d.status !== 'READY' && (
                <span className={`badge ${d.status.toLowerCase()}`}>{d.status === 'PROCESSING' ? 'Indexing…' : 'Failed'}</span>
              )}
              {p.chatFileIds.has(d.id) ? (
                <span className="in-chat" title="This file is in the open chat">✓ In chat</span>
              ) : (
                d.status !== 'FAILED' && (
                  <button className="add-btn" onClick={() => p.onAddToChat(d.id)} title="Add to this chat" aria-label={`Add ${d.filename} to this chat`}>
                    ＋
                  </button>
                )
              )}
              <span className="row-actions">
                <button
                  className="icon-btn"
                  onClick={() => confirm(`Delete "${d.filename}" everywhere? It will be removed from all chats.`) && p.onDeleteDoc(d.id)}
                  aria-label={`Delete ${d.filename}`}
                  title="Delete"
                >
                  🗑
                </button>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}
