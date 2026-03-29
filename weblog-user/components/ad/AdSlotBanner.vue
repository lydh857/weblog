<template>
  <Transition :name="bannerTransitionName">
    <div
      v-if="showBanner && currentAd"
      :class="['ad-slot-banner-wrap', `ad-slot-banner-wrap--${adSlot}`]"
    >
      <aside
        class="ad-slot-banner"
        :class="[`ad-slot-banner--${adSlot}`]"
        @mouseenter="handleMouseEnter"
        @mouseleave="handleMouseLeave"
      >
        <button
          v-if="currentAd.closable"
          class="ad-close"
          type="button"
          aria-label="关闭广告"
          @click="handleClose"
        >
          <Icon name="heroicons:x-mark-16-solid" size="16" />
        </button>

        <div
          class="ad-slide-window"
          @touchstart="handleTouchStart"
          @touchmove="handleTouchMove"
          @touchend="handleTouchEnd"
          @touchcancel="handleTouchCancel"
        >
          <div class="ad-slides" :style="slidesStyle">
            <div
              v-for="(ad, index) in ads"
              :key="`ad-${ad.id}`"
              class="ad-slide-item"
              :class="{
                active: index === currentIndex,
                leaving: index === prevIndex,
              }"
            >
              <template v-if="ad.type === 'image'">
                <div class="ad-image-wrap" :class="{ 'is-broken': isImageFailed(ad.id) }">
                  <a
                    v-if="ad.safeLinkUrl"
                    class="ad-link"
                    :href="ad.safeLinkUrl"
                    target="_blank"
                    rel="noopener noreferrer nofollow"
                    @click="handleClick(ad.id)"
                  >
                    <img
                      :src="ad.content"
                      :alt="ad.title"
                      class="ad-image"
                      :class="{ 'is-error': isImageFailed(ad.id) }"
                      loading="lazy"
                      @error="handleImageError(ad.id)"
                    >
                    <div v-if="isImageFailed(ad.id)" class="ad-image-fallback">
                      <Icon name="heroicons:photo-16-solid" size="16" />
                      <span>广告图片加载失败</span>
                    </div>
                  </a>
                  <div v-else class="ad-link">
                    <img
                      :src="ad.content"
                      :alt="ad.title"
                      class="ad-image"
                      :class="{ 'is-error': isImageFailed(ad.id) }"
                      loading="lazy"
                      @error="handleImageError(ad.id)"
                    >
                    <div v-if="isImageFailed(ad.id)" class="ad-image-fallback">
                      <Icon name="heroicons:photo-16-solid" size="16" />
                      <span>广告图片加载失败</span>
                    </div>
                  </div>
                  <span v-if="ad.adInfo && !isImageFailed(ad.id)" class="ad-info">{{ ad.adInfo }}</span>
                </div>
              </template>
              <!-- 已经过 DOMPurify 白名单净化 -->
              <!-- eslint-disable-next-line vue/no-v-html -->
              <div v-else class="ad-code" v-html="sanitize(ad.content)" />
            </div>
          </div>
        </div>

        <div v-if="ads.length > 1" class="ad-indicators" role="tablist" aria-label="广告切换">
          <button
            v-for="(item, index) in ads"
            :key="item.id"
            class="indicator-dot"
            :class="{ active: index === currentIndex }"
            :aria-label="`切换到广告 ${index + 1}`"
            :aria-selected="index === currentIndex"
            @click="handleIndicatorClick(index)"
          >
            <span
              v-if="index === currentIndex && canAutoRotate"
              :key="progressKey"
              class="indicator-progress"
              :style="{
                animationDuration: `${currentRotateDurationMs}ms`,
                animationPlayState: isPaused ? 'paused' : 'running'
              }"
            />
          </button>
        </div>

        <div v-if="canShowApplyButton" class="ad-action-float">
          <button
            class="ad-apply-btn"
            type="button"
            @click.stop="handleApplyClick"
          >
            {{ applyButtonLabel }}
          </button>
        </div>

        <span class="ad-badge">广告</span>
      </aside>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify'
