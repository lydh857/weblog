const KEY = 'weblog_nav_context_v1'

export type NavContext = {
  path: string
  fullPath: string
  scrollY: number
  ts: number
}

export function saveNavContext(ctx: Omit<NavContext, 'ts'>) {
  if (typeof window === 'undefined') return
  const payload: NavContext = { ...ctx, ts: Date.now() }
  try {
    sessionStorage.setItem(KEY, JSON.stringify(payload))
  } catch {
    // ignore
  }
}

export function consumeNavContext(maxAgeMs = 5 * 60 * 1000): NavContext | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    sessionStorage.removeItem(KEY)
    const parsed = JSON.parse(raw) as NavContext
    if (!parsed?.fullPath) return null
    if (Date.now() - (parsed.ts || 0) > maxAgeMs) return null
    return parsed
  } catch {
    return null
  }
}

