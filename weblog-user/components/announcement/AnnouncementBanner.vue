<template>
  <Transition name="banner-slide">
    <div v-if="visibleAnnouncements.length" class="announcement-bar" :class="{ 'nav-hidden': navHidden, 'transparent': transparent }">
      <div class="announcement-bar-inner">
        <Icon name="heroicons:megaphone-20-solid" size="16" class="ann-icon" />
        <div class="ann-marquee" @mouseenter="paused = true" @mouseleave="paused = false">
          <NuxtLink
            v-if="currentAnn"
            :to="`/announcement/${currentAnn.id}`"
            class="ann-link"
          >
            <span class="ann-title">{{ currentAnn.title }}</span>
            <span class="ann-sep">—</span>
            <span class="ann-summary">{{ stripHtml(currentAnn.content) }}</span>
          </NuxtLink>
        </div>
        <!-- 多条公告时显示计数和切换 -->
        <div v-if="visibleAnnouncements.length > 1" class="ann-nav">
          <button class="ann-nav-btn" aria-label="上一条" @click="prevAnn">
            <Icon name="heroicons:chevron-up-16-solid" size="14" />
          </button>
          <span class="ann-count">{{ currentIndex + 1 }}/{{ visibleAnnouncements.length }}</span>
          <button class="ann-nav-btn" aria-label="下一条" @click="nextAnn">
            <Icon name="heroicons:chevron-down-16-solid" size="14" />
          </button>
        </div>
        <button
          v-if="currentAnn?.isClosable"
          class="ann-close"
          aria-label="关闭所有公告"
          @click="dismissAll"
        >
          <Icon name="heroicons:x-mark-16-solid" size="14" />
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { announcementApi, type AnnouncementVO } from '~/api/marketing/ad'

const { bannerVisible } = useAnnouncementBar()

const props = withDefaults(defineProps<{ type?: string; navHidden?: boolean; transparent?: boolean }>(), { type: 'banner', navHidden: false, transparent: false })

const announcements = ref<AnnouncementVO[]>([])
const dismissed = ref<Set<number>>(new Set())
const currentIndex = ref(0)
const paused = ref(false)

const visibleAnnouncements = computed(() =>
  announcements.value.filter(a => !dismissed.value.has(a.id))
)

const currentAnn = computed(() =>
  visibleAnnouncements.value[currentIndex.value] || visibleAnnouncements.value[0]
)

