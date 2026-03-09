import { http } from '~/utils/http'

export interface AdvertisementVO {
  id: number
  title: string
  type: string
  content: string
  linkUrl: string
  position: string
  status: string
  startTime: string | null
  endTime: string | null
  clickCount: number
  weight: number
}

export const advertisementApi = {
  /** 按位置获取有效广告 */
  getByPosition: (position: string) =>
    http.get<any, { data: AdvertisementVO[] }>('/portal/advertisement', { params: { position } }),

  /** 记录广告点击 */
  recordClick: (id: number) =>
    http.post(`/portal/advertisement/${id}/click`),

  /** 提交广告申请 */
  apply: (data: { title: string; type: string; content: string; linkUrl?: string; position: string; startTime?: string; endTime?: string }) =>
    http.post<any, { data: AdvertisementVO }>('/portal/advertisement/apply', data),
}
