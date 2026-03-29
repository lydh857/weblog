import { reactive } from 'vue'

export type MessageType = 'info' | 'success' | 'warning' | 'error'

export interface MessageInstance {
  id: string
  type: MessageType
  content: string
  duration: number
}

// 全局响应式消息队列（单例）
const messages = reactive<MessageInstance[]>([])
const timers = new Map<string, ReturnType<typeof setTimeout>>()
let msgId = 0

function add(type: MessageType, content: string, duration = 2500) {
  // SSR 环境不显示消息
  if (import.meta.server) return

  const id = `msg_${++msgId}_${Date.now()}`
  const msg: MessageInstance = { id, type, content, duration }

  // 最多同时 5 条
  if (messages.length >= 5) {
    remove(messages[0]!.id)
  }

  messages.push(msg)
  const timer = setTimeout(() => remove(id), duration)
  timers.set(id, timer)
}

function remove(id: string) {
  const timer = timers.get(id)
  if (timer) { clearTimeout(timer); timers.delete(id) }
  const idx = messages.findIndex(m => m.id === id)
  if (idx !== -1) messages.splice(idx, 1)
}

export function useMessageStore() {
  return { messages, remove }
}

export function useMessage() {
  return {
    info: (content: string, duration?: number) => add('info', content, duration),
    success: (content: string, duration?: number) => add('success', content, duration),
    warning: (content: string, duration?: number) => add('warning', content, duration),
    error: (content: string, duration?: number) => add('error', content, duration),
  }
}
