<template>
  <Teleport to="body">
    <Transition name="notice-center">
      <div v-if="visible" class="notice-overlay" @click.self="close">
        <section class="notice-panel" role="dialog" aria-modal="true" aria-label="公告列表">
          <header class="notice-header">
            <div class="notice-title-wrap">
              <Icon name="heroicons:bell-20-solid" size="18" class="notice-icon" />
              <div>
                <h3 class="notice-title">公告</h3>
                <p class="notice-subtitle">{{ unreadCount > 0 ? `${unreadCount} 条未读` : '全部已读' }}</p>
              </div>
            </div>
            <div class="notice-actions">
              <button
                v-if="unreadCount > 0"
                type="button"
                class="mark-read-btn"
                @click="markAllRead"
              >
                全部已读
              </button>
              <button type="button" class="notice-close" aria-label="关闭公告列表" @click="close">
                <Icon name="heroicons:x-mark-20-solid" size="18" />
              </button>
            </div>
          </header>

          <div class="notice-body">
            <div v-if="loading" class="notice-skeleton">
              <div v-for="index in 5" :key="index" class="skeleton-item" />
            </div>

            <ul v-else-if="sortedAnnouncements.length > 0" class="notice-list">
              <li v-for="item in sortedAnnouncements" :key="item.id" class="notice-item-wrap">
                <button
                  type="button"
                  class="notice-item"
                  :class="{ 'notice-item--unread': isUnread(item.id) }"
                  @click="openAnnouncement(item.id)"
                >
                  <div class="item-main">
                    <span class="item-title">{{ item.title }}</span>
                    <span v-if="isUnread(item.id)" class="item-dot" aria-hidden="true" />
                  </div>
                  <div class="item-meta">
                    <time>{{ formatAnnouncementTime(item) }}</time>
                    <span v-if="item.priority > 0" class="item-tag">重要</span>
                  </div>
                </button>
              </li>
            </ul>

            <div v-else class="notice-empty">
              <Icon name="heroicons:inbox-20-solid" size="28" />
              <p>暂无可用公告</p>
            </div>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { announcementApi, type AnnouncementVO } from '~/api/ad'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'
import { formatRelativeTime } from '~/utils/format'

interface Props {
  visible: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'unread-change', value: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)
const loaded = ref(false)
const announcements = ref<AnnouncementVO[]>([])
const readIds = ref<Set<number>>(new Set())

const READ_STORAGE_KEY = 'read_announcements'

const unreadCount = computed(() =>
  announcements.value.reduce((count, item) => (readIds.value.has(item.id) ? count : count + 1), 0)
)

const sortedAnnouncements = computed(() =>
  [...announcements.value].sort((a, b) => {
    if (b.priority !== a.priority) return b.priority - a.priority
    const bTime = Date.parse(b.updateTime || b.createTime || '')
    const aTime = Date.parse(a.updateTime || a.createTime || '')
    if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0
    if (Number.isNaN(aTime)) return 1
    if (Number.isNaN(bTime)) return -1
    return bTime - aTime
  })
)

function isUnread(id: number): boolean {
  return !readIds.value.has(id)
}

function formatAnnouncementTime(item: AnnouncementVO): string {
  const dateValue = item.updateTime || item.createTime
  if (!dateValue) return '最近发布'
  return formatRelativeTime(dateValue)
}

function close() {
  emit('update:visible', false)
}

function loadReadIds() {
  if (!import.meta.client) return
  try {
    const parsed = JSON.parse(localStorage.getItem(READ_STORAGE_KEY) || '[]')
    if (!Array.isArray(parsed)) {
      readIds.value = new Set()
      return
    }
    const ids = parsed
      .map(value => Number(value))
      .filter(value => Number.isInteger(value) && value > 0)
    readIds.value = new Set(ids)
  } catch {
    readIds.value = new Set()
  }
}

function saveReadIds() {
  if (!import.meta.client) return
  const ids = Array.from(readIds.value)
  localStorage.setItem(READ_STORAGE_KEY, JSON.stringify(ids.slice(-300)))
}

function markAsRead(id: number) {
  if (readIds.value.has(id)) return
  readIds.value = new Set([...readIds.value, id])
  saveReadIds()
}

function markAllRead() {
  if (announcements.value.length === 0) return
  readIds.value = new Set(announcements.value.map(item => item.id))
  saveReadIds()
}

async function fetchAnnouncements(force = false) {
  if (loading.value) return
  if (loaded.value && !force) return

  loading.value = true
  try {
    const res = await announcementApi.getAll()
    announcements.value = res.data || []
    loaded.value = true
  } catch {
    announcements.value = []
  } finally {
    loading.value = false
  }
}

