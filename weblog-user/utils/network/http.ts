import axios from 'axios'
import type { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useLoginModal } from '~/composables/modal/useLoginModal'

// ============================================================
// 安全说明：
// CSRF 采用 HttpOnly + SameSite Cookie，前端不读取 token
// Cookie 会随请求自动发送，后端从 Cookie 中验证
// ============================================================

// 防止刷新死循环
let isRefreshing = false
type RetryRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

interface HttpError extends Error {
  code?: number
  isSecurityGatewayBlocked?: boolean
}

interface RefreshSubscriber {
  onSuccess: () => void
  onError: (error: HttpError) => void
}

let refreshSubscribers: RefreshSubscriber[] = []

function subscribeTokenRefresh(onSuccess: () => void, onError: (error: HttpError) => void) {
  refreshSubscribers.push({ onSuccess, onError })
}

function onTokenRefreshed() {
  refreshSubscribers.forEach(subscriber => subscriber.onSuccess())
  refreshSubscribers = []
}

function onTokenRefreshFailed(error: HttpError) {
  refreshSubscribers.forEach(subscriber => subscriber.onError(error))
  refreshSubscribers = []
}

function createHttpError(message: string, code?: number, isSecurityGatewayBlocked = false): HttpError {
  const error = new Error(message) as HttpError
  error.code = code
  error.isSecurityGatewayBlocked = isSecurityGatewayBlocked
  return error
}

function getHeaderValue(headers: unknown, key: string): string {
  if (!headers || typeof headers !== 'object') {
    return ''
  }
  const value = (headers as Record<string, unknown>)[key]
  return typeof value === 'string' ? value : ''
}

function isCloudflareChallengeError(error: AxiosError<{ code?: number; message?: string }>): boolean {
  const response = error.response
  if (!response) {
    return false
  }

  const cfMitigated = getHeaderValue(response.headers, 'cf-mitigated').toLowerCase()
  if (cfMitigated === 'challenge') {
    return true
  }

  const contentType = getHeaderValue(response.headers, 'content-type').toLowerCase()
  if (!contentType.includes('text/html')) {
    return false
  }

  const body = response.data
  if (typeof body !== 'string') {
    return false
  }

  const lowerBody = body.toLowerCase()
  return lowerBody.includes('just a moment')
    || lowerBody.includes('cf_chl_opt')
    || lowerBody.includes('/cdn-cgi/challenge-platform/')
}

function getBaseURL(): string {
  try {
    const config = useRuntimeConfig()
    if (import.meta.server) {
      return (config.apiInternalBase as string) || (config.public.apiBase as string)
    }
    return config.public.apiBase as string
  } catch {
    return 'http://localhost:9091/api'
  }
}

function createHttp(): AxiosInstance {
  const instance = axios.create({
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
    withCredentials: true,
  })

  // 请求拦截器
  instance.interceptors.request.use((reqConfig: InternalAxiosRequestConfig) => {
    if (!reqConfig.baseURL) {
      reqConfig.baseURL = getBaseURL()
    }

    return reqConfig
  })

  // 响应拦截器
  instance.interceptors.response.use(
    // 成功响应
    (response: AxiosResponse) => {

      const data = response.data
      if (data.code !== 200) {
        if (data.code === 401 && import.meta.client) {
          removeToken()
          try { useLoginModal().open() } catch {}
        }
        return Promise.reject(createHttpError(data.message || '请求失败', data.code))
      }
      return data
    },
    // 错误响应
    async (error: AxiosError<{ code?: number; message?: string }>) => {
      const originalRequest = error.config as RetryRequestConfig | undefined
      const responseStatus = Number(error.response?.status || 0)
      const businessCode = Number(error.response?.data?.code || 0)
      const businessMessage = error.response?.data?.message

      if (isCloudflareChallengeError(error)) {
        return Promise.reject(createHttpError(
          '请求被安全网关拦截，请稍后重试',
          40390,
          true
        ))
      }

      if (businessCode === 40103 || businessCode === 403 || businessCode === 429 || responseStatus === 429) {
        return Promise.reject(createHttpError(
          businessMessage || (responseStatus === 429 ? '访问受限，请稍后再试' : '请求失败'),
          businessCode || responseStatus
        ))
      }

      // 401 未授权：尝试使用 Refresh Token 刷新
      if (error.response?.status === 401 && import.meta.client && originalRequest && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            subscribeTokenRefresh(() => {
              originalRequest._retry = true
              resolve(instance(originalRequest))
            }, (refreshError) => {
              reject(refreshError)
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const refreshRes = await axios.post<{ code?: number; message?: string }>(
            `${getBaseURL()}/portal/auth/refresh`,
            {},
            { withCredentials: true }
          )

          if (refreshRes.data?.code !== 200) {
            throw createHttpError(refreshRes.data?.message || '登录已过期，请重新登录', 401)
          }

          onTokenRefreshed()

          return instance(originalRequest)
        } catch {
          const refreshError = createHttpError('登录已过期，请重新登录', 401)
          onTokenRefreshFailed(refreshError)
          removeToken()
          try { useLoginModal().open() } catch {}

          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      if (error.response?.status === 403) {
        return Promise.reject(createHttpError(businessMessage || '无权限访问', 403))
      }

      // 其他错误
      const serverMsg = error.response?.data?.message
      return Promise.reject(createHttpError(
        serverMsg || '网络异常，请稍后重试',
        error.response?.data?.code || error.response?.status
      ))
    },
  )

  return instance
}

export function isSecurityGatewayBlockedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const candidate = error as HttpError
  return candidate.isSecurityGatewayBlocked === true || candidate.code === 40390
}

export const http = createHttp()

// 登录状态管理
const LOGGED_IN_COOKIE = 'weblog_logged_in'
const AVATAR_COOKIE = 'weblog_avatar'
const NICKNAME_COOKIE = 'weblog_nickname'

function setCookie(name: string, value: string, maxAge = 60 * 60 * 24 * 30) {
  const isProd = import.meta.env.PROD
  const options = [
    `path=/`,
    `max-age=${maxAge}`,
    isProd ? 'SameSite=Strict' : 'SameSite=Lax',
    isProd ? 'Secure' : ''
  ].filter(Boolean).join('; ')
  document.cookie = `${name}=${encodeURIComponent(value)}; ${options}`
}

function removeCookie(name: string) {
  const isProd = import.meta.env.PROD
  const options = [
    `path=/`,
    `max-age=0`,
    isProd ? 'SameSite=Strict' : 'SameSite=Lax',
    isProd ? 'Secure' : ''
  ].filter(Boolean).join('; ')
  document.cookie = `${name}=; ${options}`
}

export function setToken(_token: string) {
  setCookie(LOGGED_IN_COOKIE, '1')
}

export function getToken(): string | null {
  if (typeof document === 'undefined') return null
  const matches = document.cookie.match(new RegExp('(^| )' + LOGGED_IN_COOKIE + '=([^;]+)'))
  if (matches && matches[2] === '1') {
    return 'logged_in'
  }
  return null
}

export function removeToken() {
  removeCookie(LOGGED_IN_COOKIE)
  removeCookie(AVATAR_COOKIE)
  removeCookie(NICKNAME_COOKIE)
}

export function syncUserCookie(avatar: string, nickname: string) {
  if (typeof document === 'undefined') return
  setCookie(AVATAR_COOKIE, avatar || '')
  setCookie(NICKNAME_COOKIE, nickname || '')
}
