<template>
  <div class="home-nav-search" :class="{ 'is-transparent': transparent, 'home-nav-search--mobile': mobile }">
    <button type="button" class="search-trigger" aria-label="搜索当前标题" @click="emitDirectSearch">
      <Icon name="heroicons:magnifying-glass-20-solid" size="16" class="search-trigger__icon" />
    </button>

    <button
      type="button"
      class="ticker-panel"
      :title="currentItem?.title || '打开搜索'"
      @click="emitPlaceholderSearch"
      @mouseenter="pauseTicker"
      @mouseleave="resumeTicker"
      @focusin="pauseTicker"
      @focusout="resumeTicker"
    >
      <div v-if="displayItems.length" class="ticker-viewport">
        <Transition name="ticker-roll" mode="out-in">
          <span
            :key="currentItem?.post_id ?? 'ticker-empty'"
            class="ticker-link"
            :title="currentItem?.title || ''"
          >
            <span class="ticker-link__text">{{ currentItem?.title || '' }}</span>
            <span class="ticker-fire" :style="getTickerFireStyle(activeIndex + 1)" aria-hidden="true">
              <Icon name="heroicons:fire-16-solid" size="14" />
            </span>
          </span>
        </Transition>
      </div>

      <div v-else class="ticker-placeholder">热榜更新中</div>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { RankingItem } from '~/api/ranking'

interface Props {
  items: RankingItem[]
  transparent?: boolean
  mobile?: boolean
}

interface Emits {
  (e: 'placeholder-search', title: string): void
  (e: 'direct-search', title: string): void
}

const props = withDefaults(defineProps<Props>(), {
  transparent: false,
  mobile: false,
})

const emit = defineEmits<Emits>()

const displayItems = computed(() => props.items.slice(0, 5))
const activeIndex = ref(0)
const tickerPaused = ref(false)

let tickerTimer: ReturnType<typeof setInterval> | null = null

const currentItem = computed(() => displayItems.value[activeIndex.value] ?? displayItems.value[0])

function getCurrentTitle() {
  return currentItem.value?.title?.trim() || ''
}

function emitPlaceholderSearch() {
  emit('placeholder-search', getCurrentTitle())
}

function emitDirectSearch() {
  emit('direct-search', getCurrentTitle())
}

function getHeatColor(rank: number): string {
  if (rank <= 1) return '#ef4444'
  if (rank <= 2) return '#f56565'
  if (rank <= 3) return '#f87171'
  if (rank <= 5) return '#fb923c'
  if (rank <= 8) return '#fdba74'
  return '#94a3b8'
}

function getTickerFireStyle(rank: number): Record<string, string> {
  const color = getHeatColor(rank)
  return {
    color,
    filter: `drop-shadow(0 0 6px ${color}66)`,
  }
}

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
  --home-nav-search-bg-dark:
    radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
    radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
    linear-gradient(180deg, #171b20, #101215);

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
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;

  .dark & {
    border-color: rgba(71, 85, 105, 0.72);
    background: var(--home-nav-search-bg-dark);
    box-shadow: 0 12px 32px rgba(2, 6, 23, 0.28);
  }

  &.is-transparent {
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(255, 255, 255, 0.14);
    box-shadow: none;
  }
}

.home-nav-search:focus-within {
  transform: translate3d(0, -1px, 0);
  border-color: rgba(59, 130, 246, 0.45);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12), 0 14px 30px rgba(15, 23, 42, 0.12);
}

.dark .home-nav-search:focus-within {
  border-color: rgba(148, 163, 184, 0.62);
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.16), 0 16px 34px rgba(2, 6, 23, 0.36);
}

.search-trigger {
  flex-shrink: 0;
  height: 100%;
  width: 34px;
  min-width: 34px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.14), rgba(59, 130, 246, 0.04));
  color: $color-text;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.42rem;
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;

  .dark & {
    color: $color-dark-text;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(30, 41, 59, 0.3));
  }

  .is-transparent & {
    color: #fff;
    background: rgba(255, 255, 255, 0.12);

  }
}

.search-trigger__icon {
  opacity: 0.9;
}

@media (hover: hover) and (pointer: fine) {
  .search-trigger:hover {
    transform: translateY(-1px);
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(59, 130, 246, 0.08));
    color: $color-primary;
  }

  .dark .search-trigger:hover {
    color: #dbeafe;
    background: linear-gradient(135deg, rgba(30, 64, 175, 0.42), rgba(30, 41, 59, 0.54));
  }

  .is-transparent .search-trigger:hover {
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
  }
}

.ticker-panel {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  padding-right: 0.5rem;
  padding-left: 0;
  text-align: left;
  border-radius: 999px;
  transition: background 180ms ease, box-shadow 180ms ease, transform 180ms ease;

  &:focus-visible {
    outline: 2px solid rgba(59, 130, 246, 0.5);
    outline-offset: 1px;
  }

  .dark &:focus-visible {
    outline-color: rgba(148, 163, 184, 0.65);
  }
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
  transition: color 0.2s ease, transform 0.2s ease;

  .dark & {
    color: $color-dark-text;

  }

  .is-transparent & {
    color: rgba(255, 255, 255, 0.92);
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

.home-nav-search--mobile {
  display: none;
}

@media (max-width: 1180px) {
  .home-nav-search:not(.home-nav-search--mobile) {
    display: none;
  }
}

@media (min-width: 1181px) and (max-width: 1366px) {
  .home-nav-search:not(.home-nav-search--mobile) {
    min-width: 220px;
    max-width: 340px;
    flex: 0 1 clamp(240px, 28vw, 340px);
    margin-left: 0.6rem;
  }
}

@media (max-width: $breakpoint-md) {
  .home-nav-search--mobile {
    display: inline-flex;
    width: min(100%, 320px);
    min-width: 0;
    max-width: 320px;
    flex: 0 1 320px;
    margin-left: clamp(0.56rem, 2.2vw, 0.88rem);
    height: 32px;
    padding: 0.16rem;
    gap: 0.24rem;
  }

  @media (max-width: 600px) {
    .home-nav-search--mobile {
      width: 100%;
      max-width: 100%;
      flex: 1;
    }
  }

  .home-nav-search--mobile .search-trigger {
    width: 24px;
    min-width: 24px;
    height: 24px;
    border-radius: 6px;
    background: transparent;
    box-shadow: none;
    transform: none;
  }

  .home-nav-search--mobile .ticker-panel {
    padding-right: 0.1rem;
  }

  .home-nav-search--mobile .ticker-viewport {
    height: 26px;
  }

  .home-nav-search--mobile .ticker-link,
  .home-nav-search--mobile .ticker-placeholder {
    font-size: 0.7rem;
    line-height: 1.25;
  }

  .home-nav-search--mobile .ticker-fire {
    transform: scale(0.9);
    transform-origin: center;
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
