<template>
  <Teleport to="body">
    <div v-if="visible && currentAnn" class="popup-overlay" @click.self="tryClose">
      <div class="popup-card">
        <div class="popup-header">
          <Icon name="heroicons:megaphone-20-solid" size="20" class="popup-icon" />
          <h3 class="popup-title">{{ currentAnn.title }}</h3>
          <button v-if="currentAnn.isClosable" class="popup-close" aria-label="关闭" @click="tryClose">
            <Icon name="heroicons:x-mark-20-solid" size="20" />
          </button>
        </div>
        <div class="popup-body" v-html="sanitize(currentAnn.content)" />
        <div v-if="popupAnnouncements.length > 1" class="popup-footer">
          <span class="popup-count">{{ currentIndex + 1 }} / {{ popupAnnouncements.length }}</span>
          <button v-if="currentIndex < popupAnnouncements.length - 1" class="popup-next" @click="currentIndex++">
            下一条 <Icon name="heroicons:chevron-right-20-solid" size="16" />
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { announcementApi, type AnnouncementVO } from '~/api/ad'
import DOMPurify from 'dompurify'

const visible = ref(false)
const popupAnnouncements = ref<AnnouncementVO[]>([])
const currentIndex = ref(0)

const currentAnn = computed(() => popupAnnouncements.value[currentIndex.value] || null)

function sanitize(html: string) {
  return DOMPurify.sanitize(html)
}

function tryClose() {
  if (!currentAnn.value?.isClosable) return
  dismiss(currentAnn.value.id)
  if (currentIndex.value < popupAnnouncements.value.length - 1) {
    currentIndex.value++
  } else {
    visible.value = false
  }
}

function dismiss(id: number) {
  if (import.meta.client) {
    const stored = JSON.parse(localStorage.getItem('dismissed_announcements') || '[]')
    if (!stored.includes(id)) {
      stored.push(id)
      localStorage.setItem('dismissed_announcements', JSON.stringify(stored))
    }
  }
}

onMounted(async () => {
  try {
    const res = await announcementApi.getByType('popup')
    const dismissed = new Set(JSON.parse(localStorage.getItem('dismissed_announcements') || '[]'))
    popupAnnouncements.value = (res.data || []).filter(a => !dismissed.has(a.id))
    if (popupAnnouncements.value.length) {
      visible.value = true
    }
  } catch { /* ignore */ }
})
</script>

<style scoped lang="scss">
.popup-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.popup-card {
  background: $color-bg;
  border-radius: $radius-lg;
  max-width: 480px;
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  .dark & { background: $color-dark-bg-secondary; }
}
.popup-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1.25rem 1.25rem 0;
}
.popup-icon { color: $color-primary; flex-shrink: 0; }
.popup-title { flex: 1; font-size: 1.1rem; font-weight: 600; color: $color-text; .dark & { color: $color-dark-text; } }
.popup-close {
  border: none;
  background: none;
  color: $color-text-muted;
  cursor: pointer;
  padding: 0.25rem;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  &:hover { color: $color-text; }
}
.popup-body {
  padding: 1rem 1.25rem;
  font-size: 0.9rem;
  line-height: 1.7;
  color: $color-text;
  .dark & { color: $color-dark-text; }
  :deep(a) { color: $color-primary; text-decoration: underline; }
}
.popup-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.25rem;
  border-top: 1px solid $color-border;
  .dark & { border-top-color: $color-dark-border; }
}
.popup-count { font-size: 0.8rem; color: $color-text-muted; }
.popup-next {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  border: none;
  background: none;
  color: $color-primary;
  font-size: 0.85rem;
  cursor: pointer;
  min-height: 44px;
  &:hover { text-decoration: underline; }
}
</style>
