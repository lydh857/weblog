let pendingDetection: Promise<boolean> | null = null

function waitForPromotionCheckFrame() {
  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => {
      window.setTimeout(resolve, 40)
    })
  })
}

async function runPromotionBlockDetection() {
  if (!import.meta.client) return false

  const bait = document.createElement('div')
  bait.className = 'adsbox ad adsbygoogle ad-banner advertisement pub_300x250 text-ad'
  bait.setAttribute('aria-hidden', 'true')
  bait.style.position = 'absolute'
  bait.style.left = '-10000px'
  bait.style.top = '-10000px'
  bait.style.width = '1px'
  bait.style.height = '1px'
  bait.style.pointerEvents = 'none'

  document.body.appendChild(bait)

  try {
    await waitForPromotionCheckFrame()
    const style = window.getComputedStyle(bait)
    return bait.offsetParent === null
      || bait.offsetHeight === 0
      || bait.clientHeight === 0
      || style.display === 'none'
      || style.visibility === 'hidden'
  } finally {
    bait.remove()
  }
}

export function usePromotionVisibility() {
  const promotionBlockedState = useState<boolean | null>('promotion-blocked', () => null)

  async function detectPromotionBlocked() {
    if (promotionBlockedState.value !== null) {
      return promotionBlockedState.value
    }

    if (!pendingDetection) {
      pendingDetection = runPromotionBlockDetection()
        .then((detected) => {
          promotionBlockedState.value = detected
          return detected
        })
        .finally(() => {
          pendingDetection = null
        })
    }

    return pendingDetection
  }

  return {
    isPromotionBlocked: readonly(promotionBlockedState),
    detectPromotionBlocked,
  }
}
