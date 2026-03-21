<template>
  <div ref="filterRef" class="category-filter">
    <!-- 移动端筛选入口（参考案例） -->
    <div ref="filterToggleContainerRef" class="filter-toggle-container mobile-only">
      <button class="filter-toggle-btn" @click="openDrawer">
        <Icon name="heroicons:funnel-20-solid" size="16" />
        筛选
      </button>
      <div class="active-filters" aria-live="polite">
        <span v-if="activeFilterSummary.parent" class="active-filter-tag">{{ activeFilterSummary.parent }}</span>
        <span v-if="activeFilterSummary.child" class="active-filter-tag">{{ activeFilterSummary.child }}</span>
        <span v-if="activeFilterSummary.tag" class="active-filter-tag">{{ activeFilterSummary.tag }}</span>
        <span v-if="activeFilterSummary.sort" class="active-filter-tag">{{ activeFilterSummary.sort }}</span>
      </div>
    </div>

    <!-- 桌面端筛选面板 -->
    <div class="filter-panel desktop-filter">
      <!-- 一级分类 -->
      <div class="filter-row">
        <span class="filter-label">
          <Icon name="heroicons:squares-2x2-16-solid" size="15" />
          一级分类
        </span>
        <div class="filter-buttons">
          <button
            v-memo="[selectedCategoryId === null]"
            class="filter-btn"
            :class="{ active: selectedCategoryId === null }"
            @click="handleCategoryChange(null)"
          >全部</button>
          <button
            v-for="cat in categories"
            :key="cat.id"
            class="filter-btn"
            :class="{ active: selectedCategoryId === cat.id }"
            @click="handleCategoryChange(cat.id)"
          >
            {{ cat.name }}
            <span v-if="cat.postCount > 0" class="badge">{{ cat.postCount }}</span>
          </button>
        </div>
      </div>

      <!-- 二级分类 -->
      <div class="filter-row">
        <span class="filter-label">
          <Icon name="heroicons:folder-16-solid" size="15" />
          二级分类
        </span>
        <div class="filter-buttons">
          <button
            v-memo="[selectedSubCategoryId === null]"
            class="filter-btn"
            :class="{ active: selectedSubCategoryId === null }"
            @click="handleSubCategoryChange(null)"
          >全部</button>
          <button
            v-for="sub in subCategories"
            :key="sub.id"
            class="filter-btn"
            :class="{ active: selectedSubCategoryId === sub.id }"
            @click="handleSubCategoryChange(sub.id)"
          >
            <template v-if="sub.parentName">
              <span class="parent-prefix">{{ sub.parentName }} / </span>
            </template>
            {{ sub.name }}
            <span v-if="sub.postCount > 0" class="badge">{{ sub.postCount }}</span>
          </button>
        </div>
      </div>

      <!-- 标签筛选 -->
      <div class="filter-row">
        <span class="filter-label">
          <Icon name="heroicons:tag-16-solid" size="15" />
          标签筛选
        </span>
        <div class="filter-buttons">
          <button
            class="filter-btn"
            :class="{ active: selectedTagId === null }"
            @click="emit('update:selectedTagId', null)"
          >全部</button>
          <button
            v-for="tag in filteredTags"
            :key="tag.id"
            class="filter-btn tag-btn"
            :class="{ active: selectedTagId === tag.id }"
            @click="emit('update:selectedTagId', tag.id)"
          >
            {{ tag.name }}
            <span v-if="tag.postCount > 0" class="badge">{{ tag.postCount }}</span>
          </button>
        </div>
      </div>

      <!-- 排序方式 -->
      <div class="filter-row no-border">
        <span class="filter-label">
          <Icon name="heroicons:bars-arrow-down-16-solid" size="15" />
          排序方式
        </span>
        <div class="filter-buttons">
          <button
            class="filter-btn"
            :class="{ active: sortBy === 'recommended' }"
            @click="emit('update:sortBy', 'recommended')"
          >
            <Icon name="heroicons:sparkles-16-solid" size="14" />
            推荐
          </button>
          <button
            class="filter-btn"
            :class="{ active: sortBy === 'latest' }"
            @click="emit('update:sortBy', 'latest')"
          >
            <Icon name="heroicons:clock-16-solid" size="14" />
            最新
          </button>
          <button
            class="filter-btn"
            :class="{ active: sortBy === 'hottest' }"
            @click="emit('update:sortBy', 'hottest')"
          >
            <Icon name="heroicons:fire-16-solid" size="14" />
            最热
          </button>
        </div>
      </div>
    </div>

    <!-- 移动端筛选抽屉 -->
    <Teleport to="body">
      <Transition name="drawer">
        <div v-if="drawerVisible" class="filter-drawer-overlay" @click.self="closeDrawer">
          <div class="filter-drawer">
            <div class="drawer-header">
              <span class="drawer-title">筛选条件</span>
              <button class="drawer-close" @click="closeDrawer">
                <Icon name="heroicons:x-mark-16-solid" size="20" />
              </button>
            </div>
            <div class="drawer-body">
              <!-- 一级分类 -->
              <div class="drawer-section">
                <span class="drawer-label">
                  <Icon name="heroicons:squares-2x2-16-solid" size="15" />
                  一级分类
                </span>
                <div class="drawer-buttons">
                  <button
                    v-memo="[selectedCategoryId === null]"
                    class="filter-btn"
                    :class="{ active: selectedCategoryId === null }"
                    @click="handleCategoryChange(null)"
                  >全部</button>
                  <button
                    v-for="cat in categories"
                    :key="cat.id"
                    class="filter-btn"
                    :class="{ active: selectedCategoryId === cat.id }"
                    @click="handleCategoryChange(cat.id)"
                  >
                    {{ cat.name }}
                    <span v-if="cat.postCount > 0" class="badge">{{ cat.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 二级分类 -->
              <div v-if="subCategories.length" class="drawer-section">
                <span class="drawer-label">
                  <Icon name="heroicons:folder-16-solid" size="15" />
                  二级分类
                </span>
                <div class="drawer-buttons">
                  <button
                    v-memo="[selectedSubCategoryId === null]"
                    class="filter-btn"
                    :class="{ active: selectedSubCategoryId === null }"
                    @click="handleSubCategoryChange(null)"
                  >全部</button>
                  <button
                    v-for="sub in subCategories"
                    :key="sub.id"
                    class="filter-btn"
                    :class="{ active: selectedSubCategoryId === sub.id }"
                    @click="handleSubCategoryChange(sub.id)"
                  >
                    <template v-if="sub.parentName">
                      <span class="parent-prefix">{{ sub.parentName }} / </span>
                    </template>
                    {{ sub.name }}
                    <span v-if="sub.postCount > 0" class="badge">{{ sub.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 标签 -->
              <div v-if="filteredTags.length" class="drawer-section">
                <span class="drawer-label">
                  <Icon name="heroicons:tag-16-solid" size="15" />
                  标签筛选
                </span>
                <div class="drawer-buttons">
                  <button
                    class="filter-btn"
                    :class="{ active: selectedTagId === null }"
                    @click="emit('update:selectedTagId', null)"
                  >全部</button>
                  <button
                    v-for="tag in filteredTags"
                    :key="tag.id"
                    class="filter-btn tag-btn"
                    :class="{ active: selectedTagId === tag.id }"
                    @click="emit('update:selectedTagId', tag.id)"
                  >
                    {{ tag.name }}
                    <span v-if="tag.postCount > 0" class="badge">{{ tag.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 排序 -->
              <div class="drawer-section">
                <span class="drawer-label">
                  <Icon name="heroicons:bars-arrow-down-16-solid" size="15" />
                  排序方式
                </span>
                <div class="drawer-buttons">
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'recommended' }"
                    @click="emit('update:sortBy', 'recommended')"
                  >推荐</button>
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'latest' }"
                    @click="emit('update:sortBy', 'latest')"
                  >最新</button>
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'hottest' }"
                    @click="emit('update:sortBy', 'hottest')"
                  >最热</button>
                </div>
              </div>
            </div>
            <div class="drawer-footer">
              <button class="drawer-confirm" @click="closeDrawer">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 浮动筛选按钮 + 弹出面板（筛选面板离开视口后显示） -->
    <Teleport to="body">
      <Transition name="fab">
        <div v-if="showFab && !isMobile" class="float-filter-widget">
          <!-- 浮动按钮：筛选/关闭切换 -->
          <button class="float-filter-btn" @click="floatPanelOpen = !floatPanelOpen">
            <Icon :name="floatPanelOpen ? 'heroicons:x-mark-20-solid' : 'heroicons:funnel-20-solid'" size="20" />
          </button>

          <!-- 浮动筛选面板 -->
          <Transition name="float-panel">
            <div v-if="floatPanelOpen" class="float-filter-panel">
              <!-- 一级分类 -->
              <div class="float-row">
                <span class="float-label">
                  <Icon name="heroicons:squares-2x2-16-solid" size="14" />
                  一级分类
                </span>
                <div class="float-buttons">
                  <button
                    v-memo="[selectedCategoryId === null]"
                    class="filter-btn"
                    :class="{ active: selectedCategoryId === null }"
                    @click="handleCategoryChange(null)"
                  >全部</button>
                  <button
                    v-for="cat in categories"
                    :key="cat.id"
                    class="filter-btn"
                    :class="{ active: selectedCategoryId === cat.id }"
                    @click="handleCategoryChange(cat.id)"
                  >
                    {{ cat.name }}
                    <span v-if="cat.postCount > 0" class="badge">{{ cat.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 二级分类 -->
              <div class="float-row">
                <span class="float-label">
                  <Icon name="heroicons:folder-16-solid" size="14" />
                  二级分类
                </span>
                <div class="float-buttons">
                  <button
                    v-memo="[selectedSubCategoryId === null]"
                    class="filter-btn"
                    :class="{ active: selectedSubCategoryId === null }"
                    @click="handleSubCategoryChange(null)"
                  >全部</button>
                  <button
                    v-for="sub in subCategories"
                    :key="sub.id"
                    class="filter-btn"
                    :class="{ active: selectedSubCategoryId === sub.id }"
                    @click="handleSubCategoryChange(sub.id)"
                  >
                    <template v-if="sub.parentName">
                      <span class="parent-prefix">{{ sub.parentName }} / </span>
                    </template>
                    {{ sub.name }}
                    <span v-if="sub.postCount > 0" class="badge">{{ sub.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 标签 -->
              <div class="float-row">
                <span class="float-label">
                  <Icon name="heroicons:tag-16-solid" size="14" />
                  标签筛选
                </span>
                <div class="float-buttons">
                  <button
                    class="filter-btn"
                    :class="{ active: selectedTagId === null }"
                    @click="emit('update:selectedTagId', null)"
                  >全部</button>
                  <button
                    v-for="tag in filteredTags"
                    :key="tag.id"
                    class="filter-btn tag-btn"
                    :class="{ active: selectedTagId === tag.id }"
                    @click="emit('update:selectedTagId', tag.id)"
                  >
                    {{ tag.name }}
                    <span v-if="tag.postCount > 0" class="badge">{{ tag.postCount }}</span>
                  </button>
                </div>
              </div>
              <!-- 排序 -->
              <div class="float-row no-border">
                <span class="float-label">
                  <Icon name="heroicons:bars-arrow-down-16-solid" size="14" />
                  排序方式
                </span>
                <div class="float-buttons">
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'recommended' }"
                    @click="emit('update:sortBy', 'recommended')"
                  >推荐</button>
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'latest' }"
                    @click="emit('update:sortBy', 'latest')"
                  >最新</button>
                  <button
                    class="filter-btn"
                    :class="{ active: sortBy === 'hottest' }"
                    @click="emit('update:sortBy', 'hottest')"
                  >最热</button>
                </div>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- 移动端固定筛选按钮（参考案例） -->
    <Teleport to="body">
      <Transition name="fab">
        <button
          v-if="showFab && isMobile && !drawerVisible"
          class="category-filter-fixed-btn"
          aria-label="打开筛选器"
          @click="openDrawer"
        >
          <Icon name="heroicons:funnel-20-solid" size="20" />
        </button>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import type { CategoryTreeVO } from '~/api/category'
import type { TagCloudVO } from '~/api/tag'

interface SubCategoryItem {
  id: number
  name: string
  postCount: number
  parentName?: string
}

interface Props {
  categories: CategoryTreeVO[]
  tags: TagCloudVO[]
  selectedCategoryId: number | null
  selectedSubCategoryId: number | null
  selectedTagId: number | null
  sortBy: 'recommended' | 'latest' | 'hottest'
  pageSize: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:selectedCategoryId', value: number | null): void
  (e: 'update:selectedSubCategoryId', value: number | null): void
  (e: 'update:selectedTagId', value: number | null): void
  (e: 'update:sortBy', value: 'recommended' | 'latest' | 'hottest'): void
  (e: 'update:pageSize', value: number): void
}>()

/** 移动端抽屉可见状态 */
const drawerVisible = ref(false)

/** 当前是否移动端 */
const isMobile = ref(false)

/** 筛选面板 DOM 引用 */
const filterRef = ref<HTMLElement | null>(null)

/** 移动端筛选入口容器 */
const filterToggleContainerRef = ref<HTMLElement | null>(null)

/** 悬浮按钮显示状态 */
const showFab = ref(false)

/** 浮动筛选面板展开状态 */
const floatPanelOpen = ref(false)

let ticking = false

const sortLabelMap: Record<Props['sortBy'], string> = {
  recommended: '推荐',
  latest: '最新',
  hottest: '最热',
}

const activeFilterSummary = computed(() => {
  const parent = props.categories.find(item => item.id === props.selectedCategoryId)?.name || ''
  const child = subCategories.value.find(item => item.id === props.selectedSubCategoryId)?.name || ''
  const tag = props.tags.find(item => item.id === props.selectedTagId)?.name || ''
  const sort = props.sortBy !== 'recommended' ? sortLabelMap[props.sortBy] : ''
  return {
    parent,
    child,
    tag,
    sort,
  }
})

/**
 * 根据选中的一级分类，计算二级分类列表
 * 未选一级分类时，展示所有子分类并带父分类前缀
 */
const subCategories = computed<SubCategoryItem[]>(() => {
  if (props.selectedCategoryId === null) {
    const all: SubCategoryItem[] = []
    for (const parent of props.categories) {
      if (parent.children?.length) {
        for (const child of parent.children) {
          all.push({
            id: child.id,
            name: child.name,
            postCount: child.postCount,
            parentName: parent.name,
          })
        }
      }
    }
    return all
  }
  const parent = props.categories.find(c => c.id === props.selectedCategoryId)
  return (parent?.children ?? []).map(c => ({
    id: c.id,
    name: c.name,
    postCount: c.postCount,
  }))
})

/** 过滤有文章的标签 */
const filteredTags = computed(() => {
  return props.tags.filter(t => t.postCount > 0)
})

/** 一级分类切换：联动重置二级分类和标签，触发滑入动画 */
function handleCategoryChange(id: number | null) {
  emit('update:selectedCategoryId', id)
}

/** 二级分类切换：联动重置标签，触发标签滑入动画 */
function handleSubCategoryChange(id: number | null) {
  emit('update:selectedSubCategoryId', id)
}

function openDrawer() {
  drawerVisible.value = true
  floatPanelOpen.value = false
}

function closeDrawer() {
  drawerVisible.value = false
}

function syncMobileState() {
  if (!import.meta.client) return
  isMobile.value = window.innerWidth <= 768
}

/** 筛选面板回到视口时，自动关闭浮动面板 */
watch(showFab, (val) => {
  if (!val || isMobile.value) floatPanelOpen.value = false
})

watch(drawerVisible, (visible) => {
  if (!import.meta.client) return
  document.body.style.overflow = visible ? 'hidden' : ''
})

/** 监听滚动，判断筛选面板是否滚出视口 */
const handleScroll = () => {
  if (!import.meta.client || ticking) return
  ticking = true
  requestAnimationFrame(() => {
    const target = isMobile.value ? filterToggleContainerRef.value : filterRef.value
    if (target) {
      showFab.value = target.getBoundingClientRect().bottom <= 0
    }
    ticking = false
  })
}

const handleResize = () => {
  syncMobileState()
  handleScroll()
  if (!isMobile.value) {
    closeDrawer()
  }
}

onMounted(() => {
  syncMobileState()
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleResize, { passive: true })
  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  document.body.style.overflow = ''
})
</script>

<style lang="scss" scoped>
/* ===== 桌面端筛选面板 ===== */
.filter-panel {
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  margin-bottom: $spacing-lg;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.mobile-only {
  display: none;
}

.desktop-only {
  display: block;
}

.filter-toggle-container {
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  margin-bottom: 0.8rem;
}

.filter-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  flex-shrink: 0;
  height: 34px;
  padding: 0 0.75rem;
  border: 1px solid $color-border;
  border-radius: 0.5rem;
  background: $color-bg;
  color: $color-text-muted;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
    color: #cbd5e1;
  }
}

.active-filters {
  display: flex;
  gap: 0.25rem;
  flex: 1;
  justify-content: flex-end;
  overflow: hidden;
  white-space: nowrap;
}

.active-filter-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  max-width: 110px;
  padding: 0 0.5rem;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 0.72rem;
  overflow: hidden;
  text-overflow: ellipsis;

  .dark & {
    background: rgba(59, 130, 246, 0.2);
    color: #93c5fd;
  }
}

.filter-row {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $color-border;
  /* 防止内容变化时高度突变 */
  overflow: hidden;

  .dark & {
    border-bottom-color: $color-dark-border;
  }

  &.no-border {
    border-bottom: none;
  }
}

.filter-label {
  flex-shrink: 0;
  width: 80px;
  font-size: 0.8rem;
  font-weight: 600;
  color: $color-text-muted;
  padding-top: 0.35rem;
  display: flex;
  align-items: center;
  gap: 0.3rem;

  .icon {
    color: $color-primary;
  }

  .dark & {
    color: #94a3b8;
  }
}

.filter-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  align-items: center;
  min-height: 32px;
}

.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.3rem 0.75rem;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: transparent;
  font-size: 0.8rem;
  color: $color-text;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }

  &.active {
    background: $color-primary;
    border-color: $color-primary;
    color: #fff;

    .badge {
      background: rgba(255, 255, 255, 0.25);
      color: #fff;
    }
  }

  .dark & {
    border-color: $color-dark-border;
    color: $color-dark-text;

    &:hover {
      border-color: $color-primary;
      color: $color-primary;
    }

    &.active {
      background: $color-primary;
      border-color: $color-primary;
      color: #fff;
    }
  }
}

