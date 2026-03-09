import { http } from '~/utils/http'

export interface SystemConfigVO {
  id: number
  configKey: string
  configValue: string
  description: string
  createTime: string
  updateTime: string
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
}
