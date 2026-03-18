<template>
  <Teleport to="body">
    <Transition name="popup-overlay-fade" appear>
      <div v-if="visible && currentAnn" class="popup-overlay" @click.self="tryClose">
        <button
          v-if="currentAnn.isClosable"
          type="button"
          class="popup-overlay-close"
          aria-label="关闭公告弹窗"
          @click.stop="tryClose"
        >
          <Icon name="heroicons:x-mark-20-solid" size="20" />
        </button>
        <div class="popup-envelope-wrap">
          <div
            class="popup-envelope"
            :class="{ 'is-open': envelopeOpen }"
            @mouseenter="handleEnvelopeMouseEnter"
            @mouseleave="handleEnvelopeMouseLeave"
            @click="toggleEnvelopeByTap"
          >
            <article class="popup-letter" role="dialog" aria-modal="true" :aria-label="`弹窗公告：${currentAnn.title}`">
              <button
                v-if="currentAnn.isClosable"
                type="button"
                class="popup-close"
                aria-label="关闭"
                @click.stop="tryClose"
              >
                <Icon name="heroicons:x-mark-20-solid" size="16" />
              </button>

              <h3 class="popup-title">{{ currentAnn.title }}</h3>
              <div class="popup-body" v-html="sanitize(currentAnn.content)" />
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
import { announcementApi, type AnnouncementVO } from '~/api/ad'
import { sanitizeHtml } from '~/utils/xss'

const visible = ref(false)
const popupAnnouncements = ref<AnnouncementVO[]>([])
const currentIndex = ref(0)
const envelopeOpen = ref(false)
const route = useRoute()

const DISMISSED_STORAGE_KEY = 'dismissed_announcements_envelope_v2'
const POPUP_CACHE_STORAGE_KEY = 'announcement_popup_cache_v1'
const POPUP_CACHE_MAX_AGE = 1000 * 60 * 30
const FETCH_RETRY_COUNT = 2
const FETCH_RETRY_DELAY = 260

let visibilityHandlerAttached = false
let visibilityChangeHandler: (() => void) | null = null
let startupDoneHandlerAttached = false
let startupDoneHandler: (() => void) | null = null
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const isStartupDone = ref(false)

const currentAnn = computed(() => popupAnnouncements.value[currentIndex.value] || null)
const hasNext = computed(() => currentIndex.value < popupAnnouncements.value.length - 1)
const forcePopupPreview = computed(() => {
  const popupPreview = route.query.popupPreview
  const forcePopup = route.query.forcePopup

  return popupPreview === '1' || forcePopup === '1'
})
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
  envelopeOpen.value = true
}

function handleEnvelopeMouseLeave() {
  envelopeOpen.value = false
}

function toggleEnvelopeByTap() {
  if (!import.meta.client) {
    return
  }

  if (window.matchMedia('(hover: hover)').matches) {
    return
  }

  envelopeOpen.value = !envelopeOpen.value
}

function showCurrentAnnouncement() {
  envelopeOpen.value = false
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

  currentIndex.value = 0
  visible.value = true
  showCurrentAnnouncement()
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

  if (hasNext.value) {
    goNext()
    return
  }

  visible.value = false
  envelopeOpen.value = false
}

