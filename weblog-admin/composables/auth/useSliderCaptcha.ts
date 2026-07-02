/**
 * 滑块验证码状态管理
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
    const callback = onSuccessCallback
    close()
    callback?.(token)
  }

  return { visible, scene, open, close, handleSuccess }
}