import { advertisementApi, type AdvertisementVO } from '~/api/marketing/advertisement'
import { fetchCachedAdSlot } from '~/composables/cache/useNonCriticalApiCache'
import { useLoginModal } from '~/composables/modal/useLoginModal'
import { useUserStore } from '~/stores/user'
import { normalizeSafeHref } from '~/utils/security/urlSafety'

interface BannerAd extends AdvertisementVO {
  safeLinkUrl: string | null
}

const emit = defineEmits<{
  closed: []
}>()

const props = withDefaults(defineProps<{
  adSlot: string
  visible?: boolean
  forceRotate?: boolean
}>(), {
  visible: true,
  forceRotate: false
})

const ads = ref<BannerAd[]>([])
const currentIndex = ref(0)
const prevIndex = ref(-1)
const closed = ref(false)
const loaded = ref(false)
const showBanner = ref(false)
const rotateTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const progressKey = ref(0)
const isPaused = ref(false)
const slideStartTime = ref(0)
const elapsed = ref(0)
const slideTransitionMs = 820
const failedImageIds = ref<Set<number>>(new Set())
const imageRatioMap = ref<Record<number, number>>({})
let touchTracking = false
let touchStartX = 0
let touchStartY = 0
let touchDeltaX = 0
let touchDeltaY = 0
const SWIPE_TRIGGER_X = 32

const currentAd = computed(() => ads.value[currentIndex.value] || null)
const currentRotateDurationMs = computed(() => {
  const current = currentAd.value
  return Math.max(2, current?.rotateIntervalSec || 6) * 1000
})
const canAutoRotate = computed(() => {
  if (!showBanner.value || ads.value.length <= 1) return false
  const current = currentAd.value
  if (!current) return false
  return props.forceRotate || Boolean(current.autoRotate)
})
const { applyEnabled, loadAdApplyConfig } = useAdApplyConfig()
const userStore = useUserStore()
const loginModal = useLoginModal()
const adApplyModal = useAdApplyModal()
const myApplication = ref<AdvertisementVO | null>(null)
const canShowApplyButton = computed(() => {
  if (!applyEnabled.value) return false
  if (!showBanner.value || !currentAd.value) return false
  if (!currentAd.value.pitEnabled) return false
  return ['home_left', 'post_top', 'post_bottom'].includes(props.adSlot)
})
const applyButtonLabel = computed(() => {
  const status = myApplication.value?.status
  if (status === 'active') return '查看推广'
  if (status === 'pending' || status === 'rejected') return '查看申请'
  if (status === 'expired') return '重新申请'
  return '申请投放'
})
const bannerTransitionName = computed(() => {
  return props.adSlot === 'home_left' ? 'ad-fade-left' : 'ad-fade-slide'
})
const slidesStyle = computed(() => {
  if (props.adSlot !== 'home_left') return undefined
  const current = currentAd.value
  if (!current || current.type !== 'image') return undefined
  const ratio = imageRatioMap.value[current.id]
  if (!ratio || !Number.isFinite(ratio) || ratio <= 0) return undefined
  return {
    '--home-left-aspect-ratio': `${ratio}`
  }
})

function sanitize(html: string) {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['a', 'img', 'span', 'div', 'p', 'strong', 'em'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'target', 'rel', 'class', 'style'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|\/(?!\/)|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  })
}

function isImageFailed(id: number) {
  return failedImageIds.value.has(id)
}

function handleImageError(id: number) {
  if (failedImageIds.value.has(id)) return
  const next = new Set(failedImageIds.value)
  next.add(id)
  failedImageIds.value = next
}

function clearRotateTimer() {
  if (!rotateTimer.value) return
  clearTimeout(rotateTimer.value)
  rotateTimer.value = null
}

function scheduleNextRotate(delay: number) {
  clearRotateTimer()
  if (delay <= 0) {
    goTo(currentIndex.value + 1)
    return
  }
  rotateTimer.value = setTimeout(() => {
    goTo(currentIndex.value + 1)
  }, delay)
}

