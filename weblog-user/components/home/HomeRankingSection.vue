<template>
  <section v-if="hasData" class="home-ranking">
    <!-- 区块标题 -->
    <div class="section-header">
      <div class="section-title-group">
        <NuxtLink to="/ranking" class="section-title-link">
          <h2 class="section-title">文章排行</h2>
          <Icon name="heroicons:arrow-right-16-solid" size="16" class="section-arrow" />
        </NuxtLink>
        <p class="section-desc">多维度发现优质内容</p>
      </div>
    </div>

    <!-- 三栏布局：左=今日飙升 | 中=本周热榜+月度精选 | 右=最高热度 -->
    <div class="ranking-grid">
      <!-- 左栏：今日飙升（大榜，第1名大封面） -->
      <div class="ranking-card ranking-card--tall ranking-card--daily">
        <div class="card-head">
          <NuxtLink to="/ranking?tab=1" class="card-title-link">
            {{ dailyBoardTitle }}
            <Icon name="heroicons:chevron-right-16-solid" size="13" class="arrow" />
          </NuxtLink>
          <span class="card-subtitle">{{ dailyBoardSubtitle }}</span>
        </div>
        <div v-if="dailyBoard.loading">
          <div class="hero-card hero-card--skeleton" aria-hidden="true">
            <div class="hero-cover sk-hero sk-shimmer">
              <span class="sk-hero-badge sk-shimmer" />
              <div class="sk-hero-bottom">
                <div class="sk-hero-title sk-shimmer" />
                <div class="sk-hero-meta">
                  <span class="sk-hero-meta-item sk-shimmer" />
                  <span class="sk-hero-meta-item sk-shimmer short" />
                  <span class="sk-hero-meta-item sk-shimmer short" />
                </div>
              </div>
            </div>
          </div>
          <div class="card-list card-list--skeleton" aria-hidden="true">
            <div v-for="i in 9" :key="i" class="rank-row rank-row--skeleton rank-row--with-cover">
              <span class="sk-num sk-shimmer" />
              <span class="sk-cover sk-shimmer" />
              <span class="sk-text sk-shimmer" />
              <span class="sk-heat sk-shimmer" />
            </div>
          </div>
        </div>
        <template v-else-if="dailyBoard.items.length">
          <!-- 第1名封面 -->
          <NuxtLink v-if="dailyTopItem" :to="`/post/${dailyTopItem.slug}`" class="hero-card">
            <div class="hero-cover">
              <img v-if="dailyTopItem.cover_image" :src="dailyTopItem.cover_image" :alt="dailyTopItem.title" loading="lazy" />
              <div v-else class="hero-placeholder"><Icon name="heroicons:bolt-20-solid" size="28" /></div>
              <div class="hero-overlay">
                <span class="hero-badge">1</span>
                <div class="hero-bottom">
                  <div class="hero-info">
                    <span class="hero-title">{{ dailyTopItem.title }}</span>
                    <div class="hero-meta">
                      <span v-if="dailyTopItem.category_name" class="hero-cat">{{ dailyTopItem.category_name }}</span>
                      <span class="hero-stat"><Icon name="heroicons:eye-16-solid" size="11" /> {{ formatScore(dailyTopItem.view_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:heart-16-solid" size="11" /> {{ formatScore(dailyTopItem.like_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:bookmark-16-solid" size="11" /> {{ formatScore(dailyTopItem.collect_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:chat-bubble-left-16-solid" size="11" /> {{ formatScore(dailyTopItem.comment_count) }}</span>
                    </div>
                  </div>
                  <span class="hero-heat" :style="{ color: getHeatColor(1) }"><Icon name="heroicons:fire-16-solid" size="16" /> {{ formatScore(dailyTopItem.score) }}</span>
                </div>
              </div>
            </div>
          </NuxtLink>
          <!-- 2~N名 -->
          <div class="card-list">
            <NuxtLink v-for="(item, idx) in dailyBoard.items.slice(1)" :key="item.post_id" :to="`/post/${item.slug}`" class="rank-row rank-row--with-cover">
              <span class="rank-num" :class="[`rank-${idx + 2}`]">{{ idx + 2 }}</span>
              <div class="rank-cover">
                <img v-if="item.cover_image" :src="item.cover_image" :alt="item.title" loading="lazy" />
                <div v-else class="rank-cover-placeholder"></div>
              </div>
              <span class="rank-title">{{ item.title }}</span>
              <span class="rank-heat" :style="{ color: getHeatColor(idx + 2) }"><Icon name="heroicons:fire-16-solid" size="11" /> {{ formatScore(item.score) }}</span>
            </NuxtLink>
          </div>
        </template>
        <div v-else class="card-empty">暂无数据</div>
      </div>

      <!-- 中栏：本周热榜 + 月度精选 -->
      <div class="ranking-card ranking-card--week">
        <div class="card-head">
          <NuxtLink to="/ranking?tab=2" class="card-title-link">
            本周热榜
            <Icon name="heroicons:chevron-right-16-solid" size="13" class="arrow" />
          </NuxtLink>
          <span class="card-subtitle">本周热度排行</span>
        </div>
        <div v-if="weekBoard.loading" class="card-list card-list--skeleton" aria-hidden="true">
          <div v-for="i in 8" :key="i" class="rank-row rank-row--skeleton">
            <span class="sk-num sk-shimmer" />
            <span class="sk-text sk-shimmer" />
            <span class="sk-heat sk-shimmer" />
          </div>
        </div>
        <div v-else-if="weekBoard.items.length" class="card-list">
          <NuxtLink v-for="(item, idx) in weekBoard.items" :key="item.post_id" :to="`/post/${item.slug}`" class="rank-row">
            <span class="rank-num" :class="[`rank-${idx + 1}`]">{{ idx + 1 }}</span>
            <span class="rank-title">{{ item.title }}</span>
            <span class="rank-heat" :style="{ color: getHeatColor(idx + 1) }"><Icon name="heroicons:fire-16-solid" size="11" /> {{ formatScore(item.score) }}</span>
          </NuxtLink>
        </div>
        <div v-else class="card-empty">暂无数据</div>
      </div>

      <!-- 月度精选 -->
      <div class="ranking-card ranking-card--month">
        <div class="card-head">
          <NuxtLink to="/ranking?tab=3" class="card-title-link">
            月度精选
            <Icon name="heroicons:chevron-right-16-solid" size="13" class="arrow" />
          </NuxtLink>
          <span class="card-subtitle">本月口碑佳作</span>
        </div>
        <div v-if="monthBoard.loading" class="card-list card-list--skeleton" aria-hidden="true">
          <div v-for="i in 8" :key="i" class="rank-row rank-row--skeleton">
            <span class="sk-num sk-shimmer" />
            <span class="sk-text sk-shimmer" />
            <span class="sk-heat sk-shimmer" />
          </div>
        </div>
        <div v-else-if="monthBoard.items.length" class="card-list">
          <NuxtLink v-for="(item, idx) in monthBoard.items" :key="item.post_id" :to="`/post/${item.slug}`" class="rank-row">
            <span class="rank-num" :class="[`rank-${idx + 1}`]">{{ idx + 1 }}</span>
            <span class="rank-title">{{ item.title }}</span>
            <span class="rank-heat" :style="{ color: getHeatColor(idx + 1) }"><Icon name="heroicons:fire-16-solid" size="11" /> {{ formatScore(item.score) }}</span>
          </NuxtLink>
        </div>
        <div v-else class="card-empty">暂无数据</div>
      </div>

      <!-- 右栏：最高热度（大榜，第1名大封面） -->
      <div class="ranking-card ranking-card--tall ranking-card--total">
        <div class="card-head">
          <NuxtLink to="/ranking?tab=4" class="card-title-link">
            最高热度
            <Icon name="heroicons:chevron-right-16-solid" size="13" class="arrow" />
          </NuxtLink>
          <span class="card-subtitle">综合热度排行</span>
        </div>
        <div v-if="totalBoard.loading">
          <div class="hero-card hero-card--skeleton" aria-hidden="true">
            <div class="hero-cover sk-hero sk-shimmer">
              <span class="sk-hero-badge sk-shimmer" />
              <div class="sk-hero-bottom">
                <div class="sk-hero-title sk-shimmer" />
                <div class="sk-hero-meta">
                  <span class="sk-hero-meta-item sk-shimmer" />
                  <span class="sk-hero-meta-item sk-shimmer short" />
                  <span class="sk-hero-meta-item sk-shimmer short" />
                </div>
              </div>
            </div>
          </div>
          <div class="card-list card-list--skeleton" aria-hidden="true">
            <div v-for="i in 9" :key="i" class="rank-row rank-row--skeleton rank-row--with-cover">
              <span class="sk-num sk-shimmer" />
              <span class="sk-cover sk-shimmer" />
              <span class="sk-text sk-shimmer" />
              <span class="sk-heat sk-shimmer" />
            </div>
          </div>
        </div>
        <template v-else-if="totalBoard.items.length">
          <!-- 第1名封面 -->
          <NuxtLink v-if="totalTopItem" :to="`/post/${totalTopItem.slug}`" class="hero-card">
            <div class="hero-cover">
              <img v-if="totalTopItem.cover_image" :src="totalTopItem.cover_image" :alt="totalTopItem.title" loading="lazy" />
              <div v-else class="hero-placeholder"><Icon name="heroicons:trophy-20-solid" size="28" /></div>
              <div class="hero-overlay">
                <span class="hero-badge">1</span>
                <div class="hero-bottom">
                  <div class="hero-info">
                    <span class="hero-title">{{ totalTopItem.title }}</span>
                    <div class="hero-meta">
                      <span v-if="totalTopItem.category_name" class="hero-cat">{{ totalTopItem.category_name }}</span>
                      <span class="hero-stat"><Icon name="heroicons:eye-16-solid" size="11" /> {{ formatScore(totalTopItem.view_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:heart-16-solid" size="11" /> {{ formatScore(totalTopItem.like_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:bookmark-16-solid" size="11" /> {{ formatScore(totalTopItem.collect_count) }}</span>
                      <span class="hero-stat"><Icon name="heroicons:chat-bubble-left-16-solid" size="11" /> {{ formatScore(totalTopItem.comment_count) }}</span>
                    </div>
                  </div>
                  <span class="hero-heat" :style="{ color: getHeatColor(1) }"><Icon name="heroicons:fire-16-solid" size="16" /> {{ formatScore(totalTopItem.score) }}</span>
                </div>
              </div>
            </div>
          </NuxtLink>
          <!-- 2~N名 -->
          <div class="card-list">
            <NuxtLink v-for="(item, idx) in totalBoard.items.slice(1)" :key="item.post_id" :to="`/post/${item.slug}`" class="rank-row rank-row--with-cover">
              <span class="rank-num" :class="[`rank-${idx + 2}`]">{{ idx + 2 }}</span>
              <div class="rank-cover">
                <img v-if="item.cover_image" :src="item.cover_image" :alt="item.title" loading="lazy" />
                <div v-else class="rank-cover-placeholder"></div>
              </div>
              <span class="rank-title">{{ item.title }}</span>
              <span class="rank-heat" :style="{ color: getHeatColor(idx + 2) }"><Icon name="heroicons:fire-16-solid" size="11" /> {{ formatScore(item.score) }}</span>
            </NuxtLink>
          </div>
        </template>
        <div v-else class="card-empty">暂无数据</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { rankingApi, type RankingItem, type RankingMeta } from '~/api/ranking'

interface Board {
  items: RankingItem[]
  loading: boolean
  meta: RankingMeta | null
}

const totalBoard = reactive<Board>({ items: [], loading: true, meta: null })
const weekBoard = reactive<Board>({ items: [], loading: true, meta: null })
const dailyBoard = reactive<Board>({ items: [], loading: true, meta: null })
const monthBoard = reactive<Board>({ items: [], loading: true, meta: null })

const hasData = computed(() =>
  totalBoard.loading || weekBoard.loading || dailyBoard.loading || monthBoard.loading
  || totalBoard.items.length > 0 || weekBoard.items.length > 0
  || dailyBoard.items.length > 0 || monthBoard.items.length > 0
)

function formatScore(score: number): string {
  if (score >= 10000) return `${(score / 10000).toFixed(1)}w`
  if (score >= 1000) return `${(score / 1000).toFixed(1)}k`
  return String(score)
}

/** 根据排名返回热度颜色，排名越高颜色越深 */
function getHeatColor(rank: number): string {
  if (rank <= 1) return '#ef4444'
  if (rank <= 2) return '#f56565'
  if (rank <= 3) return '#f87171'
  if (rank <= 5) return '#fb923c'
  if (rank <= 7) return '#fdba74'
  return '#d4d4d4'
}

function getLocalDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function resolveDailyBoardTitle(meta: RankingMeta | null): string {
  if (!meta || !meta.fallbackUsed) {
    return '今日飙升'
  }

  if (meta.fallbackReason === 'daily_empty_fallback_recent_posts') {
    return '最新发布'
  }

  if (meta.servedRankType === 1) {
    const servedDate = meta.servedStatDate
    if (!servedDate) {
      return '近期飙升'
    }
    const yesterday = getLocalDateKey(new Date(Date.now() - 24 * 60 * 60 * 1000))
    return servedDate === yesterday ? '昨日飙升' : '近期飙升'
  }

  if (meta.servedRankType === 2) {
    return '本周热榜'
  }

  if (meta.servedRankType === 4) {
    return '最高热度'
  }

  return '今日飙升'
}

function resolveDailyBoardSubtitle(meta: RankingMeta | null): string {
  if (!meta || !meta.fallbackUsed) {
    return '今日热度飙升'
  }

  if (meta.fallbackReason === 'daily_empty_fallback_recent_posts') {
    return '今日数据不足，展示最新发布'
  }

  if (meta.fallbackReason === 'daily_empty_no_recent_posts') {
    return '暂无可展示内容'
  }

  if (meta.servedRankType === 1 && meta.servedStatDate) {
    return `数据日期 ${meta.servedStatDate}`
  }

  if (meta.servedRankType === 2) {
    return '今日数据不足，展示本周热度'
  }

  if (meta.servedRankType === 4) {
    return '今日数据不足，展示综合热度'
  }

  return '暂无今日数据，已智能回退'
}

const dailyBoardTitle = computed(() => resolveDailyBoardTitle(dailyBoard.meta))
const dailyBoardSubtitle = computed(() => resolveDailyBoardSubtitle(dailyBoard.meta))
const dailyTopItem = computed(() => dailyBoard.items[0] ?? null)
const totalTopItem = computed(() => totalBoard.items[0] ?? null)

async function loadBoard(board: Board, rankType: number, limit: number) {
  try {
    const res = await rankingApi.getSmartWithRecentFallback({ rankType, limit })
    board.items = res.items
    board.meta = res.meta
  } catch {
    board.items = []
    board.meta = null
  } finally {
    board.loading = false
  }
}

onMounted(() => {
  Promise.all([
    loadBoard(totalBoard, 4, 10),
    loadBoard(weekBoard, 2, 8),
    loadBoard(dailyBoard, 1, 10),
    loadBoard(monthBoard, 3, 8),
  ])
})
</script>

<style lang="scss" scoped>
.home-ranking {
  margin-top: $spacing-xl;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
}

.section-title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.section-title-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  text-decoration: none;
  color: inherit;

  .section-arrow {
    opacity: 0;
    transform: translateX(-4px);
    transition: all 0.2s;
    color: $color-primary;
  }

  &:hover {
    .section-title { color: $color-primary; }
    .section-arrow { opacity: 1; transform: translateX(0); }
  }
}

.section-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: $color-text;
  transition: color 0.2s;
  .dark & { color: $color-dark-text; }
}

.section-desc {
  font-size: 0.8rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

/* 三栏网格 */
.ranking-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-areas:
    'daily week total'
    'daily month total';
  gap: $spacing-md;
  align-items: stretch;
}

.ranking-card--daily {
  grid-area: daily;
}

.ranking-card--week {
  grid-area: week;
}

.ranking-card--month {
  grid-area: month;
}

.ranking-card--total {
  grid-area: total;
}

/* 中栏小榜等分高度 */
.ranking-card--week,
.ranking-card--month {
  display: flex;
  flex-direction: column;
}

.ranking-card--week > .card-list,
.ranking-card--month > .card-list {
  flex: 1;
}

/* 卡片 */
.ranking-card {
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: hidden;
  .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; }
}

/* 左右大榜撑满高度 */
.ranking-card--tall {
  display: flex;
  flex-direction: column;
}

.ranking-card--tall .card-list {
  flex: 1;
}

.card-head {
  padding: $spacing-sm $spacing-sm 0;
  display: flex;
  align-items: baseline;
  gap: $spacing-xs;
}

.card-title-link {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: $color-text;
  text-decoration: none;
  transition: color 0.2s;
  .arrow { opacity: 0; transform: translateX(-4px); transition: all 0.2s; }
  &:hover { color: $color-primary; .arrow { opacity: 1; transform: translateX(0); } }
  .dark & { color: $color-dark-text; &:hover { color: $color-primary; } }
}

.card-subtitle {
  font-size: 0.65rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

/* 第1名封面（信息叠加在封面上） */
.hero-card {
  display: block;
  margin: $spacing-xs $spacing-sm 0;
  border-radius: $radius-md;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  &:hover {
    .hero-cover img { transform: scale(1.05); }
    .hero-overlay::after { opacity: 1; }
  }
}

.hero-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  max-height: 220px;
  background: $color-bg-secondary;
  .dark & { background: $color-dark-bg-elevated; }
  img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.35s ease; }
}

.hero-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.75) 0%, transparent 50%);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: $spacing-sm;
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.25) 60%, rgba(0,0,0,0.05) 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }
  /* 让内容在 ::after 之上 */
  > * { position: relative; z-index: 1; }
}

