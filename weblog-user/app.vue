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
      style="position:fixed;inset:0;z-index:90000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:#f7f9ff;"
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

<style scoped>
.startup-mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-startup, 90000);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background:
    radial-gradient(circle at 22% 20%, rgba(91, 141, 239, 0.18), transparent 44%),
    radial-gradient(circle at 78% 24%, rgba(91, 141, 239, 0.14), transparent 46%),
    #f7f9ff;
}

:global(html.dark) .startup-mask {
  background:
    radial-gradient(circle at 20% 16%, rgba(91, 141, 239, 0.24), transparent 42%),
    radial-gradient(circle at 82% 22%, rgba(91, 141, 239, 0.18), transparent 46%),
    #0f1728;
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
