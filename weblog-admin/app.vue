<template>
  <ElConfigProvider :locale="zhCn">
    <NuxtLayout>
      <NuxtPage :transition="{ name: 'page', mode: 'out-in' }" />
    </NuxtLayout>
    <Transition name="startup-fade">
      <StartupSplash v-if="showStartup" />
    </Transition>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const route = useRoute()
const showStartup = ref(route.path !== '/login')
let startupTimer: ReturnType<typeof window.setTimeout> | null = null

onMounted(() => {
  if (!showStartup.value) {
    return
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const duration = prefersReducedMotion ? 220 : 1200
  startupTimer = window.setTimeout(() => {
    showStartup.value = false
  }, duration)
})

onBeforeUnmount(() => {
  if (startupTimer) {
    window.clearTimeout(startupTimer)
  }
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
