import { http } from '~/utils/http'

export interface LoginParams {
  email: string
  password: string
  rememberMe?: boolean
}

export interface LoginResult {
  success: boolean
  userId: number
  email: string
  nickname: string
  avatar: string
  role: string
}

export const authApi = {
  login: async (data: LoginParams, verifyToken: string) => {
    return http.post<unknown, { data: LoginResult }>('/admin/auth/login', data, {
      headers: {
        'X-Captcha-Token': verifyToken,
      },
    })
  },
  logout: () => http.post('/admin/auth/logout'),
  rememberLogin: async () => {
    return http.post<unknown, { data: LoginResult }>('/admin/auth/remember-login', {})
  },
  revokeToken: async (token: string) => {
    return http.post('/admin/auth/revoke-token', { token })
  },
  revokeAllTokens: async () => {
    return http.post('/admin/auth/revoke-all-tokens')
  },
}
