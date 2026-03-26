<template>
  <div class="layout" :class="{ dark: isDark }">
    <header
      v-if="shouldRenderNavbar"
      class="navbar"
      :class="{
        'navbar--transparent': isNavbarTransparent,
        'navbar--hidden': isNavHidden
      }"
    >
        <div class="nav-inner">
          <NuxtLink to="/" class="nav-logo" :class="{ 'animate-nav-item': shouldAnimate }" :style="shouldAnimate ? '--delay: 0.05s' : ''">
          <span class="logo-mark">
            <img
              src="/brand/logo.png"
              :alt="`${siteName} logo`"
              class="logo-img"
              width="34"
              height="34"
            >
          </span>
            <span class="logo-text">{{ siteName }}</span>
          </NuxtLink>

          <HomeNavSearchTicker
            class="mobile-search-ticker"
            :items="homeNavRankingItems"
            :mobile="true"
            :transparent="isHomePage && !isScrolled"
            @placeholder-search="openSearchWithPlaceholder"
            @direct-search="openSearchWithDirectKeyword"
          />

        <HomeNavSearchTicker
          class="desktop-nav-item home-search-desktop"
          :class="{ 'animate-nav-item': shouldAnimate }"
          :style="shouldAnimate ? '--delay: 0.12s' : ''"
          :items="homeNavRankingItems"
          :transparent="isHomePage && !isScrolled"
          @placeholder-search="openSearchWithPlaceholder"
          @direct-search="openSearchWithDirectKeyword"
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
            :style="shouldAnimate ? '--delay: 0.58s' : ''"
            aria-label="公告通知"
            @click="openAnnouncementCenter"
          >
            <Icon name="heroicons:bell-20-solid" size="16" />
            <span v-if="unreadAnnouncementCount > 0" class="notice-badge-dot" aria-hidden="true" />
          </button>

          <button
            class="icon-btn desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.66s' : ''"
            aria-label="切换主题"
            @click="toggleDark()"
          >
            <Icon :name="isDark ? 'heroicons:sun-20-solid' : 'heroicons:moon-20-solid'" size="16" />
          </button>

          <div
            ref="navAuthRef"
            class="nav-auth-slot desktop-nav-item"
            :class="{ 'animate-nav-item': shouldAnimate }"
            :style="shouldAnimate ? '--delay: 0.74s' : ''"
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
        <button class="mobile-menu-btn touch-target" aria-label="菜单" @click="toggleMobileMenu">
          <Icon :name="mobileMenuOpen ? 'heroicons:x-mark-20-solid' : 'heroicons:bars-3-20-solid'" size="24" />
        </button>
      </div>
    </header>
    <Transition name="mobile-drawer-fade">
      <div v-if="mobileMenuOpen" class="mobile-drawer-backdrop" aria-hidden="true" @click="closeMobileMenu" />
    </Transition>
    <Transition name="mobile-drawer-slide">
      <aside v-if="mobileMenuOpen" class="mobile-drawer" aria-label="移动端导航抽屉">
        <div class="mobile-drawer__header">
          <span class="mobile-drawer__title">导航菜单</span>
          <button class="mobile-drawer__close" type="button" aria-label="关闭菜单" @click="closeMobileMenu">
            <Icon name="heroicons:x-mark-20-solid" size="22" />
          </button>
        </div>
        <div class="mobile-menu">
          <NuxtLink
            v-for="item in navLinks"
            :key="`mobile-${item.to}`"
            :to="item.to"
            class="mobile-link"
            @click="closeMobileMenu"
          >
            <Icon :name="item.icon" size="16" class="mobile-link__icon" />
            <span>{{ item.label }}</span>
          </NuxtLink>
          <button class="mobile-link" @click="goSearch">
            <Icon name="heroicons:magnifying-glass-20-solid" size="16" class="mobile-link__icon" />
            <span>搜索</span>
          </button>
          <button class="mobile-link" @click="openAnnouncementCenter">
            <Icon name="heroicons:bell-20-solid" size="16" class="mobile-link__icon" />
            <span>公告</span>
            <span v-if="unreadAnnouncementCount > 0" class="mobile-notice-dot" aria-hidden="true" />
          </button>
          <template v-if="showLoggedIn">
            <NuxtLink to="/user" class="mobile-link" @click="closeMobileMenu">
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
      </aside>
    </Transition>
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
      <aside v-if="shouldShowGlobalLeftAd" class="global-left-ad" :class="{ 'is-scrolling-hidden': isGlobalLeftAdScrollingHidden }">
        <AdSlotBanner ad-slot="home_left" :visible="globalLeftAdVisible" :force-rotate="true" @closed="handleGlobalLeftAdClosed" />
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
const isNavbarTransparent = computed(() => (isHomePage.value && !isScrolled.value) || forceHomeNavbarTransparent.value)
const globalLeftAdVisible = ref(false)
const globalLeftAdDismissed = ref(false)
const isGlobalLeftAdScrollingHidden = ref(false)
const shouldShowGlobalLeftAd = computed(() => globalLeftAdVisible.value && !globalLeftAdDismissed.value)
let leftAdHeroObserver: IntersectionObserver | null = null
let leftAdHeroRetryTimer: ReturnType<typeof setTimeout> | null = null
let forceHomeNavbarTimer: ReturnType<typeof setTimeout> | null = null
let leftAdMobileScrollTimer: ReturnType<typeof setTimeout> | null = null
let leftAdHeroRetryCount = 0
const LEFT_AD_HERO_RETRY_MAX = 30
const LEFT_AD_MOBILE_HIDE_DELAY = 180

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
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const isStartupDone = ref(false)
const NAV_MOBILE_BREAKPOINT = 768
const NAV_TOGGLE_SCROLL_DELTA = 8
const forceHomeNavbarTransparent = ref(false)
const shouldRenderNavbar = computed(() => !isHomePage.value || isStartupDone.value)
const hasPlayedHomeNavEntrance = ref(false)
let homeNavAnimateTimer: ReturnType<typeof setTimeout> | null = null

