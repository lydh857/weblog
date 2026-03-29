import { http } from '~/utils/http'

export interface SiteConfigVO {
  siteName: string
  siteDescription: string
}

export const siteConfigApi = {
  /** 获取用户端站点配置 */
  getPublic: () => http.get<unknown, { data: SiteConfigVO }>('/portal/site-config'),
}
