import { searchApi, type SearchHit } from '~/api/content/search'
import { isSecurityGatewayBlockedError } from '~/utils/network/http'
import { normalizeSearchSeed } from '~/composables/modal/useSearchModal'

interface UseSearchModalStateOptions {
  defaultPlaceholder?: string
}

interface OpenSearchSessionOptions {
  initialKeyword?: string
  placeholder?: string
  autoSearch?: boolean
}

const HISTORY_KEY = 'weblog_search_history'
const MAX_HISTORY = 10
const MAX_KEYWORD_LENGTH = 80
const LONG_PRESS_DURATION = 420
const LONG_PRESS_MOVE_TOLERANCE = 10

function normalizeKeywordValue(value: string): string {
  if (!value) return ''
  return normalizeSearchSeed(value).slice(0, MAX_KEYWORD_LENGTH)
}

function toTrimmedOrEmpty(value: string | undefined): string {
  return normalizeSearchSeed(value)
}

export function useSearchModalState(options: UseSearchModalStateOptions = {}) {
  const defaultPlaceholder = toTrimmedOrEmpty(options.defaultPlaceholder) || '搜索文章...'

  const keyword = ref('')
  const results = ref<SearchHit[]>([])
  const searching = ref(false)
  const activeIndex = ref(-1)
  const hasSearched = ref(false)
  const searchErrorMessage = ref('')
  const inputPlaceholderText = ref(defaultPlaceholder)

  const hasKeyword = computed(() => keyword.value.trim().length > 0)
  const showShortcutHints = computed(() => hasKeyword.value && !searching.value && results.value.length > 0)
  const shouldShowEmpty = computed(() => hasKeyword.value
    && hasSearched.value
    && !searching.value
    && results.value.length === 0
    && !searchErrorMessage.value)
  const showPlaceholderFire = computed(() => !keyword.value.trim() && inputPlaceholderText.value !== defaultPlaceholder)
  const actualInputPlaceholder = computed(() => (showPlaceholderFire.value ? '' : inputPlaceholderText.value))

  let searchRequestId = 0

  function invalidateSearchRequest() {
    searchRequestId += 1
  }

  async function doSearch() {
    const requestId = ++searchRequestId
    const normalizedKeyword = normalizeKeywordValue(keyword.value)
    if (normalizedKeyword !== keyword.value) {
      keyword.value = normalizedKeyword
    }

    const kw = normalizedKeyword.trim()
    if (!kw) {
      hasSearched.value = false
      searchErrorMessage.value = ''
      results.value = []
      searching.value = false
      return
    }

    hasSearched.value = true
    searching.value = true
    try {
      const res = await searchApi.search({ keyword: kw, pageSize: 20 })
      if (requestId !== searchRequestId) {
        return
      }
      searchErrorMessage.value = ''
      results.value = res.data.hits
      activeIndex.value = results.value.length > 0 ? 0 : -1
    } catch (error) {
      if (requestId !== searchRequestId) {
        return
      }
      searchErrorMessage.value = isSecurityGatewayBlockedError(error)
        ? '搜索请求被安全网关拦截，请稍后重试'
        : '搜索失败，请稍后重试'
      results.value = []
    } finally {
      if (requestId === searchRequestId) {
        searching.value = false
      }
    }
  }

  function handleInput() {
    keyword.value = normalizeKeywordValue(keyword.value)
    activeIndex.value = -1
    hasSearched.value = false
    searchErrorMessage.value = ''
    invalidateSearchRequest()
    if (!keyword.value.trim()) {
      results.value = []
      searching.value = false
    }
  }

  function handleSearchSubmit() {
    if (!keyword.value.trim()) {
      const placeholderKeyword = normalizeKeywordValue(normalizeSearchSeed(inputPlaceholderText.value)).trim()
      if (!placeholderKeyword || placeholderKeyword === defaultPlaceholder) {
        return
      }
      keyword.value = placeholderKeyword
    }
    void doSearch()
  }

  function clearKeyword() {
    invalidateSearchRequest()
    keyword.value = ''
    hasSearched.value = false
    searchErrorMessage.value = ''
    results.value = []
    activeIndex.value = -1
    searching.value = false
  }

  function resetSearchState() {
    invalidateSearchRequest()
    keyword.value = ''
    hasSearched.value = false
    searchErrorMessage.value = ''
    results.value = []
    activeIndex.value = -1
    searching.value = false
  }

  const history = ref<string[]>([])
  const historyLongPressIndex = ref<number | null>(null)
  const suppressHistoryClick = ref(false)
  const isCoarsePointer = ref(false)

  let historyLongPressTimer: ReturnType<typeof setTimeout> | null = null
  let longPressStartX = 0
  let longPressStartY = 0

  function loadHistory() {
    try {
      const raw = localStorage.getItem(HISTORY_KEY)
      history.value = raw ? JSON.parse(raw) : []
    } catch {
      history.value = []
    }
  }

  function saveHistory() {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
    } catch {
      // localStorage 不可用时静默降级
    }
  }

  function addToHistory(kw: string) {
    const trimmed = normalizeKeywordValue(kw).trim()
    if (!trimmed) {
      return
    }
    history.value = history.value.filter(item => item !== trimmed)
    history.value.unshift(trimmed)
    if (history.value.length > MAX_HISTORY) {
      history.value = history.value.slice(0, MAX_HISTORY)
    }
    saveHistory()
  }

  function removeHistory(index: number) {
    history.value.splice(index, 1)
    if (historyLongPressIndex.value === index) {
      historyLongPressIndex.value = null
    } else if (historyLongPressIndex.value != null && historyLongPressIndex.value > index) {
      historyLongPressIndex.value -= 1
    }
    suppressHistoryClick.value = false
    saveHistory()
  }

  function clearAllHistory() {
    history.value = []
    historyLongPressIndex.value = null
    suppressHistoryClick.value = false
    saveHistory()
  }

  function clearHistoryLongPressTimer() {
    if (historyLongPressTimer) {
      clearTimeout(historyLongPressTimer)
      historyLongPressTimer = null
    }
  }

  function searchFromHistory(kw: string) {
    keyword.value = normalizeKeywordValue(kw)
    historyLongPressIndex.value = null
    suppressHistoryClick.value = false
    void doSearch()
  }

  function handleHistoryTagClick(kw: string, index: number, event: MouseEvent) {
    if (suppressHistoryClick.value) {
      suppressHistoryClick.value = false
      event.preventDefault()
      return
    }

    if (historyLongPressIndex.value != null && historyLongPressIndex.value !== index) {
      historyLongPressIndex.value = null
    }

    searchFromHistory(kw)
  }

  function handleHistoryTouchStart(event: TouchEvent, index: number) {
    if (!isCoarsePointer.value) {
      return
    }

    const touch = event.touches[0]
    if (!touch) {
      return
    }

    clearHistoryLongPressTimer()
    suppressHistoryClick.value = false
    longPressStartX = touch.clientX
    longPressStartY = touch.clientY

    historyLongPressTimer = setTimeout(() => {
      historyLongPressIndex.value = index
      suppressHistoryClick.value = true
      historyLongPressTimer = null
    }, LONG_PRESS_DURATION)
  }

  function handleHistoryTouchMove(event: TouchEvent) {
    if (!historyLongPressTimer) {
      return
    }

    const touch = event.touches[0]
    if (!touch) {
      return
    }

    const deltaX = Math.abs(touch.clientX - longPressStartX)
    const deltaY = Math.abs(touch.clientY - longPressStartY)
    if (deltaX > LONG_PRESS_MOVE_TOLERANCE || deltaY > LONG_PRESS_MOVE_TOLERANCE) {
      clearHistoryLongPressTimer()
    }
  }

  function handleHistoryTouchEnd() {
    clearHistoryLongPressTimer()
  }

  function handleHistoryTouchCancel() {
    clearHistoryLongPressTimer()
  }

  function handleSearchBodyClick(event: MouseEvent) {
    if (historyLongPressIndex.value == null) {
      return
    }

    const target = event.target as HTMLElement | null
    if (target?.closest('.history-tag')) {
      return
    }

    historyLongPressIndex.value = null
    suppressHistoryClick.value = false
  }

  function detectCoarsePointer() {
    if (typeof window !== 'undefined') {
      isCoarsePointer.value = window.matchMedia('(pointer: coarse)').matches
    }
  }

  function openSession(session: OpenSearchSessionOptions = {}) {
    loadHistory()
    keyword.value = normalizeKeywordValue(toTrimmedOrEmpty(session.initialKeyword))
    inputPlaceholderText.value = normalizeKeywordValue(toTrimmedOrEmpty(session.placeholder)) || defaultPlaceholder
    if (session.autoSearch && keyword.value.trim()) {
      void doSearch()
    }
  }

  function closeSession() {
    resetSearchState()
    inputPlaceholderText.value = defaultPlaceholder
    historyLongPressIndex.value = null
    suppressHistoryClick.value = false
    clearHistoryLongPressTimer()
  }

  function dispose() {
    clearHistoryLongPressTimer()
  }

  return {
    keyword,
    maxKeywordLength: MAX_KEYWORD_LENGTH,
    results,
    searching,
    activeIndex,
    searchErrorMessage,
    hasKeyword,
    showShortcutHints,
    hasSearched,
    shouldShowEmpty,
    inputPlaceholderText,
    showPlaceholderFire,
    actualInputPlaceholder,
    history,
    historyLongPressIndex,
    suppressHistoryClick,
    isCoarsePointer,
    invalidateSearchRequest,
    doSearch,
    handleInput,
    handleSearchSubmit,
    clearKeyword,
    addToHistory,
    removeHistory,
    clearAllHistory,
    handleHistoryTagClick,
    handleHistoryTouchStart,
    handleHistoryTouchMove,
    handleHistoryTouchEnd,
    handleHistoryTouchCancel,
    handleSearchBodyClick,
    detectCoarsePointer,
    openSession,
    closeSession,
    dispose,
  }
}
