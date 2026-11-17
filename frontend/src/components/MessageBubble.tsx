import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage } from '../types'

/** Turn bare [n] citation markers into links we can render as clickable chips. */
const linkCitations = (text: string) => text.replace(/\[(\d{1,2})\](?!\()/g, '[$1](#cite-$1)')

export default function MessageBubble({ message }: { message: ChatMessage }) {
  const [active, setActive] = useState<number | null>(null)
  const [copied, setCopied] = useState(false)
  const sources = message.sources ?? []

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(message.content)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard blocked */
    }
  }

  if (message.role === 'user') {
    return (
      <div className="msg user">
        <div className="bubble">{message.content}</div>
      </div>
    )
  }

  const waiting = message.streaming && !message.content

  return (
    <div className="msg assistant">
      <div className="bubble">
        {waiting ? (
          <span className="typing" aria-label="Thinking">
            <i />
            <i />
            <i />
          </span>
        ) : (
          <div className="markdown">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                a: ({ href, children }) => {
                  const m = href?.match(/^#cite-(\d+)$/)
                  if (m) {
                    const n = Number(m[1])
                    return (
                      <button
                        className={`cite ${active === n ? 'on' : ''}`}
                        onClick={() => setActive(active === n ? null : n)}
                        title={sources.find((s) => s.index === n)?.filename}
                      >
                        {n}
                      </button>
                    )
                  }
                  return (
                    <a href={href} target="_blank" rel="noreferrer">
                      {children}
                    </a>
                  )
                },
              }}
            >
              {linkCitations(message.content)}
            </ReactMarkdown>
            {message.streaming && <span className="cursor" />}
          </div>
        )}
        {message.error && <div className="msg-error">⚠ {message.error}</div>}

        {!message.streaming && message.content && (
          <div className="msg-tools">
            <button className="tool-btn" onClick={copy} title="Copy answer">
              {copied ? '✓ Copied' : '⧉ Copy'}
            </button>
          </div>
        )}

        {sources.length > 0 && (
          <details className="sources" open={active !== null}>
            <summary>
              {sources.length} source{sources.length > 1 ? 's' : ''}
            </summary>
            <ol>
              {sources.map((s) => (
                <li
                  key={s.index}
                  className={active === s.index ? 'on' : ''}
                  onClick={() => setActive(active === s.index ? null : s.index)}
                >
                  <div className="source-head">
                    <span className="cite static">{s.index}</span>
                    <strong title={s.filename}>{s.filename}</strong>
                    {s.pageNumber != null && <span className="muted small">p. {s.pageNumber}</span>}
                    <span className="muted small score">{Math.round(s.similarity * 100)}% match</span>
                  </div>
                  <p className={`snippet ${active === s.index ? 'expanded' : ''}`}>{s.snippet}</p>
                </li>
              ))}
            </ol>
          </details>
        )}
      </div>
    </div>
  )
}
