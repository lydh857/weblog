export const DEFAULT_SITE_NAME = 'Weblog'
export const DEFAULT_SITE_DESCRIPTION = '记录经验、分享洞察、连接有价值的内容。'

export interface SiteConfigState {
  siteName: string
  siteDescription: string
  loaded: boolean
}

export function useSiteConfigState() {
  return useState<SiteConfigState>('site-config', () => ({
    siteName: DEFAULT_SITE_NAME,
    siteDescription: DEFAULT_SITE_DESCRIPTION,
    loaded: false,
  }))
}
