<template>
  <div class="article-toc-container" :class="{ 'is-fixed': fixed }" :style="tocContainerStyle">
    <div class="article-toc" :class="{ 'toc-visible': isVisible }">
      <!-- 目录标题 -->
      <div class="toc-header">
        <span class="toc-title">目录</span>
        <button class="print-btn" title="打印文章" aria-label="打印文章" @click="printArticle">
          <Icon name="heroicons:printer-20-solid" size="16" />
        </button>
      </div>
      <!-- 目录列表 -->
      <div v-if="tocItems.length > 0" ref="tocListRef" v-custom-scrollbar class="toc-items">
        <div
          v-for="item in tocItems"
          :key="item.id"
          class="toc-item"
          :class="{ [`level-${item.level}`]: true, active: activeId === item.id }"
          @click="scrollToHeading(item.id)"
        >
          <span class="toc-text">{{ item.text }}</span>
        </div>
      </div>
      <div v-else class="no-toc">
        <Icon name="heroicons:information-circle-20-solid" size="24" />
        <span>此文章无目录</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { sanitizeHtml, sanitizeText } from '~/utils/security/xss'

interface TocItem {
  id: string
  text: string
  level: number
}

const props = defineProps<{
  contentSelector: string
  fixed?: boolean
  fixedTop?: string
  fixedLeft?: string
  fixedWidth?: string
  fixedZIndex?: number
}>()

const fixed = computed(() => Boolean(props.fixed))

const tocContainerStyle = computed(() => {
  if (!fixed.value) {
    return undefined
  }
  return {
    '--toc-fixed-top': props.fixedTop || 'calc(var(--layout-navbar-height, 60px) + 10px)',
    '--toc-fixed-left': props.fixedLeft || 'auto',
    '--toc-fixed-width': props.fixedWidth || '240px',
    '--toc-fixed-z-index': String(props.fixedZIndex ?? 260),
  }
})

const tocItems = ref<TocItem[]>([])
const activeId = ref('')
const isVisible = ref(true)
const isManualScrolling = ref(false)
const tocListRef = ref<HTMLElement | null>(null)
const lastScrollY = ref(0)
let scrollDetectTimer: ReturnType<typeof setInterval> | null = null
let contentObserver: MutationObserver | null = null
let tocRetryTimer: ReturnType<typeof setTimeout> | null = null
let tocRetryCount = 0

// 生成目录
function generateToc() {
  const container =
    document.querySelector(props.contentSelector)
    || document.querySelector('.post-content .md-editor-preview')
    || document.querySelector('.post-content .md-editor-preview-wrapper .md-editor-preview')
  if (!container) {
    isVisible.value = true
    if (tocRetryCount < 20) {
      tocRetryCount += 1
      if (tocRetryTimer) {
        clearTimeout(tocRetryTimer)
      }
      tocRetryTimer = setTimeout(generateToc, 120)
    }
    return
  }

  tocRetryCount = 0
  if (tocRetryTimer) {
    clearTimeout(tocRetryTimer)
    tocRetryTimer = null
  }

  const headings = container.querySelectorAll('h1, h2, h3, h4')
  const items: TocItem[] = []
  headings.forEach((h, i) => {
    const id = `heading-${i}`
    h.setAttribute('id', id)
    items.push({ id, text: h.textContent || '', level: parseInt(h.tagName[1]!) })
  })
  tocItems.value = items
  const firstItem = items[0]
  if (firstItem) activeId.value = firstItem.id
  isVisible.value = true
}

// 滚动跟随高亮
function handleScroll() {
  if (isManualScrolling.value || tocItems.value.length === 0) return
  const offset = 100
  const scrollingDown = window.scrollY > lastScrollY.value
  lastScrollY.value = window.scrollY
  let newId = ''
  if (scrollingDown) {
    for (const item of tocItems.value) {
      const el = document.getElementById(item.id)
      if (el && el.getBoundingClientRect().top <= offset) newId = item.id
      else break
    }
  } else {
    for (let i = tocItems.value.length - 1; i >= 0; i--) {
      const item = tocItems.value[i]
      if (!item) continue
      const el = document.getElementById(item.id)
      if (el && el.getBoundingClientRect().top <= offset) { newId = item.id; break }
    }
  }
  if (!newId && tocItems.value.length > 0) {
    const firstItem = tocItems.value[0]
    if (firstItem) {
      newId = firstItem.id
    }
  }
  if (newId && newId !== activeId.value) {
    activeId.value = newId
    scrollTocToActive()
  }
}

