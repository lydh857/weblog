import { http } from '~/utils/network/http'

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  pages: number
}

export interface LoginLogVO {
  id: number
  userId: number
  email: string
  loginType: string
  result: string
  failReason: string
  ip: string
  userAgent: string
  createTime: string
}

export interface IpBlockStatusVO {
  ip: string
  blocked: boolean
  reason: string | null
  remainingSeconds: number
  permanent: boolean
}

export interface UserBlockStatusVO {
  userId: number
  blocked: boolean
  reason: string | null
  remainingSeconds: number
  permanent: boolean
  subject: string | null
}

export interface BlacklistVO {
  id: number
  blockType: 'IP' | 'USER'
  targetValue: string
  userId: number | null
  subject: string
  reason: string | null
  expireTime: string | null
  remainingSeconds: number
  permanent: boolean
  createTime: string
  updateTime: string
}

export interface AuditLogVO {
  id: number
  userId: number | null
  username: string
  operation: string
  module: string
  description: string
  requestMethod: string
  requestUrl: string
  responseCode: number
  ipAddress: string
  userAgent: string
  executionTime: number
  createTime: string
  adminActor: boolean
}

export const logsApi = {
  getLoginLogs: (params: {
    pageNum?: number
    pageSize?: number
    email?: string
    loginType?: string
    result?: string
    ip?: string
  }) => http.get<unknown, { data: PageResult<LoginLogVO> }>('/admin/logs/login', { params }),

  getAuditLogs: (params: {
    pageNum?: number
    pageSize?: number
    operation?: string
    module?: string
    username?: string
    ipAddress?: string
  }) => http.get<unknown, { data: PageResult<AuditLogVO> }>('/admin/logs/audit', { params }),

  blockIp: (payload: { ip: string; durationMinutes?: number; reason?: string; permanent?: boolean }) =>
    http.post<unknown, { data: IpBlockStatusVO }>('/admin/logs/ip-block', payload),

  blockUser: (payload: { userId: number; durationMinutes?: number; reason?: string; permanent?: boolean }) =>
    http.post<unknown, { data: UserBlockStatusVO }>('/admin/logs/user-block', payload),

  unblockIp: (ip: string) =>
    http.delete('/admin/logs/ip-block', { params: { ip } }),

  unblockBlacklist: (id: number) =>
    http.delete('/admin/logs/blacklist', { params: { id } }),

  getIpBlockStatus: (ip: string) =>
    http.get<unknown, { data: IpBlockStatusVO }>('/admin/logs/ip-block', { params: { ip } }),

  getUserBlockStatus: (userId: number) =>
    http.get<unknown, { data: UserBlockStatusVO }>('/admin/logs/user-block', { params: { userId } }),

  getBlacklist: (params: {
    pageNum?: number
    pageSize?: number
    blockType?: 'IP' | 'USER'
    keyword?: string
    status?: 'active' | 'expired' | 'all'
  }) => http.get<unknown, { data: PageResult<BlacklistVO> }>('/admin/logs/blacklist', { params }),
}
