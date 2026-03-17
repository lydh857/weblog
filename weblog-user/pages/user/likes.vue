<template>
  <div class="likes-page">
    <div class="page-header">
      <NuxtLink to="/user" class="back-link">
        <Icon name="heroicons:arrow-left-20-solid" size="18" /> 个人中心
      </NuxtLink>
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">
            <Icon name="heroicons:bookmark-20-solid" size="20" />
            我的收藏
          </h1>
          <span class="page-total">共 {{ total }} 篇</span>
          <template v-if="managing">
            <button class="toolbar-btn" @click="toggleSelectAll">{{ isAllSelected ? '取消全选' : '全选当前页' }}</button>
            <Transition name="fade">
              <button v-if="selectedIds.size > 0" class="toolbar-btn danger-btn" @click="showBatchConfirm = true">
                <Icon name="heroicons:bookmark-slash-16-solid" size="14" />
                取消收藏 ({{ selectedIds.size }})
              </button>
            </Transition>
          </template>
        </div>
        <button class="manage-btn" @click="managing = !managing">
          <template v-if="managing"><Icon name="heroicons:x-mark-16-solid" size="16" /> 取消</template>
          <template v-else><Icon name="heroicons:cog-6-tooth-16-solid" size="16" /> 管理</template>
        </button>
      </div>
    </div>

    <UnifiedPageLoader v-if="loading" text="加载中..." />

    <template v-else-if="posts.length">
      <article v-for="post in posts" :key="post.id" class="post-card" :class="{ selected: selectedIds.has(post.id), managing }" @click="managing && toggleSelect(post.id)">
        <NuxtLink :to="`/post/${post.slug}`" class="card-link" :class="{ disabled: managing }" @click="onCardLinkClick($event)">
          <div v-if="post.coverImage" class="card-cover"><img :src="post.coverImage" :alt="post.title" loading="lazy" /></div>
          <div class="card-body">
            <h3 class="card-title">{{ post.title }}</h3>
            <p v-if="post.summary" class="card-summary">{{ post.summary }}</p>
            <div class="card-meta">
              <span class="meta-item"><Icon name="heroicons:eye-16-solid" size="14" /> {{ post.viewCount }}</span>
              <span class="meta-item"><Icon name="heroicons:heart-16-solid" size="14" /> {{ post.likeCount }}</span>
              <span class="meta-item"><Icon name="heroicons:chat-bubble-left-16-solid" size="14" /> {{ post.commentCount }}</span>
              <span class="meta-sep">·</span>
              <span class="meta-time">收藏于 {{ formatRelativeTime(post.favoriteTime || post.createTime) }}</span>
            </div>
          </div>
        </NuxtLink>
        <button v-if="!managing" class="unfav-btn" title="取消收藏" @click.prevent.stop="pendingUnfavId = post.id; showUnfavConfirm = true">
          <Icon name="heroicons:trash-16-solid" size="14" />
        </button>
      </article>

      <Pagination :total="total" :current-page="currentPage" :page-size="pageSize" :page-size-options="[10, 20, 30]" @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
    </template>

    <div v-else class="empty-state">
      <Icon name="heroicons:bookmark-20-solid" size="48" />
      <p>还没有收藏过文章</p>
    </div>

    <ConfirmDialog :visible="showUnfavConfirm" message="确定取消收藏这篇文章吗？" @update:visible="showUnfavConfirm = $event" @confirm="confirmUnfavorite" />
    <ConfirmDialog :visible="showBatchConfirm" :message="`确定取消收藏选中的 ${selectedIds.size} 篇文章吗？`" @update:visible="showBatchConfirm = $event" @confirm="confirmBatchUnfavorite" />
  </div>
</template>

<script setup lang="ts">
import { interactionApi, type MyPostItem } from '~/api/interaction'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { formatRelativeTime } from '~/utils/format'
import Pagination from '~/components/Pagination.vue'
import ConfirmDialog from '~/components/ConfirmDialog.vue'

useHead({ title: '我的收藏' })

const userStore = useUserStore()
const posts = ref<MyPostItem[]>([])
const loading = ref(true)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedIds = ref<Set<number>>(new Set())
const managing = ref(false)

const showUnfavConfirm = ref(false)
const showBatchConfirm = ref(false)
const pendingUnfavId = ref(0)

const isAllSelected = computed(() => posts.value.length > 0 && posts.value.every(p => selectedIds.value.has(p.id)))

