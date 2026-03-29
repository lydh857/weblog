<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="poster-overlay" @click.self="$emit('close')">
        <div class="poster-modal">
          <div class="poster-header">
            <h3>分享海报</h3>
            <button class="close-btn" aria-label="关闭" @click="$emit('close')">
              <Icon name="heroicons:x-mark-20-solid" size="20" />
            </button>
          </div>
          <div class="poster-body">
            <canvas ref="posterCanvas" class="poster-canvas" />
          </div>
          <div class="poster-actions">
            <button class="btn btn-primary" @click="downloadPoster">
              <Icon name="heroicons:arrow-down-tray-16-solid" size="16" />
              保存图片
            </button>
            <button class="btn btn-secondary" @click="copyLink">
              <Icon name="heroicons:link-16-solid" size="16" />
              复制链接
            </button>
          </div>
          <p v-if="tip" class="tip">{{ tip }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  title: string
  summary?: string | null
  author?: string
  url: string
}>()

defineEmits<{ close: [] }>()

const posterCanvas = ref<HTMLCanvasElement | null>(null)
const tip = ref('')
const siteConfig = useSiteConfigState()
const siteName = computed(() => siteConfig.value.siteName || DEFAULT_SITE_NAME)

watch(() => props.visible, (v) => {
  if (v) {
    nextTick(() => {
      void drawPoster()
    })
  }
})

function buildCampaignName() {
  return siteName.value.trim().replace(/\s+/g, '-').toLowerCase() || 'site'
}

function buildPosterShareUrl() {
  const baseUrl = props.url?.trim() || ''
  if (!baseUrl) return ''
  const campaign = buildCampaignName()
  return `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}utm_source=share&utm_medium=poster&utm_campaign=${campaign}`
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.decoding = 'async'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = src
  })
}

async function drawPoster() {
  if (!import.meta.client) return

  const canvas = posterCanvas.value
  if (!canvas) return
  const shareUrl = buildPosterShareUrl() || props.url?.trim() || ''

  const dpr = window.devicePixelRatio || 1
  const viewportWidth = window.innerWidth || 390
  const w = Math.round(Math.min(360, Math.max(280, viewportWidth - 92)))
  const h = Math.round((w * 4) / 3)
  const scale = w / 360
  const unit = (value: number, min = 0) => Math.max(min, value * scale)

  canvas.width = w * dpr
  canvas.height = h * dpr
  canvas.style.width = `${w}px`
  canvas.style.height = `${h}px`

  const ctx = canvas.getContext('2d')!
  ctx.scale(dpr, dpr)

  // 背景渐变
  const grad = ctx.createLinearGradient(0, 0, 0, h)
  grad.addColorStop(0, '#1e3a5f')
  grad.addColorStop(1, '#0f172a')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, h)

  // 装饰圆
  ctx.fillStyle = 'rgba(59, 130, 246, 0.15)'
  ctx.beginPath(); ctx.arc(unit(304), unit(52), unit(84, 56), 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = 'rgba(59, 130, 246, 0.08)'
  ctx.beginPath(); ctx.arc(unit(38), h - unit(62), unit(58, 40), 0, Math.PI * 2); ctx.fill()

  // 标题
  ctx.fillStyle = '#f1f5f9'
  ctx.font = `bold ${Math.round(unit(22, 18))}px -apple-system, BlinkMacSystemFont, sans-serif`
  const titleBottomY = wrapText(
    ctx,
    props.title,
    unit(24),
    unit(66),
    w - unit(48),
    unit(30, 24),
    3
  )

  // 摘要
  if (props.summary) {
    const summaryStartY = titleBottomY + unit(8)
    const summaryLineHeight = unit(20, 16)
    const footerSafeTop = h - unit(156)
    const summaryMaxLines = Math.max(2, Math.min(5, Math.floor((footerSafeTop - summaryStartY) / summaryLineHeight)))

    ctx.fillStyle = '#94a3b8'
    ctx.font = `${Math.round(unit(14, 12))}px -apple-system, BlinkMacSystemFont, sans-serif`

    if (summaryMaxLines > 0) {
      wrapText(ctx, props.summary, unit(24), summaryStartY, w - unit(48), summaryLineHeight, summaryMaxLines)
    }
  }

  // 作者
  ctx.fillStyle = '#64748b'
  ctx.font = `${Math.round(unit(13, 12))}px -apple-system, BlinkMacSystemFont, sans-serif`
  ctx.fillText(`作者: ${props.author || siteName.value}`, unit(24), h - unit(78))

  if (shareUrl) {
    try {
      const QRCode = await import('qrcode')
      const qrDataUrl = await QRCode.toDataURL(shareUrl, {
        width: 100,
        margin: 1,
        errorCorrectionLevel: 'M',
        color: {
          dark: '#0f172a',
          light: '#ffffff'
        }
      })
      const qrImage = await loadImage(qrDataUrl)

      const qrSize = Math.round(unit(96, 84))
      const qrX = w - unit(24) - qrSize
      const qrY = h - unit(128) - (qrSize - unit(96, 84)) * 0.2

      ctx.fillStyle = 'rgba(255, 255, 255, 0.95)'
      ctx.fillRect(qrX - 3, qrY - 3, qrSize + 6, qrSize + 6)
      ctx.drawImage(qrImage, qrX, qrY, qrSize, qrSize)
    } catch {
      ctx.fillStyle = '#64748b'
      ctx.font = `${Math.round(unit(11, 10))}px -apple-system, BlinkMacSystemFont, sans-serif`
      ctx.fillText('二维码生成失败，请复制链接阅读', unit(24), h - unit(56))
    }
  }

  // 底部提示
  ctx.fillStyle = '#475569'
  ctx.font = `${Math.round(unit(12, 11))}px -apple-system, BlinkMacSystemFont, sans-serif`
  ctx.fillText('扫码阅读全文', unit(24), h - unit(24))
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, maxW: number, lineH: number, maxLines = 4) {
  let line = ''
  let lineCount = 0
  for (let i = 0; i < text.length; i++) {
    const testLine = line + text[i]
    if (ctx.measureText(testLine).width > maxW) {
      lineCount++
      if (lineCount >= maxLines) {
        ctx.fillText(line.slice(0, -1) + '...', x, y)
        return y + lineH
      }
      ctx.fillText(line, x, y)
      line = text[i]!
      y += lineH
    } else {
      line = testLine
    }
  }
  ctx.fillText(line, x, y)
  return y + lineH
}

