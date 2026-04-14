<template>
  <div class="post-detail-bg" :style="{ '--sticky-top': stickyTop }">
    <div class="post-detail-page" :class="{ 'page-entered': pageEntered }">
    <UnifiedPageLoader v-if="loading" text="加载中..." />

    <template v-else-if="post">
      <div :key="post.id" class="three-col-layout" :class="{ 'content-entered': contentEntered }">
        <!-- 左侧操作栏 -->
        <div class="left-sidebar detail-enter-block detail-enter-left">
          <div class="left-sidebar-sticky" :style="{ top: stickyTop }">
            <ArticleActionSidebar
              :post-id="post.id"
              :prev-slug="prevPost?.slug || null"
              :next-slug="nextPost?.slug || null"
              :like-count="post.likeCount"
              :collect-count="post.collectCount"
              :comment-count="post.commentCount"
              :post-title="post.title"
              :post-summary="post.summary"
              :post-author="post.authorNickname || ''"
              @scroll-to-comments="scrollToComments"
              @like-toggled="(_liked, c) => { post!.likeCount = c }"
            />
          </div>
        </div>

        <!-- 中间内容区 -->
        <div class="center-content detail-enter-block detail-enter-center">
          <div class="post-ad-slot post-ad-slot--top">
            <AdSlotBanner ad-slot="post_top" />
          </div>

          <div class="content-card">
            <!-- 文章头部 -->
            <header class="post-header">
              <h1 class="post-title">{{ post.title }}</h1>
              <div class="post-meta">
                <NuxtLink v-if="post.categoryName" :to="categoryPath" class="badge badge-cat">
                  <Icon name="heroicons:folder-16-solid" size="14" />
                  {{ post.categoryName }}
                </NuxtLink>
                <NuxtLink v-if="post.subCategoryName" :to="subCategoryPath" class="badge badge-sub">
                  <Icon name="heroicons:folder-open-16-solid" size="14" />
                  {{ post.subCategoryName }}
                </NuxtLink>
                <span class="meta-item">
                  <Icon name="heroicons:calendar-20-solid" size="14" />
                  {{ formatDate(post.createTime) }}
                </span>
                <span class="meta-item">
                  <Icon name="heroicons:eye-20-solid" size="14" />
                  {{ formatViewCount(post.viewCount) }}
                </span>
              </div>
              <div v-if="post.tags?.length" class="post-tags">
                <NuxtLink
                  v-for="(tag, index) in post.tags"
                  :key="tag.id"
                  :to="{ path: '/category', query: { tagId: tag.id } }"
                  class="tag-link"
                  :style="{ '--tag-color': getTagColor(index), '--tag-bg': getTagColor(index) + '18', '--tag-bg-hover': getTagColor(index) + '25' }"
                >
                  <Icon name="heroicons:tag-16-solid" size="14" class="tag-icon" />
                  {{ tag.name }}
                </NuxtLink>
              </div>
            </header>

            <!-- 文章内容 -->
            <ClientOnly>
              <MdPreview
                editor-id="post-preview"
                :model-value="markdownContent"
                :sanitize="sanitizeMarkdownPreviewHtml"
                :theme="editorTheme"
                :preview-theme="previewTheme"
                :code-theme="codeTheme"
                :code-foldable="false"
                :show-code-row-number="true"
                :no-mermaid="true"
                :no-katex="true"
                class="post-content"
              />
            </ClientOnly>

            <!-- 最后编辑时间 -->
            <div class="last-edit">
              <Icon name="heroicons:pencil-square-20-solid" size="16" />
              <span>最后编辑于 {{ formatLastEditTime(post.updateTime || post.createTime) }}</span>
            </div>

            <!-- 免责声明 -->
            <ArticleDisclaimer />
          </div>

          <div class="post-ad-slot post-ad-slot--bottom">
            <AdSlotBanner ad-slot="post_bottom" />
          </div>

          <!-- 互动按钮（移动端显示） -->
          <div class="mobile-interaction">
            <InteractionBar
              :post-id="post.id"
              :like-count="post.likeCount"
              :comment-count="post.commentCount"
              :post-title="post.title"
              :post-summary="post.summary"
              :post-author="post.authorNickname || ''"
              @scroll-to-comments="scrollToComments"
              @like-count-update="(c) => post!.likeCount = c"
            />
          </div>

          <!-- 上下篇导航 -->
          <nav class="post-nav">
            <NuxtLink v-if="prevPost" :to="`/post/${prevPost.slug}`" class="nav-item nav-prev">
              <div class="nav-cover">
                <img v-if="prevPost.coverImage" :src="prevPost.coverImage" :alt="prevPost.title" loading="lazy" class="nav-cover-img" >
                <div class="nav-overlay">
                  <span class="nav-label">
                    <Icon name="heroicons:chevron-left-20-solid" size="18" />
                    上一篇
                  </span>
                  <span class="nav-title">{{ prevPost.title }}</span>
                </div>
              </div>
            </NuxtLink>
            <div v-else class="nav-item nav-placeholder" />
            <NuxtLink v-if="nextPost" :to="`/post/${nextPost.slug}`" class="nav-item nav-next">
              <div class="nav-cover">
                <img v-if="nextPost.coverImage" :src="nextPost.coverImage" :alt="nextPost.title" loading="lazy" class="nav-cover-img" >
                <div class="nav-overlay">
                  <span class="nav-label">
                    下一篇
                    <Icon name="heroicons:chevron-right-20-solid" size="18" />
                  </span>
                  <span class="nav-title">{{ nextPost.title }}</span>
                </div>
              </div>
            </NuxtLink>
            <div v-else class="nav-item nav-placeholder" />
          </nav>

          <!-- 评论区 -->
          <div ref="commentSectionRef">
            <CommentSection :post-id="post.id" />
          </div>
        </div>

        <!-- 右侧目录 + 推荐 -->
        <div class="right-sidebar detail-enter-block detail-enter-right">
          <div class="right-sidebar-sticky" :style="{ top: stickyTop }">
            <ArticleToc content-selector=".post-content .md-editor-preview" />
            <SimilarPosts :post-id="post.id" :category-id="post.categoryId" />
          </div>
        </div>
      </div>

      <Teleport to="body">
        <div v-if="hasToc" class="mobile-fab-group" :class="{ 'is-visible': mobileFabVisible }">
          <button
            class="mobile-fab-btn mobile-fab-btn--toc"
            :class="{ active: tocVisible }"
            aria-label="文章目录"
            @click="toggleMobileToc"
          >
            <Icon name="heroicons:list-bullet-20-solid" size="20" />
          </button>
        </div>

        <div v-if="tocVisible" class="mobile-panel-overlay" @click="tocVisible = false" />
        <div class="mobile-bottom-panel mobile-bottom-panel--toc" :class="{ open: tocVisible }">
          <div class="mobile-panel-header">
            <span>文章目录</span>
            <button aria-label="关闭文章目录" @click="tocVisible = false">
              <Icon name="heroicons:x-mark-20-solid" size="20" />
            </button>
          </div>
          <div class="mobile-panel-body mobile-panel-body--toc">
            <ArticleToc content-selector=".post-content .md-editor-preview" />
          </div>
        </div>
      </Teleport>
    </template>

    <div v-else-if="detailErrorMessage" class="empty-state">
      <p>{{ detailErrorMessage }}</p>
      <button type="button" class="retry-btn" @click="retryLoadPost">重试</button>
      <NuxtLink to="/" class="back-link">返回首页</NuxtLink>
    </div>

    <div v-else class="empty-state">
      <p>文章不存在</p>
      <NuxtLink to="/" class="back-link">返回首页</NuxtLink>
    </div>
    
    <!-- 阅读限制弹窗 -->
    <ReadLimitOverlay
      :show="readLimitState.show"
      :read-count="readLimitState.readCount"
      :limit="readLimitState.limit"
      @login="handleReadLimitLogin"
      @close="readLimitState.show = false"
    />
  </div>
  </div>
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { postApi, type PostVO } from '~/api/content/post'
import { categoryApi, type CategoryTreeVO } from '~/api/content/category'
import { getTagColor } from '~/utils/content/tagColor'
import { useDarkMode } from '~/composables/theme/useDarkMode'
import { accessApi } from '~/api/interaction/access'
import ReadLimitOverlay from '~/components/article/ReadLimitOverlay.vue'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/modal/useLoginModal'
import { buildCategoryPathById } from '~/utils/navigation/categoryRoute'
import { isSecurityGatewayBlockedError } from '~/utils/network/http'
import { sanitizeHtml } from '~/utils/security/xss'

