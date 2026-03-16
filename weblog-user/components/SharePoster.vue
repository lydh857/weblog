<template>
  <Teleport to="body">
    <div v-if="visible" class="poster-overlay" @click.self="$emit('close')">
      <div class="poster-modal">
        <div class="poster-header">
          <h3>分享海报</h3>
          <button class="close-btn" @click="$emit('close')" aria-label="关闭">
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
  if (v) nextTick(() => drawPoster())
})

function drawPoster() {
  const canvas = posterCanvas.value
  if (!canvas) return

  const dpr = window.devicePixelRatio || 1
  const w = 360
  const h = 480
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
  ctx.beginPath(); ctx.arc(300, 60, 80, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = 'rgba(59, 130, 246, 0.08)'
  ctx.beginPath(); ctx.arc(40, 400, 60, 0, Math.PI * 2); ctx.fill()

  // Logo
  ctx.fillStyle = '#3b82f6'
  ctx.font = 'bold 20px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.fillText(siteName.value, 24, 44)

  // 分割线
  ctx.strokeStyle = 'rgba(255,255,255,0.1)'
  ctx.lineWidth = 1
  ctx.beginPath(); ctx.moveTo(24, 60); ctx.lineTo(w - 24, 60); ctx.stroke()

  // 标题
  ctx.fillStyle = '#f1f5f9'
  ctx.font = 'bold 22px -apple-system, BlinkMacSystemFont, sans-serif'
  wrapText(ctx, props.title, 24, 100, w - 48, 30)

  // 摘要
  if (props.summary) {
    ctx.fillStyle = '#94a3b8'
    ctx.font = '14px -apple-system, BlinkMacSystemFont, sans-serif'
    wrapText(ctx, props.summary, 24, 200, w - 48, 20, 3)
  }

  // 作者
  ctx.fillStyle = '#64748b'
  ctx.font = '13px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.fillText(`作者: ${props.author || siteName.value}`, 24, h - 80)

  // 底部提示
  ctx.fillStyle = '#475569'
  ctx.font = '12px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.fillText('扫码或复制链接阅读全文', 24, h - 40)

  // URL
  ctx.fillStyle = '#3b82f6'
  ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif'
  const displayUrl = props.url.length > 45 ? props.url.slice(0, 45) + '...' : props.url
  ctx.fillText(displayUrl, 24, h - 20)
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
        return
      }
      ctx.fillText(line, x, y)
      line = text[i]!
      y += lineH
    } else {
      line = testLine
    }
  }
  ctx.fillText(line, x, y)
}

function downloadPoster() {
  const canvas = posterCanvas.value
  if (!canvas) return
  const link = document.createElement('a')
  const filePrefix = siteName.value.trim().replace(/\s+/g, '-').toLowerCase() || 'site'
  link.download = `${filePrefix}-share-${Date.now()}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
  tip.value = '海报已保存'
  setTimeout(() => { tip.value = '' }, 2000)
}

function copyLink() {
  const campaign = siteName.value.trim().replace(/\s+/g, '-').toLowerCase() || 'site'
  const utmUrl = `${props.url}${props.url.includes('?') ? '&' : '?'}utm_source=share&utm_medium=poster&utm_campaign=${campaign}`
  navigator.clipboard.writeText(utmUrl).then(() => {
    tip.value = '链接已复制（含UTM追踪参数）'
    setTimeout(() => { tip.value = '' }, 2000)
  })
}
</script>

<style scoped lang="scss">
.poster-overlay {
  position: fixed; inset: 0; z-index: var(--z-modal); background: rgba(0, 0, 0, 0.6);
  display: flex; align-items: center; justify-content: center; padding: 1rem;
}
.poster-modal {
  background: $color-bg; border-radius: $radius-lg; padding: 1.5rem; max-width: 420px; width: 100%;
  .dark & { background: $color-dark-bg-secondary; }
}
.poster-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem;
  h3 { font-size: 1.1rem; font-weight: 600; color: $color-text; .dark & { color: $color-dark-text; } }
  .close-btn { border: none; background: none; color: $color-text-muted; cursor: pointer; padding: 0.25rem; }
}
.poster-body { display: flex; justify-content: center; margin-bottom: 1rem; }
.poster-canvas { border-radius: $radius-md; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15); }
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
</style>
