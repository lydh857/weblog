<template>
  <div class="topic-list-bg">
    <div class="topic-list-page">
      <header class="page-header">
        <div class="page-title-row">
          <h1 class="page-title">
            <Icon name="heroicons:book-open-20-solid" size="22" />
            专题
          </h1>
          <p class="page-desc">聚合系列主题内容，按专题高效阅读</p>
        </div>
      </header>

      <!-- 加载态 -->
      <UnifiedSkeleton v-if="loading" variant="topic" :count="pageSize" />

      <!-- 专题列表 -->
      <template v-else>
        <div v-if="topics.length" class="topic-grid">
          <NuxtLink
            v-for="item in topics"
            :key="item.id"
            :to="`/topic/${item.id}`"
            class="topic-card"
          >
            <div class="card-cover">
              <img
                v-if="item.cover"
                :src="item.cover"
                :alt="item.title"
                loading="lazy"
                class="cover-img"
              />
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
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { topicApi, type TopicItem } from '~/api/topic'
import { scrollToTopOnMobilePagination } from '~/utils/paginationScroll'

useHead({ title: '专题' })

const topics = ref<TopicItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(6)
const loading = ref(true)

async function fetchTopics() {
  loading.value = true
  try {
    const res = await topicApi.list(currentPage.value, pageSize.value)
    topics.value = res.data.records
    total.value = res.data.total
  } catch { /* 静默 */ }
  finally { loading.value = false }
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

onMounted(() => fetchTopics())
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
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
    .cover-img { transform: scale(1.05); }
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
  transition: transform 0.35s ease;
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
</style>
