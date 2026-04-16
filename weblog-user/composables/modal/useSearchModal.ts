import { ref } from 'vue'

/** SearchModal 全局显示状态 */
const isVisible = ref(false)
const initialKeyword = ref('')
const inputPlaceholder = ref('搜索文章...')
const autoSearchOnOpen = ref(false)

export function normalizeSearchSeed(value?: string): string {
  if (!value) return ''
  return value
    .replace(/\u00A0/g, ' ')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .replace(/\p{Extended_Pictographic}/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
}

interface SearchModalOpenOptions {
  keyword?: string
  placeholder?: string
  autoSearch?: boolean
}

export function useSearchModal() {
  function open(options?: SearchModalOpenOptions) {
    initialKeyword.value = normalizeSearchSeed(options?.keyword)
    inputPlaceholder.value = normalizeSearchSeed(options?.placeholder) || '搜索文章...'
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
