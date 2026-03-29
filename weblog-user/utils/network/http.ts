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

function createHttpError(message: string, code?: number): HttpError {
  const error = new Error(message) as HttpError
  error.code = code
  return error
}

function getBaseURL(): string {
  try {
    const config = useRuntimeConfig()
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

      // CSRF 验证失败
      if (error.response?.status === 403) {
        return Promise.reject(createHttpError('安全验证失败，请刷新页面重试', 403))
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
