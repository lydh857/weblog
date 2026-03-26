import { useDark, useToggle } from '@vueuse/core'
import { watch } from 'vue'

const THEME_TRANSITION_ATTR = 'data-theme-switching'
const THEME_TRANSITION_STYLE_ID = 'weblog-theme-switch-style'
const THEME_TRANSITION_DURATION = 420

const THEME_TRANSITION_STYLE_CONTENT = `
*, *::before, *::after {
  transition-property: background, background-color, color, border-color, box-shadow, fill, stroke, outline-color, text-decoration-color !important;
  transition-duration: ${THEME_TRANSITION_DURATION}ms !important;
  transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1) !important;
}
`

interface ViewTransitionLike {
  finished: Promise<void>
}

type DocumentWithViewTransition = Document & {
  startViewTransition?: (callback: () => void | Promise<void>) => ViewTransitionLike
}

function shouldReduceMotion() {
  if (!import.meta.client || !window.matchMedia) {
    return false
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function ensureThemeTransitionStyle() {
  if (!import.meta.client || shouldReduceMotion()) {
    return
  }

  const existing = document.getElementById(THEME_TRANSITION_STYLE_ID)
  if (existing) {
    return
  }

  const style = document.createElement('style')
  style.id = THEME_TRANSITION_STYLE_ID
  style.textContent = THEME_TRANSITION_STYLE_CONTENT
  document.head.appendChild(style)
}

function removeThemeTransitionStyle() {
  if (!import.meta.client) {
    return
  }

  const style = document.getElementById(THEME_TRANSITION_STYLE_ID)
  if (style) {
    style.remove()
  }
}

export function useDarkMode() {
  const isDark = useDark({
    selector: 'html',
    attribute: 'class',
    valueDark: 'dark',
    valueLight: '',
  })
  const rawToggleDark = useToggle(isDark)
  let transitionTimer: ReturnType<typeof setTimeout> | null = null

  function startThemeTransition() {
    if (!import.meta.client) {
      return
    }

    const root = document.documentElement
    ensureThemeTransitionStyle()
    root.setAttribute(THEME_TRANSITION_ATTR, '1')

    if (transitionTimer) {
      clearTimeout(transitionTimer)
    }

    transitionTimer = window.setTimeout(() => {
      root.removeAttribute(THEME_TRANSITION_ATTR)
      removeThemeTransitionStyle()
      transitionTimer = null
    }, THEME_TRANSITION_DURATION)
  }

  function applyThemeToggle(value?: boolean) {
    if (typeof value === 'boolean') {
      return rawToggleDark(value)
    }
    return rawToggleDark()
  }

  const toggleDark = (value?: boolean) => {
    if (!import.meta.client) {
      return applyThemeToggle(value)
    }

    const doc = document as DocumentWithViewTransition
    const canUseViewTransition = typeof doc.startViewTransition === 'function' && !shouldReduceMotion()

    if (canUseViewTransition) {
      const root = document.documentElement
      root.setAttribute(THEME_TRANSITION_ATTR, '1')
      const transition = doc.startViewTransition(() => {
        applyThemeToggle(value)
      })
      transition.finished.finally(() => {
        root.removeAttribute(THEME_TRANSITION_ATTR)
      })
      return
    }

    startThemeTransition()
    void window.getComputedStyle(document.documentElement).getPropertyValue('background-color')
    return applyThemeToggle(value)
  }

  if (import.meta.client) {
    watch(
      isDark,
      (value) => {
        const cookieValue = value ? 'dark' : 'light'
        const cookie = `weblog-theme=${cookieValue}; Path=/; Max-Age=31536000; SameSite=Lax`
        document.cookie = cookie
      },
      { immediate: true }
    )
  }

  return { isDark, toggleDark }
}