async function openAnnouncement(id: number) {
  markAsRead(id)
  close()
  await navigateTo(`/announcement/${id}`)
}

let locked = false

watch(() => props.visible, async (value) => {
  if (value) {
    loadReadIds()
    await fetchAnnouncements()
    if (!locked) {
      lockScroll()
      locked = true
    }
    return
  }

  if (locked) {
    unlockScroll()
    locked = false
  }
}, { immediate: true })

watch(unreadCount, (value) => {
  emit('unread-change', value)
}, { immediate: true })

onMounted(async () => {
  loadReadIds()
  await fetchAnnouncements(true)
})

onUnmounted(() => {
  if (!locked) return
  unlockScroll()
  locked = false
})
</script>

<style scoped lang="scss">
.notice-center-enter-active,
.notice-center-leave-active {
  transition: opacity 0.2s ease;
}

.notice-center-enter-from,
.notice-center-leave-to {
  opacity: 0;
}

.notice-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.notice-panel {
  width: min(560px, 100%);
  max-height: min(78vh, 720px);
  display: flex;
  flex-direction: column;
  background: $color-bg;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 16px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.24);
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: rgba(71, 85, 105, 0.55);
    box-shadow: 0 20px 48px rgba(2, 6, 23, 0.62);
  }
}

.notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  padding: 1rem 1rem 0.85rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.notice-title-wrap {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.notice-icon {
  color: $color-primary;
  flex-shrink: 0;
}

.notice-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 650;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.notice-subtitle {
  margin: 0.1rem 0 0;
  font-size: 0.78rem;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.notice-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.mark-read-btn {
  border: none;
  background: rgba(59, 130, 246, 0.12);
  color: $color-primary;
  font-size: 0.76rem;
  font-weight: 600;
  padding: 0.34rem 0.6rem;
  border-radius: 999px;
  cursor: pointer;

  &:hover {
    background: rgba(59, 130, 246, 0.18);
  }

  .dark & {
    background: rgba(59, 130, 246, 0.22);
  }
}

.notice-close {
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: $color-text-muted;
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: $color-text;
    background: rgba(148, 163, 184, 0.14);
  }

  .dark & {
    color: #94a3b8;

    &:hover {
      color: $color-dark-text;
      background: rgba(148, 163, 184, 0.2);
    }
  }
}

.notice-body {
  overflow-y: auto;
  padding: 0.4rem 0.8rem 0.85rem;
}

.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.notice-item-wrap {
  margin: 0;
}

.notice-item {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 0.72rem 0.75rem;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s;

  &:hover {
    background: rgba(59, 130, 246, 0.08);
  }

  .dark &:hover {
    background: rgba(59, 130, 246, 0.16);
  }
}

.notice-item--unread {
  .item-title {
    color: $color-text;
    font-weight: 620;

    .dark & {
      color: $color-dark-text;
    }
  }
}

.item-main {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.item-title {
  flex: 1;
  min-width: 0;
  font-size: 0.89rem;
  color: $color-text-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  .dark & {
    color: #94a3b8;
  }
}

.item-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ef4444;
  flex-shrink: 0;
}

.item-meta {
  margin-top: 0.34rem;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.74rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

.item-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.1rem 0.42rem;
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.12);
}

.notice-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  color: $color-text-muted;
  min-height: 220px;

  .dark & {
    color: #64748b;
  }

  p {
    margin: 0;
    font-size: 0.9rem;
  }
}

.notice-skeleton {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.25rem 0.1rem;
}

.skeleton-item {
  height: 54px;
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.2), rgba(148, 163, 184, 0.32), rgba(148, 163, 184, 0.2));
  animation: notice-skeleton 1.4s ease-in-out infinite;

  .dark & {
    background: linear-gradient(90deg, rgba(71, 85, 105, 0.35), rgba(100, 116, 139, 0.5), rgba(71, 85, 105, 0.35));
  }
}

@keyframes notice-skeleton {
  0%,
  100% {
    opacity: 0.65;
  }

  50% {
    opacity: 1;
  }
}

@media (max-width: $breakpoint-md) {
  .notice-overlay {
    padding: 0;
  }

  .notice-panel {
    width: 100%;
    max-height: 100vh;
    min-height: 100vh;
    border-radius: 0;
  }

  .notice-header {
    padding: 0.95rem 0.9rem 0.78rem;
  }

  .notice-body {
    padding: 0.35rem 0.65rem 0.8rem;
  }
}
</style>
