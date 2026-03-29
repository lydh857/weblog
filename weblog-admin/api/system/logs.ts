import { http } from '~/utils/http'

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
}

export const logsApi = {
  getLoginLogs: (params: {
    pageNum?: number
    pageSize?: number
    email?: string
    loginType?: string
    result?: string
  }) => http.get<unknown, { data: PageResult<LoginLogVO> }>('/admin/logs/login', { params }),

  getAuditLogs: (params: {
    pageNum?: number
    pageSize?: number
    operation?: string
    module?: string
    username?: string
    ipAddress?: string
  }) => http.get<unknown, { data: PageResult<AuditLogVO> }>('/admin/logs/audit', { params }),
}