function downloadPoster() {
  const canvas = posterCanvas.value
  if (!canvas) return
  const link = document.createElement('a')
  const filePrefix = buildCampaignName()
  link.download = `${filePrefix}-share-${Date.now()}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
  tip.value = '海报已保存'
  setTimeout(() => { tip.value = '' }, 2000)
}

function copyLink() {
  const utmUrl = buildPosterShareUrl() || props.url?.trim() || ''
  if (!utmUrl) {
    tip.value = '当前链接不可用'
    setTimeout(() => { tip.value = '' }, 2000)
    return
  }

  navigator.clipboard.writeText(utmUrl).then(() => {
    tip.value = '链接已复制（含UTM追踪参数）'
    setTimeout(() => { tip.value = '' }, 2000)
  }).catch(() => {
    tip.value = '复制失败，请手动复制地址栏链接'
    setTimeout(() => { tip.value = '' }, 2000)
  })
}
</script>

<style scoped lang="scss">
.poster-overlay {
  position: fixed; inset: 0; z-index: var(--z-modal); background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center; padding: 1rem;

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}
.poster-modal {
  background: $color-bg; border-radius: 12px; padding: 1.5rem; max-width: 420px; width: 100%;
  border: 1px solid transparent;
  max-height: calc(100vh - 2rem);
  overflow-y: auto;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  .dark & {
    background: $color-dark-bg-secondary;
    border-color: rgba(148, 163, 184, 0.14);
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
  }
}
.poster-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem;
  h3 { font-size: 1.1rem; font-weight: 600; color: $color-text; .dark & { color: $color-dark-text; } }
  .close-btn { border: none; background: none; color: $color-text-muted; cursor: pointer; padding: 0.25rem; }
}
.poster-body { display: flex; justify-content: center; margin-bottom: 1rem; }
.poster-canvas {
  border-radius: $radius-md;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  max-width: 100%;
  height: auto;
  display: block;
}
.poster-actions { display: flex; gap: 0.75rem; }
.btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 0.375rem;
  padding: 0.5rem; border: none; border-radius: $radius-md; font-size: 0.85rem; font-weight: 500;
  cursor: pointer; min-height: 44px; transition: background 0.2s;
}
.btn-primary { background: $color-primary; color: #fff; &:hover { background: $color-primary-dark; } }
.btn-secondary {
  background: transparent; border: 1px solid $color-border; color: $color-text;
  &:hover { border-color: $color-primary; color: $color-primary; }
  .dark & { border-color: $color-dark-border; color: $color-dark-text; }
}
.tip { text-align: center; margin-top: 0.75rem; font-size: 0.8rem; color: #22c55e; }

.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .poster-modal {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .poster-modal {
    transform: translateY(20px) scale(0.96);
  }
}

@media (max-width: $breakpoint-md) {
  .poster-overlay {
    align-items: flex-end;
    padding: 0.5rem 0.5rem 0;
  }

  .poster-modal {
    max-width: 100%;
    max-height: calc(100vh - 0.5rem);
    border-radius: 16px 16px 0 0;
    padding: 0.95rem 0.9rem calc(0.95rem + env(safe-area-inset-bottom));
  }

  .poster-header {
    margin-bottom: 0.75rem;
    h3 { font-size: 1rem; }
  }

  .poster-body {
    margin-bottom: 0.75rem;
  }

  .poster-actions {
    flex-direction: column;
    gap: 0.5rem;
  }

  .btn {
    font-size: 0.9rem;
    min-height: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .modal-fade-enter-active,
  .modal-fade-leave-active,
  .modal-fade-appear-active,
  .modal-fade-enter-active .poster-modal,
  .modal-fade-leave-active .poster-modal,
  .modal-fade-appear-active .poster-modal {
    transition: none;
  }
}
</style>
