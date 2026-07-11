interface ErrorLike {
  message?: string
  code?: number | string
}

function isErrorLike(error: unknown): error is ErrorLike {
  return typeof error === 'object' && error !== null
}

export function getErrorMessage(error: unknown, fallback: string): string {
  if (isErrorLike(error) && typeof error.message === 'string' && error.message.trim()) {
    return error.message
  }
  return fallback
}

export function getErrorCode(error: unknown): number | string | null {
  if (isErrorLike(error) && (typeof error.code === 'number' || typeof error.code === 'string')) {
    return error.code
  }
  return null
}