function startRotateIfNeeded() {
  clearRotateTimer()
  if (!canAutoRotate.value) return
  isPaused.value = false
  elapsed.value = 0
  slideStartTime.value = Date.now()
  progressKey.value += 1
  scheduleNextRotate(currentRotateDurationMs.value)
}

function pauseRotate() {
  if (!canAutoRotate.value || isPaused.value) return
  isPaused.value = true
  elapsed.value += Date.now() - slideStartTime.value
  clearRotateTimer()
}

function resumeRotate() {
  if (!canAutoRotate.value || !isPaused.value) return
  const remaining = currentRotateDurationMs.value - elapsed.value
  isPaused.value = false
  slideStartTime.value = Date.now()
  scheduleNextRotate(remaining)
}

function goTo(index: number) {
  if (ads.value.length === 0) return
  const normalized = (index + ads.value.length) % ads.value.length
  if (normalized === currentIndex.value) return
  prevIndex.value = currentIndex.value
  currentIndex.value = normalized

  setTimeout(() => {
    prevIndex.value = -1
  }, slideTransitionMs)

  startRotateIfNeeded()
}

function handleIndicatorClick(index: number) {
  goTo(index)
}

function handleClose() {
  closed.value = true
  showBanner.value = false
  clearRotateTimer()
  emit('closed')
}

function handleMouseEnter() {
  pauseRotate()
}

function handleMouseLeave() {
  resumeRotate()
}

function handleClick(id: number) {
  advertisementApi.recordClick(id).catch(() => {})
}

function handleApplyClick() {
  if (!applyEnabled.value) return
  const pitAdId = currentAd.value?.id
  if (!pitAdId) return

  if (!userStore.isLoggedIn) {
    loginModal.open('code', () => {
      adApplyModal.open(props.adSlot, { step: 1, pitAdId })
    })
    return
  }

  const status = myApplication.value?.status
  if (status === 'active' || status === 'pending' || status === 'rejected') {
    adApplyModal.open(props.adSlot, { step: 3, pitAdId })
    return
  }
  if (status === 'expired') {
    adApplyModal.open(props.adSlot, { step: 1, pitAdId })
    return
  }
  adApplyModal.open(props.adSlot, { step: 1, pitAdId })
}

function resetTouchState() {
  touchTracking = false
  touchStartX = 0
  touchStartY = 0
  touchDeltaX = 0
  touchDeltaY = 0
}

function handleTouchStart(event: TouchEvent) {
  if (ads.value.length <= 1) return

  const touch = event.touches[0]
  if (!touch) return

  touchTracking = true
  touchStartX = touch.clientX
  touchStartY = touch.clientY
  touchDeltaX = 0
  touchDeltaY = 0
  pauseRotate()
}

function handleTouchMove(event: TouchEvent) {
  if (!touchTracking) return
  const touch = event.touches[0]
  if (!touch) return

  touchDeltaX = touch.clientX - touchStartX
  touchDeltaY = touch.clientY - touchStartY
}

function handleTouchEnd() {
  if (!touchTracking) return

  const absX = Math.abs(touchDeltaX)
  const absY = Math.abs(touchDeltaY)
  const isHorizontalSwipe = absX >= SWIPE_TRIGGER_X && absX > absY * 1.15

  if (isHorizontalSwipe) {
    if (touchDeltaX < 0) {
      goTo(currentIndex.value + 1)
    } else {
      goTo(currentIndex.value - 1)
    }
    resetTouchState()
    return
  }

  resetTouchState()
  resumeRotate()
}

function handleTouchCancel() {
  if (!touchTracking) return
  resetTouchState()
  resumeRotate()
}

function collectImageRatios() {
  if (!import.meta.client) return

  for (const ad of ads.value) {
    if (ad.type !== 'image') continue
    if (!ad.content || imageRatioMap.value[ad.id]) continue

    const img = new Image()
    img.decoding = 'async'
    img.onload = () => {
      if (!img.naturalWidth || !img.naturalHeight) return
      const ratio = img.naturalWidth / img.naturalHeight
      if (!Number.isFinite(ratio) || ratio <= 0) return

      imageRatioMap.value = {
        ...imageRatioMap.value,
        [ad.id]: ratio
      }
    }
    img.src = ad.content
  }
}

