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

    <section v-if="loading" class="post-grid">
      <UnifiedSkeleton class="home-load-more-skeleton" variant="article" :count="8" />
    </section>

    <!-- 文章列表：双列布局 -->
    <section v-else-if="posts.length" class="post-grid">
      <template v-for="item in postGridItems" :key="item.key">
        <ArticleCard v-if="item.type === 'post'" :post="item.post" />
        <AdMimicCard v-else :ad="item.ad" />
      </template>
    </section>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <Icon name="heroicons:document-magnifying-glass" size="48" />
      <p>暂无符合条件的文章</p>
    </div>

    <!-- 分页 -->
    <Pagination
      :total="total"
      :page-count="pageCount"
      :current-page="filters.pageNum"
      :page-size="filters.pageSize"
      :page-size-options="[20, 40, 60, 80]"
      @update:current-page="goToPage"
      @update:page-size="handlePageSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { categoryApi, type CategoryTreeVO } from '~/api/category'
import { tagApi, type TagCloudVO } from '~/api/tag'
import { postApi, type PostVO } from '~/api/post'
import { advertisementApi, type AdvertisementVO } from '~/api/advertisement'
import { buildCategoryPathById, findCategoryBySlug } from '~/utils/categoryRoute'
import { scrollToTopOnMobilePagination } from '~/utils/paginationScroll'

definePageMeta({
  path: '/category/:slug?',
  key: 'category-page',
})

useHead({ title: '分类浏览' })

const route = useRoute()
const router = useRouter()

