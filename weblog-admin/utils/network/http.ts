import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'

// ============================================================
// 安全说明：
// CSRF 采用 HttpOnly + SameSite Cookie，前端不读取 token
// Cookie 会随请求自动发送，后端从 Cookie 中验证
// ============================================================

// 防止刷新死循环
let isRefreshing = false

interface RefreshSubscriber {
  onSuccess: () => void
  onError: (error: Error & { code: number }) => void
}

let refreshSubscribers: RefreshSubscriber[] = []

function subscribeTokenRefresh(onSuccess: () => void, onError: (error: Error & { code: number }) => void) {
  refreshSubscribers.push({ onSuccess, onError })
}

function onTokenRefreshed() {
  refreshSubscribers.forEach(sub => sub.onSuccess())
  refreshSubscribers = []
}

function onTokenRefreshFailed(error: Error & { code: number }) {
  refreshSubscribers.forEach(sub => sub.onError(error))
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
          return new Promise((resolve, reject) => {
            subscribeTokenRefresh(
              () => {
                resolve(instance(originalRequest))
              },
              (err) => {
                reject(err)
              }
            )
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

          onTokenRefreshed()
          isRefreshing = false

          return instance(originalRequest)
        } catch (refreshError) {
          isRefreshing = false
          removeToken()

          const err = new Error('登录已过期，请重新登录') as Error & { code: number }
          err.code = 401
          onTokenRefreshFailed(err)
          navigateTo('/login')

          return Promise.reject(err)
        }
      }

      // 主动取消的请求，静默 reject，不弹错误提示
      if (axios.isCancel(error)) {
        return Promise.reject(error)
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
