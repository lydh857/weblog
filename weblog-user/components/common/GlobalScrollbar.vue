<template>
  <ClientOnly>
    <div
      v-show="shouldShow"
      class="global-scrollbar"
      :class="{ 'is-dark': isDarkMode, 'is-dragging': isDragging }"
      aria-hidden="true"
      @mousedown.prevent="handleTrackMouseDown"
    >
      <div
        class="global-scrollbar__thumb"
        :style="thumbStyle"
        @mousedown.stop.prevent="handleThumbMouseDown"
      />
    </div>
  </ClientOnly>
</template>

<script setup lang="ts">
const TRACK_GAP = 0
const MIN_THUMB_HEIGHT = 40

const viewportHeight = ref(0)
const documentHeight = ref(0)
const scrollTop = ref(0)
const isDarkMode = ref(false)
const isDragging = ref(false)
const isCoarsePointer = ref(false)
const isScrollLocked = ref(false)

let dragStartClientY = 0
let dragStartThumbTop = 0
let themeObserver: MutationObserver | null = null
let bodyObserver: MutationObserver | null = null
let coarsePointerMedia: MediaQueryList | null = null
let activeDragMoveHandler: ((event: MouseEvent) => void) | null = null
let activeDragUpHandler: (() => void) | null = null
let layoutResizeObserver: ResizeObserver | null = null
let metricsRafId: number | null = null

function getScrollingElement() {
  if (!import.meta.client) {
    return null
  }
  return document.scrollingElement as HTMLElement | null
}

const handleWindowScroll = () => {
  if (!import.meta.client) {
    return
  }

  const scrollingElement = getScrollingElement()
  if (!scrollingElement) {
    return
  }

  scrollTop.value = scrollingElement.scrollTop
}

const handleWindowResize = () => {
  scheduleMetricsUpdate()
  updateCoarsePointerState()
}

const handleCoarsePointerChange = () => {
  updateCoarsePointerState()
  scheduleMetricsUpdate()
}

const handleDocumentVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    scheduleMetricsUpdate()
  }
}

const trackHeight = computed(() => Math.max(0, viewportHeight.value - TRACK_GAP * 2))
const maxScrollTop = computed(() => Math.max(0, documentHeight.value - viewportHeight.value))

const thumbHeight = computed(() => {
  if (trackHeight.value <= 0 || documentHeight.value <= 0) {
    return MIN_THUMB_HEIGHT
  }

  const ratio = viewportHeight.value / documentHeight.value
  return Math.min(trackHeight.value, Math.max(MIN_THUMB_HEIGHT, Math.round(trackHeight.value * ratio)))
})

const maxThumbTop = computed(() => Math.max(0, trackHeight.value - thumbHeight.value))

const thumbTop = computed(() => {
  if (maxScrollTop.value <= 0 || maxThumbTop.value <= 0) {
    return 0
  }

  const normalizedScrollTop = clamp(scrollTop.value, 0, maxScrollTop.value)
  return (normalizedScrollTop / maxScrollTop.value) * maxThumbTop.value
})

const shouldShow = computed(() => (
  !isCoarsePointer.value &&
  !isScrollLocked.value &&
  maxScrollTop.value > 2 &&
  trackHeight.value > MIN_THUMB_HEIGHT
))

const thumbStyle = computed(() => ({
  height: `${thumbHeight.value}px`,
  transform: `translate3d(0, ${thumbTop.value}px, 0)`,
}))

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function scheduleMetricsUpdate() {
  if (!import.meta.client) {
    return
  }

  if (metricsRafId !== null) {
    return
  }

  metricsRafId = window.requestAnimationFrame(() => {
    metricsRafId = null
    updateMetrics()
  })
}

function updateMetrics() {
  if (!import.meta.client) {
    return
  }

  const scrollingElement = getScrollingElement()
  if (!scrollingElement) {
    return
  }

  viewportHeight.value = window.innerHeight
  documentHeight.value = scrollingElement.scrollHeight
  scrollTop.value = scrollingElement.scrollTop
}

function updateDarkModeState() {
  if (!import.meta.client) {
    isDarkMode.value = false
    return
  }

  const root = document.documentElement
  const body = document.body
  isDarkMode.value = root.classList.contains('dark') || body.classList.contains('dark') || root.getAttribute('data-theme') === 'dark'
}

function updateScrollLockState() {
  if (!import.meta.client) {
    isScrollLocked.value = false
    return
  }

  const body = document.body
  isScrollLocked.value = body.style.position === 'fixed' || body.style.overflowY === 'hidden'
}

function updateCoarsePointerState() {
  if (!import.meta.client || !window.matchMedia) {
    isCoarsePointer.value = false
    return
  }

  if (!coarsePointerMedia) {
    coarsePointerMedia = window.matchMedia('(pointer: coarse)')
  }
  isCoarsePointer.value = coarsePointerMedia.matches
}

