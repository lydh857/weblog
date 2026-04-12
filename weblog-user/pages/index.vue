<template>
  <div class="home-page">
    <!-- 轮播区块 -->
    <HeroCarousel />

    <div class="home-content">
      <!-- 今日发布 -->
      <div
        ref="todaySectionRef"
        class="home-reveal"
        style="--reveal-delay: 40ms"
        :class="{ 'is-visible': sectionVisible.today }"
      >
        <TodayPostGrid />
      </div>

      <!-- 热门文章 -->
      <div
        ref="hotSectionRef"
        class="home-reveal"
        style="--reveal-delay: 130ms"
        :class="{ 'is-visible': sectionVisible.hot }"
      >
        <LazyHotPostGrid v-if="sectionMounted.hot" />
        <div v-else class="section-defer-placeholder" aria-hidden="true" />
      </div>

      <!-- 文章排行榜 -->
      <div
        ref="rankingSectionRef"
        class="home-reveal"
        style="--reveal-delay: 200ms"
        :class="{ 'is-visible': sectionVisible.ranking }"
      >
        <LazyHomeRankingSection v-if="sectionMounted.ranking" />
        <div v-else class="section-defer-placeholder section-defer-placeholder--ranking" aria-hidden="true" />
      </div>

      <!-- 文章列表 -->
      <section
        ref="postSectionRef"
        class="post-section home-reveal"
        style="--reveal-delay: 280ms"
        :class="{ 'is-visible': sectionVisible.post }"
      >
            <div class="section-header">
              <div class="section-title-group">
                <h2 class="section-title">推荐文章</h2>
                <p class="section-desc">精选内容，值得一读</p>
              </div>
            </div>

            <div v-if="!sectionMounted.post" class="post-grid post-grid--defer" aria-hidden="true">
              <!-- 保持与已挂载加载态一致的骨架高度，避免占位切换导致明显位移 -->
              <UnifiedSkeleton class="home-load-more-skeleton" variant="article" :count="8" />
            </div>

            <div v-else-if="loading && !posts.length" class="post-grid">
              <UnifiedSkeleton class="home-load-more-skeleton" variant="article" :count="8" />
            </div>

            <div v-else-if="posts.length" class="post-grid">
              <template v-for="item in postGridItems" :key="item.key">
                <ArticleCard
                  v-if="item.type === 'post'"
                  :post="item.post"
                  class="home-post-card"
                />
                <AdMimicCard v-else :ad="item.ad" class="post-grid-ad" />
              </template>
              <template v-if="loadingMore">
                <UnifiedSkeleton class="home-load-more-skeleton" variant="article" :count="8" />
              </template>
            </div>

            <!-- 加载更多 -->
            <div v-if="sectionMounted.post && posts.length && !loadingMore" class="load-more-container">
              <div v-if="noMore" class="no-more">没有更多文章了</div>
              <button v-else class="load-more-btn" @click="loadMore">
                <svg class="load-more-icon" viewBox="0 0 24 24" width="18" height="18">
                  <path fill="currentColor" d="M12 4V2.21c0-.45-.54-.67-.85-.35l-2.8 2.79c-.2.2-.2.51 0 .71l2.8 2.79c.31.31.85.09.85-.35V6c3.31 0 6 2.69 6 6 0 .79-.15 1.56-.44 2.25-.15.36-.04.77.23 1.04.51.51 1.37.33 1.64-.34.37-.91.57-1.91.57-2.95 0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-.79.15-1.56.44-2.25.15-.36.04-.77-.23-1.04-.51-.51-1.37-.33-1.64.34C4.2 9.96 4 10.96 4 12c0 4.42 3.58 8 8 8v1.79c0 .45.54.67.85.35l2.8-2.79c.2-.2.2-.51 0-.71l-2.8-2.79c-.31-.31-.85-.09-.85.35V18z" />
                </svg>
                加载更多
              </button>
            </div>

            <div v-if="sectionMounted.post && !loading && !posts.length" class="empty-state">
              <Icon name="heroicons:document-text-20-solid" size="48" />
              <p>暂无文章</p>
            </div>
      </section>
    </div>

  </div>
