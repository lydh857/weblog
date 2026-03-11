<template>
  <div class="home-page">
    <!-- 轮播区块 -->
    <HeroCarousel />

    <div class="home-content">
      <!-- 今日发布 -->
      <div
        ref="todaySectionRef"
        class="home-reveal"
        style="--reveal-delay: 0ms"
        :class="{ 'is-visible': sectionVisible.today }"
      >
        <TodayPostGrid />
      </div>

      <!-- 热门文章 -->
      <div
        ref="hotSectionRef"
        class="home-reveal"
        style="--reveal-delay: 70ms"
        :class="{ 'is-visible': sectionVisible.hot }"
      >
        <LazyHotPostGrid v-if="sectionMounted.hot" />
        <div v-else class="section-defer-placeholder" aria-hidden="true" />
      </div>

      <!-- 文章排行榜 -->
      <div
        ref="rankingSectionRef"
        class="home-reveal"
        style="--reveal-delay: 120ms"
        :class="{ 'is-visible': sectionVisible.ranking }"
      >
        <LazyHomeRankingSection v-if="sectionMounted.ranking" />
        <div v-else class="section-defer-placeholder section-defer-placeholder--ranking" aria-hidden="true" />
      </div>

      <!-- 文章列表 -->
      <section
        ref="postSectionRef"
        class="post-section home-reveal"
        style="--reveal-delay: 160ms"
        :class="{ 'is-visible': sectionVisible.post }"
      >
        <div class="section-header">
          <div class="section-title-group">
            <h2 class="section-title">推荐文章</h2>
            <p class="section-desc">精选内容，值得一读</p>
          </div>
        </div>

        <div v-if="!sectionMounted.post" class="post-defer-placeholder" aria-hidden="true" />

        <div v-else-if="loading && !posts.length" class="loading-state">
          <Icon name="heroicons:arrow-path-20-solid" size="20" class="spin" />
          <span>加载中...</span>
        </div>

        <div v-else-if="posts.length" ref="postGridRef" class="post-grid">
          <ArticleCard
            v-for="post in posts"
            :key="post.id"
            :post="post"
            :class="{ 'post-card-load-enter': loadingMoreIds.has(post.id) }"
          />
          <template v-if="loadingMore">
            <div v-for="i in 4" :key="`post-loading-${i}`" class="post-card-loading-placeholder" aria-hidden="true">
              <div class="post-card-loading-placeholder__cover" />
              <div class="post-card-loading-placeholder__content">
                <div class="post-card-loading-placeholder__title" />
                <div class="post-card-loading-placeholder__summary" />
                <div class="post-card-loading-placeholder__summary short" />
                <div class="post-card-loading-placeholder__meta">
                  <span class="post-card-loading-placeholder__meta-item" />
                  <span class="post-card-loading-placeholder__meta-item short" />
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 加载更多 -->
        <div v-if="sectionMounted.post && posts.length" class="load-more-container">
          <div v-if="loadingMore" class="loading-state">
            <Icon name="heroicons:arrow-path-20-solid" size="20" class="spin" />
            <span>加载中...</span>
          </div>
          <div v-else-if="noMore" class="no-more">没有更多文章了</div>
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
import { postApi, type PostVO } from '~/api/post'

useHead({ title: 'Weblog - 首页' })

const posts = ref<PostVO[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)
const currentPage = ref(1)
const pageSize = 20
const hasLoadedInitialPosts = ref(false)
const postGridRef = ref<HTMLElement | null>(null)
const loadingMoreIds = reactive(new Set<number>())
const LOAD_MORE_SCROLL_TOP_OFFSET = 84
const LOAD_MORE_MIN_SCROLL_DELTA = 28
const todaySectionRef = ref<HTMLElement | null>(null)
const hotSectionRef = ref<HTMLElement | null>(null)
const rankingSectionRef = ref<HTMLElement | null>(null)
const postSectionRef = ref<HTMLElement | null>(null)