async function loadMyApplicationStatus() {
  if (!userStore.isLoggedIn) {
    myApplication.value = null
    return
  }
  try {
    const res = await advertisementApi.getMyApplication(props.adSlot)
    myApplication.value = res.data
  } catch {
    myApplication.value = null
  }
}

watch(() => props.visible, (visible) => {
  if (!loaded.value || closed.value) return
  showBanner.value = visible
  if (visible) {
    startRotateIfNeeded()
  } else {
    isPaused.value = false
    clearRotateTimer()
  }
}, { immediate: true })

watch(() => userStore.isLoggedIn, async (loggedIn) => {
  if (!loggedIn) {
    myApplication.value = null
    return
  }
  await loadMyApplicationStatus()
}, { immediate: true })

watch(() => adApplyModal.applicationVersion.value, async () => {
  await loadMyApplicationStatus()
})

onMounted(async () => {
  void loadAdApplyConfig()
  void loadMyApplicationStatus()
  try {
    // 广告位数据使用短期缓存：避免同一路由阶段重复挂载导致重复拉取。
    const cachedAds = await fetchCachedAdSlot(props.adSlot, { ttlMs: 30_000 })
    failedImageIds.value = new Set()
    imageRatioMap.value = {}
    ads.value = cachedAds.map(item => ({
      ...item,
      safeLinkUrl: normalizeSafeHref(item.linkUrl)
    }))
    collectImageRatios()
  } catch {
    ads.value = []
    imageRatioMap.value = {}
  } finally {
    loaded.value = true
    if (!closed.value && props.visible && ads.value.length > 0) {
      showBanner.value = true
      currentIndex.value = 0
      prevIndex.value = -1
      startRotateIfNeeded()
    }
  }
})

onUnmounted(() => {
  clearRotateTimer()
})
</script>

<style scoped lang="scss">
.ad-slot-banner {
  --ad-media-bg: linear-gradient(180deg, #f3f6fb, #e8edf5);
  --ad-media-bg-dark:
    radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
    radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
    linear-gradient(180deg, #171b20, #101215);
  --ad-media-overlay: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 52%,
    rgba(15, 23, 42, 0.18) 80%,
    rgba(15, 23, 42, 0.28) 100%
  );

  position: relative;
  border-radius: $radius-lg;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: $color-bg;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
  transition: box-shadow 0.3s ease;

  .dark & {
    --ad-media-bg: var(--ad-media-bg-dark);
    --ad-media-overlay: linear-gradient(
      180deg,
      rgba(2, 6, 23, 0) 46%,
      rgba(2, 6, 23, 0.34) 82%,
      rgba(2, 6, 23, 0.5) 100%
    );

    border-color: $color-dark-border;
    background: $color-dark-bg-secondary;
    box-shadow: 0 10px 26px rgba(2, 6, 23, 0.34);
  }
}

.ad-slot-banner-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ad-slide-window {
  overflow: hidden;
  position: relative;
  touch-action: pan-y;
}

.ad-slides {
  position: relative;
  width: 100%;
  min-height: 100px;
}

.ad-slide-item {
  position: absolute;
  inset: 0;
  width: 100%;
  opacity: 0;
  pointer-events: none;
  transition: opacity 820ms cubic-bezier(0.33, 1, 0.68, 1);

  &.active {
    opacity: 1;
    z-index: 1;
    pointer-events: auto;
  }

  &.leaving {
    opacity: 0;
    z-index: 0;
    transition: opacity 520ms ease-out;
  }
}

.ad-image-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--ad-media-bg);
}

.ad-link,
.ad-image {
  display: block;
  width: 100%;
  height: 100%;
}

.ad-link {
  height: 100%;
  position: relative;
  z-index: 0;
}

.ad-image {
  object-fit: cover;
  object-position: center;
  background: var(--ad-media-bg);
  opacity: 0;
  transition: opacity 0.55s ease;
  position: relative;
  z-index: 0;
}

