<template>
  <el-dialog v-model="visible" title="图片裁剪" width="720px" :close-on-click-modal="false"
    destroy-on-close append-to-body class="image-cropper-dialog">
    <div class="cropper-body">
      <!-- 裁剪区 -->
      <div class="cropper-main">
        <div class="cropper-toolbar">
          <span class="toolbar-hint">拖动调整位置，滚轮缩放</span>
          <div class="toolbar-actions">
            <el-button-group size="small">
              <el-button @click="zoom(-0.1)" title="缩小"><el-icon><ZoomOut /></el-icon></el-button>
              <el-button @click="zoom(0.1)" title="放大"><el-icon><ZoomIn /></el-icon></el-button>
            </el-button-group>
            <el-button-group size="small">
              <el-button @click="rotate(-90)" title="左旋转"><el-icon><RefreshLeft /></el-icon></el-button>
              <el-button @click="rotate(90)" title="右旋转"><el-icon><RefreshRight /></el-icon></el-button>
            </el-button-group>
            <el-button-group size="small">
              <el-button @click="flipH" title="水平翻转">↔</el-button>
              <el-button @click="flipV" title="垂直翻转">↕</el-button>
            </el-button-group>
            <el-button size="small" @click="reset" title="重置"><el-icon><Refresh /></el-icon></el-button>
          </div>
        </div>
        <div class="cropper-container">
          <img v-show="imgSrc" ref="imgRef" :src="imgSrc" class="cropper-image" @load="handleImageLoad" />
          <div v-if="!imgSrc" class="cropper-empty" @click="triggerUpload">
            <el-icon :size="40"><Plus /></el-icon>
            <span>点击选择图片</span>
          </div>
        </div>
      </div>
      <!-- 预览区 -->
      <div class="cropper-preview-panel">
        <div class="preview-label">裁剪预览</div>
        <div class="preview-box">
          <canvas ref="previewCanvasRef" class="preview-canvas"></canvas>
        </div>
        <!-- 比例选择 -->
        <div v-if="showRatioOptions" class="ratio-options">
          <div class="preview-label">裁剪比例</div>
          <div class="ratio-btns">
            <button v-for="r in ratioPresets" :key="r.label"
              :class="['ratio-btn', { active: currentRatioLabel === r.label }]"
              @click="changeRatio(r)">
              {{ r.label }}
            </button>
          </div>
        </div>
        <!-- 输出质量 -->
        <div class="quality-control">
          <div class="preview-label">输出质量</div>
          <el-slider v-model="outputQuality" :min="0.1" :max="1" :step="0.1"
            :format-tooltip="(v: number) => Math.round(v * 100) + '%'" />
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <div class="footer-left">
          <input ref="fileInputRef" type="file" accept="image/*" hidden @change="handleFileSelect" />
          <el-button @click="triggerUpload">
            <el-icon><Upload /></el-icon> {{ imgSrc ? '更换图片' : '选择图片' }}
          </el-button>
          <span v-if="errorMsg" class="error-msg">{{ errorMsg }}</span>
        </div>
        <div class="footer-right">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" @click="confirmCrop" :disabled="!imgSrc" :loading="cropping">确认裁剪</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { Plus, ZoomIn, ZoomOut, RefreshLeft, RefreshRight, Refresh, Upload } from '@element-plus/icons-vue'

interface RatioPreset { label: string; value: [number, number] }

