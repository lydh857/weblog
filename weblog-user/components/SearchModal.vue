<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="visible"
        class="search-modal-overlay"
        @click.self="close"
        @keydown="handleKeydown"
      >
        <div class="search-modal" role="dialog" aria-modal="true" aria-label="搜索">
          <!-- 顶部搜索输入框 -->
          <div class="search-header">
            <div class="search-input-wrapper">
              <input
                ref="searchInputRef"
                v-model="keyword"
                type="text"
                class="search-input"
                :placeholder="inputPlaceholderText"
                autocomplete="off"
                @input="handleInput"
              />
              <button
                type="button"
                class="clear-btn touch-target"
                :class="{ 'clear-btn--visible': hasKeyword }"
                :aria-hidden="!hasKeyword"
                :tabindex="hasKeyword ? 0 : -1"
                aria-label="清空搜索"
                @click="clearKeyword"
              >
                <Icon name="heroicons:x-circle-20-solid" size="18" />
              </button>
              <button
                type="button"
                class="search-submit-btn touch-target"
                aria-label="搜索"
                @click="handleSearchSubmit"
              >
                <Icon name="heroicons:magnifying-glass-20-solid" size="18" />
              </button>
            </div>
            <button class="modal-close-btn touch-target" aria-label="关闭搜索" @click="close">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </div>

          <!-- 搜索内容区域 -->
          <div
            class="search-body"
            :class="{
              'search-body--ranking': !hasKeyword,
              'search-body--results': hasKeyword,
            }"
            @click="handleSearchBodyClick"
          >
            <!-- 无关键词：搜索历史 + 排行榜 -->
            <template v-if="!hasKeyword">
              <!-- 搜索历史 -->
              <div v-if="history.length > 0" class="history-section">
                <div class="section-header">
                  <span class="section-label">
                    <Icon name="heroicons:clock-16-solid" size="14" />
                    搜索历史
                  </span>
                  <button class="clear-all-btn" @click="clearAllHistory">清空</button>
                </div>
                <div class="history-tags">
                  <span
                    v-for="(item, index) in history"
                    :key="index"
                    class="history-tag"
                    :class="{ 'history-tag--removable': historyLongPressIndex === index }"
                    @click="handleHistoryTagClick(item, index, $event)"
                    @touchstart.passive="handleHistoryTouchStart($event, index)"
                    @touchmove.passive="handleHistoryTouchMove"
                    @touchend="handleHistoryTouchEnd"
                    @touchcancel="handleHistoryTouchCancel"
                  >
                    {{ item }}
                    <button
                      class="tag-remove"
                      aria-label="删除"
                      @click.stop="removeHistory(index)"
                    >
                      <Icon name="heroicons:x-mark-16-solid" size="12" />
                    </button>
                  </span>
                </div>
              </div>

              <!-- 排行榜 -->
              <div class="ranking-section">
                <div class="section-header">
                  <span class="section-label">
                    <Icon name="heroicons:chart-bar-16-solid" size="14" />
                    热门排行
                  </span>
                </div>
                <RankingList :max-items="10" />
              </div>
            </template>

            <!-- 有关键词：搜索结果 -->
            <template v-else>
              <!-- 加载中 -->
              <div v-if="searching" class="search-loading">
                <div v-for="i in 4" :key="i" class="skeleton-result">
                  <div class="skeleton-cover" />
                  <div class="skeleton-info">
                    <div class="skeleton-title" />
                    <div class="skeleton-summary" />
                    <div class="skeleton-time" />
                  </div>
                </div>
              </div>

              <!-- 搜索结果列表 -->
              <div v-else-if="results.length > 0" class="search-results">
                <a
                  v-for="(item, index) in results"
                  :key="item.id"
                  :href="`/post/${item.slug}`"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="result-item"
                  :class="{ active: activeIndex === index }"
                  @mouseenter="activeIndex = index"
                  @click="handleResultClick(item)"
                >
                  <div class="result-content">
                    <h4 class="result-title" v-html="sanitizeHtml(item.highlightTitle || item.title)" />
                    <p
                      v-if="item.highlightContent || item.summary"
                      class="result-summary"
                      v-html="sanitizeHtml(item.highlightContent || item.summary)"
                    />
                    <div class="result-meta">
                      <span v-if="formatCategoryPath(item)" class="meta-item">
                        <Icon name="heroicons:folder-16-solid" size="12" />
                        <span>{{ formatCategoryPath(item) }}</span>
                      </span>
                      <span v-if="item.createTime" class="meta-item">
                        <Icon name="heroicons:calendar-days-16-solid" size="12" />
                        <span>{{ formatDate(item.createTime) }}</span>
                      </span>
                    </div>
                    <div v-if="hasStats(item)" class="result-stats">
                      <span v-if="item.viewCount != null" class="stat-item">
                        <Icon name="heroicons:eye-16-solid" size="12" />
                        <span>{{ formatCount(item.viewCount) }}</span>
                      </span>
                      <span v-if="item.likeCount != null" class="stat-item">
                        <Icon name="heroicons:heart-16-solid" size="12" />
                        <span>{{ formatCount(item.likeCount) }}</span>
                      </span>
                      <span v-if="item.collectCount != null" class="stat-item">
                        <Icon name="heroicons:bookmark-16-solid" size="12" />
                        <span>{{ formatCount(item.collectCount) }}</span>
                      </span>
                      <span v-if="item.commentCount != null" class="stat-item">
                        <Icon name="heroicons:chat-bubble-left-16-solid" size="12" />
                        <span>{{ formatCount(item.commentCount) }}</span>
                      </span>
                    </div>
                  </div>
                </a>
              </div>

              <!-- 无结果 -->
              <div v-else-if="shouldShowEmpty" class="search-empty">
                <Icon name="heroicons:magnifying-glass-16-solid" size="40" />
                <p>未找到相关文章</p>
                <span>换个关键词试试</span>
              </div>

              <!-- 已输入，等待手动搜索 -->
              <div v-else class="search-pending">
                <Icon name="heroicons:magnifying-glass-16-solid" size="34" />
                <p>点击右侧搜索图标或按回车开始搜索</p>
              </div>
            </template>
          </div>

          <!-- 底部快捷键提示 -->
          <div v-if="showShortcutHints && !isCoarsePointer" class="search-footer">
            <div class="shortcut-hints">
              <span class="hint">
                <kbd>↑</kbd><kbd>↓</kbd> 选择
              </span>
              <span class="hint">
                <kbd>↩</kbd> 确认
              </span>
              <span class="hint">
                <kbd>Esc</kbd> 关闭
              </span>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { searchApi, type SearchHit } from '~/api/search'
