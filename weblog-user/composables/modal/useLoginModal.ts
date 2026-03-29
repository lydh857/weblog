import { ref } from 'vue'

export type LoginModalMode = 'code' | 'password' | 'register' | 'reset'

const visible = ref(false)
const initialMode = ref<LoginModalMode>('code')
let onSuccessCallback: (() => void) | null = null

export function useLoginModal() {
  function open(mode: LoginModalMode = 'code', onSuccess?: () => void) {
    initialMode.value = mode
    onSuccessCallback = onSuccess || null
    visible.value = true
  }

  function close() {
    visible.value = false
    onSuccessCallback = null
  }

  function onLoginSuccess() {
    visible.value = false
    onSuccessCallback?.()
    onSuccessCallback = null
  }

  return {
    visible,
    initialMode,
    open,
    close,
    onLoginSuccess,
  }
}
