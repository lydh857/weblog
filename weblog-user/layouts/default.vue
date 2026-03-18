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
          <span class="logo-mark">
            <img src="/brand/logo.png" :alt="`${siteName} logo`" class="logo-img">
          </span>
          <span class="logo-text">{{ siteName }}</span>
        </NuxtLink>

        <HomeNavSearchTicker
          class="desktop-nav-item home-search-desktop"
          :class="{ 'animate-nav-item': shouldAnimate }"
          :style="shouldAnimate ? '--delay: 0.12s' : ''"
          :items="homeNavRankingItems"
          :transparent="isHomePage && !isScrolled"
          @search="goSearch"
        />

        <div class="nav-main">
          <NuxtLink
            v-for="(item, index) in navLinks"
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
            :style="shouldAnimate ? '--delay: 0.8s' : ''"
            aria-label="公告通知"
            @click="openAnnouncementCenter"
          >
            <Icon name="heroicons:bell-20-solid" size="16" />
            <span v-if="unreadAnnouncementCount > 0" class="notice-badge-dot" aria-hidden="true" />
          </button>

          <button
            class="icon-btn desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.85s' : ''"
            aria-label="切换主题"
            @click="toggleDark()"
          >
            <Icon :name="isDark ? 'heroicons:sun-20-solid' : 'heroicons:moon-20-solid'" size="16" />
          </button>

          <div
            ref="navAuthRef"
            class="nav-auth-slot desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.9s' : ''"
            @mouseenter="openUserMenu"
            @mouseleave="scheduleHideUserMenu()"
          >
            <button
              v-if="showLoggedIn"
              key="nav-avatar"
              type="button"
              class="avatar-btn"
              :aria-expanded="showUserMenu"
              @click="toggleUserMenu"
            >
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
            <div
              v-if="showUserMenu"
              class="user-menu"
              @mouseenter="openUserMenu"
              @mouseleave="scheduleHideUserMenu()"
              @click="closeUserMenu"
            >
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
        <NuxtLink v-for="item in navLinks" :key="`mobile-${item.to}`" :to="item.to" class="mobile-link">
          <Icon :name="item.icon" size="16" class="mobile-link__icon" />
          <span>{{ item.label }}</span>
        </NuxtLink>
        <button class="mobile-link" @click="searchModal.open()">
          <Icon name="heroicons:magnifying-glass-20-solid" size="16" class="mobile-link__icon" />
          <span>搜索</span>
        </button>
        <button class="mobile-link" @click="openAnnouncementCenter">
          <Icon name="heroicons:bell-20-solid" size="16" class="mobile-link__icon" />
          <span>公告</span>
          <span v-if="unreadAnnouncementCount > 0" class="mobile-notice-dot" aria-hidden="true" />
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
    <AnnouncementCenter
      v-model:visible="announcementCenterVisible"
      @unread-change="handleAnnouncementUnreadChange"
    />
    <AnnouncementBanner type="banner" :nav-hidden="isNavHidden" :transparent="isHomePage && !isScrolled" />
    <main class="main-content" :class="{ 'has-announcement': announcementBarVisible && !isHomePage }">
      <slot />
    </main>
    <Transition name="left-ad-float-fade">
      <aside v-if="globalLeftAdVisible" class="global-left-ad">
        <AdSlotBanner ad-slot="home_left" :visible="globalLeftAdVisible" :force-rotate="true" />
      </aside>
    </Transition>
    <AnnouncementPopup />
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { useDarkMode } from '~/composables/useDarkMode'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { useSearchModal } from '~/composables/useSearchModal'
import { useNavScrollLock } from '~/composables/useNavScrollLock'
import { rankingApi, type RankingItem } from '~/api/ranking'

const { bannerVisible: announcementBarVisible } = useAnnouncementBar()

const { isDark, toggleDark } = useDarkMode()
const userStore = useUserStore()
const searchModal = useSearchModal()
const router = useRouter()
const route = useRoute()
const siteConfig = useSiteConfigState()
const mobileMenuOpen = ref(false)
const showUserMenu = ref(false)
const announcementCenterVisible = ref(false)
const unreadAnnouncementCount = ref(0)
const navAuthRef = ref<HTMLElement | null>(null)
const message = useMessage()
const { confirm } = useConfirm()
const loginModal = useLoginModal()
const { locked: navScrollLocked } = useNavScrollLock()
const homeNavRankingItems = ref<RankingItem[]>([])
let hideUserMenuTimer: ReturnType<typeof setTimeout> | null = null

