<template>
  <div v-if="totalPages > 1" class="pagination">
    <!-- 总条数 & 页码信息 -->
    <div class="pagination-meta">
      <span class="pagination-info">{{ totalLabel }}</span>
      <span v-if="!isUltraCompact" class="pagination-sep">|</span>
      <span class="pagination-info">{{ currentPage }}/{{ totalPages }}</span>
    </div>

    <!-- 每页条数选择 -->
    <div class="page-size-selector">
      <button ref="sizeTriggerRef" class="size-trigger" @click="sizeDropdownOpen = !sizeDropdownOpen" @blur="closeSizeDropdown">
        {{ pageSizeLabel }}
        <svg class="size-arrow" :class="{ open: sizeDropdownOpen }" viewBox="0 0 24 24" width="12" height="12">
          <path fill="currentColor" d="M7 10l5 5 5-5z" />
        </svg>
      </button>
      <Teleport to="body">
        <Transition name="size-dropdown">
          <div v-if="sizeDropdownOpen" class="size-dropdown" :style="sizeDropdownStyle">
            <button
              v-for="opt in pageSizeOptions"
              :key="opt"
              class="size-option"
              :class="{ active: pageSize === opt }"
              @mousedown.prevent
              @click="handleSizeChange(opt)"
            >{{ opt }}条/页</button>
          </div>
        </Transition>
      </Teleport>
    </div>

    <div class="pagination-controls">
      <!-- 上一页 -->
      <button class="page-btn nav-btn" :disabled="currentPage <= 1" title="上一页" @click="changePage(currentPage - 1)">
        <Icon name="heroicons:chevron-left-16-solid" :size="navIconSize" />
      </button>

      <!-- 页码列表 -->
      <div class="page-list">
        <template v-for="(item, idx) in displayedPages" :key="idx">
          <!-- 省略号 -->
          <div
            v-if="typeof item === 'object'"
            class="page-ellipsis"
            @click="toggleEllipsis($event, item.pages)"
            @mouseenter="openEllipsis($event, item.pages)"
            @mouseleave="onEllipsisLeave"
          >
            <span>...</span>
          </div>
          <!-- 普通页码 -->
          <button
            v-else
            class="page-btn"
            :class="{ active: item === currentPage }"
            @click="changePage(item)"
          >{{ item }}</button>
        </template>
      </div>

      <!-- 下一页 -->
      <button class="page-btn nav-btn" :disabled="currentPage >= totalPages" title="下一页" @click="changePage(currentPage + 1)">
        <Icon name="heroicons:chevron-right-16-solid" :size="navIconSize" />
      </button>
    </div>

    <!-- 省略号悬浮弹窗 -->
    <Teleport to="body">
      <Transition name="ellipsis-dropdown">
        <div
          v-if="ellipsisPages.length"
          class="ellipsis-dropdown"
          :style="ellipsisPos"
          @mouseenter="ellipsisDropdownHover = true"
          @mouseleave="onEllipsisDropdownLeave"
        >
          <button
            v-for="p in ellipsisPages"
            :key="p"
            class="ellipsis-page-btn"
            @click="handleEllipsisClick(p)"
          >{{ p }}</button>
        </div>
      </Transition>
    </Teleport>

    <!-- 跳转 -->
    <div class="page-jump">
      <span class="jump-label">前往</span>
      <input
        v-model="jumpInput"
        class="jump-input"
        type="text"
        maxlength="4"
        @input="onJumpInput"
        @keyup.enter="handleJump"
      />
      <span class="jump-label">页</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  total: number
  currentPage: number
  pageSize: number
  pageCount?: number
  pageSizeOptions?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  pageSizeOptions: () => [10, 20, 30, 50],
})

const emit = defineEmits<{
  'update:currentPage': [page: number]
  'update:pageSize': [size: number]
}>()

type CompactMode = 'normal' | 'compact' | 'ultra'

const compactMode = ref<CompactMode>('normal')
const isUltraCompact = computed(() => compactMode.value === 'ultra')
const totalLabel = computed(() => (isUltraCompact.value ? `${props.total}条` : `共${props.total}条`))
const pageSizeLabel = computed(() => (isUltraCompact.value ? `${props.pageSize}/页` : `${props.pageSize}条/页`))
const navIconSize = computed(() => (isUltraCompact.value ? '14' : '16'))

const totalPages = computed(() => {
  if (props.pageCount && props.pageCount > 0) {
    return props.pageCount
  }
  return Math.ceil(props.total / props.pageSize) || 1
})

