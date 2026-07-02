<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="cropper-overlay" :class="{ 'cropper-overlay--dark': isDarkMode }" @click.self="close">
        <section class="avatar-cropper-modal" role="dialog" aria-modal="true" aria-label="头像裁剪">
          <header class="cropper-header">
            <div>
              <h3>头像裁剪</h3>
              <p>拖拽移动，滚轮缩放，支持拖动裁剪框边缘自由调整大小</p>
            </div>
            <button class="icon-btn" type="button" aria-label="关闭" @click="close">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </header>

          <div class="cropper-body">
            <div class="editor-panel">
              <div class="toolbar">
                <button type="button" class="tool-btn" @click="zoom(-0.1)">
                  <Icon name="heroicons:magnifying-glass-minus-20-solid" size="16" />
                </button>
                <button type="button" class="tool-btn" @click="zoom(0.1)">
                  <Icon name="heroicons:magnifying-glass-plus-20-solid" size="16" />
                </button>
                <button type="button" class="tool-btn" @click="rotate(-90)">
                  <Icon name="heroicons:arrow-uturn-left-20-solid" size="16" />
                </button>
                <button type="button" class="tool-btn" @click="rotate(90)">
                  <Icon name="heroicons:arrow-uturn-right-20-solid" size="16" />
                </button>
                <button type="button" class="tool-btn text" @click="flipHorizontal">↔</button>
                <button type="button" class="tool-btn text" @click="flipVertical">↕</button>
                <button type="button" class="tool-btn" @click="reset">
                  <Icon name="heroicons:arrow-path-20-solid" size="16" />
                </button>
              </div>

              <div class="cropper-shell">
                <img v-show="imageReady" ref="imageRef" :src="imgSrc" class="cropper-image" alt="待裁剪头像">
                <button v-if="!imageReady" class="empty-select" type="button" @click="triggerUpload">
                  <Icon name="heroicons:photo-20-solid" size="22" />
                  <span>选择图片开始裁剪</span>
                </button>
              </div>
            </div>

            <aside class="preview-panel">
              <p class="preview-label">预览</p>
              <canvas ref="previewCanvasRef" class="preview-canvas" />
              <p class="preview-hint">输出为圆形头像</p>
            </aside>
          </div>

          <footer class="cropper-footer">
            <div class="left-actions">
              <input
                ref="fileInputRef"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                class="hidden-input"
                @change="handleFileSelect"
              >
              <button type="button" class="btn secondary" @click="triggerUpload">{{ imageReady ? '更换图片' : '选择图片' }}</button>
              <span v-if="errorMsg" class="error-msg">{{ errorMsg }}</span>
            </div>
            <div class="right-actions">
              <button type="button" class="btn secondary" @click="close">取消</button>
              <button type="button" class="btn primary" :disabled="!imageReady || cropping" @click="confirmCrop">
                <Icon v-if="cropping" name="heroicons:arrow-path-16-solid" size="14" class="spin" />
                {{ cropping ? '处理中...' : '确认裁剪' }}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

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
  set: value => emit('update:modelValue', value),
})

const imageRef = ref<HTMLImageElement | null>(null)
const previewCanvasRef = ref<HTMLCanvasElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const imgSrc = ref('')
const imageReady = ref(false)
const cropping = ref(false)
const errorMsg = ref('')

let cropper: Cropper | null = null
let localBlobUrl: string | null = null
let scaleX = 1
let scaleY = 1
const isDarkMode = ref(false)
let themeObserver: MutationObserver | null = null

function updateDarkModeState() {
  if (!import.meta.client) {
    isDarkMode.value = false
    return
  }

  const htmlEl = document.documentElement
  const bodyEl = document.body
  isDarkMode.value = htmlEl.classList.contains('dark') || bodyEl.classList.contains('dark') || htmlEl.getAttribute('data-theme') === 'dark'
}

