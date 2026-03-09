<template>
  <NuxtLink :to="`/post/${post.slug}`" class="article-card">
    <!-- 置顶角标 -->
    <div v-if="post.isTop" class="top-ribbon">
      <span class="ribbon-text">置顶</span>
    </div>

    <!-- 封面图 -->
    <div class="card-cover">
      <img
        v-if="post.coverImage"
        :src="post.coverImage"
        :alt="post.title"
        loading="lazy"
        @error="handleImageError"
      />
      <div v-else class="cover-placeholder" :style="placeholderStyle">
        <span class="placeholder-char">{{ titleFirstChar }}</span>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="card-content">
      <!-- 分类标签 -->
      <div v-if="showCategory && post.categoryName" class="card-categories">
        <span class="category-tag primary">{{ post.categoryName }}</span>
        <span v-if="post.subCategoryName" class="category-tag secondary">{{ post.subCategoryName }}</span>
      </div>

      <!-- 标题 -->
      <h2 class="card-title">{{ post.title }}</h2>

      <!-- 摘要 -->
      <p v-if="post.summary" class="card-summary">{{ post.summary }}</p>

      <!-- 底部元信息 -->
      <div class="card-meta">
        <div class="meta-left">
          <span class="meta-item">
            <Icon name="heroicons:clock-16-solid" size="14" />
            {{ formatRelativeTime(post.createTime) }}
          </span>
          <span class="meta-item">
            <Icon name="heroicons:eye-16-solid" size="14" />
            {{ formatCount(post.viewCount) }}
          </span>
          <span v-if="post.likeCount" class="meta-item">
            <Icon name="heroicons:heart-16-solid" size="14" />
            {{ formatCount(post.likeCount) }}
          </span>
          <span v-if="post.collectCount" class="meta-item">
            <Icon name="heroicons:bookmark-16-solid" size="14" />
            {{ formatCount(post.collectCount) }}
          </span>
          <span v-if="post.commentCount" class="meta-item">
            <Icon name="heroicons:chat-bubble-left-16-solid" size="14" />
            {{ formatCount(post.commentCount) }}
          </span>
        </div>
        <div v-if="showAuthor && post.authorNickname" class="meta-author">
          <img
            v-if="post.authorAvatar"
            :src="post.authorAvatar"
            :alt="post.authorNickname"
            class="author-avatar"
          />
          <div v-else class="author-avatar-placeholder">
            {{ post.authorNickname.charAt(0) }}
          </div>
          <span class="author-name">{{ post.authorNickname }}</span>
        </div>
      </div>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { PostVO } from '~/api/post'
import { formatRelativeTime, formatCount } from '~/utils/format'

interface Props {
  post: PostVO
  showCategory?: boolean
  showAuthor?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showCategory: true,
  showAuthor: true,
})

// 渐变色预设，用于无封面图时的占位背景
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

/** 标题首字，用于占位图显示 */
const titleFirstChar = computed(() => {
  const title = props.post.title || ''
  return title.charAt(0) || '?'
})

/** 根据文章 ID 选择渐变色 */
const placeholderStyle = computed(() => ({
  background: GRADIENTS[Math.abs(props.post.id) % GRADIENTS.length],
}))

/** 图片加载失败时的回退处理 */
const imageError = ref(false)
function handleImageError(e: Event) {
  imageError.value = true
  const target = e.target as HTMLImageElement
  // 隐藏加载失败的图片，显示占位
  target.style.display = 'none'
  const placeholder = target.parentElement?.querySelector('.cover-placeholder') as HTMLElement
  if (placeholder) placeholder.style.display = 'flex'
}

</script>

<style lang="scss" scoped>
/* 文章卡片：左封面图右内容水平布局 */
.article-card {
  display: flex;
  position: relative;
  height: calc(240px * 9 / 16);
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: visible;
  background: $color-bg;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  min-width: 0;
  max-width: 100%;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);

    .card-title {
      color: $color-primary;
    }
  }

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;

    &:hover {
      box-shadow: 0 6px 24px rgba(0, 0, 0, 0.3);
    }
  }
}

