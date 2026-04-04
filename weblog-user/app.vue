<template>
  <div :style="showStartup ? hiddenAppShellStyle : visibleAppShellStyle">
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </div>
  <Transition name="startup-fade">
    <div
      v-if="showStartup"
      class="startup-mask"
      style="position:fixed;inset:0;z-index:90000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:var(--startup-mask-bg);"
      role="status"
      aria-live="polite"
      aria-busy="true"
      aria-label="页面加载中"
    >
      <StartupCubeLoader />
    </div>
  </Transition>
  <MessageContainer />
  <ConfirmDialog />
  <BackToTop />
  <GlobalScrollbar />
</template>

<script setup lang="ts">
import MessageContainer from '~/components/ui/message/MessageContainer.vue'
import ConfirmDialog from '~/components/ui/confirm/ConfirmDialog.vue'
import BackToTop from '~/components/common/BackToTop.vue'
import GlobalScrollbar from '~/components/common/GlobalScrollbar.vue'
import StartupCubeLoader from '~/components/common/StartupCubeLoader.vue'

const route = useRoute()
const showStartup = ref(route.path !== '/error')
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const STARTUP_FADE_MS = 220
const STARTUP_MIN_VISIBLE_MS = 260
const STARTUP_MAX_VISIBLE_MS = 920
const STARTUP_MIN_VISIBLE_REDUCED_MS = 120
const STARTUP_MAX_VISIBLE_REDUCED_MS = 240
const hiddenAppShellStyle = 'opacity:0;visibility:hidden;pointer-events:none;'
const visibleAppShellStyle = 'opacity:1;visibility:visible;pointer-events:auto;transition:opacity 220ms ease;'
let startupVisibleStartAt = 0
let startupSettled = false
let startupRevealTimer: ReturnType<typeof window.setTimeout> | null = null
let startupDoneTimer: ReturnType<typeof window.setTimeout> | null = null
let startupFallbackTimer: ReturnType<typeof window.setTimeout> | null = null
let startupReadyRafId: number | null = null
let startupReadyRafTailId: number | null = null
let startupLoadListener: (() => void) | null = null

function markStartupDone() {
  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  if (runtimeWindow.__weblogStartupDone) {
    return
  }

  runtimeWindow.__weblogStartupDone = true
  window.dispatchEvent(new CustomEvent(STARTUP_DONE_EVENT))
}

function clearStartupLifecycleHandles() {
  if (startupRevealTimer) {
    window.clearTimeout(startupRevealTimer)
    startupRevealTimer = null
  }

  if (startupDoneTimer) {
    window.clearTimeout(startupDoneTimer)
    startupDoneTimer = null
  }

  if (startupFallbackTimer) {
    window.clearTimeout(startupFallbackTimer)
    startupFallbackTimer = null
  }

  if (startupReadyRafId !== null) {
    window.cancelAnimationFrame(startupReadyRafId)
    startupReadyRafId = null
  }

  if (startupReadyRafTailId !== null) {
    window.cancelAnimationFrame(startupReadyRafTailId)
    startupReadyRafTailId = null
  }

  if (startupLoadListener) {
    window.removeEventListener('load', startupLoadListener)
    startupLoadListener = null
  }
}

function revealAppShellAndMarkDone() {
  if (!showStartup.value) {
    markStartupDone()
    return
  }

  showStartup.value = false
  startupDoneTimer = window.setTimeout(() => {
    startupDoneTimer = null
    markStartupDone()
  }, STARTUP_FADE_MS)
}

function settleStartup(minVisibleMs: number) {
  if (startupSettled) {
    return
  }

  startupSettled = true
  if (startupFallbackTimer) {
    window.clearTimeout(startupFallbackTimer)
    startupFallbackTimer = null
  }

  if (startupLoadListener) {
    window.removeEventListener('load', startupLoadListener)
    startupLoadListener = null
  }

  if (startupReadyRafId !== null) {
    window.cancelAnimationFrame(startupReadyRafId)
    startupReadyRafId = null
  }

  if (startupReadyRafTailId !== null) {
    window.cancelAnimationFrame(startupReadyRafTailId)
    startupReadyRafTailId = null
  }

  const elapsed = Date.now() - startupVisibleStartAt
  const waitMs = Math.max(0, minVisibleMs - elapsed)
  startupRevealTimer = window.setTimeout(() => {
    startupRevealTimer = null
    revealAppShellAndMarkDone()
  }, waitMs)
}

function scheduleStartupReady(onReady: () => void) {
  const runAfterTwoFrames = () => {
    startupReadyRafId = window.requestAnimationFrame(() => {
      startupReadyRafId = null
      startupReadyRafTailId = window.requestAnimationFrame(() => {
        startupReadyRafTailId = null
        onReady()
      })
    })
  }

  if (document.readyState === 'complete') {
    runAfterTwoFrames()
    return
  }

  startupLoadListener = () => {
    if (!startupLoadListener) {
      return
    }
    window.removeEventListener('load', startupLoadListener)
    startupLoadListener = null
    runAfterTwoFrames()
  }

  window.addEventListener('load', startupLoadListener, { once: true })
}

onMounted(() => {
  if (!showStartup.value) {
    markStartupDone()
    return
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const minVisibleMs = prefersReducedMotion ? STARTUP_MIN_VISIBLE_REDUCED_MS : STARTUP_MIN_VISIBLE_MS
  const maxVisibleMs = prefersReducedMotion ? STARTUP_MAX_VISIBLE_REDUCED_MS : STARTUP_MAX_VISIBLE_MS

  startupVisibleStartAt = Date.now()
  startupSettled = false

  /**
   * 启动遮罩策略：
   * - 最短展示时长，避免一闪而过导致视觉抖动；
   * - 首帧稳定后立即退出，减少不必要等待；
   * - 最大时长兜底，避免异常场景卡在遮罩层。
   */
  startupFallbackTimer = window.setTimeout(() => {
    startupFallbackTimer = null
    settleStartup(minVisibleMs)
  }, maxVisibleMs)

  scheduleStartupReady(() => {
    settleStartup(minVisibleMs)
  })
})

onBeforeUnmount(() => {
  clearStartupLifecycleHandles()
})

// 全局图片懒加载淡入效果
useLazyImages()
</script>

<style>
.startup-mask {
  z-index: var(--z-startup, 90000);
}

.startup-fade-enter-active,
.startup-fade-leave-active {
  transition: opacity 220ms ease;
}

.startup-fade-enter-from,
.startup-fade-leave-to {
  opacity: 0;
}
</style>
