<template>
  <div class="ranking-list">
    <!-- 分类 Tab -->
    <div ref="tabsRef" class="ranking-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.rankType"
        :ref="(el) => setTabButtonRef(tab.rankType, el)"
        class="tab-btn"
        :class="{ active: activeTab === tab.rankType }"
        @click="switchTab(tab.rankType)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="dailyFallbackTip" class="ranking-tip">
      <Icon name="heroicons:information-circle-16-solid" size="14" />
      <span>{{ dailyFallbackTip }}</span>
    </div>

    <!-- 内容区域（相对定位容器） -->
    <div
      ref="rankingBodyRef"
      v-custom-scrollbar
      class="ranking-body"
      @touchstart="handleTouchStart"
      @touchmove="handleTouchMove"
      @touchend="handleTouchEnd"
      @touchcancel="handleTouchCancel"
    >
      <!-- 加载遮罩（绝对定位覆盖，不销毁列表） -->
      <Transition name="loading-fade">
        <div v-if="loading" class="ranking-loading-overlay">
          <div v-for="i in 5" :key="i" class="skeleton-item">
            <div class="skeleton-rank" />
            <div class="skeleton-content">
              <div class="skeleton-title" />
              <div class="skeleton-meta" />
            </div>
          </div>
        </div>
      </Transition>

      <!-- 排行列表 -->
      <div v-if="items.length > 0" :key="animKey" class="ranking-items">
        <NuxtLink
          v-for="(item, index) in items"
          :key="item.post_id"
          :to="`/post/${item.slug}`"
          class="ranking-item animate-item"
          :class="[index < 3 ? 'ranking-item--top' : 'ranking-item--normal']"
          :style="{ animationDelay: `${index * 0.05}s` }"
          target="_blank"
          rel="noopener noreferrer"
        >
        <!-- 前三名 -->
        <template v-if="index < 3">
          <span class="rank-badge" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
          <div v-if="item.cover_image" class="item-cover">
            <img
              v-if="!isCoverBroken(item.post_id)"
              :src="item.cover_image"
              :alt="item.title"
              loading="lazy"
              @error="handleCoverError(item.post_id, $event)"
            >
            <div v-else class="item-cover-placeholder" />
          </div>
          <div class="item-content">
            <h4 class="item-title">{{ item.title }}</h4>
            <div class="item-meta-row">
              <span v-if="item.category_name" class="meta-category">{{ item.category_name }}</span>
              <span v-if="item.sub_category_name" class="meta-category meta-sub">{{ item.sub_category_name }}</span>
              <span class="meta-stats">
                <Icon name="heroicons:eye-16-solid" size="11" /> {{ item.view_count }}
                <Icon name="heroicons:heart-16-solid" size="11" style="margin-left: 4px" /> {{ item.like_count }}
                <Icon name="heroicons:bookmark-16-solid" size="11" style="margin-left: 4px" /> {{ item.collect_count }}
                <Icon name="heroicons:chat-bubble-left-16-solid" size="11" style="margin-left: 4px" /> {{ item.comment_count }}
              </span>
            </div>
          </div>
          <span class="item-heat" :style="{ color: getHeatColor(index + 1) }">
            <Icon name="heroicons:fire-16-solid" size="14" />
            {{ formatScore(item.score) }}
          </span>
        </template>

        <!-- 4~10 名 -->
        <template v-else>
          <span class="rank-num">{{ index + 1 }}</span>
          <span class="item-title">{{ item.title }}</span>
          <span class="item-heat item-heat--sm" :style="{ color: getHeatColor(index + 1) }">
            <Icon name="heroicons:fire-16-solid" size="12" />
            {{ formatScore(item.score) }}
          </span>
        </template>
      </NuxtLink>
    </div>

    <!-- 空数据（仅在非加载且无数据时显示） -->
    <div v-if="!loading && items.length === 0" class="ranking-empty">
      <Icon name="heroicons:chart-bar-16-solid" size="32" />
      <p>暂无排行数据</p>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { rankingApi, type RankingItem, type RankingMeta } from '~/api/content/ranking'

interface Props {
  maxItems?: number
}

const props = withDefaults(defineProps<Props>(), {
  maxItems: 10,
})

const tabs = [
  { label: '今日飙升', rankType: 1 },
  { label: '本周热榜', rankType: 2 },
  { label: '月度精选', rankType: 3 },
  { label: '最高热度', rankType: 4 },
]