.ad-image.is-error {
  opacity: 0 !important;
}

.ad-image-fallback {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  padding: 0.75rem;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.7), rgba(15, 23, 42, 0.86));
  color: rgba(255, 255, 255, 0.95);
  font-size: 0.72rem;
  text-align: center;

  .dark & {
    background: var(--ad-media-bg-dark);
    color: #cbd5e1;
  }
}

.ad-image-wrap.is-broken::after {
  background: none;
}

.ad-image-wrap::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: var(--ad-media-overlay);
}

.ad-slide-item.active .ad-image {
  opacity: 1;
}

.ad-info {
  position: absolute;
  left: 0.5rem;
  right: 0.5rem;
  bottom: 1.6rem;
  z-index: 2;
  font-size: 0.72rem;
  line-height: 1.4;
  font-weight: 600;
  letter-spacing: 0.01em;
  text-align: center;
  color: #ffffff;
  -webkit-text-fill-color: #ffffff;
  text-shadow:
    0 1px 0 rgba(255, 255, 255, 0.22),
    0 2px 8px rgba(0, 0, 0, 0.42),
    0 10px 20px rgba(0, 0, 0, 0.28);
  opacity: 1;
  pointer-events: none;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ad-code {
  padding: 0.75rem;
  min-height: 140px;
}

.ad-badge {
  position: absolute;
  left: 0.38rem;
  top: 0.36rem;
  z-index: 2;
  font-size: 0.56rem;
  letter-spacing: 0.02em;
  background: rgba(15, 23, 42, 0.36);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 0.08rem 0.42rem;
  border-radius: 999px;
  backdrop-filter: blur(2px);
  pointer-events: none;
}

.ad-close {
  position: absolute;
  right: 0.45rem;
  top: 0.45rem;
  z-index: 2;
  width: 24px;
  height: 24px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.56);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transform: translate3d(0, -3px, 0);
  pointer-events: none;
  transition: opacity 180ms ease, transform 220ms ease, background 180ms ease, box-shadow 180ms ease;

  &:hover {
    background: rgba(220, 38, 38, 0.9);
    box-shadow: 0 4px 12px rgba(220, 38, 38, 0.35);
  }

  &:focus-visible {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
    outline: 2px solid rgba(255, 255, 255, 0.7);
    outline-offset: 1px;
  }
}

.ad-apply-btn {
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  padding: 0.34rem 0.72rem;
  font-size: 0.7rem;
  font-weight: 600;
  color: #f8fafc;
  background: rgba(15, 23, 42, 0.62);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.28);
  cursor: pointer;
  transition: border-color 180ms ease, color 180ms ease, background 180ms ease, transform 180ms ease, box-shadow 180ms ease;

  &:hover {
    border-color: rgba(226, 232, 240, 0.42);
    color: #ffffff;
    background: rgba(30, 41, 59, 0.86);
    transform: translate3d(0, -1px, 0);
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.34);
  }

  &:focus-visible {
    outline: 2px solid rgba(255, 255, 255, 0.7);
    outline-offset: 1px;
  }

  .dark & {
    border-color: rgba(148, 163, 184, 0.42);
    background: rgba(2, 6, 23, 0.74);
    color: #e2e8f0;

    &:hover {
      border-color: rgba(203, 213, 225, 0.52);
      background: rgba(15, 23, 42, 0.92);
      color: #f8fafc;
      box-shadow: 0 8px 20px rgba(2, 6, 23, 0.4);
    }
  }
}

.ad-action-float {
  position: absolute;
  left: 0.48rem;
  top: 0.48rem;
  z-index: 4;
  opacity: 0;
  transform: translate3d(0, -8px, 0);
  pointer-events: none;
  transition: opacity 220ms ease, transform 240ms ease;
}

.ad-indicators {
  position: absolute;
  z-index: 2;
  left: 50%;
  bottom: 0.34rem;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.32rem;
}

