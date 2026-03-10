<template>
  <!-- 空数据时隐藏整个区块 -->
  <section
    v-if="!loaded || slides.length > 0"
    ref="heroCarouselRef"
    class="hero-carousel"
    @mouseenter="pauseAutoPlay"
    @mouseleave="resumeAutoPlay"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
  >
    <!-- 加载占位：仅用于锁定高度，避免刷新时页面跳动 -->
    <div v-if="!loaded" class="carousel-placeholder" aria-hidden="true"></div>

    <template v-else>
      <!-- 轮播幻灯片 -->
      <div class="carousel-slides">
        <div
          v-for="(slide, index) in slides"
          :key="slide.id"
          class="carousel-slide"
          :class="{
            active: hasHeroEntered && index === currentIndex,
            leaving: hasHeroEntered && index === prevIndex
          }"
        >
          <img
            :src="slide.imageUrl"
            :alt="slide.title"
            class="slide-bg"
            :class="{ 'slide-bg--loaded': loadedImages.has(index) }"
            @load="handleImageLoad(index)"
            @error="handleImageError(index)"
          />
          <!-- 渐变遮罩 -->
          <div class="slide-overlay" />
        </div>
      </div>

      <!-- 文字叠加层（点击整个轮播跳转） -->
      <div class="carousel-content" :key="currentIndex" @click="handleSlideClick(currentSlide!)">
        <h1 class="carousel-title">{{ currentSlide?.title }}</h1>
        <p v-if="currentSlide?.description" class="carousel-desc">
          {{ currentSlide.description }}
        </p>
      </div>

      <!-- 装饰粒子效果（<480px 隐藏） -->
      <div class="carousel-particles">
        <span
          v-for="i in 5"
          :key="i"
          class="particle"
          :class="`particle-${i}`"
        />
      </div>

      <!-- 左右切换箭头（多张时显示） -->
      <template v-if="slides.length > 1">
        <button class="carousel-arrow carousel-arrow--left" aria-label="上一张" @click="goToPrev">
          <Icon name="heroicons:chevron-left-20-solid" size="28" />
        </button>
        <button class="carousel-arrow carousel-arrow--right" aria-label="下一张" @click="goToNext">
          <Icon name="heroicons:chevron-right-20-solid" size="28" />
        </button>
      </template>

      <!-- 指示器圆点（带进度条） -->
      <div v-if="slides.length > 1" class="carousel-indicators">
        <button
          v-for="(_, index) in slides"
          :key="index"
          class="indicator-dot"
          :class="{ active: index === currentIndex }"
          :aria-label="`切换到第 ${index + 1} 张`"
          @click="goTo(index)"
        >
          <span
            v-if="index === currentIndex"
            class="indicator-progress"
            :key="progressKey"
            :style="{ animationPlayState: isPaused ? 'paused' : 'running' }"
          />
        </button>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { carouselApi, type CarouselVO } from '~/api/carousel'

// ===== 状态 =====
const slides = ref<CarouselVO[]>([])
const loaded = ref(false)
const heroCarouselRef = ref<HTMLElement | null>(null)
const currentIndex = ref(0)
const prevIndex = ref(-1)
const autoPlayTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const touchStartX = ref(0)
const isPaused = ref(false)
const progressKey = ref(0) // 用于重置进度条动画
const slideStartTime = ref(0) // 当前幻灯片开始时间
const elapsed = ref(0) // 暂停时已经过的毫秒数
const loadedImages = reactive(new Set<number>())
const hasHeroEntered = ref(false)
const SLIDE_DURATION = 5000
const SLIDE_TRANSITION_MS = 850

// ===== 计算属性 =====
const currentSlide = computed(() => slides.value[currentIndex.value] ?? null)

// ===== 数据加载 =====
async function loadCarousel() {
  try {
    const res = await carouselApi.listPortal()
    slides.value = res.data
  } catch {
    slides.value = []
  } finally {
    loaded.value = true
  }
}

// ===== 轮播控制 =====
function goTo(index: number) {
  if (index === currentIndex.value) return
  prevIndex.value = currentIndex.value
  currentIndex.value = index
  progressKey.value++ // 重置进度条动画
  // 清除上一次离场动画标记
  setTimeout(() => { prevIndex.value = -1 }, SLIDE_TRANSITION_MS)
  // 手动切换后重启自动轮播计时
  if (!isPaused.value) startAutoPlay()
}

function goToNext() {
  if (slides.value.length <= 1) return
  const next = (currentIndex.value + 1) % slides.value.length
  goTo(next)
}

function goToPrev() {
  if (slides.value.length <= 1) return
  const prev = (currentIndex.value - 1 + slides.value.length) % slides.value.length
  goTo(prev)
}

// ===== 自动轮播 =====
function startAutoPlay() {
  stopAutoPlay()
  if (slides.value.length <= 1) return
  slideStartTime.value = Date.now()
  elapsed.value = 0
  scheduleNext(SLIDE_DURATION)
}