</template>

<script setup lang="ts">
import type { PostVO } from '~/api/content/post'
import type { AdvertisementVO } from '~/api/marketing/advertisement'
import { getErrorMessage } from '~/utils/security/error'

useHead({ title: 'Weblog - 首页' })

const posts = ref<PostVO[]>([])
const message = useMessage()
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const currentPage = ref(1)
const pageSize = 19
const hasLoadedInitialPosts = ref(false)
const MIN_HOME_INITIAL_SKELETON_MS = 220
const MIN_HOME_LOAD_MORE_SKELETON_MS = 180
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const HOME_REVEAL_BOOTSTRAP_DELAY = 140
const todaySectionRef = ref<HTMLElement | null>(null)
const hotSectionRef = ref<HTMLElement | null>(null)
const rankingSectionRef = ref<HTMLElement | null>(null)
const postSectionRef = ref<HTMLElement | null>(null)

type HomeSectionKey = 'today' | 'hot' | 'ranking' | 'post'

const sectionRevealDelays: Record<HomeSectionKey, number> = {
  today: 90,
  hot: 180,
  ranking: 270,
  post: 360,
}

const sectionVisible = reactive<Record<HomeSectionKey, boolean>>({
  today: false,
  hot: false,
  ranking: false,
  post: false
})

const sectionMounted = reactive<Record<HomeSectionKey, boolean>>({
  today: true,
  hot: false,
  ranking: false,
  post: false
})

const isStartupDone = ref(false)
const hasStartedHomeReveal = ref(false)

let sectionObserver: IntersectionObserver | null = null
let startupRevealTimer: ReturnType<typeof setTimeout> | null = null
const sectionTargetMap = new Map<Element, HomeSectionKey>()
const sectionRevealTimerMap = new Map<HomeSectionKey, ReturnType<typeof setTimeout>>()
const listCardAds = ref<AdvertisementVO[]>([])
type PostGridItem =
  | { key: string; type: 'post'; post: PostVO }
  | { key: string; type: 'ad'; ad: AdvertisementVO }

const listCardAdPool = computed(() => listCardAds.value.filter(item => item.type === 'image' && Boolean(item.content)))

type PostApi = typeof import('~/api/content/post')['postApi']
type FetchCachedAdSlot = typeof import('~/composables/cache/useNonCriticalApiCache')['fetchCachedAdSlot']

let postApiPromise: Promise<PostApi> | null = null
let fetchCachedAdSlotPromise: Promise<FetchCachedAdSlot> | null = null

async function getPostApi(): Promise<PostApi> {
  if (!postApiPromise) {
    postApiPromise = import('~/api/content/post').then(module => module.postApi)
  }
  return postApiPromise
}

async function getFetchCachedAdSlot(): Promise<FetchCachedAdSlot> {
  if (!fetchCachedAdSlotPromise) {
    fetchCachedAdSlotPromise = import('~/composables/cache/useNonCriticalApiCache').then(module => module.fetchCachedAdSlot)
  }
  return fetchCachedAdSlotPromise
}

function resolveListCardAd(pageNo: number): AdvertisementVO | null {
  const pool = listCardAdPool.value
  if (!pool.length) return null
  if (pool.length === 1) return pool[0] ?? null
  return pool[(Math.max(1, pageNo) - 1) % pool.length] ?? null
}

