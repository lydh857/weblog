interface CustomScrollbarInstance {
  enabled: boolean
  scheduleUpdate: () => void
  destroy: () => void
}

const instanceMap = new WeakMap<HTMLElement, CustomScrollbarInstance>()
const MIN_THUMB_HEIGHT = 40

function getIsDarkMode() {
  const root = document.documentElement
  const body = document.body
  return root.classList.contains('dark') || body.classList.contains('dark') || root.getAttribute('data-theme') === 'dark'
}

function createCustomScrollbar(el: HTMLElement, enabled: boolean) {
  const rail = document.createElement('div')
  rail.className = 'v-custom-scrollbar-rail'

  const thumb = document.createElement('div')
  thumb.className = 'v-custom-scrollbar-thumb'
  rail.appendChild(thumb)

  el.classList.add('v-custom-scrollbar-host')
  const computedStyle = window.getComputedStyle(el)
  const shouldPatchPosition = computedStyle.position === 'static'
  if (shouldPatchPosition) {
    el.style.position = 'relative'
  }
  el.appendChild(rail)

  let isEnabled = enabled
  let isDragging = false
  let dragStartClientY = 0
  let dragStartThumbTop = 0
  let currentThumbTop = 0
  let rafId: number | null = null
  let coarseMedia: MediaQueryList | null = null

  const resizeObserver = typeof ResizeObserver !== 'undefined'
    ? new ResizeObserver(() => scheduleUpdate())
    : null

  const themeObserver = new MutationObserver(() => {
    rail.classList.toggle('is-dark', getIsDarkMode())
  })

  const getMetrics = () => {
    const viewportHeight = el.clientHeight
    const scrollHeight = el.scrollHeight
    const maxScrollTop = Math.max(0, scrollHeight - viewportHeight)
    return { viewportHeight, scrollHeight, maxScrollTop }
  }

  const update = () => {
    rafId = null
    const isCoarsePointer = coarseMedia?.matches ?? false
    const { viewportHeight, scrollHeight, maxScrollTop } = getMetrics()
    const shouldShow = isEnabled && !isCoarsePointer && maxScrollTop > 2 && viewportHeight > MIN_THUMB_HEIGHT

    rail.style.display = shouldShow ? 'block' : 'none'
    if (!shouldShow) {
      rail.style.transform = 'translate3d(0, 0, 0)'
      return
    }

    const ratio = viewportHeight / scrollHeight
    const thumbHeight = Math.min(viewportHeight, Math.max(MIN_THUMB_HEIGHT, Math.round(viewportHeight * ratio)))
    const maxThumbTop = Math.max(0, viewportHeight - thumbHeight)
    const safeScrollTop = Math.min(maxScrollTop, Math.max(0, el.scrollTop))
    const thumbTop = maxScrollTop > 0 ? (safeScrollTop / maxScrollTop) * maxThumbTop : 0
    currentThumbTop = thumbTop

    // 由于 rail 挂在可滚动容器内部，需要用反向位移抵消容器内容滚动。
    rail.style.transform = `translate3d(0, ${safeScrollTop}px, 0)`
    thumb.style.height = `${thumbHeight}px`
    thumb.style.transform = `translate3d(0, ${thumbTop}px, 0)`
  }

  const scheduleUpdate = () => {
    if (rafId !== null) {
      return
    }
    rafId = window.requestAnimationFrame(update)
  }

  const scrollToThumbTop = (nextThumbTop: number) => {
    const { viewportHeight, scrollHeight, maxScrollTop } = getMetrics()
    if (maxScrollTop <= 0) {
      return
    }

    const thumbHeight = Math.min(viewportHeight, Math.max(MIN_THUMB_HEIGHT, Math.round(viewportHeight * (viewportHeight / scrollHeight))))
    const maxThumbTop = Math.max(0, viewportHeight - thumbHeight)
    const clampedThumbTop = Math.min(maxThumbTop, Math.max(0, nextThumbTop))
    const ratio = maxThumbTop > 0 ? (clampedThumbTop / maxThumbTop) : 0
    el.scrollTo({ top: ratio * maxScrollTop, behavior: 'auto' })
  }

  const handleScroll = () => scheduleUpdate()
  const handleResize = () => scheduleUpdate()
  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      scheduleUpdate()
    }
  }
  const handleMediaChange = () => scheduleUpdate()

  const handleTrackMouseDown = (event: MouseEvent) => {
    if (rail.style.display === 'none') {
      return
    }

    if (event.target !== rail) {
      return
    }

    const rect = rail.getBoundingClientRect()
    const clickOffset = event.clientY - rect.top
    const thumbHeight = thumb.getBoundingClientRect().height || MIN_THUMB_HEIGHT
    scrollToThumbTop(clickOffset - (thumbHeight / 2))
  }

  const handleThumbMouseDown = (event: MouseEvent) => {
    if (rail.style.display === 'none') {
      return
    }

    event.stopPropagation()
    event.preventDefault()

    isDragging = true
    rail.classList.add('is-dragging')
    dragStartClientY = event.clientY
    dragStartThumbTop = currentThumbTop

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const deltaY = moveEvent.clientY - dragStartClientY
      scrollToThumbTop(dragStartThumbTop + deltaY)
    }

    const handleMouseUp = () => {
      isDragging = false
      rail.classList.remove('is-dragging')
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
  }

  rail.addEventListener('mousedown', handleTrackMouseDown)
  thumb.addEventListener('mousedown', handleThumbMouseDown)
  el.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  coarseMedia = window.matchMedia('(pointer: coarse)')
  coarseMedia.addEventListener('change', handleMediaChange)

  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
  themeObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })

  resizeObserver?.observe(el)
  rail.classList.toggle('is-dark', getIsDarkMode())
  scheduleUpdate()

  const destroy = () => {
    rail.removeEventListener('mousedown', handleTrackMouseDown)
    thumb.removeEventListener('mousedown', handleThumbMouseDown)
    el.removeEventListener('scroll', handleScroll)
    window.removeEventListener('resize', handleResize)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    coarseMedia?.removeEventListener('change', handleMediaChange)
    resizeObserver?.disconnect()
    themeObserver.disconnect()

    if (rafId !== null) {
      window.cancelAnimationFrame(rafId)
      rafId = null
    }

    if (!isDragging) {
      rail.classList.remove('is-dragging')
    }

    rail.remove()
    el.classList.remove('v-custom-scrollbar-host')
    if (shouldPatchPosition) {
      el.style.position = ''
    }
  }

  return {
    get enabled() {
      return isEnabled
    },
    set enabled(value: boolean) {
      isEnabled = value
      scheduleUpdate()
    },
    scheduleUpdate,
    destroy,
  }
}

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.directive<HTMLElement, boolean>('custom-scrollbar', {
    mounted(el, binding) {
      if (!import.meta.client) {
        return
      }

      const instance = createCustomScrollbar(el, binding.value !== false)
      instanceMap.set(el, instance)
    },
    updated(el, binding) {
      const instance = instanceMap.get(el)
      if (!instance) {
        return
      }

      instance.enabled = binding.value !== false
      instance.scheduleUpdate()
    },
    unmounted(el) {
      const instance = instanceMap.get(el)
      if (!instance) {
        return
      }

      instance.destroy()
      instanceMap.delete(el)
    },
  })
})