import { sanitizeHtml } from '~/utils/xss'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'
import { useSearchModal } from '~/composables/useSearchModal'

// ===== Props / Emits =====
interface Props {
  visible: boolean
}
interface Emits {
  (e: 'update:visible', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const searchModal = useSearchModal()

// ===== 状态 =====
const searchInputRef = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const results = ref<SearchHit[]>([])
const searching = ref(false)
const activeIndex = ref(-1)
const hasKeyword = computed(() => keyword.value.trim().length > 0)
const showShortcutHints = computed(() => hasKeyword.value && !searching.value && results.value.length > 0)
const hasSearched = ref(false)
const shouldShowEmpty = computed(() => hasKeyword.value && hasSearched.value && !searching.value && results.value.length === 0)
const DEFAULT_SEARCH_PLACEHOLDER = '搜索文章...'
const inputPlaceholderText = ref(DEFAULT_SEARCH_PLACEHOLDER)

// ===== 搜索历史（localStorage 持久化） =====
const HISTORY_KEY = 'weblog_search_history'
const MAX_HISTORY = 10
const LONG_PRESS_DURATION = 420
const LONG_PRESS_MOVE_TOLERANCE = 10

const history = ref<string[]>([])
const historyLongPressIndex = ref<number | null>(null)
const suppressHistoryClick = ref(false)
const isCoarsePointer = ref(false)
let historyLongPressTimer: ReturnType<typeof setTimeout> | null = null
let longPressStartX = 0
let longPressStartY = 0

/** 从 localStorage 读取搜索历史 */
function loadHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (raw) history.value = JSON.parse(raw)
  } catch {
    history.value = []
  }
}

/** 保存搜索历史到 localStorage */
function saveHistory() {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
  } catch {
    // localStorage 不可用时静默降级
  }
}

/** 添加搜索关键词到历史 */
function addToHistory(kw: string) {
  const trimmed = kw.trim()
  if (!trimmed) return
  // 去重：移除已存在的相同关键词
  history.value = history.value.filter(item => item !== trimmed)
  // 添加到头部
  history.value.unshift(trimmed)
  // 限制最多 10 条
  if (history.value.length > MAX_HISTORY) {
    history.value = history.value.slice(0, MAX_HISTORY)
  }
  saveHistory()
}