function hasStartupDone() {
  if (!import.meta.client) {
    return false
  }

  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  return Boolean(runtimeWindow.__weblogStartupDone)
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

  if (globalLeftAdDismissed.value) {
    globalLeftAdVisible.value = false
    isGlobalLeftAdScrollingHidden.value = false
    return
  }

  clearLeftAdHeroWatchers()
  if (route.path === '/') {
    globalLeftAdVisible.value = false
    observeHomeHeroForLeftAd()
    return
  }

  globalLeftAdVisible.value = true
}

function clearLeftAdMobileScrollTimer() {
  if (!leftAdMobileScrollTimer) return
  clearTimeout(leftAdMobileScrollTimer)
  leftAdMobileScrollTimer = null
}

function syncGlobalLeftAdScrollState() {
  if (!import.meta.client) return

  const isMobileViewport = window.innerWidth <= NAV_MOBILE_BREAKPOINT
  const canAnimateVisibility = isMobileViewport && shouldShowGlobalLeftAd.value

  if (!canAnimateVisibility) {
    isGlobalLeftAdScrollingHidden.value = false
    clearLeftAdMobileScrollTimer()
    return
  }

  isGlobalLeftAdScrollingHidden.value = true
  clearLeftAdMobileScrollTimer()
  leftAdMobileScrollTimer = setTimeout(() => {
    isGlobalLeftAdScrollingHidden.value = false
    leftAdMobileScrollTimer = null
  }, LEFT_AD_MOBILE_HIDE_DELAY)
}

