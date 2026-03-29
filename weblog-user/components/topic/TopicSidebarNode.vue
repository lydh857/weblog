<template>
  <div class="tree-node">
    <!-- 节点行 -->
    <div
      class="node-row"
      :class="{
        'is-folder': isFolder,
        'is-article': !isFolder,
        'is-active': !isFolder && node.articleId === currentArticleId,
        [`level-${node.level}`]: true,
      }"
      @click="handleClick"
    >
      <!-- 展开/收起图标（仅文件夹节点） -->
      <button v-if="isFolder" class="expand-btn" @click.stop="expanded = !expanded">
        <Icon
          name="heroicons:chevron-right-16-solid"
          size="14"
          class="expand-icon"
          :class="{ rotated: expanded }"
        />
      </button>
      <span v-else class="node-dot" />

      <!-- 标题 -->
      <span class="node-title">{{ node.title }}</span>
    </div>

    <!-- 子节点 -->
    <div v-if="isFolder && expanded && node.children?.length" class="node-children">
      <TopicSidebarNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :current-article-id="currentArticleId"
        :force-expand="forceExpand"
        @select="(n) => emit('select', n)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CatalogNode } from '~/api/topic'

const props = defineProps<{
  node: CatalogNode
  currentArticleId: number | null
  forceExpand?: boolean | null
}>()

const emit = defineEmits<{
  select: [node: CatalogNode]
}>()

const isFolder = computed(() => !props.node.articleId && !!props.node.children?.length)
const expanded = ref(true)

// 响应父组件的全部展开/收缩指令
watch(() => props.forceExpand, (val) => {
  if (val !== null && val !== undefined) {
    expanded.value = val
  }
})

function handleClick() {
  if (isFolder.value) {
    expanded.value = !expanded.value
  } else if (props.node.articleId) {
    emit('select', props.node)
  }
}
</script>

<style scoped lang="scss">
.node-row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  cursor: pointer;
  border-radius: 6px;
  margin: 1px 0.5rem;
  transition: background 0.15s, color 0.15s;
  font-size: 0.825rem;
  color: $color-text-muted;

  &.level-2 { padding-left: 1.5rem; }
  &.level-3 { padding-left: 2.25rem; }
  &.level-4 { padding-left: 3rem; }

  &.is-folder {
    font-weight: 600;
    color: $color-text;
    font-size: 0.85rem;
    .dark & { color: $color-dark-text; }
  }

  &.is-article:hover {
    background: #f0f7ff;
    color: $color-primary;
    .dark & { background: rgba(59, 130, 246, 0.08); }
  }

  &.is-active {
    background: rgba(59, 130, 246, 0.1);
    color: $color-primary;
    font-weight: 500;
    .dark & { background: rgba(59, 130, 246, 0.15); color: #60a5fa; }
  }

  .dark & { color: $color-dark-text-muted; }
}

.expand-btn {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  color: inherit;
  flex-shrink: 0;
}

.expand-icon {
  transition: transform 0.2s ease;
  &.rotated { transform: rotate(90deg); }
}

.node-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.4;
  flex-shrink: 0;
  margin: 0 6.5px;
}

.node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  line-height: 1.4;
}

.node-children {
  /* 子节点无额外缩进，由 level 类控制 */
}
</style>
