<template>
  <div class="home-page">
    <!-- 轮播区块 -->
    <HeroCarousel />

    <div class="home-content">
      <!-- 今日发布 -->
      <TodayPostGrid />

      <!-- 热门文章 -->
      <HotPostGrid />

      <!-- 文章排行榜 -->
      <HomeRankingSection />

      <!-- 文章列表 -->
      <section class="post-section">
        <div class="section-header">
          <div class="section-title-group">
            <h2 class="section-title">推荐文章</h2>
            <p class="section-desc">精选内容，值得一读</p>
          </div>
        </div>

        <div v-if="loading && !posts.length" class="loading-state">
          <Icon name="heroicons:arrow-path-20-solid" size="20" class="spin" />
          <span>加载中...</span>
        </div>

        <div v-else-if="posts.length" ref="postGridRef" class="post-grid">
          <ArticleCard v-for="post in posts" :key="post.id" :post="post" />
        </div>

        <!-- 加载更多 -->
        <div v-if="posts.length" class="load-more-container">
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

        <div v-if="!loading && !posts.length" class="empty-state">
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
const loading = ref(true)
const loadingMore = ref(false)
const noMore = ref(false)
const currentPage = ref(1)
const pageSize = 20
const postGridRef = ref<HTMLElement | null>(null)

async function loadPosts() {
  loading.value = true
  try {
    const res = await postApi.list({ pageNum: 1, pageSize })
    posts.value = res.data.records
    noMore.value = res.data.records.length < pageSize || res.data.pages <= 1
    currentPage.value = 1
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadMore() {
  if (loadingMore.value || noMore.value) return
  const prevCount = posts.value.length
  loadingMore.value = true
  try {
    const nextPage = currentPage.value + 1
    const res = await postApi.list({ pageNum: nextPage, pageSize })
    posts.value = [...posts.value, ...res.data.records]
    currentPage.value = nextPage
    noMore.value = res.data.records.length < pageSize || nextPage >= res.data.pages

    // 滚动到新加载的第一篇文章
    nextTick(() => {
      const cards = postGridRef.value?.children
      if (cards && cards[prevCount]) {
        const el = cards[prevCount] as HTMLElement
        const top = el.getBoundingClientRect().top + window.scrollY - 80
        window.scrollTo({ top, behavior: 'smooth' })
      }
    })
  } catch { /* ignore */ }
  finally { loadingMore.value = false }
}

onMounted(() => {
  loadPosts()
})
</script>

<style scoped lang="scss">
.home-page {
  /* HeroCarousel 全屏宽，不受 max-width 限制 */
}

.home-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
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
  }
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

  .post-grid {
    grid-template-columns: 1fr;
  }

  .section-desc {
    display: none;
  }
}
</style>
