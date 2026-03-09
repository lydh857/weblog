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
              <Icon name="heroicons:magnifying-glass-20-solid" size="20" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="keyword"
                type="text"
                class="search-input"
                placeholder="搜索文章..."
                autocomplete="off"
                @input="handleInput"
              />
              <button v-if="keyword" class="clear-btn" aria-label="清空搜索" @click="clearKeyword">
                <Icon name="heroicons:x-circle-20-solid" size="18" />
              </button>
            </div>
            <button class="close-btn" aria-label="关闭搜索" @click="close">
              <kbd>Esc</kbd>
            </button>
          </div>

          <!-- 搜索内容区域 -->
          <div class="search-body">
            <!-- 无关键词：搜索历史 + 排行榜 -->
            <template v-if="!keyword.trim()">
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
                    @click="searchFromHistory(item)"
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
                      <Icon name="heroicons:document-text-16-solid" size="12" />
                      <span>文章</span>
                    </div>
                  </div>
                </a>
              </div>

              <!-- 无结果 -->
              <div v-else class="search-empty">
                <Icon name="heroicons:magnifying-glass-16-solid" size="40" />
                <p>未找到相关文章</p>
                <span>换个关键词试试</span>
              </div>
            </template>
          </div>

          <!-- 底部快捷键提示 -->
          <div class="search-footer">
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

// ===== Props / Emits =====
interface Props {
  visible: boolean
}
interface Emits {
  (e: 'update:visible', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// ===== 状态 =====
const searchInputRef = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const results = ref<SearchHit[]>([])
const searching = ref(false)
const activeIndex = ref(-1)

// ===== 搜索历史（localStorage 持久化） =====
const HISTORY_KEY = 'weblog_search_history'
const MAX_HISTORY = 10

const history = ref<string[]>([])

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
  saveHistory()
}

/** 清空全部历史 */
function clearAllHistory() {
  history.value = []
  saveHistory()
}

/** 从历史记录搜索 */
function searchFromHistory(kw: string) {
  keyword.value = kw
  doSearch()
}

// ===== 搜索逻辑 =====
let debounceTimer: ReturnType<typeof setTimeout> | null = null

/** 输入事件处理（防抖） */
function handleInput() {
  activeIndex.value = -1
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    doSearch()
  }, 300)
}

/** 执行搜索 */
async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    results.value = []
    return
  }

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

  // 仅在有搜索结果时处理上下键和回车
  if (!keyword.value.trim() || results.value.length === 0) return

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
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (activeIndex.value >= 0 && activeIndex.value < results.value.length) {
      handleResultClick(results.value[activeIndex.value])
    }
  }
}

/** 滚动选中项到可视区域 */
function scrollActiveIntoView() {
  nextTick(() => {
    const activeEl = document.querySelector('.result-item.active')
    activeEl?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

// ===== 结果点击 =====
function handleResultClick(item: SearchHit) {
  addToHistory(keyword.value.trim())
  window.open(`/post/${item.slug}`, '_blank')
}

// ===== 关闭模态框 =====
function close() {
  emit('update:visible', false)
}

// ===== 监听显示状态 =====
watch(() => props.visible, (val) => {
  if (val) {
    // 打开时加载历史、聚焦输入框、锁定 body 滚动
    loadHistory()
    nextTick(() => {
      searchInputRef.value?.focus()
    })
    document.body.style.overflow = 'hidden'
  } else {
    // 关闭时重置状态、恢复 body 滚动
    keyword.value = ''
    results.value = []
    activeIndex.value = -1
    document.body.style.overflow = ''
  }
})

// 组件卸载时确保恢复滚动
onUnmounted(() => {
  document.body.style.overflow = ''
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
  z-index: 1000;
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
  width: 90%;
  max-width: 640px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: $color-bg;
  border-radius: $radius-lg;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.15);
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.4);
  }
}

/* ===== 搜索头部 ===== */
.search-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.search-input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.search-icon {
  flex-shrink: 0;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 1rem;
  color: $color-text;
  line-height: 1.5;

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
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $color-text-muted;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: $radius-sm;
  transition: color 0.2s;

  &:hover {
    color: $color-text;
  }

  .dark & {
    color: #64748b;

    &:hover {
      color: $color-dark-text;
    }
  }
}

.close-btn {
  flex-shrink: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;

  kbd {
    display: inline-block;
    padding: 0.15rem 0.5rem;
    font-size: 0.75rem;
    font-family: inherit;
    color: $color-text-muted;
    background: $color-bg-secondary;
    border: 1px solid $color-border;
    border-radius: $radius-sm;

    .dark & {
      color: #64748b;
      background: $color-dark-bg;
      border-color: $color-dark-border;
    }
  }
}

/* ===== 搜索内容区域 ===== */
.search-body {
  flex: 1;
  overflow-y: scroll;
  padding: $spacing-md;
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
    color: #ef4444;
    background: rgba(239, 68, 68, 0.06);
  }

  .dark & {
    color: #64748b;

    &:hover {
      color: #f87171;
      background: rgba(239, 68, 68, 0.1);
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
  border: none;
  background: $color-bg-secondary;
  border-radius: 50%;
  color: $color-text-muted;
  cursor: pointer;
  padding: 0;
  opacity: 0;
  transition: color 0.2s, opacity 0.15s, background 0.2s;
  box-shadow: 0 0 0 1px $color-border;

  &:hover {
    color: #fff;
    background: #ef4444;
    box-shadow: none;
  }

  .dark & {
    background: $color-dark-bg;
    color: #64748b;
    box-shadow: 0 0 0 1px $color-dark-border;

    &:hover {
      color: #fff;
      background: #f87171;
      box-shadow: none;
    }
  }
}

.history-tag:hover .tag-remove {
  opacity: 1;
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
  gap: 0.25rem;
  margin-top: 0.375rem;
  font-size: 0.75rem;
  color: $color-text-muted;

  .dark & {
    color: #64748b;
  }
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