/** 删除单条历史 */
function removeHistory(index: number) {
  history.value.splice(index, 1)
  if (historyLongPressIndex.value === index) {
    historyLongPressIndex.value = null
  } else if (historyLongPressIndex.value != null && historyLongPressIndex.value > index) {
    historyLongPressIndex.value -= 1
  }
  suppressHistoryClick.value = false
  saveHistory()
}

/** 清空全部历史 */
function clearAllHistory() {
  history.value = []
  historyLongPressIndex.value = null
  suppressHistoryClick.value = false
  saveHistory()
}

/** 从历史记录搜索 */
function searchFromHistory(kw: string) {
  keyword.value = kw
  historyLongPressIndex.value = null
  suppressHistoryClick.value = false
  doSearch()
}

function clearHistoryLongPressTimer() {
  if (historyLongPressTimer) {
    clearTimeout(historyLongPressTimer)
    historyLongPressTimer = null
  }
}

function handleHistoryTagClick(kw: string, index: number, event: MouseEvent) {
  if (suppressHistoryClick.value) {
    suppressHistoryClick.value = false
    event.preventDefault()
    return
  }

  if (historyLongPressIndex.value != null && historyLongPressIndex.value !== index) {
    historyLongPressIndex.value = null
  }

  searchFromHistory(kw)
}

function handleHistoryTouchStart(event: TouchEvent, index: number) {
  if (!isCoarsePointer.value) {
    return
  }

  const touch = event.touches[0]
  if (!touch) {
    return
  }

  clearHistoryLongPressTimer()
  suppressHistoryClick.value = false
  longPressStartX = touch.clientX
  longPressStartY = touch.clientY

  historyLongPressTimer = setTimeout(() => {
    historyLongPressIndex.value = index
    suppressHistoryClick.value = true
    historyLongPressTimer = null
  }, LONG_PRESS_DURATION)
}

function handleHistoryTouchMove(event: TouchEvent) {
  if (!historyLongPressTimer) {
    return
  }

  const touch = event.touches[0]
  if (!touch) {
    return
  }

  const deltaX = Math.abs(touch.clientX - longPressStartX)
  const deltaY = Math.abs(touch.clientY - longPressStartY)
  if (deltaX > LONG_PRESS_MOVE_TOLERANCE || deltaY > LONG_PRESS_MOVE_TOLERANCE) {
    clearHistoryLongPressTimer()
  }
}

function handleHistoryTouchEnd() {
  clearHistoryLongPressTimer()
}

function handleHistoryTouchCancel() {
  clearHistoryLongPressTimer()
}

function handleSearchBodyClick(event: MouseEvent) {
  if (historyLongPressIndex.value == null) {
    return
  }

  const target = event.target as HTMLElement | null
  if (target?.closest('.history-tag')) {
    return
  }

  historyLongPressIndex.value = null
  suppressHistoryClick.value = false
}

// ===== 搜索逻辑 =====
/** 输入事件处理（取消自动搜索） */
function handleInput() {
  activeIndex.value = -1
  hasSearched.value = false
  if (!keyword.value.trim()) {
    results.value = []
  }
}

function handleSearchSubmit() {
  if (!keyword.value.trim()) {
    const placeholderKeyword = inputPlaceholderText.value.trim()
    if (!placeholderKeyword || placeholderKeyword === DEFAULT_SEARCH_PLACEHOLDER) {
      return
    }
    keyword.value = placeholderKeyword
  }
  void doSearch()
}

/** 执行搜索 */
async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    hasSearched.value = false
    results.value = []
    return
  }

  hasSearched.value = true
  searching.value = true
  try {
    const res = await searchApi.search({ keyword: kw, pageSize: 20 })
    results.value = res.data.hits
    activeIndex.value = results.value.length > 0 ? 0 : -1
  } catch {
    results.value = []
  } finally {
    searching.value = false
  }
}

/** 清空关键词 */
function clearKeyword() {
  keyword.value = ''
  hasSearched.value = false
  results.value = []
  activeIndex.value = -1
  searchInputRef.value?.focus()
}

// ===== 键盘操作 =====
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    close()
    return
  }

  if (e.key === 'Enter') {
    e.preventDefault()
    handleSearchSubmit()
    return
  }

  // 仅在有搜索结果时处理上下键
  if (!hasKeyword.value || results.value.length === 0) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % results.value.length
    scrollActiveIntoView()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = activeIndex.value <= 0
      ? results.value.length - 1
      : activeIndex.value - 1
    scrollActiveIntoView()
  }
}

