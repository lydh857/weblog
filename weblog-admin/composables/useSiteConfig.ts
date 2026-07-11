/**
 * 管理端站点配置
 * 代码中仅保留通用默认值，实际品牌名、版权等信息从数据库动态读取。
 */
export const DEFAULT_SITE_NAME = 'Weblog'
export const DEFAULT_SITE_COPYRIGHT = '© 2026 Weblog. All rights reserved.'

export interface AdminSiteConfigState {
  siteName: string
  siteCopyright: string
  loaded: boolean
}

export function useSiteConfigState() {
  return useState<AdminSiteConfigState>('admin-site-config', () => ({
    siteName: DEFAULT_SITE_NAME,
    siteCopyright: DEFAULT_SITE_COPYRIGHT,
    loaded: false,
  }))
}