function scheduleNext(delay: number) {
  stopAutoPlay()
  autoPlayTimer.value = setTimeout(() => {
    goToNext()
  }, delay)
}

function stopAutoPlay() {
  if (autoPlayTimer.value) {
    clearTimeout(autoPlayTimer.value)
    autoPlayTimer.value = null
  }
}

function pauseAutoPlay() {
  isPaused.value = true
  // 记录已经过的时间
  elapsed.value += Date.now() - slideStartTime.value
  stopAutoPlay()
}

function resumeAutoPlay() {
  isPaused.value = false
  if (slides.value.length <= 1) return
  const remaining = SLIDE_DURATION - elapsed.value
  slideStartTime.value = Date.now()
  if (remaining > 0) {
    scheduleNext(remaining)
  } else {
    goToNext()
  }
}

// ===== 触摸手势 =====
function handleTouchStart(e: TouchEvent) {
  const touch = e.touches[0]
  if (touch) touchStartX.value = touch.clientX
}

function handleTouchEnd(e: TouchEvent) {
  const touch = e.changedTouches[0]
  if (!touch) return
  const diff = touchStartX.value - touch.clientX
  const threshold = 50
  if (Math.abs(diff) < threshold) return
  if (diff > 0) goToNext()
  else goToPrev()
}

// ===== 点击跳转 =====
function handleSlideClick(slide: CarouselVO) {
  if (slide.type === 'article') {
    if (slide.slug) {
      navigateTo(`/post/${slide.slug}`)
    } else if (slide.linkUrl) {
      navigateTo(slide.linkUrl)
    }
  } else if (slide.type === 'image' && slide.linkUrl) {
    window.open(slide.linkUrl, '_blank')
  }
}

function triggerHeroEnter() {
  if (hasHeroEntered.value || !import.meta.client) return

  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      hasHeroEntered.value = true
    })
  })
}

function isCurrentSlideImageReady() {
  const imageNodes = heroCarouselRef.value?.querySelectorAll<HTMLImageElement>('.slide-bg')
  const currentImage = imageNodes?.[currentIndex.value]
  return Boolean(currentImage?.complete && currentImage.naturalWidth > 0)
}

function handleImageLoad(index: number) {
  loadedImages.add(index)

  if (index === currentIndex.value) {
    triggerHeroEnter()
  }
}

function handleImageError(index: number) {
  if (index === currentIndex.value) {
    triggerHeroEnter()
  }
}

// ===== 生命周期 =====
onMounted(() => {
  loadCarousel().then(() => {
    nextTick(() => {
      if (isCurrentSlideImageReady()) {
        loadedImages.add(currentIndex.value)
        triggerHeroEnter()
      }
    })

    if (slides.value.length > 1) {
      startAutoPlay()
    }
  })
})

onUnmounted(() => {
  stopAutoPlay()
})
</script>

<style lang="scss" scoped>
/* ===== 轮播容器 ===== */
.hero-carousel {
  position: relative;
  width: 100%;
  height: clamp(480px, 80vh, 720px);
  overflow: hidden;
  background: #0f172a;
  margin-top: -60px; // 覆盖到 fixed 导航栏下方
}

.carousel-placeholder {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0.92) 0%,
    rgba(15, 23, 42, 0.98) 100%
  );
}

/* ===== 幻灯片 ===== */
.carousel-slides {
  position: absolute;
  inset: 0;
}

.carousel-slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 850ms cubic-bezier(0.33, 1, 0.68, 1);
  pointer-events: none;
  will-change: opacity;

  &.active {
    opacity: 1;
    pointer-events: auto;
    z-index: 1;
  }

  &.leaving {
    opacity: 0;
    z-index: 0;
    transition: opacity 520ms ease-out;
  }
}

.slide-bg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transform: scale(1.08);
  transform-origin: center;
  transition:
    opacity 0.8s cubic-bezier(0.33, 1, 0.68, 1),
    transform 1.5s cubic-bezier(0.165, 0.84, 0.44, 1);
  will-change: opacity, transform;
  backface-visibility: hidden;

  &--loaded {
    opacity: 1;
  }
}

.carousel-slide.active .slide-bg {
  transform: scale(1.01);
}

.carousel-slide.leaving .slide-bg {
  transform: scale(1.03);
}

/* 渐变遮罩 */
.slide-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.7) 0%,
    rgba(0, 0, 0, 0.3) 40%,
    rgba(0, 0, 0, 0.15) 100%
  );

  .dark & {
    background: linear-gradient(
      to top,
      rgba(0, 0, 0, 0.85) 0%,
      rgba(0, 0, 0, 0.5) 40%,
      rgba(0, 0, 0, 0.3) 100%
    );
  }
}

