// 临时锁定导航栏滚动检测，防止评论区 DOM 变化触发导航栏显示/隐藏
const locked = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

export function useNavScrollLock() {
  // 锁定滚动检测，duration 毫秒后自动解锁
  function lock(duration = 300) {
    locked.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => { locked.value = false }, duration)
  }

  return { locked: readonly(locked), lock }
}
