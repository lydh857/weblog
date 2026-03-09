import { http } from '~/utils/http'

export interface SearchHit {
  id: number
  title: string
  slug: string
  summary: string
  highlightTitle: string
  highlightContent: string
  categoryId: number
  authorId: number
}

export interface SearchResult {
  hits: SearchHit[]
  total: number
  pageNum: number
  pageSize: number
  totalPages: number
}

export const searchApi = {
  search: (params: { keyword: string; pageNum?: number; pageSize?: number }) =>
    http.get<any, { data: SearchResult }>('/search', { params }),
}
