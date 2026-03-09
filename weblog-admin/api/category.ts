import { http } from '~/utils/http'

export interface CategoryVO {
  id: number
  name: string
  slug: string
  description: string
  parentId: number
  sortOrder: number
  articleCount: number
  createTime: string
  updateTime: string
}

export interface CategoryParams {
  name: string
  slug?: string
  description?: string
  parentId?: number
  sortOrder?: number
}

export const categoryApi = {
  listAll: (keyword?: string) =>
    http.get<unknown, { data: CategoryVO[] }>('/admin/category', { params: keyword ? { keyword } : {} }),

  listTopLevel: () =>
    http.get<unknown, { data: CategoryVO[] }>('/admin/category/top'),

  listChildren: (parentId: number) =>
    http.get<unknown, { data: CategoryVO[] }>(`/admin/category/children/${parentId}`),

  getById: (id: number) =>
    http.get<unknown, { data: CategoryVO }>(`/admin/category/${id}`),

  create: (data: CategoryParams) =>
    http.post<unknown, { data: CategoryVO }>('/admin/category', data),

  update: (id: number, data: CategoryParams) =>
    http.put<unknown, { data: CategoryVO }>(`/admin/category/${id}`, data),

  delete: (id: number) =>
    http.delete(`/admin/category/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/category/batch', { data: { ids } }),
}