// ===== 页码计算 =====
interface EllipsisItem { type: 'ellipsis'; pages: number[] }
type PageItem = number | EllipsisItem

const displayedPages = computed<PageItem[]>(() => {
  const t = totalPages.value
  const c = props.currentPage
  const MAX = compactMode.value === 'ultra' ? 3 : (compactMode.value === 'compact' ? 5 : 7)
  const SIDE = compactMode.value === 'ultra' ? 0 : (compactMode.value === 'compact' ? 1 : 2)

  if (t <= MAX) return Array.from({ length: t }, (_, i) => i + 1)

  const pages: PageItem[] = []

  if (c <= SIDE + 1) {
    for (let i = 1; i <= MAX - 1; i++) pages.push(i)
    pages.push({ type: 'ellipsis', pages: Array.from({ length: t - MAX }, (_, i) => i + MAX) })
    pages.push(t)
  } else if (c >= t - SIDE) {
    pages.push(1)
    pages.push({ type: 'ellipsis', pages: Array.from({ length: t - MAX }, (_, i) => i + 2) })
    for (let i = Math.max(2, t - MAX + 2); i <= t; i++) pages.push(i)
  } else {
    pages.push(1)
    const front: number[] = []
    for (let i = 2; i < c - SIDE; i++) front.push(i)
    if (front.length) pages.push({ type: 'ellipsis', pages: front })
    for (let i = c - SIDE; i <= c + SIDE; i++) pages.push(i)
    const back: number[] = []
    for (let i = c + SIDE + 1; i < t; i++) back.push(i)
    if (back.length) pages.push({ type: 'ellipsis', pages: back })
    pages.push(t)
  }
  return pages
})

function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === props.currentPage) return
  emit('update:currentPage', page)
}

// ===== 每页条数下拉 =====
const sizeDropdownOpen = ref(false)
const sizeTriggerRef = ref<HTMLElement | null>(null)
const sizeDropdownStyle = ref('')

function closeSizeDropdown() {
  setTimeout(() => { sizeDropdownOpen.value = false }, 100)
}

function handleSizeChange(size: number) {
  emit('update:pageSize', size)
  sizeDropdownOpen.value = false
}

watch(sizeDropdownOpen, (open) => {
  if (open && sizeTriggerRef.value) {
    const rect = sizeTriggerRef.value.getBoundingClientRect()
    const below = window.innerHeight - rect.bottom
    const h = props.pageSizeOptions.length * 36 + 8
    if (below < h && rect.top > h) {
      sizeDropdownStyle.value = `position:fixed;left:${rect.left}px;bottom:${window.innerHeight - rect.top + 4}px;width:${rect.width}px;`
    } else {
      sizeDropdownStyle.value = `position:fixed;left:${rect.left}px;top:${rect.bottom + 4}px;width:${rect.width}px;`
    }
  }
})

// ===== 省略号悬浮 =====
const ellipsisPages = ref<number[]>([])
const ellipsisPos = ref<Record<string, string>>({})
let ellipsisHover = false
const ellipsisDropdownHover = ref(false)
let ellipsisTimer: ReturnType<typeof setTimeout> | null = null

function openEllipsis(e: MouseEvent, pages: number[]) {
  if (ellipsisTimer) { clearTimeout(ellipsisTimer); ellipsisTimer = null }
  ellipsisHover = true
  ellipsisPages.value = pages
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const below = window.innerHeight - rect.bottom
  const h = Math.min(pages.length, 5) * 26 + 8
  if (below < h && rect.top > h) {
    ellipsisPos.value = { position: 'fixed', left: `${rect.left + rect.width / 2}px`, bottom: `${window.innerHeight - rect.top + 4}px`, transform: 'translateX(-50%)' }
  } else {
    ellipsisPos.value = { position: 'fixed', left: `${rect.left + rect.width / 2}px`, top: `${rect.bottom + 4}px`, transform: 'translateX(-50%)' }
  }
}

function onEllipsisLeave() {
  ellipsisHover = false
  ellipsisTimer = setTimeout(() => {
    if (!ellipsisDropdownHover.value) ellipsisPages.value = []
  }, 120)
}

function onEllipsisDropdownLeave() {
  ellipsisDropdownHover.value = false
  ellipsisTimer = setTimeout(() => {
    if (!ellipsisHover) ellipsisPages.value = []
  }, 120)
}

function handleEllipsisClick(page: number) {
  ellipsisPages.value = []
  ellipsisHover = false
  ellipsisDropdownHover.value = false
  changePage(page)
}

