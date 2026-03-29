export function scrollToTopOnMobilePagination(): boolean {
  if (!import.meta.client) return false

  const isMobileViewport = window.matchMedia('(max-width: 768px)').matches
  if (!isMobileViewport) return false

  window.scrollTo({ top: 0, behavior: 'auto' })
  return true
}