definePageMeta({
  key: route => String(route.params.slug || route.fullPath),
})

const route = useRoute()
const slug = computed(() => String(route.params.slug || ''))
const { bannerVisible } = useAnnouncementBar()
const { isDark } = useDarkMode()

const detailAsyncKey = computed(() => `post-detail:${slug.value}`)
const detailErrorMessage = ref<string | null>(null)

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

// sticky top 值：导航栏 + 间距 10px + 公告栏（如果有）
const stickyTop = computed(() => {
  if (bannerVisible.value) {
    return 'calc(var(--layout-navbar-height, 60px) + 10px + var(--layout-announcement-height, 36px))'
  }

  return 'calc(var(--layout-navbar-height, 60px) + 10px)'
})

const { data: detailData, pending: loading, refresh: refreshDetailData } = await useAsyncData(
  detailAsyncKey,
  async () => {
    if (!slug.value) return null
    detailErrorMessage.value = null
    try {
      const res = await postApi.detail(slug.value)
      return res.data
    } catch (error) {
      const code = getHttpErrorCode(error)
      if (isSecurityGatewayBlockedError(error)) {
        detailErrorMessage.value = '请求被安全网关拦截，请稍后重试'
        return null
      }
      if (code !== 404) {
        detailErrorMessage.value = '文章加载失败，请稍后重试'
      }
      return null
    }
  },
)