const activeTab = ref(1)
const items = ref<RankingItem[]>([])
const loading = ref(false)
const animKey = ref(0)
const currentMeta = ref<RankingMeta | null>(null)
const imageErrorMap = ref<Record<number, boolean>>({})
const tabsRef = ref<HTMLElement | null>(null)
const tabButtonRefs = new Map<number, HTMLElement>()
const rankingBodyRef = ref<HTMLElement | null>(null)
let touchTracking = false
let touchStartX = 0
let touchStartY = 0
let touchDeltaX = 0
let touchDeltaY = 0
const SWIPE_TRIGGER_X = 32

/** 格式化热度分数 */
function formatScore(score: number): string {
  if (score >= 10000) return `${(score / 10000).toFixed(1)}w`
  if (score >= 1000) return `${(score / 1000).toFixed(1)}k`
  return String(score)
}

/** 根据排名返回热度颜色 */
function getHeatColor(rank: number): string {
  if (rank <= 1) return '#ef4444'
  if (rank <= 2) return '#f56565'
  if (rank <= 3) return '#f87171'
  if (rank <= 5) return '#fb923c'
  if (rank <= 8) return '#fdba74'
  return '#d4d4d4'
}

const dailyFallbackTip = computed(() => {
  const meta = currentMeta.value
  if (!meta?.fallbackUsed) {
    return ''
  }

  if (activeTab.value === 1) {
    if (meta.fallbackReason === 'daily_empty_fallback_recent_posts') {
      return '今日榜暂无数据，已展示最新发布'
    }
    if (meta.fallbackReason === 'daily_empty_no_recent_posts') {
      return '今日榜和最新发布均暂无数据'
    }
    if (meta.servedRankType === 1 && meta.servedStatDate) {
      return `今日榜暂无数据，已展示 ${meta.servedStatDate} 的飙升榜`
    }
    if (meta.servedRankType === 2) {
      return '今日榜暂无数据，已展示本周热榜'
    }
    if (meta.servedRankType === 4) {
      return '今日榜暂无数据，已展示最高热度榜'
    }
    return '今日榜暂无数据，已自动切换可用榜单'
  }

  if (activeTab.value === 2) {
    if (meta.fallbackReason === 'rank_empty_fallback_total') {
      return '本周热榜暂无数据，已展示最高热度'
    }
    if (meta.fallbackReason === 'rank_empty_fallback_recent_posts') {
      return '本周热榜暂无数据，已展示最新发布'
    }
    if (meta.fallbackReason === 'rank_empty_no_recent_posts') {
      return '本周热榜和最新发布均暂无数据'
    }
  }

  if (activeTab.value === 3) {
    if (meta.fallbackReason === 'rank_empty_fallback_total') {
      return '月度精选暂无数据，已展示最高热度'
    }
    if (meta.fallbackReason === 'rank_empty_fallback_recent_posts') {
      return '月度精选暂无数据，已展示最新发布'
    }
    if (meta.fallbackReason === 'rank_empty_no_recent_posts') {
      return '月度精选和最新发布均暂无数据'
    }
  }

  if (activeTab.value === 4) {
    if (meta.fallbackReason === 'rank_empty_fallback_recent_posts') {
      return '最高热度暂无数据，已展示最新发布'
    }
    if (meta.fallbackReason === 'rank_empty_no_recent_posts') {
      return '最高热度和最新发布均暂无数据'
    }
  }

  return '当前榜单暂无数据，已自动切换可用内容'
})

/** 切换 Tab */
async function switchTab(rankType: number) {
  if (activeTab.value === rankType) {
    return
  }
  activeTab.value = rankType
  await nextTick()
  scrollActiveTabIntoView(true)
  resetRankingScrollTop()
  await loadRanking()
}

function setTabButtonRef(rankType: number, el: Element | ComponentPublicInstance | null) {
  if (el instanceof HTMLElement) {
    tabButtonRefs.set(rankType, el)
    return
  }

  tabButtonRefs.delete(rankType)
}

