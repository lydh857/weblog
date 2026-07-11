<template>
  <div class="category-page" :class="{ 'page-entered': pageEntered }">
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
      <template v-for="(item, index) in postGridItems" :key="item.key">
        <ArticleCard
          v-if="item.type === 'post'"
          :post="item.post"
          class="grid-enter-item"
          :class="{ 'card-entered': listEntered }"
          :style="{ '--enter-index': index }"
        />
        <PromotionArticleCard
          v-else
          :ad="item.ad"
          class="grid-enter-item"
          :class="{ 'card-entered': listEntered }"
          :style="{ '--enter-index': index }"
        />
      </template>
    </section>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <Icon name="heroicons:document-magnifying-glass" size="48" />
      <p>暂无符合条件的文章</p>
    </div>

    <!-- 分页 -->
    <Pagination
      v-if="!isMobileView"
      :total="total"
      :page-count="pageCount"
      :current-page="filters.pageNum"
      :page-size="filters.pageSize"
      :page-size-options="[20, 40, 60, 80]"
      @update:current-page="goToPage"
      @update:page-size="handlePageSizeChange"
    />

    <div v-if="isMobileView && totalPages > 1" ref="mobileLoadTriggerRef" class="mobile-load-trigger">
      <span v-if="mobileLoadingMore">加载中...</span>
      <span v-else-if="mobilePageNum >= totalPages">已到底</span>
      <span v-else>上滑加载更多</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { categoryApi, type CategoryTreeVO } from '~/api/content/category'
import { tagApi, type TagCloudVO } from '~/api/content/tag'
import { postApi, type PostVO } from '~/api/content/post'
import type { AdvertisementVO } from '~/api/marketing/promotion'
import { fetchCachedAdSlot } from '~/composables/cache/useNonCriticalApiCache'
import { buildCategoryPathById, findCategoryBySlug } from '~/utils/navigation/categoryRoute'
import { scrollToTopOnMobilePagination } from '~/utils/navigation/paginationScroll'
import { getErrorMessage } from '~/utils/security/error'

definePageMeta({
  path: '/category/:slug?',
  key: 'category-page',
})