function scrollToThumbTop(nextThumbTop: number) {
  if (maxThumbTop.value <= 0 || maxScrollTop.value <= 0) {
    return
  }

  const scrollingElement = getScrollingElement()
  if (!scrollingElement) {
    return
  }

  const ratio = clamp(nextThumbTop, 0, maxThumbTop.value) / maxThumbTop.value
  const nextScrollTop = ratio * maxScrollTop.value
  scrollingElement.scrollTo({ top: nextScrollTop, behavior: 'auto' })
}

function handleTrackMouseDown(event: MouseEvent) {
  if (!shouldShow.value) {
    return
  }

  const target = event.currentTarget
  if (!(target instanceof HTMLElement)) {
    return
  }

  const rect = target.getBoundingClientRect()
  const clickOffset = event.clientY - rect.top
  const nextThumbTop = clickOffset - (thumbHeight.value / 2)
  scrollToThumbTop(nextThumbTop)
}

function handleThumbMouseDown(event: MouseEvent) {
  if (!shouldShow.value) {
    return
  }

  isDragging.value = true
  dragStartClientY = event.clientY
  dragStartThumbTop = thumbTop.value

  const handleMouseMove = (moveEvent: MouseEvent) => {
    const deltaY = moveEvent.clientY - dragStartClientY
    scrollToThumbTop(dragStartThumbTop + deltaY)
  }

  const handleMouseUp = () => {
    isDragging.value = false
    if (activeDragMoveHandler) {
      window.removeEventListener('mousemove', activeDragMoveHandler)
      activeDragMoveHandler = null
    }
    if (activeDragUpHandler) {
      window.removeEventListener('mouseup', activeDragUpHandler)
      activeDragUpHandler = null
    }
  }

  activeDragMoveHandler = handleMouseMove
  activeDragUpHandler = handleMouseUp
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp)
}

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  updateMetrics()
  updateDarkModeState()
  updateScrollLockState()
  updateCoarsePointerState()

  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  window.addEventListener('resize', handleWindowResize)
  coarsePointerMedia?.addEventListener('change', handleCoarsePointerChange)

  themeObserver = new MutationObserver(() => {
    updateDarkModeState()
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
  themeObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })

  bodyObserver = new MutationObserver(() => {
    updateScrollLockState()
    scheduleMetricsUpdate()
  })
  bodyObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['style', 'class'],
  })

  if (typeof ResizeObserver !== 'undefined') {
    layoutResizeObserver = new ResizeObserver(() => {
      scheduleMetricsUpdate()
    })
    layoutResizeObserver.observe(document.documentElement)
  }

  document.addEventListener('visibilitychange', handleDocumentVisibilityChange)

})

onBeforeUnmount(() => {
  if (!import.meta.client) {
    return
  }

  window.removeEventListener('scroll', handleWindowScroll)
  window.removeEventListener('resize', handleWindowResize)
  document.removeEventListener('visibilitychange', handleDocumentVisibilityChange)
  coarsePointerMedia?.removeEventListener('change', handleCoarsePointerChange)
  if (activeDragMoveHandler) {
    window.removeEventListener('mousemove', activeDragMoveHandler)
    activeDragMoveHandler = null
  }
  if (activeDragUpHandler) {
    window.removeEventListener('mouseup', activeDragUpHandler)
    activeDragUpHandler = null
  }
  themeObserver?.disconnect()
  bodyObserver?.disconnect()
  layoutResizeObserver?.disconnect()
  if (metricsRafId !== null) {
    window.cancelAnimationFrame(metricsRafId)
    metricsRafId = null
  }
})
</script>

<style scoped lang="scss">
.global-scrollbar {
  position: fixed;
  top: 0;
  right: 0;
  width: 4px;
  height: 100vh;
  border-radius: 0;
  background: transparent;
  z-index: 55000;
  cursor: pointer;
  user-select: none;
  opacity: 0.72;
  transition: width 0.2s ease, opacity 0.2s ease;
}

.global-scrollbar::before {
  content: '';
  position: absolute;
  top: 0;
  left: -8px;
  width: 12px;
  height: 100%;
}

.global-scrollbar__thumb {
  width: 100%;
  border-radius: 0;
  background: rgba(0, 0, 0, 0.4);
  border: none;
  transition: background-color 0.2s ease;
}

.global-scrollbar:hover,
.global-scrollbar.is-dragging {
  width: 8px;
  opacity: 1;
}

.global-scrollbar:hover .global-scrollbar__thumb,
.global-scrollbar.is-dragging .global-scrollbar__thumb {
  background: rgba(0, 0, 0, 0.6);
}

.global-scrollbar.is-dark {
  background: transparent;
}

.global-scrollbar.is-dark .global-scrollbar__thumb {
  background: rgba(148, 163, 184, 0.28);
}

.global-scrollbar.is-dark:hover .global-scrollbar__thumb,
.global-scrollbar.is-dark.is-dragging .global-scrollbar__thumb {
  background: rgba(148, 163, 184, 0.58);
}

@media (pointer: coarse), (max-width: 1024px) {
  .global-scrollbar {
    display: none !important;
  }
}
</style>
