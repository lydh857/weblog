<template>
  <div class="topic-detail-bg" :style="{ '--sticky-top': stickyTop }">
    <div class="topic-detail-page" :class="{ 'page-entered': pageEntered }">
      <!-- 加载中（静态占位，避免进入页时动画打断阅读） -->
      <div v-if="loading" class="topic-loading-placeholder" aria-live="polite">加载中...</div>

      <template v-else-if="topic">
        <div class="three-col-layout" :class="{ 'content-entered': contentEntered }">
          <!-- 左侧：专题目录 -->
          <div class="left-sidebar detail-enter-block detail-enter-left" :class="{ collapsed: leftCollapsed }">
            <div class="left-sidebar-sticky" :style="{ top: stickyTop }">
              <TopicSidebar
                v-model:collapsed="leftCollapsed"
                :topic-title="topic.title"
                :catalogs="catalogs"
                :current-article-id="currentArticleId"
                @select="handleCatalogSelect"
              />
            </div>
          </div>

          <!-- 中间：文章正文 -->
          <div class="center-content detail-enter-block detail-enter-center">
            <!-- 当前文章 -->
            <template v-if="currentPost">
              <div class="post-ad-slot post-ad-slot--top">
                <AdSlotBanner ad-slot="post_top" />
              </div>

              <div class="content-card">
                <header class="article-header">
                  <div class="topic-context">
                    <h1 class="topic-context-title">{{ topic.title }}</h1>
                    <p v-if="topic.summary" class="topic-context-summary">{{ topic.summary }}</p>
                  </div>

                  <h2 class="article-title">{{ currentPost.title }}</h2>
                  <div class="article-meta">
                    <span class="meta-item">
                      <Icon name="heroicons:calendar-20-solid" size="14" />
                      {{ formatDate(currentPost.createTime) }}
                    </span>
                    <span class="meta-item">
                      <Icon name="heroicons:eye-20-solid" size="14" />
                      {{ formatViewCount(currentPost.viewCount) }}
                    </span>
                  </div>
                </header>

                <ClientOnly>
                  <MdPreview
                    editor-id="topic-preview"
                    :model-value="currentPost.content || ''"
                    :sanitize="sanitizeMarkdownPreviewHtml"
                    :theme="editorTheme"
                    :preview-theme="currentPost.previewTheme || 'default'"
                    :code-theme="currentPost.codeTheme || 'atom'"
                    :show-code-row-number="true"
                    :no-mermaid="true"
                    :no-katex="true"
                    class="post-content"
                  />
                </ClientOnly>
              </div>

              <div class="post-ad-slot post-ad-slot--bottom">
                <AdSlotBanner ad-slot="post_bottom" />
              </div>

              <!-- 上下篇导航（参考文章详情页风格） -->
              <nav class="post-nav">
                <div
                  v-if="prevArticle"
                  class="nav-item nav-prev"
                  @click="handleCatalogSelect(prevArticle!)"
                >
                  <div class="nav-cover">
                    <img v-if="prevArticle.coverImage" :src="prevArticle.coverImage" :alt="prevArticle.title" loading="lazy" class="nav-cover-img" >
                    <div class="nav-overlay">
                      <span class="nav-label">
                        <Icon name="heroicons:chevron-left-20-solid" size="18" />
                        上一篇
                      </span>
                      <span class="nav-title">{{ prevArticle.title }}</span>
                    </div>
                  </div>
                </div>
                <div v-else class="nav-item nav-placeholder" />
                <div
                  v-if="nextArticle"
                  class="nav-item nav-next"
                  @click="handleCatalogSelect(nextArticle!)"
                >
                  <div class="nav-cover">
                    <img v-if="nextArticle.coverImage" :src="nextArticle.coverImage" :alt="nextArticle.title" loading="lazy" class="nav-cover-img" >
                    <div class="nav-overlay">
                      <span class="nav-label">
                        下一篇
                        <Icon name="heroicons:chevron-right-20-solid" size="18" />
                      </span>
                      <span class="nav-title">{{ nextArticle.title }}</span>
                    </div>
                  </div>
                </div>
                <div v-else class="nav-item nav-placeholder" />
              </nav>
            </template>

            <div v-else-if="!articleLoading" class="empty-article">
              <Icon name="heroicons:document-text-20-solid" size="32" />
              <p>请从左侧目录选择文章</p>
            </div>
          </div>

          <!-- 右侧：文章目录（可收缩） -->
          <div class="right-sidebar detail-enter-block detail-enter-right" :class="{ collapsed: rightCollapsed }">
            <div class="right-sidebar-sticky" :style="{ top: stickyTop }">
              <!-- 收缩按钮（始终渲染，CSS 控制动画） -->
              <button
                class="sidebar-toggle-btn"
                :class="{ visible: rightCollapsed }"
                title="展开文章目录"
                @click="rightCollapsed = false"
              >
                <Icon name="heroicons:bars-3-bottom-right-20-solid" size="18" />
              </button>
              <!-- 面板（始终渲染，CSS 控制动画） -->
              <div class="right-toc-panel" :class="{ visible: !rightCollapsed }">
                <div class="right-toc-header">
                  <span class="right-toc-title">文章目录</span>
                  <button class="collapse-btn" title="收起目录" @click="rightCollapsed = true">
                    <Icon name="heroicons:chevron-double-right-20-solid" size="16" />
                  </button>
                </div>
                <ArticleToc
                  v-if="currentPost"
                  :key="currentArticleId ?? 'none'"
                  content-selector=".post-content .md-editor-preview"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentPost" class="mobile-fab-group" :class="{ 'is-visible': mobileFabVisible }">
          <button
            class="mobile-fab-btn mobile-fab-btn--catalog"
            :class="{ active: mobileCatalogVisible }"
            aria-label="专题目录"
            @click="toggleMobileCatalog"
          >
            <Icon name="heroicons:queue-list-20-solid" size="20" />
          </button>
          <button
            class="mobile-fab-btn mobile-fab-btn--toc"
            :class="{ active: mobileTocVisible }"
            aria-label="文章目录"
            @click="toggleMobileToc"
          >
            <Icon name="heroicons:list-bullet-20-solid" size="20" />
          </button>
        </div>

        <Teleport to="body">
          <div v-if="mobileCatalogVisible" class="mobile-panel-overlay" @click="mobileCatalogVisible = false" />
          <div class="mobile-bottom-panel" :class="{ open: mobileCatalogVisible }">
            <div class="mobile-panel-header">
              <span>专题目录</span>
              <button aria-label="关闭专题目录" @click="mobileCatalogVisible = false">
                <Icon name="heroicons:x-mark-20-solid" size="20" />
              </button>
            </div>
            <div class="mobile-panel-body mobile-panel-body--catalog">
              <TopicSidebarNode
                v-for="node in catalogs"
                :key="`mobile-topic-${node.id}`"
                :node="node"
                :current-article-id="currentArticleId"
                @select="handleMobileCatalogSelect"
              />
            </div>
          </div>

          <div v-if="mobileTocVisible" class="mobile-panel-overlay" @click="mobileTocVisible = false" />
          <div class="mobile-bottom-panel mobile-bottom-panel--toc" :class="{ open: mobileTocVisible }">
            <div class="mobile-panel-header">
              <span>文章目录</span>
              <button aria-label="关闭文章目录" @click="mobileTocVisible = false">
                <Icon name="heroicons:x-mark-20-solid" size="20" />
              </button>
            </div>
            <div class="mobile-panel-body mobile-panel-body--toc">
              <ArticleToc
                v-if="currentPost"
                :key="`mobile-toc-${currentArticleId || 'none'}`"
                content-selector=".post-content .md-editor-preview"
              />
            </div>
          </div>
        </Teleport>
      </template>

      <div v-else class="empty-state">
        <p>专题不存在</p>
        <NuxtLink to="/topic" class="back-link">返回专题列表</NuxtLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { topicApi, type TopicDetail, type CatalogNode } from '~/api/content/topic'
