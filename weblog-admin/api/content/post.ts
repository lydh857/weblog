import { http } from '~/utils/network/http'

export interface PostCreateParams {
  title: string
  slug?: string
  content: string
  summary?: string
  coverImage?: string
  categoryId?: number
  subCategoryId?: number
  tagIds?: number[]
  newTagNames?: string[]
  newCategoryName?: string
  newCategoryParentId?: number
  status?: string
  scheduledTime?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  isTop?: boolean
  previewTheme?: string
  codeTheme?: string
}

export interface PostVO {
  id: number
  title: string
  slug: string
  summary: string
  coverImage: string
  categoryId: number
  categoryName: string
  subCategoryId: number
  subCategoryName: string
  authorId: number
  status: string
  publishType: string
  scheduledTime: string
  viewCount: number
  likeCount: number
  collectCount: number
  commentCount: number
  seoTitle: string
  seoDescription: string
  seoKeywords: string
  isTop: boolean
  isDisabled: boolean
  previewTheme: string
  codeTheme: string
  content: string
  htmlContent: string
  tags: { id: number; name: string; slug: string }[]
  createTime: string
  updateTime: string
  // 运行时临时状态
  _topLoading?: boolean
  _disableLoading?: boolean
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export const postApi = {
  page: (params: {
    pageNum?: number; pageSize?: number; categoryId?: number;
    status?: string; keyword?: string; isDisabled?: boolean; tagId?: number
  }) =>
    http.get<unknown, { data: PageResult<PostVO> }>('/admin/post', { params }),

  getById: (id: number) =>
    http.get<unknown, { data: PostVO }>(`/admin/post/${id}`),

  create: (data: PostCreateParams) =>
    http.post<unknown, { data: PostVO }>('/admin/post', data),

  update: (id: number, data: PostCreateParams) =>
    http.put<unknown, { data: PostVO }>(`/admin/post/${id}`, data),

  delete: (id: number) =>
    http.delete(`/admin/post/${id}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/post/batch', { data: { ids } }),

  batchSetTop: (ids: number[], isTop: boolean) =>
    http.put('/admin/post/batch-top', { ids, isTop }),

  batchSetDisabled: (ids: number[], isDisabled: boolean) =>
    http.put('/admin/post/batch-disabled', { ids, isDisabled }),

  toggleTop: (id: number) =>
    http.put(`/admin/post/${id}/toggle-top`),

  toggleDisabled: (id: number) =>
    http.put(`/admin/post/${id}/toggle-disabled`),

  batchPublish: (ids: number[]) =>
    http.put('/admin/post/batch-publish', { ids }),

  batchCancelSchedule: (ids: number[]) =>
    http.put('/admin/post/batch-cancel-schedule', { ids }),

  batchSchedule: (ids: number[], scheduledTime: string, intervalMinutes?: number) =>
    http.put('/admin/post/batch-schedule', { ids, scheduledTime, intervalMinutes }),

  autoSave: (id: number, data: { title?: string; content?: string }) =>
    http.put(`/admin/post/${id}/auto-save`, data),

  updateSeo: (id: number, data: { seoTitle?: string; seoDescription?: string; seoKeywords?: string }) =>
    http.put(`/admin/post/${id}/seo`, data),

  // 回收站
  trashPage: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    http.get<unknown, { data: PageResult<PostVO> }>('/admin/post/trash', { params }),

  batchRestore: (ids: number[]) =>
    http.put('/admin/post/trash/batch-restore', { ids }),

  batchPermanentDelete: (ids: number[]) =>
    http.delete('/admin/post/trash/batch-permanent', { data: { ids } }),

  clearTrash: () =>
    http.delete('/admin/post/trash/clear'),
}