function handleGlobalLeftAdClosed() {
  globalLeftAdDismissed.value = true
  globalLeftAdVisible.value = false
  isGlobalLeftAdScrollingHidden.value = false
  clearLeftAdMobileScrollTimer()
  clearLeftAdHeroWatchers()
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

function clearHomeNavAnimateTimer() {
  if (!homeNavAnimateTimer) return
  clearTimeout(homeNavAnimateTimer)
  homeNavAnimateTimer = null
}

function triggerHomeNavItemsEntrance() {
  if (!import.meta.client) return
  if (hasPlayedHomeNavEntrance.value) return
  if (route.path !== '/') return
  if (window.scrollY > 2) return

  hasPlayedHomeNavEntrance.value = true
  shouldAnimate.value = true
  clearHomeNavAnimateTimer()
  homeNavAnimateTimer = setTimeout(() => {
    shouldAnimate.value = false
    homeNavAnimateTimer = null
  }, 1100)
}

watch(() => route.path, (path, oldPath) => {
  if (!import.meta.client) return

  const isEnterHomeFromOtherPage = path === '/' && Boolean(oldPath) && oldPath !== '/'
  if (isEnterHomeFromOtherPage) {
    hasPlayedHomeNavEntrance.value = false
  }

  if (path !== '/') {
    shouldAnimate.value = false
    clearHomeNavAnimateTimer()
  }

  const isMobileViewport = window.innerWidth <= NAV_MOBILE_BREAKPOINT
  if (path === '/') {
    globalLeftAdVisible.value = false
  }
  isGlobalLeftAdScrollingHidden.value = false
  clearLeftAdMobileScrollTimer()
  if (isMobileViewport) {
    isNavHidden.value = false
  }
  const isEnterHomeOnMobile = path === '/' && oldPath && oldPath !== '/' && isMobileViewport

  if (isEnterHomeOnMobile) {
    forceHomeNavbarTransparent.value = true
    isScrolled.value = false
    isNavHidden.value = false
    lastScrollY.value = 0
    window.scrollTo({ top: 0, behavior: 'auto' })

    if (forceHomeNavbarTimer) {
      clearTimeout(forceHomeNavbarTimer)
      forceHomeNavbarTimer = null
    }

    forceHomeNavbarTimer = setTimeout(() => {
      forceHomeNavbarTransparent.value = false
      forceHomeNavbarTimer = null
      handleScroll()
    }, 240)
  }

  nextTick(() => {
    handleScroll()
    refreshGlobalLeftAdVisibility()
    requestAnimationFrame(() => {
      handleScroll()
    })
  })
}, { immediate: true })

watch([isHomePage, isStartupDone], ([home, startupDone]) => {
  if (!import.meta.client) return
  if (!home || !startupDone) return

  nextTick(() => {
    triggerHomeNavItemsEntrance()
  })
}, { immediate: true, flush: 'post' })

watch(shouldShowGlobalLeftAd, (visible) => {
  if (visible) return
  isGlobalLeftAdScrollingHidden.value = false
  clearLeftAdMobileScrollTimer()
})

function handleScroll() {
  if (document.body.style.position === 'fixed') {
    return
  }

  const scrollY = Math.max(window.scrollY, 0)
  const topVisibleThreshold = window.innerWidth <= NAV_MOBILE_BREAKPOINT ? 6 : 2
  isScrolled.value = scrollY > 20

  // 评论区 DOM 操作期间跳过方向判断，只同步 lastScrollY
  if (navScrollLocked.value) {
    lastScrollY.value = scrollY
    syncGlobalLeftAdScrollState()
    return
  }

  const heroEl = document.querySelector<HTMLElement>('.hero-carousel')
  const heroRect = heroEl?.getBoundingClientRect()
  const hideStartY = Math.max(heroRect?.height ?? 0, 80)
  const hasPassedHero = heroRect ? heroRect.bottom <= 0 : scrollY > hideStartY

  if (scrollY <= topVisibleThreshold) {
    isNavHidden.value = false
    lastScrollY.value = 0
    syncGlobalLeftAdScrollState()
    return
  }

  if (isHomePage.value) {
    if (!hasPassedHero) {
      isNavHidden.value = false
      lastScrollY.value = scrollY
      syncGlobalLeftAdScrollState()
      return
    }

    const delta = scrollY - lastScrollY.value
    if (Math.abs(delta) >= NAV_TOGGLE_SCROLL_DELTA) {
      isNavHidden.value = delta > 0
    }
    lastScrollY.value = scrollY
    syncGlobalLeftAdScrollState()
    return
  }

  if (scrollY > hideStartY) {
    const delta = scrollY - lastScrollY.value
    if (Math.abs(delta) >= NAV_TOGGLE_SCROLL_DELTA) {
      isNavHidden.value = delta > 0
    }
  } else {
    isNavHidden.value = false
  }
  lastScrollY.value = scrollY
  syncGlobalLeftAdScrollState()
}

function handleWindowResize() {
  if (!import.meta.client) return
  if (window.innerWidth > 768) {
    closeMobileMenu()
  }
}

onMounted(() => {
  isStartupDone.value = hasStartupDone()
  if (!isStartupDone.value) {
    window.addEventListener(STARTUP_DONE_EVENT, handleStartupDone)
  }

  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleWindowResize, { passive: true })
  handleScroll()
  refreshGlobalLeftAdVisibility()
  void loadHomeNavRanking()
})

