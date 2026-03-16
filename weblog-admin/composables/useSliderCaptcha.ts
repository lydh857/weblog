/**
 * 滑块验证码状态管理
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
    const callback = onSuccessCallback
    close()
    callback?.(token)
  }

  return { visible, open, close, handleSuccess }
}