async function retryLoadPost() {
  await refreshDetailData()
}

const { data: categoryTreeData } = await useAsyncData(
  'post-category-tree',
  async () => {
    try {
      const res = await categoryApi.tree()
      return res.data
    } catch {
      return [] as CategoryTreeVO[]
    }
  },
)

const categoryTree = computed(() => categoryTreeData.value || [])

const post = ref<PostVO | null>(null)
const prevPost = ref<{ id: number; title: string; slug: string; coverImage?: string } | null>(null)
const nextPost = ref<{ id: number; title: string; slug: string; coverImage?: string } | null>(null)
const tocVisible = ref(false)
const hasToc = ref(false)
const mobileFabVisible = ref(false)
const commentSectionRef = ref<HTMLElement | null>(null)
const pageEntered = ref(false)
const contentEntered = ref(false)
const hydrationReady = ref(false)
const MOBILE_LAYOUT_BREAKPOINT = 1100

const userStore = useUserStore()
const loginModal = useLoginModal()
const loginFromReadLimit = ref(false)
const readLimitLoginSucceeded = ref(false)
const restoreScrollY = ref<number | null>(null)

// 阅读限制状态
const readLimitState = ref({
  show: false,
  readCount: 0,
  limit: 3,
  loggedIn: false,
})

watch(detailData, (value) => {
  post.value = value?.post ?? null
  prevPost.value = value?.prev ?? null
  nextPost.value = value?.next ?? null
}, { immediate: true })

useHead(() => ({
  title: post.value?.title ? `${post.value.title} - Weblog` : 'Weblog',
  meta: [
    { name: 'description', content: post.value?.seoDescription || post.value?.summary || '' },
    { name: 'keywords', content: post.value?.seoKeywords || '' },
  ],
}))

