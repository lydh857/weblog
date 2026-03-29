import { http } from '~/utils/http'

export interface TopicItem {
  id: number
  title: string
  cover: string | null
  summary: string | null
  weight: number
  isPublish: boolean
  isTop: boolean
  articleCount: number
  createTime: string
  updateTime: string
}

export interface TopicDetail {
  id: number
  title: string
  cover: string | null
  summary: string | null
  createTime: string
}

export interface CatalogNode {
  id: number
  title: string
  articleId: number | null
  slug?: string | null
  coverImage?: string | null
  level: number
  children?: CatalogNode[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  pages: number
}

export const topicApi = {
  /** 专题列表（已发布） */
  list: (pageNum = 1, pageSize = 12) =>
    http.get<unknown, { data: PageResult<TopicItem> }>('/portal/topic', { params: { pageNum, pageSize } }),

  /** 专题详情 */
  detail: (id: number) =>
    http.get<unknown, { data: TopicDetail }>(`/portal/topic/${id}`),

  /** 专题目录树 */
  catalogs: (id: number) =>
    http.get<unknown, { data: CatalogNode[] }>(`/portal/topic/${id}/catalogs`),
}