.filter-panel .filter-row:nth-child(-n+2) .filter-buttons > .filter-btn:first-child,
.float-filter-panel .float-row:nth-child(-n+2) .float-buttons > .filter-btn:first-child,
.drawer-body .drawer-section:nth-child(-n+2) .drawer-buttons > .filter-btn:first-child {
  transition: none;
}

/* 文章数量徽章 */
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 0.3rem;
  border-radius: 999px;
  background: $color-bg-secondary;
  color: $color-text-muted;
  font-size: 0.7rem;
  line-height: 1;

  .dark & {
    background: rgba(255, 255, 255, 0.1);
    color: #94a3b8;
  }
}

/* 父分类前缀 */
.parent-prefix {
  color: $color-text-muted;
  font-size: 0.75rem;

  .dark & {
    color: #64748b;
  }
}

/* ===== 移动端隐藏桌面筛选面板 ===== */
@media (max-width: $breakpoint-md) {
  .desktop-only,
  .desktop-filter {
    display: none;
  }

  .mobile-only,
  .filter-toggle-container {
    display: flex;
  }

  .active-filters {
    max-width: 72%;
  }

  .active-filter-tag {
    max-width: 86px;
  }

  .float-filter-widget {
    display: none;
  }
}

/* ===== 移动端抽屉 ===== */
.filter-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-modal) + 20);
  background: rgba(2, 6, 23, 0.45);
  backdrop-filter: blur(2px);
}

