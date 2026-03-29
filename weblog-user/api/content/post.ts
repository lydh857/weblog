import { http } from '~/utils/http'

export interface PostVO {
  id: number
  title: string
  slug: string
  summary: string | null
  coverImage: string | null
  categoryId: number | null
  categoryName: string | null
  subCategoryId: number | null
  subCategoryName: string | null
  authorId: number
  authorNickname: string | null
  authorAvatar: string | null
  status: string
  viewCount: number
  likeCount: number
  collectCount: number
  commentCount: number
  isTop: boolean
  createTime: string
  updateTime: string
  content?: string
  htmlContent?: string
  tags: TagVO[]
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  previewTheme?: string
  codeTheme?: string
}

export interface TagVO {
  id: number
  name: string
  slug: string
  color: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface PostDetailResult {
  post: PostVO
  prev: { id: number; title: string; slug: string; coverImage?: string } | null
  next: { id: number; title: string; slug: string; coverImage?: string } | null
}

export const postApi = {
  /** 文章列表 */
  list: (params: { pageNum?: number; pageSize?: number; categoryId?: number; tagId?: number; categorySlug?: string; tagSlug?: string; sortBy?: string }) =>
    http.get<any, { data: PageResult<PostVO> }>('/portal/post', { params }),

  /** 文章详情（含上下篇） */
  detail: (slug: string) =>
    http.get<any, { data: PostDetailResult }>(`/portal/post/${slug}`),

  /** 今日发布文章列表 */
  listToday: (limit = 8) =>
    http.get<any, { data: PostVO[] }>('/portal/post/today', { params: { limit } }),

  /** 最近发布文章列表 */
  listRecent: (limit = 10) =>
    http.get<any, { data: PostVO[] }>('/portal/post/recent', { params: { limit } }),

  /** 相关文章推荐 */
  getSimilarPosts: (postId: number) =>
    http.get<any, { data: PostVO[] }>(`/portal/post/${postId}/similar`),
}
