<template>
  <div class="ad-mimic-wrap">
    <article class="ad-mimic-card">
      <div v-if="applyEnabled && ad.pitEnabled" class="ad-action-float">
        <button
          class="ad-apply-btn"
          type="button"
          @click.stop.prevent="handleApplyClick"
        >
          {{ applyButtonLabel }}
        </button>
      </div>

      <component
        :is="safeLinkUrl ? 'a' : 'div'"
        class="ad-mimic-link"
        v-bind="linkAttributes"
        @click="handleCardClick"
      >
        <div class="ad-cover-wrap" :class="{ 'is-broken': shouldShowFallback }">
          <img
            v-if="hasCoverImage"
            :src="ad.content"
            :alt="ad.title"
            class="ad-cover"
            :class="{ 'is-error': imageLoadFailed }"
            @error="handleImageError"
          >
          <div v-if="shouldShowFallback" class="ad-cover-fallback">
            <Icon name="heroicons:photo-16-solid" size="16" />
            <span>{{ fallbackText }}</span>
          </div>
          <span v-if="ad.adInfo && !shouldShowFallback" class="ad-cover-info">{{ ad.adInfo }}</span>
        </div>
        <div class="ad-content">
          <div class="ad-tags">
            <span class="ad-tag">精选推荐</span>
            <span class="ad-tag ad-tag--sponsor">广告</span>
          </div>
          <h3 class="ad-title">{{ ad.title }}</h3>
          <p class="ad-summary">{{ adSummary }}</p>
        </div>
      </component>

    </article>
  </div>
</template>

<script setup lang="ts">
import { advertisementApi, type AdvertisementVO } from '~/api/advertisement'
import { useLoginModal } from '~/composables/useLoginModal'
import { useUserStore } from '~/stores/user'
import { normalizeSafeHref } from '~/utils/urlSafety'

const props = defineProps<{
  ad: AdvertisementVO
}>()

const safeLinkUrl = computed(() => normalizeSafeHref(props.ad.linkUrl))
const linkAttributes = computed<Partial<Record<'href' | 'target' | 'rel', string>>>(() => {
  if (!safeLinkUrl.value) return {}
  return {
    href: safeLinkUrl.value,
    target: '_blank',
    rel: 'noopener noreferrer nofollow',
  }
})
const adSummary = computed(() => props.ad.mimicContent || '品牌推广')
const hasCoverImage = computed(() => Boolean(props.ad.content && props.ad.content.trim().length > 0))
const { applyEnabled, loadAdApplyConfig } = useAdApplyConfig()
const userStore = useUserStore()
const loginModal = useLoginModal()
const adApplyModal = useAdApplyModal()
const myApplication = ref<AdvertisementVO | null>(null)
const imageLoadFailed = ref(false)
const shouldShowFallback = computed(() => !hasCoverImage.value || imageLoadFailed.value)
const fallbackText = computed(() => (hasCoverImage.value ? '广告图片加载失败' : '广告素材待更新'))
const applyButtonLabel = computed(() => {
  const status = myApplication.value?.status
  if (status === 'active') return '查看推广'
  if (status === 'pending' || status === 'rejected') return '查看申请'
  if (status === 'expired') return '重新申请'
  return '申请投放'
})

function handleClick() {
  advertisementApi.recordClick(props.ad.id).catch(() => {})
}

function handleCardClick() {
  if (!safeLinkUrl.value) return
  handleClick()
}

function handleImageError() {
  if (!hasCoverImage.value) return
  imageLoadFailed.value = true
}

function handleApplyClick() {
  if (!applyEnabled.value) return
  if (!props.ad.pitEnabled) return
  const pitAdId = props.ad.id

  if (!userStore.isLoggedIn) {
    loginModal.open('code', () => {
      adApplyModal.open('post_list_card', { step: 1, pitAdId })
    })
    return
  }

  const status = myApplication.value?.status
  if (status === 'active' || status === 'pending' || status === 'rejected') {
    adApplyModal.open('post_list_card', { step: 3, pitAdId })
    return
  }
  if (status === 'expired') {
    adApplyModal.open('post_list_card', { step: 1, pitAdId })
    return
  }
  adApplyModal.open('post_list_card', { step: 1, pitAdId })
}

