export function normalizeSafeHref(rawUrl: string | null | undefined): string | null {
  if (!rawUrl) return null

  const trimmed = rawUrl.trim()
  if (!trimmed) return null

  if (trimmed.startsWith('/') && !trimmed.startsWith('//')) {
    return trimmed
  }

  let parsed: URL
  try {
    parsed = new URL(trimmed)
  } catch {
    return null
  }

  const protocol = parsed.protocol.toLowerCase()
  if (protocol !== 'http:' && protocol !== 'https:') {
    return null
  }

  return parsed.toString()
}

export function buildSafeOutboundHref(rawUrl: string | null | undefined, scene: string = 'general'): string | null {
  const safeHref = normalizeSafeHref(rawUrl)
  if (!safeHref) return null

  if (safeHref.startsWith('/')) {
    return safeHref
  }

  const encodedTarget = encodeURIComponent(safeHref)
  const encodedScene = encodeURIComponent(scene)
  return `/outbound?target=${encodedTarget}&scene=${encodedScene}`
}
