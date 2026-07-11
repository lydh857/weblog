<template>
  <Teleport to="body">
    <Transition name="notice-modal-fade" appear>
      <div v-if="visible && currentAnn" class="notice-modal-overlay">
        <article class="notice-modal-card" role="dialog" aria-modal="true" :aria-label="`弹窗公告：${currentAnn.title}`">
          <header class="notice-modal-header">
            <h3 class="notice-modal-title">{{ currentAnn.title }}</h3>
          </header>

          <div v-if="useCustomScrollbar" v-custom-scrollbar class="notice-modal-content">
            <ClientOnly>
              <MdPreview
                editor-id="announcement-modal-preview"
                :model-value="currentAnn.content"
                :sanitize="sanitize"
                :theme="editorTheme"
                :preview-theme="previewTheme"
                :code-theme="codeTheme"
                :code-foldable="false"
                :show-code-row-number="true"
                :no-highlight="true"
                :no-mermaid="true"
                :no-katex="true"
                :no-echarts="true"
                class="notice-md-preview"
              />
              <template #fallback>
                <!-- 已经过 sanitizeHtml 净化 -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="notice-md-fallback" v-html="sanitize(currentAnn.content)" />
              </template>
            </ClientOnly>
          </div>

          <div v-else class="notice-modal-content">
            <ClientOnly>
              <MdPreview
                editor-id="announcement-modal-preview"
                :model-value="currentAnn.content"
                :sanitize="sanitize"
                :theme="editorTheme"
                :preview-theme="previewTheme"
                :code-theme="codeTheme"
                :code-foldable="false"
                :show-code-row-number="true"
                :no-highlight="true"
                :no-mermaid="true"
                :no-katex="true"
                :no-echarts="true"
                class="notice-md-preview"
              />
              <template #fallback>
                <!-- 已经过 sanitizeHtml 净化 -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="notice-md-fallback" v-html="sanitize(currentAnn.content)" />
              </template>
            </ClientOnly>
          </div>

          <footer class="notice-modal-footer">
            <p class="notice-modal-time">{{ announcementTimeText }}</p>
            <div class="notice-modal-actions">
              <button v-if="hasNext" type="button" class="notice-modal-next" @click="goNext">下一条</button>
              <button type="button" class="notice-modal-action-close" @click="tryClose">我知道了</button>
            </div>
          </footer>
        </article>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { announcementApi, type AnnouncementVO } from '~/api/marketing/announcement'
import { fetchAllAnnouncements } from '~/composables/announcement/useAnnouncementRequestCache'
import { lockScroll, unlockScroll } from '~/composables/layout/useScrollLock'
import { sanitizeHtml } from '~/utils/security/xss'

const visible = ref(false)
const modalAnnouncements = ref<AnnouncementVO[]>([])
const currentIndex = ref(0)
const route = useRoute()

const DISMISSED_STORAGE_KEY = 'dismissed_announcements_modal_v1'
const MODAL_CACHE_STORAGE_KEY = 'announcement_modal_cache_v1'
const MODAL_CACHE_MAX_AGE = 1000 * 60 * 30
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const ENVELOPE_STATE_EVENT = 'weblog:envelope-popup-state'
const FETCH_RETRY_COUNT = 2
const FETCH_RETRY_DELAY = 260
const DESKTOP_SCROLLBAR_MEDIA = '(pointer: fine) and (min-width: 1025px)'
let startupDoneHandler: (() => void) | null = null
let envelopeStateHandler: ((event: Event) => void) | null = null
let desktopScrollbarMedia: MediaQueryList | null = null
let desktopScrollbarMediaHandler: ((event: MediaQueryListEvent) => void) | null = null
const colorMode = useColorMode()
let scrollLocked = false
const waitEnvelopeClose = ref(false)
const useCustomScrollbar = ref(false)
const closedByUserInSession = ref(false)
let modalRequestPromise: Promise<AnnouncementVO[]> | null = null

const forceModalPreview = computed(() => {
  const modalPreview = route.query.modalPreview
  const forceModal = route.query.forceModal
  return modalPreview === '1' || forceModal === '1'
})

const currentAnn = computed(() => modalAnnouncements.value[currentIndex.value] || null)
const hasNext = computed(() => currentIndex.value < modalAnnouncements.value.length - 1)
const previewTheme = computed(() => (colorMode.value === 'dark' ? 'github' : 'default'))
const codeTheme = computed(() => (colorMode.value === 'dark' ? 'github-dark' : 'github'))
const editorTheme = computed(() => (colorMode.value === 'dark' ? 'dark' : 'light'))
const announcementTimeText = computed(() => {
  const source = currentAnn.value?.updateTime || currentAnn.value?.createTime || ''
  return `发布时间：${formatAnnouncementTime(source)}`
})

