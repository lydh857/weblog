import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'

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
    timeout: 10000,
    headers: { 'Content-Type': 'application/json' },
    withCredentials: true, // 关键：允许发送 Cookie
  })

  // 请求拦截器
  instance.interceptors.request.use((reqConfig: InternalAxiosRequestConfig) => {
    if (!reqConfig.baseURL) {
      reqConfig.baseURL = getBaseURL()
    }

    // X-Requested-With 标识 AJAX 请求
    if (import.meta.client) {
      reqConfig.headers['X-Requested-With'] = 'XMLHttpRequest'
    }

    return reqConfig
  })

  // 响应拦截器
  instance.interceptors.response.use(
    (response) => {
      const data = response.data
      if (data.code !== 200) {
        if (data.code === 401 && import.meta.client) {
          removeToken()
          navigateTo('/login')
        }
        const err = new Error(data.message || '请求失败') as Error & { code: number }
        err.code = data.code
        return Promise.reject(err)
      }
      return data
    },
    // 错误响应
    async (error) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
      const responseStatus = Number(error.response?.status || 0)
      const businessCode = Number(error.response?.data?.code || 0)
      const businessMessage = error.response?.data?.message as string | undefined

      const shouldBypassRefresh = businessCode === 40103 || businessCode === 403 || businessCode === 429 || responseStatus === 429
      if (shouldBypassRefresh) {
        const message = businessMessage || (responseStatus === 429 ? '访问受限，请稍后再试' : '请求失败')
        const err = new Error(message) as Error & { code: number }
        err.code = businessCode || responseStatus
        return Promise.reject(err)
      }

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
            `${getBaseURL()}/admin/auth/refresh`,
            {},
            { withCredentials: true }
          )

          onTokenRefreshed('refreshed')
          isRefreshing = false

          return instance(originalRequest)
        } catch (refreshError) {
          isRefreshing = false
          removeToken()
          navigateTo('/login')

          const err = new Error('登录已过期，请重新登录') as Error & { code: number }
          err.code = 401
          return Promise.reject(err)
        }
      }

      // 其他错误
      if (import.meta.client) {
        if (error.response?.status === 401) {
          removeToken()
          navigateTo('/login')
        }
      }

      let errorMessage = '网络异常，请稍后重试'
      if (error.code === 'ECONNABORTED') {
        errorMessage = '请求超时，请稍后重试'
      } else if (!error.response) {
        // 没有响应可能是网络问题或 CORS 被拦截
        errorMessage = '网络连接失败，请检查网络或刷新页面'
      } else if (error.response?.status === 429) {
        errorMessage = error.response?.data?.message || '访问受限，请稍后再试'
      } else if (error.response?.status === 403) {
        errorMessage = error.response?.data?.message || '无权限访问'
      } else {
        errorMessage = error.response?.data?.message || errorMessage
      }

      const err = new Error(errorMessage) as Error & { code: number }
      err.code = error.response?.data?.code || error.response?.status
      return Promise.reject(err)
    },
  )

  return instance
}

export const http = createHttp()

export function setToken(_token: string) {
}

export function getToken(): string | null {
  return null
}

export function removeToken() {
}