// ===== 导航栏滚动状态 =====
const isScrolled = ref(false)
const isNavHidden = ref(false)
const lastScrollY = ref(0)
const isHomePage = computed(() => route.path === '/')
const globalLeftAdVisible = ref(false)
let leftAdHeroObserver: IntersectionObserver | null = null
let leftAdHeroRetryTimer: ReturnType<typeof setTimeout> | null = null
let leftAdHeroRetryCount = 0
const LEFT_AD_HERO_RETRY_MAX = 30

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

const navLinks = computed<NavLinkItem[]>(() => mainNavLinks)
const siteName = computed(() => siteConfig.value.siteName || DEFAULT_SITE_NAME)
const siteDescription = computed(() => siteConfig.value.siteDescription || DEFAULT_SITE_DESCRIPTION)

useHead(() => ({
  titleTemplate: (titleChunk?: string) => {
    const currentSiteName = siteName.value
    if (!titleChunk) return currentSiteName
    if (titleChunk.includes(currentSiteName)) return titleChunk
    if (titleChunk.includes(DEFAULT_SITE_NAME)) {
      return titleChunk.replaceAll(DEFAULT_SITE_NAME, currentSiteName)
    }
    return `${titleChunk} - ${currentSiteName}`
  },
  meta: [
    {
      name: 'description',
      content: siteDescription.value,
    },
  ],
}))

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

function clearLeftAdHeroWatchers() {
  leftAdHeroObserver?.disconnect()
  leftAdHeroObserver = null

  if (leftAdHeroRetryTimer) {
    clearTimeout(leftAdHeroRetryTimer)
    leftAdHeroRetryTimer = null
  }

  leftAdHeroRetryCount = 0
}

function observeHomeHeroForLeftAd() {
  if (!import.meta.client) return

  const heroEl = document.querySelector<HTMLElement>('.hero-carousel')
  if (!heroEl) {
    globalLeftAdVisible.value = false
    if (leftAdHeroRetryCount >= LEFT_AD_HERO_RETRY_MAX) return
    leftAdHeroRetryCount += 1
    leftAdHeroRetryTimer = setTimeout(() => {
      observeHomeHeroForLeftAd()
    }, 120)
    return
  }

  leftAdHeroObserver?.disconnect()
  leftAdHeroObserver = new IntersectionObserver((entries) => {
    const entry = entries[0]
    if (!entry) return
    globalLeftAdVisible.value = !entry.isIntersecting
  }, { threshold: 0 })

  leftAdHeroObserver.observe(heroEl)
}

function refreshGlobalLeftAdVisibility() {
  if (!import.meta.client) return

  clearLeftAdHeroWatchers()
  if (route.path === '/') {
    observeHomeHeroForLeftAd()
    return
  }

  globalLeftAdVisible.value = true
}

