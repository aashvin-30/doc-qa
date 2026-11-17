export const ACCEPT = '.pdf,.docx,.txt,.md,.csv,.json,.html,.htm,.xml,.log'

export function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function fileIcon(name: string) {
  const ext = name.split('.').pop()?.toLowerCase()
  if (ext === 'pdf') return '📕'
  if (ext === 'docx') return '📘'
  if (ext === 'csv' || ext === 'json') return '📊'
  return '📄'
}

export const MAX_FILE_BYTES = 25 * 1024 * 1024
const EXTENSIONS = ACCEPT.split(',')

/** Returns an error message if the file can't be uploaded, otherwise null. */
export function validateFile(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!EXTENSIONS.some((ext) => name.endsWith(ext))) return 'Unsupported type'
  if (file.size === 0) return 'File is empty'
  if (file.size > MAX_FILE_BYTES) return 'Larger than 25 MB'
  return null
}

/** Runs async tasks with at most `limit` in flight at once. */
export async function runLimited<T>(items: T[], limit: number, task: (item: T) => Promise<void>) {
  let next = 0
  const worker = async () => {
    while (next < items.length) await task(items[next++])
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
}