useHead({ title: '分类浏览' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

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
const listCardAdsLoaded = ref(false)
const { isPromotionBlocked, detectPromotionBlocked } = usePromotionVisibility()
const total = ref(0)
const pageCount = ref(0)
const loading = ref(true)
const pageEntered = ref(false)
const listEntered = ref(true)
const hasInitialListRendered = ref(false)
const isMobileView = ref(false)
const mobilePageNum = ref(1)
const mobileLoadingMore = ref(false)
const mobileLoadTriggerRef = ref<HTMLElement | null>(null)
const MIN_SKELETON_MS = 220
let fetchPostsRequestId = 0
let listCardAdsPromise: Promise<void> | null = null
let mobileLoadObserver: IntersectionObserver | null = null
let observedMobileLoadTarget: Element | null = null
const totalPages = computed(() => Math.max(1, pageCount.value || Math.ceil(total.value / filters.pageSize) || 1))
const { setIndicator, clearIndicator } = useFloatingPageIndicator()

function waitFor(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

type CategoryGridItem =
  | { key: string; type: 'post'; post: PostVO }
  | { key: string; type: 'ad'; ad: AdvertisementVO }

const listCardAdPool = computed(() => {
  if (isPromotionBlocked.value) return []
  return listCardAds.value.filter(item => item.type === 'image' && Boolean(item.content))
})

const backendPageSize = computed(() => {
  const reservedAdCount = listCardAdPool.value.length ? 1 : 0
  return Math.max(1, filters.pageSize - reservedAdCount)
})

function resolveListCardAd(pageNo: number): AdvertisementVO | null {
  const pool = listCardAdPool.value
  if (!pool.length) return null
  if (pool.length === 1) return pool[0] ?? null
  return pool[(Math.max(1, pageNo) - 1) % pool.length] ?? null
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
async function fetchPosts(options: { pageNum?: number; append?: boolean } = {}) {
  const requestId = ++fetchPostsRequestId
  const startAt = Date.now()
  const targetPageNum = options.pageNum ?? filters.pageNum
  const append = options.append === true

  if (append) {
    mobileLoadingMore.value = true
  } else {
    loading.value = true
  }

  try {
    await fetchListCardAds()
    const params: Record<string, number | string | undefined> = {
      pageNum: targetPageNum,
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
    posts.value = append ? [...posts.value, ...res.data.records] : res.data.records
    total.value = res.data.total
    pageCount.value = Math.ceil(res.data.total / backendPageSize.value)
    if (!append) {
      filters.pageNum = targetPageNum
    }
    mobilePageNum.value = targetPageNum
  } catch (error: unknown) {
    if (requestId !== fetchPostsRequestId) {
      return
    }
    message.error(getErrorMessage(error, '访问受限，请稍后再试'))
    if (!append) {
      posts.value = []
      total.value = 0
      pageCount.value = 0
    }
  } finally {
    if (requestId !== fetchPostsRequestId) {
      return
    }
    if (!append) {
      const elapsed = Date.now() - startAt
      if (elapsed < MIN_SKELETON_MS) {
        await waitFor(MIN_SKELETON_MS - elapsed)
      }
      loading.value = false
    }
    mobileLoadingMore.value = false
  }
}

async function fetchListCardAds() {
  if (listCardAdsLoaded.value) return
  if (listCardAdsPromise) return listCardAdsPromise

  listCardAdsPromise = fetchListCardAdsOnce()
    .finally(() => {
      listCardAdsPromise = null
    })

  return listCardAdsPromise
}

async function fetchListCardAdsOnce() {
  try {
    const blocked = await detectPromotionBlocked()
    if (blocked) {
      listCardAds.value = []
      return
    }

    // 分类页与首页共用同一推广位，走短期缓存减少重复请求。
    listCardAds.value = await fetchCachedAdSlot('post_list_card', { ttlMs: 45_000 })
  } catch {
    listCardAds.value = []
  } finally {
    listCardAdsLoaded.value = true
  }
}

function syncMobileViewState() {
  if (!import.meta.client) {
    isMobileView.value = false
    return
  }
  isMobileView.value = window.innerWidth <= 768
}

async function loadMoreOnMobile() {
  if (!isMobileView.value || loading.value || mobileLoadingMore.value) {
    return
  }
  if (mobilePageNum.value >= totalPages.value) {
    return
  }
  await fetchPosts({ pageNum: mobilePageNum.value + 1, append: true })
}

function setupMobileLoadObserver() {
  if (!import.meta.client) {
    return
  }

  const shouldObserve = isMobileView.value && Boolean(mobileLoadTriggerRef.value) && mobilePageNum.value < totalPages.value
  if (!shouldObserve) {
    if (mobileLoadObserver && observedMobileLoadTarget) {
      mobileLoadObserver.unobserve(observedMobileLoadTarget)
      observedMobileLoadTarget = null
    }
    return
  }

  if (!mobileLoadObserver) {
    mobileLoadObserver = new IntersectionObserver((entries) => {
      const [entry] = entries
      if (entry?.isIntersecting) {
        void loadMoreOnMobile()
      }
    }, {
      root: null,
      rootMargin: '160px 0px 240px 0px',
      threshold: 0.01,
    })
  }

  const target = mobileLoadTriggerRef.value
  if (!target) {
    return
  }
  if (observedMobileLoadTarget !== target) {
    if (observedMobileLoadTarget) {
      mobileLoadObserver.unobserve(observedMobileLoadTarget)
    }
    mobileLoadObserver.observe(target)
    observedMobileLoadTarget = target
  }
}

/** 筛选变更后重置页码并刷新数据 */
async function resetAndFetch() {
  filters.pageNum = 1
  if (isMobileView.value) {
    await Promise.all([fetchTags(), fetchPosts({ pageNum: 1 })])
    return
  }
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
  if (isMobileView.value) {
    return
  }
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
  syncMobileViewState()
  if (import.meta.client) {
    window.addEventListener('resize', syncMobileViewState, { passive: true })
  }

  if (import.meta.client) {
    window.requestAnimationFrame(() => {
      pageEntered.value = true
    })
  }

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

onUnmounted(() => {
  if (import.meta.client) {
    window.removeEventListener('resize', syncMobileViewState)
  }
  mobileLoadObserver?.disconnect()
  mobileLoadObserver = null
  observedMobileLoadTarget = null
  clearIndicator()
})

watch(loading, (isLoading) => {
  if (isLoading) {
    if (!hasInitialListRendered.value) {
      return
    }
    listEntered.value = false
    return
  }

  if (!hasInitialListRendered.value) {
    hasInitialListRendered.value = true
    listEntered.value = true
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

watch([isMobileView, mobileLoadTriggerRef, mobilePageNum, totalPages, loading], () => {
  setupMobileLoadObserver()
  setIndicator({
    enabled: isMobileView.value && !loading.value,
    currentPage: isMobileView.value ? mobilePageNum.value : filters.pageNum,
    totalPages: totalPages.value,
  })
}, { immediate: true })
</script>

<style lang="scss" scoped>
.category-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.category-page.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

/* 双列文章列表 */
.post-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.25rem;

  > * { min-width: 0; }
}

:deep(.grid-enter-item) {
  opacity: 0;
  transform: translate3d(0, 12px, 0);
}

:deep(.grid-enter-item.card-entered) {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  transition: opacity 560ms cubic-bezier(0.22, 1, 0.36, 1), transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
  transition-delay: calc(40ms + min(var(--enter-index), 7) * 50ms);
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

.mobile-load-trigger {
  margin: 0.9rem auto 0;
  text-align: center;
  font-size: 0.78rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

@media (prefers-reduced-motion: reduce) {
  .category-page,
  .category-page.page-entered {
    opacity: 1;
    transform: none;
    transition: none;
  }

  :deep(.grid-enter-item),
  :deep(.grid-enter-item.card-entered) {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
