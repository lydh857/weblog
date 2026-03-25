<template>
  <!-- 加载中显示骨架；加载完成且无数据则隐藏 -->
  <section v-if="!loaded || posts.length > 0" class="post-scroll-section">
    <!-- 区块标题 -->
    <div class="section-header">
      <div class="section-title-group">
        <h2 class="section-title">热门文章</h2>
        <p class="section-desc">近期最受欢迎的内容</p>
      </div>
      <div class="header-right">
        <div v-if="!loaded" class="header-right-skeleton" aria-hidden="true">
          <span class="sk-circle" />
          <span class="sk-circle" />
          <span class="sk-pill" />
        </div>
        <template v-else>
          <div v-if="canScroll" class="scroll-arrows">
            <button class="scroll-arrow" :disabled="!canScrollLeft" aria-label="向左滚动" @click="scrollBy(-1)">
              <Icon name="heroicons:chevron-left-20-solid" size="18" />
            </button>
            <!-- 滚动到底延迟后变为"查看更多"，带过渡动画 -->
            <Transition name="arrow-morph" mode="out-in">
              <NuxtLink v-if="showViewMore" key="view-more" to="/ranking" class="scroll-arrow view-more-arrow" aria-label="查看更多">
                查看更多
                <Icon name="heroicons:arrow-right-16-solid" size="14" />
              </NuxtLink>
              <button v-else key="scroll-right" class="scroll-arrow" :disabled="!canScrollRight" aria-label="向右滚动" @click="scrollBy(1)">
                <Icon name="heroicons:chevron-right-20-solid" size="18" />
              </button>
            </Transition>
          </div>
          <NuxtLink v-if="!canScroll" to="/ranking" class="view-more">
            查看更多
            <Icon name="heroicons:arrow-right-16-solid" size="14" />
          </NuxtLink>
        </template>
      </div>
    </div>

    <div v-if="!loaded" class="post-scroll post-scroll--skeleton" aria-hidden="true">
      <div v-for="i in 5" :key="i" class="grid-card-skeleton">
        <div class="grid-card-skeleton__cover" />
        <div class="grid-card-skeleton__overlay" />
        <div class="grid-card-skeleton__info">
          <div class="grid-card-skeleton__title" />
          <div class="grid-card-skeleton__meta">
            <span class="grid-card-skeleton__meta-item" />
            <span class="grid-card-skeleton__meta-item" />
            <span class="grid-card-skeleton__meta-item short" />
          </div>
        </div>
      </div>
    </div>

    <!-- 横向滚动卡片 -->
    <div v-else ref="scrollRef" class="post-scroll" @scroll="updateScrollState">
      <NuxtLink
        v-for="post in posts"
        :key="post.post_id"
        :to="`/post/${post.slug}`"
        class="grid-card"
      >
        <!-- 背景图占满 -->
        <div class="card-bg">
          <img
            v-if="post.cover_image && !isImageBroken(post.post_id)"
            :src="post.cover_image"
            :alt="post.title"
            loading="lazy"
            @error="handleImageError(post.post_id, $event)"
          />
          <div v-else class="cover-placeholder" />
        </div>
        <!-- 渐变遮罩 -->
        <div class="card-overlay" />
        <!-- 文章信息叠加在图片上 -->
        <div class="card-info">
          <h3 class="card-title">{{ post.title }}</h3>
          <div class="card-meta">
            <span v-if="post.view_count" class="meta-item">
              <Icon name="heroicons:eye-16-solid" size="12" />
              {{ formatCount(post.view_count) }}
            </span>
            <span v-if="post.like_count" class="meta-item">
              <Icon name="heroicons:heart-16-solid" size="12" />
              {{ formatCount(post.like_count) }}
            </span>
            <span v-if="post.collect_count" class="meta-item">
              <Icon name="heroicons:bookmark-16-solid" size="12" />
              {{ formatCount(post.collect_count) }}
            </span>
            <span v-if="post.comment_count" class="meta-item">
              <Icon name="heroicons:chat-bubble-left-16-solid" size="12" />
              {{ formatCount(post.comment_count) }}
            </span>
          </div>
        </div>
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { rankingApi, type RankingItem } from '~/api/ranking'
import { formatCount } from '~/utils/format'

const posts = ref<RankingItem[]>([])
const scrollRef = ref<HTMLElement>()
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const canScroll = ref(false)
const loaded = ref(false)
const imageErrorMap = ref<Record<number, boolean>>({})
// 直接根据滚动状态切换"查看更多"
const showViewMore = computed(() => !canScrollRight.value && canScroll.value)

function isImageBroken(postId: number): boolean {
  return !!imageErrorMap.value[postId]
}

function handleImageError(postId: number, event: Event) {
  imageErrorMap.value = {
    ...imageErrorMap.value,
    [postId]: true,
  }

  const target = event.target as HTMLImageElement | null
  if (target) {
    target.style.display = 'none'
  }
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
  const cardWidth = el.querySelector('.grid-card')?.clientWidth || 280
  el.scrollBy({ left: direction * (cardWidth + 16), behavior: 'smooth' })
}

