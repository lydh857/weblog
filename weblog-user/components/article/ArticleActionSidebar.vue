<template>
  <div class="action-sidebar">
    <!-- 点赞 -->
    <button
      class="sidebar-btn"
      :class="{ active: liked, 'animate-pop': likeAnimating }"
      title="点赞"
      @click="handleLike"
      aria-label="点赞"
    >
      <Icon name="heroicons:heart-20-solid" size="20" :class="{ 'liked-icon': liked }" />
      <span v-if="displayLikeCount > 0" class="btn-count">{{ formatCount(displayLikeCount) }}</span>
    </button>
    <!-- 收藏 -->
    <button
      class="sidebar-btn"
      :class="{ active: favorited, 'animate-pop': favAnimating }"
      title="收藏"
      @click="handleFavorite"
      aria-label="收藏"
    >
      <Icon name="heroicons:bookmark-20-solid" size="20" :class="{ 'fav-icon': favorited }" />
      <span v-if="displayCollectCount > 0" class="btn-count">{{ formatCount(displayCollectCount) }}</span>
    </button>
    <!-- 评论 -->
    <button class="sidebar-btn" title="前往评论区" @click="$emit('scrollToComments')" aria-label="前往评论区">
      <Icon name="heroicons:chat-bubble-left-20-solid" size="20" />
      <span v-if="commentCount > 0" class="btn-count">{{ formatCount(commentCount) }}</span>
    </button>
    <!-- 分享 -->
    <button class="sidebar-btn" title="分享文章" @click="handleShare" aria-label="分享文章">
      <Icon name="heroicons:share-20-solid" size="20" />
    </button>
    <!-- 分隔线 -->
    <div class="sidebar-divider" />
    <!-- 上一篇 -->
    <button
      class="sidebar-btn nav-btn"
      title="上一篇"
      :class="{ disabled: !prevSlug }"
      @click="navigatePrev"
      aria-label="上一篇"
    >
      <Icon name="heroicons:chevron-up-20-solid" size="20" />
    </button>
    <!-- 下一篇 -->
    <button
      class="sidebar-btn nav-btn"
      title="下一篇"
      :class="{ disabled: !nextSlug }"
      @click="navigateNext"
      aria-label="下一篇"
    >
      <Icon name="heroicons:chevron-down-20-solid" size="20" />
    </button>

    <!-- 分享海报弹窗 -->
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
  prevSlug: string | null
  nextSlug: string | null
  likeCount: number
  collectCount: number
  commentCount: number
  postTitle: string
  postSummary?: string | null
  postAuthor?: string
}>()

const emit = defineEmits<{
  scrollToComments: []
  likeToggled: [liked: boolean, likeCount: number]
}>()

const router = useRouter()
const userStore = useUserStore()
const liked = ref(false)
const favorited = ref(false)
const likeAnimating = ref(false)
const favAnimating = ref(false)
const showPoster = ref(false)

// 显示数量：以接口返回为准，初始值来自文章详情
const displayLikeCount = ref(props.likeCount)
const displayCollectCount = ref(props.collectCount)

const likeCommitQueue = useDebouncedStateCommit<boolean, { data: { liked: boolean; likeCount: number } }>({
  delayMs: 600,
  commitState: (key, state) => interactionApi.setLikeState(Number(key), state),
  onSuccess: (key, _state, res) => {
    if (Number(key) !== props.postId) {
      return
    }
    liked.value = res.data.liked
    displayLikeCount.value = res.data.likeCount
    emit('likeToggled', res.data.liked, res.data.likeCount)
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
    displayCollectCount.value = res.data.collectCount
  },
  onError: (key) => {
    if (Number(key) !== props.postId) {
      return
    }
    void loadStatus()
  },
})

const shareUrl = computed(() => import.meta.client ? window.location.href : '')

// 外部 props 变化时同步（如父组件更新）
watch(() => props.likeCount, (v) => { displayLikeCount.value = v })
watch(() => props.collectCount, (v) => { displayCollectCount.value = v })

function formatCount(n: number): string {
  if (n >= 100000) return (n / 10000).toFixed(0) + 'w'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

async function loadStatus() {
  try {
    const res = await interactionApi.getStatus(props.postId)
    liked.value = res.data.liked
    favorited.value = res.data.favorited
    displayLikeCount.value = res.data.likeCount
    displayCollectCount.value = res.data.collectCount
  } catch { /* 静默 */ }
}

function handleLike() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  triggerPop('like')
  const nextLiked = !liked.value
  liked.value = nextLiked
  displayLikeCount.value = Math.max(0, displayLikeCount.value + (nextLiked ? 1 : -1))
  emit('likeToggled', nextLiked, displayLikeCount.value)
  likeCommitQueue.scheduleState(props.postId, nextLiked)
}

function handleFavorite() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  triggerPop('fav')
  const nextFavorited = !favorited.value
  favorited.value = nextFavorited
  displayCollectCount.value = Math.max(0, displayCollectCount.value + (nextFavorited ? 1 : -1))
  favoriteCommitQueue.scheduleState(props.postId, nextFavorited)
}

function triggerPop(type: 'like' | 'fav') {
  const ref = type === 'like' ? likeAnimating : favAnimating
  ref.value = true
  setTimeout(() => { ref.value = false }, 400)
}

function handleShare() {
  if (!userStore.isLoggedIn) { useLoginModal().open(); return }
  showPoster.value = true
}

function navigatePrev() {
  if (props.prevSlug) router.push(`/post/${props.prevSlug}`)
}

function navigateNext() {
  if (props.nextSlug) router.push(`/post/${props.nextSlug}`)
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
.action-sidebar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.375rem;
}
.sidebar-divider {
  width: 24px;
  height: 1px;
  background: #e2e8f0;
  margin: 2px 0;
  .dark & { background: $color-dark-border; }
}
.sidebar-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 44px;
  min-height: 44px;
  padding: 5px 0;
  border-radius: 12px;
  border: none;
  background: #fff;
  color: #8a919f;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
  cursor: pointer;
  transition: color 0.2s, transform 0.2s, box-shadow 0.2s;
  gap: 1px;
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }
  &:hover:not(:disabled) {
    color: $color-primary;
    transform: scale(1.06);
    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
  }
  &.active { color: #ef4444; }
  &.animate-pop { animation: btnPop 0.4s ease; }
  &.nav-btn {
    border-radius: 50%;
    min-height: 36px;
    width: 36px;
    padding: 0;
    box-shadow: 0 1px 4px rgba(0,0,0,0.05);
    &.disabled {
      opacity: 0.3;
      cursor: not-allowed;
      &:hover { transform: none; color: #8a919f; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
    }
  }
  .dark & {
    background: $color-dark-bg-secondary; color: $color-dark-text-muted;
    box-shadow: 0 1px 6px rgba(0,0,0,0.15);
    &:hover:not(:disabled) { color: $color-primary; }
    &.active { color: #ef4444; }
    &.nav-btn.disabled { &:hover { color: #64748b; } }
  }
}
.btn-count {
  font-size: 0.6rem;
  line-height: 1;
  font-weight: 500;
  color: inherit;
}
.liked-icon { color: #ef4444; }
.fav-icon { color: #f59e0b; }
.sidebar-btn.active:nth-child(2) { color: #f59e0b; }

@keyframes btnPop {
  0% { transform: scale(1); }
  30% { transform: scale(1.2); }
  60% { transform: scale(0.95); }
  100% { transform: scale(1); }
}
</style>