type HomeSectionKey = 'today' | 'hot' | 'ranking' | 'post'

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

let sectionObserver: IntersectionObserver | null = null
const sectionTargetMap = new Map<Element, HomeSectionKey>()

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

function initSectionObserver() {
  if (!import.meta.client) return

  sectionObserver?.disconnect()
  sectionTargetMap.clear()

  sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      const key = sectionTargetMap.get(entry.target)
      if (!key) return
      markSectionVisible(key)
      ensureSectionMounted(key)
      sectionObserver?.unobserve(entry.target)
    })
  }, {
    threshold: 0.16,
    rootMargin: '0px 0px 20% 0px'
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
    markSectionVisible('today')
    ensureSectionMounted('today')
  })
}

function animateLoadMoreCards(postIds: number[]) {
  if (!postIds.length || !import.meta.client) return

  nextTick(() => {
    requestAnimationFrame(() => {
      postIds.forEach(id => loadingMoreIds.add(id))

      setTimeout(() => {
        postIds.forEach(id => loadingMoreIds.delete(id))
      }, 820)
    })
  })
}

function maybeScrollToFirstLoadedCard(previousCount: number) {
  if (!import.meta.client) return

  nextTick(() => {
    const cards = postGridRef.value?.children
    if (!cards || !cards[previousCount]) return

    const targetCard = cards[previousCount] as HTMLElement
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    requestAnimationFrame(() => {
      setTimeout(() => {
        const top = targetCard.getBoundingClientRect().top + window.scrollY - LOAD_MORE_SCROLL_TOP_OFFSET
        if (Math.abs(top - window.scrollY) < LOAD_MORE_MIN_SCROLL_DELTA) return

        window.scrollTo({
          top,
          behavior: prefersReducedMotion ? 'auto' : 'smooth'
        })
      }, 90)
    })
  })
}

async function loadPosts() {
  if (loading.value || hasLoadedInitialPosts.value) return

  loading.value = true
  try {
    const res = await postApi.list({ pageNum: 1, pageSize })
    posts.value = res.data.records
    noMore.value = res.data.records.length < pageSize || res.data.pages <= 1
    currentPage.value = 1
  } catch { /* ignore */ }
  finally {
    loading.value = false
    hasLoadedInitialPosts.value = true
  }
}

async function loadMore() {
  if (loadingMore.value || noMore.value) return
  const previousCount = posts.value.length
  loadingMore.value = true
  try {
    const nextPage = currentPage.value + 1
    const res = await postApi.list({ pageNum: nextPage, pageSize })
    const appendedPosts = res.data.records
    posts.value = [...posts.value, ...appendedPosts]
    currentPage.value = nextPage
    noMore.value = appendedPosts.length < pageSize || nextPage >= res.data.pages

    if (appendedPosts.length > 0) {
      animateLoadMoreCards(appendedPosts.map(post => post.id))
      maybeScrollToFirstLoadedCard(previousCount)
    }
  } catch { /* ignore */ }
  finally { loadingMore.value = false }
}

onMounted(() => {
  nextTick(() => {
    initSectionObserver()
  })
})

