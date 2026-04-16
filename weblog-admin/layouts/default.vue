<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" :class="{ 'is-collapsed': isCollapsed }" class="admin-aside">
      <div class="logo" @click="navigateTo('/')">
        <div class="logo-icon">
          <img src="/brand/logo.png" alt="zhhhkl logo" class="logo-icon-img app-brand-logo">
        </div>
        <Transition name="fade">
          <span v-if="!isCollapsed" class="logo-text">zhhhkl</span>
        </Transition>
      </div>
      <el-menu
        class="admin-sidebar-menu"
        :class="{ 'is-collapsed': isCollapsed }"
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        :background-color="'transparent'"
        :text-color="'var(--admin-aside-text)'"
        :active-text-color="'var(--el-color-primary)'"
        router
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-sub-menu index="/content">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>内容管理</span>
          </template>
          <el-menu-item index="/post">
            <el-icon><List /></el-icon>
            <span>文章管理</span>
          </el-menu-item>
          <el-menu-item index="/category">
            <el-icon><Folder /></el-icon>
            <span>分类管理</span>
          </el-menu-item>
          <el-menu-item index="/tag">
            <el-icon><PriceTag /></el-icon>
            <span>标签管理</span>
          </el-menu-item>
          <el-menu-item index="/topic">
            <el-icon><Collection /></el-icon>
            <span>专题管理</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/carousel">
          <el-icon><Film /></el-icon>
          <template #title>轮播管理</template>
        </el-menu-item>
        <el-menu-item index="/media">
          <el-icon><Picture /></el-icon>
          <template #title>媒体管理</template>
        </el-menu-item>
        <el-menu-item index="/friend-link">
          <el-icon><Link /></el-icon>
          <template #title>
            <span class="menu-title-with-badge">
              友链管理
              <span v-if="pendingFriendLinkCount > 0" class="menu-count-badge menu-count-badge--warning">
                {{ pendingFriendLinkCount > 99 ? '99+' : pendingFriendLinkCount }}
              </span>
            </span>
          </template>
        </el-menu-item>
        <el-menu-item index="/comment">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>评论管理</template>
        </el-menu-item>
        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <template #title>
            <span class="menu-title-with-badge">
              用户管理
              <span v-if="pendingProfileReviewCount > 0" class="menu-count-badge menu-count-badge--warning">
                {{ pendingProfileReviewCount > 99 ? '99+' : pendingProfileReviewCount }}
              </span>
            </span>
          </template>
        </el-menu-item>
        <el-menu-item index="/advertisement">
          <el-icon><Promotion /></el-icon>
          <template #title>
            <span class="menu-title-with-badge">
              广告管理
              <span v-if="pendingAdvertisementCount > 0" class="menu-count-badge menu-count-badge--warning">
                {{ pendingAdvertisementCount > 99 ? '99+' : pendingAdvertisementCount }}
              </span>
            </span>
          </template>
        </el-menu-item>
        <el-menu-item index="/announcement">
          <el-icon><Bell /></el-icon>
          <template #title>公告管理</template>
        </el-menu-item>
        <el-menu-item index="/ai-config">
          <el-icon><MagicStick /></el-icon>
          <template #title>AI 配置</template>
        </el-menu-item>
        <el-menu-item index="/system-config">
          <el-icon><Setting /></el-icon>
          <template #title>系统配置</template>
        </el-menu-item>
        <el-menu-item index="/rate-limit">
          <el-icon><Timer /></el-icon>
          <template #title>限流与风控</template>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><List /></el-icon>
          <template #title>日志中心</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button text circle @click="isCollapsed = !isCollapsed">
            <el-icon :size="18">
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
          </el-button>
        </div>
        <div class="header-right">
          <el-button text circle @click="toggleDark()">
            <el-icon :size="18">
              <Sunny v-if="isDark" />
              <Moon v-else />
            </el-icon>
          </el-button>
          <el-dropdown trigger="hover" @command="handleCommand">
            <div class="user-dropdown-trigger">
              <el-avatar :size="32" :src="userStore.userInfo.avatar || undefined">
                <el-icon :size="18"><User /></el-icon>
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo.nickname || '管理员' }}</span>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="portal">
                  <el-icon><Monitor /></el-icon>前往用户端
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <TabBar />
      <el-main class="admin-main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {
  HomeFilled, Document, Folder, PriceTag, Picture, Film,
  Link, ChatDotRound, User, Promotion, Bell,
  Sunny, Moon, Setting, ArrowDown, Monitor,
  SwitchButton, Fold, Expand, List, Collection, MagicStick, Timer,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '~/stores/user'