import { postApi, type PostVO } from '~/api/content/post'
import { useDarkMode } from '~/composables/theme/useDarkMode'
import { sanitizeHtml } from '~/utils/security/xss'

const route = useRoute()

function parseTopicId(param: unknown): number | null {
  const raw = Array.isArray(param) ? param[0] : param
  const parsed = Number(raw)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

const topicId = computed(() => parseTopicId(route.params.id))
const { bannerVisible } = useAnnouncementBar()
const { isDark } = useDarkMode()

const stickyTop = computed(() => {
  if (bannerVisible.value) {
    return 'calc(var(--layout-navbar-height, 60px) + 10px + var(--layout-announcement-height, 36px))'
  }

  return 'calc(var(--layout-navbar-height, 60px) + 10px)'
})
const editorTheme = computed(() => isDark.value ? 'dark' : 'light')
const sanitizeMarkdownPreviewHtml = (html: string) => sanitizeHtml(html)

const topic = ref<TopicDetail | null>(null)
const catalogs = ref<CatalogNode[]>([])
const currentPost = ref<PostVO | null>(null)
const currentArticleId = ref<number | null>(null)
const loading = ref(true)
const articleLoading = ref(false)
const pageEntered = ref(false)
const contentEntered = ref(false)
const mobileCatalogVisible = ref(false)
const mobileTocVisible = ref(false)
const mobileFabVisible = ref(false)
let topicRequestId = 0
let articleRequestId = 0

// 左侧收缩
const leftCollapsed = ref(false)

// 右侧收缩
const rightCollapsed = ref(false)
const MOBILE_LAYOUT_BREAKPOINT = 1100

// 扁平化文章节点列表（用于上下篇导航）
const flatArticles = computed(() => {
  const result: CatalogNode[] = []
  function walk(nodes: CatalogNode[]) {
    for (const node of nodes) {
      if (node.articleId) result.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(catalogs.value)
  return result
})

const currentIndex = computed(() =>
  flatArticles.value.findIndex(n => n.articleId === currentArticleId.value)
)

const prevArticle = computed(() =>
  currentIndex.value > 0 ? flatArticles.value[currentIndex.value - 1] : null
)

const nextArticle = computed(() =>
  currentIndex.value >= 0 && currentIndex.value < flatArticles.value.length - 1
    ? flatArticles.value[currentIndex.value + 1]
    : null
)

// 加载文章内容
async function loadArticle(slug: string, articleId: number) {
  const requestId = ++articleRequestId
  articleLoading.value = true
  currentArticleId.value = articleId
  try {
    const res = await postApi.detail(slug)
    if (requestId !== articleRequestId) {
      return
    }
    currentPost.value = res.data.post
    useHead({ title: `${currentPost.value.title} - ${topic.value?.title} - Weblog` })
  } catch {
    if (requestId !== articleRequestId) {
      return
    }
    currentPost.value = null
  } finally {
    if (requestId === articleRequestId) {
      articleLoading.value = false
    }
  }
}

function formatViewCount(n: number): string {
  if (n >= 100000) return `${Math.floor(n / 10000)}w`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function toggleMobileCatalog() {
  mobileTocVisible.value = false
  mobileCatalogVisible.value = !mobileCatalogVisible.value
}

function toggleMobileToc() {
  mobileCatalogVisible.value = false
  mobileTocVisible.value = !mobileTocVisible.value
}

function handleMobileCatalogSelect(node: CatalogNode) {
  handleCatalogSelect(node)
  mobileCatalogVisible.value = false
}

function handleWindowResize() {
  if (!import.meta.client) return
  if (window.innerWidth > MOBILE_LAYOUT_BREAKPOINT) {
    mobileCatalogVisible.value = false
    mobileTocVisible.value = false
  }
  updateMobileFabVisibility()
}

function updateMobileFabVisibility() {
  if (!import.meta.client) return
  const isMobileLayout = window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT
  mobileFabVisible.value = isMobileLayout && Boolean(currentPost.value)
}

function restoreScrollPosition(scrollTop: number) {
  if (!import.meta.client || scrollTop < 0) {
    return
  }
  window.requestAnimationFrame(() => {
    window.scrollTo({ top: scrollTop })
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: scrollTop })
    })
  })
}

