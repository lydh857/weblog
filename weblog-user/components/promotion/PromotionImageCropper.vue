<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="cropper-overlay" @click.self="close">
        <section class="promotion-cropper-modal" role="dialog" aria-modal="true" aria-label="广告图片裁剪">
          <header class="cropper-header">
            <div>
              <h3>广告图片裁剪</h3>
              <p>拖拽调整位置，滚轮可缩放，输出比例 {{ currentRatioText }}</p>
            </div>
            <button class="icon-btn" type="button" aria-label="关闭" @click="close">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </header>

          <div class="cropper-body">
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
              <button type="button" class="tool-btn text" @click="flipH">↔</button>
              <button type="button" class="tool-btn text" @click="flipV">↕</button>
              <button type="button" class="tool-btn" @click="reset">
                <Icon name="heroicons:arrow-path-20-solid" size="16" />
              </button>
            </div>

            <div class="cropper-shell">
              <img v-show="imageReady" ref="imageRef" :src="imgSrc" class="cropper-image" alt="待裁剪图片">
              <button v-if="!imageReady" class="empty-select" type="button" @click="triggerUpload">
                <Icon name="heroicons:photo-20-solid" size="22" />
                <span>选择图片开始裁剪</span>
              </button>
            </div>
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
  imageSrc?: string
  aspectRatio?: [number, number]
  outputType?: 'image/jpeg' | 'image/png' | 'image/webp'
  maxFileSize?: number
  maxOutputWidth?: number
}>(), {
  imageSrc: '',
  aspectRatio: () => [16, 9],
  outputType: 'image/webp',
  maxFileSize: 8 * 1024 * 1024,
  maxOutputWidth: 1600,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  crop: [data: { blob: Blob; url: string }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const imageRef = ref<HTMLImageElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const imgSrc = ref('')
const imageReady = ref(false)
const cropping = ref(false)
const errorMsg = ref('')

let cropper: Cropper | null = null
let scaleX = 1
let scaleY = 1
let localBlobUrl: string | null = null

const currentAspectRatio = computed(() => {
  const [w, h] = props.aspectRatio
  if (!w || !h) return NaN
  return w / h
})

const currentRatioText = computed(() => {
  const [w, h] = props.aspectRatio
  return `${w}:${h}`
})

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

function initCropper() {
  if (!imageRef.value || !imgSrc.value) return
  destroyCropper(true)

  cropper = new Cropper(imageRef.value, {
    aspectRatio: currentAspectRatio.value,
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 0.9,
    responsive: true,
    guides: true,
    center: true,
    cropBoxMovable: true,
    cropBoxResizable: true,
    toggleDragModeOnDblclick: false,
    ready: () => {
      imageReady.value = true
    },
  })

  scaleX = 1
  scaleY = 1
}

function reset() {
  cropper?.reset()
  scaleX = 1
  scaleY = 1
}

function zoom(delta: number) {
  cropper?.zoom(delta)
}

function rotate(deg: number) {
  cropper?.rotate(deg)
}

function flipH() {
  if (!cropper) return
  scaleX = -scaleX
  cropper.scaleX(scaleX)
}

function flipV() {
  if (!cropper) return
  scaleY = -scaleY
  cropper.scaleY(scaleY)
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
  const canvas = cropper.getCroppedCanvas({
    maxWidth: props.maxOutputWidth,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high',
  })

  canvas.toBlob(blob => {
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
})
</script>

<style scoped lang="scss">
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .promotion-cropper-modal {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .promotion-cropper-modal {
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
  padding: 1rem;

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

.promotion-cropper-modal {
  position: relative;
  width: min(920px, 100%);
  border: 1px solid $color-border;
  border-radius: 12px;
  background: $color-bg;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;

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
  }

  p {
    margin: 0.28rem 0 0;
    font-size: 0.82rem;
    color: $color-text-muted;
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
  min-height: 380px;
  border: 1px solid $color-border;
  border-radius: 10px;
  overflow: hidden;
  background: rgba(241, 245, 249, 0.92);

  :deep(.cropper-container) {
    width: 100%;
    height: 380px;
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
  min-height: 380px;
  border: none;
  background: transparent;
  color: $color-text-muted;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  cursor: pointer;
}

.cropper-footer {
  padding: 0.85rem 1rem;
  border-top: 1px solid $color-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;

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
}

@media (max-width: $breakpoint-md) {
  .cropper-shell,
  .cropper-shell :deep(.cropper-container),
  .empty-select {
    min-height: 280px;
    height: 280px;
  }

  .cropper-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .left-actions,
  .right-actions {
    justify-content: space-between;
  }
}
</style>