import { useDarkMode } from '~/composables/theme/useDarkMode'
import { authApi } from '~/api/auth/auth'
import { profileReviewApi } from '~/api/system/profileReview'
import { friendLinkApi } from '~/api/content/friendLink'
import { advertisementApi } from '~/api/marketing/advertisement'

const route = useRoute()
const runtimeConfig = useRuntimeConfig()
const userStore = useUserStore()
const { isDark, toggleDark } = useDarkMode()
const isCollapsed = ref(false)
const pendingProfileReviewCount = useState<number>('pendingProfileReviewCount', () => 0)
const pendingAdvertisementCount = useState<number>('pendingAdvertisementCount', () => 0)
const pendingFriendLinkCount = useState<number>('pendingFriendLinkCount', () => 0)

function getPortalTargetUrl() {
  const configured = String(runtimeConfig.public.portalBaseUrl || '').trim()
  if (configured) {
    return configured
  }

  if (import.meta.client) {
    const { protocol, hostname, port } = window.location
    if ((hostname === 'localhost' || hostname === '127.0.0.1') && port === '3001') {
      return `${protocol}//${hostname}:3000`
    }
  }

  return '/'
}

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/post/create')) return '/post'
  if (path.startsWith('/profile-review') || path.startsWith('/avatar-review')) return '/user'
  if (path === '/category' || path === '/tag') return path
  return path
})

async function loadPendingReviewCount() {
  try {
    const res = await profileReviewApi.page({ pageNum: 1, pageSize: 1 })
    pendingProfileReviewCount.value = res.data.total
  } catch {
    pendingProfileReviewCount.value = 0
  }
}

async function loadPendingAdvertisementCount() {
  try {
    const res = await advertisementApi.list({ pageNum: 1, pageSize: 1, status: 'pending' })
    pendingAdvertisementCount.value = res.data.total
  } catch {
    pendingAdvertisementCount.value = 0
  }
}

async function loadPendingFriendLinkCount() {
  try {
    const res = await friendLinkApi.listAll()
    pendingFriendLinkCount.value = res.data.filter(item => item.status === 'pending').length
  } catch {
    pendingFriendLinkCount.value = 0
  }
}

async function loadMenuBadges() {
  await Promise.all([
    loadPendingReviewCount(),
    loadPendingAdvertisementCount(),
    loadPendingFriendLinkCount(),
  ])
}

async function handleCommand(command: string) {
  if (command === 'portal') {
    window.open(getPortalTargetUrl(), '_blank', 'noopener,noreferrer')
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    try {
      await authApi.logout()
    } catch { /* ignore */ }

    // 清除本地会话痕迹（兼容旧 key）
    localStorage.removeItem('weblog_admin_remember')
    localStorage.removeItem('remember-me')

    userStore.clearUser()
    navigateTo('/login')
  }
}

watch(() => route.path, (path) => {
  if (path.startsWith('/user') || path.startsWith('/advertisement') || path.startsWith('/friend-link')) {
    loadMenuBadges()
  }
})

onMounted(() => {
  loadMenuBadges()
})
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  background: var(--el-bg-color-page);
}

// ===== 侧边栏 =====
.admin-aside {
  background: var(--admin-menu-bg);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  transition: width 0.18s cubic-bezier(0.4, 0, 0.2, 1),
    background-color 0.3s ease,
    border-color 0.3s ease;
  will-change: width;
  overflow: hidden;
}

.logo {
  height: 54px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  cursor: pointer;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  overflow: hidden;
  transition: border-color 0.3s ease;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.logo-icon-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  letter-spacing: 0.2px;
  transition: color 0.3s ease;
}

.menu-title-with-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.menu-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
  color: #fff;
}