function scrollActiveTabIntoView(userTriggered = false) {
  if (!import.meta.client) {
    return
  }

  const tabsEl = tabsRef.value
  const activeBtn = tabButtonRefs.get(activeTab.value)
  if (!tabsEl || !activeBtn) {
    return
  }

  const isMobile = window.innerWidth <= 768
  if (!isMobile && !userTriggered) {
    return
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  activeBtn.scrollIntoView({
    inline: 'center',
    block: 'nearest',
    behavior: userTriggered && !prefersReducedMotion ? 'smooth' : 'auto',
  })
}

function resetRankingScrollTop() {
  rankingBodyRef.value?.scrollTo({ top: 0, behavior: 'auto' })
}

function switchTabByOffset(offset: number) {
  const currentIndex = tabs.findIndex(tab => tab.rankType === activeTab.value)
  if (currentIndex === -1) {
    return
  }

  const nextIndex = currentIndex + offset
  if (nextIndex < 0 || nextIndex >= tabs.length) {
    return
  }

  const targetTab = tabs[nextIndex]
  if (!targetTab) {
    return
  }

  void switchTab(targetTab.rankType)
}

function resetTouchState() {
  touchTracking = false
  touchStartX = 0
  touchStartY = 0
  touchDeltaX = 0
  touchDeltaY = 0
}

function handleTouchStart(event: TouchEvent) {
  const touch = event.touches[0]
  if (!touch) {
    return
  }

  touchTracking = true
  touchStartX = touch.clientX
  touchStartY = touch.clientY
  touchDeltaX = 0
  touchDeltaY = 0
}

function handleTouchMove(event: TouchEvent) {
  if (!touchTracking) {
    return
  }

  const touch = event.touches[0]
  if (!touch) {
    return
  }

  touchDeltaX = touch.clientX - touchStartX
  touchDeltaY = touch.clientY - touchStartY
}

function handleTouchEnd() {
  if (!touchTracking) {
    return
  }

  const absX = Math.abs(touchDeltaX)
  const absY = Math.abs(touchDeltaY)
  const isHorizontalSwipe = absX >= SWIPE_TRIGGER_X && absX > absY * 1.15

  if (isHorizontalSwipe) {
    if (touchDeltaX < 0) {
      switchTabByOffset(1)
    } else {
      switchTabByOffset(-1)
    }
  }

  resetTouchState()
}

function handleTouchCancel() {
  if (!touchTracking) {
    return
  }

  resetTouchState()
}

function isCoverBroken(postId: number): boolean {
  return !!imageErrorMap.value[postId]
}

function handleCoverError(postId: number, event: Event) {
  imageErrorMap.value = {
    ...imageErrorMap.value,
    [postId]: true,
  }

  const target = event.target as HTMLImageElement | null
  if (target) {
    target.style.display = 'none'
  }
}

/** 加载排行数据 */
async function loadRanking() {
  loading.value = true
  try {
    const res = await rankingApi.getSmartWithRecentFallback({
      rankType: activeTab.value,
      limit: props.maxItems,
    })
    items.value = res.items
    currentMeta.value = res.meta
    imageErrorMap.value = {}
    // 递增 key 强制重新触发入场动画
    animKey.value++
  } catch {
    items.value = []
    currentMeta.value = null
    imageErrorMap.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRanking()
  nextTick(() => {
    scrollActiveTabIntoView(false)
  })
})
</script>

<style lang="scss" scoped>
/* ===== 逐条入场动画（参考案例方式） ===== */
.ranking-items {
  animation: ranking-fade-in 0.2s ease-in-out;
  will-change: opacity;
  transform: translateZ(0);
}

.animate-item {
  animation: ranking-slide-in 0.3s ease-out forwards;
  opacity: 0;
  transform: translateX(-5px);
  will-change: opacity, transform;
}

@keyframes ranking-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes ranking-slide-in {
  from {
    opacity: 0;
    transform: translateX(-5px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* ===== 分类 Tab ===== */
.ranking-tabs {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
  border-bottom: 1px solid $color-border;
  padding-bottom: $spacing-sm;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.ranking-tip {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: $spacing-sm;
  font-size: 0.75rem;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.tab-btn {
  border: 1px solid $color-border;
  background: $color-bg;
  color: $color-text-muted;
  border-radius: 999px;
  padding: 0.45rem 0.95rem;
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
  flex: 0 0 auto;
  white-space: nowrap;

  @media (hover: hover) and (pointer: fine) {
    &:hover {
      border-color: $color-primary;
      color: $color-primary;
    }
  }

  &.active {
    border-color: $color-primary;
    color: #fff;
    background: $color-primary;
    box-shadow: 0 6px 14px rgba(59, 130, 246, 0.28);
  }

  .dark & {
    border-color: $color-dark-border;
    background: $color-dark-bg;
    color: $color-dark-text-muted;

    @media (hover: hover) and (pointer: fine) {
      &:hover {
        border-color: rgba(147, 197, 253, 0.56);
        color: $color-dark-text;
        background: rgba(148, 163, 184, 0.12);
      }
    }

    &.active {
      border-color: rgba(147, 197, 253, 0.54);
      color: #f8fbff;
      background: rgba(59, 130, 246, 0.38);
      box-shadow: 0 8px 18px rgba(15, 23, 42, 0.42);
    }
  }
}

/* ===== 排行列表 ===== */
.ranking-items {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

/* ===== 前三名 ===== */
.ranking-item--top {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-sm;
  border-radius: $radius-md;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(59, 130, 246, 0.04);

    .item-title {
      color: $color-primary;
    }
  }

  .dark &:hover {
    background: rgba(59, 130, 246, 0.08);
  }
}

.rank-badge {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  color: #fff;
  margin-top: 2px;

  &.rank-1 { background: linear-gradient(135deg, #f59e0b, #d97706); }
  &.rank-2 { background: linear-gradient(135deg, #94a3b8, #64748b); }
  &.rank-3 { background: linear-gradient(135deg, #d97706, #b45309); }
}

.item-cover {
  flex-shrink: 0;
  width: 64px;
  height: 48px;
  border-radius: $radius-sm;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(248, 250, 252, 0.92));

  .dark & {
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.item-cover-placeholder {
  width: 100%;
  height: 100%;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.ranking-item--top .item-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: $color-text;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s;

  .dark & { color: $color-dark-text; }
}

.item-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.25rem $spacing-sm;
  margin-top: 0.25rem;
  font-size: 0.7rem;
  color: $color-text-muted;

  .dark & { color: $color-dark-text-muted; }
}

.meta-category {
  padding: 0.1rem 0.375rem;
  background: rgba(59, 130, 246, 0.08);
  color: $color-primary;
  border-radius: $radius-sm;
  font-size: 0.65rem;
  font-weight: 500;
  white-space: nowrap;

  &.meta-sub {
    background: rgba(139, 92, 246, 0.08);
    color: #8b5cf6;
  }
}

.meta-stats {
  display: flex;
  align-items: center;
  gap: 0.15rem;
}

.item-heat {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.8rem;
  font-weight: 600;
  flex-shrink: 0;
  margin-left: auto;

  &--sm {
    font-size: 0.75rem;
    font-weight: 500;
  }
}

/* ===== 4~10 名 ===== */
.ranking-item--normal {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 0.375rem $spacing-sm;
  border-radius: $radius-sm;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(59, 130, 246, 0.04);

    .item-title {
      color: $color-primary;
    }
  }

  .dark &:hover {
    background: rgba(59, 130, 246, 0.08);
  }
}

.rank-num {
  flex-shrink: 0;
  width: 24px;
  text-align: center;
  font-size: 0.8rem;
  font-weight: 600;
  color: $color-text-muted;

  .dark & { color: $color-dark-text-muted; }
}

.ranking-item--normal .item-title {
  flex: 1;
  font-size: 0.85rem;
  color: $color-text;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s;

  .dark & { color: $color-dark-text; }
}

/* ===== 内容区域容器 ===== */
.ranking-body {
  position: relative;
  min-height: 120px;
  touch-action: pan-y;
}

/* ===== 加载遮罩（绝对定位覆盖） ===== */
.ranking-loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: $color-bg;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  .dark & {
    background: $color-dark-bg-secondary;
  }
}

.skeleton-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
}

.skeleton-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & { background: $color-dark-bg-elevated; }
}

.skeleton-content { flex: 1; }

.skeleton-title {
  height: 14px;
  width: 80%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & { background: $color-dark-bg-elevated; }
}

.skeleton-meta {
  height: 10px;
  width: 40%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-top: 0.375rem;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & { background: $color-dark-bg-elevated; }
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ===== 加载遮罩过渡 ===== */
.loading-fade-enter-active {
  transition: opacity 0.15s ease;
}

.loading-fade-leave-active {
  transition: opacity 0.2s ease;
}

.loading-fade-enter-from,
.loading-fade-leave-to {
  opacity: 0;
}

/* ===== 空数据 ===== */
.ranking-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  color: $color-text-muted;
  gap: $spacing-sm;

  p { font-size: 0.85rem; }
  .dark & { color: $color-dark-text-muted; }
}

/* ===== 减少动画偏好 ===== */
@media (prefers-reduced-motion: reduce) {
  .animate-item {
    animation: none;
    opacity: 1;
    transform: none;
  }

  .ranking-items {
    animation: none;
  }
}
</style>
