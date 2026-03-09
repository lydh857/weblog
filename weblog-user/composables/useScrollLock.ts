let lockCount = 0

function getScrollbarWidth(): number {
  return window.innerWidth - document.documentElement.clientWidth
}

export function lockScroll() {
  if (typeof window === 'undefined') return
  lockCount++
  if (lockCount === 1) {
    const sw = getScrollbarWidth()
    document.documentElement.style.setProperty('--scrollbar-width', `${sw}px`)
    document.documentElement.style.overflowY = 'hidden'
    document.documentElement.style.paddingRight = `${sw}px`
  }
}

export function unlockScroll() {
  if (typeof window === 'undefined') return
  lockCount = Math.max(0, lockCount - 1)
  if (lockCount === 0) {
    document.documentElement.style.overflowY = ''
    document.documentElement.style.paddingRight = ''
    document.documentElement.style.setProperty('--scrollbar-width', '0px')
  }
}
