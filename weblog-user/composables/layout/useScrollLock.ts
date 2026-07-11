let lockCount = 0
let lockScrollTop = 0
let lockBodyWidth = ''

const bodyStyleSnapshot = {
  position: '',
  top: '',
  left: '',
  right: '',
  width: '',
  overflowY: '',
}

export function lockScroll() {
  if (typeof window === 'undefined') return
  lockCount++
  if (lockCount === 1) {
    const body = document.body
    lockBodyWidth = `${document.documentElement.clientWidth}px`

    bodyStyleSnapshot.position = body.style.position
    bodyStyleSnapshot.top = body.style.top
    bodyStyleSnapshot.left = body.style.left
    bodyStyleSnapshot.right = body.style.right
    bodyStyleSnapshot.width = body.style.width
    bodyStyleSnapshot.overflowY = body.style.overflowY

    lockScrollTop = window.scrollY || window.pageYOffset || 0

    body.style.position = 'fixed'
    body.style.top = `-${lockScrollTop}px`
    body.style.left = '0'
    body.style.right = '0'
    body.style.width = lockBodyWidth
    body.style.overflowY = 'hidden'
  }
}

export function unlockScroll() {
  if (typeof window === 'undefined') return
  lockCount = Math.max(0, lockCount - 1)
  if (lockCount === 0) {
    const body = document.body

    body.style.position = bodyStyleSnapshot.position
    body.style.top = bodyStyleSnapshot.top
    body.style.left = bodyStyleSnapshot.left
    body.style.right = bodyStyleSnapshot.right
    body.style.width = bodyStyleSnapshot.width
    body.style.overflowY = bodyStyleSnapshot.overflowY

    window.scrollTo(0, lockScrollTop)
  }
}
