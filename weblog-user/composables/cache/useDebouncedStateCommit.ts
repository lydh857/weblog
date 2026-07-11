type CommitKey = string | number

interface QueueEntry<TState> {
  desiredState: TState
  timer: ReturnType<typeof setTimeout> | null
  inflight: boolean
  queued: boolean
}

interface DebouncedStateCommitOptions<TState, TResult> {
  delayMs?: number
  flushOnPageHide?: boolean
  commitState: (key: CommitKey, state: TState) => Promise<TResult>
  onSuccess?: (key: CommitKey, state: TState, result: TResult) => void
  onError?: (key: CommitKey, state: TState, error: unknown) => void
}

export function useDebouncedStateCommit<TState, TResult>(
  options: DebouncedStateCommitOptions<TState, TResult>,
) {
  const delayMs = Math.max(100, options.delayMs ?? 600)
  const queueMap = new Map<CommitKey, QueueEntry<TState>>()
  let disposed = false

  function getOrCreateEntry(key: CommitKey, state: TState) {
    const existing = queueMap.get(key)
    if (existing) {
      return existing
    }

    const entry: QueueEntry<TState> = {
      desiredState: state,
      timer: null,
      inflight: false,
      queued: false,
    }
    queueMap.set(key, entry)
    return entry
  }

  function clearTimer(entry: QueueEntry<TState>) {
    if (!entry.timer) {
      return
    }
    clearTimeout(entry.timer)
    entry.timer = null
  }

  function scheduleTimer(key: CommitKey, entry: QueueEntry<TState>) {
    clearTimer(entry)
    entry.timer = setTimeout(() => {
      void commitKey(key)
    }, delayMs)
  }

  async function commitKey(key: CommitKey) {
    const entry = queueMap.get(key)
    if (!entry || disposed) {
      return
    }

    if (entry.inflight) {
      entry.queued = true
      return
    }

    clearTimer(entry)
    entry.inflight = true
    entry.queued = false
    const stateToCommit = entry.desiredState

    try {
      const result = await options.commitState(key, stateToCommit)
      if (!disposed) {
        options.onSuccess?.(key, stateToCommit, result)
      }
    } catch (error) {
      if (!disposed) {
        options.onError?.(key, stateToCommit, error)
      }
    } finally {
      entry.inflight = false
      if (disposed) {
        queueMap.delete(key)
        return
      }

      const latestEntry = queueMap.get(key)
      if (!latestEntry) {
        return
      }

      if (latestEntry.queued || !Object.is(latestEntry.desiredState, stateToCommit)) {
        latestEntry.queued = false
        scheduleTimer(key, latestEntry)
        return
      }

      if (!latestEntry.timer) {
        queueMap.delete(key)
      }
    }
  }

  function scheduleState(key: CommitKey, state: TState) {
    if (disposed) {
      return
    }

    const entry = getOrCreateEntry(key, state)
    entry.desiredState = state

    if (entry.inflight) {
      entry.queued = true
      return
    }

    scheduleTimer(key, entry)
  }

  function flushKey(key: CommitKey) {
    const entry = queueMap.get(key)
    if (!entry || disposed) {
      return
    }

    clearTimer(entry)
    if (entry.inflight) {
      entry.queued = true
      return
    }

    void commitKey(key)
  }

  function flushAll() {
    for (const key of Array.from(queueMap.keys())) {
      flushKey(key)
    }
  }

  function isInflight(key: CommitKey) {
    return Boolean(queueMap.get(key)?.inflight)
  }

  let pageHideHandler: (() => void) | null = null
  let visibilityHandler: (() => void) | null = null

  if (import.meta.client && options.flushOnPageHide !== false) {
    pageHideHandler = () => {
      flushAll()
    }
    visibilityHandler = () => {
      if (document.visibilityState === 'hidden') {
        flushAll()
      }
    }

    window.addEventListener('pagehide', pageHideHandler)
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  function dispose() {
    if (disposed) {
      return
    }

    disposed = true
    for (const entry of queueMap.values()) {
      clearTimer(entry)
    }
    queueMap.clear()

    if (import.meta.client) {
      if (pageHideHandler) {
        window.removeEventListener('pagehide', pageHideHandler)
      }
      if (visibilityHandler) {
        document.removeEventListener('visibilitychange', visibilityHandler)
      }
    }
  }

  onBeforeUnmount(() => {
    flushAll()
    dispose()
  })

  return {
    scheduleState,
    flushKey,
    flushAll,
    isInflight,
    dispose,
  }
}
