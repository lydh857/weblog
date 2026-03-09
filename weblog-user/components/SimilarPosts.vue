<template>
  <section v-if="posts.length > 0" class="similar-posts">
    <h3 class="section-title">相关文章推荐</h3>
    <div class="posts-grid">
      <NuxtLink
        v-for="p in posts"
        :key="p.id"
        :to="`/post/${p.slug}`"
        class="post-card"
      >
        <div v-if="p.coverImage" class="card-cover">
          <img :src="p.coverImage" :alt="p.title" loading="lazy" />
        </div>
        <div class="card-body">
          <h4 class="card-title">{{ p.title }}</h4>
          <p v-if="p.summary" class="card-summary">{{ p.summary }}</p>
        </div>
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { postApi, type PostVO } from '~/api/post'

const props = defineProps<{ postId: number; categoryId?: number | null }>()

const posts = ref<PostVO[]>([])

onMounted(async () => {
  try {
    const res = await postApi.getSimilarPosts(props.postId)
    posts.value = res.data || []
  } catch {
    // 静默失败
  }
})
</script>

<style scoped lang="scss">
.similar-posts {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.06);
  padding: 0.875rem 1rem;
  .dark & { background: $color-dark-bg-secondary; box-shadow: 0 1px 8px rgba(0,0,0,0.2); }
}

.section-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: $color-text;
  margin-bottom: 0.625rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f0f0f0;
  letter-spacing: 0.02em;
  .dark & { color: $color-dark-text; border-bottom-color: $color-dark-border; }
}

.posts-grid {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.post-card {
  display: flex;
  gap: 0.625rem;
  padding: 0.5rem 0.375rem;
  border-radius: 6px;
  text-decoration: none;
  color: inherit;
  transition: background 0.2s;
  cursor: pointer;
  &:hover {
    background: #f8fafc;
    .card-title { color: $color-primary; }
    .dark & { background: rgba(59,130,246,0.06); }
  }
}

.card-cover {
  width: 56px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #f1f5f9;
  .dark & { background: #334155; }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.3s;
    .post-card:hover & { transform: scale(1.05); }
  }
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.card-title {
  font-size: 0.775rem;
  font-weight: 500;
  color: #475569;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
  transition: color 0.2s;
  .dark & { color: #cbd5e1; }
}

.card-summary {
  display: none;
}
</style>