onUnmounted(() => {
  sectionObserver?.disconnect()
  sectionTargetMap.clear()
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

.section-defer-placeholder,
.post-defer-placeholder {
  border-radius: $radius-lg;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: linear-gradient(
    120deg,
    rgba(241, 245, 249, 0.72) 0%,
    rgba(226, 232, 240, 0.9) 50%,
    rgba(241, 245, 249, 0.72) 100%
  );

  .dark & {
    border-color: rgba(100, 116, 139, 0.24);
    background: linear-gradient(
      120deg,
      rgba(30, 41, 59, 0.78) 0%,
      rgba(51, 65, 85, 0.92) 50%,
      rgba(30, 41, 59, 0.78) 100%
    );
  }
}

.section-defer-placeholder {
  min-height: 236px;
  margin-bottom: $spacing-xl;
}

.section-defer-placeholder--ranking {
  min-height: 520px;
  margin-top: $spacing-xl;
}

.post-defer-placeholder {
  min-height: 280px;
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

.post-card-load-enter {
  animation: postCardLoadEnter 780ms cubic-bezier(0.2, 0.9, 0.2, 1) both;
  will-change: opacity, transform;
}

@keyframes postCardLoadEnter {
  from {
    opacity: 0;
    transform: translate3d(0, 30px, 0) scale(0.985);
  }

  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}

.post-card-loading-placeholder {
  display: flex;
  height: calc(240px * 9 / 16);
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-bg;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: rgba(100, 116, 139, 0.28);
  }
}

.post-card-loading-placeholder__cover {
  width: 240px;
  flex-shrink: 0;
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.14) 0%,
    rgba(148, 163, 184, 0.28) 50%,
    rgba(148, 163, 184, 0.14) 100%
  );
  background-size: 200% 100%;
  animation: postLoadingShimmer 1.2s linear infinite;

  .dark & {
    background: linear-gradient(
      90deg,
      rgba(71, 85, 105, 0.2) 0%,
      rgba(100, 116, 139, 0.34) 50%,
      rgba(71, 85, 105, 0.2) 100%
    );
    background-size: 200% 100%;
  }
}

.post-card-loading-placeholder__content {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
}

.post-card-loading-placeholder__title {
  width: 74%;
  height: 13px;
  border-radius: 999px;
  margin-bottom: 0.5rem;
  background: rgba(148, 163, 184, 0.24);

  .dark & {
    background: rgba(100, 116, 139, 0.34);
  }
}

.post-card-loading-placeholder__summary {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  margin-bottom: 0.3rem;
  background: rgba(148, 163, 184, 0.18);

  &.short {
    width: 84%;
  }

  .dark & {
    background: rgba(100, 116, 139, 0.28);
  }
}

.post-card-loading-placeholder__meta {
  margin-top: auto;
  display: flex;
  gap: 0.45rem;
}

.post-card-loading-placeholder__meta-item {
  width: 70px;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);

  &.short {
    width: 50px;
  }

  .dark & {
    background: rgba(100, 116, 139, 0.3);
  }
}

@keyframes postLoadingShimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
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

    &::before {
      background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.05), transparent);
    }

    &:hover {
      border-color: $color-primary;
      color: $color-primary;
      box-shadow: 0 4px 16px rgba(59, 130, 246, 0.2);
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
  padding: 5rem 1rem;
  color: #94a3b8;
  p { margin-top: 0.75rem; font-size: 0.95rem; }
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 响应式 ===== */
@media (max-width: $breakpoint-md) {
  .home-content { padding: 1.25rem 1rem; }

  .section-defer-placeholder {
    min-height: 200px;
  }

  .section-defer-placeholder--ranking {
    min-height: 460px;
  }

  .post-defer-placeholder {
    min-height: 240px;
  }

  .post-grid {
    grid-template-columns: 1fr;
  }

  .section-desc {
    display: none;
  }

  .post-card-loading-placeholder {
    height: calc(180px * 9 / 16);
  }

  .post-card-loading-placeholder__cover {
    width: 180px;
  }
}

@media (max-width: 480px) {
  .section-defer-placeholder {
    min-height: 180px;
  }

  .section-defer-placeholder--ranking {
    min-height: 360px;
  }

  .post-defer-placeholder {
    min-height: 220px;
  }

  .post-card-loading-placeholder {
    flex-direction: column;
    height: auto;
  }

  .post-card-loading-placeholder__cover {
    width: 100%;
    aspect-ratio: 16 / 9;
  }

  .post-card-loading-placeholder__content {
    padding: $spacing-md;
    min-height: 120px;
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

  .post-card-load-enter {
    animation: none !important;
  }

  .post-card-loading-placeholder__cover {
    animation: none !important;
  }
}
</style>