function close() {
  visible.value = false
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function revokeLocalBlobUrl() {
  if (!localBlobUrl) return
  URL.revokeObjectURL(localBlobUrl)
  localBlobUrl = null
}

function destroyCropper(keepBlobUrl = false) {
  if (cropper) {
    cropper.destroy()
    cropper = null
  }
  if (!keepBlobUrl) {
    revokeLocalBlobUrl()
  }
  imageReady.value = false
}

function renderPreview() {
  const previewCanvas = previewCanvasRef.value
  if (!previewCanvas || !cropper) return

  const size = 180
  if (previewCanvas.width !== size) previewCanvas.width = size
  if (previewCanvas.height !== size) previewCanvas.height = size

  const previewCtx = previewCanvas.getContext('2d')
  if (!previewCtx) return

  const croppedCanvas = cropper.getCroppedCanvas({
    width: size,
    height: size,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high',
  })

  previewCtx.clearRect(0, 0, size, size)
  previewCtx.save()
  previewCtx.beginPath()
  previewCtx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  previewCtx.clip()
  previewCtx.drawImage(croppedCanvas, 0, 0, size, size)
  previewCtx.restore()
}

function initCropper() {
  if (!imageRef.value || !imgSrc.value) return
  destroyCropper(true)

  cropper = new Cropper(imageRef.value, {
    aspectRatio: 1,
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 0.78,
    responsive: true,
    guides: true,
    center: true,
    cropBoxMovable: true,
    cropBoxResizable: true,
    toggleDragModeOnDblclick: false,
    minCropBoxWidth: 90,
    minCropBoxHeight: 90,
    ready: () => {
      imageReady.value = true
      renderPreview()
    },
    crop: () => {
      renderPreview()
    },
  })

  scaleX = 1
  scaleY = 1
}

function reset() {
  cropper?.reset()
  scaleX = 1
  scaleY = 1
  renderPreview()
}

function zoom(delta: number) {
  cropper?.zoom(delta)
}

function rotate(deg: number) {
  cropper?.rotate(deg)
  renderPreview()
}

function flipHorizontal() {
  if (!cropper) return
  scaleX = -scaleX
  cropper.scaleX(scaleX)
  renderPreview()
}

function flipVertical() {
  if (!cropper) return
  scaleY = -scaleY
  cropper.scaleY(scaleY)
  renderPreview()
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

  revokeLocalBlobUrl()
  localBlobUrl = URL.createObjectURL(file)
  imgSrc.value = localBlobUrl

  nextTick(() => {
    initCropper()
  })

  if (fileInputRef.value) fileInputRef.value.value = ''
}

function confirmCrop() {
  if (!cropper || cropping.value) return

  cropping.value = true
  const size = props.outputSize
  const squareCanvas = cropper.getCroppedCanvas({
    width: size,
    height: size,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high',
  })

  const outputCanvas = document.createElement('canvas')
  outputCanvas.width = size
  outputCanvas.height = size
  const outputCtx = outputCanvas.getContext('2d')
  if (!outputCtx) {
    cropping.value = false
    errorMsg.value = '裁剪失败，请重试'
    return
  }

  outputCtx.clearRect(0, 0, size, size)
  if (props.outputType === 'image/jpeg') {
    outputCtx.fillStyle = '#ffffff'
    outputCtx.fillRect(0, 0, size, size)
  }
  outputCtx.save()
  outputCtx.beginPath()
  outputCtx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  outputCtx.clip()
  outputCtx.drawImage(squareCanvas, 0, 0, size, size)
  outputCtx.restore()

  outputCanvas.toBlob(blob => {
    cropping.value = false
    if (!blob) {
      errorMsg.value = '裁剪失败，请重试'
      return
    }

    const url = URL.createObjectURL(blob)
    emit('crop', { blob, url })
    close()
  }, props.outputType, props.outputType === 'image/png' ? undefined : 0.9)
}

watch(() => visible.value, show => {
  if (!show) {
    destroyCropper()
    errorMsg.value = ''
    cropping.value = false
    return
  }

  imgSrc.value = props.imageSrc || ''
  errorMsg.value = ''
  imageReady.value = false

  nextTick(() => {
    if (!imgSrc.value) return
    initCropper()
  })
})

watch(() => props.imageSrc, value => {
  if (!visible.value) return
  if (localBlobUrl) return
  imgSrc.value = value || ''
  nextTick(() => {
    if (!imgSrc.value) return
    initCropper()
  })
})

onUnmounted(() => {
  destroyCropper()
  themeObserver?.disconnect()
  themeObserver = null
})

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  updateDarkModeState()
  themeObserver = new MutationObserver(() => {
    updateDarkModeState()
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
  themeObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
})
</script>

<style scoped lang="scss">
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .avatar-cropper-modal {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .avatar-cropper-modal {
    transform: translateY(20px) scale(0.96);
  }
}

.cropper-overlay {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-modal) + 2);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  padding: max(1rem, env(safe-area-inset-top)) max(1rem, env(safe-area-inset-right)) max(1rem, env(safe-area-inset-bottom)) max(1rem, env(safe-area-inset-left));
  overflow: auto;

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

