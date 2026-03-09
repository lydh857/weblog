<template>
  <div class="tab-bar">
    <div class="tab-bar-scroll" ref="scrollRef">
      <div
        v-for="tab in tabStore.tabs"
        :key="tab.path"
        :class="['tab-item', { active: tabStore.activeTab === tab.path }]"
        @click="handleClick(tab.path)"
        @contextmenu.prevent="openContextMenu($event, tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <span
          v-if="tab.path !== '/'"
          class="tab-close"
          @click.stop="tabStore.closeTab(tab.path)"
        >&times;</span>
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
  height: 38px;
  background: var(--el-bg-color);
  border-bottom: 1px solid $color-border-light;
  display: flex;
  align-items: center;
  padding: 0 8px;
  user-select: none;
  flex-shrink: 0;
  transition: background-color 0.3s ease, border-color 0.3s ease;
  .dark & {
    border-bottom-color: $color-border-dark;
  }
}

.tab-bar-scroll {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  // 隐藏滚动条但保留滚动功能
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background-color 0.15s, color 0.15s;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
    .dark & { background: rgba(255, 255, 255, 0.06); }
  }

  &.active {
    background: #5b8def;
    color: #fff;
    .dark & {
      background: #7ba4f2;
      color: #fff;
    }
    .tab-close {
      color: rgba(255, 255, 255, 0.7);
      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}

.tab-title {
  line-height: 1;
}

.tab-close {
  font-size: 14px;
  line-height: 1;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--el-text-color-placeholder);
  transition: background-color 0.15s, color 0.15s;
  &:hover {
    background: rgba(0, 0, 0, 0.08);
    color: var(--el-text-color-primary);
    .dark & {
      background: rgba(255, 255, 255, 0.12);
    }
  }
}

// 右键菜单
.tab-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 4px 0;
  min-width: 120px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.ctx-item {
  padding: 6px 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: background-color 0.15s;
  &:hover {
    background: rgba(91, 141, 239, 0.08);
    color: #5b8def;
    .dark & {
      background: rgba(123, 164, 242, 0.1);
      color: #7ba4f2;
    }
  }
}
</style>