// 目录节点点击
async function handleCatalogSelect(node: CatalogNode) {
  if (!node.articleId || !node.slug) return
  if (node.articleId === currentArticleId.value) return
  const scrollTop = import.meta.client ? window.scrollY : 0
  mobileCatalogVisible.value = false
  mobileTocVisible.value = false
  await loadArticle(node.slug, node.articleId)
  restoreScrollPosition(scrollTop)
}

// 找到第一篇文章节点
function findFirstArticle(nodes: CatalogNode[]): CatalogNode | null {
  for (const node of nodes) {
    if (node.articleId && node.slug) return node
    if (node.children?.length) {
      const found = findFirstArticle(node.children)
      if (found) return found
    }
  }
  return null
}

function resetTopicState() {
  topic.value = null
  catalogs.value = []
  currentPost.value = null
  currentArticleId.value = null
  mobileCatalogVisible.value = false
  mobileTocVisible.value = false
}

async function loadTopicDetail() {
  const currentTopicId = topicId.value
  const requestId = ++topicRequestId
  articleRequestId += 1
  loading.value = true
  articleLoading.value = false
  resetTopicState()

  if (currentTopicId == null) {
    loading.value = false
    return
  }

  try {
    const [detailRes, catalogRes] = await Promise.all([
      topicApi.detail(currentTopicId),
      topicApi.catalogs(currentTopicId),
    ])

    if (requestId !== topicRequestId) {
      return
    }

    topic.value = detailRes.data
    catalogs.value = catalogRes.data
    useHead({ title: `${topic.value.title} - Weblog` })

    const first = findFirstArticle(catalogs.value)
    if (first) {
      const firstSlug = first.slug
      const firstArticleId = first.articleId
      if (firstSlug && firstArticleId) {
        await loadArticle(firstSlug, firstArticleId)
      }
    }
  } catch {
    if (requestId !== topicRequestId) {
      return
    }
    resetTopicState()
  } finally {
    if (requestId === topicRequestId) {
      loading.value = false
    }
  }
}

