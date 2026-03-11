<template>
  <div class="layout" :class="{ dark: isDark }">
    <header
      class="navbar"
      :class="{
        'navbar--transparent': isHomePage && !isScrolled,
        'navbar--hidden': isNavHidden,
        'navbar--pre-enter': shouldHideNavbarBeforeEnter
      }"
    >
      <div class="nav-inner">
        <NuxtLink to="/" class="nav-logo" :class="{ 'animate-nav-item': shouldAnimate }" :style="shouldAnimate ? '--delay: 0.05s' : ''">
          <span class="logo-text">zhhhkl</span>
        </NuxtLink>

        <div class="nav-main">
          <NuxtLink
            v-for="(item, index) in mainNavLinks"
            :key="item.to"
            :to="item.to"
            class="nav-link desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? `--delay: ${0.15 + index * 0.1}s` : ''"
          >
            <Icon :name="item.icon" size="16" class="nav-link__icon" />
            <span>{{ item.label }}</span>
          </NuxtLink>

          <button
            class="icon-btn desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.75s' : ''"
            aria-label="搜索"
            @click="goSearch"
          >
            <Icon name="heroicons:magnifying-glass-20-solid" size="16" />
          </button>

          <button
            class="icon-btn desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.8s' : ''"
            aria-label="切换主题"
            @click="toggleDark()"
          >
            <Icon :name="isDark ? 'heroicons:sun-20-solid' : 'heroicons:moon-20-solid'" size="16" />
          </button>

          <div class="nav-auth-slot desktop-nav-item" :class="{ 'animate-nav-item': shouldAnimate }" :style="shouldAnimate ? '--delay: 0.85s' : ''">
            <button v-if="showLoggedIn" key="nav-avatar" type="button" class="avatar-btn" @mouseenter="showUserMenu = true">
              <img v-if="displayAvatar && !avatarLoadFailed" :src="displayAvatar" alt="头像" class="user-avatar" @error="onAvatarError" />
              <span v-else class="user-avatar-placeholder">{{ displayNickname.charAt(0) }}</span>
            </button>

            <button
              v-else
              key="nav-login"
              type="button"
              class="login-btn"
              @click="openLogin"
            >
              <Icon name="heroicons:user-16-solid" size="16" class="login-btn__icon" />
              <span>登录</span>
            </button>

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
        <NuxtLink v-for="item in mainNavLinks" :key="`mobile-${item.to}`" :to="item.to" class="mobile-link">
          <Icon :name="item.icon" size="16" class="mobile-link__icon" />
          <span>{{ item.label }}</span>
        </NuxtLink>
        <button class="mobile-link" @click="searchModal.open()">
          <Icon name="heroicons:magnifying-glass-20-solid" size="16" class="mobile-link__icon" />
          <span>搜索</span>
        </button>
        <template v-if="showLoggedIn">
          <NuxtLink to="/user" class="mobile-link">
            <Icon name="heroicons:user-circle-16-solid" size="16" class="mobile-link__icon" />
            <span>个人中心</span>
          </NuxtLink>
          <button class="mobile-link logout-link" @click="handleLogout">
            <Icon name="heroicons:arrow-right-start-on-rectangle-16-solid" size="16" class="mobile-link__icon" />
            <span>退出登录</span>
          </button>
        </template>
        <button v-else class="mobile-link" @click="openLogin">
          <Icon name="heroicons:user-16-solid" size="16" class="mobile-link__icon" />
          <span>登录</span>
        </button>
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

interface NavLinkItem {
  to: string
  label: string
  icon: string
}

const mainNavLinks: NavLinkItem[] = [
  { to: '/', label: '首页', icon: 'heroicons:home-20-solid' },
  { to: '/category', label: '分类', icon: 'heroicons:squares-2x2-20-solid' },
  { to: '/topic', label: '专题', icon: 'heroicons:book-open-20-solid' },
  { to: '/tags', label: '标签', icon: 'heroicons:tag-20-solid' },
  { to: '/ranking', label: '排行', icon: 'heroicons:trophy-20-solid' },
  { to: '/friend-links', label: '友链', icon: 'heroicons:link-20-solid' }
]

