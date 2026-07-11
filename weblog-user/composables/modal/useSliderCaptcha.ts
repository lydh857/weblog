/**
 * 滑块验证码 composable — 管理弹出状态和成功回调
 */
export function useSliderCaptcha() {
  const visible = ref(false)
  const scene = ref('default')
  let onSuccessCallback: ((token: string) => void) | null = null

  function open(onSuccess: (token: string) => void, captchaScene = 'default') {
    onSuccessCallback = onSuccess
    scene.value = captchaScene
    visible.value = true
  }

  function close() {
    visible.value = false
    scene.value = 'default'
    onSuccessCallback = null
  }

  function handleSuccess(token: string) {
    const cb = onSuccessCallback
    close()
    cb?.(token)
  }

  return { visible, scene, open, close, handleSuccess }
}
