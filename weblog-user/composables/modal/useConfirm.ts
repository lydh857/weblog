import { reactive, ref } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'info' | 'warning' | 'danger'
}

// 全局单例状态
const visible = ref(false)
const options = reactive<ConfirmOptions>({ message: '' })
let resolvePromise: ((val: boolean) => void) | null = null

function confirm(val: boolean) {
  visible.value = false
  resolvePromise?.(val)
  resolvePromise = null
}

export function useConfirmStore() {
  return {
    visible,
    options,
    confirm: () => confirm(true),
    cancel: () => confirm(false),
  }
}

export function useConfirm() {
  return {
    confirm: (opts: ConfirmOptions): Promise<boolean> => {
      if (import.meta.server) return Promise.resolve(false)
      Object.assign(options, opts)
      visible.value = true
      return new Promise<boolean>((resolve) => { resolvePromise = resolve })
    },
    danger: (message: string, title = '确认操作'): Promise<boolean> => {
      if (import.meta.server) return Promise.resolve(false)
      Object.assign(options, { message, title, type: 'danger' as const, confirmText: '确定', cancelText: '取消' })
      visible.value = true
      return new Promise<boolean>((resolve) => { resolvePromise = resolve })
    },
  }
}
