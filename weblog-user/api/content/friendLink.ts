import { http } from '~/utils/network/http'

export interface FriendLinkVO {
  id: number
  name: string
  url: string
  logo: string
  description: string
  sortOrder: number
  status: string
  reason: string | null
  applicantUserId: number | null
}

export interface ApplyLinkForm {
  name: string
  url: string
  logo: string
  description: string
}

export const friendLinkApi = {
  /** 获取有效友链列表 */
  listActive: () =>
    http.get<FriendLinkVO[], { data: FriendLinkVO[] }>('/friend-link'),

  /** 申请友链 */
  applyLink: (data: ApplyLinkForm) =>
    http.post<FriendLinkVO, { data: FriendLinkVO }>('/friend-link/apply', data),

  /** 查询友链申请入口开关 */
  getApplyStatus: () =>
    http.get<{ enabled: boolean }, { data: { enabled: boolean } }>('/friend-link/apply-status'),

  /** 查询我的友链申请 */
  getMyLink: () =>
    http.get<FriendLinkVO | null, { data: FriendLinkVO | null }>('/friend-link/my'),

  /** 更新我的友链申请 */
  updateMyLink: (data: ApplyLinkForm) =>
    http.put<FriendLinkVO, { data: FriendLinkVO }>('/friend-link/my', data),
}
