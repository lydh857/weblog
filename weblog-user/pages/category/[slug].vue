<template>
  <div class="category-posts-page">
    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
      <span>加载中...</span>
    </div>

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
          <article v-for="post in posts" :key="post.id" class="post-item">
            <NuxtLink :to="`/post/${post.slug}`" class="post-link">
              <h3 class="post-title">{{ post.title }}</h3>
              <p v-if="post.summary" class="post-summary">{{ post.summary }}</p>
              <div class="post-meta">
                <time>{{ formatDate(post.createTime) }}</time>
                <span>{{ post.viewCount }} 阅读</span>
              </div>
            </NuxtLink>
          </article>

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

const route = useRoute()
const slug = route.params.slug as string

const categoryName = ref('')
const categoryDesc = ref('')
const posts = ref<PostVO[]>([])
const loading = ref(true)
const currentPage = ref(1)
const totalPages = ref(0)

async function loadPosts() {
  try {
    const res = await postApi.list({ pageNum: currentPage.value, pageSize: 10, categorySlug: slug })
    posts.value = res.data.records
    totalPages.value = res.data.pages
  } catch { /* ignore */ }
}

function changePage(page: number) {
  currentPage.value = page
  loadPosts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  try {
    const catRes = await categoryApi.getBySlug(slug)
    categoryName.value = catRes.data.name
    categoryDesc.value = catRes.data.description || ''

    useHead({
      title: `${categoryName.value} - 分类 - Weblog`,
      meta: [
        { name: 'description', content: `${categoryName.value}分类下的所有文章` },
      ],
    })

    await loadPosts()
  } catch { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.category-posts-page { max-width: 800px; margin: 0 auto; padding: 2rem 1.5rem; }

.page-header { margin-bottom: 1.5rem; }
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
.page-title { font-size: 1.5rem; font-weight: 700; color: $color-text; .dark & { color: $color-dark-text; } }
.page-desc { font-size: 0.9rem; color: $color-text-muted; margin-top: 0.375rem; }

.post-item {
  padding: 1rem 0;
  border-bottom: 1px solid $color-border;
  .dark & { border-bottom-color: $color-dark-border; }
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

.loading-state { display: flex; align-items: center; justify-content: center; gap: 0.5rem; padding: 3rem; color: $color-text-muted; }
.empty-state { text-align: center; padding: 3rem; color: #94a3b8; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