watch(managing, (val) => { if (!val) selectedIds.value = new Set() })

function onCardLinkClick(e: MouseEvent) { if (managing.value) e.preventDefault() }
function toggleSelect(id: number) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}
function toggleSelectAll() {
  if (isAllSelected.value) selectedIds.value = new Set()
  else selectedIds.value = new Set(posts.value.map(p => p.id))
}

async function confirmUnfavorite() {
  if (!pendingUnfavId.value) return
  try {
    await interactionApi.toggleFavorite(pendingUnfavId.value)
    selectedIds.value.delete(pendingUnfavId.value)
    await loadData(currentPage.value)
  } catch {}
  pendingUnfavId.value = 0
}

async function confirmBatchUnfavorite() {
  const ids = Array.from(selectedIds.value)
  if (!ids.length) return
  try {
    await interactionApi.batchUnfavorite(ids)
    selectedIds.value = new Set()
    await loadData(currentPage.value)
  } catch {}
}

function handlePageChange(page: number) { selectedIds.value = new Set(); loadData(page) }
function handleSizeChange(size: number) { pageSize.value = size; selectedIds.value = new Set(); loadData(1) }

async function loadData(page = 1) {
  if (!userStore.isLoggedIn) { useLoginModal().open(); navigateTo('/'); return }
  loading.value = true
  try {
    const res = await interactionApi.myFavorites(page, pageSize.value)
    posts.value = res.data.records
    currentPage.value = res.data.current
    total.value = res.data.total
  } catch {}
  finally { loading.value = false }
}

onMounted(() => loadData())
</script>
<style scoped lang="scss">
.likes-page { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
.page-header { margin-bottom: var(--layout-page-header-margin-bottom); }
.back-link { display: inline-flex; align-items: center; gap: .35rem; color: $color-text-muted; text-decoration: none; margin-bottom: .45rem; }
.header-row { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.header-left { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
.page-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  line-height: 1.2;
  display: flex;
  align-items: center;
  gap: .35rem;
  min-height: 2rem;
}

.page-total { font-size: .82rem; color: $color-text-muted; }

.toolbar-btn, .manage-btn {
  display: inline-flex; align-items: center; gap: .25rem;
  padding: .34rem .7rem; border: 1px solid $color-border; border-radius: 9px;
  background: transparent; color: $color-text-muted; cursor: pointer;
}
.danger-btn { border-color: #fca5a5; color: #dc2626; background: #fef2f2; }

.post-card { position: relative; display: flex; align-items: flex-start; margin-bottom: .75rem; border: 1px solid $color-border; border-radius: 12px; overflow: hidden; padding: .85rem; background: $color-bg; transition: border-color .2s, box-shadow .2s; }
.post-card.managing { cursor: pointer; }
.post-card.selected { border-color: $color-primary; background: rgba($color-primary, .04); }
.post-card:hover { border-color: rgba(59, 130, 246, .45); box-shadow: 0 8px 20px rgba(59,130,246,.08); }
.card-link { display: flex; flex: 1; gap: .75rem; text-decoration: none; color: inherit; min-width: 0; }
.card-link.disabled { pointer-events: none; }
.card-cover { width: 120px; height: 80px; border-radius: 10px; overflow: hidden; flex-shrink: 0; background: #e2e8f0; }
.card-cover img { width: 100%; height: 100%; object-fit: cover; }
.card-body { flex: 1; min-width: 0; }
.card-title { margin: 0 0 .35rem; font-size: 1rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-summary { margin: 0; font-size: .86rem; color: $color-text-muted; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.card-meta { display: flex; align-items: center; gap: .55rem; margin-top: .45rem; font-size: .78rem; color: $color-text-muted; flex-wrap: wrap; }
.meta-item { display: inline-flex; align-items: center; gap: .2rem; }
.unfav-btn {
  position: absolute;
  top: .375rem;
  right: .375rem;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: $radius-sm;
  background: rgba(0,0,0,.04);
  color: $color-text-muted;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity .2s, color .2s, background-color .2s;
}

.post-card:hover .unfav-btn {
  opacity: 1;
}

.unfav-btn:hover,
.unfav-btn:focus-visible {
  color: #dc2626;
  background: #fee2e2;
}

.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 0; color: $color-text-muted; gap: .5rem; border: 1px dashed rgba(148,163,184,.45); border-radius: 12px; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: $breakpoint-md) {
  .likes-page { padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
  .card-cover { width: 96px; height: 64px; }
}
</style>
