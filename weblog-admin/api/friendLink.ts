import { http } from '~/utils/http'

export interface FriendLinkVO {
  id: number
  name: string
  url: string
  logo: string
  description: string
  status: string
  sortOrder: number
  applicantUserId: number | null
  reason: string | null
  lastCheckTime: string
  createTime: string
  updateTime: string
}

export interface FriendLinkParams {
  name: string
  url: string
  logo?: string
  description?: string
  status?: string
  sortOrder?: number
}

export const friendLinkApi = {
  listAll: () =>
    http.get<unknown, { data: FriendLinkVO[] }>('/admin/friend-link'),

  getById: (id: number) =>
    http.get<unknown, { data: FriendLinkVO }>(`/admin/friend-link/${id}`),

  create: (data: FriendLinkParams) =>
    http.post<unknown, { data: FriendLinkVO }>('/admin/friend-link', data),

  update: (id: number, data: FriendLinkParams) =>
    http.put<unknown, { data: FriendLinkVO }>(`/admin/friend-link/${id}`, data),

  delete: (id: number) =>
    http.delete(`/admin/friend-link/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/friend-link/batch', { data: ids }),

  batchUpdateStatus: (ids: number[], status: string) =>
    http.put('/admin/friend-link/batch/status', { ids, status }),

  checkLinks: () =>
    http.post<unknown, { data: number }>('/admin/friend-link/check'),

  approve: (id: number) =>
    http.put<unknown, { data: FriendLinkVO }>(`/admin/friend-link/${id}/approve`),

  reject: (id: number, reason?: string) =>
    http.put<unknown, { data: FriendLinkVO }>(`/admin/friend-link/${id}/reject`, { reason }),
}
