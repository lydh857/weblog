<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="admin-aside">
      <div class="logo" @click="navigateTo('/')">
        <div class="logo-icon">
          <img src="/brand/logo.png" alt="zhhhkl logo" class="logo-icon-img app-brand-logo">
        </div>
        <Transition name="fade">
          <span v-if="!isCollapsed" class="logo-text">zhhhkl</span>
        </Transition>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        :background-color="'transparent'"
        :text-color="isDark ? '#8b949e' : '#5a6d82'"
        :active-text-color="isDark ? '#7ba4f2' : '#5b8def'"
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
          <template #title>友链管理</template>
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
              <el-badge
                v-if="pendingProfileReviewCount > 0"
                :value="pendingProfileReviewCount > 99 ? '99+' : pendingProfileReviewCount"
                type="danger"
                class="menu-review-badge"
              />
            </span>
          </template>
        </el-menu-item>
        <el-menu-item index="/advertisement">
          <el-icon><Promotion /></el-icon>
          <template #title>广告管理</template>
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
  SwitchButton, Fold, Expand, List, Collection, MagicStick, DataLine,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '~/stores/user'
import { useDarkMode } from '~/composables/useDarkMode'
import { authApi } from '~/api/auth'
import { profileReviewApi } from '~/api/profileReview'

const route = useRoute()
const userStore = useUserStore()
const { isDark, toggleDark } = useDarkMode()
const isCollapsed = ref(false)
const pendingProfileReviewCount = useState<number>('pendingProfileReviewCount', () => 0)

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

async function handleCommand(command: string) {
  if (command === 'portal') {
    window.open('/', '_blank', 'noopener,noreferrer')
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
  if (path.startsWith('/user')) {
    loadPendingReviewCount()
  }
})

onMounted(() => {
  loadPendingReviewCount()
})
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
}

// ===== 侧边栏 =====
.admin-aside {
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              background-color 0.3s ease,
              border-color 0.3s ease;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  cursor: pointer;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
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
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  transition: color 0.3s ease;
}

.menu-title-with-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.menu-review-badge {
  :deep(.el-badge__content) {
    transform: scale(0.92);
  }
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
  border-right: none;
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

// 菜单项样式
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

:deep(.el-menu-item.is-active) {
  background: rgba(91, 141, 239, 0.1);
  color: #5b8def;
  font-weight: 500;

  .dark & {
    background: rgba(123, 164, 242, 0.12);
    color: #7ba4f2;
  }
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--el-fill-color-light) !important;
}

// 折叠状态下菜单项居中
:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 !important;
  justify-content: center;
}

:deep(.el-menu--collapse .el-menu-item .el-icon),
:deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin-right: 0 !important;
}

// 子菜单缩进
:deep(.el-sub-menu .el-menu-item) {
  padding-left: 48px !important;
  min-width: auto;
}

// ===== 头部 =====
.admin-header {
  height: 56px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
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
  gap: 4px;
}

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  outline: none;
  transition: background-color 0.15s ease;

  &:hover {
    background: var(--el-fill-color-light);
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
  background: var(--el-fill-color-blank);
  overflow-y: auto;
  transition: background-color 0.3s ease;

  .dark & {
    background: var(--el-bg-color-page);
  }
}
</style>
