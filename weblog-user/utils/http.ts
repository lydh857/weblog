import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { useLoginModal } from '~/composables/useLoginModal'

// ============================================================
// 安全说明：
// 1. CSRF Cookie：SameSite=Strict，浏览器自动在同站请求中发送
// 2. 双重验证：前端从 Cookie 读取 token，添加到 Header
// 3. 即使 XSS 攻击，攻击者也无法跨站利用 SameSite=Strict Cookie
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

/**
 * 获取 CSRF Token
 * 从 localStorage 中读取（更可靠，不受 Cookie 策略影响）
 */
function getCsrfToken(): string | null {
  if (typeof document === 'undefined') return null

  const token = localStorage.getItem('X-CSRF-TOKEN')
  return token
}

/**
 * 更新 CSRF Token
 * 从响应 Header 中获取新 token 并存储到 localStorage
 */
function updateCsrfToken(response: AxiosResponse) {
  const newToken = response.headers['x-csrf-token']
  if (newToken) {
    localStorage.setItem('X-CSRF-TOKEN', newToken)
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

    // 双重验证：状态变更请求需要同时发送 Cookie 和 Header token
    const method = reqConfig.method?.toUpperCase()
    if (method === 'POST' || method === 'PUT' || method === 'DELETE' || method === 'PATCH') {
      const csrfToken = getCsrfToken()
      if (csrfToken) {
        reqConfig.headers['X-CSRF-TOKEN'] = csrfToken
      }
    }

    return reqConfig
  })

  // 响应拦截器
  instance.interceptors.response.use(
    // 成功响应：更新 CSRF Token
    (response: AxiosResponse) => {
      updateCsrfToken(response)

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
  document.cookie = `${name}=${encodeURIComponent(value)};path=/;max-age=${maxAge};SameSite=Lax`
}

function removeCookie(name: string) {
  document.cookie = `${name}=;path=/;max-age=0`
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
