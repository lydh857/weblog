// 注意：Token 现在通过 HttpOnly Cookie 管理，浏览器会自动在请求中发送
// 不需要手动设置 Authorization 请求头

function getBaseURL(): string {
  try {
    const config = useRuntimeConfig()
    return config.public.apiBase as string
  } catch {
    return 'http://localhost:9091/api'
  }
}

interface SseCallbacks {
  onMessage: (cb: (text: string) => void) => SseCallbacks
  onError: (cb: (err: Error) => void) => SseCallbacks
  onDone: (cb: () => void) => SseCallbacks
  abort: () => void
}

/**
 * 通过 POST 请求建立 SSE 连接
 * 使用 fetch + ReadableStream 实现，支持 AbortController 取消
 */
export function ssePost(
  url: string,
  data: Record<string, unknown>,
  options?: { signal?: AbortSignal },
): SseCallbacks {
  let messageCb: ((text: string) => void) | null = null
  let errorCb: ((err: Error) => void) | null = null
  let doneCb: (() => void) | null = null

  const controller = new AbortController()
  const signal = options?.signal
    ? combineSignals(options.signal, controller.signal)
    : controller.signal

  startSse(url, data, signal).catch(() => {})

  async function startSse(
    sseUrl: string,
    body: Record<string, unknown>,
    abortSignal: AbortSignal,
  ) {
    try {
      const baseUrl = getBaseURL()
      const fullUrl = sseUrl.startsWith('http') ? sseUrl : `${baseUrl}${sseUrl}`

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      }

      // 注意：Token 现在通过 HttpOnly Cookie 传输，浏览器会自动发送
      // 不需要手动设置 Authorization 请求头

      const response = await fetch(fullUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: abortSignal,
        credentials: 'include', // 关键：发送 Cookie
      })

      if (!response.ok) {
        const text = await response.text().catch(() => '')
        let errMsg = `SSE 请求失败: ${response.status}`
        try {
          const json = JSON.parse(text)
          if (json.message) errMsg = json.message
        } catch { /* 非 JSON 响应 */ }
        errorCb?.(new Error(errMsg))
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        errorCb?.(new Error('响应体不可读'))
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed === '') continue
          if (trimmed.startsWith('data:')) {
            const content = trimmed.slice(5).trimStart()
            if (content === '[DONE]') {
              doneCb?.()
              return
            }
            messageCb?.(content)
          }
        }
      }

      if (buffer.trim()) {
        const trimmed = buffer.trim()
        if (trimmed.startsWith('data:')) {
          const content = trimmed.slice(5).trimStart()
          if (content !== '[DONE]') {
            messageCb?.(content)
          }
        }
      }

      doneCb?.()
    } catch (err) {
      if ((err as DOMException).name === 'AbortError') return
      errorCb?.(err instanceof Error ? err : new Error(String(err)))
    }
  }

  const callbacks: SseCallbacks = {
    onMessage(cb) { messageCb = cb; return callbacks },
    onError(cb) { errorCb = cb; return callbacks },
    onDone(cb) { doneCb = cb; return callbacks },
    abort() { controller.abort() },
  }

  return callbacks
}

function combineSignals(s1: AbortSignal, s2: AbortSignal): AbortSignal {
  const controller = new AbortController()
  const onAbort = () => controller.abort()
  s1.addEventListener('abort', onAbort)
  s2.addEventListener('abort', onAbort)
  if (s1.aborted || s2.aborted) controller.abort()
  return controller.signal
}