/** 滚动选中项到可视区域 */
function scrollActiveIntoView() {
  nextTick(() => {
    const activeEl = document.querySelector('.result-item.active')
    activeEl?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

function formatCount(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}w`
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}k`
  }
  return String(value)
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) {
    return dateStr
  }

  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function hasStats(item: SearchHit): boolean {
  return item.viewCount != null
    || item.likeCount != null
    || item.collectCount != null
    || item.commentCount != null
}

function formatCategoryPath(item: SearchHit): string {
  if (item.categoryName && item.subCategoryName) {
    return `${item.categoryName} / ${item.subCategoryName}`
  }
  if (item.subCategoryName) {
    return item.subCategoryName
  }
  if (item.categoryName) {
    return item.categoryName
  }
  if (item.categoryId != null) {
    return `分类 #${item.categoryId}`
  }
  return ''
}

// ===== 结果点击 =====
function handleResultClick(item: SearchHit) {
  addToHistory(keyword.value.trim())
  window.open(`/post/${item.slug}`, '_blank', 'noopener,noreferrer')
}

// ===== 关闭模态框 =====
function close() {
  searchModal.close()
  emit('update:visible', false)
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    isCoarsePointer.value = window.matchMedia('(pointer: coarse)').matches
  }
})

// ===== 监听显示状态 =====
let locked = false

watch(() => props.visible, (val) => {
  if (val) {
    loadHistory()
    keyword.value = searchModal.initialKeyword.value
    inputPlaceholderText.value = searchModal.inputPlaceholder.value || DEFAULT_SEARCH_PLACEHOLDER
    nextTick(() => {
      searchInputRef.value?.focus()
    })
    if (searchModal.autoSearchOnOpen.value && keyword.value.trim()) {
      void doSearch()
      searchModal.autoSearchOnOpen.value = false
    }
    if (!locked) {
      lockScroll()
      locked = true
    }
  } else {
    keyword.value = ''
    hasSearched.value = false
    inputPlaceholderText.value = DEFAULT_SEARCH_PLACEHOLDER
    results.value = []
    activeIndex.value = -1
    historyLongPressIndex.value = null
    suppressHistoryClick.value = false
    clearHistoryLongPressTimer()
    if (locked) {
      unlockScroll()
      locked = false
    }
  }
}, { immediate: true })

// 组件卸载时确保恢复滚动
onUnmounted(() => {
  clearHistoryLongPressTimer()
  if (locked) {
    unlockScroll()
    locked = false
  }
})
</script>

<style lang="scss" scoped>
/* ===== 过渡动画 ===== */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .search-modal,
.modal-leave-active .search-modal {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;

  .search-modal {
    transform: translateY(-20px) scale(0.98);
    opacity: 0;
  }
}

/* ===== 遮罩层（毛玻璃） ===== */
.search-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 10vh;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

/* ===== 模态框主体 ===== */
.search-modal {
  position: relative;
  width: 90%;
  max-width: 640px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: $color-bg;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 16px;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.24);
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: rgba(71, 85, 105, 0.55);
    box-shadow: 0 28px 70px rgba(2, 6, 23, 0.62);
  }
}

.modal-close-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border: 1px solid rgba(148, 163, 184, 0.44);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.96);
  color: $color-text-muted;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.18s, background 0.18s, border-color 0.18s;

  &:hover {
    color: $color-text;
    border-color: $color-primary;
    background: #fff;
  }

  .dark & {
    background: rgba(15, 23, 42, 0.92);
    border-color: $color-dark-border;
    color: #94a3b8;

    &:hover {
      color: $color-dark-text;
      border-color: $color-primary;
      background: rgba(15, 23, 42, 1);
    }
  }
}

/* ===== 搜索头部 ===== */
.search-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 0.8rem;
  border-bottom: 1px solid $color-border;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(248, 250, 252, 0.58));

  .dark & {
    border-bottom-color: $color-dark-border;
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.95), rgba(15, 23, 42, 0.7));
  }
}

.search-input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.12rem;
  box-sizing: border-box;
  border: 1px solid $color-border;
  border-radius: 12px;
  height: 48px;
  padding: 0 0.42rem;
  background: rgba(255, 255, 255, 0.9);
  .dark & {
    border-color: $color-dark-border;
    background: rgba(15, 23, 42, 0.78);
  }
}