function toggleEllipsis(e: MouseEvent, pages: number[]) {
  if (
    ellipsisPages.value.length === pages.length
    && ellipsisPages.value.every((p, index) => p === pages[index])
  ) {
    ellipsisPages.value = []
    ellipsisHover = false
    ellipsisDropdownHover.value = false
    return
  }

  openEllipsis(e, pages)
  ellipsisDropdownHover.value = true
}

// ===== 跳转 =====
const jumpInput = ref('')
function onJumpInput(e: Event) {
  const input = e.target as HTMLInputElement
  input.value = input.value.replace(/\D/g, '')
  jumpInput.value = input.value
}
function handleJump() {
  const p = parseInt(jumpInput.value)
  if (!isNaN(p) && p >= 1 && p <= totalPages.value) changePage(p)
  jumpInput.value = ''
}

function syncCompactMode() {
  if (!import.meta.client) return
  const width = window.innerWidth

  if (width <= 360) {
    compactMode.value = 'ultra'
    return
  }

  if (width <= 640) {
    compactMode.value = 'compact'
    return
  }

  compactMode.value = 'normal'
}

onMounted(() => {
  syncCompactMode()
  if (import.meta.client) {
    window.addEventListener('resize', syncCompactMode, { passive: true })
  }
})

onUnmounted(() => {
  if (ellipsisTimer) clearTimeout(ellipsisTimer)
  if (import.meta.client) {
    window.removeEventListener('resize', syncCompactMode)
  }
})
</script>

<style lang="scss" scoped>
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  margin-top: 2rem;
  padding: 0.5rem 0.75rem;
  background: $color-bg;
  border-radius: $radius-lg;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  width: fit-content;
  margin-left: auto;
  margin-right: auto;

  .dark & {
    background: $color-dark-bg-secondary;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  }
}

.pagination-info {
  font-size: 0.8rem;
  color: $color-text-muted;
  white-space: nowrap;
  .dark & { color: $color-dark-text-muted; }
}

.pagination-meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.pagination-sep {
  color: $color-border;
  .dark & { color: $color-dark-border; }
}

/* 页码按钮 */
.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 36px;
  padding: 0 0.25rem;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: transparent;
  font-size: 0.85rem;
  color: $color-text;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(:disabled):not(.active) {
    border-color: $color-primary;
    color: $color-primary;
    background: rgba(59, 130, 246, 0.04);
  }

  &.active {
    background: linear-gradient(135deg, $color-primary 0%, #60a5fa 100%);
    border-color: $color-primary;
    color: #fff;
    font-weight: 600;
    box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;

    &:hover:not(:disabled):not(.active) {
      border-color: $color-primary;
      color: $color-primary;
      background: rgba(59, 130, 246, 0.1);
    }

    &.active {
      background: linear-gradient(135deg, $color-primary 0%, #60a5fa 100%);
      border-color: $color-primary;
      color: #fff;
    }
  }
}

.page-list {
  display: flex;
  gap: 0.25rem;
}

/* 省略号 */
.page-ellipsis {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-muted;
  font-size: 0.85rem;
  cursor: pointer;
  border-radius: $radius-md;
  transition: all 0.2s;

  &:hover {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.04);
  }
}

/* 省略号悬浮弹窗 */
.ellipsis-dropdown {
  z-index: 10000;
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-sm;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-height: 138px;
  overflow-y: auto;
  min-width: 38px;
}

.ellipsis-page-btn {
  min-width: 30px;
  height: 24px;
  border: none;
  border-radius: 2px;
  background: transparent;
  color: $color-text;
  font-size: 0.75rem;
  cursor: pointer;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: rgba(59, 130, 246, 0.08);
    color: $color-primary;
  }
}

/* 每页条数选择 */
.page-size-selector {
  position: relative;
  margin: 0 0.5rem;
}

.size-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.15rem;
  height: 28px;
  padding: 0 0.5rem;
  border: 1px solid $color-border;
  border-radius: 10px;
  background: transparent;
  font-size: 0.8rem;
  color: $color-text;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;
  }
}

.size-arrow {
  transition: transform 0.2s;
  &.open { transform: rotate(180deg); }
}

.size-dropdown {
  z-index: 10000;
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: 12px;
  box-shadow: none;
  padding: 0.3rem;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.size-option {
  display: block;
  width: 100%;
  padding: 0.42rem 0.25rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 0.8rem;
  color: $color-text;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: rgba(59, 130, 246, 0.06);
    color: $color-primary;
  }

  &.active {
    background: rgba(59, 130, 246, 0.1);
    color: $color-primary;
    font-weight: 500;
  }

  .dark & {
    color: $color-dark-text;
  }
}

