<template>
  <div class="search-page">
    <h1 class="page-title">搜索文章</h1>

    <div class="search-box">
      <Icon name="heroicons:magnifying-glass-20-solid" size="20" class="search-icon" />
      <input
        ref="searchInput"
        v-model="keyword"
        type="text"
        placeholder="输入关键词搜索..."
        class="search-input"
        @keyup.enter="doSearch"
      />
      <button v-if="keyword" class="clear-btn" @click="keyword = ''" aria-label="清除">
        <Icon name="heroicons:x-mark-20-solid" size="18" />
      </button>
      <button class="search-btn" @click="doSearch">搜索</button>
    </div>

    <div v-if="searched" class="search-results">
      <div v-if="loading" class="loading-state">
        <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
        <span>搜索中...</span>
      </div>

      <template v-else-if="result && result.hits.length">
        <p class="result-count">找到 <strong>{{ result.total }}</strong> 篇相关文章</p>

        <article v-for="hit in result.hits" :key="hit.id" class="result-item">
          <NuxtLink :to="`/post/${hit.slug}`" class="result-link">
            <h3 class="result-title" v-html="sanitizeHtml(hit.highlightTitle || hit.title)" />
            <p class="result-content" v-html="sanitizeHtml(hit.highlightContent || hit.summary)" />
          </NuxtLink>
        </article>

        <div v-if="result.totalPages > 1" class="pagination">
          <button class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">
            <Icon name="heroicons:chevron-left-20-solid" size="18" />
          </button>
          <span class="page-info">{{ currentPage }} / {{ result.totalPages }}</span>
          <button class="page-btn" :disabled="currentPage >= result.totalPages" @click="changePage(currentPage + 1)">
            <Icon name="heroicons:chevron-right-20-solid" size="18" />
          </button>
        </div>
      </template>

      <div v-else class="empty-state">
        <Icon name="heroicons:magnifying-glass-20-solid" size="48" />
        <p>未找到相关文章</p>
        <p class="empty-hint">试试其他关键词</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { searchApi, type SearchResult } from '~/api/search'
import { sanitizeHtml } from '~/utils/xss'

useHead({ title: '搜索' })

const route = useRoute()
const searchInput = ref<HTMLInputElement>()
const keyword = ref((route.query.q as string) || '')
const result = ref<SearchResult | null>(null)
const loading = ref(false)
const searched = ref(false)
const currentPage = ref(1)

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return
  loading.value = true
  searched.value = true
  try {
    const res = await searchApi.search({ keyword: q, pageNum: currentPage.value, pageSize: 10 })
    result.value = res.data
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  doSearch()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  searchInput.value?.focus()
  if (keyword.value) doSearch()
})
</script>

<style scoped lang="scss">
.search-page { max-width: 800px; margin: 0 auto; padding: 2rem 1.5rem; }
.page-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem; }

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 2rem;
  .search-icon { position: absolute; left: 0.875rem; color: #94a3b8; pointer-events: none; z-index: 1; }
  .search-input {
    flex: 1;
    padding: 0.75rem 2.5rem 0.75rem 2.75rem;
    border: 1px solid $color-border;
    border-radius: $radius-lg;
    font-size: 1rem;
    background: $color-bg-secondary;
    color: $color-text;
    outline: none;
    transition: border-color 0.2s, box-shadow 0.2s;
    &:focus { border-color: $color-primary; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }
    .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; color: $color-dark-text; }
  }
  .clear-btn {
    position: absolute;
    right: 5.5rem;
    border: none;
    background: none;
    color: #94a3b8;
    cursor: pointer;
    padding: 0.25rem;
    display: flex;
    &:hover { color: $color-text; }
  }
  .search-btn {
    padding: 0.75rem 1.25rem;
    border: none;
    border-radius: $radius-lg;
    background: $color-primary;
    color: #fff;
    font-size: 0.95rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.2s;
    min-width: 44px;
    min-height: 44px;
    &:hover { background: $color-primary-dark; }
  }
}

.result-count {
  font-size: 0.9rem;
  color: $color-text-muted;
  margin-bottom: 1.25rem;
  strong { color: $color-primary; }
}

.result-item {
  padding: 1rem 0;
  border-bottom: 1px solid $color-border;
  .dark & { border-bottom-color: $color-dark-border; }
}
.result-link {
  display: block;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  &:hover .result-title { color: $color-primary; }
}
.result-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 0.375rem;
  color: $color-text;
  transition: color 0.2s;
  .dark & { color: $color-dark-text; }
  :deep(em), :deep(mark) { color: #ef4444; font-style: normal; font-weight: 700; background: none; }
}
.result-content {
  font-size: 0.875rem;
  color: $color-text-muted;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  .dark & { color: #94a3b8; }
  :deep(em), :deep(mark) { color: #ef4444; font-style: normal; font-weight: 600; background: none; }
}

.pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 2rem; }
.page-btn {
  width: 44px; height: 44px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid $color-border; border-radius: $radius-md;
  background: transparent; color: $color-text; cursor: pointer;
  transition: all 0.2s;
  &:hover:not(:disabled) { border-color: $color-primary; color: $color-primary; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  .dark & { border-color: $color-dark-border; color: $color-dark-text; }
}
.page-info { font-size: 0.9rem; color: $color-text-muted; }

.loading-state { display: flex; align-items: center; justify-content: center; gap: 0.5rem; padding: 3rem; color: $color-text-muted; }
.empty-state { text-align: center; padding: 4rem 1rem; color: #94a3b8; p { margin-top: 0.75rem; } }
.empty-hint { font-size: 0.85rem; opacity: 0.7; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: $breakpoint-md) {
  .search-page { padding: 1.5rem 1rem; }
}
</style>