onMounted(async () => {
  if (import.meta.client) {
    window.addEventListener('resize', handleWindowResize)
    handleWindowResize()
    updateMobileFabVisibility()
    window.requestAnimationFrame(() => {
      pageEntered.value = true
    })
  }

  await loadTopicDetail()
})

watch([() => loading.value, () => topic.value?.id], ([isLoading, topicValueId]) => {
  if (isLoading || !topicValueId) {
    contentEntered.value = false
    return
  }

  if (!import.meta.client) {
    contentEntered.value = true
    return
  }

  window.requestAnimationFrame(() => {
    contentEntered.value = true
  })
}, { immediate: true })

watch(() => route.params.id, () => {
  void loadTopicDetail()
})

watch(() => currentPost.value?.id, () => {
  updateMobileFabVisibility()
})

onUnmounted(() => {
  if (import.meta.client) {
    window.removeEventListener('resize', handleWindowResize)
  }
})
</script>

<style scoped lang="scss">
.topic-loading-placeholder {
  min-height: 44vh;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.95rem;
  color: $color-text-muted;

  .dark & {
    color: $color-dark-text-muted;
  }
}

.topic-detail-bg {
  background: #f5f5f5;
  min-height: 100vh;
  .dark & { background: $color-dark-bg; }
}

.topic-detail-page {
  max-width: var(--layout-max-width);
  --topic-sidebar-width: 240px;
  --topic-sidebar-collapsed-width: 40px;
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x) calc(var(--layout-page-padding-y) + 1rem);
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.topic-detail-page.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.three-col-layout {
  display: flex;
  gap: 1.25rem;
  align-items: flex-start;
  min-width: 0;
  @media (max-width: 1100px) {
    flex-direction: column;
    .left-sidebar, .right-sidebar { display: none; }
  }
}

