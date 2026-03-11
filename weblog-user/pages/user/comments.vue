<template>
  <div class="comments-page">
    <div class="page-header">
      <NuxtLink to="/user" class="back-link">
        <Icon name="heroicons:arrow-left-20-solid" size="18" /> 个人中心
      </NuxtLink>
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">我的评论</h1>
          <template v-if="managing">
            <button class="toolbar-btn" @click="toggleSelectAll">{{ isAllSelected ? '取消全选' : '全选当前页' }}</button>
            <Transition name="fade">
              <button v-if="selectedIds.size > 0" class="toolbar-btn danger-btn" @click="showBatchConfirm = true">
                <Icon name="heroicons:trash-16-solid" size="14" />
                删除 ({{ selectedIds.size }})
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

    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
    </div>

    <template v-else-if="comments.length">
      <div v-for="comment in comments" :key="comment.id" class="comment-card" :class="{ selected: selectedIds.has(comment.id), managing }" @click="managing && toggleSelect(comment.id)">
        <div class="row-top">
          <NuxtLink v-if="comment.postSlug" :to="`/post/${comment.postSlug}#comment-${comment.id}`" class="post-link" @click.stop>
            {{ comment.postTitle || '未知文章' }}
          </NuxtLink>
          <span v-else class="post-link disabled">{{ comment.postTitle || '文章已删除' }}</span>
          <span class="meta-likes"><Icon name="heroicons:heart-16-solid" size="12" /> {{ comment.likeCount }}</span>
          <span v-if="comment.status === 'pending'" class="status-badge">待审核</span>
          <time class="meta-time">{{ formatRelativeTime(comment.createTime) }}</time>
        </div>

        <div class="comment-text">
          <span v-if="comment.parentId && comment.parentId > 0 && comment.replyToNickname" class="reply-prefix">回复 @{{ comment.replyToNickname }}：</span>{{ comment.content }}
        </div>

        <button v-if="!managing" class="delete-btn" title="删除" @click.stop="pendingDeleteId = comment.id; showDeleteConfirm = true">
          <Icon name="heroicons:trash-16-solid" size="14" />
        </button>
      </div>

      <Pagination :total="total" :current-page="currentPage" :page-size="pageSize" :page-size-options="[10,20,30]" @update:current-page="handlePageChange" @update:page-size="handleSizeChange" />
    </template>

    <div v-else class="empty-state">
      <Icon name="heroicons:chat-bubble-left-ellipsis-20-solid" size="48" />
      <p>还没有发表过评论</p>
    </div>

    <ConfirmDialog :visible="showDeleteConfirm" message="确定删除这条评论吗？删除后不可恢复。" @update:visible="showDeleteConfirm = $event" @confirm="confirmDelete" />
    <ConfirmDialog :visible="showBatchConfirm" :message="`确定删除选中的 ${selectedIds.size} 条评论吗？`" @update:visible="showBatchConfirm = $event" @confirm="confirmBatchDelete" />
  </div>
</template>

<script setup lang="ts">
import { commentApi, type CommentVO } from '~/api/comment'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { formatRelativeTime } from '~/utils/format'
import Pagination from '~/components/Pagination.vue'
import ConfirmDialog from '~/components/ConfirmDialog.vue'

useHead({ title: '我的评论' })

const userStore = useUserStore()
const comments = ref<CommentVO[]>([])
const loading = ref(true)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedIds = ref<Set<number>>(new Set())
const managing = ref(false)

const showDeleteConfirm = ref(false)
const showBatchConfirm = ref(false)
const pendingDeleteId = ref(0)

const isAllSelected = computed(() => comments.value.length > 0 && comments.value.every(c => selectedIds.value.has(c.id)))

watch(managing, (val) => { if (!val) selectedIds.value = new Set() })

function toggleSelect(id: number) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}
function toggleSelectAll() {
  if (isAllSelected.value) selectedIds.value = new Set()
  else selectedIds.value = new Set(comments.value.map(c => c.id))
}

async function confirmDelete() {
  if (!pendingDeleteId.value) return
  try {
    await commentApi.delete(pendingDeleteId.value)
    selectedIds.value.delete(pendingDeleteId.value)
    await loadData(currentPage.value)
  } catch {}
  pendingDeleteId.value = 0
}

async function confirmBatchDelete() {
  const ids = Array.from(selectedIds.value)
  if (!ids.length) return
  try {
    await commentApi.batchDelete(ids)
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
    const res = await commentApi.myComments(page, pageSize.value)
    comments.value = res.data.records
    currentPage.value = res.data.current
    total.value = res.data.total
  } catch {}
  finally { loading.value = false }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.comments-page { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
.page-header { margin-bottom: var(--layout-page-header-margin-bottom); }
.back-link { display: inline-flex; align-items: center; gap: .375rem; color: $color-text-muted; text-decoration: none; margin-bottom: .5rem; }
.header-row { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.header-left { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
.page-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
}
.toolbar-btn, .manage-btn { display: inline-flex; align-items: center; gap: .25rem; padding: .25rem .625rem; border: 1px solid $color-border; border-radius: $radius-md; background: transparent; color: $color-text-muted; cursor: pointer; }
.danger-btn { border-color: #fca5a5; color: #dc2626; background: #fef2f2; }
.comment-card { position: relative; padding: .65rem .85rem; margin-bottom: .4rem; border: 2px solid $color-border; border-radius: $radius-lg; }
.comment-card.managing { cursor: pointer; }
.comment-card.selected { border-color: $color-primary; background: rgba($color-primary,.03); }
.row-top { display: flex; align-items: center; gap: .5rem; margin-bottom: .25rem; min-height: 20px; padding-right: 1.5rem; }
.post-link { font-size: .8rem; color: $color-primary; text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
.post-link.disabled { color: $color-text-muted; }
.meta-likes { display: inline-flex; align-items: center; gap: .15rem; font-size: .7rem; color: $color-text-muted; }
.meta-time { font-size: .7rem; color: $color-text-muted; white-space: nowrap; }
.status-badge { padding: 0 .3rem; border-radius: 999px; font-size: .6rem; line-height: 1.6; background: #fef3c7; color: #92400e; }
.comment-text { font-size: .85rem; line-height: 1.5; word-break: break-word; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.reply-prefix { color: #94a3b8; font-size: .8rem; }
.delete-btn { position: absolute; top: .375rem; right: .375rem; width: 24px; height: 24px; border: none; border-radius: $radius-sm; background: rgba(0,0,0,.04); color: $color-text-muted; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; opacity: 0; }
.comment-card:hover .delete-btn { opacity: 1; }
.empty-state { text-align: center; padding: 4rem 1rem; color: #94a3b8; }
.loading-state { display: flex; justify-content: center; padding: 3rem; color: $color-text-muted; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