.hero-badge {
  position: absolute;
  top: 6px; left: 6px;
  width: 24px; height: 24px;
  border-radius: 6px;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #fff;
  font-size: 0.8rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 6px rgba(245, 158, 11, 0.4);
}

.hero-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-sm;
}

.hero-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.hero-title {
  font-size: 0.85rem; font-weight: 600;
  color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.hero-cat {
  padding: 0.05rem 0.4rem;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  font-size: 0.6rem;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

.hero-stat {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  font-size: 0.65rem;
  color: rgba(255, 255, 255, 0.7);
}

.hero-heat {
  display: flex; align-items: center; gap: 0.25rem;
  font-size: 0.95rem;
  font-weight: 700;
  flex-shrink: 0;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

/* 列表 */
.card-list {
  padding: 0 $spacing-xs $spacing-xs;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem $spacing-xs;
  box-sizing: border-box;
  height: 30px;
  border-radius: $radius-sm;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s;
  &:hover {
    background: $color-bg-secondary;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    .rank-title { color: $color-primary; }
  }
  .dark &:hover {
    background: rgba(255, 255, 255, 0.04);
    box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  }
}

.rank-num {
  flex-shrink: 0;
  width: 20px; height: 20px;
  border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.7rem; font-weight: 700;
  color: $color-text-muted;
  background: $color-bg-secondary;
  &.rank-1 { background: linear-gradient(135deg, #f59e0b, #d97706); color: #fff; }
  &.rank-2 { background: linear-gradient(135deg, #94a3b8, #64748b); color: #fff; }
  &.rank-3 { background: linear-gradient(135deg, #d97706, #b45309); color: #fff; }
  .dark & { background: rgba(255, 255, 255, 0.08); color: $color-dark-text-muted; }
}

.rank-cover {
  flex-shrink: 0;
  width: 44px; height: 30px;
  border-radius: 4px;
  overflow: hidden;
  img { width: 100%; height: 100%; object-fit: cover; }
}

.rank-cover-placeholder {
  width: 100%;
  height: 100%;
  background: rgba(148, 163, 184, 0.2);

  .dark & {
    background: rgba(100, 116, 139, 0.3);
  }
}

.rank-row--with-cover {
  height: 40px;
}

.rank-title {
  font-size: 0.8rem; font-weight: 500;
  color: $color-text;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  flex: 1; min-width: 0;
  transition: color 0.2s;
  .dark & { color: $color-dark-text; }
}

.rank-heat {
  display: flex; align-items: center; gap: 0.15rem;
  font-size: 0.7rem;
  flex-shrink: 0;
}

/* 骨架屏 */
.hero-card--skeleton {
  cursor: default;
  pointer-events: none;
}

.card-list--skeleton {
  pointer-events: none;
}

.rank-row--skeleton {
  cursor: default;
  height: 30px;

  &:hover {
    background: transparent;
    box-shadow: none;
  }
}

.rank-row--skeleton.rank-row--with-cover {
  height: 40px;
}

.sk-hero {
  position: relative;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.22);
}

.sk-hero-badge {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 24px;
  height: 24px;
  border-radius: 6px;
}

.sk-hero-bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $spacing-sm;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  background: transparent;
}

.sk-hero-title {
  width: 78%;
  height: 12px;
  border-radius: 999px;
}

.sk-hero-meta {
  display: flex;
  gap: 0.3rem;
}

.sk-hero-meta-item {
  width: 62px;
  height: 9px;
  border-radius: 999px;

  &.short {
    width: 46px;
  }
}

.sk-num {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  flex-shrink: 0;
}

.sk-cover {
  display: block;
  width: 44px;
  height: 30px;
  border-radius: 4px;
  flex-shrink: 0;
}

.sk-text {
  display: block;
  flex: 1;
  height: 14px;
  border-radius: $radius-sm;
  min-width: 0;
}

.sk-heat {
  display: block;
  width: 46px;
  height: 12px;
  border-radius: 999px;
  flex-shrink: 0;
}

.sk-shimmer {
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.16) 0%,
    rgba(148, 163, 184, 0.3) 50%,
    rgba(148, 163, 184, 0.16) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s linear infinite;

  .dark & {
    background: linear-gradient(
      90deg,
      rgba(71, 85, 105, 0.24) 0%,
      rgba(100, 116, 139, 0.4) 50%,
      rgba(71, 85, 105, 0.24) 100%
    );
    background-size: 200% 100%;
  }
}

@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.card-empty {
  padding: $spacing-md;
  text-align: center;
  font-size: 0.8rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

/* 响应式 */
@media (max-width: $breakpoint-lg) {
  .ranking-grid {
    grid-template-columns: 1fr 1fr;
    grid-template-areas:
      'daily week'
      'month total';
  }
}

@media (max-width: $breakpoint-md) {
  .section-desc { display: none; }
  .ranking-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(16rem, calc(100% - 3rem)));
    grid-template-areas:
      'daily week total'
      'daily month total';
    gap: $spacing-sm;
    overflow-x: auto;
    padding: 0 0.5rem 2px 0;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
}
</style>