@media (hover: none) and (pointer: coarse) {
  .cropper-overlay {
    backdrop-filter: none;
    background: rgba(0, 0, 0, 0.52);
  }
}

.cropper-overlay--dark {
  background: rgba(0, 0, 0, 0.6);
}

.avatar-cropper-modal {
  --cropper-shell-height: clamp(220px, 52dvh, 390px);
  width: min(960px, 100%);
  max-height: calc(100dvh - env(safe-area-inset-top) - env(safe-area-inset-bottom) - 2rem);
  display: flex;
  flex-direction: column;
  border: 1px solid $color-border;
  border-radius: 12px;
  background: $color-bg;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  transform: translate3d(0, 0, 0);
  will-change: transform;

  .dark & {
    border-color: rgba(148, 163, 184, 0.14);
    background: $color-dark-bg-secondary;
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
  }
}

.cropper-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }

  h3 {
    margin: 0;
    font-size: 1rem;

    .dark & {
      color: $color-dark-text;
    }
  }

  p {
    margin: 0.28rem 0 0;
    font-size: 0.82rem;
    color: $color-text-muted;

    .dark & {
      color: $color-dark-text-muted;
    }
  }
}

.icon-btn {
  width: 30px;
  height: 30px;
  border: 1px solid $color-border;
  border-radius: 8px;
  background: $color-bg;
  color: $color-text-muted;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  .dark & {
    border-color: $color-dark-border;
    background: $color-dark-bg;
    color: $color-dark-text-muted;
  }
}

.cropper-body {
  padding: 0.9rem 1rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 0.9rem;
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.editor-panel {
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 0.75rem;
}

.tool-btn {
  width: 32px;
  height: 32px;
  border: 1px solid $color-border;
  border-radius: 8px;
  background: $color-bg;
  color: $color-text;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  .dark & {
    border-color: $color-dark-border;
    background: $color-dark-bg;
    color: $color-dark-text;
  }

  &.text {
    font-size: 0.9rem;
    font-weight: 700;
  }
}

.cropper-shell {
  min-height: var(--cropper-shell-height);
  border: 1px solid $color-border;
  border-radius: 10px;
  overflow: hidden;
  background: rgba(241, 245, 249, 0.92);

  :deep(.cropper-container) {
    width: 100%;
    height: var(--cropper-shell-height);
  }

  :deep(.cropper-view-box),
  :deep(.cropper-face) {
    border-radius: 50%;
  }

  :deep(.cropper-view-box) {
    outline: 2px solid rgba(59, 130, 246, 0.92);
    outline-color: rgba(59, 130, 246, 0.92);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9);
  }

  :deep(.cropper-line) {
    background-color: rgba(59, 130, 246, 0.82);
  }

  :deep(.cropper-point) {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background-color: #3b82f6;
    border: 1px solid rgba(255, 255, 255, 0.9);
  }

  .dark & {
    border-color: $color-dark-border;
    background: rgba(15, 23, 42, 0.82);
  }
}

