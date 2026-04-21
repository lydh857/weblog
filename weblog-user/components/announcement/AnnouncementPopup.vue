<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible && currentAnn" class="popup-overlay">
        <div class="popup-envelope-wrap">
          <div
            class="popup-envelope"
            :class="{ 'is-open': envelopeOpen }"
            @mouseenter="handleEnvelopeMouseEnter"
            @mouseleave="handleEnvelopeMouseLeave"
            @click="toggleEnvelopeByTap"
          >
            <article class="popup-letter" role="dialog" aria-modal="true" :aria-label="`信封公告：${currentAnn.title}`">
              <button
                v-if="currentAnn.isClosable"
                type="button"
                class="popup-close"
                aria-label="关闭"
                @pointerdown="handleCloseAction"
                @click="handleCloseAction"
              >
                <Icon name="heroicons:x-mark-20-solid" size="16" />
              </button>

              <h3 class="popup-title">{{ currentAnn.title }}</h3>
              <div class="popup-body">
                <ClientOnly>
                  <MdPreview
                    editor-id="announcement-envelope-preview"
                    :model-value="currentAnn.content"
                    :sanitize="sanitize"
                    :theme="editorTheme"
                    :preview-theme="previewTheme"
                    :code-theme="codeTheme"
                    :code-foldable="false"
                    :show-code-row-number="true"
                    :no-mermaid="true"
                    :no-katex="true"
                    class="popup-md-preview"
                  />
                  <template #fallback>
                    <!-- 已经过 sanitizeHtml 净化 -->
                    <!-- eslint-disable-next-line vue/no-v-html -->
                    <div class="popup-md-fallback" v-html="sanitize(currentAnn.content)" />
                  </template>
                </ClientOnly>
              </div>
              <p class="popup-time">{{ announcementTimeText }}</p>
              <p class="popup-signature">zhhhkl</p>

              <footer class="popup-meta">
                <span class="popup-count">{{ currentIndex + 1 }} / {{ popupAnnouncements.length }}</span>
                <div class="popup-actions">
                  <button
                    v-if="hasNext"
                    type="button"
                    class="popup-next"
                    @click.stop="goNext"
                  >
                    下一条
                  </button>
                </div>
              </footer>
            </article>

            <button type="button" class="popup-seal" aria-label="打开公告" @click.stop="toggleEnvelopeByTap">
              SMKY
            </button>

            <div class="envelope-face envelope-top" />
            <div class="envelope-face envelope-left" />
            <div class="envelope-face envelope-right" />
            <div class="envelope-face envelope-bottom" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { announcementApi, type AnnouncementVO } from '~/api/marketing/ad'
import { fetchAllAnnouncements } from '~/composables/announcement/useAnnouncementRequestCache'
import { lockScroll, unlockScroll } from '~/composables/layout/useScrollLock'
import { sanitizeHtml } from '~/utils/security/xss'

const visible = ref(false)
const popupAnnouncements = ref<AnnouncementVO[]>([])
const currentIndex = ref(0)
const envelopeOpen = ref(false)
const closedByUserInSession = ref(false)
const route = useRoute()
const colorMode = useColorMode()

const DISMISSED_STORAGE_KEY = 'dismissed_announcements_envelope_v2'
const POPUP_CACHE_STORAGE_KEY = 'announcement_popup_cache_v1'
const ENVELOPE_STATE_EVENT = 'weblog:envelope-popup-state'
const POPUP_CACHE_MAX_AGE = 1000 * 60 * 30
const FETCH_RETRY_COUNT = 2
const FETCH_RETRY_DELAY = 260
const ENVELOPE_HOVER_OPEN_DELAY_MS = 320

let visibilityHandlerAttached = false
let visibilityChangeHandler: (() => void) | null = null
let startupDoneHandlerAttached = false
let startupDoneHandler: (() => void) | null = null
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const isStartupDone = ref(false)
let popupRequestPromise: Promise<AnnouncementVO[]> | null = null
let popupIdlePrefetchCancel: (() => void) | null = null
let interactionPrefetchCleanup: (() => void) | null = null
let envelopeHoverOpenTimer: ReturnType<typeof window.setTimeout> | null = null
let scrollLocked = false

