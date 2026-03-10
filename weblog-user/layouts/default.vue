<template>
  <div class="layout" :class="{ dark: isDark }">
    <header class="navbar" :class="{ 'navbar--transparent': isHomePage && !isScrolled, 'navbar--hidden': isNavHidden }">
      <div class="nav-inner">
        <NuxtLink to="/" class="nav-logo" :class="{ 'animate-nav-item': shouldAnimate }" :style="shouldAnimate ? '--delay: 0.05s' : ''">
          <span class="logo-text">zhhhkl</span>
        </NuxtLink>
        <div class="nav-right">
          <nav class="nav-links">
            <NuxtLink
              to="/"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.15s' : ''"
            >首页</NuxtLink>
            <NuxtLink
              to="/category"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.25s' : ''"
            >分类</NuxtLink>
            <NuxtLink
              to="/topic"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.35s' : ''"
            >专题</NuxtLink>
            <NuxtLink
              to="/tags"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.45s' : ''"
            >标签</NuxtLink>
            <NuxtLink
              to="/ranking"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.55s' : ''"
            >排行</NuxtLink>
            <NuxtLink
              to="/friend-links"
              class="nav-link"
              :class="{ 'animate-nav-item': shouldAnimate }"
              :style="shouldAnimate ? '--delay: 0.65s' : ''"
            >友链</NuxtLink>
          </nav>
          <div class="nav-actions" :class="{ 'animate-nav-item': shouldAnimate }" :style="shouldAnimate ? '--delay: 0.75s' : ''">
            <button class="icon-btn" aria-label="搜索" @click="goSearch">
              <Icon name="heroicons:magnifying-glass-20-solid" size="20" />
            </button>
            <button class="icon-btn" aria-label="切换主题" @click="toggleDark()">
              <Icon :name="isDark ? 'heroicons:sun-20-solid' : 'heroicons:moon-20-solid'" size="20" />
            </button>
            <div class="nav-auth-slot">
              <button v-if="authReady && showLoggedIn" key="nav-avatar" type="button" class="avatar-btn" @mouseenter="showUserMenu = true">
                <img v-if="displayAvatar && !avatarLoadFailed" :src="displayAvatar" alt="头像" class="user-avatar" @error="onAvatarError" />
                <span v-else class="user-avatar-placeholder">{{ displayNickname.charAt(0) }}</span>
              </button>
              <button v-else-if="authReady" key="nav-login" type="button" class="login-btn" @click="openLogin">登录</button>
              <span v-else class="nav-auth-placeholder" aria-hidden="true" />
            </div>
            <!-- 用户菜单 -->
            <div v-if="showUserMenu" class="user-menu" @mouseleave="showUserMenu = false" @click="showUserMenu = false">
              <NuxtLink to="/user" class="menu-item">
                <Icon name="heroicons:user-circle-16-solid" size="16" /> 个人中心
              </NuxtLink>
              <NuxtLink to="/user/likes" class="menu-item">
                <Icon name="heroicons:bookmark-16-solid" size="16" /> 我的收藏
              </NuxtLink>
              <NuxtLink to="/user/comments" class="menu-item">
                <Icon name="heroicons:chat-bubble-left-16-solid" size="16" /> 我的评论
              </NuxtLink>
              <button class="menu-item logout" @click="handleLogout">
                <Icon name="heroicons:arrow-right-start-on-rectangle-16-solid" size="16" /> 退出登录
              </button>
            </div>
          </div>
        </div>
        <!-- 移动端菜单按钮 -->
        <button class="mobile-menu-btn" aria-label="菜单" @click="mobileMenuOpen = !mobileMenuOpen">
          <Icon :name="mobileMenuOpen ? 'heroicons:x-mark-20-solid' : 'heroicons:bars-3-20-solid'" size="24" />
        </button>
      </div>
      <!-- 移动端菜单 -->
      <div v-if="mobileMenuOpen" class="mobile-menu" @click="mobileMenuOpen = false">
        <NuxtLink to="/" class="mobile-link">首页</NuxtLink>
        <NuxtLink to="/category" class="mobile-link">分类</NuxtLink>
        <NuxtLink to="/topic" class="mobile-link">专题</NuxtLink>
        <NuxtLink to="/tags" class="mobile-link">标签</NuxtLink>
        <NuxtLink to="/ranking" class="mobile-link">排行</NuxtLink>
        <NuxtLink to="/friend-links" class="mobile-link">友链</NuxtLink>
        <button class="mobile-link" @click="searchModal.open()">搜索</button>
        <template v-if="showLoggedIn">
          <NuxtLink to="/user" class="mobile-link">个人中心</NuxtLink>
          <button class="mobile-link logout-link" @click="handleLogout">退出登录</button>
        </template>
        <button v-else class="mobile-link" @click="openLogin">登录</button>
      </div>
    </header>
    <!-- 搜索模态框 -->
    <SearchModal v-model:visible="searchModal.isVisible.value" />
    <AnnouncementBanner type="banner" :nav-hidden="isNavHidden" :transparent="isHomePage && !isScrolled" />
    <main class="main-content" :class="{ 'has-announcement': announcementBarVisible && !isHomePage }">
      <slot />
    </main>
    <AnnouncementPopup />
    <footer class="site-footer">
      <p>&copy; {{ new Date().getFullYear() }} zhhhkl. All rights reserved.</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useDarkMode } from '~/composables/useDarkMode'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { useSearchModal } from '~/composables/useSearchModal'
