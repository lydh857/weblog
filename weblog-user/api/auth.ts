import { http } from '~/utils/http'
import { encryptPassword } from '~/utils/crypto'

export interface LoginParams {
  email: string
  password: string
  rememberMe?: boolean
}

export interface CodeLoginParams {
  email: string
  code: string
}

export interface RegisterParams {
  email: string
  password: string
  nickname?: string
}

export interface SendCodeParams {
  email: string
  scene: 'login' | 'register' | 'bind' | 'change-email' | 'reset-pwd' | 'forgot-password'
}

export interface LoginResult {
  success: boolean
  userId: number
  email: string
  nickname: string
  avatar: string
  role: string
  rememberToken?: string
}

export const authApi = {
  login: async (data: LoginParams, verifyToken?: string) => {
    return http.post<any, { data: LoginResult }>('/portal/auth/login', data, verifyToken ? {
      headers: { 'X-Captcha-Token': verifyToken },
    } : undefined)
  },
  loginByCode: (data: CodeLoginParams) => http.post<any, { data: LoginResult }>('/portal/auth/login-by-code', data),
  register: async (data: RegisterParams, code: string, verifyToken: string) => {
    return http.post('/portal/auth/register', data, {
      params: { code },
      headers: { 'X-Captcha-Token': verifyToken },
    })
  },
  sendCode: (data: SendCodeParams, verifyToken: string) =>
    http.post('/portal/auth/send-code', data, {
      headers: { 'X-Captcha-Token': verifyToken },
    }),
  checkEmail: (email: string) =>
    http.post('/portal/auth/check-email', { email }),
  forgotPassword: async (data: { email: string; code: string; password: string }) => {
    return http.post('/portal/auth/forgot-password', data)
  },
  logout: () => http.post('/portal/auth/logout'),
  rememberLogin: async (token: string) => {
    return http.post<any, { data: LoginResult }>('/portal/auth/remember-login', { token })
  },
  getGithubAuthUrl: (redirectUri: string) =>
    http.get<any, { data: string }>('/portal/oauth/github/authorize', { params: { redirectUri } }),
  githubCallback: (code: string, state: string) =>
    http.post<any, { data: LoginResult }>('/portal/oauth/github/callback', null, { params: { code, state } }),
}