.search-submit-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.1);
  color: $color-text-muted;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease;

  &:hover {
    color: $color-primary;
    border-color: rgba(59, 130, 246, 0.3);
    background: rgba(59, 130, 246, 0.14);
  }

  .dark & {
    color: #64748b;

    &:hover {
      color: #93c5fd;
      border-color: rgba(96, 165, 250, 0.34);
      background: rgba(59, 130, 246, 0.28);
    }
  }
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.96rem;
  color: $color-text;
  line-height: 1.4;

  &::placeholder {
    color: $color-text-muted;
  }

  .dark & {
    color: $color-dark-text;

    &::placeholder {
      color: #64748b;
    }
  }
}

.clear-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  background: rgba(148, 163, 184, 0.1);
  color: $color-text-muted;
  cursor: pointer;
  padding: 0;
  border-radius: 12px;
  opacity: 0;
  pointer-events: none;
  transition: color 0.2s, opacity 0.18s, background 0.18s, border-color 0.18s;

  &:hover {
    color: $color-text;
    border-color: rgba(100, 116, 139, 0.26);
    background: rgba(148, 163, 184, 0.16);
  }

  .dark & {
    color: #64748b;

    &:hover {
      color: $color-dark-text;
      border-color: rgba(148, 163, 184, 0.28);
      background: rgba(100, 116, 139, 0.24);
    }
  }
}

.clear-btn--visible {
  opacity: 1;
  pointer-events: auto;
}

/* ===== 搜索内容区域 ===== */
.search-body {
  flex: 1;
  min-height: 0;
  padding: $spacing-md;
}

.search-body--results {
  overflow-y: auto;
}

.search-body--ranking {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  overflow: hidden;
}

.search-body--ranking .history-section {
  flex-shrink: 0;
  margin-bottom: 0;
}

.search-body--ranking .ranking-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.search-body--ranking .ranking-section :deep(.ranking-list) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.search-body--ranking .ranking-section :deep(.ranking-list .ranking-tabs),
.search-body--ranking .ranking-section :deep(.ranking-list .ranking-tip) {
  flex-shrink: 0;
}

.search-body--ranking .ranking-section :deep(.ranking-list .ranking-body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

/* ===== 搜索历史 ===== */
.history-section {
  margin-bottom: $spacing-lg;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.clear-all-btn {
  border: none;
  background: transparent;
  font-size: 0.75rem;
  color: $color-text-muted;
  cursor: pointer;
  padding: 0.125rem 0.375rem;
  border-radius: $radius-sm;
  transition: color 0.2s, background 0.2s;

  &:hover {
    color: var(--status-danger);
    background: var(--status-danger-soft-bg);
  }

  .dark & {
    color: #64748b;

    &:hover {
      color: var(--status-danger);
      background: var(--status-danger-soft-bg);
    }
  }
}

.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  padding-top: 4px;
}

.history-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.625rem;
  font-size: 0.8rem;
  color: $color-text;
  background: $color-bg-secondary;
  border: 1px solid $color-border;
  border-radius: 999px;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.2s, border-color 0.2s, background 0.2s;

  &:hover {
    color: $color-primary;
    border-color: $color-primary;
    background: rgba(59, 130, 246, 0.04);
  }

  .dark & {
    color: $color-dark-text;
    background: $color-dark-bg;
    border-color: $color-dark-border;

    &:hover {
      color: $color-primary;
      border-color: $color-primary;
      background: rgba(59, 130, 246, 0.1);
    }
  }
}

.tag-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  min-width: 16px;
  min-height: 16px;
  border: none;
  background: $color-bg-secondary;
  border-radius: 50%;
  color: $color-text-muted;
  cursor: pointer;
  padding: 0;
  opacity: 0;
  pointer-events: none;
  transition: color 0.2s, opacity 0.15s, background 0.2s;
  box-shadow: 0 0 0 1px $color-border;

  &:hover {
    color: #fff;
    background: var(--status-danger);
    box-shadow: none;
  }

  .dark & {
    background: $color-dark-bg;
    color: #64748b;
    box-shadow: 0 0 0 1px $color-dark-border;

    &:hover {
      color: #fff;
      background: var(--status-danger);
      box-shadow: none;
    }
  }
}

.history-tag:hover .tag-remove {
  opacity: 1;
  pointer-events: auto;
}

