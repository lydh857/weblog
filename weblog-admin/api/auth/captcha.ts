import { http } from '~/utils/network/http'

export interface TrackPoint {
  x: number
  y: number
  timestamp: number
}

export interface CaptchaGenerateResult {
  captchaToken: string
  backgroundImage: string
  puzzleImage: string
  puzzleY: number
  puzzleWidth: number
  puzzleHeight: number
}

export interface CaptchaVerifyResult {
  success: boolean
  verifyToken?: string
  message?: string
}

export const captchaApi = {
  generate: (scene: string) =>
    http.get<unknown, { data: CaptchaGenerateResult }>('/captcha/generate', { params: { scene, t: Date.now() } }),

  verify: (data: { captchaToken: string; scene: string; sliderPosition: number; slideTrack: TrackPoint[] }) =>
    http.post<unknown, { data: CaptchaVerifyResult }>('/captcha/verify', data),

  refresh: (oldToken: string, scene: string) =>
    http.post<unknown, { data: CaptchaGenerateResult }>('/captcha/refresh', { oldToken, scene }),
}