.menu-count-badge--warning {
  background: var(--el-color-warning);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.el-menu {
  background: var(--admin-menu-bg);
  border-right: none;
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px;
}

// 菜单项样式
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  position: relative;
  padding-left: 44px !important;
  color: var(--admin-aside-text);
  border-radius: 8px;
  margin-bottom: 4px;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  font-weight: 500;
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

:deep(.el-menu-item > .el-icon),
:deep(.el-sub-menu__title > .el-icon:not(.el-sub-menu__icon-arrow)) {
  position: absolute;
  left: 24px;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
  margin: 0 !important;
  color: var(--admin-aside-text-muted);
  transition: color 0.2s ease;
}

:deep(.el-menu-item.is-active) {
  background: var(--admin-menu-active-bg);
  color: var(--admin-aside-text-active);
  border: none;
  box-shadow: none;
  font-weight: 500;
}

:deep(.el-menu-item.is-active > .el-icon),
:deep(.el-sub-menu.is-opened > .el-sub-menu__title > .el-icon:not(.el-sub-menu__icon-arrow)) {
  color: var(--admin-aside-text-active);
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--admin-menu-hover-bg) !important;
  color: var(--admin-aside-text-active) !important;
}

:deep(.el-menu-item:focus),
:deep(.el-menu-item:focus-visible),
:deep(.el-menu-item:active),
:deep(.el-sub-menu__title:focus),
:deep(.el-sub-menu__title:focus-visible),
:deep(.el-sub-menu__title:active) {
  outline: none;
  border: none;
  box-shadow: none;
}

:deep(.el-menu-item:hover > .el-icon),
:deep(.el-sub-menu__title:hover > .el-icon:not(.el-sub-menu__icon-arrow)) {
  color: var(--admin-aside-text-active) !important;
}

:deep(.el-sub-menu .el-menu) {
  background: transparent;
}

:deep(.el-menu--popup-container) {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  overflow: hidden;
}

:deep(.el-menu--popup) {
  padding: 6px;
  background: var(--admin-surface-1);
}

:deep(.el-sub-menu .el-menu-item) {
  margin-bottom: 2px;
  font-weight: 500;
}

// 折叠状态下顶层菜单项居中
:deep(.admin-sidebar-menu.is-collapsed > .el-menu-item),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger > .el-sub-menu__title) {
  padding: 0 !important;
  width: 48px;
  margin-left: auto;
  margin-right: auto;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.admin-sidebar-menu.is-collapsed > .el-menu-item > .el-menu-tooltip__trigger),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger) {
  width: 48px;
  margin-left: auto;
  margin-right: auto;
  display: block;
}

:deep(.admin-sidebar-menu.is-collapsed > .el-menu-item > .el-menu-tooltip__trigger),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger > .el-sub-menu__title) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.admin-sidebar-menu.is-collapsed > .el-menu-item .el-icon),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title > .el-icon:not(.el-sub-menu__icon-arrow)),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger > .el-sub-menu__title > .el-icon:not(.el-sub-menu__icon-arrow)) {
  position: static !important;
  left: auto !important;
  top: auto !important;
  transform: none !important;
  margin: 0 !important;
}

:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title > .el-sub-menu__icon-arrow),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger > .el-sub-menu__title > .el-sub-menu__icon-arrow) {
  display: none !important;
}

:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-sub-menu__title > span),
:deep(.admin-sidebar-menu.is-collapsed > .el-sub-menu > .el-tooltip__trigger > .el-sub-menu__title > span),
:deep(.admin-sidebar-menu.is-collapsed > .el-menu-item > span) {
  display: none !important;
}

// 子菜单缩进
:deep(.admin-sidebar-menu:not(.is-collapsed) .el-sub-menu .el-menu-item) {
  padding-left: 72px !important;
  min-width: auto;
}

:deep(.admin-sidebar-menu:not(.is-collapsed) .el-sub-menu .el-menu-item > .el-icon) {
  left: 40px;
}

// ===== 头部 =====
.admin-header {
  height: 56px;
  background: var(--admin-topbar-bg);
  border-bottom: 1px solid var(--admin-topbar-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 8px;
  outline: none;
  transition: background-color 0.15s ease;

  &:hover {
    background: var(--admin-menu-hover-bg);
  }
}

.user-name {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.dropdown-arrow {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

// ===== 主内容区 =====
.admin-main {
  background: var(--el-bg-color-page);
  overflow-y: auto;
  transition: background-color 0.3s ease;
}
</style>