async function loadMyApplicationStatus() {
  if (!userStore.isLoggedIn) {
    myApplication.value = null
    return
  }
  try {
    const res = await advertisementApi.getMyApplication('post_list_card')
    myApplication.value = res.data
  } catch {
    myApplication.value = null
  }
}

onMounted(() => {
  void loadAdApplyConfig()
  void loadMyApplicationStatus()
})

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

watch(() => props.ad.content, () => {
  imageLoadFailed.value = false
})
</script>

<style scoped lang="scss">
.ad-mimic-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ad-mimic-card {
  --ad-mimic-media-bg: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(248, 250, 252, 0.92));

  display: flex;
  flex-direction: column;
  position: relative;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-bg;
  min-width: 0;
  max-width: 100%;
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);

    .ad-title {
      color: $color-primary;
    }

    .ad-cover {
      transform: scale(1.05);
    }
  }

  .dark & {
    --ad-mimic-media-bg:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;

    &:hover {
      box-shadow: 0 6px 24px rgba(0, 0, 0, 0.3);
    }
  }
}

.ad-mimic-link {
  display: flex;
  width: 100%;
  height: calc(240px * 9 / 16);
  min-width: 0;
  text-decoration: none;
  color: inherit;
}

.ad-action-float {
  position: absolute;
  left: 0.48rem;
  top: 0.48rem;
  z-index: 6;
  opacity: 0;
  transform: translate3d(0, -8px, 0);
  pointer-events: none;
  transition: opacity 220ms ease, transform 240ms ease;
}

.ad-apply-btn {
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  padding: 0.3rem 0.7rem;
  font-size: 0.72rem;
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
    color: #e2e8f0;
    background: rgba(2, 6, 23, 0.74);

    &:hover {
      border-color: rgba(203, 213, 225, 0.52);
      background: rgba(15, 23, 42, 0.92);
      color: #f8fafc;
      box-shadow: 0 8px 20px rgba(2, 6, 23, 0.4);
    }
  }
}

.ad-cover-wrap {
  position: relative;
  overflow: hidden;
  width: 240px;
  height: 100%;
  flex-shrink: 0;
  border-radius: $radius-lg 0 0 $radius-lg;
  background: var(--ad-mimic-media-bg);

  .dark & {
    background: var(--ad-mimic-media-bg);
  }
}

.ad-cover-wrap.is-broken {
  background: var(--ad-mimic-media-bg);
}

.ad-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  background: var(--ad-mimic-media-bg);
  transition: transform 0.35s ease;
}

.ad-cover.is-error {
  opacity: 0;
}

.ad-cover-fallback {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  padding: 0.8rem;
  text-align: center;
  color: rgba(255, 255, 255, 0.95);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.7), rgba(15, 23, 42, 0.86));
  font-size: 0.72rem;

  .dark & {
    color: #cbd5e1;
    background: var(--ad-mimic-media-bg);
  }
}

.ad-cover-wrap.is-broken .ad-cover {
  transform: none !important;
}

