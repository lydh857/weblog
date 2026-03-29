type OpenCaptchaFn = (onSuccess: (verifyToken: string) => void) => void

interface RunCaptchaActionOptions {
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
    })
  }

  return {
    runCaptchaAction,
  }
}