.filter-drawer {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 86dvh;
  max-height: 86dvh;
  background: $color-bg;
  border-radius: 0 0 1rem 1rem;
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
  }
}

@supports not (height: 1dvh) {
  .filter-drawer {
    height: 86vh;
    max-height: 86vh;
  }
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: max(0.8rem, env(safe-area-inset-top)) 1rem 0.7rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.drawer-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.drawer-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 0.55rem;
  background: transparent;
  color: $color-text-muted;
  cursor: pointer;

  &:hover {
    background: $color-bg-secondary;
  }

  .dark & {
    color: #94a3b8;

    &:hover {
      background: $color-dark-bg;
    }
  }
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  overflow-anchor: none;
  scrollbar-gutter: stable;
  padding: 0.5rem 1rem;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.72rem 0;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }

  &:last-child {
    border-bottom: none;
  }
}

.drawer-label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: $color-text-muted;
  margin-bottom: 0;

  .icon {
    color: $color-primary;
  }

  .dark & {
    color: #94a3b8;
  }
}

.drawer-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.drawer-body .filter-btn {
  min-height: 28px;
  padding: 0.22rem 0.58rem;
  border-radius: 0.45rem;
  font-size: 0.75rem;
}

.drawer-body .badge {
  min-width: 16px;
  height: 16px;
  font-size: 0.64rem;
}

