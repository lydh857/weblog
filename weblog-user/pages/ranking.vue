<template>
  <div class="ranking-page">
    <header class="page-header">
      <h1 class="page-title">
        <Icon name="heroicons:trophy-20-solid" size="22" />
        排行榜
      </h1>
      <p class="page-desc">每小时更新一次，按热度综合排序</p>
    </header>

    <section class="ranking-panel">
      <div class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-btn"
          :class="{ active: rankType === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="fallbackNotice" class="state state--hint">
        <Icon name="heroicons:information-circle-16-solid" size="16" />
        <span>{{ fallbackNotice }}</span>
      </div>

      <div v-if="!loading && !items.length" class="state state--empty">
        <Icon name="heroicons:chart-bar-square-20-solid" size="28" />
        <span>暂无排行数据</span>
      </div>

      <div v-else-if="items.length" :key="animKey" class="list ranking-items">
        <NuxtLink
          v-for="(item, idx) in items"
          :key="`${item.post_id}-${idx}`"
          :to="`/post/${item.slug}`"
          class="item animate-item"
          :style="getItemAnimationStyle(idx)"
        >
          <span class="rank" :class="[`rank-${idx + 1}`]">{{ idx + 1 }}</span>
          <div class="content">
            <h3>{{ item.title }}</h3>
            <p class="meta-line">
              <span class="meta-item"><Icon name="heroicons:eye-16-solid" size="12" /> {{ item.view_count }}</span>
              <span class="meta-item"><Icon name="heroicons:heart-16-solid" size="12" /> {{ item.like_count }}</span>
              <span class="meta-item"><Icon name="heroicons:bookmark-16-solid" size="12" /> {{ item.collect_count }}</span>
              <span class="meta-item"><Icon name="heroicons:chat-bubble-left-16-solid" size="12" /> {{ item.comment_count }}</span>
            </p>
          </div>
          <span class="score" :style="{ color: getHeatColor(idx + 1) }">
            <Icon name="heroicons:fire-16-solid" size="13" />
            {{ formatScore(item.score) }}
          </span>
        </NuxtLink>
      </div>

      <div v-if="items.length" class="footer">
        <button class="more-btn" :disabled="loading || loadingMore || !hasMore" @click="loadMore">
          <Icon v-if="loadingMore" name="heroicons:arrow-path-20-solid" size="16" class="spin" />
          {{ loadingMore ? '加载中...' : (hasMore ? '加载更多' : '已加载全部') }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { rankingApi, type RankingItem, type RankingMeta } from '~/api/ranking'

useHead({ title: '排行榜 - Weblog' })

const tabs = [
  { label: '今日飙升', value: 1 },
  { label: '本周热榜', value: 2 },
  { label: '月度精选', value: 3 },
  { label: '总热度榜', value: 4 },
]

const rankType = ref(1)
const limit = 20
const offset = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const items = ref<RankingItem[]>([])
const animKey = ref(0)
const animationStartIndex = ref(0)
const rankingMeta = ref<RankingMeta | null>(null)
const rankingSource = ref<'ranking' | 'recent'>('ranking')

const fallbackNotice = computed(() => {
  const meta = rankingMeta.value
  if (rankType.value !== 1 || !meta?.fallbackUsed) {
    return ''
  }
  if (meta.fallbackReason === 'daily_empty_fallback_recent_posts') {
    return '今日飙升暂无数据，已展示最新发布'
  }
  if (meta.fallbackReason === 'daily_empty_no_recent_posts') {
    return '今日飙升与最新发布均暂无数据'
  }
  if (meta.servedRankType === 1 && meta.servedStatDate) {
    return `今日飙升暂无数据，已展示 ${meta.servedStatDate} 的飙升榜`
  }
  if (meta.servedRankType === 2) {
    return '今日飙升暂无数据，已自动回退到本周热榜'
  }
  if (meta.servedRankType === 4) {
    return '今日飙升暂无数据，已自动回退到总热度榜'
  }
  return '今日飙升暂无数据，已自动回退到可用榜单'
})

function formatScore(score: number): string {
  if (score >= 10000) return `${(score / 10000).toFixed(1)}w`
  if (score >= 1000) return `${(score / 1000).toFixed(1)}k`
  return String(score)
}

function getHeatColor(rank: number): string {
  if (rank <= 1) return '#ef4444'
  if (rank <= 2) return '#f56565'
  if (rank <= 3) return '#f87171'
  if (rank <= 5) return '#fb923c'
  if (rank <= 8) return '#fdba74'
  return '#94a3b8'
}

function getItemAnimationStyle(idx: number): Record<string, string> {
  const delayIndex = Math.max(0, idx - animationStartIndex.value)
  return {
    animationDelay: `${Math.min(delayIndex, 6) * 0.05}s`,
  }
}

async function fetchRanking(append = false) {
  const nextAnimationStartIndex = append ? items.value.length : 0
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
  }
  try {
    const res = await rankingApi.getSmartWithRecentFallback({ rankType: rankType.value, limit, offset: offset.value })
    const data = res.items || []
    rankingMeta.value = res.meta || null
    rankingSource.value = res.source

    if (res.source === 'recent') {
      if (!append) {
        animationStartIndex.value = 0
        items.value = data
        animKey.value += 1
      }
      hasMore.value = false
      return
    }

    animationStartIndex.value = nextAnimationStartIndex
    items.value = append ? [...items.value, ...data] : data
    hasMore.value = data.length === limit
    if (!append) {
      animKey.value += 1
    }
  } catch {
    if (!append) items.value = []
    rankingMeta.value = null
    rankingSource.value = 'ranking'
    hasMore.value = false
  } finally {
    if (append) {
      loadingMore.value = false
    } else {
      loading.value = false
    }
  }
}