/* 跳转 */
.page-jump {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-left: 0.5rem;
}

.jump-label {
  font-size: 0.8rem;
  color: $color-text-muted;
}

.jump-input {
  width: 40px;
  height: 28px;
  border: 1px solid $color-border;
  border-radius: $radius-sm;
  padding: 0 0.25rem;
  text-align: center;
  font-size: 0.8rem;
  color: $color-text;
  background: transparent;
  outline: none;
  transition: border-color 0.2s;

  &:focus {
    border-color: $color-primary;
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;
  }
}

/* 动画 */
/* 每页条数下拉动画 */
.size-dropdown-enter-active,
.size-dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.size-dropdown-enter-from,
.size-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 省略号弹窗动画 */
.ellipsis-dropdown-enter-active,
.ellipsis-dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.ellipsis-dropdown-enter-from,
.ellipsis-dropdown-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 640px) {
  .pagination {
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: center;
    width: 100%;
    gap: 0.5rem;
    padding: 0.6rem;
    border-radius: $radius-md;
  }

  .pagination-meta {
    order: 1;
    gap: 0.3rem;
    overflow: hidden;
  }

  .page-size-selector {
    order: 2;
    margin: 0;
    justify-self: end;
  }

  .pagination-controls {
    order: 3;
    grid-column: 1 / -1;
    justify-content: center;
    gap: 0.25rem;
    min-width: 0;
  }

  .page-list {
    gap: 0.2rem;
  }

  .page-btn {
    min-width: 34px;
    height: 34px;
    font-size: 0.8rem;
    border-radius: 10px;
  }

  .page-ellipsis {
    width: 34px;
    height: 34px;
  }

  .page-jump {
    display: none;
  }

  .pagination-info,
  .pagination-sep {
    font-size: 0.75rem;
  }

  .size-trigger {
    height: 32px;
    padding: 0 0.45rem;
    border-radius: 9px;
    font-size: 0.75rem;
  }
}

@media (max-width: 430px) {
  .pagination {
    gap: 0.45rem;
    padding: 0.55rem 0.5rem;
  }

  .pagination-meta {
    gap: 0.24rem;
  }

  .pagination-info,
  .pagination-sep {
    font-size: 0.72rem;
  }

  .pagination-controls {
    gap: 0.2rem;
  }

  .page-list {
    gap: 0.16rem;
  }

  .page-btn {
    min-width: 32px;
    height: 32px;
    padding: 0 0.2rem;
    font-size: 0.76rem;
  }

  .page-ellipsis {
    width: 32px;
    height: 32px;
    font-size: 0.8rem;
  }

  .size-trigger {
    height: 30px;
    padding: 0 0.4rem;
    font-size: 0.72rem;
  }
}

@media (max-width: 390px) {
  .pagination {
    gap: 0.4rem;
    padding: 0.5rem 0.45rem;
  }

  .pagination-meta {
    gap: 0.2rem;
  }

  .pagination-controls {
    gap: 0.16rem;
  }

  .page-list {
    gap: 0.12rem;
  }

  .page-btn {
    min-width: 31px;
    height: 31px;
    font-size: 0.74rem;
    border-radius: 9px;
  }

  .page-ellipsis {
    width: 31px;
    height: 31px;
    font-size: 0.74rem;
  }

  .size-trigger {
    height: 29px;
    padding: 0 0.36rem;
    font-size: 0.7rem;
  }
}

@media (max-width: 360px) {
  .pagination {
    gap: 0.34rem;
    padding: 0.48rem 0.4rem;
  }

  .pagination-info:first-child {
    max-width: 3.8rem;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pagination-controls {
    gap: 0.14rem;
  }

  .page-btn {
    min-width: 30px;
    height: 30px;
    padding: 0 0.15rem;
    font-size: 0.72rem;
    border-radius: 8px;
  }

  .page-ellipsis {
    width: 30px;
    height: 30px;
    font-size: 0.72rem;
    border-radius: 8px;
  }
}

@media (max-width: 640px) {
  .pagination .page-btn,
  .pagination .page-ellipsis,
  .pagination .size-trigger {
    min-width: 34px;
    min-height: 34px;
  }
}

@media (max-width: 430px) {
  .pagination .page-btn,
  .pagination .page-ellipsis,
  .pagination .size-trigger {
    min-width: 32px;
    min-height: 32px;
  }
}

@media (max-width: 360px) {
  .pagination .page-btn,
  .pagination .page-ellipsis,
  .pagination .size-trigger {
    min-width: 30px;
    min-height: 30px;
  }
}
</style>
