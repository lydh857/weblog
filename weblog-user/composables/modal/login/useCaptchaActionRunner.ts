type OpenCaptchaFn = (onSuccess: (verifyToken: string) => void, scene?: string) => void

interface RunCaptchaActionOptions {
  scene: string
  onStart?: () => void
  onFinally?: () => void
  action: (verifyToken: string) => Promise<void>
}

export function useCaptchaActionRunner(openCaptcha: OpenCaptchaFn) {
  function runCaptchaAction(options: RunCaptchaActionOptions) {
    openCaptcha(async (verifyToken: string) => {
      options.onStart?.()
      try {
        await options.action(verifyToken)
      } finally {
        options.onFinally?.()
      }
    }, options.scene)
  }

  return {
    runCaptchaAction,
  }
}
