<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
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
                :placeholder="actualInputPlaceholder"
                :maxlength="maxKeywordLength"
                autocomplete="off"
                @input="handleInput"
              >
              <span v-if="showPlaceholderFire" class="input-placeholder-with-fire" aria-hidden="true">
                <span class="input-placeholder-text">{{ inputPlaceholderText }}</span>
                <span class="placeholder-fire">
                  <Icon name="heroicons:fire-16-solid" size="16" />
                </span>
              </span>
              <button
                type="button"
                class="clear-btn"
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
                class="search-submit-btn"
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
            v-custom-scrollbar="hasKeyword"
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
                  @click="handleResultClick"
                >
                  <div class="result-content">
                    <!-- 搜索高亮片段已通过 sanitizeHtml 净化 -->
                    <!-- eslint-disable vue/no-v-html -->
                    <h4 class="result-title" v-html="sanitizeHtml(item.highlightTitle || item.title)" />
                    <p
                      v-if="item.highlightContent || item.summary"
                      class="result-summary"
                      v-html="sanitizeHtml(item.highlightContent || item.summary)"
                    />
                    <!-- eslint-enable vue/no-v-html -->
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

              <div v-else-if="searchErrorMessage" class="search-empty search-error">
                <Icon name="heroicons:exclamation-triangle-16-solid" size="40" />
                <p>{{ searchErrorMessage }}</p>
                <span>请稍后重试，或刷新页面后再试</span>
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
import type { SearchHit } from '~/api/content/search'
import { sanitizeHtml } from '~/utils/security/xss'
import { lockScroll, unlockScroll } from '~/composables/layout/useScrollLock'
import { useSearchModal } from '~/composables/modal/useSearchModal'
import { useSearchModalState } from '~/composables/modal/search/useSearchModalState'

interface Props {
  visible: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const searchModal = useSearchModal()

const searchInputRef = ref<HTMLInputElement | null>(null)

const {
  keyword,
  results,
  searching,
  activeIndex,
  searchErrorMessage,
  hasKeyword,
  showShortcutHints,
  shouldShowEmpty,
  inputPlaceholderText,
  maxKeywordLength,
  showPlaceholderFire,
  actualInputPlaceholder,
  history,
  historyLongPressIndex,
  isCoarsePointer,
  handleInput,
  handleSearchSubmit,
  clearKeyword: clearKeywordState,
  addToHistory,
  removeHistory,
  clearAllHistory,
  handleHistoryTagClick,
  handleHistoryTouchStart,
  handleHistoryTouchMove,
  handleHistoryTouchEnd,
  handleHistoryTouchCancel,
  handleSearchBodyClick,
  detectCoarsePointer,
  openSession,
  closeSession,
  dispose,
} = useSearchModalState({ defaultPlaceholder: '搜索文章...' })

function clearKeyword() {
  clearKeywordState()
  searchInputRef.value?.focus()
}

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

