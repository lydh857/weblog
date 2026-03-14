import { http } from '~/utils/http'

export interface AdvertisementVO {
  id: number
  title: string
  type: string
  content: string
  adInfo?: string | null
  reviewReason?: string | null
  mimicContent?: string | null
  linkUrl: string
  position: string
  insertAfter?: number | null
  closable?: boolean
  autoRotate?: boolean
  rotateIntervalSec?: number | null
  pitEnabled?: boolean | null
  pitAdId?: number | null
  pitIndex?: number | null
  status: string
  startTime: string | null
  endTime: string | null
  clickCount: number
  weight: number
  createTime?: string
  updateTime?: string
}

export interface AdPriceRuleVO {
  position: string
  pitIndex: number
  durationDays: number
  price: number
}

export interface AdApplyPitOption {
  pitAdId: number
  position: string
  title: string
  insertAfter?: number | null
  pitIndex?: number | null
}

export interface AdApplyStatusVO {
  enabled: boolean
  rules: AdPriceRuleVO[]
  pitOptions: AdApplyPitOption[]
}

export interface AdApplyPayload {
  title: string
  type: string
  content: string
  linkUrl?: string
  position: string
  adInfo?: string
  mimicContent?: string
  insertAfter?: number
  pitAdId?: number
  startTime?: string
  endTime?: string
}

export const advertisementApi = {
  /** 按展示位获取有效广告 */
  getBySlot: (slot: string) =>
    http.get<unknown, { data: AdvertisementVO[] }>('/portal/advertisement', { params: { slot } }),

  /** 按位置获取有效广告 */
  getByPosition: (position: string) =>
    http.get<unknown, { data: AdvertisementVO[] }>('/portal/advertisement', { params: { position } }),

  /** 记录广告点击 */
  recordClick: (id: number) =>
    http.post(`/portal/advertisement/${id}/click`),

  /** 提交广告申请 */
  apply: (data: AdApplyPayload) =>
    http.post<unknown, { data: AdvertisementVO }>('/portal/advertisement/apply', data),

  /** 查询我的广告申请 */
  getMyApplication: (position?: string) =>
    http.get<unknown, { data: AdvertisementVO | null }>('/portal/advertisement/my', {
      params: { position: position || undefined },
    }),

  /** 查询广告申请入口开关 */
  getApplyStatus: () =>
    http.get<unknown, { data: AdApplyStatusVO }>('/portal/advertisement/apply-status'),
}
