import { defineStore } from 'pinia'

export interface TabItem {
  path: string
  title: string
}

// 路径 → 标签标题映射
const TAB_TITLE_MAP: Record<string, string> = {
  '/': '仪表盘',
  '/post': '文章管理',
  '/post/create': '写文章',
  '/category': '分类管理',
  '/tag': '标签管理',
  '/media': '媒体管理',
  '/profile-review': '个人信息审核',
  '/avatar-review': '个人信息审核',
  '/friend-link': '友链管理',
  '/carousel': '轮播管理',
  '/comment': '评论管理',
  '/user': '用户管理',
  '/advertisement': '广告管理',
  '/announcement': '公告管理',
  '/topic': '专题管理',
  '/ai-config': 'AI 配置',
  '/system-config': '系统配置',
}

// 固定首页标签，不可关闭
const HOME_TAB: TabItem = { path: '/', title: '仪表盘' }

const STORAGE_KEY = 'weblog-admin-tabs'

function loadTabs(): TabItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const tabs = JSON.parse(raw) as TabItem[]
      // 确保首页标签始终存在
      if (!tabs.some(t => t.path === '/')) {
        tabs.unshift(HOME_TAB)
      }
      return tabs
    }
  } catch { /* ignore */ }
  return [HOME_TAB]
}

function saveTabs(tabs: TabItem[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tabs))
}

export function getTabTitle(path: string): string {
  return TAB_TITLE_MAP[path] || '未知页面'
}

export const useTabBarStore = defineStore('tabBar', () => {
  const tabs = ref<TabItem[]>(loadTabs())
  const activeTab = ref('/')

  // 添加标签
  function addTab(path: string) {
    // 登录页不加标签
    if (path === '/login') return
    activeTab.value = path
    if (!tabs.value.some(t => t.path === path)) {
      tabs.value.push({ path, title: getTabTitle(path) })
      saveTabs(tabs.value)
    }
  }

  // 关闭标签
  function closeTab(path: string) {
    // 首页不可关闭
    if (path === '/') return
    const idx = tabs.value.findIndex(t => t.path === path)
    if (idx === -1) return
    tabs.value.splice(idx, 1)
    saveTabs(tabs.value)
    // 如果关闭的是当前激活标签，跳转到前一个
    if (activeTab.value === path) {
      const newIdx = Math.min(idx, tabs.value.length - 1)
      activeTab.value = tabs.value[newIdx].path
      navigateTo(activeTab.value)
    }
  }

  // 关闭其他标签
  function closeOthers(path: string) {
    tabs.value = tabs.value.filter(t => t.path === '/' || t.path === path)
    saveTabs(tabs.value)
    activeTab.value = path
    navigateTo(path)
  }

  // 关闭右侧标签
  function closeRight(path: string) {
    const idx = tabs.value.findIndex(t => t.path === path)
    if (idx === -1) return
    tabs.value = tabs.value.slice(0, idx + 1)
    saveTabs(tabs.value)
    // 如果当前激活标签被关闭了，跳转到目标标签
    if (!tabs.value.some(t => t.path === activeTab.value)) {
      activeTab.value = path
      navigateTo(path)
    }
  }

  // 关闭所有标签（保留首页）
  function closeAll() {
    tabs.value = [HOME_TAB]
    saveTabs(tabs.value)
    activeTab.value = '/'
    navigateTo('/')
  }

  return { tabs, activeTab, addTab, closeTab, closeOthers, closeRight, closeAll }
})
