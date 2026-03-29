import { http } from '~/utils/http'

export interface UserVO {
  id: number
  email: string
  nickname: string
  avatar: string | null
  bio: string | null
  role: string
  status: string
  githubId: string | null
  lastLoginTime: string | null
  lastLoginIp: string | null
  failedLoginAttempts: number | null
  lockUntil: string | null
  createTime: string
}

export interface UserPageResult {
  records: UserVO[]
  total: number
  current: number
  pages: number
}

export const userApi = {
  list: (params: { pageNum?: number; pageSize?: number; keyword?: string; role?: string; status?: string }) =>
    http.get<unknown, { data: UserPageResult }>('/admin/user', { params }),

  me: () =>
    http.get<unknown, { data: { userId: number; email: string; nickname: string; avatar: string; role: string } }>('/admin/user/me'),

  updateStatus: (userId: number, status: string) =>
    http.put(`/admin/user/${userId}/status`, null, { params: { status } }),

  resetPassword: (userId: number) =>
    http.post(`/admin/user/${userId}/reset-password`),

  unlock: (userId: number) =>
    http.post(`/admin/user/${userId}/unlock`),

  batchUpdateStatus: (ids: number[], status: string) =>
    http.put('/admin/user/batch/status', { ids, status }),
}
