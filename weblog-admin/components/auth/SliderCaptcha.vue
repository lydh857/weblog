<script setup lang="ts">
import { captchaApi, type CaptchaGenerateResult, type TrackPoint } from '~/api/auth/captcha'

const props = withDefaults(defineProps<{
  visible: boolean
  imgWidth?: number
  imgHeight?: number
}>(), {
  imgWidth: 320,
  imgHeight: 200,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'success': [verifyToken: string]
}>()

const loading = ref(false)
const verifying = ref(false)
const captchaData = ref<CaptchaGenerateResult | null>(null)
const sliderLeft = ref(0)
const status = ref<'idle' | 'success' | 'fail'>('idle')
const resultMessage = ref('')
const isDragging = ref(false)
const runtimeConfig = useRuntimeConfig()

const trackPoints: TrackPoint[] = []
let startX = 0
let thumbTop = 0

const maxSlide = computed(() => {
  const puzzleWidth = captchaData.value?.puzzleWidth ?? 60
  return Math.max(0, props.imgWidth - puzzleWidth)
})

function resolveCaptchaImageUrl(rawUrl?: string): string {
  if (!rawUrl) return ''
  if (rawUrl.startsWith('data:') || rawUrl.startsWith('blob:') || /^https?:\/\//.test(rawUrl)) {
    return rawUrl
  }

  if (!import.meta.client) return rawUrl

  const apiBase = String(runtimeConfig.public.apiBase || '/api').replace(/\/+$/, '')
  const origin = window.location.origin

  if (rawUrl.startsWith('/api/')) {
    return `${origin}${rawUrl}`
  }

  if (rawUrl.startsWith('/captcha/')) {
    if (/^https?:\/\//.test(apiBase)) {
      const apiUrl = new URL(apiBase)
      const apiPath = apiUrl.pathname.replace(/\/+$/, '')
      return `${apiUrl.origin}${apiPath}${rawUrl}`
    }
    return `${origin}${apiBase}${rawUrl}`
  }

  if (rawUrl.startsWith('/')) {
    return `${origin}${rawUrl}`
  }

  if (/^https?:\/\//.test(apiBase)) {
    return new URL(rawUrl, `${apiBase}/`).toString()
  }
  return `${origin}/${rawUrl.replace(/^\/+/, '')}`
}

const backgroundImageUrl = computed(() => resolveCaptchaImageUrl(captchaData.value?.backgroundImage))
const puzzleImageUrl = computed(() => resolveCaptchaImageUrl(captchaData.value?.puzzleImage))

async function loadCaptcha() {
  loading.value = true
  status.value = 'idle'
  resultMessage.value = ''
  sliderLeft.value = 0
  trackPoints.length = 0
  try {
    const res = await captchaApi.generate()
    captchaData.value = res.data
  } catch {
    captchaData.value = null
  } finally {
    loading.value = false
  }
}

async function refreshCaptcha() {
  if (loading.value || verifying.value) return
  loading.value = true
  status.value = 'idle'
  resultMessage.value = ''
  sliderLeft.value = 0
  trackPoints.length = 0
  try {
    if (captchaData.value?.captchaToken) {
      const res = await captchaApi.refresh(captchaData.value.captchaToken)
      captchaData.value = res.data
    } else {
      const res = await captchaApi.generate()
      captchaData.value = res.data
    }
  } catch {
    captchaData.value = null
  } finally {
    loading.value = false
  }
}

function onPointerDown(event: PointerEvent) {
  if (!captchaData.value || loading.value || verifying.value || status.value !== 'idle') return
  isDragging.value = true
  startX = event.clientX
  thumbTop = (event.target as HTMLElement).getBoundingClientRect().top
  trackPoints.length = 0
  trackPoints.push({ x: 0, y: 0, timestamp: Date.now() })
  ;(event.target as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!isDragging.value) return
  const delta = event.clientX - startX
  const clamped = Math.max(0, Math.min(delta, maxSlide.value))
  sliderLeft.value = clamped

  const timestamp = Date.now()
  const lastPoint = trackPoints[trackPoints.length - 1]
  if (!lastPoint || timestamp - lastPoint.timestamp >= 8) {
    trackPoints.push({
      x: clamped,
      y: event.clientY - thumbTop,
      timestamp,
    })
  }
}

async function onPointerUp() {
  if (!isDragging.value || !captchaData.value) return
  isDragging.value = false
  if (sliderLeft.value < 5) return

  verifying.value = true
  try {
    const res = await captchaApi.verify({
      captchaToken: captchaData.value.captchaToken,
      sliderPosition: Math.round(sliderLeft.value),
      slideTrack: trackPoints,
    })

    if (res.data.success && res.data.verifyToken) {
      status.value = 'success'
      resultMessage.value = '验证通过'
      setTimeout(() => {
        emit('success', res.data.verifyToken!)
      }, 400)
      return
    }

    status.value = 'fail'
    resultMessage.value = res.data.message || '验证失败，请重试'
    setTimeout(() => {
      refreshCaptcha()
    }, 700)
  } catch {
    status.value = 'fail'
    resultMessage.value = '验证失败，请重试'
    setTimeout(() => {
      refreshCaptcha()
    }, 700)
  } finally {
    verifying.value = false
  }
}

function closeModal() {
  emit('update:visible', false)
  status.value = 'idle'
  resultMessage.value = ''
  sliderLeft.value = 0
  trackPoints.length = 0
}

watch(() => props.visible, (visible) => {
  if (visible) {
    loadCaptcha()
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="captcha-fade">
      <div v-if="visible" class="captcha-overlay" @click.self="closeModal">
        <div class="captcha-modal">
          <div class="captcha-header">
            <span>安全验证</span>
            <button class="close-btn" @click="closeModal">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </div>

          <div class="captcha-image" :style="{ width: `${imgWidth}px`, height: `${imgHeight}px` }">
            <div
              v-if="captchaData"
              class="captcha-bg"
              :style="{ backgroundImage: 'url(' + backgroundImageUrl + ')' }"
            />
            <div
              v-if="captchaData"
              class="captcha-piece"
              :class="status"
              :style="{
                backgroundImage: 'url(' + puzzleImageUrl + ')',
                width: `${captchaData.puzzleWidth}px`,
                height: `${captchaData.puzzleHeight}px`,
                top: `${captchaData.puzzleY}px`,
                left: `${sliderLeft}px`,
              }"
            />
            <button v-if="!loading" class="refresh-btn" @click="refreshCaptcha">
              <Icon name="heroicons:arrow-path-20-solid" size="16" />
            </button>
            <div v-if="loading" class="loading-mask">加载中...</div>
            <div v-if="status === 'success' || status === 'fail'" class="result" :class="status">
              {{ resultMessage }}
            </div>
          </div>

          <div class="slider-track" :style="{ width: `${imgWidth}px` }">
            <div class="slider-progress" :style="{ width: `${sliderLeft}px` }" :class="status" />
            <div
              class="slider-thumb"
              :style="{ left: `${sliderLeft}px` }"
              :class="{ dragging: isDragging, success: status === 'success', fail: status === 'fail' }"
              @pointerdown.prevent="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
            >
              <Icon v-if="status === 'success'" name="heroicons:check-20-solid" size="16" />
              <Icon v-else-if="status === 'fail'" name="heroicons:x-mark-20-solid" size="16" />
              <Icon v-else name="heroicons:arrows-right-left-20-solid" size="16" />
            </div>
            <span v-if="status === 'idle' && sliderLeft === 0" class="hint">向右拖动滑块完成拼图</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.captcha-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.52);
}

.captcha-modal {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.3);
  user-select: none;
  touch-action: none;
}

