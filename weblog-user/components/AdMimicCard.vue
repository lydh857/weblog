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
        <div class="ad-cover-wrap" :class="{ 'is-broken': imageLoadFailed }">
          <img
            :src="ad.content"
            :alt="ad.title"
            class="ad-cover"
            :class="{ 'is-error': imageLoadFailed }"
            @error="handleImageError"
          >
          <div v-if="imageLoadFailed" class="ad-cover-fallback">
            <Icon name="heroicons:photo-16-solid" size="16" />
            <span>广告图片加载失败</span>
          </div>
          <span v-if="ad.adInfo && !imageLoadFailed" class="ad-cover-info">{{ ad.adInfo }}</span>
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
const linkAttributes = computed<Record<string, string>>(() => {
  if (!safeLinkUrl.value) return {}
  return {
    href: safeLinkUrl.value,
    target: '_blank',
    rel: 'noopener noreferrer nofollow',
  }
})
const adSummary = computed(() => props.ad.mimicContent || '品牌推广')
const { applyEnabled, loadAdApplyConfig } = useAdApplyConfig()
const userStore = useUserStore()
const loginModal = useLoginModal()
const adApplyModal = useAdApplyModal()
const myApplication = ref<AdvertisementVO | null>(null)
const imageLoadFailed = ref(false)
const applyButtonLabel = computed(() => {
  const status = myApplication.value?.status
  if (status === 'active') return '查看推广'
  if (status === 'pending') return '查看申请'
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
  imageLoadFailed.value = true
}

function handleApplyClick() {
  if (!applyEnabled.value) return
  if (!props.ad.pitEnabled) return
  const pitAdId = props.ad.id

  if (!userStore.isLoggedIn) {
    loginModal.open('code', () => {
      adApplyModal.open('post_list_card', { step: 2, pitAdId })
    })
    return
  }

  const status = myApplication.value?.status
  if (status === 'active' || status === 'pending') {
    adApplyModal.open('post_list_card', { step: 3, pitAdId })
    return
  }
  adApplyModal.open('post_list_card', { step: 2, pitAdId })
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
  display: flex;
  flex-direction: column;
  position: relative;
  border: 1px solid $color-border;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-bg;
  min-width: 0;
  max-width: 100%;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.15);

    .ad-title {
      color: $color-primary;
    }

    .ad-cover {
      transform: scale(1.05);
    }
  }

  .dark & {
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
  border: 1px solid rgba(59, 130, 246, 0.36);
  border-radius: 999px;
  padding: 0.3rem 0.7rem;
  font-size: 0.72rem;
  font-weight: 600;
  color: #1d4ed8;
  background: rgba(239, 246, 255, 0.95);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.18);
  cursor: pointer;

  &:hover {
    border-color: rgba(37, 99, 235, 0.56);
    color: #1e40af;
    background: rgba(219, 234, 254, 0.98);
  }

  .dark & {
    border-color: rgba(96, 165, 250, 0.42);
    color: #bfdbfe;
    background: rgba(30, 58, 138, 0.32);
  }
}

.ad-cover-wrap {
  position: relative;
  overflow: hidden;
  width: 240px;
  height: 100%;
  flex-shrink: 0;
  border-radius: $radius-lg 0 0 $radius-lg;
  background: $color-bg-secondary;

  .dark & {
    background: #1a2332;
  }
}

.ad-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  color: #334155;
  background: linear-gradient(180deg, #eef2f7, #e2e8f0);
  font-size: 0.72rem;

  .dark & {
    color: #cbd5e1;
    background: linear-gradient(180deg, #1f2937, #111827);
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
  background: #eff6ff;
  color: #1d4ed8;

  .dark & {
    background: #1e3a5f;
    color: #93c5fd;
  }
}

.ad-tag--sponsor {
  background: #f0fdf4;
  color: #15803d;

  .dark & {
    background: #14532d;
    color: #86efac;
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

@media (max-width: $breakpoint-md) {
  .ad-mimic-link {
    height: calc(180px * 9 / 16);
  }

  .ad-mimic-link {
    width: 100%;
  }

  .ad-cover-wrap {
    width: 180px;
  }

  .ad-content {
    padding: 0.375rem 0.5rem;
  }

  .ad-title {
    font-size: 0.85rem;
  }

  .ad-summary {
    font-size: 0.75rem;
  }

}

@media (max-width: 480px) {
  .ad-mimic-card {
    height: auto;
  }

  .ad-mimic-link {
    flex-direction: column;
  }

  .ad-cover-wrap {
    width: 100%;
    height: auto;
    border-radius: $radius-lg $radius-lg 0 0;
  }

  .ad-cover {
    aspect-ratio: 16 / 9;
  }

  .ad-content {
    padding: $spacing-md;
    border-radius: 0 0 $radius-lg $radius-lg;
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