async function loadHomeNavRanking() {
  if (!import.meta.client) return
  if (homeNavRankingItems.value.length) return

  try {
    const res = await rankingApi.get({ rankType: 4, limit: 5 })
    homeNavRankingItems.value = (res.data || []).slice(0, 5)
  } catch {
    homeNavRankingItems.value = []
  }
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

watch(() => route.path, () => {
  if (!import.meta.client) return
  nextTick(() => {
    refreshGlobalLeftAdVisibility()
  })
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
  refreshGlobalLeftAdVisibility()
  void loadHomeNavRanking()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  clearLeftAdHeroWatchers()
})

onMounted(async () => {
  await userStore.fetchUser()
})

function clearHideUserMenuTimer() {
  if (hideUserMenuTimer !== null) {
    clearTimeout(hideUserMenuTimer)
    hideUserMenuTimer = null
  }
}

function openUserMenu() {
  if (!showLoggedIn.value) return
  clearHideUserMenuTimer()
  showUserMenu.value = true
}

function scheduleHideUserMenu(delay = 120) {
  clearHideUserMenuTimer()
  hideUserMenuTimer = setTimeout(() => {
    showUserMenu.value = false
    hideUserMenuTimer = null
  }, delay)
}

function closeUserMenu() {
  clearHideUserMenuTimer()
  showUserMenu.value = false
}

function toggleUserMenu() {
  if (showUserMenu.value) {
    closeUserMenu()
    return
  }
  openUserMenu()
}

function handleGlobalPointerDown(event: PointerEvent) {
  const target = event.target
  if (!(target instanceof Node)) return
  if (navAuthRef.value?.contains(target)) return
  closeUserMenu()
}

onMounted(() => {
  document.addEventListener('pointerdown', handleGlobalPointerDown)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', handleGlobalPointerDown)
  clearHideUserMenuTimer()
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

watch(() => route.fullPath, () => {
  closeUserMenu()
})

watch(showLoggedIn, (val) => {
  if (!val) closeUserMenu()
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

function openAnnouncementCenter() {
  mobileMenuOpen.value = false
  announcementCenterVisible.value = true
}

function handleAnnouncementUnreadChange(count: number) {
  unreadAnnouncementCount.value = Math.max(0, count)
}

function openLogin() {
  loginModal.open()
}

async function handleLogout() {
  const ok = await confirm({ title: '退出登录', message: '确定要退出登录吗？', type: 'warning', confirmText: '退出' })
  if (!ok) return

  const shouldRedirectHome = route.path.startsWith('/user')

  try {
    await authApi.logout()
  } catch {}

  // 清除记住我相关数据，防止自动重新登录
  localStorage.removeItem('weblog_user_remember')
  localStorage.removeItem('remember_credentials')
  localStorage.removeItem('remember_token')

  userStore.clearUser()
  message.success('已退出登录')

  if (shouldRedirectHome) {
    await navigateTo('/')
  }
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
  max-width: var(--layout-max-width);
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
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
}

.logo-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
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
  position: relative;
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

.notice-badge-dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
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
  background: transparent;
  cursor: pointer;
  padding: 0;
  width: 100%;
  min-width: 0;
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  outline: none;
  border-radius: 999px;
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
  margin-top: 0.42rem;
  min-width: 176px;
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: 12px;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.16);
  z-index: 200;
  padding: 0.4rem;
  backdrop-filter: blur(10px);
  .dark & {
    background: rgba(15, 23, 42, 0.92);
    border-color: $color-dark-border;
    box-shadow: 0 14px 32px rgba(2, 6, 23, 0.55);
  }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.56rem 0.66rem;
  font-size: 0.85rem;
  color: $color-text;
  text-decoration: none;
  border: none;
  border-radius: 8px;
  background: none;
  cursor: pointer;
  transition: background 0.16s, color 0.16s;
  &:hover {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.1);
  }
  &.logout {
    margin-top: 0.2rem;
    color: #ef4444;
    border-top: 1px solid rgba(148, 163, 184, 0.3);
    border-radius: 0 0 8px 8px;
    padding-top: 0.66rem;
    &:hover {
      color: #dc2626;
      background: rgba(239, 68, 68, 0.1);
    }
  }
  .dark & {
    color: $color-dark-text;
    &.logout {
      color: #f87171;
      border-top-color: rgba(71, 85, 105, 0.5);
      &:hover {
        color: #ef4444;
      }
    }
  }
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

.mobile-notice-dot {
  margin-left: auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}

.global-left-ad {
  position: fixed;
  top: 50%;
  left: clamp(8px, calc((100vw - var(--layout-max-width)) / 2 - 158px), 96px);
  width: 140px;
  transform: translateY(-50%);
  z-index: 55;
}

.left-ad-float-fade-enter-active,
.left-ad-float-fade-leave-active {
  transition: opacity 260ms ease, transform 320ms ease;
}

.left-ad-float-fade-enter-from,
.left-ad-float-fade-leave-to {
  opacity: 0;
  transform: translate3d(-12px, -50%, 0);
}

.main-content {
  min-height: calc(100vh - 60px - 60px);
  padding-top: 60px;
  background: #f5f5f5;

  &.has-announcement {
    padding-top: 96px; /* 60px 导航栏 + 36px 公告栏 */
  }
}

@media (max-width: 1540px) {
  .global-left-ad {
    display: none;
  }
}

@media (max-width: $breakpoint-md) {
  .global-left-ad {
    display: none;
  }
}

</style>
