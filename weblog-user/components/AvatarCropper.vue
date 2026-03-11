<template>
  <Teleport to="body">
    <Transition name="cropper-fade">
      <div v-if="visible" class="cropper-overlay" @click="close">
        <div class="cropper-dialog" @click.stop>
          <div class="cropper-header">
            <div>
              <h3 class="cropper-title">头像裁剪</h3>
              <p class="cropper-subtitle">拖拽调整位置，滚轮或按钮缩放</p>
            </div>
            <button type="button" class="icon-btn" aria-label="关闭" @click="close">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </div>

          <div class="cropper-body">
            <div class="editor-panel">
              <div class="toolbar">
                <div class="tool-group">
                  <button type="button" class="tool-btn" title="缩小" @click="zoom(-0.08)">
                    <Icon name="heroicons:magnifying-glass-minus-20-solid" size="16" />
                  </button>
                  <button type="button" class="tool-btn" title="放大" @click="zoom(0.08)">
                    <Icon name="heroicons:magnifying-glass-plus-20-solid" size="16" />
                  </button>
                </div>
                <div class="tool-group">
                  <button type="button" class="tool-btn" title="左旋转" @click="rotate(-90)">
                    <Icon name="heroicons:arrow-uturn-left-20-solid" size="16" />
                  </button>
                  <button type="button" class="tool-btn" title="右旋转" @click="rotate(90)">
                    <Icon name="heroicons:arrow-uturn-right-20-solid" size="16" />
                  </button>
                </div>
                <div class="tool-group">
                  <button type="button" class="tool-btn text-tool" title="水平翻转" @click="flipHorizontal">↔</button>
                  <button type="button" class="tool-btn text-tool" title="垂直翻转" @click="flipVertical">↕</button>
                </div>
                <button type="button" class="tool-btn" title="重置" @click="reset">
                  <Icon name="heroicons:arrow-path-20-solid" size="16" />
                </button>
              </div>
              <canvas
                ref="editorCanvasRef"
                class="editor-canvas"
                @pointerdown="onPointerDown"
                @pointermove="onPointerMove"
                @pointerup="onPointerUp"
                @pointercancel="onPointerUp"
                @pointerleave="onPointerUp"
                @wheel.prevent="onWheel"
              />
            </div>

            <aside class="preview-panel">
              <p class="preview-label">预览</p>
              <canvas ref="previewCanvasRef" class="preview-canvas" />
              <p class="preview-hint">输出为圆形头像</p>
            </aside>
          </div>

          <div class="cropper-footer">
            <div class="footer-left">
              <input
                ref="fileInputRef"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                class="hidden-input"
                @change="handleFileSelect"
              />
              <button type="button" class="btn btn-secondary" @click="triggerUpload">{{ imageLoaded ? '更换图片' : '选择图片' }}</button>
              <span v-if="errorMsg" class="error-msg">{{ errorMsg }}</span>
            </div>
            <div class="footer-actions">
              <button type="button" class="btn btn-secondary" @click="close">取消</button>
              <button type="button" class="btn btn-primary" :disabled="!imageLoaded || cropping" @click="confirmCrop">
                <Icon v-if="cropping" name="heroicons:arrow-path-16-solid" size="14" class="spin" />
                {{ cropping ? '处理中...' : '确认裁剪' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: boolean
  imageSrc: string
  outputSize?: number
  outputType?: 'image/jpeg' | 'image/png' | 'image/webp'
  maxFileSize?: number
}>(), {
  outputSize: 320,
  outputType: 'image/webp',
  maxFileSize: 5 * 1024 * 1024,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  crop: [payload: { blob: Blob; url: string }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const editorCanvasRef = ref<HTMLCanvasElement | null>(null)
const previewCanvasRef = ref<HTMLCanvasElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const cropping = ref(false)
const errorMsg = ref('')
const imageLoaded = ref(false)
const outputQuality = 0.8

let imageEl: HTMLImageElement | null = null
let localBlobUrl: string | null = null

let canvasWidth = 0
let canvasHeight = 0
let cropRadius = 0
let cropCenterX = 0
let cropCenterY = 0

let scale = 1
let minScale = 1
let maxScale = 4
let translateX = 0
let translateY = 0
let rotationDeg = 0
let flipX = 1
let flipY = 1
let boundWidthAtScaleOne = 0
let boundHeightAtScaleOne = 0

let dragging = false
let dragLastX = 0
let dragLastY = 0
let resizeRafId: number | null = null

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function close() {
  visible.value = false
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function clearLocalBlobUrl() {
  if (localBlobUrl) {
    URL.revokeObjectURL(localBlobUrl)
    localBlobUrl = null
  }
}

function setCanvasMetrics() {
  const canvas = editorCanvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const width = Math.max(320, Math.floor(rect.width))
  const height = Math.max(260, Math.floor(rect.height))
  if (canvas.width !== width) canvas.width = width
  if (canvas.height !== height) canvas.height = height
  canvasWidth = width
  canvasHeight = height
  cropCenterX = width / 2
  cropCenterY = height / 2
  cropRadius = Math.floor(Math.min(width, height) * 0.33)
}

function refreshScaleBounds() {
  if (!imageEl) return
  const rad = (rotationDeg * Math.PI) / 180
  const absCos = Math.abs(Math.cos(rad))
  const absSin = Math.abs(Math.sin(rad))
  boundWidthAtScaleOne = imageEl.width * absCos + imageEl.height * absSin
  boundHeightAtScaleOne = imageEl.width * absSin + imageEl.height * absCos
  const minScaleX = (cropRadius * 2) / boundWidthAtScaleOne
  const minScaleY = (cropRadius * 2) / boundHeightAtScaleOne
  minScale = Math.max(minScaleX, minScaleY, 0.01)
  maxScale = Math.max(minScale * 4.5, minScale + 1)
}

function clampTranslate() {
  if (!imageEl) return
  const halfBoundW = (boundWidthAtScaleOne * scale) / 2
  const halfBoundH = (boundHeightAtScaleOne * scale) / 2

  const minX = cropRadius - halfBoundW
  const maxX = halfBoundW - cropRadius
  const minY = cropRadius - halfBoundH
  const maxY = halfBoundH - cropRadius

  translateX = clamp(translateX, Math.min(minX, maxX), Math.max(minX, maxX))
  translateY = clamp(translateY, Math.min(minY, maxY), Math.max(minY, maxY))
}

function normalizeTransform() {
  if (!imageEl) return
  refreshScaleBounds()
  scale = clamp(scale, minScale, maxScale)
  clampTranslate()
}

function drawImageToContext(ctx: CanvasRenderingContext2D) {
  if (!imageEl) return
  ctx.save()
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.translate(cropCenterX + translateX, cropCenterY + translateY)
  ctx.rotate((rotationDeg * Math.PI) / 180)
  ctx.scale(scale * flipX, scale * flipY)
  ctx.drawImage(imageEl, -imageEl.width / 2, -imageEl.height / 2, imageEl.width, imageEl.height)
  ctx.restore()
}

function buildSourceCanvas() {
  if (!imageEl || !canvasWidth || !canvasHeight) return null
  const sourceCanvas = document.createElement('canvas')
  sourceCanvas.width = canvasWidth
  sourceCanvas.height = canvasHeight
  const sourceCtx = sourceCanvas.getContext('2d')
  if (!sourceCtx) return null
  sourceCtx.fillStyle = '#f1f5f9'
  sourceCtx.fillRect(0, 0, canvasWidth, canvasHeight)
  drawImageToContext(sourceCtx)
  return sourceCanvas
}

function renderPreview() {
  const previewCanvas = previewCanvasRef.value
  if (!previewCanvas || !imageLoaded.value) return
  const sourceCanvas = buildSourceCanvas()
  if (!sourceCanvas) return

  const size = 180
  if (previewCanvas.width !== size) previewCanvas.width = size
  if (previewCanvas.height !== size) previewCanvas.height = size

  const ctx = previewCanvas.getContext('2d')
  if (!ctx) return

  ctx.clearRect(0, 0, size, size)
  ctx.save()
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()
  ctx.drawImage(
    sourceCanvas,
    cropCenterX - cropRadius,
    cropCenterY - cropRadius,
    cropRadius * 2,
    cropRadius * 2,
    0,
    0,
    size,
    size,
  )
  ctx.restore()
}

function renderEditor() {
  const editorCanvas = editorCanvasRef.value
  if (!editorCanvas) return
  const ctx = editorCanvas.getContext('2d')
  if (!ctx) return

  ctx.clearRect(0, 0, canvasWidth, canvasHeight)
  ctx.fillStyle = '#f1f5f9'
  ctx.fillRect(0, 0, canvasWidth, canvasHeight)

  if (imageLoaded.value) {
    drawImageToContext(ctx)
  }

  ctx.save()
  ctx.fillStyle = 'rgba(15, 23, 42, 0.36)'
  ctx.fillRect(0, 0, canvasWidth, canvasHeight)
  ctx.globalCompositeOperation = 'destination-out'
  ctx.beginPath()
  ctx.arc(cropCenterX, cropCenterY, cropRadius, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()

  ctx.save()
  ctx.strokeStyle = '#3b82f6'
  ctx.lineWidth = 2.5
  ctx.shadowColor = 'rgba(59, 130, 246, 0.28)'
  ctx.shadowBlur = 4
  ctx.beginPath()
  ctx.arc(cropCenterX, cropCenterY, cropRadius, 0, Math.PI * 2)
  ctx.stroke()
  ctx.restore()

  ctx.save()
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.92)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.arc(cropCenterX, cropCenterY, cropRadius - 2, 0, Math.PI * 2)
  ctx.stroke()
  ctx.restore()

  renderPreview()
}

function initTransform() {
  if (!imageEl) return
  rotationDeg = 0
  flipX = 1
  flipY = 1
  translateX = 0
  translateY = 0
  scale = 1
  normalizeTransform()
  scale = clamp(minScale * 1.18, minScale, maxScale)
  clampTranslate()
}

function reset() {
  if (!imageLoaded.value) return
  initTransform()
  renderEditor()
}

function zoom(delta: number) {
  if (!imageLoaded.value) return
  scale = clamp(scale + delta * scale, minScale, maxScale)
  clampTranslate()
  renderEditor()
}

function rotate(deg: number) {
  if (!imageLoaded.value) return
  rotationDeg = (rotationDeg + deg) % 360
  normalizeTransform()
  renderEditor()
}

function flipHorizontal() {
  if (!imageLoaded.value) return
  flipX = -flipX
  renderEditor()
}

function flipVertical() {
  if (!imageLoaded.value) return
  flipY = -flipY
  renderEditor()
}

function onWheel(event: WheelEvent) {
  zoom(event.deltaY > 0 ? -0.06 : 0.06)
}

function onPointerDown(event: PointerEvent) {
  if (!imageLoaded.value) return
  dragging = true
  dragLastX = event.clientX
  dragLastY = event.clientY
  if (event.currentTarget) {
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  }
}

function onPointerMove(event: PointerEvent) {
  if (!dragging || !imageLoaded.value) return
  const deltaX = event.clientX - dragLastX
  const deltaY = event.clientY - dragLastY
  dragLastX = event.clientX
  dragLastY = event.clientY
  translateX += deltaX
  translateY += deltaY
  clampTranslate()
  renderEditor()
}

function onPointerUp(event: PointerEvent) {
  dragging = false
  if (event.currentTarget) {
    const target = event.currentTarget as HTMLElement
    if (target.hasPointerCapture(event.pointerId)) {
      target.releasePointerCapture(event.pointerId)
    }
  }
}

function loadImageFromSrc(src: string) {
  errorMsg.value = ''
  imageLoaded.value = false
  imageEl = null

  if (!src) {
    renderEditor()
    return
  }

  const img = new Image()
  if (!src.startsWith('data:') && !src.startsWith('blob:')) {
    img.crossOrigin = 'anonymous'
  }
  img.onload = () => {
    imageEl = img
    imageLoaded.value = true
    nextTick(() => {
      setCanvasMetrics()
      initTransform()
      renderEditor()
    })
  }
  img.onerror = () => {
    errorMsg.value = '图片加载失败，请重新选择'
    imageLoaded.value = false
    imageEl = null
    renderEditor()
  }
  img.src = src
}

function handleFileSelect(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  errorMsg.value = ''

  if (file.size > props.maxFileSize) {
    errorMsg.value = `图片不能超过 ${(props.maxFileSize / 1024 / 1024).toFixed(0)}MB`
    if (fileInputRef.value) fileInputRef.value.value = ''
    return
  }

  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    errorMsg.value = '仅支持 JPG、PNG、WebP 格式'
    if (fileInputRef.value) fileInputRef.value.value = ''
    return
  }

  clearLocalBlobUrl()
  localBlobUrl = URL.createObjectURL(file)
  loadImageFromSrc(localBlobUrl)

  if (fileInputRef.value) fileInputRef.value.value = ''
}

function confirmCrop() {
  if (!imageLoaded.value || cropping.value) return
  const sourceCanvas = buildSourceCanvas()
  if (!sourceCanvas) {
    errorMsg.value = '裁剪失败，请重试'
    return
  }

  cropping.value = true
  const output = document.createElement('canvas')
  const size = props.outputSize
  output.width = size
  output.height = size

  const ctx = output.getContext('2d')
  if (!ctx) {
    cropping.value = false
    errorMsg.value = '裁剪失败，请重试'
    return
  }

  ctx.clearRect(0, 0, size, size)
  ctx.save()
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()
  ctx.drawImage(
    sourceCanvas,
    cropCenterX - cropRadius,
    cropCenterY - cropRadius,
    cropRadius * 2,
    cropRadius * 2,
    0,
    0,
    size,
    size,
  )
  ctx.restore()

  const quality = props.outputType === 'image/png' ? undefined : outputQuality
  output.toBlob((blob) => {
    cropping.value = false
    if (!blob) {
      errorMsg.value = '裁剪失败，请重试'
      return
    }
    const url = URL.createObjectURL(blob)
    emit('crop', { blob, url })
    close()
  }, props.outputType, quality)
}

function handleResize() {
  if (!visible.value || !imageLoaded.value) return
  if (resizeRafId) cancelAnimationFrame(resizeRafId)
  resizeRafId = requestAnimationFrame(() => {
    resizeRafId = null
    setCanvasMetrics()
    normalizeTransform()
    renderEditor()
  })
}

watch(() => props.modelValue, (show) => {
  if (show) {
    errorMsg.value = ''
    nextTick(() => {
      setCanvasMetrics()
      loadImageFromSrc(props.imageSrc)
    })
  } else {
    clearLocalBlobUrl()
    dragging = false
    cropping.value = false
    errorMsg.value = ''
  }
})

watch(() => props.imageSrc, (src) => {
  if (visible.value) {
    loadImageFromSrc(src)
  }
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeRafId) cancelAnimationFrame(resizeRafId)
  clearLocalBlobUrl()
})
</script>

<style scoped lang="scss">
.cropper-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(15, 23, 42, 0.44);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.cropper-dialog {
  width: min(860px, 96vw);
  background: $color-bg;
  border: 1px solid $color-border;
  border-radius: 16px;
  box-shadow: 0 24px 52px rgba(15, 23, 42, 0.24);
  padding: 1rem;
  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.cropper-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 0.8rem;
}

.cropper-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
}

.cropper-subtitle {
  margin-top: 0.2rem;
  font-size: 0.8rem;
  color: $color-text-muted;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border: 1px solid $color-border;
  border-radius: 9px;
  background: $color-bg;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.cropper-body {
  display: grid;
  grid-template-columns: 1fr 250px;
  gap: 0.9rem;
}

.editor-panel {
  border: 1px solid $color-border;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.86), rgba(241, 245, 249, 0.9));
  padding: 0.65rem;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
}