// 目录列表自动滚动到激活项
function scrollTocToActive() {
  if (!tocListRef.value) return
  requestAnimationFrame(() => {
    const container = tocListRef.value
    if (!container) return
    const activeEl = container.querySelector('.toc-item.active') as HTMLElement
    if (!activeEl) return
    const cRect = container.getBoundingClientRect()
    const aRect = activeEl.getBoundingClientRect()
    const relTop = aRect.top - cRect.top
    if (relTop < 30 || relTop + aRect.height > cRect.height - 30) {
      container.scrollTo({ top: container.scrollTop + relTop - cRect.height / 3, behavior: 'smooth' })
    }
  })
}

// 点击跳转
function scrollToHeading(id: string) {
  const el = document.getElementById(id)
  if (!el) return
  activeId.value = id
  isManualScrolling.value = true
  scrollTocToActive()
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  lastScrollY.value = window.scrollY
  if (scrollDetectTimer) clearInterval(scrollDetectTimer)
  scrollDetectTimer = setInterval(() => {
    if (Math.abs(window.scrollY - lastScrollY.value) < 2) {
      isManualScrolling.value = false
      if (scrollDetectTimer) { clearInterval(scrollDetectTimer); scrollDetectTimer = null }
    }
    lastScrollY.value = window.scrollY
  }, 100)
}