function sanitize(html: string) {
  return sanitizeHtml(html)
}

function formatAnnouncementTime(value: string) {
  if (!value) {
    return '--'
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

function getDismissedKeys(): Set<string> {
  if (!import.meta.client) {
    return new Set()
  }

  try {
    const parsed = JSON.parse(localStorage.getItem(DISMISSED_STORAGE_KEY) || '[]')
    if (!Array.isArray(parsed)) {
      return new Set()
    }
    return new Set(parsed.map(item => String(item).trim()).filter(item => item.length > 0))
  } catch {
    return new Set()
  }
}

function saveDismissedKeys(keys: Set<string>) {
  if (!import.meta.client) {
    return
  }
  localStorage.setItem(DISMISSED_STORAGE_KEY, JSON.stringify(Array.from(keys).slice(-300)))
}

function getDismissKey(item: AnnouncementVO | null) {
  if (!item) {
    return ''
  }
  return `${item.id}:${item.updateTime || item.createTime || ''}`
}

function dismissCurrent() {
  if (!import.meta.client || !currentAnn.value || forceModalPreview.value) {
    return
  }
  const key = getDismissKey(currentAnn.value)
  if (!key) {
    return
  }
  const dismissed = getDismissedKeys()
  dismissed.add(key)
  saveDismissedKeys(dismissed)
}

function goNext() {
  if (!hasNext.value) {
    return
  }
  currentIndex.value += 1
}

function tryClose() {
  dismissCurrent()
  closedByUserInSession.value = true
  if (!forceModalPreview.value) {
    modalAnnouncements.value = filterModalAnnouncements(modalAnnouncements.value, getDismissedKeys())
  }
  visible.value = false
}

function syncPageScrollLock(lock: boolean) {
  if (!import.meta.client) {
    return
  }

  if (lock) {
    if (scrollLocked) {
      return
    }
    lockScroll()
    document.documentElement.classList.add('notice-modal-scroll-lock')
    document.body.classList.add('notice-modal-scroll-lock')
    scrollLocked = true
    return
  }

  if (!scrollLocked) {
    return
  }

  document.documentElement.classList.remove('notice-modal-scroll-lock')
  document.body.classList.remove('notice-modal-scroll-lock')
  unlockScroll()
  scrollLocked = false
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

function sortAnnouncements(source: AnnouncementVO[]) {
  return [...source].sort((a, b) => {
    if (b.priority !== a.priority) {
      return b.priority - a.priority
    }
    const bTime = Date.parse(b.updateTime || b.createTime || '')
    const aTime = Date.parse(a.updateTime || a.createTime || '')
    if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0
    if (Number.isNaN(aTime)) return 1
    if (Number.isNaN(bTime)) return -1
    return bTime - aTime
  })
}

function filterModalAnnouncements(source: AnnouncementVO[], dismissed: Set<string>) {
  return sortAnnouncements(
    source.filter(item => item.type === 'modal' && !dismissed.has(getDismissKey(item))),
  )
}

interface ModalCachePayload {
  savedAt: number
  items: AnnouncementVO[]
}

function isAnnouncementArray(value: unknown): value is AnnouncementVO[] {
  if (!Array.isArray(value)) {
    return false
  }

  return value.every((item) => {
    if (!item || typeof item !== 'object') {
      return false
    }

    const record = item as Partial<AnnouncementVO>
    return typeof record.id === 'number' && typeof record.type === 'string'
  })
}

function readModalCache(): AnnouncementVO[] {
  if (!import.meta.client) {
    return []
  }

  try {
    const raw = localStorage.getItem(MODAL_CACHE_STORAGE_KEY)
    if (!raw) {
      return []
    }

    const parsed = JSON.parse(raw) as Partial<ModalCachePayload>
    if (typeof parsed.savedAt !== 'number' || Number.isNaN(parsed.savedAt)) {
      return []
    }

    if (Date.now() - parsed.savedAt > MODAL_CACHE_MAX_AGE) {
      return []
    }

    if (!isAnnouncementArray(parsed.items)) {
      return []
    }

    return parsed.items
  } catch {
    return []
  }
}

function saveModalCache(items: AnnouncementVO[]) {
  if (!import.meta.client || items.length === 0) {
    return
  }

  const payload: ModalCachePayload = {
    savedAt: Date.now(),
    items: items.slice(0, 20),
  }

  localStorage.setItem(MODAL_CACHE_STORAGE_KEY, JSON.stringify(payload))
}

async function fetchModalAnnouncements(): Promise<AnnouncementVO[]> {
  const dismissed = forceModalPreview.value ? new Set<string>() : getDismissedKeys()
  let requestError: unknown = null

  for (let attempt = 0; attempt <= FETCH_RETRY_COUNT; attempt += 1) {
    try {
      const byType = await announcementApi.getByType('modal').then(res => res.data || [])
      const filteredByType = filterModalAnnouncements(byType, dismissed)
      if (filteredByType.length > 0) {
        saveModalCache(filteredByType)
        return filteredByType
      }

      const allAnnouncements = await fetchAllAnnouncements()
      const filteredAll = filterModalAnnouncements(allAnnouncements, dismissed)
      if (filteredAll.length > 0) {
        saveModalCache(filteredAll)
      }
      return filteredAll
    } catch (error) {
      requestError = error
      if (attempt < FETCH_RETRY_COUNT && import.meta.client) {
        await sleep(FETCH_RETRY_DELAY * (attempt + 1))
      }
    }
  }

  const cachedAnnouncements = filterModalAnnouncements(readModalCache(), dismissed)
  if (cachedAnnouncements.length > 0) {
    return cachedAnnouncements
  }

  if (import.meta.dev && requestError) {
    console.warn('[AnnouncementModal] 获取公告失败，且无可用缓存', requestError)
  }

  return []
}

function applyModalAnnouncements(announcements: AnnouncementVO[]) {
  modalAnnouncements.value = announcements
  if (currentIndex.value >= announcements.length) {
    currentIndex.value = 0
  }
}

function ensureModalAnnouncementsLoaded(forceRefresh = false) {
  if (!forceRefresh && modalAnnouncements.value.length > 0) {
    return Promise.resolve(modalAnnouncements.value)
  }

  if (!forceRefresh && modalRequestPromise) {
    return modalRequestPromise
  }

  modalRequestPromise = fetchModalAnnouncements()
    .then((announcements) => {
      applyModalAnnouncements(announcements)
      return announcements
    })
    .finally(() => {
      modalRequestPromise = null
    })

  return modalRequestPromise
}

function revealIfReady() {
  if (modalAnnouncements.value.length === 0) {
    return
  }
  if (closedByUserInSession.value && !forceModalPreview.value) {
    return
  }
  if (waitEnvelopeClose.value) {
    return
  }
  currentIndex.value = 0
  visible.value = true
}

function hasStartupDone() {
  if (!import.meta.client) {
    return false
  }
  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  return Boolean(runtimeWindow.__weblogStartupDone)
}

function syncCustomScrollbarMode() {
  if (!import.meta.client || !desktopScrollbarMedia) {
    return
  }
  useCustomScrollbar.value = desktopScrollbarMedia.matches
}

onMounted(async () => {
  if (import.meta.client) {
    desktopScrollbarMedia = window.matchMedia(DESKTOP_SCROLLBAR_MEDIA)
    syncCustomScrollbarMode()
    desktopScrollbarMediaHandler = () => {
      syncCustomScrollbarMode()
    }
    desktopScrollbarMedia.addEventListener('change', desktopScrollbarMediaHandler)
  }

  await ensureModalAnnouncementsLoaded()

  if (import.meta.client && !forceModalPreview.value) {
    const runtimeWindow = window as Window & {
      __weblogEnvelopeAnnouncementActive?: boolean
      __weblogEnvelopeAnnouncementPending?: boolean
    }
    waitEnvelopeClose.value = Boolean(
      runtimeWindow.__weblogEnvelopeAnnouncementActive || runtimeWindow.__weblogEnvelopeAnnouncementPending,
    )

    envelopeStateHandler = (event: Event) => {
      const customEvent = event as CustomEvent<{ active?: boolean, closed?: boolean, pending?: boolean }>
      const detail = customEvent.detail
      if (!detail) {
        return
      }

      if (detail.pending) {
        waitEnvelopeClose.value = true
        visible.value = false
        return
      }

      if (detail.active) {
        waitEnvelopeClose.value = true
        visible.value = false
        return
      }

      if (detail.closed || detail.active === false) {
        waitEnvelopeClose.value = false
        if (modalAnnouncements.value.length > 0) {
          revealIfReady()
          return
        }

        void ensureModalAnnouncementsLoaded(true).then(() => {
          revealIfReady()
        })
      }
    }

    window.addEventListener(ENVELOPE_STATE_EVENT, envelopeStateHandler)
  }

  if (hasStartupDone()) {
    revealIfReady()
    return
  }

  startupDoneHandler = () => {
    revealIfReady()
  }

  window.addEventListener(STARTUP_DONE_EVENT, startupDoneHandler, { once: true })
})

watch(visible, (value) => {
  syncPageScrollLock(value)
})

onUnmounted(() => {
  syncPageScrollLock(false)
  if (!import.meta.client) {
    return
  }

  if (startupDoneHandler) {
    window.removeEventListener(STARTUP_DONE_EVENT, startupDoneHandler)
    startupDoneHandler = null
  }

  if (envelopeStateHandler) {
    window.removeEventListener(ENVELOPE_STATE_EVENT, envelopeStateHandler)
    envelopeStateHandler = null
  }

  if (desktopScrollbarMedia && desktopScrollbarMediaHandler) {
    desktopScrollbarMedia.removeEventListener('change', desktopScrollbarMediaHandler)
  }
  desktopScrollbarMedia = null
  desktopScrollbarMediaHandler = null
})
</script>

<style scoped lang="scss">
.notice-modal-fade-enter-active,
.notice-modal-fade-leave-active,
.notice-modal-fade-appear-active {
  transition: opacity 0.25s;

  .notice-modal-card {
    transition: transform 0.25s;
  }
}

.notice-modal-fade-enter-from,
.notice-modal-fade-leave-to,
.notice-modal-fade-appear-from {
  opacity: 0;

  .notice-modal-card {
    transform: translateY(20px) scale(0.96);
  }
}

.notice-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(16, 22, 34, 0.5);
  backdrop-filter: blur(8px);
  overscroll-behavior: contain;

  .dark & {
    background: rgba(10, 12, 18, 0.66);
  }
}

.notice-modal-card {
  width: min(560px, 100%);
  border-radius: 18px;
  background: linear-gradient(145deg, #ffffff 0%, #f5f8ff 100%);
  border: 1px solid rgba(59, 130, 246, 0.14);
  box-shadow:
    0 24px 56px rgba(15, 23, 42, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  overflow: hidden;

  .dark & {
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    border-color: rgba(148, 163, 184, 0.2);
    box-shadow:
      0 26px 60px rgba(2, 6, 23, 0.58),
      inset 0 1px 0 rgba(148, 163, 184, 0.2);
  }
}

.notice-modal-header {
  display: flex;
  align-items: center;
  padding: 1rem 1rem 0.25rem;
}

.notice-modal-title {
  margin: 0;
  font-size: 1.04rem;
  line-height: 1.4;
  color: #0f172a;
  font-weight: 700;

  .dark & {
    color: #e2e8f0;
  }
}

.notice-modal-content {
  padding: 0.95rem calc(1rem + 12px) 0.95rem 1rem;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(148, 163, 184, 0.24);
  color: #334155;
  line-height: 1.75;
  max-height: 52vh;
  overflow: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  box-sizing: border-box;

  .dark & {
    color: #cbd5e1;
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    border-color: rgba(148, 163, 184, 0.2);
  }

}

:global(html.notice-modal-scroll-lock),
:global(body.notice-modal-scroll-lock) {
  overflow: hidden;
}

.notice-md-preview {
  :deep(.md-editor),
  :deep(.md-editor-dark),
  :deep(.md-editor-light),
  :deep(.md-editor-preview-wrapper) {
    border: none;
    background: transparent !important;
    padding: 0;
  }

  :deep(.md-editor-preview) {
    background: transparent !important;
    --md-theme-bg-color: transparent;
    --md-theme-bg-color-inset: rgba(15, 23, 42, 0.06);
    color: inherit !important;
    font-size: 0.98rem;
    line-height: 1.78;
    font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
  }

  :deep(.md-editor-preview *) {
    color: inherit;
  }

  .dark & {
    :deep(.md-editor),
    :deep(.md-editor-dark),
    :deep(.md-editor-preview-wrapper),
    :deep(.md-editor-preview),
    :deep(.md-editor-preview > *) {
      background:
        radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
        radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
        linear-gradient(180deg, #171b20, #101215) !important;
      background-color: transparent !important;
    }

    :deep(.md-editor-preview [style*="background"]),
    :deep(.md-editor-preview [style*="background-color"]) {
      background: transparent !important;
      background-color: transparent !important;
    }

    :deep(.md-editor-preview *:not(pre):not(code):not(.hljs):not(.hljs *)) {
      background-color: transparent !important;
    }

    :deep(.md-editor-preview pre),
    :deep(.md-editor-preview .hljs),
    :deep(.md-editor-preview .code-block-wrapper) {
      background: rgba(15, 23, 42, 0.42) !important;
    }
  }

  :deep(.md-editor-preview pre),
  :deep(.md-editor-preview .hljs),
  :deep(.md-editor-preview .code-block-wrapper) {
    background: rgba(15, 23, 42, 0.06) !important;

    .dark & {
      background: rgba(15, 23, 42, 0.42) !important;
    }
  }

  :deep(.md-editor-preview pre *),
  :deep(.md-editor-preview .hljs *),
  :deep(.md-editor-preview .code-block-wrapper *),
  :deep(.md-editor-preview pre code),
  :deep(.md-editor-preview code.hljs),
  :deep(.md-editor-preview code.hljs *) {
    background: transparent !important;
    color: inherit !important;
  }

  :deep(.md-editor-preview p) {
    margin: 0 0 0.85rem;
  }

  :deep(.md-editor-preview p:last-child) {
    margin-bottom: 0;
  }

  :deep(.md-editor-preview h1),
  :deep(.md-editor-preview h2),
  :deep(.md-editor-preview h3),
  :deep(.md-editor-preview h4) {
    margin: 1rem 0 0.65rem;
    line-height: 1.35;
  }

  :deep(.md-editor-preview ul),
  :deep(.md-editor-preview ol) {
    margin: 0.3rem 0 0.85rem;
    padding-left: 1.3rem;
  }

  :deep(.md-editor-preview blockquote) {
    margin: 0.75rem 0;
    padding: 0.58rem 0.78rem;
    border-radius: 8px;
    border-left-color: rgba(59, 130, 246, 0.58);
    background: rgba(59, 130, 246, 0.08);

    .dark & {
      border-left-color: rgba(96, 165, 250, 0.62);
      background: rgba(59, 130, 246, 0.14);
    }
  }

  :deep(.md-editor-preview pre) {
    margin: 0.9rem 0;
    padding: 0.78rem 0.88rem;
    border-radius: 10px;
  }

  :deep(.md-editor-preview pre code) {
    background: transparent !important;
    color: inherit;
  }

  :deep(.md-editor-preview .hljs),
  :deep(.md-editor-preview .hljs code) {
    background: transparent !important;
    color: inherit;
  }

  :deep(.md-editor-preview code:not(pre code)) {
    background: rgba(148, 163, 184, 0.16) !important;
    border-radius: 6px;
    padding: 0.08rem 0.32rem;

    .dark & {
      background: rgba(148, 163, 184, 0.22) !important;
    }
  }

  :deep(.md-editor-preview [style*="background"]),
  :deep(.md-editor-preview [style*="background-color"]) {
    background: transparent !important;
    background-color: transparent !important;
  }

  :deep(.md-editor-preview table) {
    margin: 0.85rem 0;
  }

  :deep(.md-editor-preview table th),
  :deep(.md-editor-preview table td) {
    padding: 0.45rem 0.58rem;
    border-color: rgba(148, 163, 184, 0.3);

    .dark & {
      border-color: rgba(148, 163, 184, 0.25);
    }
  }
}

.notice-md-fallback {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.78;
  font-size: 0.98rem;
  color: inherit;

  :deep(p) {
    margin: 0 0 0.85rem;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }
}

.notice-modal-footer {
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  padding: 0.85rem 1rem 1rem;

  .dark & {
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    border-top-color: rgba(148, 163, 184, 0.2);
  }
}

.notice-modal-time {
  margin: 0 0 0.75rem;
  color: #64748b;
  font-size: 0.82rem;

  .dark & {
    color: #94a3b8;
  }
}

.notice-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.notice-modal-next,
.notice-modal-action-close {
  height: 2rem;
  padding: 0 0.8rem;
  border-radius: 999px;
  border: 0;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.22s ease;
}

.notice-modal-next {
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.14);

  &:hover {
    background: rgba(59, 130, 246, 0.22);
    transform: translateY(-1px);
  }

  .dark & {
    color: #bfdbfe;
    background: rgba(59, 130, 246, 0.2);

    &:hover {
      background: rgba(59, 130, 246, 0.3);
    }
  }
}

.notice-modal-action-close {
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 20px rgba(37, 99, 235, 0.3);
  }

  .dark & {
    background: linear-gradient(135deg, #3b82f6, #2563eb);
  }
}

@media (max-width: $breakpoint-sm) {
  .notice-modal-card {
    width: 100%;
    border-radius: 16px;
  }

  .notice-modal-content {
    max-height: 58vh;
  }
}
</style>