async function switchTab(value: number) {
  if (rankType.value === value) return
  rankType.value = value
  rankingMeta.value = null
  rankingSource.value = 'ranking'
  offset.value = 0
  hasMore.value = true
  await fetchRanking(false)
}

async function loadMore() {
  if (rankingSource.value === 'recent') return
  if (!hasMore.value || loading.value || loadingMore.value) return
  offset.value += limit
  await fetchRanking(true)
}

onMounted(() => {
  fetchRanking(false)
})
</script>

<style scoped lang="scss">
.ranking-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

.page-header {
  margin-bottom: var(--layout-page-header-margin-bottom);
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  margin: 0;
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.page-desc {
  margin-top: var(--layout-page-desc-margin-top);
  font-size: 0.92rem;
  line-height: 1.4;
  color: $color-text-muted;
  .dark & { color: #94a3b8; }
}

.ranking-panel {
  border: 1px solid $color-border;
  border-radius: 16px;
  background: $color-bg;
  padding: 0.9rem;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.04);
  .dark & {
    border-color: $color-dark-border;
    background: $color-dark-bg-secondary;
    box-shadow: none;
  }
}

.tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.9rem;
}

.tab-btn {
  border: 1px solid $color-border;
  background: $color-bg;
  color: $color-text-muted;
  border-radius: 999px;
  padding: 0.45rem 0.95rem;
  cursor: pointer;
  font-size: 0.86rem;
  font-weight: 600;
  transition: all 0.2s;
  &:hover { border-color: $color-primary; color: $color-primary; }
  &.active {
    border-color: $color-primary;
    color: #fff;
    background: $color-primary;
    box-shadow: 0 6px 14px rgba(59, 130, 246, 0.28);
  }
}

.state {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: $color-text-muted;
  border-radius: 12px;
}

.state--hint {
  justify-content: flex-start;
  margin-bottom: 0.85rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid rgba(59, 130, 246, 0.22);
  background: rgba(59, 130, 246, 0.05);
  font-size: 0.82rem;
}

.state--empty {
  min-height: 250px;
  justify-content: center;
  border: 1px dashed rgba(148, 163, 184, 0.5);
}

.list {
  display: grid;
  gap: 0.75rem;
}

.ranking-items {
  animation: ranking-fade-in 0.2s ease-in-out;
  will-change: opacity;
  transform: translateZ(0);
}

.animate-item {
  opacity: 0;
  transform: translateX(-5px);
  animation: ranking-slide-in 0.3s ease-out forwards;
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

.item {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  border: 1px solid $color-border;
  border-radius: 12px;
  padding: 0.9rem;
  text-decoration: none;
  color: inherit;
  transition: all 0.2s;
  &:hover {
    border-color: $color-primary;
    transform: translateY(-1px);
    box-shadow: 0 8px 18px rgba(59, 130, 246, 0.1);
  }
}

.rank {
  width: 2rem;
  height: 2rem;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: $color-text-muted;
  background: rgba(148, 163, 184, 0.14);
  &.rank-1 { color: #fff; background: linear-gradient(135deg, #f59e0b, #d97706); }
  &.rank-2 { color: #fff; background: linear-gradient(135deg, #94a3b8, #64748b); }
  &.rank-3 { color: #fff; background: linear-gradient(135deg, #f97316, #ea580c); }
}

.content {
  flex: 1;
  min-width: 0;
  h3 {
    margin: 0;
    font-size: 1rem;
    line-height: 1.35;
    color: $color-text;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  p {
    margin-top: 0.35rem;
    font-size: 0.82rem;
    color: $color-text-muted;
  }
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.65rem;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  line-height: 1;
}

.score {
  font-size: 0.9rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

.footer {
  margin-top: 1rem;
  text-align: center;
}

.more-btn {
  border: 1px solid $color-border;
  border-radius: 999px;
  padding: 0.52rem 1.1rem;
  background: $color-bg;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  transition: all 0.2s;
  &:hover:not(:disabled) { border-color: $color-primary; color: $color-primary; }
  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: $breakpoint-md) {
  .ranking-panel {
    padding: 0.7rem;
    border-radius: 14px;
  }
  .item {
    align-items: flex-start;
  }
  .score {
    margin-top: 0.12rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ranking-items,
  .animate-item,
  .spin {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }
}
</style>
