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
          <Icon name="heroicons:x-mark-16-solid" size="14" />
        </button>

        <div class="ad-slide-window">
          <div class="ad-slides">
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
                <div class="ad-image-wrap">
                  <a
                    v-if="ad.safeLinkUrl"
                    class="ad-link"
                    :href="ad.safeLinkUrl"
                    target="_blank"
                    rel="noopener noreferrer nofollow"
                    @click="handleClick(ad.id)"
                  >
                    <img :src="ad.content" :alt="ad.title" class="ad-image" loading="lazy">
                  </a>
                  <img
                    v-else
                    :src="ad.content"
                    :alt="ad.title"
                    class="ad-image"
                    loading="lazy"
                  >
                  <span v-if="ad.adInfo" class="ad-info">{{ ad.adInfo }}</span>
                </div>
              </template>
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
import { advertisementApi, type AdvertisementVO } from '~/api/advertisement'
import { useLoginModal } from '~/composables/useLoginModal'
import { useUserStore } from '~/stores/user'
import { normalizeSafeHref } from '~/utils/urlSafety'

interface BannerAd extends AdvertisementVO {
  safeLinkUrl: string | null
}

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
  if (status === 'pending') return '查看申请'
  return '申请投放'
})
const bannerTransitionName = computed(() => {
  return props.adSlot === 'home_left' ? 'ad-fade-left' : 'ad-fade-slide'
})

function sanitize(html: string) {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['a', 'img', 'span', 'div', 'p', 'strong', 'em'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'target', 'rel', 'class', 'style'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|\/(?!\/)|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  })
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
      adApplyModal.open(props.adSlot, { step: 2, pitAdId })
    })
    return
  }

  const status = myApplication.value?.status
  if (status === 'active' || status === 'pending') {
    adApplyModal.open(props.adSlot, { step: 3, pitAdId })
    return
  }
  adApplyModal.open(props.adSlot, { step: 2, pitAdId })
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
    const res = await advertisementApi.getBySlot(props.adSlot)
    ads.value = (res.data || []).map(item => ({
      ...item,
      safeLinkUrl: normalizeSafeHref(item.linkUrl)
    }))
  } catch {
    ads.value = []
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
  position: relative;
  border-radius: $radius-lg;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: $color-bg;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.14);
}

.ad-slot-banner-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ad-slide-window {
  overflow: hidden;
  position: relative;
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
  opacity: 0;
  transition: opacity 0.55s ease;
  position: relative;
  z-index: 0;
}

.ad-image-wrap::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 52%,
    rgba(15, 23, 42, 0.18) 80%,
    rgba(15, 23, 42, 0.28) 100%
  );
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
  border: none;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
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
  border: 1px solid rgba(59, 130, 246, 0.35);
  border-radius: 999px;
  padding: 0.34rem 0.72rem;
  font-size: 0.7rem;
  font-weight: 600;
  color: #1d4ed8;
  background: rgba(239, 246, 255, 0.95);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.18);
  cursor: pointer;
  transition: border-color 180ms ease, color 180ms ease, background 180ms ease;

  &:hover {
    border-color: rgba(37, 99, 235, 0.56);
    color: #1e40af;
    background: rgba(219, 234, 254, 0.98);
  }

  .dark & {
    border-color: rgba(96, 165, 250, 0.45);
    background: rgba(30, 58, 138, 0.28);
    color: #bfdbfe;
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
}

.ad-slot-banner--home_left .ad-slides {
  aspect-ratio: 5 / 8;
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

.ad-slot-banner--post_top .ad-image,
.ad-slot-banner--post_bottom .ad-image {
  height: 100%;
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
