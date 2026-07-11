<template>
  <div class="announcement-detail">
    <div v-if="pending" class="detail-skeleton">
      <div class="skeleton-title" />
      <div class="skeleton-meta" />
      <div class="skeleton-line" />
      <div class="skeleton-line long" />
      <div class="skeleton-line" />
    </div>

    <article v-else-if="announcement" class="detail-card">
      <header class="detail-header">
        <NuxtLink to="/" class="back-link">
          <Icon name="heroicons:arrow-left-16-solid" size="16" />
          返回首页
        </NuxtLink>
        <p class="detail-label">站点公告</p>
        <h1 class="detail-title">{{ announcement.title }}</h1>
        <div class="detail-meta">
          <span class="meta-item">
            <Icon name="heroicons:calendar-days-16-solid" size="14" />
            发布于 {{ formatDateTime(announcement.createTime) }}
          </span>
          <span v-if="showUpdateTime" class="meta-item">
            <Icon name="heroicons:clock-16-solid" size="14" />
            更新于 {{ formatDateTime(announcement.updateTime) }}
          </span>
        </div>
      </header>

      <div class="detail-body">
        <ClientOnly>
          <MdPreview
            editor-id="announcement-detail-preview"
            :model-value="announcement.content"
            :sanitize="sanitize"
            :theme="editorTheme"
            :preview-theme="previewTheme"
            :code-theme="codeTheme"
            :code-foldable="false"
            :show-code-row-number="true"
            :no-highlight="true"
            :no-mermaid="true"
            :no-katex="true"
            :no-echarts="true"
            class="detail-md-preview"
          />
          <template #fallback>
            <!-- 已经过 sanitizeHtml 净化 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div class="detail-md-fallback" v-html="sanitize(announcement.content)" />
          </template>
        </ClientOnly>
      </div>
    </article>

    <div v-else-if="loadErrorMessage" class="detail-empty">
      <Icon name="heroicons:signal-slash" size="48" />
      <p>{{ loadErrorMessage }}</p>
      <button type="button" class="retry-btn" @click="retryLoadAnnouncement">重试</button>
      <NuxtLink to="/" class="back-link">返回首页</NuxtLink>
    </div>

    <div v-else class="detail-empty">
      <Icon name="heroicons:document-magnifying-glass" size="48" />
      <p>公告不存在或已下线</p>
      <NuxtLink to="/" class="back-link">返回首页</NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { announcementApi, type AnnouncementVO } from '~/api/marketing/announcement'
import { sanitizeHtml } from '~/utils/security/xss'

const route = useRoute()
const loadErrorMessage = ref<string | null>(null)
const colorMode = useColorMode()

const announcementId = computed(() => Number(route.params.id))
const previewTheme = computed(() => (colorMode.value === 'dark' ? 'github' : 'default'))
const codeTheme = computed(() => (colorMode.value === 'dark' ? 'atom' : 'atom'))
const editorTheme = computed(() => (colorMode.value === 'dark' ? 'dark' : 'light'))

function getHttpErrorCode(error: unknown): number | null {
  if (!error || typeof error !== 'object') {
    return null
  }
  const candidate = (error as { code?: unknown }).code
  if (typeof candidate === 'number' && Number.isFinite(candidate)) {
    return candidate
  }
  return null
}

const { data: announcement, pending, refresh } = await useAsyncData<AnnouncementVO | null>(
  () => `announcement-${announcementId.value || 'invalid'}`,
  async () => {
    if (!Number.isInteger(announcementId.value) || announcementId.value <= 0) {
      loadErrorMessage.value = null
      return null
    }

    loadErrorMessage.value = null
    try {
      const res = await announcementApi.getById(announcementId.value)
      return res.data || null
    } catch (error) {
      const code = getHttpErrorCode(error)
      if (code !== 404) {
        loadErrorMessage.value = '公告加载失败，请稍后重试'
      }
      return null
    }
  },
  {
    default: () => null,
    watch: [announcementId]
  }
)

async function retryLoadAnnouncement() {
  await refresh()
}

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  if (!pending.value && !announcement.value && announcementId.value > 0) {
    void refresh()
  }
})

