import { http } from '~/utils/network/http'

export interface InteractionStatus {
  liked: boolean
  favorited: boolean
  likeCount: number
  collectCount: number
}

export interface MyPostItem {
  id: number
  title: string
  slug: string
  summary: string | null
  coverImage: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  createTime: string
  favoriteTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  pages: number
}

export const interactionApi = {
  /** 设置点赞状态（幂等） */
  setLikeState: (postId: number, liked: boolean) =>
    http.post<unknown, { data: { liked: boolean; likeCount: number } }>(`/portal/interaction/like/${postId}/state`, { liked }),

  /** 点赞/取消点赞 */
  toggleLike: (postId: number) =>
    http.post<unknown, { data: { liked: boolean; likeCount: number } }>(`/portal/interaction/like/${postId}`),

  /** 查询是否点赞 */
  isLiked: (postId: number) =>
    http.get<unknown, { data: { liked: boolean; likeCount: number } }>(`/portal/interaction/like/${postId}`),

  /** 设置收藏状态（幂等） */
  setFavoriteState: (postId: number, favorited: boolean) =>
    http.post<unknown, { data: { favorited: boolean; collectCount: number } }>(`/portal/interaction/favorite/${postId}/state`, { favorited }),

  /** 收藏/取消收藏 */
  toggleFavorite: (postId: number) =>
    http.post<unknown, { data: { favorited: boolean; collectCount: number } }>(`/portal/interaction/favorite/${postId}`),

  /** 查询是否收藏 */
  isFavorited: (postId: number) =>
    http.get<unknown, { data: { favorited: boolean } }>(`/portal/interaction/favorite/${postId}`),

  /** 查询文章互动状态 */
  getStatus: (postId: number) =>
    http.get<unknown, { data: InteractionStatus }>(`/portal/interaction/status/${postId}`),

  /** 我点赞的文章 */
  myLikes: (pageNum = 1, pageSize = 10) =>
    http.get<unknown, { data: PageResult<MyPostItem> }>('/portal/interaction/my/likes', { params: { pageNum, pageSize } }),

  /** 我收藏的文章 */
  myFavorites: (pageNum = 1, pageSize = 10) =>
    http.get<unknown, { data: PageResult<MyPostItem> }>('/portal/interaction/my/favorites', { params: { pageNum, pageSize } }),

  /** 批量取消收藏 */
  batchUnfavorite: (postIds: number[]) =>
    http.delete<unknown, { data: void }>('/portal/interaction/favorite/batch', { data: postIds }),
}