function emitEnvelopeState(active: boolean, closed = false, pending = false) {
  if (!import.meta.client) {
    return
  }

  const runtimeWindow = window as Window & {
    __weblogEnvelopeAnnouncementActive?: boolean
    __weblogEnvelopeAnnouncementPending?: boolean
  }
  runtimeWindow.__weblogEnvelopeAnnouncementActive = active
  runtimeWindow.__weblogEnvelopeAnnouncementPending = pending
  window.dispatchEvent(new CustomEvent(ENVELOPE_STATE_EVENT, {
    detail: { active, closed, pending },
  }))
}

function clearEnvelopeHoverOpenTimer() {
  if (!import.meta.client || !envelopeHoverOpenTimer) {
    return
  }

  window.clearTimeout(envelopeHoverOpenTimer)
  envelopeHoverOpenTimer = null
}

const currentAnn = computed(() => popupAnnouncements.value[currentIndex.value] || null)
const hasNext = computed(() => currentIndex.value < popupAnnouncements.value.length - 1)
const forcePopupPreview = computed(() => {
  const popupPreview = route.query.popupPreview
  const forcePopup = route.query.forcePopup

  return popupPreview === '1' || forcePopup === '1'
})
const previewTheme = computed(() => (colorMode.value === 'dark' ? 'github' : 'default'))
const codeTheme = computed(() => (colorMode.value === 'dark' ? 'atom' : 'atom'))
const editorTheme = computed(() => (colorMode.value === 'dark' ? 'dark' : 'light'))
const announcementTimeText = computed(() => {
  const source = currentAnn.value?.updateTime || currentAnn.value?.createTime || ''
  return `时间：${formatAnnouncementTime(source)}`
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

function handleEnvelopeMouseEnter() {
  if (!import.meta.client || !window.matchMedia('(hover: hover)').matches) {
    return
  }

  clearEnvelopeHoverOpenTimer()
  envelopeHoverOpenTimer = window.setTimeout(() => {
    envelopeOpen.value = true
    envelopeHoverOpenTimer = null
  }, ENVELOPE_HOVER_OPEN_DELAY_MS)
}

function handleEnvelopeMouseLeave() {
  if (!import.meta.client || !window.matchMedia('(hover: hover)').matches) {
    return
  }

  clearEnvelopeHoverOpenTimer()
  envelopeOpen.value = false
}

function toggleEnvelopeByTap() {
  if (!import.meta.client) {
    return
  }

  if (window.matchMedia('(hover: hover)').matches) {
    return
  }

  if (envelopeOpen.value) {
    return
  }

  clearEnvelopeHoverOpenTimer()
  envelopeOpen.value = true
}

function showCurrentAnnouncement() {
  clearEnvelopeHoverOpenTimer()
  envelopeOpen.value = false
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
    scrollLocked = true
    return
  }

  if (!scrollLocked) {
    return
  }

  unlockScroll()
  scrollLocked = false
}

function hasStartupDone() {
  if (!import.meta.client) {
    return false
  }

  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  return Boolean(runtimeWindow.__weblogStartupDone)
}

function revealPopupIfReady() {
  if (!isStartupDone.value || popupAnnouncements.value.length === 0) {
    return
  }

  if (closedByUserInSession.value && !forcePopupPreview.value) {
    return
  }

  currentIndex.value = 0
  visible.value = true
  emitEnvelopeState(true)
  showCurrentAnnouncement()
}

function runWhenBrowserIdle(task: () => void, fallbackDelay = 360): () => void {
  if (!import.meta.client) {
    return () => {}
  }

  if (typeof window.requestIdleCallback === 'function') {
    const idleId = window.requestIdleCallback(() => {
      task()
    }, { timeout: 1400 })
    return () => {
      window.cancelIdleCallback(idleId)
    }
  }

  const timer = window.setTimeout(task, fallbackDelay)
  return () => {
    window.clearTimeout(timer)
  }
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

    const keys = parsed
      .map(value => String(value).trim())
      .filter(value => value.length > 0)

    return new Set(keys)
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

function getAnnouncementDismissKey(item: AnnouncementVO | null) {
  if (!item) {
    return ''
  }

  return `${item.id}:${item.updateTime || item.createTime || ''}`
}

function dismiss(item: AnnouncementVO | null) {
  if (!import.meta.client || !item) {
    return
  }

  const dismissKey = getAnnouncementDismissKey(item)
  if (!dismissKey) {
    return
  }

  const keys = getDismissedKeys()
  if (keys.has(dismissKey)) {
    return
  }

  keys.add(dismissKey)
  saveDismissedKeys(keys)
}

interface PopupCachePayload {
  savedAt: number
  items: AnnouncementVO[]
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
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

function readPopupCache(): AnnouncementVO[] {
  if (!import.meta.client) {
    return []
  }

  try {
    const raw = localStorage.getItem(POPUP_CACHE_STORAGE_KEY)
    if (!raw) {
      return []
    }

    const parsed = JSON.parse(raw) as Partial<PopupCachePayload>
    if (typeof parsed.savedAt !== 'number' || Number.isNaN(parsed.savedAt)) {
      return []
    }

    if (Date.now() - parsed.savedAt > POPUP_CACHE_MAX_AGE) {
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

function savePopupCache(items: AnnouncementVO[]) {
  if (!import.meta.client || items.length === 0) {
    return
  }

  const payload: PopupCachePayload = {
    savedAt: Date.now(),
    items: items.slice(0, 20),
  }

  localStorage.setItem(POPUP_CACHE_STORAGE_KEY, JSON.stringify(payload))
}

function goNext() {
  if (!hasNext.value) {
    return
  }

  currentIndex.value += 1
  showCurrentAnnouncement()
}

function tryClose() {
  if (!currentAnn.value?.isClosable) {
    return
  }

  if (!forcePopupPreview.value) {
    dismiss(currentAnn.value)
  }

  clearEnvelopeHoverOpenTimer()
  closedByUserInSession.value = true
  visible.value = false
  envelopeOpen.value = false
  emitEnvelopeState(false, true)
}

function handleCloseAction(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  tryClose()
}

function filterPopupAnnouncements(source: AnnouncementVO[], dismissed: Set<string>): AnnouncementVO[] {
  return source
    .filter(item => item.type === 'envelope' && !dismissed.has(getAnnouncementDismissKey(item)))
    .sort((a, b) => {
      if (b.priority !== a.priority) {
        return b.priority - a.priority
      }

      const bTime = Date.parse(b.updateTime || b.createTime || '')
      const aTime = Date.parse(a.updateTime || a.createTime || '')
      if (Number.isNaN(aTime) && Number.isNaN(bTime)) {
        return 0
      }
      if (Number.isNaN(aTime)) {
        return 1
      }
      if (Number.isNaN(bTime)) {
        return -1
      }
      return bTime - aTime
    })
}

async function fetchPopupAnnouncements(): Promise<AnnouncementVO[]> {
  const dismissed = forcePopupPreview.value ? new Set<string>() : getDismissedKeys()

  let requestError: unknown = null

  for (let attempt = 0; attempt <= FETCH_RETRY_COUNT; attempt += 1) {
    try {
      const byType = await announcementApi.getByType('envelope').then(res => res.data || [])
      const filteredByType = filterPopupAnnouncements(byType, dismissed)
      if (filteredByType.length > 0) {
        savePopupCache(filteredByType)
        return filteredByType
      }

      const allAnnouncements = await fetchAllAnnouncements()
      const filteredAll = filterPopupAnnouncements(allAnnouncements, dismissed)
      if (filteredAll.length > 0) {
        savePopupCache(filteredAll)
      }
      return filteredAll
    } catch (error) {
      requestError = error
      if (attempt < FETCH_RETRY_COUNT && import.meta.client) {
        await sleep(FETCH_RETRY_DELAY * (attempt + 1))
      }
    }
  }

  const cachedAnnouncements = filterPopupAnnouncements(readPopupCache(), dismissed)
  if (cachedAnnouncements.length > 0) {
    return cachedAnnouncements
  }

  if (import.meta.dev && requestError) {
    console.warn('[AnnouncementPopup] 获取公告失败，且无可用缓存', requestError)
  }

  return []
}

function applyPopupAnnouncements(announcements: AnnouncementVO[]) {
  popupAnnouncements.value = announcements

  if (announcements.length > 0 && !closedByUserInSession.value) {
    emitEnvelopeState(true, false, false)
  } else {
    emitEnvelopeState(false, false, false)
  }

  revealPopupIfReady()
}

function prefetchPopupAnnouncements() {
  if (!import.meta.client) {
    return Promise.resolve<AnnouncementVO[]>([])
  }

  if (popupRequestPromise) {
    return popupRequestPromise
  }

  popupRequestPromise = fetchPopupAnnouncements()
    .then((announcements) => {
      applyPopupAnnouncements(announcements)
      return announcements
    })
    .finally(() => {
      popupRequestPromise = null
    })

  return popupRequestPromise
}

function clearInteractionPrefetchListeners() {
  if (!interactionPrefetchCleanup) {
    return
  }

  interactionPrefetchCleanup()
  interactionPrefetchCleanup = null
}

function setupInteractionPrefetch() {
  if (!import.meta.client || interactionPrefetchCleanup) {
    return
  }

  // 首次真实交互时提权触发请求，避免一直等待 idle 导致公告感知滞后。
  const events: Array<keyof WindowEventMap> = ['pointerdown', 'keydown', 'touchstart']
  const boostFetch = () => {
    clearInteractionPrefetchListeners()
    if (popupIdlePrefetchCancel) {
      popupIdlePrefetchCancel()
      popupIdlePrefetchCancel = null
    }
    void prefetchPopupAnnouncements()
  }

  events.forEach((eventName) => {
    window.addEventListener(eventName, boostFetch, { passive: true, once: true, capture: true })
  })

  interactionPrefetchCleanup = () => {
    events.forEach((eventName) => {
      window.removeEventListener(eventName, boostFetch, true)
    })
  }
}

onMounted(async () => {
  emitEnvelopeState(true, false, true)

  isStartupDone.value = hasStartupDone()

  if (import.meta.client && !isStartupDone.value && !startupDoneHandlerAttached) {
    startupDoneHandler = () => {
      isStartupDone.value = true
      revealPopupIfReady()
    }
    window.addEventListener(STARTUP_DONE_EVENT, startupDoneHandler)
    startupDoneHandlerAttached = true
  }

  const dismissed = forcePopupPreview.value ? new Set<string>() : getDismissedKeys()
  const cachedAnnouncements = filterPopupAnnouncements(readPopupCache(), dismissed)
  if (cachedAnnouncements.length > 0) {
    applyPopupAnnouncements(cachedAnnouncements)
  }

  // 公告请求属于非首屏关键路径：先空闲预取，减少与首屏资源竞争。
  popupIdlePrefetchCancel = runWhenBrowserIdle(() => {
    void prefetchPopupAnnouncements()
    popupIdlePrefetchCancel = null
  }, 420)
  setupInteractionPrefetch()
  revealPopupIfReady()

  if (import.meta.client && !visibilityHandlerAttached) {
    visibilityChangeHandler = () => {
      if (document.visibilityState !== 'visible' || visible.value) {
        return
      }

      prefetchPopupAnnouncements().then((announcements) => {
        if (announcements.length === 0) {
          return
        }
      })
    }

    document.addEventListener('visibilitychange', visibilityChangeHandler)
    visibilityHandlerAttached = true
  }
})

watch(visible, (value) => {
  syncPageScrollLock(value)
})

onUnmounted(() => {
  syncPageScrollLock(false)

  if (import.meta.client && visibilityChangeHandler && visibilityHandlerAttached) {
    document.removeEventListener('visibilitychange', visibilityChangeHandler)
    visibilityChangeHandler = null
    visibilityHandlerAttached = false
  }

  if (import.meta.client && startupDoneHandler && startupDoneHandlerAttached) {
    window.removeEventListener(STARTUP_DONE_EVENT, startupDoneHandler)
    startupDoneHandler = null
    startupDoneHandlerAttached = false
  }

  if (popupIdlePrefetchCancel) {
    popupIdlePrefetchCancel()
    popupIdlePrefetchCancel = null
  }

  clearEnvelopeHoverOpenTimer()

  clearInteractionPrefetchListeners()

  emitEnvelopeState(false, false, false)
})
</script>

<style scoped lang="scss">
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .popup-envelope-wrap {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .popup-envelope-wrap {
    transform: translateY(20px) scale(0.96);
  }
}

.popup-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

.popup-envelope-wrap {
  width: min(620px, 100%);
  display: flex;
  justify-content: center;
}

.popup-envelope {
  --letter-closed-shift: 20%;
  --letter-open-shift: 21%;
  --letter-clip-safe: 4px;

  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 300px;
  cursor: pointer;
  overflow: visible;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F5F5;
  transition: all 0.7s ease;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.32);
  transform: translateZ(0);
  will-change: transform;
}

.popup-letter {
  position: absolute;
  inset: 0;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  justify-content: flex-start;
  padding: 1rem 1.7rem 1.85rem;
  transform: translateY(var(--letter-closed-shift));
  clip-path: inset(0 2px calc(var(--letter-closed-shift) + var(--letter-clip-safe)) 0);
  z-index: 12;
  opacity: 1;
  pointer-events: none;
  transition:
    transform 0.72s cubic-bezier(0.22, 0.72, 0.22, 1),
    opacity 0.25s ease;
  will-change: transform, opacity;
  box-shadow: none;

  .dark & {
    background: #f5f5f5;
  }
}

.popup-envelope.is-open .popup-letter {
  transform: translateY(calc(var(--letter-open-shift) * -1));
  clip-path: inset(0 2px calc(var(--letter-open-shift) + var(--letter-clip-safe)) 0);
  z-index: 14;
  opacity: 1;
  pointer-events: auto;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.2);
}

.popup-title {
  margin: 0.45rem 0 0.45rem;
  max-width: min(84%, 520px);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(1.65rem, 2.4vw, 2.75rem);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: 0.4px;
  color: #6b7280;
}

.popup-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: #334155;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: none;
  z-index: 25;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-10px);
  transition: opacity 0.2s ease, transform 0.2s ease, background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  cursor: pointer;

  &:hover {
    background: #0f172a;
    color: #f8fafc;
    border-color: rgba(15, 23, 42, 0.4);
    box-shadow: 0 8px 16px rgba(15, 23, 42, 0.32);
    transform: scale(1.08);
  }

  &:focus-visible {
    outline: 2px solid rgba(59, 130, 246, 0.7);
    outline-offset: 1px;
  }
}

.popup-envelope.is-open .popup-close {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.popup-body {
  width: min(84%, 500px);
  max-height: 46%;
  overflow-y: auto;
  font-size: clamp(0.95rem, 1.5vw, 1.1rem);
  line-height: 1.55;
  color: #374151;
  background: #f5f5f5;

  :deep(a) {
    color: #2563eb;
    text-decoration: underline;
  }

  :deep(p) {
    margin: 0;
    background: #f5f5f5;
  }

  :deep(p + p) {
    margin-top: 0.5rem;
  }

  :deep(.md-editor),
  :deep(.md-editor-preview-wrapper),
  :deep(.md-editor-preview),
  :deep(.md-editor-preview *),
  :deep(pre),
  :deep(code),
  :deep(blockquote),
  :deep(table),
  :deep(thead),
  :deep(tbody),
  :deep(tr),
  :deep(td),
  :deep(th),
  :deep(ul),
  :deep(ol),
  :deep(li) {
    background: #f5f5f5 !important;
    background-color: #f5f5f5 !important;
  }
}

.popup-time {
  width: min(84%, 500px);
  margin: 0.5rem 0 0;
  font-size: 0.8rem;
  color: #6b7280;
}

.popup-signature {
  width: min(84%, 500px);
  margin: 0.3rem 0 0;
  font-size: 0.95rem;
  letter-spacing: 0.4px;
  color: #334155;
}

.popup-meta {
  width: min(84%, 500px);
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 0.55rem;
}

.popup-count {
  font-size: 0.78rem;
  color: #6b7280;
}

.popup-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.popup-next {
  border: 1px solid transparent;
  min-height: 30px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  cursor: pointer;
  font-size: 0.76rem;
  font-weight: 600;
  padding: 0 0.6rem;
  background: rgba(100, 116, 139, 0.12);
  color: #475569;

  &:hover {
    background: rgba(148, 163, 184, 0.3);
  }
}

.popup-seal {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 56px;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border: 3px solid #881337;
  border-radius: 999px;
  background: #fb7185;
  color: #7f1d1d;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  clip-path: polygon(50% 0%, 80% 10%, 100% 35%, 100% 70%, 80% 90%, 50% 100%, 20% 90%, 0% 70%, 0% 35%, 20% 10%);
  transition: transform 1s ease, opacity 1s ease;
  z-index: 40;
  will-change: transform, opacity;
  box-shadow: 0 8px 16px rgba(136, 19, 55, 0.35);
}

.popup-envelope.is-open .popup-seal {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0) rotate(180deg);
  pointer-events: none;
}

.envelope-face {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transition: all 0.45s ease;
}

.envelope-top {
  background: #262626;
  clip-path: polygon(50% 50%, 100% 0, 0 0);
  z-index: 35;
  transition: clip-path 0.55s ease;
}

.envelope-left {
  background: #171717;
  clip-path: polygon(50% 50%, 0 0, 0 100%);
  z-index: 30;
}

.envelope-right {
  background: #262626;
  clip-path: polygon(50% 50%, 100% 0, 100% 100%);
  z-index: 31;
}

.envelope-bottom {
  background: #171717;
  clip-path: polygon(50% 50%, 100% 100%, 0 100%);
  z-index: 29;
}

.popup-envelope.is-open .envelope-top {
  clip-path: polygon(50% 0%, 100% 0, 0 0);
  transition-duration: 0.1s;
}

@media (max-width: $breakpoint-md) {
  .popup-overlay {
    align-items: center;
    justify-content: center;
    padding: 0.75rem;
  }

  .popup-envelope {
    --letter-open-shift: 23%;
    min-height: 248px;
    aspect-ratio: 16 / 9;
  }

  .popup-letter {
    padding: 0.7rem 0.75rem 1rem;
  }

  .popup-title,
  .popup-body,
  .popup-time,
  .popup-signature,
  .popup-meta {
    width: min(90%, 460px);
    max-width: min(90%, 460px);
  }

  .popup-title {
    margin-top: 0.2rem;
    margin-bottom: 0.3rem;
    font-size: 1.15rem;
    line-height: 1.16;
  }

  .popup-body {
    max-height: 56%;
    font-size: 0.84rem;
    line-height: 1.45;
  }

  .popup-time {
    margin-top: 0.35rem;
    font-size: 0.72rem;
  }

  .popup-signature {
    margin-top: 0.2rem;
    font-size: 0.82rem;
  }

  .popup-meta {
    flex-direction: column;
    align-items: stretch;
    gap: 0.35rem;
    padding-top: 0.35rem;
  }

  .popup-count {
    text-align: left;
    font-size: 0.72rem;
  }

  .popup-actions {
    justify-content: flex-end;
  }

  .popup-next {
    min-height: 26px;
    font-size: 0.7rem;
    padding: 0 0.52rem;
  }

}

@media (prefers-reduced-motion: reduce) {
  .modal-fade-enter-active,
  .modal-fade-leave-active,
  .modal-fade-appear-active,
  .modal-fade-enter-active .popup-envelope-wrap,
  .modal-fade-leave-active .popup-envelope-wrap,
  .modal-fade-appear-active .popup-envelope-wrap,
  .popup-letter,
  .popup-seal,
  .envelope-face {
    transition: none !important;
  }

  .popup-envelope.is-open .popup-letter {
    transform: translateY(0);
  }
}
</style>
