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
      style="position:fixed;inset:0;z-index:90000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:var(--startup-mask-bg,radial-gradient(120% 120% at 0% 0%,rgba(59,130,246,0.13),transparent 45%),radial-gradient(120% 120% at 100% 100%,rgba(56,189,248,0.1),transparent 52%),linear-gradient(180deg,#171b20,#101215));"
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
  <LoginModal />
  <AdApplyModal />
  <BackToTop />
</template>

<script setup lang="ts">
import MessageContainer from '~/components/ui/message/MessageContainer.vue'
import ConfirmDialog from '~/components/ui/confirm/ConfirmDialog.vue'
import LoginModal from '~/components/LoginModal.vue'
import AdApplyModal from '~/components/AdApplyModal.vue'
import BackToTop from '~/components/BackToTop.vue'
import StartupCubeLoader from '~/components/StartupCubeLoader.vue'

const route = useRoute()
const showStartup = ref(route.path !== '/error')
let startupTimer: ReturnType<typeof window.setTimeout> | null = null
const STARTUP_DONE_EVENT = 'weblog:startup-done'
const hiddenAppShellStyle = 'opacity:0;visibility:hidden;pointer-events:none;'
const visibleAppShellStyle = 'opacity:1;visibility:visible;pointer-events:auto;transition:opacity 220ms ease;'

function markStartupDone() {
  const runtimeWindow = window as Window & { __weblogStartupDone?: boolean }
  if (runtimeWindow.__weblogStartupDone) {
    return
  }

  runtimeWindow.__weblogStartupDone = true
  window.dispatchEvent(new CustomEvent(STARTUP_DONE_EVENT))
}

onMounted(() => {
  if (!showStartup.value) {
    markStartupDone()
    return
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const duration = prefersReducedMotion ? 180 : 920
  startupTimer = window.setTimeout(() => {
    showStartup.value = false
    window.setTimeout(() => {
      markStartupDone()
    }, 220)
  }, duration)
})

onBeforeUnmount(() => {
  if (startupTimer) {
    window.clearTimeout(startupTimer)
  }
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
