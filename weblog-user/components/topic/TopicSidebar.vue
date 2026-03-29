<template>
  <div class="topic-sidebar-wrapper" :class="{ collapsed: collapsed }">
    <!-- 收缩按钮（始终渲染，CSS 控制显隐动画） -->
    <button
      class="sidebar-toggle-btn"
      :class="{ visible: collapsed }"
      title="展开专题目录"
      @click="emit('update:collapsed', false)"
    >
      <Icon name="heroicons:list-bullet-20-solid" size="18" />
    </button>

    <!-- 面板（始终渲染，CSS 控制显隐动画） -->
    <div class="sidebar-panel" :class="{ visible: !collapsed }">
      <div class="sidebar-header">
        <span class="sidebar-title">{{ topicTitle }}</span>
        <div class="header-actions">
          <button
            class="action-btn"
            :title="allExpanded ? '收缩全部' : '展开全部'"
            @click="toggleAllNodes"
          >
            <Icon
              :name="allExpanded ? 'heroicons:chevron-double-up-20-solid' : 'heroicons:chevron-double-down-20-solid'"
              size="15"
            />
          </button>
          <button class="action-btn" title="收起目录" @click="emit('update:collapsed', true)">
            <Icon name="heroicons:chevron-double-left-20-solid" size="15" />
          </button>
        </div>
      </div>
      <div v-custom-scrollbar class="sidebar-tree">
        <TopicSidebarNode
          v-for="node in catalogs"
          :key="node.id"
          :node="node"
          :current-article-id="currentArticleId"
          :force-expand="forceExpand"
          @select="(n: CatalogNode) => emit('select', n)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CatalogNode } from '~/api/content/topic'

defineProps<{
  topicTitle: string
  catalogs: CatalogNode[]
  currentArticleId: number | null
  collapsed: boolean
}>()

const emit = defineEmits<{
  select: [node: CatalogNode]
  'update:collapsed': [value: boolean]
}>()

const allExpanded = ref(true)
const forceExpand = ref<boolean | null>(null)

function toggleAllNodes() {
  allExpanded.value = !allExpanded.value
  forceExpand.value = allExpanded.value
  nextTick(() => { forceExpand.value = null })
}
</script>

<style scoped lang="scss">
.topic-sidebar-wrapper {
  width: 100%;
  min-width: 0;
  flex-shrink: 0;
  position: relative;
}

.sidebar-toggle-btn {
  position: absolute;
  top: 0;
  left: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: #fff;
  color: $color-text-muted;
  cursor: pointer;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.08);
  opacity: 0;
  transform: scale(0.8);
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease, color 0.2s, background 0.2s;
  &.visible { opacity: 1; transform: scale(1); pointer-events: auto; }
  &:hover { color: $color-primary; background: #f0f7ff; }
  .dark & {
    background: $color-dark-bg-secondary; color: #64748b;
    box-shadow: 0 1px 6px rgba(0, 0, 0, 0.2);
    &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.1); }
  }
}

.sidebar-panel {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  opacity: 0;
  transform: translateX(-8px);
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease;
  &.visible { opacity: 1; transform: translateX(0); pointer-events: auto; }
  .dark & { background: $color-dark-bg-secondary; box-shadow: 0 1px 8px rgba(0, 0, 0, 0.2); }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f0f0f0;
  .dark & { border-bottom-color: $color-dark-border; }
}

.sidebar-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  .dark & { color: $color-dark-text; }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
}

.action-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: none;
  color: #999;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { background: #f0f7ff; color: $color-primary; }
  .dark & { color: $color-dark-text-muted; &:hover { background: rgba(59, 130, 246, 0.1); color: $color-primary; } }
}

.sidebar-tree {
  max-height: calc(80vh - 100px);
  overflow-y: auto;
  padding: 0.5rem 0;
}
</style>
