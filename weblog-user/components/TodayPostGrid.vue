<template>
  <!-- 空数据时隐藏区块 -->
  <section v-if="posts.length > 0" class="post-scroll-section">
    <!-- 区块标题 -->
    <div class="section-header">
      <div class="section-title-group">
        <h2 class="section-title">最近发布</h2>
        <p class="section-desc">最新发布的文章</p>
      </div>
      <div v-if="canScroll" class="scroll-arrows">
        <button class="scroll-arrow" :disabled="!canScrollLeft" aria-label="向左滚动" @click="scrollBy(-1)">
          <Icon name="heroicons:chevron-left-20-solid" size="18" />
        </button>
        <button class="scroll-arrow" :disabled="!canScrollRight" aria-label="向右滚动" @click="scrollBy(1)">
          <Icon name="heroicons:chevron-right-20-solid" size="18" />
        </button>
      </div>
    </div>

    <!-- 横向滚动卡片 -->
    <div ref="scrollRef" class="post-scroll" @scroll="updateScrollState">
      <NuxtLink
        v-for="post in posts"
        :key="post.id"
        :to="`/post/${post.slug}`"
        class="grid-card"
      >
        <!-- 背景图占满 -->
        <div class="card-bg">
          <img
            v-if="post.coverImage"
            :src="post.coverImage"
            :alt="post.title"
            loading="lazy"
          />
          <div v-else class="cover-placeholder" :style="getPlaceholderStyle(post.id)" />
        </div>
        <!-- 渐变遮罩 -->
        <div class="card-overlay" />
        <!-- 文章信息叠加在图片上 -->
        <div class="card-info">
          <h3 class="card-title">{{ post.title }}</h3>
          <div class="card-meta">
            <span v-if="post.authorNickname" class="meta-item">{{ post.authorNickname }}</span>
            <span class="meta-item">
              <Icon name="heroicons:clock-16-solid" size="12" />
              {{ formatRelativeTime(post.createTime) }}
            </span>
          </div>
        </div>
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { postApi, type PostVO } from '~/api/post'
import { formatRelativeTime } from '~/utils/format'

const posts = ref<PostVO[]>([])
const scrollRef = ref<HTMLElement>()
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const canScroll = ref(false)

const GRADIENTS = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
  'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
]

function getPlaceholderStyle(postId: number) {
  return { background: GRADIENTS[Math.abs(postId) % GRADIENTS.length] }
}

function updateScrollState() {
  const el = scrollRef.value
  if (!el) return
  canScrollLeft.value = el.scrollLeft > 0
  canScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
  canScroll.value = el.scrollWidth > el.clientWidth
}

function scrollBy(direction: number) {
  const el = scrollRef.value
  if (!el) return
  // 滚动一个卡片宽度 + gap
  const cardWidth = el.querySelector('.grid-card')?.clientWidth || 280
  el.scrollBy({ left: direction * (cardWidth + 16), behavior: 'smooth' })
}

async function loadRecentPosts() {
  try {
    const res = await postApi.listRecent(10)
    posts.value = res.data
  } catch {
    posts.value = []
  }
}

onMounted(() => {
  loadRecentPosts().then(() => {
    nextTick(updateScrollState)
  })
  // 监听窗口 resize 更新滚动状态
  window.addEventListener('resize', updateScrollState, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('resize', updateScrollState)
})
</script>

<style lang="scss" scoped>
/* ===== 区块容器 ===== */
.post-scroll-section {
  margin-bottom: $spacing-xl;
}

/* ===== 区块标题 ===== */
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

/* ===== 滚动箭头 ===== */
.scroll-arrows {
  display: flex;
  gap: 0.375rem;
}

.scroll-arrow {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid $color-border;
  border-radius: 50%;
  background: transparent;
  color: $color-text;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    border-color: $color-text-muted;
    color: $color-text;
    background: rgba(0, 0, 0, 0.02);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;

    &:hover:not(:disabled) {
      border-color: #64748b;
      color: $color-dark-text;
      background: rgba(255, 255, 255, 0.05);
    }
  }
}

/* ===== 横向滚动容器 ===== */
.post-scroll {
  display: flex;
  gap: $spacing-lg;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  padding: 4px 0;

  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
}

/* ===== 单张卡片 ===== */
.grid-card {
  position: relative;
  flex-shrink: 0;
  width: 280px;
  height: 180px;
  border-radius: $radius-lg;
  overflow: hidden;
  text-decoration: none;
  color: #fff;
  cursor: pointer;
  scroll-snap-align: start;

  &:hover {
    .card-bg img {
      transform: scale(1.05);
    }
  }
}

/* ===== 背景图 ===== */
.card-bg {
  position: absolute;
  inset: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.35s ease;
  }
}

.cover-placeholder {
  width: 100%;
  height: 100%;
}

/* ===== 渐变遮罩 ===== */
.card-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.7) 0%,
    rgba(0, 0, 0, 0.1) 60%,
    transparent 100%
  );

  /* 悬浮加深层 */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(
      to top,
      rgba(0, 0, 0, 0.85) 0%,
      rgba(0, 0, 0, 0.25) 60%,
      rgba(0, 0, 0, 0.05) 100%
    );
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  .grid-card:hover &::after {
    opacity: 1;
  }
}

/* ===== 卡片信息（叠加在图片上） ===== */
.card-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: $spacing-md;
  z-index: 1;
}

.card-title {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.4;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: $spacing-xs;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.card-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.8);
}

/* ===== 响应式 ===== */
@media (max-width: $breakpoint-md) {
  .grid-card {
    width: 240px;
    height: 160px;
  }

  .section-desc {
    display: none;
  }
}

/* ===== 减少动画偏好 ===== */
@media (prefers-reduced-motion: reduce) {
  .grid-card {
    transition: none;
  }

  .card-bg img {
    transition: none;
  }
}
</style>
