export interface ImageWatermarkConfig {
  enabled: boolean
  targets: Array<'cover' | 'content'>
  text: string
  mode: 'single' | 'tile'
  position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right' | 'center'
  opacity: number
  fontSize: number
  angle: number
  spacingX: number
  spacingY: number
  fontWeight: number
  color: string
}

export function shouldApplyWatermark(config: ImageWatermarkConfig, target: 'cover' | 'content'): boolean {
  return config.enabled && config.targets.includes(target) && config.text.trim().length > 0
}

export async function applyWatermarkToBlob(blob: Blob, config: ImageWatermarkConfig): Promise<Blob> {
  const objectUrl = URL.createObjectURL(blob)
  try {
    const img = await loadImage(objectUrl)
    const canvas = document.createElement('canvas')
    canvas.width = img.naturalWidth
    canvas.height = img.naturalHeight
    const ctx = canvas.getContext('2d')
    if (!ctx) return blob
    ctx.drawImage(img, 0, 0)
    drawWatermark(ctx, canvas.width, canvas.height, config)
    const outputType = blob.type || 'image/webp'
    return await canvasToBlob(canvas, outputType)
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}

export async function applyWatermarkToFile(file: File, config: ImageWatermarkConfig): Promise<File> {
  const blob = await applyWatermarkToBlob(file, config)
  return new File([blob], file.name, { type: blob.type || file.type })
}

function drawWatermark(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  config: ImageWatermarkConfig,
) {
  const safeWidth = Math.max(width, 1)
  const safeHeight = Math.max(height, 1)
  const padding = Math.max(16, Math.round(Math.min(safeWidth, safeHeight) * 0.04))
  const fontSize = Math.max(12, Math.round(config.fontSize * Math.min(safeWidth, safeHeight) / 720))
  ctx.save()
  ctx.globalAlpha = Math.min(Math.max(config.opacity, 0.1), 1)
  ctx.fillStyle = config.color || '#ffffff'
  ctx.font = `${config.fontWeight || 600} ${fontSize}px sans-serif`
  ctx.textBaseline = 'middle'
  const text = config.text.trim()
  const angleRad = (config.angle || 0) * Math.PI / 180

  if (config.mode === 'tile') {
    drawTiledWatermark(ctx, safeWidth, safeHeight, text, fontSize, angleRad, config)
    ctx.restore()
    return
  }

  const metrics = ctx.measureText(text)
  const textWidth = metrics.width
  const textHeight = fontSize

  let x = padding
  let y = padding + textHeight / 2

  switch (config.position) {
    case 'top-right':
      x = safeWidth - textWidth - padding
      y = padding + textHeight / 2
      break
    case 'bottom-left':
      x = padding
      y = safeHeight - padding - textHeight / 2
      break
    case 'bottom-right':
      x = safeWidth - textWidth - padding
      y = safeHeight - padding - textHeight / 2
      break
    case 'center':
      x = (safeWidth - textWidth) / 2
      y = safeHeight / 2
      break
    default:
      break
  }

  drawSingleWatermark(ctx, text, x, y, fontSize, angleRad)
  ctx.restore()
}

function drawSingleWatermark(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  fontSize: number,
  angleRad: number,
) {
  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(angleRad)
  ctx.textAlign = 'left'
  ctx.strokeStyle = 'rgba(0, 0, 0, 0.35)'
  ctx.lineWidth = Math.max(2, Math.round(fontSize * 0.12))
  ctx.strokeText(text, 0, 0)
  ctx.fillText(text, 0, 0)
  ctx.restore()
}

function drawTiledWatermark(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  text: string,
  fontSize: number,
  angleRad: number,
  config: ImageWatermarkConfig,
) {
  const metrics = ctx.measureText(text)
  const textWidth = metrics.width
  const textHeight = fontSize
  const spacingX = Math.max(40, config.spacingX)
  const spacingY = Math.max(30, config.spacingY)
  const stepX = textWidth + spacingX
  const stepY = textHeight + spacingY

  const startX = -textWidth
  const endX = width + textWidth
  const startY = -textHeight
  const endY = height + textHeight

  for (let y = startY; y <= endY; y += stepY) {
    for (let x = startX; x <= endX; x += stepX) {
      drawSingleWatermark(ctx, text, x, y, fontSize, angleRad)
    }
  }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片加载失败'))
    img.src = src
  })
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('水印生成失败'))
        return
      }
      resolve(blob)
    }, type, 0.92)
  })
}
