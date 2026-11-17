import { useCallback, useEffect, useRef, useState } from 'react'
import {
  attachDocument,
  createConversation,
  deleteConversation,
  deleteDocument,
  detachDocument,
  listConversations,
  listDocuments,
  renameConversation,
  uploadDocument,
} from './api'
import type { Conversation, DocumentInfo, PendingUpload } from './types'
import { runLimited, validateFile } from './files'
import Sidebar, { type Tab } from './components/Sidebar'
import ChatPanel from './components/ChatPanel'
import Appearance, { ACCENTS, type Accent, type ThemeMode } from './components/Appearance'

const ACTIVE_KEY = 'docqa.activeChat'
const TAB_KEY = 'docqa.tab'

// localStorage can throw (private mode, blocked storage) - never let that break the app.
const load = (k: string) => {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
const store = (k: string, v: string | null) => {
  try {
    if (v === null) localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch {
    /* ignore */
  }
}

export default function App() {
  const [documents, setDocuments] = useState<DocumentInfo[]>([])
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeId, setActiveId] = useState<string | null>(() => load(ACTIVE_KEY))
  const [chatFileIds, setChatFileIds] = useState<string[]>([]) // files of the open chat
  const [chatKey, setChatKey] = useState(0) // bump to force a fresh, empty chat panel
  const [tab, setTab] = useState<Tab>(() => (load(TAB_KEY) === 'files' ? 'files' : 'chats'))
  const [error, setError] = useState<string | null>(null)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [theme, setTheme] = useState<ThemeMode>(() => {
    const t = load('docqa.theme')
    return t === 'light' || t === 'dark' ? t : 'system'
  })
  const [accent, setAccent] = useState<Accent>(() => {
    const a = load('docqa.accent')
    return ACCENTS.some((x) => x.id === a) ? (a as Accent) : 'violet'
  })
  const [pending, setPending] = useState<PendingUpload[]>([]) // uploads in progress (or failed)
  const [notice, setNotice] = useState<string | null>(null)
  const creating = useRef<Promise<string> | null>(null)

  useEffect(() => {
    if (!notice) return
    const t = setTimeout(() => setNotice(null), 4000)
    return () => clearTimeout(t)
  }, [notice])

  const fail = (e: unknown, fallback: string) => setError(e instanceof Error ? e.message : fallback)

  const refreshDocs = useCallback(async () => {
    try {
      setDocuments(await listDocuments())
    } catch (e) {
      fail(e, 'Could not reach the backend')
    }
  }, [])

  const refreshChats = useCallback(async () => {
    try {
      setConversations(await listConversations())
    } catch (e) {
      fail(e, 'Could not load chats')
    }
  }, [])

  useEffect(() => {
    refreshDocs()
    refreshChats()
  }, [refreshDocs, refreshChats])

  useEffect(() => store(ACTIVE_KEY, activeId), [activeId])
  useEffect(() => store(TAB_KEY, tab), [tab])

  // Apply theme + accent to <html>; CSS tokens do the rest.
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') delete root.dataset.theme
    else root.dataset.theme = theme
    root.dataset.accent = accent
    store('docqa.theme', theme)
    store('docqa.accent', accent)
  }, [theme, accent])

  // Poll while any document is still being indexed.
  useEffect(() => {
    if (!documents.some((d) => d.status === 'PROCESSING')) return
    const t = setInterval(refreshDocs, 2000)
    return () => clearInterval(t)
  }, [documents, refreshDocs])

  const newChat = () => {
    setActiveId(null)
    setChatFileIds([])
    setPending([])
    setChatKey((k) => k + 1)
    setSidebarOpen(false)
  }

  const openChat = (id: string) => {
    if (id !== activeId) {
      setChatFileIds([])
      setPending([])
      setActiveId(id)
    }
    setSidebarOpen(false)
  }

  /** A chat is created as soon as its first file is added. */
  const ensureChat = async (): Promise<string> => {
    if (activeId) return activeId
    if (!creating.current) {
      creating.current = createConversation()
        .then((c) => {
          setActiveId(c.id)
          refreshChats()
          return c.id
        })
        .finally(() => {
          creating.current = null
        })
    }
    return creating.current
  }

  const addToChat = (id: string) => setChatFileIds((prev) => (prev.includes(id) ? prev : [...prev, id]))

  const attachUpload = async (files: File[]) => {
    if (!files.length) return
    let chatId: string
    try {
      chatId = await ensureChat()
    } catch (e) {
      return fail(e, 'Could not start a chat')
    }

    // Show every file immediately: valid ones get a progress bar, invalid ones an error chip.
    const items = files.map((file) => ({ file, key: `${Date.now()}-${Math.random()}`, error: validateFile(file) }))
    setPending((prev) => [
      ...prev,
      ...items.map((i) => ({ key: i.key, name: i.file.name, size: i.file.size, progress: 0, error: i.error ?? undefined })),
    ])
    const patchPending = (key: string, fn: (p: PendingUpload) => PendingUpload) =>
      setPending((prev) => prev.map((x) => (x.key === key ? fn(x) : x)))

    let added = 0
    await runLimited(
      items.filter((i) => !i.error),
      3, // upload 3 files at the same time
      async ({ file, key }) => {
        try {
          const doc = await uploadDocument(file, chatId, (f) => patchPending(key, (x) => ({ ...x, progress: f })))
          setDocuments((prev) => [doc, ...prev.filter((d) => d.id !== doc.id)])
          addToChat(doc.id)
          setPending((prev) => prev.filter((x) => x.key !== key))
          added++
        } catch (e) {
          patchPending(key, (x) => ({ ...x, error: e instanceof Error ? e.message : 'Upload failed' }))
        }
      },
    )
    const failed = items.length - added
    if (items.length > 1) {
      setNotice(
        failed === 0
          ? `${added} files added to this chat`
          : `${added} of ${items.length} files added · ${failed} could not be uploaded`,
      )
    }
  }

  const attachExisting = async (documentId: string) => {
    try {
      const chatId = await ensureChat()
      addToChat(documentId)
      const docs = await attachDocument(chatId, documentId)
      setChatFileIds(docs.map((d) => d.id))
    } catch (e) {
      fail(e, 'Could not add the file')
    }
  }

  const detach = async (documentId: string) => {
    if (!activeId) return
    setChatFileIds((prev) => prev.filter((id) => id !== documentId))
    try {
      const docs = await detachDocument(activeId, documentId)
      setChatFileIds(docs.map((d) => d.id))
    } catch (e) {
      fail(e, 'Could not remove the file')
    }
  }

  const handleDeleteDoc = async (id: string) => {
    try {
      await deleteDocument(id)
      setDocuments((prev) => prev.filter((d) => d.id !== id))
      setChatFileIds((prev) => prev.filter((x) => x !== id))
    } catch (e) {
      fail(e, 'Delete failed')
    }
  }

  const handleRenameChat = async (id: string, title: string) => {
    try {
      const updated = await renameConversation(id, title)
      setConversations((prev) => prev.map((c) => (c.id === id ? updated : c)))
    } catch (e) {
      fail(e, 'Rename failed')
    }
  }

  const handleDeleteChat = async (id: string) => {
    try {
      await deleteConversation(id)
      setConversations((prev) => prev.filter((c) => c.id !== id))
      if (id === activeId) newChat()
    } catch (e) {
      fail(e, 'Delete failed')
    }
  }

  const byId = new Map(documents.map((d) => [d.id, d]))
  const chatFiles = chatFileIds.map((id) => byId.get(id)).filter((d): d is DocumentInfo => !!d)
  const activeTitle = conversations.find((c) => c.id === activeId)?.title

  return (
    <div className={`app ${sidebarOpen ? 'sidebar-open' : ''}`}>
      <Sidebar
        tab={tab}
        onTab={setTab}
        onNewChat={newChat}
        onClose={() => setSidebarOpen(false)}
        conversations={conversations}
        activeId={activeId}
        onOpenChat={openChat}
        onRenameChat={handleRenameChat}
        onDeleteChat={handleDeleteChat}
        documents={documents}
        chatFileIds={new Set(chatFileIds)}
        onAddToChat={attachExisting}
        onUpload={attachUpload}
        onDeleteDoc={handleDeleteDoc}
        footer={<Appearance theme={theme} accent={accent} onTheme={setTheme} onAccent={setAccent} />}
      />
      <main className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setSidebarOpen(true)} aria-label="Open sidebar">
            ☰
          </button>
          <div className="brand">
            <span className="logo">DQ</span>
            <div className="brand-text">
              <h1>{activeTitle ?? 'New chat'}</h1>
              <p className="muted">
                {chatFiles.length === 0
                  ? 'No files in this chat yet'
                  : `${chatFiles.length} file${chatFiles.length > 1 ? 's' : ''} in this chat`}
              </p>
            </div>
          </div>
          <div className="topbar-actions">
            <button className="btn" onClick={newChat} title="Start a new chat with new files">
              ＋ New chat
            </button>
          </div>
        </header>
        {error && (
          <div className="banner" role="alert">
            {error}
            <button className="link-btn" onClick={() => setError(null)}>
              Dismiss
            </button>
          </div>
        )}
        {notice && (
          <div className="toast" role="status">
            ✓ {notice}
          </div>
        )}
        <ChatPanel
          key={chatKey}
          conversationId={activeId}
          chatFiles={chatFiles}
          pending={pending}
          onDismissPending={(key) => setPending((prev) => prev.filter((x) => x.key !== key))}
          library={documents}
          onAttachUpload={attachUpload}
          onAttachExisting={attachExisting}
          onDetach={detach}
          onLoaded={(ids) => setChatFileIds((prev) => [...new Set([...ids, ...prev])])}
          onConversationStarted={(id) => {
            setActiveId(id)
            refreshChats()
          }}
          onAnswerFinished={() => {
            refreshChats()
            refreshDocs()
          }}
          onMissingConversation={newChat}
        />
      </main>
    </div>
  )
}