.ad-cover-info {
  position: absolute;
  left: 0.5rem;
  right: 0.5rem;
  bottom: 0.5rem;
  color: #ffffff;
  font-size: 0.72rem;
  line-height: 1.4;
  font-weight: 600;
  letter-spacing: 0.01em;
  text-shadow:
    0 1px 0 rgba(0, 0, 0, 0.42),
    0 2px 6px rgba(0, 0, 0, 0.38),
    0 8px 18px rgba(0, 0, 0, 0.26);
  opacity: 0.98;
  z-index: 2;
  pointer-events: none;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ad-content {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 0 $radius-lg $radius-lg 0;
}

.ad-tags {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: 0.25rem;
}

.ad-tag {
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: 500;
  line-height: 1.5;
  background: var(--status-info-soft-bg);
  color: var(--status-info);

  .dark & {
    background: var(--status-info-soft-bg);
    color: var(--status-info);
  }
}

.ad-tag--sponsor {
  background: var(--status-success-soft-bg);
  color: var(--status-success);

  .dark & {
    background: var(--status-success-soft-bg);
    color: var(--status-success);
  }
}

.ad-title {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.4;
  color: $color-text;
  margin-bottom: 0.2rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s ease;

  .dark & {
    color: $color-dark-text;
  }
}

.ad-summary {
  font-size: 0.78rem;
  line-height: 1.5;
  color: $color-text-muted;
  margin-bottom: auto;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;

  .dark & {
    color: #94a3b8;
  }
}

.ad-mimic-card:hover .ad-action-float,
.ad-mimic-card:focus-within .ad-action-float {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  pointer-events: auto;
}

@media (min-width: calc(#{$breakpoint-md} + 1px)) and (max-width: 1180px) {
  .ad-mimic-link {
    height: calc(200px * 9 / 16);
  }

  .ad-cover-wrap {
    width: 200px;
  }

  .ad-cover {
    width: 200px;
    height: calc(200px * 9 / 16);
  }

  .ad-action-float {
    left: 0.52rem;
    top: 0.52rem;
  }

  .ad-apply-btn {
    min-height: 34px;
    min-width: auto;
    padding: 0 0.68rem;
    font-size: 0.78rem;
    font-weight: 600;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
  }

  .ad-cover-fallback {
    padding: 0.64rem;
    font-size: 0.66rem;
    gap: 0.24rem;
  }

  .ad-content {
    padding: 0.45rem 0.6rem;
  }

  .ad-tags {
    margin-bottom: 0.2rem;
  }

  .ad-tag {
    font-size: 0.62rem;
  }

  .ad-title {
    font-size: 0.86rem;
    margin-bottom: 0.18rem;
  }

  .ad-summary {
    font-size: 0.74rem;
  }
}

@media (max-width: $breakpoint-md) {
  .ad-mimic-link {
    flex-direction: row;
    width: 100%;
    height: calc(180px * 9 / 16);
  }

  .ad-cover-wrap {
    width: 180px;
    height: calc(180px * 9 / 16);
    aspect-ratio: auto;
    min-height: 0;
    border-radius: $radius-lg 0 0 $radius-lg;
  }

  .ad-cover {
    width: 180px;
    height: calc(180px * 9 / 16);
    aspect-ratio: auto;
  }

  .ad-action-float {
    left: 0.52rem;
    top: 0.52rem;
  }

  .ad-apply-btn {
    min-height: 34px;
    min-width: auto;
    padding: 0 0.68rem;
    font-size: 0.78rem;
    font-weight: 600;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
    border-color: rgba(255, 255, 255, 0.28);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);
  }

  .ad-cover-fallback {
    padding: 0.64rem;
    font-size: 0.66rem;
    gap: 0.24rem;
  }

  .ad-content {
    padding: 0.375rem 0.5rem;
    border-radius: 0 $radius-lg $radius-lg 0;
  }

  .ad-tags {
    margin-bottom: 0.25rem;
  }

  .ad-tag {
    font-size: 0.65rem;
    padding: 0.05rem 0.4rem;
  }

  .ad-title {
    font-size: 0.85rem;
    margin-bottom: 0.2rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    display: block;
    -webkit-line-clamp: unset;
  }

  .ad-summary {
    font-size: 0.75rem;
    margin-bottom: auto;
  }
}

@media (max-width: $breakpoint-md) {
  .ad-apply-btn {
    min-height: 38px;
    min-width: auto;
  }
}

@media (max-width: 480px) {
  .ad-mimic-link {
    flex-direction: column;
    height: auto;
  }

  .ad-action-float {
    left: 0.56rem;
    top: 0.56rem;
  }

  .ad-cover-wrap {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
    min-height: 136px;
    border-radius: $radius-lg $radius-lg 0 0;
  }

  .ad-cover {
    width: 100%;
    height: 100%;
    aspect-ratio: 16 / 9;
  }

  .ad-apply-btn {
    min-height: 36px;
    padding: 0 0.78rem;
    font-size: 0.84rem;
  }

  .ad-cover-fallback {
    padding-top: 3.2rem;
    gap: 0.3rem;
  }

  .ad-cover-fallback span {
    font-size: 0.72rem;
  }

  .ad-content {
    padding: $spacing-md;
    border-radius: 0 0 $radius-lg $radius-lg;
  }

  .ad-title {
    font-size: 0.9rem;
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .ad-summary {
    font-size: 0.78rem;
    margin-bottom: 0;
  }
}

@media (hover: none) {
  .ad-action-float {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    pointer-events: auto;
  }
}
</style>