onUnmounted(() => {
  window.removeEventListener(STARTUP_DONE_EVENT, handleStartupDone)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleWindowResize)
  clearHomeNavAnimateTimer()
  clearLeftAdMobileScrollTimer()
  if (forceHomeNavbarTimer) {
    clearTimeout(forceHomeNavbarTimer)
    forceHomeNavbarTimer = null
  }
  clearLeftAdHeroWatchers()
})

function handleStartupDone() {
  isStartupDone.value = true
}

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
  closeMobileMenu()
})

watch(mobileMenuOpen, (open) => {
  if (!import.meta.client) return
  document.body.style.overflow = open ? 'hidden' : ''
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

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function goSearch() {
  openSearchDefault()
}

function openSearchDefault() {
  closeMobileMenu()
  searchModal.open()
}

function openSearchWithPlaceholder(title: string) {
  closeMobileMenu()
  const placeholder = title.trim() || '搜索文章...'
  searchModal.open({ placeholder })
}

function openSearchWithDirectKeyword(title: string) {
  closeMobileMenu()
  const keyword = title.trim()
  searchModal.open({
    keyword,
    placeholder: keyword || '搜索文章...',
    autoSearch: Boolean(keyword),
  })
}

function openAnnouncementCenter() {
  closeMobileMenu()
  announcementCenterVisible.value = true
}

function handleAnnouncementUnreadChange(count: number) {
  unreadAnnouncementCount.value = Math.max(0, count)
}

function openLogin() {
  closeMobileMenu()
  loginModal.open()
}

async function handleLogout() {
  closeMobileMenu()
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

onUnmounted(() => {
  if (!import.meta.client) return
  document.body.style.overflow = ''
})
</script>

<style lang="scss">
.layout {
  --layout-navbar-height: 60px;
  --layout-announcement-height: 36px;
}

/* ===== 导航栏入场动画 ===== */
.animate-nav-item {
  opacity: 0;
  transform: translateY(-14px);
  animation: navFadeInDown 0.52s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
  animation-delay: var(--delay, 0s);
}

@keyframes navFadeInDown {
  from {
    opacity: 0;
    transform: translateY(-14px);
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
    background: rgba(16, 18, 21, 0.86);
    border-bottom-color: $color-dark-border;
  }
  &--transparent {
    background: transparent;
    backdrop-filter: none;
    border-bottom-color: transparent;
    .dark & {
      background: transparent;
      border-bottom-color: transparent;
      .nav-logo .logo-text { color: rgba(255, 255, 255, 0.88); }
    }
    .nav-logo .logo-text { color: rgba(255, 255, 255, 0.88); }
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

}

@media (max-width: $breakpoint-md) {
  .navbar {
    padding-right: 0;
    transition: transform 0.3s ease, opacity 0.24s ease;
  }
}

.nav-inner {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: 0 var(--layout-page-padding-x);
  height: var(--layout-navbar-height);
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.mobile-search-ticker {
  display: none;
}

@media (max-width: $breakpoint-md) {
  .mobile-search-ticker {
    display: flex;
    flex: 0 1 188px;
    min-width: 122px;
    max-width: 188px;
    margin-left: 0;
    margin-right: 0.42rem;
    padding: 0;
  }
}

.nav-main {
  display: flex;
  align-items: center;
  gap: 0.16rem;
  min-width: 0;
  margin-left: auto;

  @media (max-width: $breakpoint-md) {
    display: none;
  }
}

/* ===== Logo 样式 ===== */

.nav-logo {
  text-decoration: none;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 0.52rem;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
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
  font-family: inherit;
  font-size: 1.2rem;
  font-weight: 800;
  color: #000;
  letter-spacing: 0.02em;
  line-height: 1;
  transition: color 0.2s;

  .dark & {
    color: #fff;
  }
}

@media (max-width: $breakpoint-md) {
  .nav-logo { gap: 0.4rem; }
  .nav-logo { margin-right: 0.12rem; }
  .logo-mark {
    width: 30px;
    height: 30px;
    border-radius: 9px;
  }

  .logo-text {
    font-size: 0.98rem;
  }

  .nav-inner {
    gap: 0;
  }
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
    color: #c6cfdb;
    &:hover,
    &.router-link-active {
      color: #f2f5fa;
      background: rgba(148, 163, 184, 0.18);
    }
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
  .dark & {
    color: #c6cfdb;
    &:hover {
      color: #f2f5fa;
      background: rgba(148, 163, 184, 0.18);
    }
  }
}

.notice-badge-dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-danger);
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
  line-height: 1;
  white-space: nowrap;
  border: none;
  cursor: pointer;
  &:hover {
    color: $color-primary;
    background: rgba(59, 130, 246, 0.08);
  }
  .dark & {
    color: #c6cfdb;
    &:hover {
      color: #f2f5fa;
      background: rgba(148, 163, 184, 0.18);
    }
  }
}

.login-btn__icon {
  display: block;
  opacity: 0.9;
  flex-shrink: 0;
}

.login-btn > span {
  display: inline-flex;
  align-items: center;
  line-height: 1;
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
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.11), transparent 46%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.08), transparent 54%),
      linear-gradient(180deg, #171b20, #101215);
    border-color: rgba(148, 163, 184, 0.34);
    box-shadow: 0 16px 36px rgba(2, 6, 23, 0.56);
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
    color: var(--status-danger);
    border-top: 1px solid rgba(148, 163, 184, 0.3);
    border-radius: 0 0 8px 8px;
    padding-top: 0.66rem;
    &:hover {
      color: var(--status-danger);
      background: var(--status-danger-soft-bg);
    }
  }
  .dark & {
    color: #d6dbe4;
    &:hover {
      color: #f8fafc;
      background: rgba(148, 163, 184, 0.14);
    }
    &.logout {
      color: #fda4af;
      border-top-color: rgba(71, 85, 105, 0.62);
      &:hover {
        color: #fecaca;
        background: rgba(239, 68, 68, 0.16);
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
  padding: 0.32rem 0 0.32rem 0.26rem;
  min-width: 38px;
  min-height: 32px;
  align-items: center;
  justify-content: flex-end;
  margin-left: auto;
  .dark & { color: $color-dark-text; }
  @media (max-width: $breakpoint-md) { display: flex; }
}

.mobile-drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-modal) + 10);
  background: rgba(15, 23, 42, 0.26);
}