.tool-group {
  display: inline-flex;
  gap: 0.4rem;
}

.tool-btn {
  width: 32px;
  height: 32px;
  border: 1px solid $color-border;
  border-radius: 8px;
  background: $color-bg;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: $color-text;
}

.text-tool {
  font-size: 0.95rem;
  font-weight: 700;
}

.editor-canvas {
  width: 100%;
  height: 360px;
  display: block;
  border-radius: 10px;
  background: #f1f5f9;
  touch-action: none;
  cursor: grab;
}

.editor-canvas:active {
  cursor: grabbing;
}

.preview-panel {
  border: 1px solid $color-border;
  border-radius: 12px;
  padding: 0.7rem;
  background: $color-bg-secondary;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
}

.preview-label {
  width: 100%;
  font-size: 0.8rem;
  color: $color-text-muted;
}

.preview-canvas {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  border: 1px solid $color-border;
  background: #fff;
}

.preview-hint {
  font-size: 0.75rem;
  color: $color-text-muted;
}

.cropper-footer {
  margin-top: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn {
  min-height: 36px;
  border-radius: 9px;
  padding: 0.4rem 0.8rem;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  white-space: nowrap;
}

.btn-primary {
  background: $color-primary;
  color: #fff;
}

.btn-secondary {
  background: transparent;
  color: $color-text;
  border: 1px solid $color-border;
}

.hidden-input {
  display: none;
}

.error-msg {
  font-size: 0.75rem;
  color: #ef4444;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.cropper-fade-enter-active,
.cropper-fade-leave-active {
  transition: opacity 0.2s ease;
}

.cropper-fade-enter-from,
.cropper-fade-leave-to {
  opacity: 0;
}

@media (max-width: $breakpoint-md) {
  .cropper-dialog {
    width: min(96vw, 96vw);
  }

  .cropper-body {
    grid-template-columns: 1fr;
  }

  .preview-panel {
    align-items: flex-start;
  }

  .preview-canvas {
    width: 132px;
    height: 132px;
  }

  .editor-canvas {
    height: 300px;
  }

  .cropper-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .footer-actions {
    justify-content: flex-end;
  }
}
</style>
