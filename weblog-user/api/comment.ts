import { http } from '~/utils/http'

export interface CommentVO {
  id: number
  postId: number
  userId: number
  nickname: string
  avatar: string | null
  parentId: number | null
  content: string
  likeCount: number
  liked?: boolean
  isTop: boolean
  status: string
  createTime: string
  postTitle?: string
  postSlug?: string
  replyToNickname: string | null
  replyTotal?: number
  replies?: CommentVO[]
}

export interface CreateCommentRequest {
  postId: number
  parentId?: number | null
  replyToUserId?: number | null
  content: string
}

export interface CommentPageResult {
  records: CommentVO[]
  total: number
  current: number
  pages: number
}

export const commentApi = {
  /** 发表评论 */
  create: (data: CreateCommentRequest) =>
    http.post<unknown, { data: CommentVO }>('/portal/comment', data),

  /** 删除评论 */
  delete: (commentId: number) =>
    http.delete<unknown, { data: void }>(`/portal/comment/${commentId}`),

  /** 批量删除评论 */
  batchDelete: (commentIds: number[]) =>
    http.delete<unknown, { data: void }>('/portal/comment/batch', { data: commentIds }),

  /** 文章评论列表 */
  listByPost: (postId: number, pageNum = 1, pageSize = 10, sort = 'new') =>
    http.get<unknown, { data: CommentPageResult }>(`/portal/comment/post/${postId}`, { params: { pageNum, pageSize, sort } }),

  /** 评论点赞/取消 */
  toggleLike: (commentId: number) =>
    http.post<unknown, { data: { liked: boolean; likeCount: number } }>(`/portal/comment/like/${commentId}`),

  /** 子评论分页列表 */
  listReplies: (parentId: number, pageNum = 1, pageSize = 10) =>
    http.get<unknown, { data: CommentPageResult }>(`/portal/comment/replies/${parentId}`, { params: { pageNum, pageSize } }),

  /** 我的评论 */
  myComments: (pageNum = 1, pageSize = 10) =>
    http.get<unknown, { data: CommentPageResult }>('/portal/comment/my', { params: { pageNum, pageSize } }),
}
