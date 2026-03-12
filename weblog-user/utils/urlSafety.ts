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
