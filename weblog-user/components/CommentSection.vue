<template>
  <div class="comment-section">
    <div class="section-header">
      <h3 class="section-title">
        <Icon name="heroicons:chat-bubble-left-ellipsis-20-solid" size="20" />
        评论区 ({{ totalCount }})
      </h3>
      <div class="sort-tabs">
        <button class="sort-tab" :class="{ active: sortMode === 'hot' }" @click="switchSort('hot')">最热</button>
        <button class="sort-tab" :class="{ active: sortMode === 'new' }" @click="switchSort('new')">最新</button>
      </div>
    </div>

    <!-- 主评论表单 -->
    <div ref="formRef" class="comment-form">
      <div class="form-avatar">
        <img v-if="showSelfAvatar" :src="userStore.userInfo.avatar" alt="头像" @error="handleSelfAvatarError" />
        <span v-else class="avatar-ph" :class="{ guest: !isLoggedIn }">
          <template v-if="isLoggedIn">{{ getAvatarText(userStore.userInfo.nickname) }}</template>
          <Icon v-else name="heroicons:user-circle-20-solid" size="40" />
        </span>
      </div>
      <div class="form-body">
        <div class="textarea-wrapper" :class="{ focused: mainFocused }">
          <textarea
            ref="mainTextareaRef"
            v-model="newComment"
            class="comment-input"
            :placeholder="replyTo ? `回复 @${replyTo.nickname}...` : '写下你的评论...'"
            rows="1"
            maxlength="500"
            @focus="handleFormFocus"
            @input="autoResize($event)"
          />
          <span class="word-count">{{ newComment.length }}/500</span>
        </div>
        <div class="comment-toolbar">
          <div class="toolbar-left">
            <button ref="mainEmojiBtnRef" type="button" class="emoji-toggle" @click.stop="openEmoji('main')" aria-label="表情">
              <Icon name="heroicons:face-smile-20-solid" size="20" />
            </button>
            <span v-if="replyTo" class="reply-hint">
              回复 @{{ replyTo.nickname }}
              <button class="cancel-reply" @click="cancelReply">×</button>
            </span>
          </div>
          <div class="toolbar-right">
            <button
              class="submit-btn"
              :class="{ submitting, success: submitSuccess }"
              :disabled="!newComment.trim() || submitting"
              @click="submitComment('main')"
            >{{ submitSuccess ? '发表成功' : submitting ? '提交中...' : '发表' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 评论列表 -->
    <div class="comment-list-wrapper">
      <div v-if="loadingComments && !comments.length" class="loading-state">
        <Icon name="heroicons:arrow-path-20-solid" size="20" class="spin" />
        <span>加载评论中...</span>
      </div>

      <div v-else-if="comments.length" class="comment-list" :class="{ refreshing: isRefreshing }">
        <TransitionGroup name="comment-fade" tag="div">
          <div v-for="comment in comments" :key="comment.id" class="comment-item" :class="{ topped: comment.isTop }">
            <div class="comment-main">
              <div class="comment-avatar">
                <img v-if="showCommentAvatar(comment)" :src="comment.avatar || ''" :alt="comment.nickname" @error="markAvatarLoadError(comment.id)" />
                <span v-else class="avatar-ph">{{ getAvatarText(comment.nickname) }}</span>
              </div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-nick">{{ comment.nickname }}</span>
                  <span v-if="comment.isTop" class="top-badge">置顶</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
                <div class="comment-actions">
                  <time class="action-time">{{ timeAgo(comment.createTime) }}</time>
                  <button class="action-sm" @click="handleCommentLike(comment)">
                    <Icon name="heroicons:heart-16-solid" size="14" :class="{ liked: comment.liked }" />
                    {{ comment.likeCount > 0 ? comment.likeCount : '' }}
                  </button>
                  <button class="action-sm reply-action" :class="{ active: inlineReplyId === comment.id }" @click="toggleInlineReply(comment)">
                    <Icon name="heroicons:chat-bubble-left-16-solid" size="14" />
                    回复
                  </button>
                  <button v-if="canDelete(comment)" class="action-sm delete" @click="requestDelete(comment.id)">删除</button>
                </div>

                <!-- 内联回复表单 -->
                <Transition name="reply-form">
                  <div v-if="inlineReplyId === comment.id" class="inline-reply-form">
                    <div class="form-avatar sm">
                      <img v-if="showSelfAvatar" :src="userStore.userInfo.avatar" alt="头像" @error="handleSelfAvatarError" />
                      <span v-else class="avatar-ph sm" :class="{ guest: !isLoggedIn }">
                        <template v-if="isLoggedIn">{{ getAvatarText(userStore.userInfo.nickname) }}</template>
                        <Icon v-else name="heroicons:user-circle-20-solid" size="32" />
                      </span>
                    </div>
                    <div class="inline-form-body">
                      <div class="textarea-wrapper mini" :class="{ focused: inlineFocused }">
                        <textarea
                          ref="inlineTextareaRef"
                          v-model="inlineComment"
                          class="comment-input"
                          placeholder="写下你的回复..."
                          rows="1"
                          maxlength="500"
                          @focus="inlineFocused = true"
                          @blur="inlineFocused = false"
                          @input="autoResize($event)"
                        />
                      </div>
                      <div class="inline-toolbar">
                        <button type="button" class="emoji-toggle sm" @click.stop="openEmoji('inline', $event)" aria-label="表情">
                          <Icon name="heroicons:face-smile-20-solid" size="16" />
                        </button>
                        <button
                          class="submit-btn sm"
                          :class="{ submitting }"
                          :disabled="!inlineComment.trim() || submitting"
                          @click="submitInlineReply(comment)"
                        >回复</button>
                      </div>
                    </div>
                  </div>
                </Transition>

                <!-- 子评论区域 -->
                <div v-if="getReplyData(comment).list.length" class="replies">
                  <div v-for="reply in getReplyData(comment).list" :key="reply.id" class="reply-item">
                    <div class="comment-avatar sm">
                      <img v-if="showCommentAvatar(reply)" :src="reply.avatar || ''" :alt="reply.nickname" @error="markAvatarLoadError(reply.id)" />
                      <span v-else class="avatar-ph sm">{{ getAvatarText(reply.nickname) }}</span>
                    </div>
                    <div class="comment-body">
                      <div class="comment-head-line">
                        <span class="comment-author-cell">
                          <span class="comment-nick">{{ reply.nickname }}</span>
                        </span>
                        <span class="comment-content-wrapper">
                          <span v-if="reply.replyToNickname" class="reply-to">
                            回复 <span class="reply-to-nick">@{{ reply.replyToNickname }}</span>：
                          </span>
                          <span class="comment-text-inline">{{ reply.content }}</span>
                        </span>
                      </div>
                      <div class="comment-actions">
                        <time class="action-time">{{ timeAgo(reply.createTime) }}</time>
                        <button class="action-sm" @click="handleCommentLike(reply)">
                          <Icon name="heroicons:heart-16-solid" size="14" :class="{ liked: reply.liked }" />
                          {{ reply.likeCount > 0 ? reply.likeCount : '' }}
                        </button>
                        <button class="action-sm reply-action" :class="{ active: inlineReplyId === reply.id }" @click="toggleInlineReply(comment, reply)">
                          <Icon name="heroicons:chat-bubble-left-16-solid" size="14" />
                          回复
                        </button>
                        <button v-if="canDelete(reply)" class="action-sm delete" @click="requestDelete(reply.id, comment.id)">删除</button>
                      </div>

                      <!-- 子评论内联回复 -->
                      <Transition name="reply-form">
                        <div v-if="inlineReplyId === reply.id" class="inline-reply-form">
                          <div class="form-avatar sm">
                            <img v-if="showSelfAvatar" :src="userStore.userInfo.avatar" alt="头像" @error="handleSelfAvatarError" />
                            <span v-else class="avatar-ph sm" :class="{ guest: !isLoggedIn }">
                              <template v-if="isLoggedIn">{{ getAvatarText(userStore.userInfo.nickname) }}</template>
                              <Icon v-else name="heroicons:user-circle-20-solid" size="32" />
                            </span>
                          </div>
                          <div class="inline-form-body">
                            <div class="textarea-wrapper mini" :class="{ focused: inlineFocused }">
                              <textarea
                                ref="inlineTextareaRef"
                                v-model="inlineComment"
                                class="comment-input"
                                :placeholder="`回复 @${inlineReplyNick}...`"
                                rows="1"
                                maxlength="500"
                                @focus="inlineFocused = true"
                                @blur="inlineFocused = false"
                                @input="autoResize($event)"
                              />
                            </div>
                            <div class="inline-toolbar">
                              <button type="button" class="emoji-toggle sm" @click.stop="openEmoji('inline', $event)" aria-label="表情">
                                <Icon name="heroicons:face-smile-20-solid" size="16" />
                              </button>
                              <button class="submit-btn sm" :disabled="!inlineComment.trim() || submitting" @click="submitInlineReply(comment, reply)">回复</button>
                            </div>
                          </div>
                        </div>
                      </Transition>
                    </div>
                  </div>

                  <!-- 展开/收起 + 分页 -->
                  <div class="reply-footer">
                    <template v-if="!expandedMap[comment.id]">
                      <button v-if="getReplyTotal(comment) > 3" class="view-more-btn" @click="expandReplies(comment)">
                        <span class="view-more-text">查看全部 {{ getReplyTotal(comment) }} 条回复</span>
                        <Icon name="heroicons:chevron-down-16-solid" size="14" />
                      </button>
                    </template>
                    <template v-else>
                      <div v-if="getReplyPageData(comment.id).pages > 1" class="reply-pagination">
                        <span class="reply-page-info">共{{ getReplyPageData(comment.id).pages }}页</span>
                        <button
                          class="reply-page-btn"
                          :disabled="getReplyPageData(comment.id).current <= 1"
                          @click="loadReplyPage(comment.id, getReplyPageData(comment.id).current - 1)"
                        >上一页</button>
                        <button
                          v-for="p in getReplyPageData(comment.id).pages"
                          :key="p"
                          class="reply-page-num"
                          :class="{ active: p === getReplyPageData(comment.id).current }"
                          @click="loadReplyPage(comment.id, p)"
                        >{{ p }}</button>
                        <button
                          class="reply-page-btn"
                          :disabled="getReplyPageData(comment.id).current >= getReplyPageData(comment.id).pages"
                          @click="loadReplyPage(comment.id, getReplyPageData(comment.id).current + 1)"
                        >下一页</button>
                      </div>
                      <button class="view-more-btn" @click="collapseReplies(comment)">
                        <span class="view-more-text">收起</span>
                        <Icon name="heroicons:chevron-up-16-solid" size="14" />
                      </button>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </TransitionGroup>

        <!-- 分页 -->
        <div v-if="totalPages > 1" class="comment-pagination">
          <button class="page-btn" :disabled="currentPage <= 1" @click="loadComments(currentPage - 1)">上一页</button>
          <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="loadComments(currentPage + 1)">下一页</button>
        </div>
      </div>

      <div v-else class="no-comments">
        <Icon name="heroicons:chat-bubble-left-ellipsis-20-solid" size="32" />
        <p>暂无评论，快来抢沙发吧~</p>
      </div>
    </div>

    <!-- 底部固定评论栏 -->
    <Transition name="bottom-bar-slide">
      <div v-if="showBottomBar" class="bottom-comment-bar" :style="bottomBarStyle">
        <div class="bottom-bar-inner">
          <div class="form-avatar sm">
            <img v-if="showSelfAvatar" :src="userStore.userInfo.avatar" alt="头像" @error="handleSelfAvatarError" />
            <span v-else class="avatar-ph sm" :class="{ guest: !isLoggedIn }">
              <template v-if="isLoggedIn">{{ getAvatarText(userStore.userInfo.nickname) }}</template>
              <Icon v-else name="heroicons:user-circle-20-solid" size="32" />
            </span>
          </div>
          <input
            v-model="newComment"
            class="bottom-input"
            :placeholder="replyTo ? `回复 @${replyTo.nickname}...` : '写下你的评论...'"
            maxlength="500"
            @focus="handleBottomFocus"
          />
          <button ref="bottomEmojiBtnRef" type="button" class="emoji-toggle sm" @click.stop="openEmoji('bottom')" aria-label="表情">
            <Icon name="heroicons:face-smile-20-solid" size="18" />
          </button>
          <button class="bottom-send" :disabled="!newComment.trim() || submitting" @click="submitComment('main')">
            <Icon name="heroicons:paper-airplane-20-solid" size="18" />
          </button>
        </div>
      </div>
    </Transition>

    <!-- 表情弹窗 -->
    <Teleport to="body">
      <div v-if="showEmojiPicker" class="emoji-overlay" @click="showEmojiPicker = false" />
      <Transition name="emoji-pop">
        <div v-if="showEmojiPicker" class="emoji-popup" :class="{ 'position-top': emojiOnTop }" :style="emojiPopupStyle">
          <div class="emoji-grid">
            <button v-for="emoji in currentEmojis" :key="emoji" class="emoji-btn" @click="insertEmoji(emoji)">{{ emoji }}</button>
          </div>
          <div class="emoji-tabs">
            <button v-for="cat in emojiCategories" :key="cat.name" class="emoji-tab" :class="{ active: activeEmojiTab === cat.name }" @click="activeEmojiTab = cat.name">{{ cat.label }}</button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <ConfirmDialog :visible="deleteConfirm.visible" message="确定删除这条评论？删除后不可恢复。" @update:visible="deleteConfirm.visible = $event" @confirm="confirmDelete" />
  </div>
</template>

<script setup lang="ts">
import { commentApi, type CommentVO } from '~/api/comment'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { useMessage } from '~/composables/useMessage'
import { useNavScrollLock } from '~/composables/useNavScrollLock'
import { formatRelativeTime } from '~/utils/format'

const props = defineProps<{ postId: number }>()
const userStore = useUserStore()
const message = useMessage()
const { lock: lockNavScroll } = useNavScrollLock()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const selfAvatarLoadFailed = ref(false)
const avatarLoadErrorMap = ref<Record<number, boolean>>({})
const showSelfAvatar = computed(() => isLoggedIn.value && !!userStore.userInfo.avatar && !selfAvatarLoadFailed.value)

type CommentItem = CommentVO

// 状态
const comments = ref<CommentItem[]>([])
const loadingComments = ref(true)
const isRefreshing = ref(false)
const currentPage = ref(1)
const totalPages = ref(0)
const totalCount = ref(0)
const newComment = ref('')
const submitting = ref(false)
const submitSuccess = ref(false)
const mainTextareaRef = ref<HTMLTextAreaElement | null>(null)
const inlineTextareaRef = ref<HTMLTextAreaElement | null>(null)
const formRef = ref<HTMLElement | null>(null)
const sectionRef = ref<HTMLElement | null>(null)
const mainEmojiBtnRef = ref<HTMLElement | null>(null)
const bottomEmojiBtnRef = ref<HTMLElement | null>(null)
const replyTo = ref<{ id: number; nickname: string; userId: number } | null>(null)
const expandedMap = ref<Record<number, boolean>>({})
const sortMode = ref<'new' | 'hot'>('hot')
const mainFocused = ref(false)
const inlineFocused = ref(false)

// 内联回复
const inlineReplyId = ref<number | null>(null)
const inlineComment = ref('')
const inlineReplyNick = ref('')
const inlineReplyParentId = ref(0)
const inlineReplyUserId = ref(0)

// 底部栏
const formInView = ref(true)
const sectionInView = ref(false)
const showBottomBar = computed(() => !formInView.value && sectionInView.value && comments.value.length > 0)
const bottomBarStyle = ref<Record<string, string>>({})

// 表情
const showEmojiPicker = ref(false)
const activeEmojiTab = ref('常用')
const emojiPopupStyle = ref<Record<string, string>>({})
const emojiOnTop = ref(false)
const emojiTarget = ref<'main' | 'inline' | 'bottom'>('main')

const emojiCategories = [
  { name: '常用', label: '😀', emojis: ['😀','😂','🤣','😍','🥰','😎','🤔','😅','😢','😊','🙏','💪','👍','👏','🎉','🔥','❤️','💯','✨','🤝','😏','🥺','😤','🤩','😴','🤗','😇','🤭','😋','🤤'] },
  { name: '表情', label: '😜', emojis: ['😜','😝','🤪','😛','🤑','🤫','🤥','😶','😐','😑','😬','🙄','😯','😦','😧','😮','😲','🥱','😵','🤯','🤠','🥳','🥴','😷','🤒','🤕','🤢','🤮','🤧','😈'] },
  { name: '手势', label: '👋', emojis: ['👋','🤚','🖐️','✋','🖖','👌','🤌','🤏','✌️','🤞','🤟','🤘','🤙','👈','👉','👆','🖕','👇','☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝'] },
  { name: '动物', label: '🐱', emojis: ['🐱','🐶','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🐔','🐧','🐦','🐤','🦄','🐝','🐛','🦋','🐌','🐞','🐜','🪲','🐢','🐍','🦎'] },
  { name: '食物', label: '🍕', emojis: ['🍕','🍔','🍟','🌭','🍿','🧂','🥓','🥚','🍳','🧇','🥞','🧈','🍞','🥐','🥨','🧀','🥗','🥙','🥪','🌮','🌯','🫔','🥘','🍝','🍜','🍲','🍛','🍣','🍱','🥟'] },
  { name: '符号', label: '💖', emojis: ['💖','💝','💘','💗','💓','💞','💕','❣️','💔','🩷','🧡','💛','💚','💙','🩵','💜','🤎','🖤','🩶','🤍','💢','💥','💫','💦','💨','🕳️','💣','💬','👁️‍🗨️','🗯️'] },
]
const currentEmojis = computed(() => emojiCategories.find(c => c.name === activeEmojiTab.value)?.emojis || [])

// 自动调整 textarea 高度
function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

// 表情弹窗 - 智能定位
function openEmoji(target: 'main' | 'inline' | 'bottom', e?: MouseEvent) {
  if (!isLoggedIn.value) { useLoginModal().open(); return }
  if (showEmojiPicker.value && emojiTarget.value === target) { showEmojiPicker.value = false; return }
  emojiTarget.value = target
  let btn: HTMLElement | null = null
  if (target === 'main') btn = mainEmojiBtnRef.value
  else if (target === 'inline') btn = e?.currentTarget as HTMLElement ?? null
  else btn = bottomEmojiBtnRef.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const popupH = 300
  const popupW = 360
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  // 判断上/下弹出
  if (spaceBelow < popupH + 16 && spaceAbove > popupH + 16) {
    emojiOnTop.value = true
    emojiPopupStyle.value = {
      position: 'fixed',
      left: `${Math.max(8, Math.min(rect.left, window.innerWidth - popupW - 8))}px`,
      bottom: `${window.innerHeight - rect.top + 8}px`,
      zIndex: '5000',
    }
  } else {
    emojiOnTop.value = false
    emojiPopupStyle.value = {
      position: 'fixed',
      left: `${Math.max(8, Math.min(rect.left, window.innerWidth - popupW - 8))}px`,
      top: `${rect.bottom + 8}px`,
      zIndex: '5000',
    }
  }
  showEmojiPicker.value = true
}

function insertEmoji(emoji: string) {
  if (emojiTarget.value === 'inline') inlineComment.value += emoji
  else newComment.value += emoji
}

function timeAgo(dateStr: string) { return formatRelativeTime(dateStr) }
function canDelete(comment: CommentVO) { return userStore.userInfo.userId === comment.userId }

function getAvatarText(name: string | null | undefined) {
  const text = (name || '').trim()
  return text ? text.charAt(0).toUpperCase() : 'U'
}

function handleSelfAvatarError() {
  selfAvatarLoadFailed.value = true
}

function markAvatarLoadError(commentId: number) {
  if (avatarLoadErrorMap.value[commentId]) {
    return
  }
  avatarLoadErrorMap.value = {
    ...avatarLoadErrorMap.value,
    [commentId]: true,
  }
}

function showCommentAvatar(comment: CommentVO) {
  return !!comment.avatar && !avatarLoadErrorMap.value[comment.id]
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'message' in error) {
    const messageText = String((error as { message?: unknown }).message || '').trim()
    if (messageText) {
      return messageText
    }
  }
  return fallback
}

function showSubmitError(error: unknown, fallback: string) {
  const messageText = getErrorMessage(error, fallback)
  message.error(messageText)
}

watch(() => userStore.userInfo.avatar, () => {
  selfAvatarLoadFailed.value = false
})

watch(() => props.postId, (nextId, prevId) => {
  if (typeof prevId === 'number' && prevId > 0 && prevId !== nextId) {
    loadComments(1)
  }
})

function handleFormFocus() {
  mainFocused.value = true
  if (!isLoggedIn.value) { mainTextareaRef.value?.blur(); useLoginModal().open() }
}

function handleBottomFocus(e: FocusEvent) {
  if (!isLoggedIn.value) { (e.target as HTMLInputElement)?.blur(); useLoginModal().open() }
}

// 内联回复切换
function toggleInlineReply(parent: CommentItem, reply?: CommentItem) {
  if (!isLoggedIn.value) { useLoginModal().open(); return }
  const targetId = reply ? reply.id : parent.id
  if (inlineReplyId.value === targetId) { inlineReplyId.value = null; return }
  inlineReplyId.value = targetId
  inlineReplyNick.value = reply ? reply.nickname : parent.nickname
  inlineReplyParentId.value = parent.id
  inlineReplyUserId.value = reply ? reply.userId : parent.userId
  inlineComment.value = ''
  nextTick(() => {
    const el = inlineTextareaRef.value
    if (Array.isArray(el)) (el as HTMLTextAreaElement[])[0]?.focus()
    else el?.focus()
  })
}

function cancelReply() { replyTo.value = null }

// 提交主评论
async function submitComment(_source: 'main' | 'bottom') {
  if (!isLoggedIn.value) { useLoginModal().open(); return }
  if (!newComment.value.trim()) return
  submitting.value = true
  try {
    const res = await commentApi.create({
      postId: props.postId,
      parentId: replyTo.value?.id || null,
      replyToUserId: replyTo.value?.userId || null,
      content: newComment.value.trim(),
    })
    newComment.value = ''
    submitSuccess.value = true
    setTimeout(() => { submitSuccess.value = false }, 1500)
    if (res.data?.status === 'pending') {
      message.info('评论已提交，等待审核')
    } else {
      message.success('评论发表成功')
    }
    replyTo.value = null
    await refreshComments()
  } catch (error) {
    showSubmitError(error, '评论提交失败，请稍后重试')
  }
  finally { submitting.value = false }
}

// 提交内联回复（局部刷新，不重载整个评论列表）
async function submitInlineReply(parent: CommentItem, reply?: CommentItem) {
  if (!isLoggedIn.value) { useLoginModal().open(); return }
  if (!inlineComment.value.trim()) return
  submitting.value = true
  try {
    const res = await commentApi.create({
      postId: props.postId,
      parentId: parent.id,
      replyToUserId: reply ? reply.userId : null,
      content: inlineComment.value.trim(),
    })
    inlineComment.value = ''
    inlineReplyId.value = null
    if (res.data?.status === 'pending') {
      message.info('回复已提交，等待审核')
    } else {
      message.success('回复成功')
      totalCount.value++
    }
    // 局部刷新：只重新加载该父评论的子评论
    lockNavScroll()
    expandedMap.value[parent.id] = true
    const pageInfo = replyPageMap.value[parent.id]
    await loadReplyPage(parent.id, pageInfo ? pageInfo.current : 1)
    // 同步更新父评论的 replyTotal
    const parentComment = comments.value.find(c => c.id === parent.id)
    if (parentComment && replyPageMap.value[parent.id]) {
      parentComment.replyTotal = replyPageMap.value[parent.id].total
    }
  } catch (error) {
    showSubmitError(error, '回复提交失败，请稍后重试')
  }
  finally { submitting.value = false }
}

function applyCommentLikeState(commentId: number, liked: boolean, likeCount: number) {
  const safeCount = Math.max(0, likeCount)

  for (const item of comments.value) {
    if (item.id === commentId) {
      item.liked = liked
      item.likeCount = safeCount
    }
    if (item.replies?.length) {
      for (const reply of item.replies) {
        if (reply.id === commentId) {
          reply.liked = liked
          reply.likeCount = safeCount
        }
      }
    }
  }

  for (const pageInfo of Object.values(replyPageMap.value)) {
    for (const reply of pageInfo.list) {
      if (reply.id === commentId) {
        reply.liked = liked
        reply.likeCount = safeCount
      }
    }
  }
}

const commentLikeCommitQueue = useDebouncedStateCommit<boolean, { data: { liked: boolean; likeCount: number } }>({
  delayMs: 600,
  commitState: (key, state) => commentApi.setLikeState(Number(key), state),
  onSuccess: (key, _state, res) => {
    applyCommentLikeState(Number(key), res.data.liked, res.data.likeCount)
  },
  onError: (_key, _state, error) => {
    const messageText = getErrorMessage(error, '点赞操作失败，请稍后重试')
    message.warning(messageText)
    void refreshComments()
  },
})

function handleCommentLike(comment: CommentItem) {
  if (!isLoggedIn.value) { useLoginModal().open(); return }
  const nextLiked = !Boolean(comment.liked)
  const nextLikeCount = (comment.likeCount || 0) + (nextLiked ? 1 : -1)
  applyCommentLikeState(comment.id, nextLiked, nextLikeCount)
  commentLikeCommitQueue.scheduleState(comment.id, nextLiked)
}

// 删除
const deleteConfirm = reactive({ visible: false, commentId: 0, parentId: 0 })
function requestDelete(commentId: number, parentId = 0) {
  deleteConfirm.commentId = commentId; deleteConfirm.parentId = parentId; deleteConfirm.visible = true
}
async function confirmDelete() {
  const { commentId, parentId } = deleteConfirm
  try {
    await commentApi.delete(commentId)
    if (parentId > 0) {
      const parent = comments.value.find(c => c.id === parentId)
      if (parent?.replies) { parent.replies = parent.replies.filter(r => r.id !== commentId); if (parent.replyTotal) parent.replyTotal-- }
      const pageInfo = replyPageMap.value[parentId]
      if (pageInfo) { pageInfo.list = pageInfo.list.filter(r => r.id !== commentId); pageInfo.total-- }
    } else { comments.value = comments.value.filter(c => c.id !== commentId) }
    totalCount.value = Math.max(0, totalCount.value - 1)
  } catch { /* 静默 */ }
}

// 排序
function switchSort(mode: 'new' | 'hot') {
  if (sortMode.value === mode) return
  sortMode.value = mode
  lockNavScroll()
  refreshComments()
}

// 刷新评论（不闪烁，保留展开状态）
async function refreshComments() {
  isRefreshing.value = true
  // 保存当前展开状态
  const savedExpanded = { ...expandedMap.value }
  try {
    const res = await commentApi.listByPost(props.postId, currentPage.value, 10, sortMode.value)
    comments.value = res.data.records
    avatarLoadErrorMap.value = {}
    currentPage.value = res.data.current
    totalPages.value = res.data.pages
    totalCount.value = res.data.total ?? 0
    // 恢复展开状态，并重新加载已展开的子评论分页
    expandedMap.value = {}
    replyPageMap.value = {}
    for (const [idStr, expanded] of Object.entries(savedExpanded)) {
      const id = Number(idStr)
      if (expanded && comments.value.some(c => c.id === id)) {
        expandedMap.value[id] = true
        loadReplyPage(id, 1)
      }
    }
  } catch { /* 静默 */ }
  finally { isRefreshing.value = false }
}

// 子评论分页
interface ReplyPageInfo { list: CommentItem[]; current: number; pages: number; total: number }
const replyPageMap = ref<Record<number, ReplyPageInfo>>({})
function getReplyTotal(comment: CommentItem): number { return comment.replyTotal ?? comment.replies?.length ?? 0 }
function getReplyData(comment: CommentItem): { list: CommentItem[] } {
  if (expandedMap.value[comment.id] && replyPageMap.value[comment.id]) return { list: replyPageMap.value[comment.id].list }
  return { list: ((comment.replies || []) as CommentItem[]).slice(0, 3) }
}
function getReplyPageData(parentId: number): { current: number; pages: number } {
  const info = replyPageMap.value[parentId]; return info ? { current: info.current, pages: info.pages } : { current: 1, pages: 1 }
}
async function expandReplies(comment: CommentItem) { lockNavScroll(); expandedMap.value[comment.id] = true; await loadReplyPage(comment.id, 1) }
function collapseReplies(comment: CommentItem) { lockNavScroll(); expandedMap.value[comment.id] = false }
async function loadReplyPage(parentId: number, page: number) {
  lockNavScroll()
  try {
    const res = await commentApi.listReplies(parentId, page, 10)
    replyPageMap.value[parentId] = { list: res.data.records as CommentItem[], current: res.data.current, pages: res.data.pages, total: res.data.total ?? 0 }
  } catch { /* 静默 */ }
}

// 首次加载
async function loadComments(page = 1) {
  lockNavScroll()
  loadingComments.value = true
  try {
    const res = await commentApi.listByPost(props.postId, page, 10, sortMode.value)
    comments.value = res.data.records; currentPage.value = res.data.current; totalPages.value = res.data.pages; totalCount.value = res.data.total ?? 0
    avatarLoadErrorMap.value = {}
    expandedMap.value = {}; replyPageMap.value = {}
  } catch { /* 静默 */ }
  finally { loadingComments.value = false }
}

// 底部栏位置
function updateBottomBarPosition() {
  const el = sectionRef.value; if (!el) return
  const rect = el.getBoundingClientRect()
  bottomBarStyle.value = { position: 'fixed', left: `${rect.left}px`, width: `${rect.width}px`, bottom: '0', zIndex: '200' }
}

let formObserver: IntersectionObserver | null = null
let sectionObserver: IntersectionObserver | null = null
let rafId = 0
function onScrollUpdate() { if (showBottomBar.value) rafId = requestAnimationFrame(() => updateBottomBarPosition()) }

onMounted(() => {
  loadComments()
  nextTick(() => {
    const rootEl = document.querySelector('.comment-section') as HTMLElement
    if (rootEl) sectionRef.value = rootEl
    if (formRef.value) {
      formObserver = new IntersectionObserver(([entry]) => { formInView.value = entry.isIntersecting }, { threshold: 0.1 })
      formObserver.observe(formRef.value)
    }
    if (sectionRef.value) {
      sectionObserver = new IntersectionObserver(([entry]) => { sectionInView.value = entry.isIntersecting; if (entry.isIntersecting) updateBottomBarPosition() }, { threshold: 0 })
      sectionObserver.observe(sectionRef.value)
    }
    window.addEventListener('scroll', onScrollUpdate, { passive: true })
    window.addEventListener('resize', onScrollUpdate, { passive: true })
  })
})
onUnmounted(() => {
  formObserver?.disconnect(); sectionObserver?.disconnect()
  window.removeEventListener('scroll', onScrollUpdate); window.removeEventListener('resize', onScrollUpdate)
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<style scoped lang="scss">
.comment-section { margin-top: 2rem; }

/* 标题 + 排序 */
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.25rem; }
.section-title {
  display: flex; align-items: center; gap: 0.5rem; font-size: 1.1rem; font-weight: 600; color: $color-text; margin: 0;
  .dark & { color: $color-dark-text; }
}
.sort-tabs { display: flex; gap: 0.25rem; }
.sort-tab {
  padding: 0.25rem 0.75rem; border: 1px solid $color-border; border-radius: 999px;
  background: transparent; color: $color-text-muted; font-size: 0.8rem; cursor: pointer; transition: all 0.2s;
  &:hover { border-color: $color-primary; color: $color-primary; }
  &.active { background: $color-primary; color: #fff; border-color: $color-primary; }
  .dark & { border-color: $color-dark-border; color: $color-dark-text-muted; &.active { background: $color-primary; color: #fff; border-color: $color-primary; } }
}

@media (max-width: $breakpoint-md) {
  .section-header {
    margin-bottom: 0.9rem;
  }

  .section-title {
    gap: 0.35rem;
    font-size: 0.98rem;
  }

  .sort-tabs {
    gap: 0.18rem;
    padding: 0.12rem;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.12);
    border: 1px solid rgba(148, 163, 184, 0.26);

    .dark & {
      background: rgba(30, 41, 59, 0.74);
      border-color: rgba(100, 116, 139, 0.42);
    }
  }

  .sort-tab {
    min-height: 32px;
    min-width: 52px;
    padding: 0.2rem 0.64rem;
    line-height: 1;
    font-size: 0.76rem;
    border-color: transparent;
  }

  .sort-tab.active {
    box-shadow: 0 2px 6px rgba(37, 99, 235, 0.24);
  }
}

/* 评论表单 */
.comment-form { display: flex; gap: 0.75rem; margin-bottom: 1.5rem; }
.form-avatar {
  flex-shrink: 0;
  img { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
  &.sm img { width: 32px; height: 32px; }
}
.form-avatar .avatar-ph {
  display: flex; align-items: center; justify-content: center; width: 40px; height: 40px;
  border-radius: 50%; background: $color-primary; color: #fff; font-size: 0.9rem; font-weight: 600;
  &.guest { background: transparent; color: #94a3b8; }
  .dark & { background: $color-primary; &.guest { background: transparent; color: $color-dark-text-muted; } }
  &.sm { width: 32px; height: 32px; font-size: 0.8rem; }
}
.form-body, .inline-form-body { flex: 1; min-width: 0; }
.textarea-wrapper {
  position: relative; border: 1px solid $color-border; border-radius: $radius-md; background: #fff;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  &::after { content: ''; position: absolute; bottom: 0; left: 0; width: 100%; height: 2px; background: $color-primary; transform: scaleX(0); transition: transform 0.3s; }
  &.focused { border-color: rgba(59,130,246,0.5); box-shadow: 0 2px 10px rgba(30,136,229,0.12); &::after { transform: scaleX(1); } }
  &.mini { border-radius: $radius-sm; }
  .dark & { background: $color-dark-bg; border-color: $color-dark-border; }
}
.comment-input {
  width: 100%; padding: 0.75rem; border: none; border-radius: $radius-md; font-size: 0.9rem; font-family: inherit;
  resize: none; min-height: 40px; max-height: 120px; background: transparent; color: $color-text; outline: none; line-height: 1.5;
  .dark & { color: $color-dark-text; }
}
.word-count { position: absolute; right: 0.75rem; bottom: 4px; font-size: 0.75rem; color: $color-text-muted; background: rgba(255,255,255,0.8); padding: 0 4px; border-radius: 4px; }
.comment-toolbar { display: flex; align-items: center; justify-content: space-between; margin-top: 0.5rem; }
.toolbar-left { display: flex; align-items: center; gap: 0.5rem; }
.toolbar-right { display: flex; align-items: center; gap: 0.75rem; }

/* 表情按钮 */
.emoji-toggle {
  background: rgba(240,240,240,0.7); border: none; cursor: pointer; padding: 8px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; color: $color-text-muted;
  transition: all 0.3s cubic-bezier(0.25,0.8,0.25,1); position: relative; overflow: hidden;
  &::before { content: ''; position: absolute; inset: 0; background: rgba(59,130,246,0.08); border-radius: 50%; transform: scale(0); transition: transform 0.4s; }
  &:hover { background: rgba(230,240,255,0.7); color: $color-primary; &::before { transform: scale(2); } }
  &:active { transform: scale(0.92); }
  &.sm { padding: 5px; border-radius: 6px; }
  .dark & { background: rgba(42,49,58,0.56); color: $color-dark-text-muted; &:hover { background: rgba(59,130,246,0.15); color: $color-primary; } }
}

/* 发布按钮 - 参照案例 */
.submit-btn {
  padding: 8px 18px; border: none; border-radius: 8px; background-color: #1976d2;
  color: #fff; font-size: 0.95rem; font-weight: 500; cursor: pointer; min-height: 36px;
  min-width: 90px; display: inline-flex; align-items: center; justify-content: center;
  transition: all 0.3s cubic-bezier(0.25,0.8,0.25,1); box-shadow: 0 2px 10px rgba(30,136,229,0.3);
  position: relative; overflow: hidden; letter-spacing: 0.2px;
  &::before {
    content: ''; position: absolute; top: 0; left: 0; width: 100%; height: 100%;
    background: linear-gradient(to right, rgba(255,255,255,0), rgba(255,255,255,0.2), rgba(255,255,255,0));
    transform: translateX(-100%); transition: transform 0.7s ease; z-index: 1; will-change: transform;
  }
  &:hover:not(:disabled) {
    background-color: #1976d2; box-shadow: 0 4px 12px rgba(25,118,210,0.4); transform: translateY(-2px);
    &::before { transform: translateX(100%); }
  }
  &:active:not(:disabled) { transform: translateY(0); background-color: #1565c0; box-shadow: 0 2px 5px rgba(21,101,192,0.3); }
  &:disabled { background-color: #90caf9; cursor: not-allowed; box-shadow: none; opacity: 0.7; &::before { display: none; } }
  &.submitting { background-color: #1976d2; &::before { display: none; } }
  &.success {
    background-color: #43a047; box-shadow: 0 2px 10px rgba(67,160,71,0.3);
    &::after {
      content: ''; position: absolute; left: 50%; top: 50%; width: 0; height: 0;
      background-color: rgba(255,255,255,0.2); border-radius: 50%; transform: translate(-50%,-50%);
      animation: successPulse 0.6s ease-out; will-change: width, height, opacity;
    }
    &::before {
      content: ''; position: absolute; left: 0; top: 0; width: 100%; height: 100%;
      background: linear-gradient(45deg, rgba(255,255,255,0.1), rgba(255,255,255,0.2), rgba(255,255,255,0.1));
      animation: successShine 1s ease-out; z-index: 1; will-change: transform;
    }
  }
  &.sm { padding: 4px 12px; font-size: 0.8rem; min-height: 30px; min-width: 60px; border-radius: 6px; }
}
@keyframes successPulse {
  0% { width: 0; height: 0; opacity: 0.5; }
  100% { width: 150%; height: 150%; opacity: 0; }
}
@keyframes successShine {
  0% { transform: translateX(-100%) rotate(45deg); }
  100% { transform: translateX(100%) rotate(45deg); }
}

.reply-hint {
  font-size: 0.8rem; color: $color-primary; display: flex; align-items: center; gap: 0.25rem;
  .cancel-reply { border: none; background: none; color: $color-text-muted; cursor: pointer; font-size: 1rem; padding: 0 0.25rem; }
}

/* 刷新不闪烁 */
.comment-list-wrapper { position: relative; min-height: 100px; }
.comment-list.refreshing { opacity: 0.6; pointer-events: none; transition: opacity 0.15s; }

/* 评论列表过渡 */
.comment-fade-leave-active { transition: opacity 0.25s, transform 0.25s; }
.comment-fade-leave-to { opacity: 0; transform: translateX(-20px); }

/* 评论列表 */
.comment-item {
  padding: 1rem 0; border-bottom: 1px solid $color-border;
  &:last-child { border-bottom: none; }
  &.topped { background: rgba(59,130,246,0.03); border-radius: $radius-md; padding: 1rem; margin-bottom: 0.5rem; border-bottom: none; }
  .dark & { border-bottom-color: $color-dark-border; }
}
.comment-main { display: flex; gap: 0.75rem; }
.comment-avatar {
  flex-shrink: 0;
  img { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
  &.sm img { width: 28px; height: 28px; }
}
.avatar-ph {
  display: flex; align-items: center; justify-content: center; width: 36px; height: 36px;
  border-radius: 50%; background: $color-primary; color: #fff; font-size: 0.85rem; font-weight: 600;
  &.sm { width: 28px; height: 28px; font-size: 0.75rem; }
}
.comment-body { flex: 1; min-width: 0; }
.comment-header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem; flex-wrap: wrap; }
.comment-nick { font-size: 0.85rem; font-weight: 600; color: $color-text; .dark & { color: $color-dark-text; } }
.reply-to-inline { font-size: 0.8rem; color: $color-text-muted; .reply-to-nick { color: $color-primary; font-weight: 500; } }
/* 子评论 table 布局：昵称 + @用户：内容 同一行 */
.comment-head-line {
  display: table; width: 100%; margin-bottom: 0.25rem;
}
.comment-author-cell {
  display: table-cell; white-space: nowrap; padding-right: 0.5rem; vertical-align: top; width: 1px;
  .comment-nick { font-size: 0.85rem; font-weight: 600; }
}
.comment-content-wrapper {
  display: table-cell; vertical-align: top;
  .reply-to {
    font-size: 0.85rem; color: #888; white-space: nowrap; margin-right: 4px;
    .reply-to-nick { color: $color-primary; font-weight: 500; }
  }
  .comment-text-inline {
    font-size: 0.85rem; line-height: 1.5; color: $color-text; word-break: break-word;
    .dark & { color: $color-dark-text; }
  }
}
.top-badge { font-size: 0.65rem; padding: 0.0625rem 0.375rem; border-radius: 999px; background: #eff6ff; color: #1d4ed8; .dark & { background: rgba(59,130,246,0.2); color: #93c5fd; } }
.comment-text { font-size: 0.9rem; line-height: 1.6; color: $color-text; word-break: break-word; .dark & { color: $color-dark-text; } }
.comment-actions { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.375rem; }
.action-time { font-size: 0.75rem; color: $color-text-muted; margin-right: 0.25rem; .dark & { color: $color-dark-text-muted; } }
.action-sm {
  display: flex; align-items: center; gap: 0.25rem; border: none; background: none;
  font-size: 0.75rem; color: $color-text-muted; cursor: pointer; padding: 0.25rem 0; transition: color 0.2s;
  &:hover { color: $color-primary; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  &:disabled:hover { color: $color-text-muted; }
  &.delete:hover { color: #ef4444; }
  &.reply-action.active { color: $color-primary; font-weight: 500; }
  .liked { color: #ef4444; }
  .dark & { color: $color-dark-text-muted; }
}

/* 内联回复表单 */
.inline-reply-form {
  display: flex; gap: 0.5rem; margin-top: 0.75rem; padding: 0.75rem; background: #f5f7fa; border-radius: 8px;
  .dark & { background: rgba(30,41,59,0.5); }
}
.inline-toolbar { display: flex; align-items: center; justify-content: space-between; margin-top: 0.375rem; }
.reply-form-enter-active { transition: all 0.25s ease-out; }
.reply-form-leave-active { transition: all 0.2s ease-in; }
.reply-form-enter-from { opacity: 0; max-height: 0; transform: translateY(-8px); }
.reply-form-leave-to { opacity: 0; max-height: 0; transform: translateY(-8px); }
.reply-form-enter-to, .reply-form-leave-from { opacity: 1; max-height: 200px; }

/* 子评论 */
.replies { margin-top: 0.75rem; padding-left: 0.5rem; border-left: 2px solid $color-border; .dark & { border-left-color: $color-dark-border; } }
.reply-item { display: flex; gap: 0.5rem; padding: 0.5rem 0; }
.reply-footer { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; padding: 0.5rem 0; }
.view-more-btn {
  display: flex; align-items: center; gap: 4px; border: none; background: none; color: $color-text-muted;
  font-size: 0.85rem; cursor: pointer; padding: 4px 0; transition: all 0.2s; user-select: none;
  .view-more-text { font-weight: 400; }
  &:hover { color: $color-primary; }
  .dark & { color: $color-dark-text-muted; &:hover { color: $color-primary; } }
}
.reply-pagination { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; font-size: 0.85rem; color: $color-text-muted; }
.reply-page-info { margin-right: 2px; }
.reply-page-btn {
  padding: 0 4px; border: none; background: none; color: $color-text-muted; font-size: 0.85rem;
  cursor: pointer; transition: all 0.2s;
  &:hover:not(:disabled) { color: $color-primary; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  .dark & { color: $color-dark-text-muted; }
}
.reply-page-num {
  padding: 0 5px; margin: 0 1px; border: none; background: none; color: $color-text-muted;
  font-size: 0.85rem; cursor: pointer; transition: all 0.2s;
  &:hover:not(.active) { color: $color-primary; }
  &.active { color: $color-primary; font-weight: 500; }
  .dark & { color: $color-dark-text-muted; &.active { color: $color-primary; } }
}

/* 一级分页 */
.comment-pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; }
.page-btn {
  padding: 0.375rem 0.75rem; border: 1px solid $color-border; border-radius: $radius-md;
  background: transparent; color: $color-text; font-size: 0.8rem; cursor: pointer; min-height: 44px;
  &:hover:not(:disabled) { border-color: $color-primary; color: $color-primary; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  .dark & { border-color: $color-dark-border; color: $color-dark-text; }
}
.page-info { font-size: 0.85rem; color: $color-text-muted; }

.no-comments { text-align: center; padding: 2.5rem; color: $color-text-muted; font-size: 0.9rem; p { margin-top: 0.5rem; } }
.loading-state { display: flex; align-items: center; justify-content: center; gap: 0.5rem; padding: 2rem; color: $color-text-muted; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 底部固定评论栏 */
.bottom-comment-bar {
  background: #fff; border-top: 1px solid $color-border; border-radius: 12px 12px 0 0;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06); padding: 0.5rem 0.75rem;
  .dark & { background: $color-dark-bg-secondary; border-top-color: $color-dark-border; box-shadow: 0 -2px 12px rgba(0,0,0,0.2); }
}
.bottom-bar-inner { display: flex; align-items: center; gap: 0.5rem; }
.bottom-input {
  flex: 1; padding: 0.5rem 0.75rem; border: 1px solid $color-border; border-radius: 999px;
  font-size: 0.85rem; font-family: inherit; background: #f8fafc; color: $color-text; outline: none; min-height: 36px;
  &:focus { border-color: $color-primary; background: #fff; }
  .dark & { background: $color-dark-bg; border-color: $color-dark-border; color: $color-dark-text; &:focus { background: $color-dark-bg-secondary; } }
}
.bottom-send {
  width: 36px; height: 36px; border-radius: 50%; border: none; background: $color-primary; color: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: all 0.2s;
  &:hover { background: $color-primary-dark; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.bottom-bar-slide-enter-active { transition: transform 0.25s ease-out, opacity 0.25s; }
.bottom-bar-slide-leave-active { transition: transform 0.2s ease-in, opacity 0.2s; }
.bottom-bar-slide-enter-from, .bottom-bar-slide-leave-to { transform: translateY(100%); opacity: 0; }

/* 表情弹窗 */
.emoji-overlay { position: fixed; inset: 0; z-index: 4999; background: transparent; }
.emoji-popup {
  width: 360px; max-width: calc(100vw - 16px); background: #fff; border: 1px solid $color-border; border-radius: 10px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15); overflow: hidden; display: flex; flex-direction: column;
  animation: emojiPopIn 0.25s ease;
  &.position-top { animation: emojiPopDown 0.25s ease; }
  .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; box-shadow: 0 8px 32px rgba(0,0,0,0.3); }
}
@keyframes emojiPopIn { from { opacity: 0; transform: translateY(10px) scale(0.95); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes emojiPopDown { from { opacity: 0; transform: translateY(-10px) scale(0.95); } to { opacity: 1; transform: translateY(0) scale(1); } }
.emoji-grid {
  display: flex; flex-wrap: wrap; padding: 0.5rem; max-height: 220px; overflow-y: auto;
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: #d1d1d1; border-radius: 4px; }
}
.emoji-btn {
  width: 40px; height: 40px; border: none; background: none; font-size: 1.4rem; cursor: pointer;
  border-radius: 6px; display: flex; align-items: center; justify-content: center; transition: all 0.15s;
  &:hover { background: #f0f2f5; transform: scale(1.15); }
  &:active { transform: scale(0.95); }
}
.emoji-tabs {
  display: flex; border-top: 1px solid $color-border; background: #f9f9f9; flex-shrink: 0;
  .dark & { border-top-color: $color-dark-border; background: $color-dark-bg; }
}
.emoji-tab {
  flex: 1; border: none; background: none; padding: 0.5rem; font-size: 1rem; cursor: pointer; transition: all 0.2s;
  &:hover:not(.active) { background: #f0f0f0; }
  &.active { color: $color-primary; background: rgba(59,130,246,0.1); }
}
.emoji-pop-enter-active { transition: opacity 0.15s, transform 0.15s; }
.emoji-pop-leave-active { transition: opacity 0.1s, transform 0.1s; }
.emoji-pop-enter-from, .emoji-pop-leave-to { opacity: 0; }
</style>
