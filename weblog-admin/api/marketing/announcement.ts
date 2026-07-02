import { http } from '~/utils/network/http'

export interface AnnouncementVO {
  id: number
  title: string
  content: string
  type: string
  status: string
  priority: number
  startTime: string | null
  endTime: string | null
  isClosable: boolean
  createTime: string
}

export interface AnnPageResult {
  records: AnnouncementVO[]
  total: number
  current: number
  pages: number
}

export const announcementApi = {
  list: (params: { pageNum?: number; pageSize?: number; status?: string; type?: string }) =>
    http.get<unknown, { data: AnnPageResult }>('/admin/announcement', { params }),

  create: (data: Partial<AnnouncementVO>) =>
    http.post<unknown, { data: AnnouncementVO }>('/admin/announcement', data),

  update: (id: number, data: Partial<AnnouncementVO>) =>
    http.put(`/admin/announcement/${id}`, data),

  updateStatus: (id: number, status: string) =>
    http.put(`/admin/announcement/${id}/status`, null, { params: { status } }),

  delete: (id: number) =>
    http.delete(`/admin/announcement/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/announcement/batch', { data: ids }),

  batchUpdateStatus: (ids: number[], status: string) =>
    http.put('/admin/announcement/batch/status', { ids, status }),
}
