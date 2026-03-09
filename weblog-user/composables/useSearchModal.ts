import { ref } from 'vue'

/** SearchModal 全局显示状态 */
const isVisible = ref(false)

export function useSearchModal() {
  return {
    isVisible,
    open: () => { isVisible.value = true },
    close: () => { isVisible.value = false },
  }
}
