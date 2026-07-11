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
  data: object,
  options?: { signal?: AbortSignal },
): SseCallbacks {
  let messageCb: ((text: string) => void) | null = null
  let errorCb: ((err: Error) => void) | null = null
  let doneCb: (() => void) | null = null

  const controller = new AbortController()
  const signal = options?.signal
    ? combineSignals(options.signal, controller.signal)
    : controller.signal

  // 异步启动 SSE 读取
  startSse(url, data, signal).catch(() => {})

  async function startSse(
    sseUrl: string,
    body: object,
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
        } catch {
          // 非 JSON 响应
        }
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
      // SSE 规范：同一事件中多个 data: 行用 \n 连接
      let dataLines: string[] = []

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        // 保留最后一个不完整的行
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          const trimmed = line.trimEnd()
          // 空行表示事件结束，派发已收集的 data
          if (trimmed === '') {
            if (dataLines.length > 0) {
              const result = dispatchSseEvent(dataLines, messageCb, errorCb, doneCb)
              dataLines = []
              if (result === 'done' || result === 'error') return
            }
            continue
          }
          if (trimmed.startsWith('data:')) {
            dataLines.push(trimmed.slice(5).trimStart())
          }
        }
      }

      // 处理 buffer 中剩余数据
      if (buffer.trim()) {
        const trimmed = buffer.trim()
        if (trimmed.startsWith('data:')) {
          dataLines.push(trimmed.slice(5).trimStart())
        }
      }
      if (dataLines.length > 0) {
        const result = dispatchSseEvent(dataLines, messageCb, errorCb, doneCb)
        if (result === 'done' || result === 'error') return
      }

      doneCb?.()
    } catch (err) {
      if ((err as DOMException).name === 'AbortError') return
      errorCb?.(err instanceof Error ? err : new Error(String(err)))
    }
  }

  const callbacks: SseCallbacks = {
    onMessage(cb) {
      messageCb = cb
      return callbacks
    },
    onError(cb) {
      errorCb = cb
      return callbacks
    },
    onDone(cb) {
      doneCb = cb
      return callbacks
    },
    abort() {
      controller.abort()
    },
  }

  return callbacks
}

/**
 * 派发一个 SSE 事件：将多个 data: 行合并为完整内容
 * 返回 'done' | 'error' | 'message' 表示事件类型
 */
function dispatchSseEvent(
  dataLines: string[],
  messageCb: ((text: string) => void) | null,
  errorCb: ((err: Error) => void) | null,
  doneCb: (() => void) | null,
): 'done' | 'error' | 'message' {
  const content = dataLines.join('\n')
  if (content === '[DONE]') {
    doneCb?.()
    return 'done'
  }
  if (content.startsWith('[ERROR]')) {
    errorCb?.(new Error(content.slice(7).trim() || 'SSE 服务端错误'))
    return 'error'
  }
  if (content.startsWith('[TIMEOUT]')) {
    errorCb?.(new Error(content.slice(9).trim() || 'AI 响应超时，请稍后重试'))
    return 'error'
  }
  messageCb?.(content)
  return 'message'
}

/**
 * 合并两个 AbortSignal
 */
function combineSignals(s1: AbortSignal, s2: AbortSignal): AbortSignal {
  // 优先使用原生 AbortSignal.any（现代浏览器支持）
  if ('any' in AbortSignal) {
    return AbortSignal.any([s1, s2])
  }
  // 降级方案：手动合并，确保清理监听器
  const controller = new AbortController()
  const onAbort = () => {
    controller.abort()
    s1.removeEventListener('abort', onAbort)
    s2.removeEventListener('abort', onAbort)
  }
  s1.addEventListener('abort', onAbort)
  s2.addEventListener('abort', onAbort)
  if (s1.aborted || s2.aborted) {
    controller.abort()
    s1.removeEventListener('abort', onAbort)
    s2.removeEventListener('abort', onAbort)
  }
  return controller.signal
}

/**
 * 解析 SSE 数据行（用于测试）
 */
export function parseSseLines(raw: string): string[] {
  const results: string[] = []
  const lines = raw.split('\n')
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('data:')) {
      const content = trimmed.slice(5).trimStart()
      if (content !== '[DONE]' && !content.startsWith('[ERROR]')) {
        results.push(content)
      }
    }
  }
  return results
}