// ===== 首页入场动画 =====
const shouldAnimate = ref(false)
const shouldHideNavbarBeforeEnter = ref(false)

function triggerHomeNavEnterAnimation() {
  shouldAnimate.value = false
  shouldHideNavbarBeforeEnter.value = true

  if (!import.meta.client) return

  nextTick(() => {
    requestAnimationFrame(() => {
      shouldHideNavbarBeforeEnter.value = false
      shouldAnimate.value = true
    })
  })
}

// 进入首页时，等 DOM 渲染完成后再触发动画，确保用户能看到
watch(isHomePage, (val) => {
  if (val) {
    triggerHomeNavEnterAnimation()
  } else {
    shouldAnimate.value = false
    shouldHideNavbarBeforeEnter.value = false
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

onMounted(async () => {
  await userStore.fetchUser()
})

const showLoggedIn = computed(() => userStore.isLoggedIn)

const avatarLoadFailed = ref(false)

const displayAvatar = computed(() => {
  const raw = userStore.userInfo?.avatar || ''
  const value = typeof raw === 'string' ? raw.trim() : ''
  if (!value || value === 'null' || value === 'undefined') return ''
  return value
})
const displayNickname = computed(() => {
  return userStore.userInfo?.nickname || 'U'
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
  transition: transform 0.3s ease, opacity 0.3s ease, background 0.3s ease, border-color 0.3s ease;
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
    .nav-link,
    .login-btn {
      color: rgba(255, 255, 255, 0.88);
      &.router-link-active {
        color: rgba(255, 255, 255, 0.88);
        background: transparent;
      }
      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.26);
      }
    }
    .icon-btn {
      color: rgba(255, 255, 255, 0.88);
      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.26);
      }
    }
    .mobile-menu-btn { color: #fff; }
  }
  &--hidden {
    transform: translateY(-100%);
  }

  &--pre-enter {
    opacity: 0;
    transform: translateY(-100%);
    pointer-events: none;
  }
}

.nav-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.nav-main {
  display: flex;
  align-items: center;
  gap: 0.16rem;
  min-width: 0;
  margin-left: auto;

  @media (max-width: $breakpoint-md) {
    margin-left: 0;
  }
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


.desktop-nav-item {
  @media (max-width: $breakpoint-md) { display: none; }
}

.nav-link,
.icon-btn,
.login-btn {
  height: 34px;
  padding: 0 0.52rem;
  border-radius: $radius-md;
  transition: color 0.2s, background 0.2s;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  font-weight: 500;
  color: $color-text-muted;
  text-decoration: none;
  &:hover, &.router-link-active {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.08);
  }
  .dark & {
    color: #94a3b8;
    &:hover, &.router-link-active { color: $color-primary; background: rgba(59, 130, 246, 0.15); }
  }
}

.nav-auth-slot {
  width: 56px;
  min-width: 56px;
  min-height: 34px;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.icon-btn {
  min-width: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $color-text-muted;
  cursor: pointer;
  &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.08); }
  .dark & { color: #94a3b8; &:hover { color: $color-primary; background: rgba(59, 130, 246, 0.15); } }
}

.login-btn {
  padding: 0 0.25rem;
  background: transparent;
  color: $color-text-muted;
  font-size: 0.8rem;
  font-weight: 500;
  text-decoration: none;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.18rem;
  white-space: nowrap;
  border: none;
  cursor: pointer;
  &:hover {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.08);
  }
  .dark & {
    color: #94a3b8;
    &:hover {
      color: $color-primary;
      background: rgba(59, 130, 246, 0.15);
    }
  }
}

.login-btn__icon {
  opacity: 0.9;
  flex-shrink: 0;
}

.avatar-btn {
  border: none;
  background: transparent !important;
  cursor: pointer;
  padding: 0;
  width: 100%;
  min-width: 0;
  min-height: 34px;
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
  min-height: 34px;
  align-items: center;
  justify-content: center;
  margin-left: auto;
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
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 0;
  font-size: 0.95rem;
  font-weight: 500;
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

.mobile-link__icon {
  flex-shrink: 0;
  opacity: 0.92;
}

.main-content {
  min-height: calc(100vh - 60px - 60px);
  padding-top: 60px;
  background: #f5f5f5;

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