.mobile-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: calc(var(--z-modal) + 11);
  width: min(82vw, 320px);
  background: #fff;
  border-left: 1px solid $color-border;
  box-shadow: none;
  display: flex;
  flex-direction: column;

  .dark & {
    background: $color-dark-bg-secondary;
    border-left-color: $color-dark-border;
  }

  @media (min-width: calc(#{$breakpoint-md} + 1px)) {
    display: none;
  }
}

.mobile-drawer__header {
  min-height: 60px;
  padding: max(0.72rem, env(safe-area-inset-top)) var(--layout-page-padding-x) 0.72rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.mobile-drawer__title {
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.mobile-drawer__close {
  border: none;
  background: none;
  color: #64748b;
  width: 32px;
  height: 32px;
  min-width: 32px;
  min-height: 32px;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.16s ease, color 0.16s ease;

  &:hover {
    background: rgba(100, 116, 139, 0.12);
    color: #475569;
  }

  .dark & {
    color: #94a3b8;

    &:hover {
      background: rgba(148, 163, 184, 0.16);
      color: #cbd5e1;
    }
  }
}

.mobile-menu {
  display: flex;
  flex-direction: column;
  gap: 0.26rem;
  padding: 0.5rem var(--layout-page-padding-x) max(0.9rem, env(safe-area-inset-bottom));
  overflow-y: auto;
}

.mobile-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.72rem 0.72rem;
  font-size: 1rem;
  font-weight: 500;
  color: $color-text;
  text-decoration: none;
  border: none;
  border: 1px solid transparent;
  border-radius: 10px;
  background: none;
  text-align: left;
  cursor: pointer;
  width: 100%;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
  -webkit-tap-highlight-color: transparent;

  &:hover {
    background: rgba(100, 116, 139, 0.08);
  }

  .dark & {
    color: $color-dark-text;

    &:hover {
      background: rgba(148, 163, 184, 0.14);
    }
  }

  &.logout-link { color: var(--status-danger); }

  &.router-link-active,
  &.router-link-exact-active {
    color: $color-primary;
    font-weight: 700;
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.24);

    .mobile-link__icon {
      color: $color-primary;
      opacity: 1;
    }

    .dark & {
      color: #f2f5fa;
      background: rgba(148, 163, 184, 0.18);
      border-color: rgba(148, 163, 184, 0.34);

      .mobile-link__icon {
        color: #f2f5fa;
      }
    }
  }
}

