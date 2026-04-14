<template>
  <div
    class="floating-page-indicator"
    :class="{
      'is-visible': visible,
      'is-above-filter': shouldLiftForFilter,
    }"
    aria-live="polite"
  >
    <span class="page-text">{{ state.currentPage }}/{{ state.totalPages }}</span>
  </div>
</template>

<script setup lang="ts">
const { state } = useFloatingPageIndicator()
const route = useRoute()
const scrollVisible = ref(false)

const visible = computed(() => state.value.enabled && state.value.totalPages > 1 && scrollVisible.value)
const shouldLiftForFilter = computed(() => route.path.startsWith('/category'))

function syncScrollVisible() {
  if (!import.meta.client) return
  scrollVisible.value = window.scrollY > 100
}

onMounted(() => {
  syncScrollVisible()
  window.addEventListener('scroll', syncScrollVisible, { passive: true })
  window.addEventListener('resize', syncScrollVisible, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', syncScrollVisible)
  window.removeEventListener('resize', syncScrollVisible)
})
</script>

<style lang="scss" scoped>
.floating-page-indicator {
  position: fixed;
  right: 20px;
  bottom: calc(72px + 46px + 12px);
  z-index: 997;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.45);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(8px);
  opacity: 0;
  visibility: hidden;
  transform: translateY(20px);
  transition: opacity 0.3s, visibility 0.3s, transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;

  &.is-visible {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
  }

  &.is-above-filter {
    bottom: calc(72px + 46px + 12px + 46px + 12px);
  }
}

:global(body.with-comment-bottom-bar) .floating-page-indicator {
  bottom: calc(var(--comment-bottom-bar-avoidance, 0px) + env(safe-area-inset-bottom) + 34px + 46px + 12px);
}

:global(body.with-comment-bottom-bar) .floating-page-indicator.is-above-filter {
  bottom: calc(var(--comment-bottom-bar-avoidance, 0px) + env(safe-area-inset-bottom) + 34px + 46px + 12px + 46px + 12px);
}

.page-text {
  font-size: 0.76rem;
  line-height: 1;
  font-weight: 600;
  letter-spacing: 0.02em;
}

@media (min-width: 769px) {
  .floating-page-indicator {
    display: none;
  }
}
</style>
