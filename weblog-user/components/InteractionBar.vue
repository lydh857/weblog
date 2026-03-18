<template>
  <div class="interaction-bar">
    <button class="action-btn" :class="{ active: liked }" @click="handleLike" aria-label="点赞">
      <Icon :name="liked ? 'heroicons:heart-20-solid' : 'heroicons:heart-20-solid'" size="22"
        :class="{ 'like-active': liked }" />
      <span class="action-count">{{ likeCount }}</span>
      <span class="action-label">点赞</span>
    </button>
    <button class="action-btn" :class="{ active: favorited }" @click="handleFavorite" aria-label="收藏">
      <Icon :name="favorited ? 'heroicons:bookmark-20-solid' : 'heroicons:bookmark-20-solid'" size="22"
        :class="{ 'fav-active': favorited }" />
      <span class="action-label">{{ favorited ? '已收藏' : '收藏' }}</span>
    </button>
    <button class="action-btn" @click="$emit('scrollToComments')" aria-label="评论">
      <Icon name="heroicons:chat-bubble-left-20-solid" size="22" />
      <span class="action-count">{{ commentCount }}</span>
      <span class="action-label">评论</span>
    </button>
    <button class="action-btn" @click="handleShare" aria-label="分享">
      <Icon name="heroicons:share-20-solid" size="22" />
      <span class="action-label">分享</span>
    </button>

    <SharePoster
      :visible="showPoster"
      :title="postTitle"
      :summary="postSummary"
      :author="postAuthor"
      :url="shareUrl"
      @close="showPoster = false"
    />
  </div>
</template>

<script setup lang="ts">
import { interactionApi } from '~/api/interaction'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'

const props = defineProps<{
  postId: number
  likeCount: number
  commentCount: number
  postTitle?: string
  postSummary?: string | null
  postAuthor?: string
}>()

const emit = defineEmits<{
  scrollToComments: []
  likeCountUpdate: [count: number]
}>()

const liked = ref(false)
const favorited = ref(false)
const userStore = useUserStore()
const likeCount = ref(props.likeCount)
const showPoster = ref(false)
const shareUrl = computed(() => import.meta.client ? window.location.href : '')

const likeCommitQueue = useDebouncedStateCommit<boolean, { data: { liked: boolean; likeCount: number } }>({
  delayMs: 600,
  commitState: (key, state) => interactionApi.setLikeState(Number(key), state),
  onSuccess: (key, _state, res) => {
    if (Number(key) !== props.postId) {
      return
    }
    liked.value = res.data.liked
    likeCount.value = res.data.likeCount
    emit('likeCountUpdate', res.data.likeCount)
  },
  onError: (key) => {
    if (Number(key) !== props.postId) {
      return
    }
    void loadStatus()
  },
})

const favoriteCommitQueue = useDebouncedStateCommit<boolean, { data: { favorited: boolean; collectCount: number } }>({
  delayMs: 600,
  commitState: (key, state) => interactionApi.setFavoriteState(Number(key), state),
  onSuccess: (key, _state, res) => {
    if (Number(key) !== props.postId) {
      return
    }
    favorited.value = res.data.favorited
  },
  onError: (key) => {
    if (Number(key) !== props.postId) {
      return
    }
    void loadStatus()
  },
})

watch(() => props.likeCount, (v) => { likeCount.value = v })

async function loadStatus() {
  try {
    const res = await interactionApi.getStatus(props.postId)
    liked.value = res.data.liked
    favorited.value = res.data.favorited
    likeCount.value = res.data.likeCount
  } catch { /* ignore */ }
}

function handleShare() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  showPoster.value = true
}

function handleLike() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  const nextLiked = !liked.value
  liked.value = nextLiked
  likeCount.value = Math.max(0, likeCount.value + (nextLiked ? 1 : -1))
  emit('likeCountUpdate', likeCount.value)
  likeCommitQueue.scheduleState(props.postId, nextLiked)
}

function handleFavorite() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  const nextFavorited = !favorited.value
  favorited.value = nextFavorited
  favoriteCommitQueue.scheduleState(props.postId, nextFavorited)
}

onMounted(loadStatus)
watch(() => props.postId, (nextId, prevId) => {
  if (typeof prevId === 'number' && prevId > 0 && prevId !== nextId) {
    likeCommitQueue.flushKey(prevId)
    favoriteCommitQueue.flushKey(prevId)
  }
  void loadStatus()
})
</script>

<style scoped lang="scss">
.interaction-bar {
  display: flex;
  gap: 0.5rem;
  padding: 1rem 0;
  margin-top: 1.5rem;
  border-top: 1px solid $color-border;
  border-bottom: 1px solid $color-border;
  .dark & { border-color: $color-dark-border; }
}
.action-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.625rem 0.5rem;
  border: none;
  border-radius: $radius-md;
  background: transparent;
  color: $color-text-muted;
  font-size: 0.8rem;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
  min-height: 44px;
  &:hover { background: rgba(59, 130, 246, 0.06); color: $color-primary; }
  &:disabled { opacity: 0.6; }
  &.active { color: #ef4444; }
  .dark & { color: #64748b; &:hover { background: rgba(59, 130, 246, 0.1); color: $color-primary; } &.active { color: #ef4444; } }
}
.like-active { color: #ef4444; }
.fav-active { color: #f59e0b; }
.action-btn.active:nth-child(2) { color: #f59e0b; }
.action-count { font-size: 0.85rem; font-weight: 600; }
.action-label { font-size: 0.75rem; }
</style>
