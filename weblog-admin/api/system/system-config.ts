import { http } from '~/utils/network/http'

export interface SystemConfigVO {
  id: number
  configKey: string
  configValue: string
  description: string
  createTime: string
  updateTime: string
}

export interface SecurityLogCleanupResultVO {
  loginDeleted: number
  auditDeleted: number
}

export interface SecuritySingleCleanupResultVO {
  deleted: number
}

export const systemConfigApi = {
  /** 获取所有配置 */
  list: () => http.get<unknown, { data: SystemConfigVO[] }>('/admin/system-config'),

  /** 批量更新配置 */
  batchUpdate: (configs: Record<string, string>) =>
    http.put('/admin/system-config', configs),

  /** 手动刷新排行榜 */
  refreshRanking: () =>
    http.post('/admin/system-config/refresh-ranking'),

  /** 手动清理安全日志 */
  cleanupSecurityLogs: () =>
    http.post<unknown, { data: SecurityLogCleanupResultVO }>('/admin/system-config/cleanup-security-logs'),

  /** 手动清理登录日志 */
  cleanupLoginLogs: () =>
    http.post<unknown, { data: SecuritySingleCleanupResultVO }>('/admin/system-config/cleanup-login-logs'),

  /** 手动清理审计日志 */
  cleanupAuditLogs: () =>
    http.post<unknown, { data: SecuritySingleCleanupResultVO }>('/admin/system-config/cleanup-audit-logs'),
}
