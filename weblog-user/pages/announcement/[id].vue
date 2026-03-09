<template>
  <div class="announcement-detail">
    <!-- 加载中 -->
    <div v-if="loading" class="detail-skeleton">
      <div class="skeleton-title" />
      <div class="skeleton-meta" />
      <div class="skeleton-line" />
      <div class="skeleton-line long" />
      <div class="skeleton-line" />
    </div>

    <!-- 内容 -->
    <article v-else-if="announcement" class="detail-card">
      <div class="detail-header">
        <div class="detail-badge">
          <Icon name="heroicons:megaphone-20-solid" size="16" />
          公告
        </div>
        <h1 class="detail-title">{{ announcement.title }}</h1>
        <div class="detail-meta">
          <span class="meta-priority">
            <Icon name="heroicons:flag-16-solid" size="14" />
            优先级 {{ announcement.priority }}
          </span>
        </div>
      </div>
      <div class="detail-body" v-html="sanitize(announcement.content)" />
      <div class="detail-footer">
        <NuxtLink to="/" class="back-link">
          <Icon name="heroicons:arrow-left-16-solid" size="16" />
          返回首页
        </NuxtLink>
      </div>
    </article>

    <!-- 404 -->
    <div v-else class="detail-empty">
      <Icon name="heroicons:document-magnifying-glass" size="48" />
      <p>公告不存在或已下线</p>
      <NuxtLink to="/" class="back-link">返回首页</NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { announcementApi, type AnnouncementVO } from '~/api/ad'
import DOMPurify from 'dompurify'

const route = useRoute()
const announcement = ref<AnnouncementVO | null>(null)
const loading = ref(true)

function sanitize(html: string) {
  return DOMPurify.sanitize(html)
}

useHead({
  title: computed(() => announcement.value ? `${announcement.value.title} - 公告` : '公告详情'),
})

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id || isNaN(id)) {
    loading.value = false
    return
  }
  try {
    const res = await announcementApi.getById(id)
    announcement.value = res.data
  } catch {
    announcement.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.announcement-detail {
  max-width: 800px;
  margin: 0 auto;
  padding: $spacing-xl $spacing-lg;
}

.detail-card {
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.detail-header {
  padding: 2rem 2rem 1.5rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.detail-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  background: #eff6ff;
  color: $color-primary;
  font-size: 0.78rem;
  font-weight: 500;
  margin-bottom: 1rem;

  .dark & {
    background: rgba(59, 130, 246, 0.15);
  }
}

.detail-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: $color-text;
  line-height: 1.4;
  margin-bottom: 0.75rem;

  .dark & {
    color: $color-dark-text;
  }
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-size: 0.82rem;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.meta-priority {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.detail-body {
  padding: 2rem;
  font-size: 0.95rem;
  line-height: 1.8;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }

  :deep(a) {
    color: $color-primary;
    text-decoration: underline;
  }

  :deep(p) {
    margin-bottom: 1rem;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(img) {
    max-width: 100%;
    border-radius: $radius-md;
  }
}

.detail-footer {
  padding: 1.25rem 2rem;
  border-top: 1px solid $color-border;

  .dark & {
    border-top-color: $color-dark-border;
  }
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: $color-primary;
  font-size: 0.85rem;
  text-decoration: none;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.8;
  }
}

/* 空状态 */
.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  padding: 4rem 0;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }

  p {
    font-size: 0.95rem;
  }
}

/* 骨架屏 */
.detail-skeleton {
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  padding: 2rem;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.skeleton-title {
  height: 28px;
  width: 60%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-bottom: 1rem;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1a2332;
  }
}

.skeleton-meta {
  height: 16px;
  width: 30%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-bottom: 2rem;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1a2332;
  }
}

.skeleton-line {
  height: 14px;
  width: 100%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-bottom: 0.75rem;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  &.long {
    width: 80%;
  }

  .dark & {
    background: #1a2332;
  }
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@media (max-width: $breakpoint-md) {
  .detail-header,
  .detail-body,
  .detail-footer {
    padding-left: 1.25rem;
    padding-right: 1.25rem;
  }
}
</style>