.history-tag--removable .tag-remove {
  opacity: 1;
  pointer-events: auto;
}

/* ===== 排行榜区块 ===== */
.ranking-section {
  .section-header {
    margin-bottom: $spacing-sm;
  }
}

/* ===== 搜索结果 ===== */
.search-results {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.result-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: background 0.15s;

  &:hover,
  &.active {
    background: rgba(59, 130, 246, 0.06);
  }

  .dark & {
    &:hover,
    &.active {
      background: rgba(59, 130, 246, 0.1);
    }
  }
}

.result-content {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: $color-text;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  :deep(em) {
    color: $color-primary;
    font-style: normal;
    font-weight: 700;
  }

  .dark & {
    color: $color-dark-text;
  }
}

.result-summary {
  font-size: 0.8rem;
  color: $color-text-muted;
  line-height: 1.5;
  margin-top: 0.25rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;

  :deep(em) {
    color: $color-primary;
    font-style: normal;
  }

  .dark & {
    color: #94a3b8;
  }
}

.result-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.2rem 0.65rem;
  margin-top: 0.375rem;
  font-size: 0.75rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  line-height: 1;
}

.result-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.2rem 0.65rem;
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  line-height: 1;
}

/* ===== 搜索加载骨架屏 ===== */
.search-loading {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.skeleton-result {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-sm;
}

.skeleton-cover {
  flex-shrink: 0;
  width: 64px;
  height: 48px;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1e293b;
  }
}

.skeleton-info {
  flex: 1;
}

.skeleton-title {
  height: 16px;
  width: 70%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1e293b;
  }
}

.skeleton-summary {
  height: 12px;
  width: 90%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-top: $spacing-sm;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1e293b;
  }
}

.skeleton-time {
  height: 10px;
  width: 30%;
  border-radius: $radius-sm;
  background: $color-bg-secondary;
  margin-top: $spacing-sm;
  animation: skeleton-pulse 1.5s ease-in-out infinite;

  .dark & {
    background: #1e293b;
  }
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ===== 无结果 ===== */
.search-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl * 2;
  color: $color-text-muted;
  gap: $spacing-sm;

  p {
    font-size: 0.95rem;
    font-weight: 500;
  }

  span {
    font-size: 0.8rem;
  }

  .dark & {
    color: #64748b;
  }
}

.search-pending {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl * 2;
  color: $color-text-muted;
  gap: $spacing-sm;

  p {
    font-size: 0.9rem;
    font-weight: 500;
    text-align: center;
  }

  .dark & {
    color: #64748b;
  }
}

/* ===== 底部快捷键提示 ===== */
.search-footer {
  padding: $spacing-sm $spacing-md;
  border-top: 1px solid $color-border;

  .dark & {
    border-top-color: $color-dark-border;
  }
}

.shortcut-hints {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.hint {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }

  kbd {
    display: inline-block;
    padding: 0.1rem 0.35rem;
    font-size: 0.7rem;
    font-family: inherit;
    color: $color-text-muted;
    background: $color-bg-secondary;
    border: 1px solid $color-border;
    border-radius: $radius-sm;
    line-height: 1.4;

    .dark & {
      color: #64748b;
      background: $color-dark-bg;
      border-color: $color-dark-border;
    }
  }
}

/* ===== 响应式 ===== */
@media (max-width: $breakpoint-md) {
  .search-modal-overlay {
    padding-top: 0;
    align-items: stretch;
  }

  .search-modal {
    width: 100%;
    max-width: 100%;
    max-height: 100vh;
    border-radius: 0;
  }

  .search-header {
    padding: 0.7rem;
    gap: 0.5rem;
  }

  .search-input {
    font-size: 1rem;
  }

  .search-empty,
  .search-pending {
    padding: 2.5rem 1rem;
  }

  .search-footer {
    border-top: none;
  }

  .tag-remove {
    top: -7px;
    right: -7px;
    width: 18px;
    height: 18px;
    min-width: 18px;
    min-height: 18px;
  }

  .tag-remove :deep(svg) {
    width: 11px;
    height: 11px;
  }

  .shortcut-hints {
    display: none;
  }
}

/* ===== 减少动画偏好 ===== */
@media (prefers-reduced-motion: reduce) {
  .modal-enter-active,
  .modal-leave-active,
  .modal-enter-active .search-modal,
  .modal-leave-active .search-modal {
    transition: none;
  }
}
</style>
