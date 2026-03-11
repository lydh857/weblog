<template>
  <div class="category-page">
    <!-- 筛选器 -->
    <CategoryFilter
      :categories="categories"
      :tags="tags"
      :selected-category-id="filters.categoryId"
      :selected-sub-category-id="filters.subCategoryId"
      :selected-tag-id="filters.tagId"
      :sort-by="filters.sortBy"
      :page-size="filters.pageSize"
      @update:selected-category-id="handleCategoryChange"
      @update:selected-sub-category-id="handleSubCategoryChange"
      @update:selected-tag-id="handleTagChange"
      @update:sort-by="handleSortChange"
      @update:page-size="handlePageSizeChange"
    />

    <!-- 文章列表：双列布局 -->
    <section v-if="!loading && posts.length" class="post-grid">
      <ArticleCard v-for="post in posts" :key="post.id" :post="post" />
    </section>

    <!-- 空状态 -->
    <div v-if="!loading && !posts.length" class="empty-state">
      <Icon name="heroicons:document-magnifying-glass" size="48" />
      <p>暂无符合条件的文章</p>
    </div>

    <!-- 加载骨架屏 -->
    <div v-if="loading" class="skeleton-grid">
      <div v-for="i in 4" :key="i" class="skeleton-card">
        <div class="skeleton-cover" />
        <div class="skeleton-content">
          <div class="skeleton-line short" />
          <div class="skeleton-line" />
          <div class="skeleton-line long" />
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <Pagination
      :total="total"
      :current-page="filters.pageNum"
      :page-size="filters.pageSize"
      @update:current-page="goToPage"
      @update:page-size="handlePageSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { categoryApi, type CategoryTreeVO } from '~/api/category'
import { tagApi, type TagCloudVO } from '~/api/tag'
import { postApi, type PostVO } from '~/api/post'

useHead({ title: '分类浏览' })

const route = useRoute()
const router = useRouter()

const filters = reactive({
  categoryId: null as number | null,
  subCategoryId: null as number | null,
  tagId: null as number | null,
  sortBy: 'recommended' as 'recommended' | 'latest' | 'hottest',
  pageSize: 20,
  pageNum: 1,
})

const categories = ref<CategoryTreeVO[]>([])
const tags = ref<TagCloudVO[]>([])
const posts = ref<PostVO[]>([])
const total = ref(0)
const loading = ref(false)

/** 从 URL 查询参数初始化筛选状态 */
function parseQueryParams() {
  const q = route.query
  filters.categoryId = q.categoryId ? Number(q.categoryId) : null
  filters.subCategoryId = q.subCategoryId ? Number(q.subCategoryId) : null
  filters.tagId = q.tagId ? Number(q.tagId) : null
  filters.sortBy = (['recommended', 'latest', 'hottest'].includes(q.sortBy as string) ? q.sortBy : 'recommended') as 'recommended' | 'latest' | 'hottest'
  filters.pageSize = q.pageSize ? Number(q.pageSize) : 20
  filters.pageNum = q.pageNum ? Number(q.pageNum) : 1
}

/** 同步筛选状态到 URL */
function syncQueryParams() {
  const query: Record<string, string> = {}
  if (filters.categoryId !== null) query.categoryId = String(filters.categoryId)
  if (filters.subCategoryId !== null) query.subCategoryId = String(filters.subCategoryId)
  if (filters.tagId !== null) query.tagId = String(filters.tagId)
  if (filters.sortBy !== 'recommended') query.sortBy = filters.sortBy
  if (filters.pageSize !== 20) query.pageSize = String(filters.pageSize)
  if (filters.pageNum !== 1) query.pageNum = String(filters.pageNum)
  router.replace({ query })
}

/** 加载标签（按当前分类筛选） */
async function fetchTags() {
  try {
    const params: { categoryId?: number; subCategoryId?: number } = {}
    if (filters.subCategoryId !== null) {
      params.subCategoryId = filters.subCategoryId
    } else if (filters.categoryId !== null) {
      params.categoryId = filters.categoryId
    }
    const res = await tagApi.cloud(params)
    tags.value = res.data
  } catch {
    tags.value = []
  }
}

/** 加载文章列表 */
async function fetchPosts() {
  loading.value = true
  try {
    const params: Record<string, number | string | undefined> = {
      pageNum: filters.pageNum,
      pageSize: filters.pageSize,
    }
    if (filters.subCategoryId !== null) {
      params.categoryId = filters.subCategoryId
    } else if (filters.categoryId !== null) {
      params.categoryId = filters.categoryId
    }
    if (filters.tagId !== null) params.tagId = filters.tagId
    params.sortBy = filters.sortBy

    const res = await postApi.list(params as Parameters<typeof postApi.list>[0])
    posts.value = res.data.records
    total.value = res.data.total
  } catch {
    posts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 筛选变更后重置页码并刷新数据 */
function resetAndFetch() {
  filters.pageNum = 1
  syncQueryParams()
  fetchPosts()
}

function handleCategoryChange(id: number | null) {
  filters.categoryId = id
  filters.subCategoryId = null
  filters.tagId = null
  filters.pageNum = 1
  syncQueryParams()
  Promise.all([fetchTags(), fetchPosts()])
}

function handleSubCategoryChange(id: number | null) {
  filters.subCategoryId = id
  filters.tagId = null
  filters.pageNum = 1
  syncQueryParams()
  Promise.all([fetchTags(), fetchPosts()])
}

function handleTagChange(id: number | null) {
  filters.tagId = id
  resetAndFetch()
}

function handleSortChange(sortBy: 'recommended' | 'latest' | 'hottest') {
  filters.sortBy = sortBy
  resetAndFetch()
}

function handlePageSizeChange(size: number) {
  filters.pageSize = size
  resetAndFetch()
}

function goToPage(page: number) {
  filters.pageNum = page
  syncQueryParams()
  fetchPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 监听浏览器前进/后退，重新同步查询参数
watch(() => route.query, () => {
  parseQueryParams()
  fetchPosts()
})

onMounted(async () => {
  parseQueryParams()
  const [catRes] = await Promise.all([categoryApi.tree(), fetchTags()])
  categories.value = catRes.data
  await fetchPosts()
})
</script>

<style lang="scss" scoped>
.category-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

/* 双列文章列表 */
.post-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.25rem;

  > * { min-width: 0; }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  padding: 4rem 0;
  color: $color-text-muted;
  .dark & { color: #64748b; }
  p { font-size: 0.95rem; }
}

/* 骨架屏：双列 */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.25rem;
}

.skeleton-card {
  display: flex;
  height: calc(240px * 9 / 16);
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-bg;
  .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; }
}

.skeleton-cover {
  flex-shrink: 0;
  width: 240px;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
  .dark & { background: #1a2332; }
}

.skeleton-content {
  flex: 1;
  padding: $spacing-md $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.skeleton-line {
  height: 14px;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
  &.short { width: 30%; }
  &.long { width: 80%; }
  .dark & { background: #1a2332; }
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 响应式 */
@media (max-width: $breakpoint-md) {
  .category-page {
    padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
  }

  .post-grid,
  .skeleton-grid {
    grid-template-columns: 1fr;
  }

  .skeleton-card {
    height: calc(180px * 9 / 16);
  }

  .skeleton-cover {
    width: 180px;
  }
}

@media (max-width: 480px) {
  .skeleton-card {
    flex-direction: column;
    height: auto;
  }

  .skeleton-cover {
    width: 100%;
    height: 160px;
  }
}
</style>
