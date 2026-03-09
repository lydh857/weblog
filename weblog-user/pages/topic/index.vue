<template>
  <div class="topic-list-bg">
    <div class="topic-list-page">
      <h1 class="page-title">
        <Icon name="heroicons:book-open-20-solid" size="24" />
        专题
      </h1>

      <!-- 加载态 -->
      <div v-if="loading" class="loading-state">
        <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
        <span>加载中...</span>
      </div>

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
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchTopics()
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => fetchTopics())
</script>

<style scoped lang="scss">
.topic-list-bg {
  background: $color-bg-page;
  min-height: 100vh;
  .dark & { background: $color-dark-bg; }
}

.topic-list-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: $color-text;
  margin-bottom: 1.5rem;
  .dark & { color: $color-dark-text; }
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
  .dark & { background: #334155; }
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
  .dark & { color: #475569; }
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
  .dark & { color: #94a3b8; }
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.8rem;
  color: $color-text-muted;
  .dark & { color: #64748b; }
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
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 4rem;
  color: $color-text-muted;
}
.empty-state {
  text-align: center;
  padding: 4rem;
  color: #94a3b8;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
