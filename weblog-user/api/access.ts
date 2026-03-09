import { http } from '~/utils/http'

export const accessApi = {
  /** 检查是否可以阅读文章 */
  check: (postId: number) =>
    http.get<any, { data: { allowed: boolean; readCount: number; limit: number; unlocked: boolean; loggedIn: boolean } }>(`/portal/access/check/${postId}`),

  /** 记录阅读 */
  recordRead: (postId: number) =>
    http.post(`/portal/access/read/${postId}`),

  /** 滑块验证解锁 */
  unlock: () =>
    http.post('/portal/access/unlock'),
}
