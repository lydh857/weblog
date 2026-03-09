import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { useLoginModal } from '~/composables/useLoginModal'

// ============================================================
// 安全说明：
// CSRF 采用 HttpOnly + SameSite Cookie，前端不读取 token
// Cookie 会随请求自动发送，后端从 Cookie 中验证
// ============================================================

// 防止刷新死循环
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
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
        const err: any = new Error(data.message || '请求失败')
        err.code = data.code
        return Promise.reject(err)
      }
      return data
    },
    // 错误响应
    async (error) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

      // 401 未授权：尝试使用 Refresh Token 刷新
      if (error.response?.status === 401 && import.meta.client && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve) => {
            subscribeTokenRefresh(() => {
              resolve(instance(originalRequest))
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          await axios.post(
            `${getBaseURL()}/portal/auth/refresh`,
            {},
            { withCredentials: true }
          )

          onTokenRefreshed('refreshed')
          isRefreshing = false

          return instance(originalRequest)
        } catch (refreshError) {
          isRefreshing = false
          removeToken()
          try { useLoginModal().open() } catch {}

          const err: any = new Error('登录已过期，请重新登录')
          err.code = 401
          return Promise.reject(err)
        }
      }

      // CSRF 验证失败
      if (error.response?.status === 403) {
        const err: any = new Error('安全验证失败，请刷新页面重试')
        err.code = 403
        return Promise.reject(err)
      }

      // 其他错误
      const serverMsg = error.response?.data?.message
      const err: any = new Error(serverMsg || '网络异常，请稍后重试')
      err.code = error.response?.data?.code || error.response?.status
      return Promise.reject(err)
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
  const isProd = process.env.NODE_ENV === 'production'
  const options = [
    `path=/`,
    `max-age=${maxAge}`,
    isProd ? 'SameSite=Strict' : 'SameSite=Lax',
    isProd ? 'Secure' : ''
  ].filter(Boolean).join('; ')
  document.cookie = `${name}=${encodeURIComponent(value)}; ${options}`
}

function removeCookie(name: string) {
  const isProd = process.env.NODE_ENV === 'production'
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
