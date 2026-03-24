import { ref } from 'vue'

/** SearchModal 全局显示状态 */
const isVisible = ref(false)
const initialKeyword = ref('')
const inputPlaceholder = ref('搜索文章...')
const autoSearchOnOpen = ref(false)

interface SearchModalOpenOptions {
  keyword?: string
  placeholder?: string
  autoSearch?: boolean
}

export function useSearchModal() {
  function open(options?: SearchModalOpenOptions) {
    initialKeyword.value = options?.keyword?.trim() || ''
    inputPlaceholder.value = options?.placeholder?.trim() || '搜索文章...'
    autoSearchOnOpen.value = Boolean(options?.autoSearch)
    isVisible.value = true
  }

  function close() {
    isVisible.value = false
    autoSearchOnOpen.value = false
  }

  return {
    isVisible,
    initialKeyword,
    inputPlaceholder,
    autoSearchOnOpen,
    open,
    close,
  }
}