/* 置顶角标 - 与参考案例一致的实现 */
.top-ribbon {
  position: absolute;
  top: -3px;
  left: -3px;
  width: 200px;
  height: 140px;
  overflow: hidden;
  z-index: 10;

  /* 折叠三角 - 顶部方向（丝带右端与卡片边缘之间） */
  &::before {
    content: '';
    position: absolute;
    left: 45px;
    top: 0;
    width: 9px;
    height: 3px;
    border-radius: 2px 5px 0 0;
    background-color: #000;
  }

  /* 折叠三角 - 左侧方向（丝带左端与卡片边缘之间） */
  &::after {
    content: '';
    position: absolute;
    left: 0;
    top: 45px;
    width: 3px;
    height: 9px;
    border-radius: 2px 2px 2px 9px;
    background-color: #000;
  }

  .ribbon-text {
    display: inline-block;
    position: absolute;
    top: 11px;
    left: -22px;
    width: 80px;
    height: 18px;
    line-height: 18px;
    text-align: center;
    transform: rotate(-45deg);
    background-color: #222;
    color: #fff;
    font-size: 9px;
    font-weight: 500;
    letter-spacing: 0.5px;
    overflow: hidden;
    z-index: 11;
    box-shadow:
      0 0 0 1px #222,
      0 3px 3px -2px rgba(0, 0, 0, 0.9);
    padding-bottom: 1px;
  }
}

/* 封面图区域 - 16:9 比例决定卡片高度 */
.card-cover {
  flex-shrink: 0;
  width: 240px;
  overflow: hidden;
  background: $color-bg-secondary;
  border-radius: $radius-lg 0 0 $radius-lg;

  .dark & {
    background: #1a2332;
  }

  img {
    width: 240px;
    height: calc(240px * 9 / 16);
    display: block;
    object-fit: cover;
    transition: transform 0.35s ease;
  }

  .article-card:hover & img {
    transform: scale(1.05);
  }
}

/* 无封面图渐变占位 */
.cover-placeholder {
  width: 240px;
  height: calc(240px * 9 / 16);
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-char {
  font-size: 2.5rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  user-select: none;
}

/* 内容区 - 高度跟随封面 */
.card-content {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 0 $radius-lg $radius-lg 0;
}

/* 分类标签 */
.card-categories {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: 0.25rem;
}

.category-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: 500;
  line-height: 1.5;

  &.primary {
    background: #eff6ff;
    color: #1d4ed8;

    .dark & {
      background: #1e3a5f;
      color: #93c5fd;
    }
  }

  &.secondary {
    background: #f0fdf4;
    color: #15803d;

    .dark & {
      background: #14532d;
      color: #86efac;
    }
  }
}

/* 标题 - 单行截断 */
.card-title {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.4;
  color: $color-text;
  margin-bottom: 0.2rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s ease;

  .dark & {
    color: $color-dark-text;
  }
}

/* 摘要 - 两行截断 */
.card-summary {
  font-size: 0.78rem;
  color: $color-text-muted;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: auto;

  .dark & {
    color: #94a3b8;
  }
}

/* 底部元信息 */
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.375rem;
  margin-top: auto;
  padding-top: 0.375rem;

  .dark & {
  }
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  font-size: 0.7rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

/* 作者信息 - 右对齐 */
.meta-author {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  flex-shrink: 0;
}

.author-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}

.author-avatar-placeholder {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: $color-primary;
  color: #fff;
  font-size: 0.65rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.author-name {
  font-size: 0.75rem;
  color: $color-text-muted;
  white-space: nowrap;

  .dark & {
    color: #94a3b8;
  }
}

/* ===== 响应式 ===== */

/* 移动端：封面图缩小 */
@media (max-width: $breakpoint-md) {
  .article-card {
    height: calc(180px * 9 / 16);
  }

  .card-cover {
    width: 180px;

    img {
      width: 180px;
      height: calc(180px * 9 / 16);
    }
  }

  .cover-placeholder {
    width: 180px;
    height: calc(180px * 9 / 16);
  }

  .card-content {
    padding: 0.375rem 0.5rem;
  }

  .card-title {
    font-size: 0.85rem;
  }

  .card-summary {
    font-size: 0.75rem;
  }

  .top-ribbon {
    width: 180px;
    height: 120px;

    .ribbon-text {
      top: 8px;
      left: -26px;
      font-size: 8px;
      width: 76px;
      height: 16px;
      line-height: 16px;
    }
  }
}

/* 超小屏：垂直布局（图上文下） */
@media (max-width: 480px) {
  .article-card {
    flex-direction: column;
    height: auto;
  }

  .card-cover {
    width: 100%;
    border-radius: $radius-lg $radius-lg 0 0;

    img {
      width: 100%;
      height: auto;
      aspect-ratio: 16 / 9;
    }
  }

  .cover-placeholder {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
  }

  .card-content {
    padding: $spacing-md;
    border-radius: 0 0 $radius-lg $radius-lg;
  }

  .card-title {
    font-size: 0.9rem;
  }

  .card-summary {
    font-size: 0.78rem;
  }

  .meta-left {
    gap: 0.5rem;
  }
}
</style>
