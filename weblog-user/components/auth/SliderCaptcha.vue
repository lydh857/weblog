<script setup lang="ts">
import { captchaApi, type CaptchaGenerateResult, type TrackPoint } from '~/api/auth/captcha'

const props = withDefaults(defineProps<{
  visible: boolean
  scene?: string
  imgWidth?: number
  imgHeight?: number
}>(), {
  scene: 'default',
  imgWidth: 320,
  imgHeight: 200,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'success': [verifyToken: string]
}>()

// 状态
const loading = ref(false)
const verifying = ref(false)
const captchaData = ref<CaptchaGenerateResult | null>(null)
const sliderLeft = ref(0)
const isDragging = ref(false)
const status = ref<'idle' | 'success' | 'fail'>('idle')
const resultMessage = ref('')
const isRefreshing = ref(false)
const isDarkMode = ref(false)
let themeObserver: MutationObserver | null = null

// 叠化效果：双缓冲图片
const bgImages = reactive<Array<{ src: string; active: boolean }>>([
  { src: '', active: true },
  { src: '', active: false },
])
const puzzleImages = reactive<Array<{ src: string; y: number; left: number; active: boolean }>>([
  { src: '', y: 0, left: 0, active: true },
  { src: '', y: 0, left: 0, active: false },
])
let currentBgIndex = 0
let currentPuzzleIndex = 0

function updateBgImage(src: string) {
  const next = (currentBgIndex + 1) % 2
  bgImages[next]!.src = src
  bgImages[currentBgIndex]!.active = false
  bgImages[next]!.active = true
  currentBgIndex = next
}

function updatePuzzleImage(src: string, y: number) {
  const next = (currentPuzzleIndex + 1) % 2
  // 冻结旧拼图块的当前位置（用于淡出时保持原位）
  puzzleImages[currentPuzzleIndex]!.left = sliderLeft.value
  // 新拼图块从位置 0 开始
  puzzleImages[next]!.src = src
  puzzleImages[next]!.y = y
  puzzleImages[next]!.left = 0
  puzzleImages[currentPuzzleIndex]!.active = false
  puzzleImages[next]!.active = true
  currentPuzzleIndex = next
}

// 轨迹记录
const trackPoints: TrackPoint[] = []
let startX = 0
let thumbRectTop = 0
let refreshTimer: ReturnType<typeof setTimeout> | null = null
const MAX_TRACK_POINTS = 200

// 计算最大滑动距离
const maxSlide = computed(() => props.imgWidth - (captchaData.value?.puzzleWidth ?? 60))

function clearRefreshTimer() {
  if (!refreshTimer) return
  clearTimeout(refreshTimer)
  refreshTimer = null
}

function scheduleRefresh(delay: number) {
  clearRefreshTimer()
  refreshTimer = setTimeout(() => {
    refreshTimer = null
    if (!props.visible || isDragging.value) return
    void refresh()
  }, delay)
}

function appendFinalTrackPoint() {
  const now = Date.now()
  const last = trackPoints[trackPoints.length - 1]
  if (!last || Math.abs(last.x - sliderLeft.value) > 0.5) {
    trackPoints.push({ x: sliderLeft.value, y: last?.y ?? 0, timestamp: Math.max(now, last?.timestamp ?? now) })
  }
}

// 加载验证码（初次打开，无过渡）
async function loadCaptcha() {
  clearRefreshTimer()
  loading.value = true
  status.value = 'idle'
  resultMessage.value = ''
  sliderLeft.value = 0
  trackPoints.length = 0
  // 重置双缓冲，清除旧图片，避免重新打开时看到切换过程
  bgImages[0]!.src = ''
  bgImages[0]!.active = true
  bgImages[1]!.src = ''
  bgImages[1]!.active = false
  puzzleImages[0]!.src = ''
  puzzleImages[0]!.y = 0
  puzzleImages[0]!.left = 0
  puzzleImages[0]!.active = true
  puzzleImages[1]!.src = ''
  puzzleImages[1]!.y = 0
  puzzleImages[1]!.left = 0
  puzzleImages[1]!.active = false
  currentBgIndex = 0
  currentPuzzleIndex = 0
  try {
    const res = await captchaApi.generate(props.scene)
    captchaData.value = res.data
    // 直接设置，无叠化
    bgImages[0]!.src = res.data.backgroundImage
    puzzleImages[0]!.src = res.data.puzzleImage
    puzzleImages[0]!.y = res.data.puzzleY
  } catch {
    captchaData.value = null
    status.value = 'fail'
    resultMessage.value = '验证码加载失败，请刷新重试'
  } finally {
    loading.value = false
  }
}

// 刷新（叠化过渡）
async function refresh() {
  if (loading.value || isRefreshing.value || isDragging.value) return
  clearRefreshTimer()
  isRefreshing.value = true
  // 重置状态（但不重置 sliderLeft，updatePuzzleImage 会先冻结旧位置）
  status.value = 'idle'
  resultMessage.value = ''
  trackPoints.length = 0
  try {
    const oldToken = captchaData.value?.captchaToken
    const res = oldToken
      ? await captchaApi.refresh(oldToken, props.scene)
      : await captchaApi.generate(props.scene)
    captchaData.value = res.data
    updateBgImage(res.data.backgroundImage)
    updatePuzzleImage(res.data.puzzleImage, res.data.puzzleY)
    // 新拼图已设置 left=0，现在重置滑块
    sliderLeft.value = 0
  } catch {
    captchaData.value = null
    sliderLeft.value = 0
    status.value = 'fail'
    resultMessage.value = '验证码加载失败，请刷新重试'
  } finally {
    isRefreshing.value = false
  }
}

// 拖动逻辑
function onPointerDown(e: PointerEvent) {
  if (loading.value || verifying.value || status.value !== 'idle') return
  isDragging.value = true
  startX = e.clientX
  thumbRectTop = (e.target as HTMLElement).getBoundingClientRect().top
  trackPoints.length = 0
  trackPoints.push({ x: 0, y: 0, timestamp: Date.now() })
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - startX
  const clamped = Math.max(0, Math.min(dx, maxSlide.value))
  sliderLeft.value = clamped
  // 采样：跳过与上一个点时间间隔 < 8ms 的点，减少数据量
  const now = Date.now()
  const last = trackPoints[trackPoints.length - 1]
  if (!last || now - last.timestamp >= 8) {
    if (trackPoints.length < MAX_TRACK_POINTS) {
      trackPoints.push({ x: clamped, y: e.clientY - thumbRectTop, timestamp: now })
    }
  }
}

async function onPointerUp() {
  if (!isDragging.value) return
  isDragging.value = false
  if (!captchaData.value || sliderLeft.value < 5) return
  appendFinalTrackPoint()

  verifying.value = true
  try {
    const res = await captchaApi.verify({
      captchaToken: captchaData.value.captchaToken,
      scene: props.scene,
      sliderPosition: Math.round(sliderLeft.value),
      slideTrack: trackPoints,
    })
    if (res.data.success && res.data.verifyToken) {
      status.value = 'success'
      resultMessage.value = '验证通过'
      setTimeout(() => {
        emit('success', res.data.verifyToken!)
      }, 800)
    } else {
      status.value = 'fail'
      resultMessage.value = res.data.message || '验证失败，请重试'
      // 显示失败状态 1s 后，用叠化过渡刷新图片（和手动刷新相同效果）
      scheduleRefresh(1000)
    }
  } catch {
    status.value = 'fail'
    resultMessage.value = '验证失败，请重试'
    scheduleRefresh(1000)
  } finally {
    verifying.value = false
  }
}

function close() {
  clearRefreshTimer()
  emit('update:visible', false)
  // 重置状态，下次打开时干净
  status.value = 'idle'
  resultMessage.value = ''
  sliderLeft.value = 0
}

function syncDarkMode() {
  if (!import.meta.client) return
  const root = document.documentElement
  const body = document.body
  isDarkMode.value = root.classList.contains('dark') || body.classList.contains('dark')
}

// 显示时自动加载
watch(() => props.visible, (val) => {
  if (val) loadCaptcha()
})

onMounted(() => {
  if (!import.meta.client) return
  syncDarkMode()
  themeObserver = new MutationObserver(syncDarkMode)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
  themeObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
})

onUnmounted(() => {
  clearRefreshTimer()
  themeObserver?.disconnect()
  themeObserver = null
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="captcha-overlay" :class="{ 'captcha-overlay--dark': isDarkMode }" @click.self="close">
        <div class="captcha-modal">
          <!-- 标题栏 -->
          <div class="captcha-header">
            <span>安全验证</span>
            <button class="captcha-close-btn" title="关闭" @click="close">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </div>

          <!-- 图片区域 -->
          <div class="captcha-image-area" :style="{ width: imgWidth + 'px', height: imgHeight + 'px' }">
            <!-- 叠化背景图 -->
            <div class="crossfade-container">
              <div
                v-for="(img, index) in bgImages"
                :key="index"
                class="captcha-bg-layer"
                :class="{ active: img.active }"
                :style="{ backgroundImage: img.src ? `url(${img.src})` : 'none' }"
              />
            </div>

            <!-- 叠化拼图块 -->
            <div class="crossfade-container puzzle-layer">
              <div
                v-for="(img, index) in puzzleImages"
                :key="index"
                class="captcha-puzzle-piece"
                :class="{ active: img.active, success: status === 'success' && img.active, fail: status === 'fail' && img.active }"
                :style="{
                  backgroundImage: img.src ? `url(${img.src})` : 'none',
                  left: (img.active ? sliderLeft : img.left) + 'px',
                  top: img.y + 'px',
                  width: (captchaData?.puzzleWidth ?? 60) + 'px',
                  height: (captchaData?.puzzleHeight ?? 60) + 'px',
                }"
              />
            </div>

            <!-- 加载状态 -->
            <div v-if="loading" class="captcha-loading">
              <div class="captcha-spinner" />
            </div>

            <!-- 刷新按钮 -->
            <button
              v-if="!loading && !isRefreshing && status === 'idle'"
              class="captcha-refresh-btn"
              title="刷新"
              @click="refresh"
            >
              <Icon name="heroicons:arrow-path-20-solid" size="16" />
            </button>
            <div v-if="isRefreshing" class="captcha-refresh-btn refreshing">
              <Icon name="heroicons:arrow-path-20-solid" size="16" />
            </div>

            <!-- 成功图标 -->
            <Transition name="captcha-result-fade">
              <div v-if="status === 'success'" class="captcha-success-icon">
                <Icon name="heroicons:check-circle-20-solid" size="40" />
              </div>
            </Transition>

            <!-- 底部结果提示条 -->
            <Transition name="captcha-result-slide">
              <div v-if="status === 'success' || status === 'fail'" class="captcha-result-bar" :class="status">
                {{ resultMessage }}
              </div>
            </Transition>
          </div>

          <!-- 滑动轨道 -->
          <div class="captcha-slider-track" :style="{ width: imgWidth + 'px' }">
            <div class="captcha-slider-progress" :style="{ width: sliderLeft + 'px' }" :class="status" />
            <div
              class="captcha-slider-thumb"
              :style="{ left: sliderLeft + 'px' }"
              :class="{ dragging: isDragging, success: status === 'success', fail: status === 'fail' }"
              @pointerdown.prevent="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
            >
              <Icon v-if="status === 'success'" name="heroicons:check-20-solid" size="18" />
              <Icon v-else-if="status === 'fail'" name="heroicons:x-mark-20-solid" size="18" />
              <Icon v-else name="heroicons:arrows-right-left-20-solid" size="18" />
            </div>
            <span v-if="sliderLeft === 0 && status === 'idle'" class="captcha-slider-hint">向右拖动滑块完成拼图</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.captcha-overlay {
  --captcha-overlay-bg: rgba(0, 0, 0, 0.4);
  --captcha-modal-bg: #ffffff;
  --captcha-modal-border: transparent;
  --captcha-modal-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  --captcha-header-bg: linear-gradient(135deg, #4e80ee 0%, #6a9bff 100%);
  --captcha-header-text: #ffffff;
  --captcha-close-hover-bg: rgba(255, 255, 255, 0.15);
  --captcha-image-bg: #f1f5f9;
  --captcha-loading-bg: rgba(255, 255, 255, 0.7);
  --captcha-refresh-bg: rgba(0, 0, 0, 0.2);
  --captcha-refresh-hover-bg: rgba(0, 0, 0, 0.5);
  --captcha-track-bg: #f0f2f5;
  --captcha-track-shadow: 0 2px 5px rgba(0, 0, 0, 0.1), inset 0 1px 3px rgba(0, 0, 0, 0.1);
  --captcha-progress-bg: rgba(78, 128, 238, 0.1);
  --captcha-thumb-bg: #4e80ee;
  --captcha-thumb-hover-bg: #40a9ff;
  --captcha-hint: #999;

  position: fixed; inset: 0; z-index: var(--z-captcha);
  display: flex; align-items: center; justify-content: center;
  background: var(--captcha-overlay-bg);
  backdrop-filter: blur(8px);

}

.captcha-overlay--dark {
  --captcha-overlay-bg: rgba(0, 0, 0, 0.6);
  --captcha-modal-bg: linear-gradient(180deg, #171b20, #101215);
  --captcha-modal-border: rgba(148, 163, 184, 0.14);
  --captcha-modal-shadow: 0 16px 36px rgba(2, 6, 23, 0.48);
  --captcha-header-bg: linear-gradient(180deg, rgba(8, 10, 14, 0.96), rgba(20, 24, 31, 0.96));
  --captcha-header-text: #f8fafc;
  --captcha-close-hover-bg: rgba(148, 163, 184, 0.16);
  --captcha-image-bg: rgba(8, 10, 14, 0.92);
  --captcha-loading-bg: rgba(8, 10, 14, 0.74);
  --captcha-refresh-bg: rgba(8, 10, 14, 0.72);
  --captcha-refresh-hover-bg: rgba(20, 24, 31, 0.92);
  --captcha-track-bg: rgba(8, 10, 14, 0.9);
  --captcha-track-shadow: 0 4px 12px rgba(2, 6, 23, 0.4), inset 0 1px 3px rgba(2, 6, 23, 0.45);
  --captcha-progress-bg: rgba(148, 163, 184, 0.14);
  --captcha-thumb-bg: rgba(8, 10, 14, 0.96);
  --captcha-thumb-hover-bg: rgba(20, 24, 31, 0.96);
  --captcha-hint: #94a3b8;
}

.captcha-modal {
  background: var(--captcha-modal-bg); border-radius: 12px; overflow: hidden;
  border: 1px solid var(--captcha-modal-border);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  user-select: none; touch-action: none;

  .dark & {
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
    border-color: rgba(148, 163, 184, 0.14);
  }
}

.captcha-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 15px;
  background: var(--captcha-header-bg);
  color: var(--captcha-header-text); font-size: 0.95rem; font-weight: 500;
}

.captcha-close-btn {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 6px;
  background: none; border: none; color: var(--captcha-header-text); cursor: pointer;
  opacity: 0.8; transition: all 0.2s;
  &:hover { opacity: 1; background: var(--captcha-close-hover-bg); }
}

.captcha-image-area {
  position: relative; overflow: hidden; margin: 15px;
  border-radius: 8px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  background: var(--captcha-image-bg);
}

// 叠化容器
.crossfade-container {
  position: absolute; inset: 0;
  &.puzzle-layer { z-index: 3; pointer-events: none; }
}

.captcha-bg-layer {
  position: absolute; inset: 0;
  background-size: cover; background-position: center;
  opacity: 0; transition: opacity 0.4s ease;
  &.active { opacity: 1; }
}

.captcha-puzzle-piece {
  position: absolute;
  background-size: contain; background-position: 0 0; background-repeat: no-repeat;
  opacity: 0; transition: opacity 0.4s ease, filter 0.3s;
  &.active { opacity: 1; }
  &.success {
    filter: drop-shadow(0 0 6px rgba(34, 197, 94, 0.9)) drop-shadow(0 0 12px rgba(34, 197, 94, 0.6));
  }
  &.fail {
    filter: drop-shadow(0 0 6px rgba(239, 68, 68, 0.9)) drop-shadow(0 0 12px rgba(239, 68, 68, 0.6));
    animation: captcha-shake-piece 0.5s ease-in-out;
  }
}

// 加载
.captcha-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: var(--captcha-loading-bg); z-index: 20;
}

.captcha-spinner {
  width: 36px; height: 36px;
  border: 3px solid rgba(0, 0, 0, 0.1); border-left-color: #4e80ee;
  border-radius: 50%; animation: captcha-spin 1s linear infinite;
}

.captcha-overlay--dark .captcha-spinner {
  border-color: rgba(255, 255, 255, 0.1);
  border-left-color: #cbd5e1;
}

// 刷新按钮
.captcha-refresh-btn {
  position: absolute; top: 10px; right: 10px; z-index: 10;
  width: 30px; height: 30px; border-radius: 50%;
  background: var(--captcha-refresh-bg); border: 1px solid rgba(255, 255, 255, 0.2);
  display: flex; align-items: center; justify-content: center;
  color: #fff; cursor: pointer; transition: background-color 0.3s;
  &:hover { background: var(--captcha-refresh-hover-bg); transform: rotate(30deg); transition: background-color 0.3s, transform 0.3s; }
  &.refreshing { cursor: default; animation: captcha-spin 1s linear infinite; }
}

// 成功图标（中央放大动画）
.captcha-success-icon {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  color: #22c55e; z-index: 11; animation: captcha-success-pop 0.5s ease;
}

// 底部结果提示条
.captcha-result-bar {
  position: absolute; bottom: 0; left: 0; right: 0; z-index: 10;
  padding: 5px 0; text-align: center;
  font-size: 12px; font-weight: 600; color: #fff;
  &.success { background: rgba(34, 197, 94, 0.9); }
  &.fail { background: rgba(239, 68, 68, 0.9); }
}

// 滑动轨道
.captcha-slider-track {
  position: relative; height: 40px; margin: 0 15px 15px;
  background: var(--captcha-track-bg); border-radius: 5px; overflow: hidden;
  box-shadow: var(--captcha-track-shadow);
}

.captcha-slider-progress {
  position: absolute; left: 0; top: 0; height: 100%;
  background: var(--captcha-progress-bg); transition: background-color 0.3s;
  &.success { background: rgba(34, 197, 94, 0.1); }
  &.fail { background: rgba(239, 68, 68, 0.1); }
}

.captcha-slider-thumb {
  position: absolute; top: 0;
  width: 40px; height: 40px; border-radius: 5px;
  background: var(--captcha-thumb-bg); color: #fff; cursor: grab;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.3);
  transition: background-color 0.3s;
  z-index: 2;
  &:hover { background: var(--captcha-thumb-hover-bg); }
  &.dragging { cursor: grabbing; }
  &.success { background: #22c55e; }
  &.fail { background: #ef4444; }
}

.captcha-slider-hint {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 0.8rem; color: var(--captcha-hint); pointer-events: none; z-index: 1;
}

// ===== 动画 =====
@keyframes captcha-spin { to { transform: rotate(360deg); } }

@keyframes captcha-shake-piece {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-5px); }
  40%, 80% { transform: translateX(5px); }
}

@keyframes captcha-success-pop {
  0% { transform: translate(-50%, -50%) scale(0.5); opacity: 0; }
  70% { transform: translate(-50%, -50%) scale(1.2); }
  100% { transform: translate(-50%, -50%) scale(1); opacity: 1; }
}

// 结果条从底部滑入
.captcha-result-slide-enter-active { animation: captcha-slide-up 0.3s ease-out; }
.captcha-result-slide-leave-active { transition: opacity 0.3s; }
.captcha-result-slide-leave-to { opacity: 0; }

@keyframes captcha-slide-up {
  0% { transform: translateY(100%); }
  100% { transform: translateY(0); }
}

// 成功图标淡入
.captcha-result-fade-enter-active { transition: opacity 0.3s; }
.captcha-result-fade-leave-active { transition: opacity 0.5s; }
.captcha-result-fade-enter-from, .captcha-result-fade-leave-to { opacity: 0; }

// 整体淡入淡出
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .captcha-modal {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .captcha-modal {
    transform: translateY(20px) scale(0.96);
  }
}
</style>
