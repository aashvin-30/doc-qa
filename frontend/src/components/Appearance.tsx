import { useEffect, useRef, useState } from 'react'

export type ThemeMode = 'system' | 'light' | 'dark'
export type Accent = 'violet' | 'ocean' | 'emerald' | 'sunset' | 'rose'

export const ACCENTS: { id: Accent; label: string; colors: [string, string] }[] = [
  { id: 'violet', label: 'Violet', colors: ['#7c5cff', '#c26bff'] },
  { id: 'ocean', label: 'Ocean', colors: ['#2f7cf6', '#06b6d4'] },
  { id: 'emerald', label: 'Emerald', colors: ['#10a37f', '#3fd18a'] },
  { id: 'sunset', label: 'Sunset', colors: ['#f2643d', '#f5a524'] },
  { id: 'rose', label: 'Rose', colors: ['#e9407a', '#b45cf0'] },
]

interface Props {
  theme: ThemeMode
  accent: Accent
  onTheme: (t: ThemeMode) => void
  onAccent: (a: Accent) => void
}

/** Sidebar footer: brand line + an "Appearance" popover to pick theme and accent color. */
export default function Appearance({ theme, accent, onTheme, onAccent }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false)
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const current = ACCENTS.find((a) => a.id === accent)!

  return (
    <div className="sidebar-foot" ref={ref}>
      <span className="foot-brand">
        <span className="dot" /> Doc-QA · RAG assistant
      </span>
      <button className="appearance-btn" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="swatch-dot" /> Appearance
      </button>

      {open && (
        <div className="appearance" role="dialog" aria-label="Appearance">
          <h4>Theme</h4>
          <div className="seg">
            {(['light', 'dark', 'system'] as ThemeMode[]).map((t) => (
              <button key={t} className={theme === t ? 'on' : ''} onClick={() => onTheme(t)}>
                {t === 'light' ? '☀ Light' : t === 'dark' ? '☾ Dark' : '⚙ Auto'}
              </button>
            ))}
          </div>

          <h4>Accent color</h4>
          <div>
            <div className="swatches">
              {ACCENTS.map((a) => (
                <button
                  key={a.id}
                  className={`swatch ${accent === a.id ? 'on' : ''}`}
                  style={{ background: `linear-gradient(135deg, ${a.colors[0]}, ${a.colors[1]})` }}
                  onClick={() => onAccent(a.id)}
                  aria-label={a.label}
                  title={a.label}
                />
              ))}
            </div>
            <div className="swatch-label">{current.label}</div>
          </div>
        </div>
      )}
    </div>
  )
}