function parseNumberParam(value: unknown): number | null {
  const normalized = Array.isArray(value) ? value[0] : value
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeDisplayPageSize(value: number | null | undefined) {
  if (!value || Number.isNaN(value)) return 20
  if (value <= 20) return 20
  return Math.ceil(value / 20) * 20
}

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
const listCardAds = ref<AdvertisementVO[]>([])
const total = ref(0)
const pageCount = ref(0)
const loading = ref(true)
const MIN_SKELETON_MS = 220
let fetchPostsRequestId = 0

function waitFor(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

type CategoryGridItem =
  | { key: string; type: 'post'; post: PostVO }
  | { key: string; type: 'ad'; ad: AdvertisementVO }

const listCardAdPool = computed(() => listCardAds.value.filter(item => item.type === 'image' && Boolean(item.content)))

const backendPageSize = computed(() => Math.max(1, filters.pageSize - 1))

function resolveListCardAd(pageNo: number): AdvertisementVO | null {
  const pool = listCardAdPool.value
  if (!pool.length) return null
  if (pool.length === 1) return pool[0]
  return pool[(Math.max(1, pageNo) - 1) % pool.length]
}

const postGridItems = computed<CategoryGridItem[]>(() => {
  const items: CategoryGridItem[] = posts.value.map(post => ({ key: `post-${post.id}`, type: 'post', post }))
  const ad = resolveListCardAd(filters.pageNum)
  if (!ad) return items

  const insertAfter = Math.max(1, ad.insertAfter || 4)
  const insertIndex = Math.min(insertAfter, items.length)
  items.splice(insertIndex, 0, {
    key: `ad-${ad.id}-p${filters.pageNum}`,
    type: 'ad',
    ad,
  })
  return items
})

/** 从 URL 查询参数初始化筛选状态 */
function parseQueryParams() {
  const q = route.query
  let nextCategoryId = parseNumberParam(q.categoryId)
  let nextSubCategoryId = parseNumberParam(q.subCategoryId)
  const nextTagId = parseNumberParam(q.tagId)
  const nextSortBy = (['recommended', 'latest', 'hottest'].includes(q.sortBy as string) ? q.sortBy : 'recommended') as 'recommended' | 'latest' | 'hottest'
  const nextPageSize = normalizeDisplayPageSize(parseNumberParam(q.pageSize) ?? 20)
  const nextPageNum = parseNumberParam(q.pageNum) ?? 1

  const paramSlug = Array.isArray(route.params.slug)
    ? (route.params.slug[0] || '')
    : (typeof route.params.slug === 'string' ? route.params.slug : '')
  const pathSlug = route.path.startsWith('/category/')
    ? decodeURIComponent(route.path.slice('/category/'.length).split('/')[0] || '')
    : ''
  const slugParam = (paramSlug || pathSlug || '').trim()
  if (slugParam) {
    const matched = findCategoryBySlug(categories.value, slugParam)
    if (matched.category) {
      nextCategoryId = matched.category.id
      nextSubCategoryId = matched.subCategory?.id ?? null
    }
  }

  if (filters.categoryId !== nextCategoryId) filters.categoryId = nextCategoryId
  if (filters.subCategoryId !== nextSubCategoryId) filters.subCategoryId = nextSubCategoryId
  if (filters.tagId !== nextTagId) filters.tagId = nextTagId
  if (filters.sortBy !== nextSortBy) filters.sortBy = nextSortBy
  if (filters.pageSize !== nextPageSize) filters.pageSize = nextPageSize
  if (filters.pageNum !== nextPageNum) filters.pageNum = nextPageNum
}

/** 同步筛选状态到 URL */
async function syncQueryParams() {
  const query: Record<string, string> = {}
  if (filters.tagId !== null) query.tagId = String(filters.tagId)
  if (filters.sortBy !== 'recommended') query.sortBy = filters.sortBy
  if (filters.pageSize !== 20) query.pageSize = String(filters.pageSize)
  if (filters.pageNum !== 1) query.pageNum = String(filters.pageNum)
  await router.replace({ query })
}

function buildSeoQuery(options?: { keepTag?: boolean }): Record<string, string> {
  const query: Record<string, string> = {}
  if (options?.keepTag && filters.tagId !== null) query.tagId = String(filters.tagId)
  if (filters.sortBy !== 'recommended') query.sortBy = filters.sortBy
  if (filters.pageSize !== 20) query.pageSize = String(filters.pageSize)
  if (filters.pageNum !== 1) query.pageNum = String(filters.pageNum)
  return query
}

function buildCategoryRouteQuery(
  query: Record<string, string>,
  categoryId: number | null,
  subCategoryId: number | null,
): { path: string; query: Record<string, string> } {
  const path = buildCategoryPathById(categories.value, categoryId, subCategoryId)
  if (path !== '/category') {
    return { path, query }
  }

  const fallbackQuery: Record<string, string> = { ...query }
  if (subCategoryId !== null) {
    fallbackQuery.subCategoryId = String(subCategoryId)
  } else if (categoryId !== null) {
    fallbackQuery.categoryId = String(categoryId)
  }

  return { path: '/category', query: fallbackQuery }
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
  const requestId = ++fetchPostsRequestId
  const startAt = Date.now()
  loading.value = true
  try {
    const params: Record<string, number | string | undefined> = {
      pageNum: filters.pageNum,
      pageSize: backendPageSize.value,
    }
    if (filters.subCategoryId !== null) {
      params.categoryId = filters.subCategoryId
    } else if (filters.categoryId !== null) {
      params.categoryId = filters.categoryId
    }
    if (filters.tagId !== null) params.tagId = filters.tagId
    params.sortBy = filters.sortBy

    const res = await postApi.list(params as Parameters<typeof postApi.list>[0])
    if (requestId !== fetchPostsRequestId) {
      return
    }
    posts.value = res.data.records
    total.value = res.data.total
    pageCount.value = Math.ceil(res.data.total / filters.pageSize)
  } catch {
    if (requestId !== fetchPostsRequestId) {
      return
    }
    posts.value = []
    total.value = 0
    pageCount.value = 0
  } finally {
    if (requestId !== fetchPostsRequestId) {
      return
    }
    const elapsed = Date.now() - startAt
    if (elapsed < MIN_SKELETON_MS) {
      await waitFor(MIN_SKELETON_MS - elapsed)
    }
    loading.value = false
  }
}

async function fetchListCardAds() {
  try {
    const res = await advertisementApi.getBySlot('post_list_card')
    listCardAds.value = res.data || []
  } catch {
    listCardAds.value = []
  }
}

/** 筛选变更后重置页码并刷新数据 */
async function resetAndFetch() {
  filters.pageNum = 1
  await syncQueryParams()
}

async function handleCategoryChange(id: number | null) {
  const nextCategoryId = id === null ? null : Number(id)
  if (filters.categoryId === nextCategoryId && filters.subCategoryId === null && filters.tagId === null && filters.pageNum === 1) {
    return
  }
  const targetCategoryId = Number.isFinite(nextCategoryId as number) ? nextCategoryId : null
  const query: Record<string, string> = {}
  if (filters.sortBy !== 'recommended') query.sortBy = filters.sortBy
  if (filters.pageSize !== 20) query.pageSize = String(filters.pageSize)
  const target = buildCategoryRouteQuery(query, targetCategoryId, null)
  await navigateTo(target)
}

async function handleSubCategoryChange(id: number | null) {
  const nextSubCategoryId = id === null ? null : Number(id)
  if (filters.subCategoryId === nextSubCategoryId && filters.tagId === null && filters.pageNum === 1) {
    return
  }
  const targetSubCategoryId = Number.isFinite(nextSubCategoryId as number) ? nextSubCategoryId : null
  const query: Record<string, string> = {}
  if (filters.sortBy !== 'recommended') query.sortBy = filters.sortBy
  if (filters.pageSize !== 20) query.pageSize = String(filters.pageSize)
  const target = buildCategoryRouteQuery(query, filters.categoryId, targetSubCategoryId)
  await navigateTo(target)
}

function handleTagChange(id: number | null) {
  filters.tagId = id
  void resetAndFetch()
}

function handleSortChange(sortBy: 'recommended' | 'latest' | 'hottest') {
  filters.sortBy = sortBy
  void resetAndFetch()
}

function handlePageSizeChange(size: number) {
  filters.pageSize = normalizeDisplayPageSize(size)
  void resetAndFetch()
  scrollToTopOnMobilePagination()
}

async function goToPage(page: number) {
  filters.pageNum = page
  await syncQueryParams()
  if (scrollToTopOnMobilePagination()) return
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 监听浏览器前进/后退，重新同步查询参数
watch(() => route.fullPath, () => {
  parseQueryParams()
  Promise.all([fetchTags(), fetchPosts()])
})

onMounted(async () => {
  parseQueryParams()
  const [catRes] = await Promise.all([categoryApi.tree(), fetchListCardAds()])
  categories.value = catRes.data
  parseQueryParams()

  const hasLegacyCategoryQuery = Number.isFinite(Number(route.query.categoryId)) || Number.isFinite(Number(route.query.subCategoryId))
  if (hasLegacyCategoryQuery) {
    const targetPath = buildCategoryPathById(
      categories.value,
      route.query.categoryId ? Number(route.query.categoryId) : null,
      route.query.subCategoryId ? Number(route.query.subCategoryId) : null,
    )
    if (targetPath !== '/category') {
      const nextQuery: Record<string, string> = {}
      if (typeof route.query.tagId === 'string') nextQuery.tagId = route.query.tagId
      if (typeof route.query.sortBy === 'string') nextQuery.sortBy = route.query.sortBy
      if (typeof route.query.pageSize === 'string') nextQuery.pageSize = route.query.pageSize
      if (typeof route.query.pageNum === 'string') nextQuery.pageNum = route.query.pageNum
      await navigateTo({ path: targetPath, query: nextQuery }, { replace: true })
      return
    }
  }

  await Promise.all([fetchTags(), fetchPosts()])
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
  .dark & { color: $color-dark-text-muted; }
  p { font-size: 0.95rem; }
}

:deep(.home-load-more-skeleton) {
  display: contents;
}

:deep(.dark .home-load-more-skeleton .skeleton-item) {
  background: $color-dark-bg-secondary;
  border-color: $color-dark-border;
}

:deep(.dark .home-load-more-skeleton .skeleton-cover) {
  background:
    radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.11), transparent 45%),
    radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.08), transparent 52%),
    linear-gradient(180deg, #171b20, #101215);
}

/* 响应式 */
@media (max-width: $breakpoint-md) {
  .category-page {
    padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
  }

  .post-grid {
    grid-template-columns: 1fr;
  }
}
</style>