import { useNavScrollLock } from '~/composables/useNavScrollLock'

const { bannerVisible: announcementBarVisible } = useAnnouncementBar()

const { isDark, toggleDark } = useDarkMode()
const userStore = useUserStore()
const searchModal = useSearchModal()
const router = useRouter()
const route = useRoute()
const mobileMenuOpen = ref(false)
const showUserMenu = ref(false)
const message = useMessage()
const { confirm } = useConfirm()
const loginModal = useLoginModal()
const { locked: navScrollLocked } = useNavScrollLock()

// ===== 导航栏滚动状态 =====
const isScrolled = ref(false)
const isNavHidden = ref(false)
const lastScrollY = ref(0)
const isHomePage = computed(() => route.path === '/')

// ===== 首页入场动画 =====
const shouldAnimate = ref(false)

// 进入首页时，等 DOM 渲染完成后再触发动画，确保用户能看到
watch(isHomePage, (val) => {
  if (val) {
    shouldAnimate.value = false
    nextTick(() => {
      requestAnimationFrame(() => {
        shouldAnimate.value = true
      })
    })
  } else {
    shouldAnimate.value = false
  }
}, { immediate: true })

function handleScroll() {
  const scrollY = window.scrollY
  isScrolled.value = scrollY > 20

  // 评论区 DOM 操作期间跳过方向判断，只同步 lastScrollY
  if (navScrollLocked.value) {
    lastScrollY.value = scrollY
    return
  }

  const heroEl = document.querySelector('.hero-carousel')
  const heroBottom = heroEl ? heroEl.getBoundingClientRect().height : 0
  if (scrollY > heroBottom) {
    isNavHidden.value = scrollY > lastScrollY.value
  } else {
    isNavHidden.value = false
  }
  lastScrollY.value = scrollY
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

const authReady = ref(false)
onMounted(async () => {
  await userStore.fetchUser()
  authReady.value = true
})

const showLoggedIn = computed(() => userStore.isLoggedIn)

const avatarLoadFailed = ref(false)

const avatarLoadFailed = ref(false)

const displayAvatar = computed(() => {
  const raw = authReady.value ? (userStore.userInfo?.avatar || '') : ''
  const value = typeof raw === 'string' ? raw.trim() : ''
  if (!value || value === 'null' || value === 'undefined') return ''
  return value
})
const displayNickname = computed(() => {
  if (authReady.value) return userStore.userInfo?.nickname || 'U'
  return 'U'
})

watch(displayAvatar, () => {
  avatarLoadFailed.value = false
})

function onAvatarError() {
  avatarLoadFailed.value = true
}

function goSearch() {
  searchModal.open()
}

function openLogin() {
  loginModal.open()
}

async function handleLogout() {
  const ok = await confirm({ title: '退出登录', message: '确定要退出登录吗？', type: 'warning', confirmText: '退出' })
  if (!ok) return

  try {
    await authApi.logout()
  } catch {}

  // 清除记住我相关数据，防止自动重新登录
  localStorage.removeItem('weblog_user_remember')
  localStorage.removeItem('remember_credentials')
  localStorage.removeItem('remember_token')

  userStore.clearUser()
  message.success('已退出登录')
  // 保持在当前页面，不强制跳转
}
</script>

<style lang="scss">
/* ===== 导航栏入场动画 ===== */
.animate-nav-item {
  opacity: 0;
  transform: translateY(-20px);
  animation: navFadeInDown 0.6s ease forwards;
  animation-delay: var(--delay, 0s);
}

@keyframes navFadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 导航栏 ===== */
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  box-sizing: border-box;
  padding-right: var(--scrollbar-width, 0px);
  z-index: 100;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid $color-border;
  transition: transform 0.3s ease, background 0.3s ease, border-color 0.3s ease;
  .dark & {
    background: rgba(15, 23, 42, 0.85);
    border-bottom-color: $color-dark-border;
  }
  &--transparent {
    background: transparent;
    backdrop-filter: none;
    border-bottom-color: transparent;
    .dark & {
      background: transparent;
      border-bottom-color: transparent;
    }
    .nav-logo .logo-text { color: #fff; }
    .nav-link { color: rgba(255, 255, 255, 0.85); &:hover, &.router-link-active { color: #fff; background: rgba(255, 255, 255, 0.12); } }
    .icon-btn { color: rgba(255, 255, 255, 0.85); &:hover { color: #fff; background: rgba(255, 255, 255, 0.12); } }
    .login-btn { background: rgba(255, 255, 255, 0.15); backdrop-filter: blur(4px); &:hover { background: rgba(255, 255, 255, 0.25); } }
    .mobile-menu-btn { color: #fff; }
  }
  &--hidden {
    transform: translateY(-100%);
  }
}

.nav-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* ===== Logo 艺术字体（通过 nuxt.config.ts head 预加载） ===== */

.nav-logo {
  text-decoration: none;
  flex-shrink: 0;
}

.logo-text {
  font-family: 'Pacifico', cursive;
  font-size: 1.6rem;
  color: $color-primary;
  letter-spacing: 1px;
  transition: color 0.2s;
}

/* ===== 右侧导航区域 ===== */
.nav-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  @media (max-width: $breakpoint-md) { display: none; }
}

.nav-links {
  display: flex;
  gap: 0.25rem;
}

.nav-link {
  padding: 0.375rem 0.75rem;
  border-radius: $radius-md;
  font-size: 0.9rem;
  color: $color-text-muted;
  text-decoration: none;
  transition: color 0.2s, background 0.2s;
  &:hover, &.router-link-active {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.08);
  }
  .dark & {
    color: #94a3b8;
    &:hover, &.router-link-active { color: $color-primary; background: rgba(59, 130, 246, 0.15); }
  }
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: 0.5rem;
  position: relative;
}

.nav-auth-slot {
  min-width: 52px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.nav-auth-placeholder {
  width: 52px;
  height: 44px;
  display: inline-block;
}

.icon-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: $radius-md;
  background: transparent;
  color: $color-text-muted;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
  &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.08); }
  .dark & { color: #94a3b8; &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.15); } }
}

.login-btn {
  padding: 0.5rem 1rem;
  border-radius: $radius-md;
  background: $color-primary;
  color: #fff;
  font-size: 0.85rem;
  font-weight: 500;
  text-decoration: none;
  transition: background 0.2s;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  border: none;
  cursor: pointer;
  &:hover { background: $color-primary-dark; }
}

.avatar-btn {
  border: none;
  background: transparent !important;
  cursor: pointer;
  padding: 0;
  min-width: 32px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  outline: none;
  &:focus-visible {
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
    border-radius: 999px;
  }
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.user-avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $color-primary;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 600;
}

.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 0.5rem;
  min-width: 160px;
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  z-index: 200;
  padding: 0.375rem 0;
  .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3); }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem 1rem;
  font-size: 0.85rem;
  color: $color-text;
  text-decoration: none;
  border: none;
  background: none;
  cursor: pointer;
  transition: background 0.15s;
  &:hover { background: rgba(59, 130, 246, 0.06); }
  &.logout { color: #ef4444; &:hover { background: rgba(239, 68, 68, 0.06); } }
  .dark & { color: $color-dark-text; &.logout { color: #f87171; } }
}

/* ===== 移动端 ===== */
.mobile-menu-btn {
  display: none;
  border: none;
  background: none;
  color: $color-text;
  cursor: pointer;
  padding: 0.5rem;
  min-width: 44px;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  .dark & { color: $color-dark-text; }
  @media (max-width: $breakpoint-md) { display: flex; }
}

.mobile-menu {
  display: none;
  flex-direction: column;
  padding: 0.5rem 1.5rem 1rem;
  border-top: 1px solid $color-border;
  .dark & { border-top-color: $color-dark-border; }
  @media (max-width: $breakpoint-md) { display: flex; }
}

.mobile-link {
  padding: 0.75rem 0;
  font-size: 0.95rem;
  color: $color-text;
  text-decoration: none;
  border: none;
  border-bottom: 1px solid $color-border;
  background: none;
  text-align: left;
  cursor: pointer;
  width: 100%;
  .dark & { color: $color-dark-text; border-bottom-color: $color-dark-border; }
  &:last-child { border-bottom: none; }
  &.logout-link { color: #ef4444; }
}

.main-content {
  min-height: calc(100vh - 60px - 60px);
  padding-top: 60px;
  transition: padding-top 0.3s ease;

  &.has-announcement {
    padding-top: 96px; /* 60px 导航栏 + 36px 公告栏 */
  }
}

.site-footer {
  padding: 1.25rem;
  text-align: center;
  font-size: 0.8rem;
  color: $color-text-muted;
  border-top: 1px solid $color-border;
  .dark & { color: #64748b; border-top-color: $color-dark-border; }
}
</style>