const postGridItems = computed<PostGridItem[]>(() => {
  const items: PostGridItem[] = []
  if (!posts.value.length) return items

  const pageCount = Math.ceil(posts.value.length / pageSize)
  for (let pageNo = 1; pageNo <= pageCount; pageNo += 1) {
    const start = (pageNo - 1) * pageSize
    const end = start + pageSize
    const chunk = posts.value.slice(start, end)
    const chunkItems: PostGridItem[] = chunk.map(post => ({ key: `post-${post.id}`, type: 'post', post }))

    const ad = resolveListCardAd(pageNo)
    if (ad) {
      const insertAfter = Math.max(1, ad.insertAfter || 4)
      const insertIndex = Math.min(insertAfter, chunkItems.length)
      chunkItems.splice(insertIndex, 0, {
        key: `ad-${ad.id}-p${pageNo}`,
        type: 'ad',
        ad,
      })
    }

    items.push(...chunkItems)
  }

  return items
})

function markSectionVisible(key: HomeSectionKey) {
  if (sectionVisible[key]) return
  sectionVisible[key] = true
}

function ensureSectionMounted(key: HomeSectionKey) {
  if (sectionMounted[key]) return
  sectionMounted[key] = true

  if (key === 'post') {
    loadPosts()
  }
}

function hasStartupDone() {
  if (!import.meta.client) {
    return false
  }

  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  return Boolean(runtimeWindow.__weblogStartupDone)
}

function clearSectionRevealTimers() {
  sectionRevealTimerMap.forEach((timer) => {
    clearTimeout(timer)
  })
  sectionRevealTimerMap.clear()
}

function scheduleSectionReveal(key: HomeSectionKey) {
  if (sectionVisible[key] || sectionRevealTimerMap.has(key)) {
    return
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const delay = prefersReducedMotion ? 0 : sectionRevealDelays[key]
  const timer = window.setTimeout(() => {
    sectionRevealTimerMap.delete(key)
    markSectionVisible(key)
    ensureSectionMounted(key)
  }, delay)

  sectionRevealTimerMap.set(key, timer)
}

function startHomeReveal() {
  if (!import.meta.client || hasStartedHomeReveal.value) {
    return
  }

  hasStartedHomeReveal.value = true
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const delay = prefersReducedMotion ? 0 : HOME_REVEAL_BOOTSTRAP_DELAY

  startupRevealTimer = window.setTimeout(() => {
    startupRevealTimer = null
    nextTick(() => {
      initSectionObserver()
    })
  }, delay)
}

function handleStartupDone() {
  if (isStartupDone.value) {
    return
  }

  isStartupDone.value = true
  startHomeReveal()
}

function initSectionObserver() {
  if (!import.meta.client) return

  sectionObserver?.disconnect()
  sectionTargetMap.clear()

  sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      const key = sectionTargetMap.get(entry.target)
      if (!key) return
      scheduleSectionReveal(key)
      sectionObserver?.unobserve(entry.target)
    })
  }, {
    // 提前在视口外触发挂载，让占位到真实内容的切换尽量发生在屏幕外。
    threshold: 0.01,
    rootMargin: '280px 0px'
  })

  const sections: Array<[HomeSectionKey, HTMLElement | null]> = [
    ['today', todaySectionRef.value],
    ['hot', hotSectionRef.value],
    ['ranking', rankingSectionRef.value],
    ['post', postSectionRef.value]
  ]

  sections.forEach(([key, el]) => {
    if (!el) return
    sectionTargetMap.set(el, key)
    sectionObserver?.observe(el)
  })

  requestAnimationFrame(() => {
    scheduleSectionReveal('today')
  })
}

async function loadListCardAds() {
  try {
    const fetchCachedAdSlot = await getFetchCachedAdSlot()
    // 首页拟态卡广告属于非关键数据，使用短期缓存避免重复请求。
    listCardAds.value = await fetchCachedAdSlot('post_list_card', { ttlMs: 45_000 })
  } catch {
    listCardAds.value = []
  }
}

async function loadPosts() {
  if (loading.value || hasLoadedInitialPosts.value) return

  const startAt = Date.now()
  loading.value = true
  try {
    const postApi = await getPostApi()
    const res = await postApi.list({ pageNum: 1, pageSize })
    posts.value = res.data.records
    noMore.value = res.data.records.length < pageSize || res.data.pages <= 1
    currentPage.value = 1
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '访问受限，请稍后再试'))
  }
  finally {
    const elapsed = Date.now() - startAt
    if (elapsed < MIN_HOME_INITIAL_SKELETON_MS) {
      await new Promise<void>(resolve => setTimeout(resolve, MIN_HOME_INITIAL_SKELETON_MS - elapsed))
    }
    loading.value = false
    hasLoadedInitialPosts.value = true
  }
}