.indicator-dot {
  position: relative;
  width: 7px;
  height: 7px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.5);
  padding: 0;
  cursor: pointer;
  overflow: hidden;
  transition: width 240ms ease, background 220ms ease;

  &.active {
    width: 26px;
    background: rgba(255, 255, 255, 0.28);
  }

  &:hover:not(.active) {
    background: rgba(255, 255, 255, 0.78);
  }
}

.indicator-progress {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #fff;
  transform-origin: left center;
  animation: indicatorFill linear forwards;
}

@keyframes indicatorFill {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

.ad-slot-banner--home_left .ad-image {
  height: 100%;
  object-fit: contain;
  background: var(--ad-media-bg);
}

.ad-slot-banner--home_left {
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: linear-gradient(180deg, rgba(241, 245, 249, 0.96), rgba(226, 232, 240, 0.98));
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.2);

  .dark & {
    border-color: rgba(148, 163, 184, 0.22);
    background: var(--ad-media-bg-dark);
    box-shadow: 0 10px 26px rgba(2, 6, 23, 0.34);
  }
}

.ad-slot-banner--home_left .ad-image-wrap::after {
  display: none;
}

.ad-slot-banner--home_left .ad-slide-window {
  border-radius: $radius-lg;
  background: rgba(15, 23, 42, 0.08);

  .dark & {
    background: rgba(15, 23, 42, 0.26);
  }
}

@media (min-width: calc(#{$breakpoint-md} + 1px)) and (max-width: 1180px) {
  .ad-slot-banner--post_top .ad-close,
  .ad-slot-banner--post_bottom .ad-close {
    width: 34px;
    height: 34px;
    right: 0.58rem;
    top: 0.58rem;
    min-width: 34px;
    min-height: 34px;
    background: rgba(30, 41, 59, 0.58);
    border-color: rgba(255, 255, 255, 0.2);
    box-shadow: 0 6px 14px rgba(15, 23, 42, 0.28);
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
  }

  .ad-slot-banner--post_top .ad-action-float,
  .ad-slot-banner--post_bottom .ad-action-float {
    left: 0.58rem;
    top: 0.58rem;
    max-width: calc(100% - 4.8rem);
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
  }

  .ad-slot-banner--post_top .ad-apply-btn,
  .ad-slot-banner--post_bottom .ad-apply-btn {
    min-height: 34px;
    min-width: auto;
    padding: 0 0.78rem;
    font-size: 0.82rem;
    font-weight: 700;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
    border-color: rgba(255, 255, 255, 0.26);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);
  }
}

