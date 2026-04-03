<template>
  <ElConfigProvider :locale="zhCn">
    <NuxtLayout>
      <NuxtPage :transition="{ name: 'page' }" />
    </NuxtLayout>
    <Transition name="startup-fade">
      <StartupSplash v-if="showStartup" />
    </Transition>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const route = useRoute()
const nuxtApp = useNuxtApp()
const showStartup = ref(false)
const hasPlayedStartup = ref(false)
const isPageLoading = ref(false)
const startupMinDurationReached = ref(false)
let startupTimer: ReturnType<typeof setTimeout> | null = null

function clearStartupTimer() {
  if (startupTimer) {
    clearTimeout(startupTimer)
    startupTimer = null
  }
}

function hideStartupWhenReady() {
  if (!showStartup.value || !startupMinDurationReached.value || isPageLoading.value) {
    return
  }

  showStartup.value = false
  hasPlayedStartup.value = true
}

function playStartup() {
  if (showStartup.value || hasPlayedStartup.value) {
    return
  }

  showStartup.value = true
  startupMinDurationReached.value = false

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const duration = prefersReducedMotion ? 220 : 1200
  clearStartupTimer()
  startupTimer = setTimeout(() => {
    startupMinDurationReached.value = true
    hideStartupWhenReady()
  }, duration)
}

watch(
  () => route.path,
  (path) => {
    if (path !== '/login') {
      playStartup()
    }
  },
  { immediate: true },
)

nuxtApp.hook('page:loading:start', () => {
  isPageLoading.value = true
})

nuxtApp.hook('page:loading:end', () => {
  isPageLoading.value = false
  hideStartupWhenReady()
})

onNuxtReady(() => {
  isPageLoading.value = false
  hideStartupWhenReady()
})

onBeforeUnmount(() => {
  clearStartupTimer()
})
</script>

<style scoped>
.startup-fade-enter-active,
.startup-fade-leave-active {
  transition: opacity 0.3s ease;
}

.startup-fade-enter-from,
.startup-fade-leave-to {
  opacity: 0;
}
</style>