async function loadMore() {
  if (loadingMore.value || noMore.value) return
  const startAt = Date.now()
  loadingMore.value = true
  try {
    const postApi = await getPostApi()
    const nextPage = currentPage.value + 1
    const res = await postApi.list({ pageNum: nextPage, pageSize })
    const appendedPosts = res.data.records
    posts.value = [...posts.value, ...appendedPosts]
    currentPage.value = nextPage
    noMore.value = appendedPosts.length < pageSize || nextPage >= res.data.pages
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '访问受限，请稍后再试'))
  }
  finally {
    const elapsed = Date.now() - startAt
    if (elapsed < MIN_HOME_LOAD_MORE_SKELETON_MS) {
      await new Promise<void>(resolve => setTimeout(resolve, MIN_HOME_LOAD_MORE_SKELETON_MS - elapsed))
    }
    loadingMore.value = false
  }
}

onMounted(() => {
  loadListCardAds()

  if (!import.meta.client) {
    return
  }

  isStartupDone.value = hasStartupDone()
  if (isStartupDone.value) {
    startHomeReveal()
    return
  }

  window.addEventListener(STARTUP_DONE_EVENT, handleStartupDone)
})

onUnmounted(() => {
  if (import.meta.client) {
    window.removeEventListener(STARTUP_DONE_EVENT, handleStartupDone)
  }

  if (startupRevealTimer) {
    clearTimeout(startupRevealTimer)
    startupRevealTimer = null
  }

  sectionObserver?.disconnect()
  sectionTargetMap.clear()
  clearSectionRevealTimers()
})
</script>

<style scoped lang="scss">
.home-page {
  /* HeroCarousel 全屏宽，不受 max-width 限制 */
}

.home-content {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

.home-reveal {
  --reveal-delay: 0ms;
  opacity: 0;
  transform: translate3d(0, 26px, 0) scale(0.992);
  filter: blur(1px);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 760ms ease;
  transition-delay: var(--reveal-delay);
  will-change: opacity, transform;

  &.is-visible {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
    filter: blur(0);
  }
}

.section-defer-placeholder {
  border-radius: $radius-lg;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(248, 250, 252, 0.92);

  .dark & {
    border-color: rgba(71, 85, 105, 0.34);
    background: rgba(15, 23, 42, 0.72);
  }
}

.section-defer-placeholder {
  position: relative;
  overflow: hidden;
}

.section-defer-placeholder::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(105deg, transparent 35%, rgba(255, 255, 255, 0.72) 50%, transparent 65%);
  animation: homeSkeletonSweep 1.35s ease-in-out infinite;
}

.dark .section-defer-placeholder::after {
  background: linear-gradient(105deg, transparent 35%, rgba(148, 163, 184, 0.2) 50%, transparent 65%);
}