.detail-enter-block {
  opacity: 0;
  transform: translate3d(0, 12px, 0);
}

.three-col-layout.content-entered .detail-enter-block {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  transition:
    opacity 560ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.three-col-layout.content-entered .detail-enter-left {
  transition-delay: 40ms;
}

.three-col-layout.content-entered .detail-enter-center {
  transition-delay: 90ms;
}

.three-col-layout.content-entered .detail-enter-right {
  transition-delay: 140ms;
}

/* 左侧专题目录 */
.left-sidebar {
  width: var(--topic-sidebar-width);
  flex-shrink: 0;
  align-self: stretch;
  position: relative;
  transition: width 0.3s ease;
  &.collapsed { width: var(--topic-sidebar-collapsed-width); }
}

.left-sidebar-sticky {
  position: sticky;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 中间内容 */
.center-content {
  flex: 1;
  min-width: 0;
  transition: flex-basis 0.3s ease, width 0.3s ease;

  @media (max-width: 1100px) {
    width: 100%;
  }
}

.content-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 0.75rem 2.5rem 2rem;
  min-width: 0;
  width: 100%;
  .dark & { background: #171b20; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.2); }
  @media (max-width: $breakpoint-md) { padding: 0.75rem 1rem 1.25rem; }
}

.post-ad-slot {
  margin-bottom: 0.9rem;
}

.post-ad-slot--bottom {
  margin-top: 0.9rem;
}

.article-header {
  padding-bottom: 0.95rem;
  margin-bottom: 0.95rem;
  border-bottom: 1px solid #f0f0f0;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.topic-context {
  margin-bottom: 0.9rem;
  padding-bottom: 0.8rem;
  border-bottom: 1px dashed #e2e8f0;

  .dark & {
    border-bottom-color: rgba(148, 163, 184, 0.24);
  }
}

.topic-context-title {
  margin: 0;
  font-size: 1.08rem;
  font-weight: 700;
  line-height: 1.35;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.topic-context-summary {
  margin: 0.34rem 0 0;
  font-size: 0.84rem;
  line-height: 1.58;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.article-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.35;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.article-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-top: 0.6rem;
  color: $color-text-muted;
  font-size: 0.82rem;

  .dark & {
    color: #94a3b8;
  }
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.26rem;
}

/* MdPreview 适配 */
.post-content {
  position: relative;
  z-index: 0;
  isolation: isolate;
  min-width: 0;

  :deep(.md-editor-code),
  :deep(.md-editor-code-head),
  :deep(.md-editor-copy-button),
  :deep(.md-editor-collapse-tips) {
    z-index: 1 !important;
  }

  :deep(.md-editor) { border: none; background: transparent; }
  :deep(.md-editor-preview-wrapper) { padding: 0; }
  :deep(.md-editor-preview) {
    font-size: 1rem;
    line-height: 1.8;
    color: $color-text;
    .dark & { background: #171b20; }
    .dark & { color: $color-dark-text; }
    @media (max-width: $breakpoint-md) { font-size: 0.95rem; }
  }
  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    scroll-margin-top: var(--sticky-top, calc(var(--layout-navbar-height, 60px) + 10px));
  }
  :deep(.md-editor-preview),
  :deep(.md-editor-preview p),
  :deep(.md-editor-preview li),
  :deep(.md-editor-preview blockquote) {
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  :deep(pre),
  :deep(.md-editor-code),
  :deep(.md-editor-code-head),
  :deep(table) {
    max-width: 100%;
    overflow-x: auto;
  }

  :deep(table) {
    display: block;
  }

  :deep(img) { max-width: 100%; border-radius: $radius-md; }
}

/* 上下篇导航（参考文章详情页） */
.post-nav {
  display: flex;
  gap: 1rem;
  margin-top: 1.25rem;
  @media (max-width: 1100px) { flex-direction: column; }
}
.nav-item {
  flex: 1;
  min-width: 0;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  &:hover {
    .nav-cover-img { transform: scale(1.05); }
    .nav-overlay::after { opacity: 1; }
  }
}
.nav-cover {
  width: 100%;
  height: 100px;
  position: relative;
  background: linear-gradient(135deg, #e2e8f0 0%, #cbd5e1 100%);
  .dark & { background: linear-gradient(135deg, $color-dark-bg-elevated 0%, $color-dark-border 100%); }
}
.nav-cover-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}
.nav-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55) 0%, rgba(0, 0, 0, 0.15) 60%, transparent 100%);
  color: #fff;
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.7) 0%, rgba(0, 0, 0, 0.2) 60%, rgba(0, 0, 0, 0.05) 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }
  > * { position: relative; z-index: 1; }
}
.nav-label {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8rem;
  font-weight: 500;
  margin-bottom: 0.375rem;
  opacity: 0.9;
  .nav-next & { justify-content: flex-end; }
}
.nav-title {
  font-size: 0.95rem;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.4);
  .nav-next & { text-align: right; }
}
.nav-prev { text-align: left; }
.nav-next { text-align: right; }
.nav-placeholder { visibility: hidden; height: 0; }

