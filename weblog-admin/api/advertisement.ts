import { http } from '~/utils/http'

export interface AdvertisementVO {
  id: number
  title: string
  type: string
  content: string
  linkUrl: string
  position: string
  advertiserId: number | null
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

export const advertisementApi = {
  list: (params: { pageNum?: number; pageSize?: number; status?: string; position?: string }) =>
    http.get<unknown, { data: AdPageResult }>('/admin/advertisement', { params }),

  create: (data: Partial<AdvertisementVO>) =>
    http.post<unknown, { data: AdvertisementVO }>('/admin/advertisement', data),

  update: (id: number, data: Partial<AdvertisementVO>) =>
    http.put(`/admin/advertisement/${id}`, data),

  updateStatus: (id: number, status: string) =>
    http.put(`/admin/advertisement/${id}/status`, null, { params: { status } }),

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