@keyframes homeSkeletonSweep {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

@media (prefers-reduced-motion: reduce) {
  .section-defer-placeholder::after {
    animation: none;
  }
}

.section-defer-placeholder {
  min-height: 272px;
  margin-bottom: $spacing-xl;
}

.section-defer-placeholder--ranking {
  min-height: 612px;
  margin-top: $spacing-xl;
}

.post-grid--defer {
  pointer-events: none;
}

/* ===== 文章列表 ===== */
.post-section {
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

.section-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.section-desc {
  font-size: 0.8rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.25rem;

  > * {
    min-width: 0;
    opacity: 0;
    transform: translate3d(0, 18px, 0);
    transition:
      opacity 560ms cubic-bezier(0.22, 1, 0.36, 1),
      transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
  }
}

.home-reveal.is-visible .post-grid > * {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.home-reveal.is-visible .post-grid > *:nth-child(1) { transition-delay: 40ms; }
.home-reveal.is-visible .post-grid > *:nth-child(2) { transition-delay: 90ms; }
.home-reveal.is-visible .post-grid > *:nth-child(3) { transition-delay: 140ms; }
.home-reveal.is-visible .post-grid > *:nth-child(4) { transition-delay: 190ms; }
.home-reveal.is-visible .post-grid > *:nth-child(5) { transition-delay: 240ms; }
.home-reveal.is-visible .post-grid > *:nth-child(6) { transition-delay: 290ms; }
.home-reveal.is-visible .post-grid > *:nth-child(7) { transition-delay: 340ms; }
.home-reveal.is-visible .post-grid > *:nth-child(8) { transition-delay: 390ms; }

:deep(.home-load-more-skeleton) {
  display: contents;
}

:deep(.home-post-card.article-card) {
  transition: box-shadow 0.3s ease, border-color 0.3s ease;
}

:deep(.home-post-card.article-card:hover) {
  transform: none;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);
}

:deep(.home-post-card .cover-image) {
  transform: none;
  transition: opacity 0.28s ease, filter 0.28s ease;
}

:deep(.home-post-card .cover-image--loaded),
:deep(.home-post-card.article-card:hover .cover-image--loaded) {
  transform: none;
}

.dark :deep(.home-post-card.article-card:hover) {
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.3);
}

/* ===== 加载更多 ===== */
.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 2rem;
}

.load-more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem 1.75rem;
  border: 1px solid $color-border;
  border-radius: 999px;
  background: $color-bg;
  color: $color-text-muted;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;

  .load-more-icon {
    transition: transform 0.5s ease;
  }

  /* 光扫效果 */
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.8), transparent);
    transform: translateX(-100%);
    transition: transform 0.8s ease;
  }

  &:hover {
    color: $color-primary;
    border-color: $color-primary;
    box-shadow: 0 4px 16px rgba(59, 130, 246, 0.15);
    transform: translateY(-2px);

    &::before {
      transform: translateX(100%);
    }

    .load-more-icon {
      transform: rotate(180deg);
    }
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
    color: #94a3b8;
    box-shadow: 0 2px 10px rgba(2, 6, 23, 0.32);

    &::before {
      background: linear-gradient(to right, transparent, rgba(148, 163, 184, 0.2), transparent);
    }

    &:hover {
      border-color: rgba(148, 163, 184, 0.52);
      color: $color-dark-text;
      background: rgba(23, 27, 32, 0.95);
      box-shadow: 0 6px 16px rgba(2, 6, 23, 0.4);
    }

    &:active {
      box-shadow: 0 2px 8px rgba(2, 6, 23, 0.45);
    }
  }
}

.no-more {
  font-size: 0.85rem;
  color: $color-text-muted;
  padding: 0.5rem 0;

  .dark & {
    color: #64748b;
  }
}

/* ===== 状态 ===== */
.empty-state {
  text-align: center;
  padding: 5rem 1rem;
  color: #94a3b8;
  p { margin-top: 0.75rem; font-size: 0.95rem; }
}

/* ===== 响应式 ===== */
@media (max-width: $breakpoint-md) {
  .home-content { padding: 1.25rem 1rem; }

  .section-defer-placeholder {
    min-height: 228px;
  }

  .section-defer-placeholder--ranking {
    min-height: 560px;
  }

  .post-grid {
    grid-template-columns: 1fr;
  }

  .section-desc {
    display: none;
  }

}

@media (max-width: 480px) {
  .section-defer-placeholder {
    min-height: 208px;
  }

  .section-defer-placeholder--ranking {
    min-height: 500px;
  }

}

@media (prefers-reduced-motion: reduce) {
  .home-reveal,
  .post-grid > * {
    opacity: 1 !important;
    transform: none !important;
    filter: none !important;
    transition: none !important;
  }

}
</style>