/** 去除 HTML 标签，提取纯文本摘要 */
function stripHtml(html: string): string {
  if (!html) return ''
  return html
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function nextAnn() {
  if (visibleAnnouncements.value.length <= 1) return
  currentIndex.value = (currentIndex.value + 1) % visibleAnnouncements.value.length
}

function prevAnn() {
  if (visibleAnnouncements.value.length <= 1) return
  currentIndex.value = (currentIndex.value - 1 + visibleAnnouncements.value.length) % visibleAnnouncements.value.length
}

function dismissAll() {
  if (!currentAnn.value) return
  // 一次性关闭所有公告
  const allIds = visibleAnnouncements.value.map(a => a.id)
  dismissed.value = new Set([...dismissed.value, ...allIds])
  if (import.meta.client) {
    const stored = JSON.parse(localStorage.getItem('dismissed_announcements') || '[]')
    const newDismissed = [...new Set([...stored, ...allIds])]
    localStorage.setItem('dismissed_announcements', JSON.stringify(newDismissed))
  }
}

// 自动轮播（多条公告时每 5 秒切换）
let timer: ReturnType<typeof setInterval> | null = null

function startAutoPlay() {
  stopAutoPlay()
  if (visibleAnnouncements.value.length <= 1) return
  timer = setInterval(() => {
    if (!paused.value) nextAnn()
  }, 5000)
}

function stopAutoPlay() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(visibleAnnouncements, () => {
  bannerVisible.value = visibleAnnouncements.value.length > 0
  if (visibleAnnouncements.value.length > 1) {
    startAutoPlay()
  } else {
    stopAutoPlay()
  }
})

onMounted(async () => {
  const stored = JSON.parse(localStorage.getItem('dismissed_announcements') || '[]')
  dismissed.value = new Set(stored)

  try {
    const res = await announcementApi.getByType(props.type)
    announcements.value = res.data || []
  } catch { /* ignore */ }
  finally {
    bannerVisible.value = visibleAnnouncements.value.length > 0
    if (visibleAnnouncements.value.length > 1) startAutoPlay()
  }
})

onUnmounted(() => stopAutoPlay())
</script>

<style scoped lang="scss">
.announcement-bar {
  position: fixed;
  top: var(--layout-navbar-height, 60px);
  left: 0;
  right: 0;
  box-sizing: border-box;
  padding-right: var(--scrollbar-width, 0px);
  z-index: 99; /* 低于导航栏(100)，高于页面内容 */
  background: rgba(239, 246, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: none;
  transition:
    transform 0.44s cubic-bezier(0.22, 1, 0.36, 1),
    background-color 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    backdrop-filter 0.42s cubic-bezier(0.22, 1, 0.36, 1);

  &.nav-hidden {
    transform: translateY(calc(var(--layout-navbar-height, 60px) * -1));
  }

  &.transparent {
    background: rgba(15, 23, 42, 0.34);
    backdrop-filter: blur(4px);
    box-shadow: none;

    .ann-icon { color: rgba(255, 255, 255, 0.9); }
    .ann-link { color: rgba(255, 255, 255, 0.9); &:hover { color: #fff; } }
    .ann-title { color: #fff; }
    .ann-sep { color: rgba(255, 255, 255, 0.5); }
    .ann-summary { color: rgba(255, 255, 255, 0.8); }
    .ann-nav-btn { color: rgba(255, 255, 255, 0.7); &:hover { background: rgba(255, 255, 255, 0.1); } }
    .ann-count { color: rgba(255, 255, 255, 0.6); }
    .ann-close { color: rgba(255, 255, 255, 0.6); &:hover { background: rgba(255, 255, 255, 0.1); color: #fff; } }
  }

  .dark & {
    background: rgba(23, 27, 32, 0.9);
    box-shadow: none;
  }

  @media (max-width: $breakpoint-md) {
    padding-right: 0;
  }
}

.announcement-bar-inner {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: 0 var(--layout-page-padding-x);
  height: var(--layout-announcement-height, 36px);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ann-icon {
  flex-shrink: 0;
  color: $color-primary;
  transition: color 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}

.ann-marquee {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}

.ann-link {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  text-decoration: none;
  color: #1e40af;
  font-size: 0.82rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.3s cubic-bezier(0.22, 1, 0.36, 1);

  &:hover {
    color: $color-primary;
  }

  .dark & {
    color: #d2d9e3;

    &:hover {
      color: #e8edf5;
    }
  }
}

.ann-title {
  font-weight: 600;
  flex-shrink: 0;
}

.ann-sep {
  color: #93c5fd;
  flex-shrink: 0;
  transition: color 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.ann-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.ann-nav {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s ease;

  .announcement-bar:hover & {
    opacity: 1;
  }
}

.ann-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: $radius-sm;
  background: transparent;
  color: #1e40af;
  cursor: pointer;
  transition: background-color 0.24s ease, color 0.24s ease;

  &:hover {
    background: rgba(59, 130, 246, 0.1);
  }

  .dark & {
    color: #b9c3d2;

    &:hover {
      background: rgba(148, 163, 184, 0.16);
    }
  }
}

.ann-count {
  font-size: 0.72rem;
  color: #6b7280;
  min-width: 24px;
  text-align: center;
  transition: color 0.3s cubic-bezier(0.22, 1, 0.36, 1);

  .dark & {
    color: #9aa5b5;
  }
}

.ann-close {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: $radius-sm;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  transition: opacity 0.24s ease, background-color 0.24s ease, color 0.24s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.06);
    color: #374151;
  }

  .dark & {
    color: #9aa5b5;

    &:hover {
      background: rgba(148, 163, 184, 0.16);
      color: #d6dbe4;
    }
  }

  @media (max-width: $breakpoint-md) {
    width: 24px;
    height: 24px;
    min-width: 24px;
    min-height: 24px;
    flex: 0 0 24px;
    padding: 0;
    margin-right: 0;
    justify-content: center;
  }
}

/* 进出动画 */
.banner-slide-enter-active,
.banner-slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.banner-slide-enter-from,
.banner-slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