const props = withDefaults(defineProps<{
  modelValue: boolean
  imageSrc?: string
  aspectRatio?: [number, number]
  outputType?: 'image/jpeg' | 'image/png' | 'image/webp'
  maxFileSize?: number
  maxOutputWidth?: number
  showRatioOptions?: boolean
}>(), {
  aspectRatio: (): [number, number] => [16, 9],
  outputType: 'image/webp',
  maxFileSize: 5 * 1024 * 1024,
  maxOutputWidth: 1200,
  showRatioOptions: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'crop': [data: { blob: Blob; url: string }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const imgRef = ref<HTMLImageElement>()
const previewCanvasRef = ref<HTMLCanvasElement>()
const fileInputRef = ref<HTMLInputElement>()
const imgSrc = ref(props.imageSrc || '')
const errorMsg = ref('')
const outputQuality = ref(0.9)
let cropper: Cropper | null = null
let scaleX = 1
let scaleY = 1
let previewRafId: number | null = null
let currentAspectRatio = NaN // 跟踪当前裁剪比例，替代 cropper.options.aspectRatio
let previewCtx: CanvasRenderingContext2D | null = null
let lastPreviewW = 0
let lastPreviewH = 0
let localBlobUrl: string | null = null
const PREVIEW_WIDTH = 218 // 预览面板宽度 - 2px border

const ratioPresets: RatioPreset[] = [
  { label: '16:9', value: [16, 9] },
  { label: '4:3', value: [4, 3] },
  { label: '3:2', value: [3, 2] },
  { label: '1:1', value: [1, 1] },
  { label: '自由', value: [0, 0] },
]
const currentRatioLabel = ref('16:9')

function canUseImageSource(src: string) {
  if (!src) return false
  if (src.startsWith('blob:') || src.startsWith('data:') || src.startsWith('/')) return true
  if (typeof window === 'undefined') return false
  try {
    return new URL(src, window.location.origin).origin === window.location.origin
  } catch {
    return false
  }
}

// imageSrc 统一在 watch(visible) 中处理，避免跨域问题

watch(visible, async (show) => {
  if (show) {
    const ar = props.aspectRatio
    const matched = ratioPresets.find(r => r.value[0] === ar[0] && r.value[1] === ar[1])
    currentRatioLabel.value = matched?.label || '16:9'
    imgSrc.value = ''
    errorMsg.value = ''
    if (props.imageSrc) {
      if (canUseImageSource(props.imageSrc)) {
        imgSrc.value = props.imageSrc
      } else {
        errorMsg.value = '远程图片无法直接裁剪，请重新选择本地图片'
      }
    }
    await nextTick()
    if (imgRef.value?.complete) {
      initCropper()
    }
  } else {
    destroyCropper(true)
    errorMsg.value = ''
  }
}, { immediate: true })

function updatePreview() {
  if (!cropper || !previewCanvasRef.value || !imgRef.value) return
  try {
    const data = cropper.getData(true)
    if (data.width <= 0 || data.height <= 0) return

    const w = PREVIEW_WIDTH
    // 固定比例时用精确比例值计算高度，避免 getData 整数取整导致 ±1px 跳动抖动
    const h = (currentAspectRatio && isFinite(currentAspectRatio))
      ? Math.floor(w / currentAspectRatio)
      : Math.floor(w * data.height / data.width)
    if (h <= 0) return
    const canvas = previewCanvasRef.value

    // 仅在尺寸变化时重设 canvas 缓冲区
    if (lastPreviewW !== w || lastPreviewH !== h) {
      canvas.width = w
      canvas.height = h
      canvas.style.height = h + 'px'
      lastPreviewW = w
      lastPreviewH = h
      previewCtx = null
    }

    if (!previewCtx) {
      previewCtx = canvas.getContext('2d')
      if (!previewCtx) return
    }

    previewCtx.clearRect(0, 0, w, h)

    const hasTransform = data.rotate !== 0 || scaleX !== 1 || scaleY !== 1
    if (hasTransform) {
      // 有旋转/翻转时，用 canvas transform 手动绘制，避免每帧调用 getCroppedCanvas
      previewCtx.save()
      previewCtx.translate(w / 2, h / 2)
      previewCtx.rotate(data.rotate * Math.PI / 180)
      previewCtx.scale(scaleX, scaleY)
      // 旋转 90/270 度时源区域宽高互换
      const isRotated = Math.abs(data.rotate % 180) === 90
      const sw = isRotated ? data.height : data.width
      const sh = isRotated ? data.width : data.height
      const sx = isRotated ? data.y : data.x
      const sy = isRotated ? data.x : data.y
      previewCtx.drawImage(imgRef.value, sx, sy, sw, sh, -w / 2, -h / 2, w, h)
      previewCtx.restore()
    } else {
      previewCtx.drawImage(imgRef.value, data.x, data.y, data.width, data.height, 0, 0, w, h)
    }
  } catch { /* 过渡期间忽略错误 */ }
}

function schedulePreview() {
  if (previewRafId) return
  previewRafId = requestAnimationFrame(() => {
    previewRafId = null
    updatePreview()
  })
}

function initCropper() {
  destroyCropper(false)
  if (!imgRef.value || !imgSrc.value) return
  const ar = props.aspectRatio
  const ratio = ar[0] === 0 ? NaN : ar[0] / ar[1]
  currentAspectRatio = ratio
  cropper = new Cropper(imgRef.value, {
    aspectRatio: ratio,
    viewMode: 1,
    dragMode: 'move',
    autoCrop: true,
    autoCropArea: 0.8,
    responsive: true,
    restore: true,
    guides: true,
    center: true,
    highlight: true,
    cropBoxMovable: true,
    cropBoxResizable: true,
    toggleDragModeOnDblclick: false,
    ready: () => {
      if (!cropper) return
      const containerData = cropper.getContainerData()
      const width = Math.max(120, Math.floor(containerData.width * 0.8))
      const height = currentAspectRatio && isFinite(currentAspectRatio)
        ? Math.max(80, Math.floor(width / currentAspectRatio))
        : Math.max(80, Math.floor(containerData.height * 0.8))
      cropper.setCropBoxData({
        left: Math.max(0, Math.floor((containerData.width - width) / 2)),
        top: Math.max(0, Math.floor((containerData.height - height) / 2)),
        width,
        height,
      })
      schedulePreview()
    },
    crop: schedulePreview,
    cropmove: schedulePreview,
  })
  scaleX = 1
  scaleY = 1
}

async function handleImageLoad() {
  if (!visible.value || !imgSrc.value) return
  await nextTick()
  initCropper()
}

function destroyCropper(releaseLocalBlobUrl = false) {
  if (previewRafId) { cancelAnimationFrame(previewRafId); previewRafId = null }
  if (cropper) { cropper.destroy(); cropper = null }
  if (releaseLocalBlobUrl && localBlobUrl) { URL.revokeObjectURL(localBlobUrl); localBlobUrl = null }
  previewCtx = null
  lastPreviewW = 0
  lastPreviewH = 0
}

function zoom(delta: number) { cropper?.zoom(delta) }
function rotate(deg: number) { cropper?.rotate(deg) }
function flipH() { scaleX = -scaleX; cropper?.scaleX(scaleX) }
function flipV() { scaleY = -scaleY; cropper?.scaleY(scaleY) }
function reset() {
  cropper?.reset()
  scaleX = 1
  scaleY = 1
  // 重置时恢复初始比例
  const ar = props.aspectRatio
  const matched = ratioPresets.find(r => r.value[0] === ar[0] && r.value[1] === ar[1])
  currentRatioLabel.value = matched?.label || '16:9'
  const ratio = ar[0] === 0 ? NaN : ar[0] / ar[1]
  currentAspectRatio = ratio
  cropper?.setAspectRatio(ratio)
}

function changeRatio(r: RatioPreset) {
  currentRatioLabel.value = r.label
  const ratio = r.value[0] === 0 ? NaN : r.value[0] / r.value[1]
  currentAspectRatio = ratio
  cropper?.setAspectRatio(ratio)
  schedulePreview()
}

function triggerUpload() { fileInputRef.value?.click() }

function handleFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  errorMsg.value = ''
  if (file.size > props.maxFileSize) {
    errorMsg.value = `图片不能超过 ${(props.maxFileSize / 1024 / 1024).toFixed(0)}MB`
    if (fileInputRef.value) fileInputRef.value.value = ''
    return
  }
  // 释放上一次的 blob URL
  if (localBlobUrl) { URL.revokeObjectURL(localBlobUrl); localBlobUrl = null }
  localBlobUrl = URL.createObjectURL(file)
  imgSrc.value = localBlobUrl
  nextTick(() => {
    scaleX = 1
    scaleY = 1
    if (cropper) {
      cropper.replace(imgSrc.value, false)
      schedulePreview()
    }
    else initCropper()
  })
  if (fileInputRef.value) fileInputRef.value.value = ''
}

const cropping = ref(false)

function confirmCrop() {
  if (!cropper || cropping.value) return
  cropping.value = true
  const canvas = cropper.getCroppedCanvas({
    maxWidth: props.maxOutputWidth,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high',
  })

  if (!canvas) {
    cropping.value = false
    errorMsg.value = '裁剪失败，请重新选择图片'
    return
  }

  canvas.toBlob((blob) => {
    cropping.value = false
    if (!blob) {
      errorMsg.value = '裁剪失败，请重试'
      return
    }
    const url = URL.createObjectURL(blob)
    emit('crop', { blob, url })
    visible.value = false
  }, props.outputType, outputQuality.value)
}

onBeforeUnmount(() => { destroyCropper(true) })
</script>

<style lang="scss">
.image-cropper-dialog {
  .el-dialog__body { padding: 16px 20px; }
  .el-dialog { border-radius: 12px; }
}
</style>

<style scoped lang="scss">
.cropper-body {
  display: flex;
  gap: 16px;
  min-height: 380px;
}

.cropper-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.cropper-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;

  .toolbar-hint {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }
  .toolbar-actions {
    display: flex;
    gap: 6px;
  }
}

.cropper-container {
  flex: 1;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  min-height: 320px;
}

.cropper-image {
  display: block;
  max-width: 100%;
}

.cropper-empty {
  width: 100%;
  height: 100%;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
  transition: color 0.2s;
  &:hover { color: var(--el-color-primary); }
}

.cropper-preview-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.preview-box {
  width: 100%;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;

  .preview-canvas {
    display: block;
    width: 100%;
    // height 由 JS 动态设置
  }
}

.ratio-btns {
  display: flex;
  gap: 6px;

  .ratio-btn {
    padding: 4px 12px;
    font-size: 12px;
    font-weight: 500;
    border: 1px solid var(--el-border-color);
    background: var(--el-fill-color-blank, #fff);
    border-radius: 4px;
    cursor: pointer;
    color: var(--el-text-color-regular);
    transition: all 0.15s;
    white-space: nowrap;

    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }
    &.active {
      background: var(--el-color-primary);
      color: #fff;
      border-color: var(--el-color-primary);
    }
  }
}

.quality-control {
  :deep(.el-slider) { padding: 0 6px; }
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .footer-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .footer-right {
    display: flex;
    gap: 8px;
  }
  .error-msg {
    font-size: 12px;
    color: var(--el-color-danger);
  }
}
</style>
