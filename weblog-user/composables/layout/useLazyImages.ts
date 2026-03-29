/**
 * 全局图片懒加载淡入效果
 * 通过 JS 直接操作 style，避免 scoped CSS 优先级问题
 */
export function useLazyImages() {
  if (import.meta.server) return

  function initImage(img: HTMLImageElement) {
    // 跳过已处理的图片
    if (img.dataset.lazyInit) return
    img.dataset.lazyInit = '1'

    // 已缓存的图片直接显示，不做动画
    if (img.complete && img.naturalWidth > 0) return

    // 设置初始透明 + 模糊
    img.style.opacity = '0'
    img.style.filter = 'blur(4px)'
    img.style.transition = 'opacity 0.45s ease, filter 0.45s ease'

    function reveal() {
      img.style.opacity = '1'
      img.style.filter = 'blur(0)'
      // 过渡结束后清理内联样式
      img.addEventListener('transitionend', () => {
        img.style.removeProperty('opacity')
        img.style.removeProperty('filter')
        img.style.removeProperty('transition')
      }, { once: true })
    }

    img.addEventListener('load', reveal, { once: true })
    img.addEventListener('error', reveal, { once: true })
  }

  function scanImages() {
    document.querySelectorAll<HTMLImageElement>('img[loading="lazy"]').forEach(initImage)
  }

  onMounted(() => {
    scanImages()

    const pendingNodes = new Set<Element>()
    let flushRafId: number | null = null

    function flushPendingNodes() {
      flushRafId = null
      if (pendingNodes.size === 0) return

      pendingNodes.forEach((node) => {
        if (node instanceof HTMLImageElement) {
          if (node.loading === 'lazy') {
            initImage(node)
          }
          return
        }

        node.querySelectorAll<HTMLImageElement>('img[loading="lazy"]').forEach(initImage)
      })

      pendingNodes.clear()
    }

    function scheduleFlushPendingNodes() {
      if (flushRafId !== null) return
      flushRafId = window.requestAnimationFrame(flushPendingNodes)
    }

    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node instanceof HTMLImageElement && node.loading === 'lazy') {
            pendingNodes.add(node)
          } else if (node instanceof Element) {
            pendingNodes.add(node)
          }
        }
      }

      if (pendingNodes.size > 0) {
        scheduleFlushPendingNodes()
      }
    })

    observer.observe(document.body, { childList: true, subtree: true })
    onUnmounted(() => {
      observer.disconnect()
      if (flushRafId !== null) {
        window.cancelAnimationFrame(flushRafId)
        flushRafId = null
      }
      pendingNodes.clear()
    })
  })
}
