<template>
  <div v-if="totalPages > 1" class="pagination">
    <!-- 总条数 & 页码信息 -->
    <span class="pagination-info">共{{ total }}条</span>
    <span class="pagination-sep">|</span>
    <span class="pagination-info">{{ currentPage }}/{{ totalPages }}</span>

    <!-- 每页条数选择 -->
    <div class="page-size-selector">
      <button ref="sizeTriggerRef" class="size-trigger" @click="sizeDropdownOpen = !sizeDropdownOpen" @blur="closeSizeDropdown">
        {{ pageSize }}条/页
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

    <!-- 上一页 -->
    <button class="page-btn nav-btn" :disabled="currentPage <= 1" title="上一页" @click="changePage(currentPage - 1)">
      <Icon name="heroicons:chevron-left-16-solid" size="16" />
    </button>

    <!-- 页码列表 -->
    <div class="page-list">
      <template v-for="(item, idx) in displayedPages" :key="idx">
        <!-- 省略号 -->
        <div
          v-if="typeof item === 'object'"
          class="page-ellipsis"
          @mouseenter="openEllipsis(idx, $event, item.pages)"
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

    <!-- 下一页 -->
    <button class="page-btn nav-btn" :disabled="currentPage >= totalPages" title="下一页" @click="changePage(currentPage + 1)">
      <Icon name="heroicons:chevron-right-16-solid" size="16" />
    </button>

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
  const MAX = 7
  const SIDE = 2

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

function openEllipsis(idx: number, e: MouseEvent, pages: number[]) {
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

onUnmounted(() => {
  if (ellipsisTimer) clearTimeout(ellipsisTimer)
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
  .dark & { color: #64748b; }
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
  border-radius: $radius-sm;
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
  border-radius: $radius-sm;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.12);
  padding: 0.25rem 0;
}

.size-option {
  display: block;
  width: 100%;
  padding: 0.35rem 0;
  border: none;
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
    flex-wrap: wrap;
    width: 100%;
    gap: 0.375rem;
    padding: 0.5rem;
  }

  .page-btn {
    min-width: 32px;
    height: 32px;
  }

  .page-ellipsis {
    width: 32px;
    height: 32px;
  }

  .page-jump {
    display: none;
  }

  .pagination-info,
  .pagination-sep {
    font-size: 0.75rem;
  }
}
</style>
