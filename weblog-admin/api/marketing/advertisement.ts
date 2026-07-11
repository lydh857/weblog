import { http } from '~/utils/network/http'

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
  pitTitle?: string | null
  advertiserId: number | null
  advertiserEmail?: string | null
  advertiserNickname?: string | null
  linkDomain?: string | null
  linkDomainPolicyStatus?: string | null
  linkDomainPolicyReason?: string | null
  status: string
  startTime: string | null
  endTime: string | null
  clickCount: number
  weight: number
  createTime: string
}

export interface AdPageResult {
  records: AdvertisementVO[]
  total: number
  current: number
  pages: number
}

export interface AdPriceRuleVO {
  position: string
  pitIndex: number
  durationDays: number
  price: number
}

export interface AdPitOrderPayload {
  position: string
  pitIds: number[]
}

export interface AdDisplayOrderPayload {
  position: string
  adIds: number[]
}

export const advertisementApi = {
  list: (params: { pageNum?: number; pageSize?: number; status?: string; position?: string }) =>
    http.get<unknown, { data: AdPageResult }>('/admin/advertisement', { params }),

  create: (data: Partial<AdvertisementVO>) =>
    http.post<unknown, { data: AdvertisementVO }>('/admin/advertisement', data),

  update: (id: number, data: Partial<AdvertisementVO>) =>
    http.put(`/admin/advertisement/${id}`, data),

  setPitEnabled: (id: number, enabled: boolean) =>
    http.put(`/admin/advertisement/${id}/pit`, { enabled }),

  updatePitOrder: (data: AdPitOrderPayload) =>
    http.put('/admin/advertisement/pit-order', data),

  updateDisplayOrder: (data: AdDisplayOrderPayload) =>
    http.put('/admin/advertisement/order', data),

  updateStatus: (id: number, status: string, reason?: string) =>
    http.put(`/admin/advertisement/${id}/status`, null, {
      params: {
        status,
        reason: reason || undefined,
      },
    }),

  delete: (id: number) =>
    http.delete(`/admin/advertisement/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/advertisement/batch', { data: ids }),

  batchUpdateStatus: (ids: number[], status: string) =>
    http.put('/admin/advertisement/batch/status', { ids, status }),

  getApplySwitch: () =>
    http.get<unknown, { data: { enabled: boolean } }>('/admin/advertisement/apply-switch'),

  setApplySwitch: (enabled: boolean) =>
    http.put('/admin/advertisement/apply-switch', { enabled }),

  getPriceRules: () =>
    http.get<unknown, { data: { rules: AdPriceRuleVO[] } }>('/admin/advertisement/price-rules'),

  setPriceRules: (rules: AdPriceRuleVO[]) =>
    http.put('/admin/advertisement/price-rules', { rules }),

  // 回收站
  trashPage: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    http.get<unknown, { data: AdPageResult }>('/admin/advertisement/trash', { params }),

  batchRestore: (ids: number[]) =>
    http.put('/admin/advertisement/trash/batch-restore', ids),

  batchPermanentDelete: (ids: number[]) =>
    http.delete('/admin/advertisement/trash/batch-permanent', { data: { ids } }),

  clearTrash: () =>
    http.delete('/admin/advertisement/trash/clear'),
}