.captcha-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  color: #fff;
  background: linear-gradient(135deg, #4e80ee 0%, #6a9bff 100%);
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.captcha-image {
  position: relative;
  margin: 14px;
  border-radius: 8px;
  overflow: hidden;
  background: #e2e8f0;
}

.captcha-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
}

.captcha-piece {
  position: absolute;
  background-repeat: no-repeat;
  background-size: contain;
  transition: filter 0.2s ease;

  &.success {
    filter: drop-shadow(0 0 6px rgba(34, 197, 94, 0.8));
  }

  &.fail {
    filter: drop-shadow(0 0 6px rgba(239, 68, 68, 0.8));
  }
}

.refresh-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #334155;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.7);
}

.result {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  padding: 5px 0;

  &.success {
    background: rgba(34, 197, 94, 0.9);
  }

  &.fail {
    background: rgba(239, 68, 68, 0.9);
  }
}

.slider-track {
  position: relative;
  height: 40px;
  margin: 0 14px 14px;
  border-radius: 8px;
  overflow: hidden;
  background: #f1f5f9;
}

.slider-progress {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(78, 128, 238, 0.14);

  &.success {
    background: rgba(34, 197, 94, 0.14);
  }

  &.fail {
    background: rgba(239, 68, 68, 0.14);
  }
}

.slider-thumb {
  position: absolute;
  top: 0;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  background: #4e80ee;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  z-index: 2;

  &.dragging {
    cursor: grabbing;
  }

  &.success {
    background: #22c55e;
  }

  &.fail {
    background: #ef4444;
  }
}

.hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 12px;
  pointer-events: none;
}

.captcha-fade-enter-active,
.captcha-fade-leave-active {
  transition: opacity 0.25s ease;
}

.captcha-fade-enter-from,
.captcha-fade-leave-to {
  opacity: 0;
}
</style>
