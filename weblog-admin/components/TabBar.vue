<template>
  <div class="tab-bar">
    <div class="tab-bar-scroll" ref="scrollRef">
      <div
        v-for="tab in tabStore.tabs"
        :key="tab.path"
        :class="['tab-item', { active: tabStore.activeTab === tab.path, closable: tab.path !== '/' }]"
        @click="handleClick(tab.path)"
        @contextmenu.prevent="openContextMenu($event, tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span
          v-if="tab.path !== '/'"
          class="tab-close"
          aria-label="关闭标签"
          @click.stop="tabStore.closeTab(tab.path)"
        >
          <el-icon><Close /></el-icon>
        </span>
      </div>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="tab-context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div class="ctx-item" @click="handleRefresh">刷新</div>
        <div
          v-if="contextMenu.tab?.path !== '/'"
          class="ctx-item"
          @click="handleCtxClose"
        >关闭</div>
        <div class="ctx-item" @click="handleCtxCloseOthers">关闭其他</div>
        <div class="ctx-item" @click="handleCtxCloseRight">关闭右侧</div>
        <div class="ctx-item" @click="handleCtxCloseAll">关闭所有</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import { useTabBarStore, type TabItem } from '~/stores/tabBar'

const tabStore = useTabBarStore()
const route = useRoute()
const scrollRef = ref<HTMLElement>()

// 右键菜单状态
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  tab: null as TabItem | null,
})

// 路由变化时自动添加标签
watch(() => route.path, (path) => {
  tabStore.addTab(path)
}, { immediate: true })

function handleClick(path: string) {
  if (path !== route.path) {
    navigateTo(path)
  }
}

function openContextMenu(e: MouseEvent, tab: TabItem) {
  contextMenu.visible = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.tab = tab
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.tab = null
}

function handleRefresh() {
  const path = contextMenu.tab?.path
  closeContextMenu()
  if (path) {
    // 通过临时导航实现刷新
    const currentPath = route.path
    if (path === currentPath) {
      // 利用 replace 重新加载当前页面
      navigateTo(path, { replace: true })
      // 强制重新渲染：通过 key 变化（由布局处理）
      refreshNuxtData()
    } else {
      navigateTo(path)
    }
  }
}

function handleCtxClose() {
  if (contextMenu.tab) tabStore.closeTab(contextMenu.tab.path)
  closeContextMenu()
}

function handleCtxCloseOthers() {
  if (contextMenu.tab) tabStore.closeOthers(contextMenu.tab.path)
  closeContextMenu()
}

function handleCtxCloseRight() {
  if (contextMenu.tab) tabStore.closeRight(contextMenu.tab.path)
  closeContextMenu()
}

function handleCtxCloseAll() {
  tabStore.closeAll()
  closeContextMenu()
}

// 点击其他区域关闭右键菜单
onMounted(() => {
  document.addEventListener('click', closeContextMenu)
})
onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
})
</script>

<style scoped lang="scss">
.tab-bar {
  height: 34px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  align-items: center;
  padding: 0 6px;
  user-select: none;
  flex-shrink: 0;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}

.tab-bar-scroll {
  display: flex;
  align-items: center;
  gap: 3px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  // 隐藏滚动条但保留滚动功能
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
}

.tab-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 8px;
  height: 24px;
  border-radius: 5px;
  border: 1px solid transparent;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;

  &:hover {
    background: var(--admin-primary-soft);
    border-color: var(--el-color-primary-light-8);
    color: var(--el-color-primary);
  }

  &.active {
    background: var(--admin-primary-soft-hover);
    border-color: var(--el-color-primary-light-7);
    color: var(--el-color-primary);
    font-weight: 600;

    .tab-close {
      border-color: var(--el-color-primary-light-7);
      color: var(--el-color-primary);

      &:hover {
        color: var(--el-color-danger);
        background: var(--el-color-danger-light-9);
        border-color: var(--el-color-danger-light-5);
      }
    }
  }
}

.tab-item.closable {
  padding-right: 10px;
}

.tab-title {
  line-height: 1;
}

.tab-close {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-1px) scale(0.86);
  pointer-events: none;
  z-index: 5;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;

  :deep(.el-icon) {
    font-size: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &:hover {
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-5);
    color: var(--el-color-danger);
    transform: scale(1);
  }
}

.tab-item:hover .tab-close {
  opacity: 1;
  visibility: visible;
  transform: scale(1);
  pointer-events: auto;
}

// 右键菜单
.tab-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 4px 0;
  min-width: 108px;
  box-shadow: none;
}

.ctx-item {
  padding: 5px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover {
    background: var(--admin-primary-soft);
    color: var(--el-color-primary);
  }
}
</style>