function filterPopupAnnouncements(source: AnnouncementVO[], dismissed: Set<string>): AnnouncementVO[] {
  return source
    .filter(item => item.type === 'popup' && !dismissed.has(getAnnouncementDismissKey(item)))
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
      const byType = await announcementApi.getByType('popup').then(res => res.data || [])
      const filteredByType = filterPopupAnnouncements(byType, dismissed)
      if (filteredByType.length > 0) {
        savePopupCache(filteredByType)
        return filteredByType
      }

      const allAnnouncements = await announcementApi.getAll().then(res => res.data || [])
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

onMounted(async () => {
  isStartupDone.value = hasStartupDone()

  if (import.meta.client && !isStartupDone.value && !startupDoneHandlerAttached) {
    startupDoneHandler = () => {
      isStartupDone.value = true
      revealPopupIfReady()
    }
    window.addEventListener(STARTUP_DONE_EVENT, startupDoneHandler)
    startupDoneHandlerAttached = true
  }

  popupAnnouncements.value = await fetchPopupAnnouncements()
  revealPopupIfReady()

  if (import.meta.client && !visibilityHandlerAttached) {
    visibilityChangeHandler = () => {
      if (document.visibilityState !== 'visible' || visible.value) {
        return
      }

      fetchPopupAnnouncements().then((announcements) => {
        popupAnnouncements.value = announcements
        if (popupAnnouncements.value.length === 0) {
          return
        }

        revealPopupIfReady()
      })
    }

    document.addEventListener('visibilitychange', visibilityChangeHandler)
    visibilityHandlerAttached = true
  }
})

onUnmounted(() => {
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
})
</script>

<style scoped lang="scss">
.popup-overlay-fade-enter-active,
.popup-overlay-fade-appear-active,
.popup-overlay-fade-leave-active {
  transition: opacity 0.24s ease;
}

.popup-overlay-fade-enter-from,
.popup-overlay-fade-appear-from,
.popup-overlay-fade-leave-to {
  opacity: 0;
}

.popup-overlay-fade-enter-active .popup-envelope-wrap,
.popup-overlay-fade-appear-active .popup-envelope-wrap {
  animation: popup-envelope-zoom-in 420ms cubic-bezier(0.22, 0.72, 0.22, 1) both;
}

@keyframes popup-envelope-zoom-in {
  from {
    opacity: 0;
    transform: scale(0.92);
  }

  to {
    opacity: 1;
    transform: scale(1);
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
  background:
    radial-gradient(circle at 50% 38%, rgba(148, 163, 184, 0.18), transparent 60%),
    rgba(2, 6, 23, 0.55);
  backdrop-filter: blur(6px);
}

.popup-overlay-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(148, 163, 184, 0.55);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #f8fafc;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 60;
  transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;

  &:hover {
    transform: scale(1.05);
    background: rgba(15, 23, 42, 0.86);
    border-color: rgba(148, 163, 184, 0.8);
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
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.35);
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
  padding: 1.35rem 1.7rem 2rem;
  transform: translateY(var(--letter-closed-shift));
  clip-path: inset(0 2px calc(var(--letter-closed-shift) + var(--letter-clip-safe)) 0);
  z-index: 12;
  opacity: 1;
  pointer-events: none;
  transition:
    transform 0.95s cubic-bezier(0.22, 0.72, 0.22, 1),
    clip-path 0.95s cubic-bezier(0.22, 0.72, 0.22, 1),
    box-shadow 0.4s ease,
    opacity 0.25s ease;
  will-change: transform, clip-path;
  box-shadow: none;

  .dark & {
    background: #e5e7eb;
  }
}

.popup-envelope.is-open .popup-letter {
  transform: translateY(calc(var(--letter-open-shift) * -1));
  clip-path: inset(0 2px calc(var(--letter-open-shift) + var(--letter-clip-safe)) 0);
  z-index: 14;
  opacity: 1;
  pointer-events: auto;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.22);
}

.popup-title {
  margin: 0.8rem 0 0.7rem;
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
  max-height: 37%;
  overflow-y: auto;
  font-size: clamp(0.95rem, 1.5vw, 1.1rem);
  line-height: 1.55;
  color: #374151;

  :deep(a) {
    color: #2563eb;
    text-decoration: underline;
  }

  :deep(p) {
    margin: 0;
  }

  :deep(p + p) {
    margin-top: 0.5rem;
  }
}

.popup-time {
  width: min(84%, 500px);
  margin: 0.8rem 0 0;
  font-size: 0.8rem;
  color: #6b7280;
}

.popup-signature {
  width: min(84%, 500px);
  margin: 0.45rem 0 0;
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
  padding-top: 0.85rem;
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
  transition: all 0.7s ease;
}

.envelope-top {
  background: #262626;
  clip-path: polygon(50% 50%, 100% 0, 0 0);
  z-index: 35;
  transition: clip-path 1s ease;
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
    align-items: flex-end;
    padding: 0.75rem;
  }

  .popup-overlay-close {
    top: 12px;
    right: 12px;
  }

  .popup-envelope {
    min-height: 248px;
    aspect-ratio: 16 / 9;
  }

  .popup-letter {
    padding: 1rem 1rem 1.35rem;
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
    font-size: 1.4rem;
  }

  .popup-body {
    max-height: 38%;
    font-size: 0.92rem;
  }

  .popup-meta {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }

  .popup-count {
    text-align: left;
  }

  .popup-actions {
    justify-content: flex-end;
  }

}

@media (prefers-reduced-motion: reduce) {
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
