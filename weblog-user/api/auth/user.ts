import { http } from '~/utils/http'

export interface UserProfileVO {
  userId: number
  email: string | null
  nickname: string
  avatar: string | null
  bio: string | null
  role: string
  hasPassword: boolean
  needBindEmail: boolean
  createTime: string
  profileReviewStatus?: 'pending' | 'approved' | 'rejected'
  profileReviewRejectReason?: string | null
  profileReviewSubmitTime?: string | null
  pendingNickname?: string | null
  pendingBio?: string | null
  pendingAvatar?: string | null
}

export interface UpdateProfileRequest {
  nickname?: string
  bio?: string
  avatar?: string
}

export const userApi = {
  /** 获取个人资料 */
  getProfile: () =>
    http.get<any, { data: UserProfileVO }>('/portal/user/profile'),

  /** 更新个人资料 */
  updateProfile: (data: UpdateProfileRequest) =>
    http.put<any, { data: void }>('/portal/user/profile', data),

  /** 上传头像 */
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<any, { data: string }>('/portal/user/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** 绑定邮箱 */
  bindEmail: (data: { email: string; code: string }) =>
    http.post('/portal/user/bind-email', data),

  /** 换绑邮箱 */
  changeEmail: (data: { email: string; code: string }) =>
    http.post('/portal/user/change-email', data),

  /** 设置密码 */
  setPassword: (data: { password: string }) =>
    http.post('/portal/user/set-password', data),

  /** 重置密码 */
  resetPassword: (data: { code: string; password: string }) =>
    http.post('/portal/user/reset-password', data),
}
