<template>
  <div class="home-nav-search" :class="{ 'is-transparent': transparent }">
    <button type="button" class="search-trigger" aria-label="打开搜索" @click="$emit('search')">
      <Icon name="heroicons:magnifying-glass-20-solid" size="16" class="search-trigger__icon" />
      <span class="search-trigger__text">搜索文章</span>
    </button>

    <div class="ticker-panel" @mouseenter="pauseTicker" @mouseleave="resumeTicker" @focusin="pauseTicker" @focusout="resumeTicker">
      <div v-if="displayItems.length" class="ticker-viewport">
        <Transition name="ticker-roll" mode="out-in">
          <NuxtLink
            :key="currentItem.post_id"
            :to="`/post/${currentItem.slug}`"
            class="ticker-link"
            :title="currentItem.title"
          >
            <span class="ticker-link__text">{{ currentItem.title }}</span>
            <span class="ticker-fire" aria-hidden="true">
              <Icon name="heroicons:fire-16-solid" size="14" />
            </span>
          </NuxtLink>
        </Transition>
      </div>

      <div v-else class="ticker-placeholder">热榜更新中</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RankingItem } from '~/api/ranking'

interface Props {
  items: RankingItem[]
  transparent?: boolean
}

interface Emits {
  (e: 'search'): void
}

const props = withDefaults(defineProps<Props>(), {
  transparent: false,
})

defineEmits<Emits>()

const displayItems = computed(() => props.items.slice(0, 5))
const activeIndex = ref(0)
const tickerPaused = ref(false)

let tickerTimer: ReturnType<typeof setInterval> | null = null

const currentItem = computed(() => displayItems.value[activeIndex.value] ?? displayItems.value[0])

function stopTicker() {
  if (tickerTimer) {
    clearInterval(tickerTimer)
    tickerTimer = null
  }
}

function startTicker() {
  stopTicker()
  if (!import.meta.client || tickerPaused.value || displayItems.value.length <= 1) return

  tickerTimer = setInterval(() => {
    activeIndex.value = (activeIndex.value + 1) % displayItems.value.length
  }, 3200)
}

function pauseTicker() {
  tickerPaused.value = true
  stopTicker()
}

function resumeTicker() {
  tickerPaused.value = false
  startTicker()
}

watch(displayItems, () => {
  activeIndex.value = 0
  startTicker()
}, { immediate: true })

onUnmounted(() => {
  stopTicker()
})
</script>

<style scoped lang="scss">
.home-nav-search {
  flex: 0 1 460px;
  min-width: 280px;
  max-width: 460px;
  margin-left: 0.9rem;
  height: 40px;
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.3rem;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
  min-width: 0;

  .dark & {
    border-color: rgba(71, 85, 105, 0.72);
    background: rgba(15, 23, 42, 0.78);
    box-shadow: 0 12px 32px rgba(2, 6, 23, 0.28);
  }

  &.is-transparent {
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(255, 255, 255, 0.14);
    box-shadow: none;
  }
}

.search-trigger {
  flex-shrink: 0;
  height: 100%;
  min-width: 126px;
  padding: 0 0.85rem;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.14), rgba(59, 130, 246, 0.04));
  color: $color-text;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.42rem;
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(59, 130, 246, 0.08));
    color: $color-primary;
  }

  .dark & {
    color: $color-dark-text;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(30, 41, 59, 0.3));
  }

  .is-transparent & {
    color: #fff;
    background: rgba(255, 255, 255, 0.12);

    &:hover {
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
    }
  }
}

.search-trigger__icon {
  opacity: 0.9;
}

.search-trigger__text {
  font-size: 0.83rem;
  font-weight: 600;
  white-space: nowrap;
}

.ticker-panel {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  padding-right: 0.5rem;
}

.ticker-viewport {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  position: relative;
  height: 24px;
  display: flex;
  align-items: center;
}

.ticker-link,
.ticker-placeholder {
  width: 100%;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.35rem;
  font-size: 0.8rem;
  line-height: 1;
}

.ticker-link {
  color: $color-text;
  text-decoration: none;
  transition: color 0.2s ease;

  &:hover {
    color: $color-primary;
  }

  .dark & {
    color: $color-dark-text;

    &:hover {
      color: #93c5fd;
    }
  }

  .is-transparent & {
    color: rgba(255, 255, 255, 0.92);

    &:hover {
      color: #fff;
    }
  }
}

.ticker-link__text {
  min-width: 0;
  max-width: calc(100% - 22px);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ticker-placeholder {
  min-width: 0;
  flex: 1;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }

  .is-transparent & {
    color: rgba(255, 255, 255, 0.7);
  }
}

.ticker-fire {
  flex-shrink: 0;
  color: #fb923c;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 0 6px rgba(249, 115, 22, 0.35));
  animation: flame-flicker 1.15s ease-in-out infinite;
}

.ticker-roll-enter-active,
.ticker-roll-leave-active {
  transition: opacity 220ms ease, transform 260ms ease;
}

.ticker-roll-enter-from {
  opacity: 0;
  transform: translate3d(0, 110%, 0);
}

.ticker-roll-leave-to {
  opacity: 0;
  transform: translate3d(0, -110%, 0);
}

@keyframes flame-flicker {
  0%, 100% {
    transform: translate3d(0, 0, 0) scale(1);
    opacity: 0.92;
  }
  35% {
    transform: translate3d(0, -1px, 0) scale(1.08);
    opacity: 1;
  }
  68% {
    transform: translate3d(0, 0.5px, 0) scale(0.96);
    opacity: 0.82;
  }
}

@media (max-width: 1180px) {
  .home-nav-search {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ticker-roll-enter-active,
  .ticker-roll-leave-active,
  .ticker-fire,
  .search-trigger {
    transition: none !important;
    animation: none !important;
    transform: none !important;
  }
}
</style>
