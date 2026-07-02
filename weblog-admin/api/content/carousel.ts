import { http } from '~/utils/network/http'

/** 轮播项 */
export interface CarouselItem {
  id: number
  type: 'article' | 'image'
  title: string | null
  description: string | null
  imageUrl: string
  linkUrl: string | null
  articleId: number | null
  sortOrder: number
  isEnabled: boolean
  startTime: string | null
  endTime: string | null
  createTime: string
  updateTime: string
}

export interface CarouselPageResult {
  records: CarouselItem[]
  total: number
  current: number
  pages: number
}

export interface CarouselForm {
  type: string
  title?: string | null
  description?: string | null
  imageUrl: string
  linkUrl?: string | null
  articleId?: number | null
  sortOrder?: number
  isEnabled?: boolean
  startTime?: string | null
  endTime?: string | null
}

export const carouselApi = {
  /** 分页列表 */
  list: (params: { pageNum?: number; pageSize?: number }) =>
    http.get<unknown, { data: CarouselPageResult }>('/admin/carousel', { params }),

  /** 创建 */
  create: (data: CarouselForm) =>
    http.post<unknown, { data: CarouselItem }>('/admin/carousel', data),

  /** 更新 */
  update: (id: number, data: Partial<CarouselForm>) =>
    http.put(`/admin/carousel/${id}`, data),

  /** 删除 */
  delete: (id: number) =>
    http.delete(`/admin/carousel/${id}`),
}
