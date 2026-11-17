/** "just now", "5 min ago", "3 h ago", "Yesterday", "Mon", "12 Nov". */
export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`
  if (isSameDay(d, new Date())) return `${Math.floor(diff / 3600)} h ago`
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  if (isSameDay(d, yesterday)) return 'Yesterday'
  if (diff < 7 * 86400) return d.toLocaleDateString(undefined, { weekday: 'short' })
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export type Group = 'Today' | 'Yesterday' | 'Previous 7 days' | 'Older'

export function groupOf(iso: string): Group {
  const d = new Date(iso)
  const now = new Date()
  if (isSameDay(d, now)) return 'Today'
  const y = new Date()
  y.setDate(y.getDate() - 1)
  if (isSameDay(d, y)) return 'Yesterday'
  if (now.getTime() - d.getTime() < 7 * 86400 * 1000) return 'Previous 7 days'
  return 'Older'
}

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}
