<template>
  <div
    class="back-to-top"
    title="返回顶部"
    :class="{ 'is-visible': visible }"
    @click="scrollToTop"
  >
    <!-- 环形进度条 -->
    <svg class="progress-ring" width="52" height="52" viewBox="0 0 52 52">
      <circle
        class="progress-ring__circle"
        stroke-width="2"
        fill="transparent"
        r="23"
        cx="26"
        cy="26"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <!-- 内圆按钮 -->
    <div class="back-to-top__inner">
      <Icon name="heroicons:chevron-up-20-solid" size="20" />
    </div>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  threshold?: number
}>(), {
  threshold: 100,
})

const visible = ref(false)
const progress = ref(0)

const radius = 23
const circumference = 2 * Math.PI * radius
const dashOffset = computed(() => circumference * (1 - progress.value / 100))

let bodyMutationObserver: MutationObserver | null = null

function handleScroll() {
  visible.value = window.scrollY > props.threshold
  const total = document.documentElement.scrollHeight - window.innerHeight
  progress.value = total > 0 ? Math.min(Math.round((window.scrollY / total) * 100), 100) : 0
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleScroll, { passive: true })

  if (typeof MutationObserver !== 'undefined') {
    bodyMutationObserver = new MutationObserver(() => {
      handleScroll()
    })
    bodyMutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
    })
  }

  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleScroll)
  bodyMutationObserver?.disconnect()
  bodyMutationObserver = null
})
</script>

<style lang="scss" scoped>
$btn-size: 46px;

.back-to-top {
  position: fixed;
  right: 40px;
  bottom: 70px;
  width: $btn-size;
  height: $btn-size;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 999;
  opacity: 0;
  visibility: hidden;
  transform: translateY(20px);
  transition: opacity 0.3s, visibility 0.3s, transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform, opacity;

  &.is-visible {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
  }

  &__inner {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    transition: background 0.3s;

    &:hover {
      background: rgba(0, 0, 0, 0.6);
    }
  }
}

.progress-ring {
  position: absolute;
  top: -3px;
  left: -3px;
  width: 52px;
  height: 52px;
  transform: rotate(-90deg);
  pointer-events: none;
  filter: drop-shadow(0 0 1px rgba(0, 0, 0, 0.5));

  &__circle {
    stroke: #fff;
    stroke-linecap: round;
    transition: stroke-dashoffset 0.3s;
    filter: drop-shadow(0 0 1px rgba(255, 255, 255, 0.3));
  }
}

@media (max-width: 768px) {
  .back-to-top {
    right: 20px;
    bottom: 40px;
  }
}
</style>