/* 右侧文章目录 */
.right-sidebar {
  width: var(--topic-sidebar-width);
  flex-shrink: 0;
  align-self: stretch;
  position: relative;
  transition: width 0.3s ease;
  &.collapsed { width: var(--topic-sidebar-collapsed-width); }
}
.right-sidebar-sticky {
  position: sticky;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.sidebar-toggle-btn {
  position: absolute;
  top: 0;
  right: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: #fff;
  color: $color-text-muted;
  cursor: pointer;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.08);
  opacity: 0;
  transform: scale(0.8);
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease, color 0.2s, background 0.2s;
  &.visible { opacity: 1; transform: scale(1); pointer-events: auto; }
  &:hover { color: $color-primary; background: #f0f7ff; }
  .dark & {
    background: $color-dark-bg-secondary;
    color: $color-dark-text-muted;
    box-shadow: 0 1px 6px rgba(0, 0, 0, 0.2);
    &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.1); }
  }
}

.right-toc-panel {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  opacity: 0;
  transform: translateX(8px);
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease;
  &.visible { opacity: 1; transform: translateX(0); pointer-events: auto; }
  .dark & { background: $color-dark-bg-secondary; box-shadow: 0 1px 8px rgba(0, 0, 0, 0.2); }

  /* 覆盖 ArticleToc 内部样式 */
  :deep(.article-toc-container) { width: 100%; }
  :deep(.article-toc) { box-shadow: none; border-radius: 0; padding-top: 0; }
  :deep(.toc-header) { display: none; }
}

.right-toc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f0f0f0;
  .dark & { border-bottom-color: $color-dark-border; }
}

.right-toc-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.collapse-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: none;
  color: #999;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  &:hover { background: #f0f7ff; color: $color-primary; }
  .dark & { color: $color-dark-text-muted; &:hover { background: rgba(59, 130, 246, 0.1); color: $color-primary; } }
}

.mobile-fab-group {
  display: none;
}

.mobile-fab-btn {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease, background 0.2s ease;

  &.active {
    background: rgba(37, 99, 235, 0.76);
  }

  &:active {
    transform: scale(0.96);
  }
}

