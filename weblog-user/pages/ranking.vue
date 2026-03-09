<template>
  <div class="ranking-page">
    <header class="page-header">
      <h1 class="page-title">排行榜</h1>
      <p class="page-subtitle">每小时更新一次，按热度综合排序</p>
    </header>

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

    <div v-if="loading && !items.length" class="state">加载中...</div>

    <div v-else-if="!items.length" class="state">暂无排行数据</div>

    <div v-else class="list">
      <NuxtLink
        v-for="(item, idx) in items"
        :key="`${item.post_id}-${idx}`"
        :to="`/post/${item.slug}`"
        class="item"
      >
        <span class="rank">{{ idx + 1 }}</span>
        <div class="content">
          <h3>{{ item.title }}</h3>
          <p>
            阅读 {{ item.view_count }} · 点赞 {{ item.like_count }} · 收藏 {{ item.collect_count }} · 评论 {{ item.comment_count }}
          </p>
        </div>
        <span class="score">{{ formatScore(item.score) }}</span>
      </NuxtLink>
    </div>

    <div v-if="items.length" class="footer">
      <button class="more-btn" :disabled="loading || !hasMore" @click="loadMore">
        {{ loading ? '加载中...' : (hasMore ? '加载更多' : '已加载全部') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { rankingApi, type RankingItem } from '~/api/ranking'

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
const items = ref<RankingItem[]>([])

function formatScore(score: number): string {
  if (score >= 10000) return `${(score / 10000).toFixed(1)}w`
  if (score >= 1000) return `${(score / 1000).toFixed(1)}k`
  return String(score)
}

async function fetchRanking(append = false) {
  loading.value = true
  try {
    const res = await rankingApi.get({ rankType: rankType.value, limit, offset: offset.value })
    const data = res.data || []
    items.value = append ? [...items.value, ...data] : data
    hasMore.value = data.length === limit
  } catch {
    if (!append) items.value = []
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

async function switchTab(value: number) {
  if (rankType.value === value) return
  rankType.value = value
  offset.value = 0
  hasMore.value = true
  await fetchRanking(false)
}

async function loadMore() {
  if (!hasMore.value || loading.value) return
  offset.value += limit
  await fetchRanking(true)
}

onMounted(() => {
  fetchRanking(false)
})
</script>

<style scoped lang="scss">
.ranking-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 1.5rem;
}

.page-header {
  margin-bottom: 1rem;
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
}

.page-subtitle {
  margin-top: 0.25rem;
  color: $color-text-muted;
}

.tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.tab-btn {
  border: 1px solid $color-border;
  background: $color-bg;
  color: $color-text;
  border-radius: 999px;
  padding: 0.4rem 0.9rem;
  cursor: pointer;

  &.active {
    border-color: $color-primary;
    color: $color-primary;
  }
}

.state {
  color: $color-text-muted;
  padding: 2rem 0;
}

.list {
  display: grid;
  gap: 0.75rem;
}

.item {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  border: 1px solid $color-border;
  border-radius: 12px;
  padding: 0.85rem;
  text-decoration: none;
  color: inherit;

  &:hover {
    border-color: $color-primary;
  }
}

.rank {
  width: 2rem;
  text-align: center;
  font-weight: 700;
  color: $color-primary;
}

.content {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0;
    font-size: 1rem;
    line-height: 1.3;
    color: $color-text;
  }

  p {
    margin-top: 0.3rem;
    font-size: 0.82rem;
    color: $color-text-muted;
  }
}

.score {
  font-size: 0.9rem;
  font-weight: 700;
  color: #ef4444;
}

.footer {
  margin-top: 1rem;
  text-align: center;
}

.more-btn {
  border: 1px solid $color-border;
  border-radius: 999px;
  padding: 0.45rem 1rem;
  background: $color-bg;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}
</style>