  if (!hasKeyword.value || results.value.length === 0) {
    return
  }

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

function handleResultClick() {
  addToHistory(keyword.value.trim())
}

function close() {
  searchModal.close()
  emit('update:visible', false)
}

onMounted(() => {
  detectCoarsePointer()
})

let locked = false

watch(() => props.visible, (val) => {
  if (val) {
    const initialKeyword = searchModal.initialKeyword.value
    const placeholder = searchModal.inputPlaceholder.value
    const shouldAutoSearch = searchModal.autoSearchOnOpen.value && initialKeyword.trim().length > 0

    openSession({
      initialKeyword,
      placeholder,
      autoSearch: shouldAutoSearch,
    })

    nextTick(() => {
      searchInputRef.value?.focus()
    })

    if (shouldAutoSearch) {
      searchModal.autoSearchOnOpen.value = false
    }

    if (!locked) {
      lockScroll()
      locked = true
    }
  } else {
    closeSession()
    if (locked) {
      unlockScroll()
      locked = false
    }
  }
}, { immediate: true })

onUnmounted(() => {
  dispose()
  if (locked) {
    unlockScroll()
    locked = false
  }
})
</script>

<style lang="scss" scoped>
/* ===== 过渡动画 ===== */
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .search-modal {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .search-modal {
    transform: translateY(20px) scale(0.96);
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
  will-change: opacity;

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

/* ===== 模态框主体 ===== */
.search-modal {
  --search-muted: #64748b;
  --search-muted-strong: #475569;
  --search-surface: rgba(248, 250, 252, 0.96);
  --search-surface-soft: rgba(148, 163, 184, 0.1);
  --search-border-soft: rgba(148, 163, 184, 0.44);
  --search-hover-soft: rgba(148, 163, 184, 0.16);
  --search-shell-bg-dark:
    radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
    radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
    linear-gradient(180deg, #171b20, #101215);

  position: relative;
  width: 90%;
  max-width: 640px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: $color-bg;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  transform: translate3d(0, 0, 0);
  will-change: transform;

  .dark & {
    --search-muted: #9aa5b5;
    --search-muted-strong: #d6dbe4;
    --search-surface: rgba(16, 18, 21, 0.92);
    --search-surface-soft: rgba(148, 163, 184, 0.14);
    --search-border-soft: rgba(71, 85, 105, 0.55);
    --search-hover-soft: rgba(148, 163, 184, 0.2);

    background: var(--search-shell-bg-dark);
    border-color: rgba(148, 163, 184, 0.14);
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
  }
}

@media (hover: none) and (pointer: coarse) {
  .search-modal-overlay {
    backdrop-filter: none;
    background: rgba(0, 0, 0, 0.52);
  }
}

.modal-close-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border: 1px solid var(--search-border-soft);
  border-radius: 12px;
  background: var(--search-surface);
  color: var(--search-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.18s, background 0.18s, border-color 0.18s;

  &:hover {
    color: $color-text;
    border-color: rgba(100, 116, 139, 0.26);
    background: rgba(148, 163, 184, 0.16);
  }

  .dark & {
    background: var(--search-surface);
    border-color: var(--search-border-soft);
    color: var(--search-muted);

    &:hover {
      color: var(--search-muted-strong);
      border-color: rgba(148, 163, 184, 0.28);
      background: var(--search-hover-soft);
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
    background: var(--search-shell-bg-dark);
  }
}

.search-input-wrapper {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.12rem;
  box-sizing: border-box;
  border: 1px solid $color-border;
  border-radius: 12px;
  height: 44px;
  padding: 0 0.3rem;
  background: var(--search-surface);
  gap: 0.08rem;

  .dark & {
    border-color: $color-dark-border;
    background: var(--search-surface);
  }
}

.input-placeholder-with-fire {
  position: absolute;
  left: 0.72rem;
  right: 5.5rem;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  gap: 0.22rem;
  min-width: 0;
  pointer-events: none;
}

.input-placeholder-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--search-muted);
}

.search-submit-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  min-width: 38px;
  min-height: 38px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--search-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease;

  &:hover {
    color: $color-text;
    border-color: rgba(100, 116, 139, 0.26);
    background: rgba(148, 163, 184, 0.16);
  }

  .dark & {
    color: var(--search-muted);

    &:hover {
      color: var(--search-muted-strong);
      border-color: rgba(148, 163, 184, 0.28);
      background: var(--search-hover-soft);
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
    color: var(--search-muted);
  }

  .dark & {
    color: $color-dark-text;

    &::placeholder {
      color: var(--search-muted);
    }
  }
}

.placeholder-fire {
  flex-shrink: 0;
  color: #fb923c;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 0 6px rgba(251, 146, 60, 0.4));
  animation: flame-flicker 1.15s ease-in-out infinite;
}

@keyframes flame-flicker {
  0%, 100% {
    transform: translate3d(0, 0, 0) scale(1);
    opacity: 0.92;
  }
  35% {
    transform: translate3d(0, -1px, 0) scale(1.08);
    opacity: 1;
  }
  68% {
    transform: translate3d(0, 0.5px, 0) scale(0.96);
    opacity: 0.82;
  }
}

.clear-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  min-width: 38px;
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  background: transparent;
  color: var(--search-muted);
  cursor: pointer;
  padding: 0;
  border-radius: 10px;
  opacity: 0;
  pointer-events: none;
  transition: color 0.2s, opacity 0.18s, background 0.18s, border-color 0.18s;

  &:hover {
    color: $color-text;
    border-color: rgba(100, 116, 139, 0.26);
    background: rgba(148, 163, 184, 0.16);
  }

  .dark & {
    color: var(--search-muted);

    &:hover {
      color: var(--search-muted-strong);
      border-color: rgba(148, 163, 184, 0.28);
      background: var(--search-hover-soft);
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
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.search-body--results::-webkit-scrollbar,
.search-body--results::-webkit-scrollbar-button,
.search-body--results::-webkit-scrollbar-track,
.search-body--results::-webkit-scrollbar-thumb,
.search-body--results::-webkit-scrollbar-corner {
  width: 0;
  height: 0;
  display: none;
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

.search-body--ranking .ranking-section :deep(.ranking-list .ranking-tabs) {
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.search-body--ranking .ranking-section :deep(.ranking-list .ranking-tabs::-webkit-scrollbar) {
  display: none;
}

.search-body--ranking .ranking-section :deep(.ranking-list .ranking-body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.search-body :deep(.v-custom-scrollbar-host .v-custom-scrollbar-rail) {
  opacity: 0;
  transition: width 0.24s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.2s ease;
}

.search-body :deep(.v-custom-scrollbar-host:hover .v-custom-scrollbar-rail),
.search-body :deep(.v-custom-scrollbar-host:focus-within .v-custom-scrollbar-rail) {
  opacity: 0.72;
}

.search-body :deep(.v-custom-scrollbar-host .v-custom-scrollbar-rail:hover),
.search-body :deep(.v-custom-scrollbar-host .v-custom-scrollbar-rail.is-dragging) {
  width: 8px;
  opacity: 1;
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
    color: $color-dark-text-muted;
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
    color: $color-dark-text-muted;

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
    color: $color-dark-text-muted;
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

  .dark & {
    border: 1px solid transparent;
  }

  &:hover,
  &.active {
    background: rgba(59, 130, 246, 0.06);
  }

  .dark & {
    &:hover,
    &.active {
      background: rgba(59, 130, 246, 0.1);
      border-color: rgba(148, 163, 184, 0.26);
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
    color: $color-dark-text-muted;
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
    color: $color-dark-text-muted;
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
    color: $color-dark-text-muted;
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
    color: $color-dark-text-muted;
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
    color: $color-dark-text-muted;
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
      color: $color-dark-text-muted;
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
  .placeholder-fire,
  .modal-fade-enter-active,
  .modal-fade-leave-active,
  .modal-fade-appear-active,
  .modal-fade-enter-active .search-modal,
  .modal-fade-leave-active .search-modal,
  .modal-fade-appear-active .search-modal {
    transition: none;
  }
}
</style>