async function loadHotPosts() {
  try {
    const res = await rankingApi.get({ rankType: 4, limit: 8 })
    posts.value = res.data
    imageErrorMap.value = {}
  } catch {
    posts.value = []
    imageErrorMap.value = {}
  } finally {
    loaded.value = true
  }
}

onMounted(() => {
  loadHotPosts().then(() => {
    nextTick(updateScrollState)
  })
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

.header-right {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.header-right-skeleton {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.sk-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.16) 0%,
    rgba(148, 163, 184, 0.3) 50%,
    rgba(148, 163, 184, 0.16) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s linear infinite;
  flex-shrink: 0;

  .dark & {
    background: linear-gradient(
      90deg,
      rgba(71, 85, 105, 0.24) 0%,
      rgba(100, 116, 139, 0.4) 50%,
      rgba(71, 85, 105, 0.24) 100%
    );
    background-size: 200% 100%;
  }
}

.sk-pill {
  width: 74px;
  height: 32px;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.16) 0%,
    rgba(148, 163, 184, 0.3) 50%,
    rgba(148, 163, 184, 0.16) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s linear infinite;

  .dark & {
    background: linear-gradient(
      90deg,
      rgba(71, 85, 105, 0.24) 0%,
      rgba(100, 116, 139, 0.4) 50%,
      rgba(71, 85, 105, 0.24) 100%
    );
    background-size: 200% 100%;
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

  /* 滚动到底时变为"查看更多"样式 */
  &.view-more-arrow {
    width: auto;
    border-radius: 999px;
    padding: 0 0.75rem;
    gap: 0.25rem;
    font-size: 0.8rem;
    font-weight: 500;
    text-decoration: none;
    color: $color-text;
    border-color: $color-border;
    background: transparent;

    &:hover {
      border-color: $color-text-muted;
      background: rgba(0, 0, 0, 0.02);
    }
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;

    &:hover:not(:disabled) {
      border-color: #64748b;
      color: $color-dark-text;
      background: rgba(255, 255, 255, 0.05);
    }

    &.view-more-arrow {
      color: $color-dark-text;
      border-color: $color-dark-border;
      background: transparent;

      &:hover {
        border-color: #64748b;
        background: rgba(255, 255, 255, 0.05);
      }
    }
  }
}

.view-more {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: $color-text-muted;
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: $color-primary;
  }

  .dark & {
    color: #94a3b8;

    &:hover {
      color: $color-primary;
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

.post-scroll--skeleton {
  pointer-events: none;
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

.grid-card-skeleton {
  position: relative;
  flex-shrink: 0;
  width: 280px;
  height: 180px;
  border-radius: $radius-lg;
  overflow: hidden;
}

.grid-card-skeleton__cover {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.16) 0%,
    rgba(148, 163, 184, 0.3) 50%,
    rgba(148, 163, 184, 0.16) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s linear infinite;

  .dark & {
    background: linear-gradient(
      90deg,
      rgba(71, 85, 105, 0.24) 0%,
      rgba(100, 116, 139, 0.4) 50%,
      rgba(71, 85, 105, 0.24) 100%
    );
    background-size: 200% 100%;
  }
}

.grid-card-skeleton__overlay {
  display: none;
}

.grid-card-skeleton__info {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
  padding: $spacing-md;
}

.grid-card-skeleton__title {
  width: 80%;
  height: 14px;
  border-radius: 999px;
  margin-bottom: $spacing-xs;
  background: rgba(148, 163, 184, 0.2);

  .dark & {
    background: rgba(100, 116, 139, 0.3);
  }
}

.grid-card-skeleton__meta {
  display: flex;
  gap: $spacing-xs;
}

.grid-card-skeleton__meta-item {
  width: 52px;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);

  .dark & {
    background: rgba(100, 116, 139, 0.28);
  }

  &.short {
    width: 36px;
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
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(248, 250, 252, 0.92));

  .dark & {
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
  }
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
  .section-header {
    margin-bottom: 0.75rem;
  }

  .header-right {
    gap: 0.5rem;
  }

  .scroll-arrows,
  .header-right-skeleton {
    gap: 0.3rem;
  }

  .sk-circle {
    width: 36px;
    height: 36px;
  }

  .sk-pill {
    width: 108px;
    height: 36px;
  }

  .scroll-arrow {
    width: 38px;
    height: 38px;

    &.view-more-arrow {
      min-width: 108px;
      padding: 0 0.78rem;
      font-size: 0.8rem;
    }
  }

  .grid-card {
    width: 240px;
    height: 160px;
  }

  .grid-card-skeleton {
    width: 240px;
    height: 160px;
  }

  .section-desc {
    display: none;
  }

  .view-more {
    display: none;
  }
}

/* ===== 箭头 → 查看更多 过渡动画 ===== */
.arrow-morph-enter-active,
.arrow-morph-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.arrow-morph-enter-from {
  opacity: 0;
  transform: translateX(6px);
}

.arrow-morph-leave-to {
  opacity: 0;
  transform: translateX(-6px);
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

@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
