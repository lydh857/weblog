<template>
  <div class="category-posts-page">
    <UnifiedPageLoader v-if="loading" text="加载中..." />

    <template v-else>
      <header class="page-header">
        <NuxtLink to="/category" class="back-link">
          <Icon name="heroicons:arrow-left-20-solid" size="18" /> 所有分类
        </NuxtLink>
        <h1 class="page-title">{{ categoryName }}</h1>
        <p v-if="categoryDesc" class="page-desc">{{ categoryDesc }}</p>
      </header>

      <section class="post-list">
        <template v-if="posts.length">
          <template v-for="item in postRenderItems" :key="item.key">
            <article v-if="item.type === 'post'" class="post-item">
              <NuxtLink :to="`/post/${item.post.slug}`" class="post-link">
                <h3 class="post-title">{{ item.post.title }}</h3>
                <p v-if="item.post.summary" class="post-summary">{{ item.post.summary }}</p>
                <div class="post-meta">
                  <time>{{ formatDate(item.post.createTime) }}</time>
                  <span>{{ item.post.viewCount }} 阅读</span>
                </div>
              </NuxtLink>
            </article>
            <AdMimicCard v-else class="post-item post-item--ad" :ad="item.ad" />
          </template>

          <div v-if="totalPages > 1" class="pagination">
            <button class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">
              <Icon name="heroicons:chevron-left-20-solid" size="18" />
            </button>
            <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
            <button class="page-btn" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">
              <Icon name="heroicons:chevron-right-20-solid" size="18" />
            </button>
          </div>
        </template>
        <div v-else class="empty-state">该分类下暂无文章</div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { postApi, type PostVO } from '~/api/post'
import { categoryApi } from '~/api/category'
import { advertisementApi, type AdvertisementVO } from '~/api/advertisement'

const route = useRoute()
const slug = computed(() => String(route.params.slug || ''))

function parsePage(page: unknown): number {
  if (Array.isArray(page)) {
    return parsePage(page[0])
  }
  const value = Number(page)
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 1
}

const currentPage = ref(parsePage(route.query.page))
const pageSize = 10

const { data: categoryPageData, pending: loading } = await useAsyncData(
  'category-page',
  async () => {
    if (!slug.value) {
      return {
        categoryName: '',
        categoryDesc: '',
        posts: [] as PostVO[],
        totalPages: 0,
        listCardAds: [] as AdvertisementVO[],
      }
    }

    try {
      const [catRes, postRes, adRes] = await Promise.all([
        categoryApi.getBySlug(slug.value),
        postApi.list({ pageNum: currentPage.value, pageSize, categorySlug: slug.value }),
        advertisementApi.getBySlot('post_list_card').catch(() => ({ data: [] as AdvertisementVO[] })),
      ])

      return {
        categoryName: catRes.data.name,
        categoryDesc: catRes.data.description || '',
        posts: postRes.data.records,
        totalPages: postRes.data.pages,
        listCardAds: adRes.data || [],
      }
    } catch {
      return {
        categoryName: '',
        categoryDesc: '',
        posts: [] as PostVO[],
        totalPages: 0,
        listCardAds: [] as AdvertisementVO[],
      }
    }
  },
  {
    watch: [slug, currentPage],
  },
)

const categoryName = computed(() => categoryPageData.value?.categoryName || '')
const categoryDesc = computed(() => categoryPageData.value?.categoryDesc || '')
const posts = computed(() => categoryPageData.value?.posts || [])
const totalPages = computed(() => categoryPageData.value?.totalPages || 0)
const listCardAds = computed(() => categoryPageData.value?.listCardAds || [])

watch(
  () => route.query.page,
  (page) => {
    const nextPage = parsePage(page)
    if (nextPage !== currentPage.value) {
      currentPage.value = nextPage
    }
  },
)

watch(
  () => slug.value,
  () => {
    if (currentPage.value !== 1) {
      currentPage.value = 1
    }
  },
)

useHead(() => ({
  title: categoryName.value ? `${categoryName.value} - 分类 - Weblog` : '分类 - Weblog',
  meta: [
    { name: 'description', content: categoryName.value ? `${categoryName.value}分类下的所有文章` : '分类文章列表' },
  ],
}))

type RenderItem =
  | { key: string; type: 'post'; post: PostVO }
  | { key: string; type: 'ad'; ad: AdvertisementVO }

const listCardAdPool = computed(() => listCardAds.value.filter(item => item.type === 'image' && Boolean(item.content)))

function resolveListCardAd(pageNo: number): AdvertisementVO | null {
  const pool = listCardAdPool.value
  if (!pool.length) return null
  if (pool.length === 1) return pool[0]
  return pool[(Math.max(1, pageNo) - 1) % pool.length]
}

const postRenderItems = computed<RenderItem[]>(() => {
  const postItems: RenderItem[] = posts.value.map(post => ({
    key: `post-${post.id}`,
    type: 'post',
    post,
  }))

  const ad = resolveListCardAd(currentPage.value)
  if (!ad) return postItems
  const insertAfter = Math.max(1, ad.insertAfter || 4)
  const insertIndex = Math.min(insertAfter, postItems.length)

  postItems.splice(insertIndex, 0, {
    key: `ad-${ad.id}-p${currentPage.value}`,
    type: 'ad',
    ad,
  })
  return postItems
})

async function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  await navigateTo({
    path: route.path,
    query: page > 1 ? { page: String(page) } : {},
  })
  if (import.meta.client) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

</script>

<style scoped lang="scss">
.category-posts-page { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }

.page-header { margin-bottom: var(--layout-page-header-margin-bottom); }
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: $color-primary;
  text-decoration: none;
  margin-bottom: 0.75rem;
  &:hover { text-decoration: underline; }
}
.page-title {
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}
.page-desc {
  font-size: 0.92rem;
  line-height: 1.4;
  color: $color-text-muted;
  margin-top: var(--layout-page-desc-margin-top);
}

.post-item {
  padding: 1rem 0;
  border-bottom: 1px solid $color-border;
  .dark & { border-bottom-color: $color-dark-border; }
}

.post-item--ad {
  padding: 0;
  border-bottom: none;
  margin: 0.8rem 0;
}

.post-link { text-decoration: none; color: inherit; display: block; cursor: pointer; }
.post-title { font-size: 1.05rem; font-weight: 600; margin-bottom: 0.375rem; color: $color-text; .dark & { color: $color-dark-text; } }
.post-summary { font-size: 0.875rem; color: $color-text-muted; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-bottom: 0.375rem; }
.post-meta { font-size: 0.8rem; color: $color-text-muted; display: flex; gap: 0.75rem; }

.pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1.5rem; }
.page-btn {
  width: 44px; height: 44px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid $color-border; border-radius: $radius-md;
  background: transparent; color: $color-text; cursor: pointer;
  transition: all 0.2s;
  &:hover:not(:disabled) { border-color: $color-primary; color: $color-primary; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  .dark & { border-color: $color-dark-border; color: $color-dark-text; }
}
.page-info { font-size: 0.85rem; color: $color-text-muted; }

.empty-state { text-align: center; padding: 3rem; color: #94a3b8; }
</style>