.cropper-image {
  display: block;
  max-width: 100%;
}

.empty-select {
  width: 100%;
  min-height: var(--cropper-shell-height);
  border: none;
  background: transparent;
  color: $color-text-muted;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  cursor: pointer;

  .dark & {
    color: $color-dark-text-muted;
  }
}

.preview-panel {
  border: 1px solid $color-border;
  border-radius: 12px;
  padding: 0.72rem;
  background: $color-bg-secondary;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;

  .dark & {
    border-color: $color-dark-border;
    background: $color-dark-bg;
  }
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

  .dark & {
    border-color: $color-dark-border;
    background: #0b1220;
  }
}

.cropper-overlay--dark .preview-panel {
  border-color: #2a313a;
  background: #101215;
}

.cropper-overlay--dark .preview-label,
.cropper-overlay--dark .preview-hint {
  color: #9aa5b5;
}

.cropper-overlay--dark .preview-canvas {
  border-color: #2a313a;
  background: #0b1220;
}

:global(html.dark) .preview-panel {
  border-color: #2a313a;
  background: #101215;
}

:global(html.dark) .preview-canvas {
  border-color: #2a313a;
  background: #0b1220;
}

.preview-hint {
  font-size: 0.75rem;
  color: $color-text-muted;
}

.cropper-footer {
  padding: 0.85rem 1rem;
  border-top: 1px solid $color-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  flex-shrink: 0;

  .dark & {
    border-top-color: $color-dark-border;
  }
}

.left-actions,
.right-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.left-actions {
  min-width: 0;
}

.hidden-input {
  display: none;
}

.btn {
  height: 34px;
  border-radius: 9px;
  border: 1px solid $color-border;
  padding: 0 0.85rem;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.28rem;

  &.secondary {
    background: $color-bg;
    color: $color-text-muted;

    .dark & {
      background: $color-dark-bg;
      border-color: $color-dark-border;
      color: $color-dark-text-muted;
    }
  }

  &.primary {
    border-color: $color-primary;
    background: $color-primary;
    color: #fff;

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}

.error-msg {
  font-size: 0.75rem;
  color: var(--status-danger);
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

@media (max-width: $breakpoint-md) {
  .cropper-overlay {
    align-items: flex-start;
    padding: max(0.55rem, env(safe-area-inset-top)) max(0.55rem, env(safe-area-inset-right)) max(0.55rem, env(safe-area-inset-bottom)) max(0.55rem, env(safe-area-inset-left));
  }

  .avatar-cropper-modal {
    --cropper-shell-height: clamp(180px, 38dvh, 300px);
    width: 100%;
    max-height: calc(100dvh - env(safe-area-inset-top) - env(safe-area-inset-bottom) - 1.1rem);
    border-radius: 10px;
  }

  .cropper-header {
    padding: 0.8rem 0.85rem;

    p {
      margin-top: 0.24rem;
      font-size: 0.78rem;
      line-height: 1.35;
    }
  }

  .cropper-body {
    padding: 0.78rem 0.85rem;
    grid-template-columns: 1fr;
  }

  .preview-panel {
    align-items: flex-start;
    padding: 0.62rem;
  }

  .preview-canvas {
    width: 132px;
    height: 132px;
  }

  .cropper-footer {
    padding: 0.75rem 0.85rem max(0.75rem, env(safe-area-inset-bottom));
    flex-direction: column;
    align-items: stretch;
  }

  .left-actions,
  .right-actions {
    justify-content: space-between;
  }

  .error-msg {
    white-space: normal;
    line-height: 1.35;
  }
}
</style>