function scrollToComments() {
  commentSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function restoreScrollPosition() {
  if (!import.meta.client) return
  const y = restoreScrollY.value
  if (typeof y !== 'number') return
  // BaseModal 会锁滚动/解锁滚动，部分浏览器可能导致轻微跳动，做一次 rAF 兜底
  requestAnimationFrame(() => {
    window.scrollTo({ top: y })
    requestAnimationFrame(() => window.scrollTo({ top: y }))
  })
}

function handleReadLimitLogin() {
  loginFromReadLimit.value = true
  readLimitLoginSucceeded.value = false
  readLimitState.value.show = false
  restoreScrollY.value = import.meta.client ? window.scrollY : null
  loginModal.open('code', () => {
    // 登录成功：保持当前位置，解除限制
    readLimitLoginSucceeded.value = true
    readLimitState.value.show = false
    readLimitState.value.loggedIn = true
    restoreScrollPosition()
  })
}

watch(() => loginModal.visible.value, (v, oldV) => {
  if (oldV === true && v === false && loginFromReadLimit.value) {
    loginFromReadLimit.value = false
    if (readLimitLoginSucceeded.value) {
      readLimitLoginSucceeded.value = false
      readLimitState.value.show = false
      readLimitState.value.loggedIn = true
      restoreScrollPosition()
      return
    }

    if (!userStore.userInfo?.userId) {
      // 用户未登录就关闭了登录弹窗：继续限制
      readLimitState.value.show = true
      restoreScrollPosition()
    } else {
      readLimitState.value.show = false
      readLimitState.value.loggedIn = true
      restoreScrollPosition()
    }
  }
})

// 登录态发生变化时（尤其是退出登录），必须重新执行阅读限制校验，避免“登出后无限阅读”
watch(() => userStore.userInfo?.userId, async (uid, oldUid) => {
  if (uid === oldUid) return
  // 登出：重新校验限制（可能需要弹窗）
  if (!uid) {
    readLimitState.value.loggedIn = false
    // 仅在文章已加载时校验
    if (post.value?.id) {
      await checkAccess()
    }
  }
})

// 预览主题
const previewTheme = computed(() => post.value?.previewTheme || 'default')
const codeTheme = computed(() => post.value?.codeTheme || 'atom')
const editorTheme = computed(() => isDark.value ? 'dark' : 'light')
const markdownContent = computed(() => post.value?.content || '')
const sanitizeMarkdownPreviewHtml = (html: string) => sanitizeHtml(html)

const categoryPath = computed(() => {
  if (!post.value) return '/category'
  return buildCategoryPathById(categoryTree.value, post.value.categoryId, null)
})

const subCategoryPath = computed(() => {
  if (!post.value) return '/category'
  return buildCategoryPathById(categoryTree.value, post.value.categoryId, post.value.subCategoryId)
})

function formatViewCount(n: number): string {
  if (n >= 100000) return (n / 10000).toFixed(0) + 'w'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatTimeAgo(dateStr: string): string {
  const now = Date.now()
  const past = new Date(dateStr).getTime()
  const diff = now - past
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months} 个月前`
  return `${Math.floor(months / 12)} 年前`
}

function formatLastEditTime(dateStr: string): string {
  if (!hydrationReady.value) {
    return formatDate(dateStr)
  }
  return formatTimeAgo(dateStr)
}

// 记录阅读
async function recordRead() {
  if (!post.value) return

  // 已登录用户跳过
  if (userStore.userInfo?.userId) {
    return
  }

  try {
    await accessApi.recordRead(post.value.id)
  } catch (error) {
    console.error('记录阅读失败:', error)
  }
}

// 检查是否可以阅读
async function checkAccess(): Promise<boolean | null> {
  if (!post.value) return null

  // 已登录用户跳过检查
  if (userStore.userInfo?.userId) {
    readLimitState.value.loggedIn = true
    return true
  }

  try {
    const res = await accessApi.check(post.value.id)
    const { allowed, readCount, limit } = res.data

    readLimitState.value = {
      show: !allowed,
      readCount,
      limit,
      loggedIn: false,
    }

    return allowed
  } catch (error) {
    console.error('检查阅读权限失败:', error)
    return null
  }
}

let tocDetectTimer: ReturnType<typeof setTimeout> | null = null

function detectToc() {
  if (!import.meta.client) return
  if (tocDetectTimer) {
    clearTimeout(tocDetectTimer)
  }
  tocDetectTimer = setTimeout(() => {
    const contentEl = document.querySelector('.post-content .md-editor-preview')
    hasToc.value = Boolean(contentEl?.querySelectorAll('h1, h2, h3, h4').length)
    updateMobileFabVisibility()
  }, 500)
}

function toggleMobileToc() {
  tocVisible.value = !tocVisible.value
}

function handleWindowResize() {
  if (!import.meta.client) return
  if (window.innerWidth > MOBILE_LAYOUT_BREAKPOINT) {
    tocVisible.value = false
  }
  updateMobileFabVisibility()
}

function updateMobileFabVisibility() {
  if (!import.meta.client) return
  const isMobileLayout = window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT
  mobileFabVisible.value = isMobileLayout && Boolean(post.value) && hasToc.value
}

async function checkAndRecordAccess() {
  const allowed = await checkAccess()
  if (allowed === true) {
    await recordRead()
  }
}

watch(() => post.value?.id, (id) => {
  tocVisible.value = false
  hasToc.value = false
  mobileFabVisible.value = false
  readLimitState.value.show = false
  if (!id || !import.meta.client) return
  detectToc()
  void checkAndRecordAccess()
}, { immediate: true })

watch(hasToc, () => {
  updateMobileFabVisibility()
})

watch([() => loading.value, () => post.value?.id], ([isLoading, postId]) => {
  if (isLoading || !postId) {
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

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  hydrationReady.value = true

  if (!loading.value && !post.value && slug.value) {
    void refreshDetailData()
  }

  window.addEventListener('resize', handleWindowResize)
  handleWindowResize()

  window.requestAnimationFrame(() => {
    pageEntered.value = true
  })
})

onUnmounted(() => {
  if (tocDetectTimer) {
    clearTimeout(tocDetectTimer)
  }

  if (import.meta.client) {
    window.removeEventListener('resize', handleWindowResize)
  }

})


</script>

<style scoped lang="scss">
/* 灰色背景全宽 */
.post-detail-bg {
  background: #f5f5f5;
  min-height: 100vh;
  .dark & { background: $color-dark-bg; }
}

/* 三栏布局 */
.post-detail-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: 1rem 1rem 2rem;
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.post-detail-page.page-entered {
  opacity: 1;
  transform: none;
}
.three-col-layout {
  display: flex;
  gap: 1.25rem;
  align-items: flex-start;
  @media (max-width: 1100px) { flex-direction: column; }
}

.detail-enter-block {
  opacity: 0;
  transform: translate3d(0, 12px, 0);
}

.three-col-layout.content-entered .detail-enter-block {
  opacity: 1;
  transform: none;
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

/* 左侧操作栏 */
.left-sidebar {
  width: 50px;
  flex-shrink: 0;
  align-self: stretch;
  @media (max-width: 1100px) { display: none; }
}
.left-sidebar-sticky {
  position: sticky;
}

/* 中间内容 */
.center-content {
  flex: 1;
  min-width: 0;
  max-width: calc(100% - 50px - 240px - 2.5rem);
  @media (max-width: 1100px) { max-width: 100%; }
}
.content-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  padding: 0.75rem 2rem 2rem;
  .dark & { background: #171b20; box-shadow: 0 2px 12px rgba(0,0,0,0.2); }
  @media (max-width: $breakpoint-md) { padding: 0.75rem 1.25rem 1.25rem; }
}

.post-ad-slot {
  margin-bottom: 0.9rem;
}

.post-ad-slot--bottom {
  margin-top: 0.9rem;
}

/* 右侧目录 */
.right-sidebar {
  width: 240px;
  flex-shrink: 0;
  align-self: stretch;
  @media (max-width: 1100px) { display: none; }
}
.right-sidebar-sticky {
  position: sticky;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 移动端互动栏 */
.mobile-interaction {
  display: none;
  @media (max-width: 1100px) { display: block; }
}

/* 文章头部 */
.post-header {
  padding-bottom: 1rem;
  border-bottom: 1px solid #f0f0f0;
  .dark & { border-bottom-color: $color-dark-border; }
}
.post-title {
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1.35;
  color: $color-text;
  margin-bottom: 0.75rem;
  .dark & { color: $color-dark-text; }
  @media (max-width: $breakpoint-md) { font-size: 1.4rem; }
}
.post-meta {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  font-size: 0.8rem;
  color: $color-text-muted;
  flex-wrap: wrap;
  .dark & { color: $color-dark-text-muted; }
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}
.badge-cat, .badge-sub {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: $color-text-muted;
  text-decoration: none;
  transition: color 0.2s;
  cursor: pointer;
  &:hover { color: $color-primary; }
  .dark & { color: $color-dark-text-muted; &:hover { color: $color-primary; } }
}
.post-tags { display: flex; gap: 0.5rem; margin-top: 0.625rem; flex-wrap: wrap; }
.tag-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 500;
  text-decoration: none;
  color: var(--tag-color);
  background: var(--tag-bg);
  border: 1px solid transparent;
  transition: transform 0.25s ease, background 0.25s ease, border-color 0.25s ease;
  cursor: pointer;
  &:hover {
    transform: translateY(-2px);
    background: var(--tag-bg-hover);
    border-color: var(--tag-color);
    .tag-icon { transform: rotate(-15deg); }
  }
  .dark & {
    background: var(--tag-bg);
    &:hover { background: var(--tag-bg-hover); border-color: var(--tag-color); }
  }
}
.tag-icon {
  flex-shrink: 0;
  transition: transform 0.25s ease;
}

/* 文章内容（MdPreview 适配） */
.post-content {
  position: relative;
  z-index: 0;
  isolation: isolate;

  :deep(.md-editor-code),
  :deep(.md-editor-code-head),
  :deep(.md-editor-copy-button),
  :deep(.md-editor-collapse-tips) {
    z-index: 1 !important;
  }

  :deep(.md-editor) {
    border: none;
    background: transparent;
  }
  :deep(.md-editor-preview-wrapper) {
    padding: 0;
  }
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
  :deep(img) {
    max-width: 100%;
    border-radius: $radius-md;
  }
}

/* 最后编辑时间 */
.last-edit {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.25rem;
  color: #888;
  font-size: 0.85rem;
  margin: 2rem 0 0.5rem;
  .dark & { color: $color-dark-text-muted; }
}

/* 上下篇导航 */
.post-nav {
  display: flex;
  gap: 1rem;
  margin-top: 1.5rem;
  @media (max-width: $breakpoint-md) { flex-direction: column; }
}
.nav-item {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  cursor: pointer;
  &:hover {
    .nav-cover-img { transform: scale(1.05); }
    .nav-overlay::after { opacity: 1; }
  }
}
.nav-cover {
  width: 100%;
  height: 130px;
  position: relative;
  background-color: #e2e8f0;
  .dark & { background-color: $color-dark-border; }
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
  background: linear-gradient(to top, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0.1) 60%, transparent 100%);
  color: #fff;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.25) 60%, rgba(0,0,0,0.05) 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }
  > * { position: relative; z-index: 1; }
}
.nav-label {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: 500;
  margin-bottom: 0.5rem;
  opacity: 0.9;
  .nav-next & { justify-content: flex-end; }
}
.nav-title {
  font-size: 1.05rem;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-shadow: 0 2px 4px rgba(0,0,0,0.4);
  .nav-next & { text-align: right; }
}
.nav-prev { text-align: left; }
.nav-next { text-align: right; }
.nav-placeholder { visibility: hidden; height: 0; }

/* 移动端目录（与专题详情页一致） */
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
  height: min(74vh, 620px);
  max-height: min(74vh, 620px);
  background: #fff;
  z-index: 4000;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -6px 24px rgba(15, 23, 42, 0.14);
  visibility: hidden;
  transform: translate3d(0, 100%, 0);
  transition: transform 0.28s ease, visibility 0s linear 0.28s;
  will-change: transform;
  contain: layout paint;
  pointer-events: none;

  &.open {
    visibility: visible;
    transform: translate3d(0, 0, 0);
    transition: transform 0.28s ease, visibility 0s;
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
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 0.65rem 0.9rem calc(0.9rem + env(safe-area-inset-bottom));
}

.mobile-panel-body--toc {
  :deep(.article-toc-container) { width: 100%; }
  :deep(.article-toc) { box-shadow: none; border-radius: 0; padding: 0; }
  :deep(.toc-header) { display: none; }
}

@media (max-width: 1100px) {
  .mobile-fab-group {
    display: flex;
    position: fixed;
    right: 40px;
    bottom: calc(152px + env(safe-area-inset-bottom));
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

  :global(body.with-comment-bottom-bar) .mobile-fab-group {
    bottom: calc(var(--comment-bottom-bar-avoidance, 0px) + env(safe-area-inset-bottom) + 36px);
    z-index: 180;
  }
}

@media (max-width: 768px) {
  .mobile-fab-group {
    right: 20px;
    bottom: calc(122px + env(safe-area-inset-bottom));
  }

  :global(body.with-comment-bottom-bar) .mobile-fab-group {
    bottom: calc(var(--comment-bottom-bar-avoidance, 0px) + env(safe-area-inset-bottom) + 30px);
    z-index: 180;
  }
}

/* 状态 */
.empty-state { text-align: center; padding: 4rem; color: #94a3b8; }
.retry-btn {
  margin-top: 1rem;
  border: none;
  background: rgba(37, 99, 235, 0.92);
  color: #fff;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  cursor: pointer;
}
.back-link { display: inline-block; margin-top: 1rem; color: $color-primary; text-decoration: underline; }

@media (prefers-reduced-motion: reduce) {
  .post-detail-page,
  .post-detail-page.page-entered,
  .detail-enter-block,
  .three-col-layout.content-entered .detail-enter-block {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
