interface NavbarScrollBehaviorOptions {
  isHomePage: Readonly<Ref<boolean>>
  isHomeHeroPassed: Readonly<Ref<boolean>>
  navScrollLocked: Readonly<Ref<boolean>>
  onScrollStateSync: () => void
  mobileBreakpoint?: number
  toggleScrollDelta?: number
  hideStartY?: number
}

interface ResetNavStateOptions {
  forceVisible?: boolean
  forceTop?: boolean
}

export function useNavbarScrollBehavior(options: NavbarScrollBehaviorOptions) {
  const mobileBreakpoint = options.mobileBreakpoint ?? 768
  const toggleScrollDelta = options.toggleScrollDelta ?? 8
  const hideStartY = options.hideStartY ?? 80

  const isScrolled = ref(false)
  const isNavHidden = ref(false)
  const lastScrollY = ref(0)

  let scrollRafId: number | null = null

  function syncScrollState() {
    options.onScrollStateSync()
  }

  function handleScroll() {
    if (!import.meta.client) {
      return
    }

    if (document.body.style.position === 'fixed') {
      return
    }

    const scrollY = Math.max(window.scrollY, 0)
    const topVisibleThreshold = window.innerWidth <= mobileBreakpoint ? 6 : 2
    isScrolled.value = scrollY > 20

    if (options.navScrollLocked.value) {
      lastScrollY.value = scrollY
      syncScrollState()
      return
    }

    const hasPassedHero = options.isHomeHeroPassed.value || scrollY > hideStartY

    if (scrollY <= topVisibleThreshold) {
      isNavHidden.value = false
      lastScrollY.value = 0
      syncScrollState()
      return
    }

    if (options.isHomePage.value) {
      if (!hasPassedHero) {
        isNavHidden.value = false
        lastScrollY.value = scrollY
        syncScrollState()
        return
      }

      const delta = scrollY - lastScrollY.value
      if (Math.abs(delta) >= toggleScrollDelta) {
        isNavHidden.value = delta > 0
      }
      lastScrollY.value = scrollY
      syncScrollState()
      return
    }

    if (scrollY > hideStartY) {
      const delta = scrollY - lastScrollY.value
      if (Math.abs(delta) >= toggleScrollDelta) {
        isNavHidden.value = delta > 0
      }
    } else {
      isNavHidden.value = false
    }

    lastScrollY.value = scrollY
    syncScrollState()
  }

  function scheduleHandleScroll() {
    if (!import.meta.client) {
      return
    }

    if (scrollRafId !== null) {
      return
    }

    scrollRafId = window.requestAnimationFrame(() => {
      scrollRafId = null
      handleScroll()
    })
  }

  function cancelScheduledScroll() {
    if (!import.meta.client || scrollRafId === null) {
      return
    }

    window.cancelAnimationFrame(scrollRafId)
    scrollRafId = null
  }

  function resetNavState(options: ResetNavStateOptions = {}) {
    if (!import.meta.client) {
      return
    }

    if (options.forceVisible) {
      isNavHidden.value = false
    }

    if (options.forceTop) {
      lastScrollY.value = 0
      return
    }

    lastScrollY.value = Math.max(window.scrollY, 0)
  }

  return {
    isScrolled,
    isNavHidden,
    lastScrollY,
    handleScroll,
    scheduleHandleScroll,
    cancelScheduledScroll,
    resetNavState,
  }
}
