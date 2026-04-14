<template>
  <div class="topic-list-bg">
    <div class="topic-list-page" :class="{ 'page-entered': pageEntered }">
      <header class="page-header">
        <div class="page-title-row">
          <h1 class="page-title">
            <Icon name="heroicons:book-open-20-solid" size="22" />
            专题
          </h1>
          <p class="page-desc">聚合系列主题内容，按专题高效阅读</p>
        </div>
      </header>

      <div v-if="loading">
        <UnifiedSkeleton class="topic-page-skeleton" variant="topic" :count="pageSize" />
      </div>

      <div v-else>
        <!-- 专题列表 -->
        <div v-if="topics.length" class="topic-grid">
          <NuxtLink
            v-for="(item, index) in topics"
            :key="item.id"
            :to="`/topic/${item.id}`"
            class="topic-card"
            target="_blank"
            rel="noopener noreferrer"
            :class="{ 'card-entered': listEntered }"
            :style="{ '--enter-index': index }"
          >
            <div class="card-cover">
              <img
                v-if="item.cover && !coverImageErrors[item.id]"
                :src="item.cover"
                :alt="item.title"
                loading="lazy"
                class="cover-img"
                :class="{ 'cover-img--loaded': loadedCoverIds.has(item.id) }"
                @load="handleCoverLoad(item.id)"
                @error="handleCoverError(item.id)"
              >
              <div v-else class="cover-placeholder">
                <Icon name="heroicons:book-open-20-solid" size="32" />
              </div>
            </div>
            <div class="card-body">
              <h2 class="card-title">{{ item.title }}</h2>
              <p v-if="item.summary" class="card-summary">{{ item.summary }}</p>
              <div class="card-footer">
                <span class="article-count">
                  <Icon name="heroicons:document-text-16-solid" size="14" />
                  {{ item.articleCount }} 篇文章
                </span>
                <span class="create-time">{{ formatDate(item.createTime) }}</span>
              </div>
            </div>
          </NuxtLink>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <Icon name="heroicons:inbox-20-solid" size="48" />
          <p>暂无专题</p>
        </div>

        <!-- 分页 -->
        <Pagination
          v-if="total > 0"
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-size-options="[6, 12, 24]"
          @update:current-page="handlePageChange"
          @update:page-size="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { topicApi, type TopicItem } from '~/api/content/topic'
import { scrollToTopOnMobilePagination } from '~/utils/navigation/paginationScroll'

useHead({ title: '专题' })

const topics = ref<TopicItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(6)
const loading = ref(true)
const pageEntered = ref(false)
const listEntered = ref(false)
const MIN_SKELETON_MS = 220
let fetchTopicsRequestId = 0
const coverImageErrors = reactive<Record<number, boolean>>({})
const loadedCoverIds = reactive(new Set<number>())

function resetCoverLoadState() {
  loadedCoverIds.clear()
  Object.keys(coverImageErrors).forEach((key) => {
    delete coverImageErrors[Number(key)]
  })
}

function handleCoverLoad(id: number) {
  loadedCoverIds.add(id)
}

function handleCoverError(id: number) {
  coverImageErrors[id] = true
  loadedCoverIds.delete(id)
}

function waitFor(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

async function fetchTopics() {
  const requestId = ++fetchTopicsRequestId
  const startAt = Date.now()
  resetCoverLoadState()
  loading.value = true
  try {
    const res = await topicApi.list(currentPage.value, pageSize.value)
    if (requestId !== fetchTopicsRequestId) {
      return
    }
    topics.value = res.data.records
    total.value = res.data.total
  } catch { /* 静默 */ }
  finally {
    if (requestId !== fetchTopicsRequestId) {
      return
    }
    const elapsed = Date.now() - startAt
    if (elapsed < MIN_SKELETON_MS) {
      await waitFor(MIN_SKELETON_MS - elapsed)
    }
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchTopics()
  if (scrollToTopOnMobilePagination()) return
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchTopics()
  scrollToTopOnMobilePagination()
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  if (import.meta.client) {
    window.requestAnimationFrame(() => {
      pageEntered.value = true
    })
  }

  void fetchTopics()
})

watch(loading, (isLoading) => {
  if (isLoading) {
    listEntered.value = false
    return
  }

  if (!import.meta.client) {
    listEntered.value = true
    return
  }

  window.requestAnimationFrame(() => {
    listEntered.value = true
  })
}, { immediate: true })
</script>

<style scoped lang="scss">
.topic-list-bg {
  background: #f5f5f5;
  min-height: 100vh;
  .dark & { background: $color-dark-bg; }
}

.topic-list-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x) 3rem;
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.topic-list-page.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.page-header {
  margin-bottom: var(--layout-page-header-margin-bottom);
}

.page-title-row {
  display: flex;
  align-items: flex-end;
  gap: 0.62rem;
  min-width: 0;
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  color: $color-text;
  margin: 0;
  .dark & { color: $color-dark-text; }
}

.page-desc {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.4;
  color: $color-text-muted;
  white-space: nowrap;
  .dark & { color: $color-dark-text-muted; }
}

@media (max-width: $breakpoint-md) {
  .page-title-row {
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.22rem 0.5rem;
  }

  .page-desc {
    white-space: normal;
  }
}

.topic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.25rem;
  @media (max-width: $breakpoint-sm) {
    grid-template-columns: 1fr;
  }
}

.topic-card {
  display: flex;
  flex-direction: column;
  height: 334px;
  min-height: 334px;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  opacity: 0;
  transform: translate3d(0, 12px, 0);
  transition: border-color 0.2s, opacity 560ms cubic-bezier(0.22, 1, 0.36, 1), transform 620ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.25s ease;

  &.card-entered {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    transition-delay: calc(40ms + min(var(--enter-index), 7) * 50ms);
  }

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  }
  .dark & {
    background: $color-dark-bg-secondary;
    box-shadow: 0 1px 8px rgba(0, 0, 0, 0.2);
    &:hover { box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35); }
  }
}

.card-cover {
  width: 100%;
  height: 180px;
  overflow: hidden;
  background: #e2e8f0;
  .dark & {
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
  }
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  filter: blur(2px);
  transition: opacity 0.28s ease, filter 0.28s ease;
}

.cover-img--loaded {
  opacity: 1;
  filter: blur(0);
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  .dark & {
    color: $color-dark-text-muted;
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
  }
}

.card-body {
  padding: 1rem 1.25rem 1.25rem;
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 154px;
  min-height: 154px;
}

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: $color-text;
  margin-bottom: 0.5rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  .dark & { color: $color-dark-text; }
}

.card-summary {
  font-size: 0.85rem;
  color: $color-text-muted;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 0.75rem;
  flex: 1;
  .dark & { color: $color-dark-text-muted; }
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  font-size: 0.8rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

.article-count {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: $color-primary;
  font-weight: 500;
}

.create-time {
  font-size: 0.75rem;
}

/* 状态 */
.empty-state {
  text-align: center;
  padding: 4rem;
  color: #94a3b8;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

:deep(.dark .topic-page-skeleton .skeleton-item) {
  background: $color-dark-bg-secondary;
}

:deep(.dark .topic-page-skeleton.variant-topic .skeleton-item) {
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.2);
}

@media (prefers-reduced-motion: reduce) {
  .topic-list-page,
  .topic-list-page.page-entered,
  .topic-card,
  .topic-card.card-entered {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
