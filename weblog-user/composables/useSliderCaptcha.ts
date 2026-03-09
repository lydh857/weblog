/**
 * 滑块验证码 composable — 管理弹出状态和成功回调
 */
export function useSliderCaptcha() {
  const visible = ref(false)
  let onSuccessCallback: ((token: string) => void) | null = null

  function open(onSuccess: (token: string) => void) {
    onSuccessCallback = onSuccess
    visible.value = true
  }

  function close() {
    visible.value = false
    onSuccessCallback = null
  }

  function handleSuccess(token: string) {
    const cb = onSuccessCallback
    close()
    cb?.(token)
  }

  return { visible, open, close, handleSuccess }
}