@media (min-width: calc(#{$breakpoint-md} + 1px)) and (max-width: 1180px) {
  .ad-slot-banner--home_left .ad-indicators {
    bottom: 0.52rem;
    gap: 0.28rem;
  }

  .ad-slot-banner--post_top .ad-indicators,
  .ad-slot-banner--post_bottom .ad-indicators {
    bottom: 0.44rem;
    gap: 0.28rem;
  }

  .ad-slot-banner--home_left .indicator-dot,
  .ad-slot-banner--post_top .indicator-dot,
  .ad-slot-banner--post_bottom .indicator-dot {
    min-width: 9px !important;
    min-height: 9px !important;
    width: 9px;
    height: 9px;
    padding: 0 !important;
  }

  .ad-slot-banner--home_left .indicator-dot.active,
  .ad-slot-banner--post_top .indicator-dot.active,
  .ad-slot-banner--post_bottom .indicator-dot.active {
    min-width: 24px !important;
    width: 24px;
  }
}

.ad-slot-banner--home_left .ad-slides {
  min-height: 0;
  aspect-ratio: var(--home-left-aspect-ratio, 5 / 8);
}

.ad-slot-banner--post_top .ad-slides,
.ad-slot-banner--post_bottom .ad-slides {
  aspect-ratio: 16 / 5;
}

.ad-slot-banner--post_top .ad-badge,
.ad-slot-banner--post_bottom .ad-badge {
  top: auto;
  bottom: 0.36rem;
}

.ad-slot-banner--home_left .ad-badge {
  top: auto;
  left: auto;
  right: 0.5rem;
  bottom: 0.5rem;
}

.ad-slot-banner--post_top .ad-image,
.ad-slot-banner--post_bottom .ad-image {
  height: 100%;
}

@media (max-width: $breakpoint-md) {
  .ad-slot-banner--post_top .ad-close,
  .ad-slot-banner--post_bottom .ad-close,
  .ad-slot-banner--home_left .ad-close {
    width: 40px;
    height: 40px;
    right: 0.62rem;
    top: 0.62rem;
    min-width: 40px;
    min-height: 40px;
    background: rgba(30, 41, 59, 0.62);
    border-color: rgba(255, 255, 255, 0.2);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.28);
  }

  .ad-slot-banner--post_top .ad-action-float,
  .ad-slot-banner--post_bottom .ad-action-float,
  .ad-slot-banner--home_left .ad-action-float {
    left: 0.62rem;
    top: 0.62rem;
  }

  .ad-slot-banner--post_top .ad-action-float,
  .ad-slot-banner--post_bottom .ad-action-float {
    max-width: calc(100% - 5.1rem);
  }

  .ad-slot-banner--post_top .ad-apply-btn,
  .ad-slot-banner--post_bottom .ad-apply-btn,
  .ad-slot-banner--home_left .ad-apply-btn {
    min-height: 40px;
    min-width: auto;
    padding: 0 0.88rem;
    font-size: 0.96rem;
    font-weight: 700;
    white-space: nowrap;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-width: 1px;
    background: rgba(15, 23, 42, 0.68);
    border-color: rgba(255, 255, 255, 0.28);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);

    .dark & {
      background: rgba(2, 6, 23, 0.8);
      border-color: rgba(148, 163, 184, 0.42);
      color: #e2e8f0;
      box-shadow: 0 8px 18px rgba(2, 6, 23, 0.36);
    }
  }

  .ad-slot-banner--post_top .ad-image-fallback,
  .ad-slot-banner--post_bottom .ad-image-fallback {
    padding-top: 3rem;
  }

  .ad-slot-banner--post_top .ad-image-fallback span,
  .ad-slot-banner--post_bottom .ad-image-fallback span {
    font-size: 0.7rem;
    white-space: nowrap;
  }

  .ad-slot-banner--post_top .ad-badge,
  .ad-slot-banner--post_bottom .ad-badge {
    left: 0.56rem;
    bottom: 0.5rem;
    font-size: 0.6rem;
  }

  .ad-slot-banner--home_left .ad-image-fallback {
    padding-top: 4.4rem;
    gap: 0.35rem;
  }

  .ad-slot-banner--home_left .ad-image-fallback span {
    font-size: 0.72rem;
    white-space: nowrap;
  }

  .ad-slot-banner--home_left .ad-badge {
    top: auto;
    left: auto;
    right: 0.62rem;
    bottom: 0.68rem;
    font-size: 0.62rem;
  }

  .ad-indicators .indicator-dot {
    min-width: 10px !important;
    min-height: 10px !important;
    width: 10px;
    height: 10px;
    padding: 0 !important;
  }

  .ad-indicators .indicator-dot.active {
    min-width: 26px !important;
    width: 26px;
  }
}

.ad-fade-slide-enter-active,
.ad-fade-slide-leave-active {
  transition: opacity 280ms ease, transform 320ms ease;
}

.ad-fade-slide-enter-from,
.ad-fade-slide-leave-to {
  opacity: 0;
  transform: translate3d(0, 14px, 0);
}

.ad-fade-left-enter-active,
.ad-fade-left-leave-active {
  transition: opacity 300ms ease, transform 320ms ease;
}

.ad-fade-left-enter-from {
  opacity: 0;
  transform: translate3d(14px, 0, 0);
}

.ad-fade-left-leave-to {
  opacity: 0;
  transform: translate3d(-16px, 0, 0);
}

.ad-slot-banner:hover .ad-close {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  pointer-events: auto;
}

.ad-slot-banner:hover .ad-action-float,
.ad-slot-banner:focus-within .ad-action-float {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  pointer-events: auto;
}

@media (hover: none) {
  .ad-close {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
  }

  .ad-action-float {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
  }
}
</style>
