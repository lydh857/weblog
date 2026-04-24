import { http } from '~/utils/network/http'

export interface CommentVO {
  id: number
  postId: number
  userId: number
  nickname: string
  avatar: string | null
  parentId: number
  content: string
  likeCount: number
  isTop: boolean
  status: string
  createTime: string
  /** 文章标题 */
  postTitle: string
  /** 回复对象昵称 */
  replyToNickname: string | null
}

export interface CommentPageResult {
  records: CommentVO[]
  total: number
  current: number
  pages: number
}

export const commentApi = {
  list: (params: { pageNum?: number; pageSize?: number; status?: string; postId?: number; postTitle?: string; isTop?: boolean }) =>
    http.get<unknown, { data: CommentPageResult }>('/admin/comment', { params }),

  updateStatus: (commentId: number, status: string) =>
    http.put(`/admin/comment/${commentId}/status`, null, { params: { status } }),

  toggleTop: (commentId: number) =>
    http.put(`/admin/comment/${commentId}/top`),

  delete: (commentId: number) =>
    http.delete(`/admin/comment/${commentId}`),

  batchDelete: (ids: number[]) =>
    http.delete('/admin/comment/batch', { data: ids }),

  batchUpdateStatus: (ids: number[], status: string) =>
    http.put('/admin/comment/batch/status', { ids, status }),
}
