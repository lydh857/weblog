interface FloatingPageIndicatorState {
  enabled: boolean
  currentPage: number
  totalPages: number
}

export function useFloatingPageIndicator() {
  const state = useState<FloatingPageIndicatorState>('floating-page-indicator', () => ({
    enabled: false,
    currentPage: 1,
    totalPages: 1,
  }))

  function setIndicator(payload: { enabled: boolean; currentPage: number; totalPages: number }) {
    const nextEnabled = payload.enabled
    const nextCurrentPage = Math.max(1, Math.trunc(payload.currentPage) || 1)
    const nextTotalPages = Math.max(1, Math.trunc(payload.totalPages) || 1)

    const current = state.value
    if (
      current.enabled === nextEnabled
      && current.currentPage === nextCurrentPage
      && current.totalPages === nextTotalPages
    ) {
      return
    }

    state.value = {
      enabled: nextEnabled,
      currentPage: nextCurrentPage,
      totalPages: nextTotalPages,
    }
  }

  function clearIndicator() {
    if (!state.value.enabled && state.value.currentPage === 1 && state.value.totalPages === 1) {
      return
    }
    state.value = {
      enabled: false,
      currentPage: 1,
      totalPages: 1,
    }
  }

  return {
    state,
    setIndicator,
    clearIndicator,
  }
}