@media (hover: none) and (pointer: coarse) {
  .mobile-link:hover,
  .mobile-link:active {
    background: none;
    border-color: transparent;
  }

  .dark .mobile-link:hover,
  .dark .mobile-link:active {
    background: none;
    border-color: transparent;
  }
}

.mobile-drawer-fade-enter-active,
.mobile-drawer-fade-leave-active {
  transition: opacity 220ms ease;
}

.mobile-drawer-fade-enter-from,
.mobile-drawer-fade-leave-to {
  opacity: 0;
}

.mobile-drawer-slide-enter-active,
.mobile-drawer-slide-leave-active {
  transition: transform 260ms ease;
}

.mobile-drawer-slide-enter-from,
.mobile-drawer-slide-leave-to {
  transform: translateX(100%);
}

.mobile-link__icon {
  flex-shrink: 0;
  opacity: 0.82;
  color: #475569;

  .dark & {
    color: #cbd5e1;
  }
}

.mobile-notice-dot {
  margin-left: auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-danger);
}

.global-left-ad {
  position: fixed;
  top: 50%;
  left: clamp(8px, calc((100vw - var(--layout-max-width)) / 2 - 158px), 96px);
  width: 140px;
  transform: translateY(-50%);
  z-index: 55;
  transition: opacity 220ms ease, transform 260ms ease;
  will-change: opacity, transform;
}

.global-left-ad.is-scrolling-hidden {
  opacity: 0;
  pointer-events: none;
  transform: translate3d(-12px, -50%, 0);
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
  min-height: calc(100vh - var(--layout-navbar-height) - 60px);
  padding-top: var(--layout-navbar-height);
  background: #f5f5f5;

  .dark & {
    background: $color-dark-bg;
  }

  &.has-announcement {
    padding-top: calc(var(--layout-navbar-height) + var(--layout-announcement-height));
  }
}

@media (max-width: 1540px) and (min-width: 1201px) {
  .global-left-ad {
    display: none;
  }
}

@media (min-width: 769px) and (max-width: 1200px) {
  .global-left-ad {
    display: block;
    top: 50%;
    bottom: auto;
    left: calc(8px + env(safe-area-inset-left));
    width: clamp(154px, 24vw, 210px);
    transform: translate3d(0, -50%, 0);
    z-index: 56;
  }

  .global-left-ad.is-scrolling-hidden {
    transform: translate3d(-10px, -50%, 0);
  }
}

@media (max-width: $breakpoint-md) {
  .layout {
    --layout-navbar-height: 56px;
  }

  .global-left-ad {
    display: block;
    top: 50%;
    bottom: auto;
    left: calc(8px + env(safe-area-inset-left));
    width: clamp(164px, 44vw, 210px);
    transform: translate3d(0, -50%, 0);
    z-index: 56;
  }

  .global-left-ad.is-scrolling-hidden {
    transform: translate3d(-12px, -50%, 0);
  }

  .main-content {
    min-height: calc(100vh - var(--layout-navbar-height) - 60px);
    padding-top: var(--layout-navbar-height);

    &.has-announcement {
      padding-top: calc(var(--layout-navbar-height) + var(--layout-announcement-height));
    }
  }
}

</style>