const showUpdateTime = computed(() => {
  const current = announcement.value
  if (!current?.createTime || !current.updateTime) return false

  const createTime = Date.parse(current.createTime)
  const updateTime = Date.parse(current.updateTime)
  if (Number.isNaN(createTime) || Number.isNaN(updateTime)) {
    return current.createTime !== current.updateTime
  }

  return Math.abs(updateTime - createTime) > 60_000
})

function sanitize(html: string): string {
  return sanitizeHtml(html)
}

function formatDateTime(value?: string): string {
  if (!value) return '最近'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '最近'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

useHead({
  title: computed(() => announcement.value ? `${announcement.value.title} - 公告` : '公告详情')
})
</script>

<style lang="scss" scoped>
.announcement-detail {
  max-width: 920px;
  margin: 0 auto;
  padding: 1.75rem 1rem;
}

.detail-card {
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: 16px;
  overflow: hidden;
  animation: detail-enter 0.42s cubic-bezier(0.22, 1, 0.36, 1) both;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.detail-empty {
  animation: detail-enter 0.42s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.detail-header {
  padding: 1.5rem 1.75rem 1.25rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  text-decoration: none;
  color: $color-primary;
  font-size: 0.84rem;
  margin-bottom: 0.85rem;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.82;
  }
}

.detail-label {
  margin: 0;
  font-size: 0.76rem;
  letter-spacing: 0.04em;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.detail-title {
  margin: 0.45rem 0 0;
  font-size: clamp(1.3rem, 3vw, 1.65rem);
  line-height: 1.45;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.detail-meta {
  margin-top: 0.82rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
  color: $color-text-muted;
  font-size: 0.8rem;

  .dark & {
    color: #94a3b8;
  }
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
}

.detail-body {
  padding: 1.45rem 1.75rem;
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
    margin: 0 0 0.95rem;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 10px;
  }
}

.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.85rem;
  min-height: 300px;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }

  p {
    margin: 0;
    font-size: 0.95rem;
  }
}

.detail-md-preview {
  :deep(.md-editor),
  :deep(.md-editor-preview-wrapper) {
    border: none;
    background: transparent;
    padding: 0;
  }

  :deep(.md-editor-preview) {
    color: inherit;
    font-size: inherit;
    line-height: 1.8;
    font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
  }

  :deep(.md-editor-preview p) {
    margin: 0 0 0.95rem;
  }

  :deep(.md-editor-preview p:last-child) {
    margin-bottom: 0;
  }
}

.detail-md-fallback {
  :deep(p) {
    margin: 0 0 0.95rem;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }
}

.retry-btn {
  border: none;
  background: rgba(37, 99, 235, 0.92);
  color: #fff;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  cursor: pointer;
}

.detail-skeleton {
  border-radius: 16px;
  border: 1px solid $color-border;
  background: $color-bg;
  padding: 1.5rem 1.75rem;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.skeleton-title,
.skeleton-meta,
.skeleton-line {
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.2), rgba(148, 163, 184, 0.35), rgba(148, 163, 184, 0.2));
  animation: detail-skeleton 1.5s ease-in-out infinite;

  .dark & {
    background: linear-gradient(90deg, rgba(71, 85, 105, 0.35), rgba(100, 116, 139, 0.5), rgba(71, 85, 105, 0.35));
  }
}

.skeleton-title {
  height: 30px;
  width: min(72%, 480px);
  margin-bottom: 0.95rem;
}

.skeleton-meta {
  height: 15px;
  width: min(42%, 240px);
  margin-bottom: 1.4rem;
}

.skeleton-line {
  height: 14px;
  width: 100%;
  margin-bottom: 0.75rem;
}

.skeleton-line.long {
  width: 84%;
}

@keyframes detail-skeleton {
  0%,
  100% {
    opacity: 0.65;
  }

  50% {
    opacity: 1;
  }
}

@keyframes detail-enter {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: $breakpoint-md) {
  .announcement-detail {
    padding: 1rem 0.75rem;
  }

  .detail-header,
  .detail-body,
  .detail-skeleton {
    padding-left: 1rem;
    padding-right: 1rem;
  }
}
</style>
