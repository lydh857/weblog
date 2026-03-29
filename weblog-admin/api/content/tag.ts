import { http } from '~/utils/http'

export interface TagVO {
  id: number
  name: string
  slug: string
  articleCount: number
  createTime: string
  updateTime: string
}

export interface TagParams {
  name: string
  slug?: string
}

export interface BatchTagItem {
  name: string
  slug?: string
}

export const tagApi = {
  listAll: (keyword?: string) =>
    http.get<unknown, { data: TagVO[] }>('/admin/tag', { params: keyword ? { keyword } : {} }),

  getById: (id: number) =>
    http.get<unknown, { data: TagVO }>(`/admin/tag/${id}`),

  create: (data: TagParams) =>
    http.post<unknown, { data: TagVO }>('/admin/tag', data),

  batchCreate: (tags: BatchTagItem[]) =>
    http.post<unknown, { data: TagVO[] }>('/admin/tag/batch', { tags }),

  update: (id: number, data: TagParams) =>
    http.put<unknown, { data: TagVO }>(`/admin/tag/${id}`, data),

  delete: (id: number) =>
    http.delete(`/admin/tag/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/tag/batch', { data: { ids } }),
}