// 打印文章
function printArticle() {
  const content = document.querySelector(props.contentSelector)
  if (!content) return
  const title = sanitizeText(document.querySelector('.post-title')?.textContent || '文章打印')
  const safeContent = sanitizeHtml(content.innerHTML)

  const printHtml = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>${title}</title>
    <style>body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;padding:2rem;line-height:1.8;color:#333}
    h1,h2,h3,h4{margin-top:1.5em;margin-bottom:0.5em;font-weight:600}
    pre{background:#f5f5f5;padding:1rem;border-radius:4px;overflow-x:auto;font-size:0.9rem}
    code{font-family:Consolas,monospace;font-size:0.9em}
    img{max-width:100%;height:auto}
    table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:0.5rem}
    blockquote{border-left:4px solid #3498db;padding:0.5rem 1rem;margin:1rem 0;color:#555;background:#f8f9fa}</style>
    </head><body><h1>${title}</h1>${safeContent}</body></html>`

  const blob = new Blob([printHtml], { type: 'text/html' })
  const blobUrl = URL.createObjectURL(blob)

  const printWin = window.open(blobUrl, '_blank', 'noopener,noreferrer')
  if (!printWin) {
    URL.revokeObjectURL(blobUrl)
    return
  }

  try {
    printWin.opener = null
  } catch {
    // 忽略即可
  }

  printWin.addEventListener('load', () => {
    URL.revokeObjectURL(blobUrl)
    printWin.print()
  }, { once: true })
}

// 监听 DOM 变化
function observeContent() {
  const el = document.querySelector(props.contentSelector)
  if (!el) return
  contentObserver = new MutationObserver((mutations) => {
    const hasHeadingChange = mutations.some(m =>
      m.type === 'childList' && Array.from(m.addedNodes).some(n =>
        n.nodeType === Node.ELEMENT_NODE && (/^H[1-4]$/.test((n as HTMLElement).tagName) || (n as HTMLElement).querySelector?.('h1,h2,h3,h4'))
      )
    )
    if (hasHeadingChange) setTimeout(generateToc, 100)
  })
  contentObserver.observe(el, { childList: true, subtree: true })
}

let throttleTimer = 0
function throttledScroll() {
  const now = Date.now()
  if (now - throttleTimer < 50) return
  throttleTimer = now
  requestAnimationFrame(handleScroll)
}

onMounted(() => {
  nextTick(() => {
    isVisible.value = true
    generateToc()
    observeContent()
    window.addEventListener('scroll', throttledScroll, { passive: true })
  })
})

onUnmounted(() => {
  window.removeEventListener('scroll', throttledScroll)
  if (scrollDetectTimer) clearInterval(scrollDetectTimer)
  if (contentObserver) contentObserver.disconnect()
  if (tocRetryTimer) clearTimeout(tocRetryTimer)
})
</script>

<style scoped lang="scss">
.article-toc-container { width: 240px; flex-shrink: 0; }

.article-toc-container.is-fixed {
  min-height: clamp(260px, 42vh, 420px);
}

.article-toc-container.is-fixed .article-toc {
  position: fixed;
  top: var(--toc-fixed-top);
  left: var(--toc-fixed-left);
  width: var(--toc-fixed-width);
  max-width: calc(100vw - 24px);
  z-index: var(--toc-fixed-z-index);
}

@media (max-width: 1100px) {
  .article-toc-container.is-fixed {
    min-height: 0;
  }

  .article-toc-container.is-fixed .article-toc {
    position: static;
    top: auto;
    left: auto;
    width: auto;
    max-width: none;
    z-index: auto;
  }
}

.article-toc {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.06);
  padding: 0.875rem 1rem;
  opacity: 0;
  transform: translateY(-8px);
  transition: opacity 0.25s ease, transform 0.25s ease;
  &.toc-visible { opacity: 1; transform: translateY(0); }
  .dark & { background: $color-dark-bg-secondary; box-shadow: 0 1px 8px rgba(0,0,0,0.2); }
}
.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.625rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f0f0f0;
  .dark & { border-bottom-color: $color-dark-border; }
}
.toc-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: $color-text;
  letter-spacing: 0.02em;
  .dark & { color: $color-dark-text; }
}
.print-btn {
  background: none; border: none; padding: 4px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 6px;
  color: #999; transition: all 0.2s;
  &:hover { background: #f0f7ff; color: $color-primary; }
  .dark & { color: $color-dark-text-muted; &:hover { background: rgba(59,130,246,0.1); color: $color-primary; } }
}
.toc-items {
  max-height: clamp(220px, calc(70vh - 100px), 560px);
  overflow-y: auto;
  scroll-behavior: smooth;
  padding-right: 2px;
  margin-left: 2px;
  border-left: 1.5px solid #eef2f6;
  .dark & { border-left-color: $color-dark-border; }
  mask-image: linear-gradient(to bottom, transparent 0, black 4px, black calc(100% - 4px), transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0, black 4px, black calc(100% - 4px), transparent 100%);
  padding-top: 2px; padding-bottom: 2px;
}
.toc-item {
  font-size: 0.8rem;
  padding: 4px 8px 4px 10px;
  margin-left: -1.5px;
  cursor: pointer;
  color: #64748b;
  line-height: 1.4;
  position: relative;
  border-left: 1.5px solid transparent;
  transition: color 0.2s, border-color 0.2s, background 0.15s;
  &:hover {
    color: $color-primary;
    background: #f8fafc;
    .dark & { background: rgba(59,130,246,0.06); }
  }
  &.active {
    color: $color-primary;
    border-left-color: $color-primary;
    font-weight: 500;
    background: #f0f7ff;
    .dark & { color: #60a5fa; border-left-color: #60a5fa; background: rgba(59,130,246,0.1); }
  }
  .dark & { color: $color-dark-text-muted; }
  .toc-text {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &.level-1 { font-size: 0.825rem; font-weight: 600; color: #334155; .dark & { color: $color-dark-text; } }
  &.level-2 { padding-left: 18px; }
  &.level-3 { padding-left: 28px; font-size: 0.775rem; }
  &.level-4 { padding-left: 38px; font-size: 0.75rem; color: #94a3b8; }
}
  .no-toc {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #999; padding: 1.5rem 0; font-size: 0.85rem; gap: 0.5rem;
  .dark & { color: $color-dark-text-muted; }
}
</style>