/* ===== 文字叠加层 ===== */
.carousel-content {
  position: absolute;
  z-index: 2;
  bottom: 20%;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  width: 90%;
  max-width: 700px;
  color: #fff;
  cursor: pointer;
}

.carousel-title {
  font-size: 2.25rem;
  font-weight: 700;
  line-height: 1.3;
  margin-bottom: $spacing-sm;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  /* 入场动画：从下方淡入 */
  animation: fadeInUp 600ms ease both;
}

.carousel-desc {
  font-size: 1rem;
  line-height: 1.6;
  opacity: 0.9;
  margin-bottom: $spacing-sm;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  /* 入场动画：延迟 150ms */
  animation: fadeInUp 600ms ease 150ms both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 装饰粒子 ===== */
.carousel-particles {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: particleFloat 8s ease-in-out infinite;

  &.particle-1 {
    width: 6px;
    height: 6px;
    top: 20%;
    left: 15%;
    animation-delay: 0s;
    animation-duration: 7s;
  }

  &.particle-2 {
    width: 8px;
    height: 8px;
    top: 60%;
    left: 80%;
    animation-delay: 1.5s;
    animation-duration: 9s;
  }

  &.particle-3 {
    width: 5px;
    height: 5px;
    top: 35%;
    left: 65%;
    animation-delay: 3s;
    animation-duration: 6s;
  }

  &.particle-4 {
    width: 10px;
    height: 10px;
    top: 75%;
    left: 25%;
    animation-delay: 2s;
    animation-duration: 10s;
  }

  &.particle-5 {
    width: 4px;
    height: 4px;
    top: 45%;
    left: 45%;
    animation-delay: 4s;
    animation-duration: 8s;
  }
}

@keyframes particleFloat {
  0%, 100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.3;
  }
  25% {
    transform: translate(15px, -20px) scale(1.2);
    opacity: 0.6;
  }
  50% {
    transform: translate(-10px, -35px) scale(0.8);
    opacity: 0.4;
  }
  75% {
    transform: translate(20px, -15px) scale(1.1);
    opacity: 0.5;
  }
}

/* ===== 左右切换箭头 ===== */
.carousel-arrow {
  position: absolute;
  z-index: 3;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.25);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.3s, background 0.2s;
  backdrop-filter: blur(4px);

  &:hover {
    background: rgba(0, 0, 0, 0.45);
  }

  .hero-carousel:hover & {
    opacity: 1;
  }

  &--left {
    left: 24px;
  }

  &--right {
    right: 24px;
  }
}

/* ===== 指示器圆点 ===== */
.carousel-indicators {
  position: absolute;
  z-index: 3;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 0.5rem;
}

.indicator-dot {
  position: relative;
  width: 10px;
  height: 10px;
  border: none;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  padding: 0;
  overflow: hidden;
  transition: background 0.3s, width 0.3s;

  &.active {
    width: 36px;
    background: rgba(255, 255, 255, 0.3);
  }

  &:hover:not(.active) {
    background: rgba(255, 255, 255, 0.7);
  }
}

/* 进度条填充 */
.indicator-progress {
  position: absolute;
  inset: 0;
  border-radius: 5px;
  background: #fff;
  transform-origin: left center;
  animation: indicatorFill 5s linear forwards;
}

@keyframes indicatorFill {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

/* ===== 响应式 ===== */

/* 平板端 */
@media (max-width: $breakpoint-lg) {
  .hero-carousel {
    height: clamp(380px, 65vh, 580px);
  }

  .carousel-title {
    font-size: 1.75rem;
  }

  .carousel-desc {
    font-size: 0.9rem;
  }
}

/* 移动端 */
@media (max-width: $breakpoint-md) {
  .hero-carousel {
    height: clamp(280px, 50vh, 440px);
  }

  .carousel-title {
    font-size: 1.5rem;
  }

  .carousel-desc {
    font-size: 0.85rem;
    -webkit-line-clamp: 1;
  }

  .carousel-content {
    bottom: 25%;
  }

  .carousel-arrow {
    display: none;
  }

  .carousel-indicators {
    bottom: 50px;
  }
}

/* 超小屏：隐藏粒子 */
@media (max-width: 480px) {
  .carousel-particles {
    display: none;
  }

  .carousel-title {
    font-size: 1.25rem;
  }

  .carousel-desc {
    display: none;
  }

  .carousel-content {
    bottom: 30%;
  }
}

/* ===== 减少动画偏好 ===== */
@media (prefers-reduced-motion: reduce) {
  .carousel-slide {
    transition: opacity 200ms ease;
  }

  .slide-bg {
    transition: opacity 200ms ease;
    transform: none !important;
  }

  .particle {
    animation: none;
  }

  .carousel-title,
  .carousel-desc {
    animation: none;
  }

  .indicator-progress {
    animation: none;
    transform: scaleX(1);
  }
}
</style>