.drawer-footer {
  padding: 0.8rem 1rem max(0.8rem, env(safe-area-inset-bottom));
  border-top: 1px solid $color-border;

  .dark & {
    border-top-color: $color-dark-border;
  }
}

.drawer-confirm {
  width: 100%;
  height: 38px;
  border: none;
  border-radius: 0.55rem;
  background: $color-primary;
  color: #fff;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: $color-primary-dark;
  }
}

/* ===== 浮动筛选组件 ===== */
.float-filter-widget {
  position: fixed;
  right: 2rem;
  bottom: 200px;
  z-index: 980;
}

.category-filter-fixed-btn {
  position: fixed;
  right: 20px;
  bottom: 100px;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.46);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 998;
  transition: background 0.2s, transform 0.2s;

  &:hover {
    background: rgba(0, 0, 0, 0.62);
    transform: scale(1.04);
  }
}

.float-filter-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: $color-primary;
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.35);
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: scale(1.05);
    box-shadow: 0 6px 20px rgba(59, 130, 246, 0.45);
  }
}

.float-filter-panel {
  position: absolute;
  bottom: 60px;
  right: 0;
  width: max(680px, 50vw);
  max-width: calc(100vw - 4rem);
  max-height: 70vh;
  overflow-y: auto;
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  padding: $spacing-md $spacing-lg;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  }

  @media (max-width: $breakpoint-md) {
    width: calc(100vw - 3rem);
    right: -1rem;
  }
}

.float-row {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $color-border;
  overflow: hidden;

  .dark & {
    border-bottom-color: $color-dark-border;
  }

  &.no-border {
    border-bottom: none;
  }
}

.float-label {
  flex-shrink: 0;
  width: 80px;
  font-size: 0.8rem;
  font-weight: 600;
  color: $color-text-muted;
  padding-top: 0.35rem;
  display: flex;
  align-items: center;
  gap: 0.3rem;

  .icon {
    color: $color-primary;
  }

  .dark & {
    color: #94a3b8;
  }
}

.float-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  align-items: center;
  min-height: 32px;
}

/* 浮动面板弹出动画 */
.float-panel-enter-active,
.float-panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.float-panel-enter-from,
.float-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}

.float-panel-enter-to,
.float-panel-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

/* ===== 过渡动画 ===== */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.3s ease;

  .filter-drawer {
    transition: transform 0.3s ease;
  }
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;

  .filter-drawer {
    transform: translateY(-100%);
  }
}

.fab-enter-active,
.fab-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fab-enter-from,
.fab-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
</style>