.mobile-panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.48);
  z-index: 3000;
}

.mobile-bottom-panel {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 0;
  background: #fff;
  z-index: 4000;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -6px 24px rgba(15, 23, 42, 0.14);
  transition: height 0.28s ease;
  pointer-events: none;

  &.open {
    height: min(74vh, 620px);
    pointer-events: auto;
  }

  .dark & {
    background: $color-dark-bg-secondary;
  }
}

.mobile-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid #f0f0f0;
  font-size: 0.95rem;
  font-weight: 600;
  color: $color-text;

  button {
    border: none;
    background: none;
    color: $color-text-muted;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    width: 30px;
    height: 30px;
    border-radius: 8px;
  }

  .dark & {
    color: $color-dark-text;
    border-bottom-color: $color-dark-border;

    button {
      color: #94a3b8;
    }
  }
}

.mobile-panel-body {
  height: calc(100% - 50px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0.65rem 0.9rem calc(0.9rem + env(safe-area-inset-bottom));
}

.mobile-panel-body--catalog {
  :deep(.node-row) {
    margin: 1px 0;
    border-radius: 8px;
    padding: 0.45rem 0.55rem;
    font-size: 0.86rem;
  }

  :deep(.node-row.level-2) { padding-left: 1.2rem; }
  :deep(.node-row.level-3) { padding-left: 1.9rem; }
  :deep(.node-row.level-4) { padding-left: 2.5rem; }
  :deep(.node-title) {
    white-space: normal;
    word-break: break-word;
    line-height: 1.35;
  }
}

.mobile-panel-body--toc {
  :deep(.article-toc-container) { width: 100%; }
  :deep(.article-toc) { box-shadow: none; border-radius: 0; padding: 0; }
  :deep(.toc-header) { display: none; }
}

@media (max-width: 1100px) {
  .topic-detail-bg,
  .topic-detail-page {
    overflow-x: hidden;
  }

  .content-card {
    border-radius: 12px;
    padding: 0.8rem 1rem 1.15rem;
    overflow-x: hidden;
  }

  .topic-context {
    margin-bottom: 0.78rem;
    padding-bottom: 0.7rem;
  }

  .topic-context-title {
    font-size: 1rem;
  }

  .topic-context-summary {
    margin-top: 0.28rem;
    font-size: 0.8rem;
    line-height: 1.5;
  }

  .article-title {
    font-size: 1.28rem;
  }

  .article-meta {
    margin-top: 0.5rem;
    gap: 0.5rem;
    font-size: 0.78rem;
  }

  .post-nav {
    margin-top: 1rem;
  }

  .mobile-fab-group {
    display: flex;
    position: fixed;
    right: 40px;
    bottom: calc(126px + env(safe-area-inset-bottom));
    flex-direction: column;
    gap: 10px;
    z-index: 998;
    opacity: 0;
    visibility: hidden;
    transform: translateY(20px);
    pointer-events: none;
    transition: opacity 0.3s, visibility 0.3s, transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    &.is-visible {
      opacity: 1;
      visibility: visible;
      transform: translateY(0);
      pointer-events: auto;
    }
  }
}

@media (max-width: 768px) {
  .mobile-fab-group {
    right: 20px;
    bottom: calc(96px + env(safe-area-inset-bottom));
  }
}

/* 空状态 */
.empty-article {
  text-align: center;
  padding: 4rem;
  color: #94a3b8;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  .dark & { background: $color-dark-bg-secondary; color: $color-dark-text-muted; }
}

.empty-state { text-align: center; padding: 4rem; color: #94a3b8; }
.back-link { display: inline-block; margin-top: 1rem; color: $color-primary; text-decoration: underline; }

@media (prefers-reduced-motion: reduce) {
  .topic-detail-page,
  .topic-detail-page.page-entered,
  .detail-enter-block,
  .three-col-layout.content-entered .detail-enter-block {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
